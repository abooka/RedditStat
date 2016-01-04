package net.reddit.statistics;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;



public class SubRedditStatJSON {
	
	
	static Connection DBConn;
	static List<String> posts;
	static JSONParser parser = new JSONParser();
	static String reportDir;
	static String dbName;
	static String subRedditName;
	static Date endOfPeriod;
	static int chunkInDays; 
	static int chunks;
	static int commentsTreshold;
	static int postsTreshold;
	static int showTopCount;
	
	public static void main(String[] args) throws ParseException, IOException, SQLException, InterruptedException {

		
		String url;
		String after = "";
		posts = new ArrayList<String>();		

		//read properties and initialize variables
		Properties props = readProperties();
		subRedditName = props.getProperty("subRedditName");
		String searchURL = "https://www.reddit.com/r/"+subRedditName+"/search.json?rank=title&sort=new&restrict_sr=on&syntax=cloudsearch";
		int threadCount = Integer.parseInt(props.getProperty("threadCount"));
		showTopCount = Integer.parseInt(props.getProperty("showTopCount"));
		chunkInDays = Integer.parseInt(props.getProperty("chunkInDays"));
		chunks = Integer.parseInt(props.getProperty("periodInChunks"));
		commentsTreshold = Integer.parseInt(props.getProperty("commentsTreshold"));
		postsTreshold = Integer.parseInt(props.getProperty("postsTreshold"));
		RedditAccessor.getInstance().setInterval(Integer.parseInt(props.getProperty("httpRequestIntervalInMills")));
		SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
		Date endDate = new Date();
		try {
			endDate = sdf.parse(props.getProperty("endOfPeriod"));
			endOfPeriod = (Date)endDate.clone();
		} catch (java.text.ParseException e) {
			e.printStackTrace();
			System.out.println("ERROR Can't parse date. Using current date. Correct date format: dd.MM.yy HH:mm:ss");
		}
		sdf = new SimpleDateFormat("yyyy.MM.dd-HH:mm:ss");
		reportDir = "Report_"+sdf.format(new Date());
		dbName = reportDir+"/redditdb"; 
		
		//creating database
		System.out.println("=======================================================================");
		System.out.println("Preparing DB..");
		DBConn = DriverManager.getConnection("jdbc:hsqldb:file:"+dbName+";shutdown=true;hsqldb.write_delay=false", "SA", "");	
		initDB ();

		//collecting posts using search api
		System.out.println("=======================================================================");
		System.out.println("Collecting information about posts..");;
		String urlForPeriod = searchURL+"&q=timestamp:"+(endDate.getTime()/1000 - chunkInDays*24*60*60)+".."+endDate.getTime()/1000;
		int counter = 0;
		while(counter < chunks){
			url = urlForPeriod;

			//process search result for each time chunk 
			while(url!=null){
				//retrieving post list from search page
				JSONObject postList = (JSONObject) parser.parse(RedditAccessor.getInstance().getJSONbyURL(url));
				JSONObject data = (JSONObject) postList.get("data");
				after = (String)data.get("after");	 
				int retry=0;
				
				//sometimes reddit returns an empty list even if data is present, so do some retries just to make sure
				// that we really have reached end of list.
				while((String)data.get("before")==null && after==null && retry<3){
					postList = (JSONObject) parser.parse(RedditAccessor.getInstance().getJSONbyURL(url));
					data = (JSONObject) postList.get("data");
					after = (String)data.get("after");	 
					retry++;
				}
				//if there is nextpage in listing then process it, otherwise -end the cycle.
				if (after!= null){
					 url = urlForPeriod+ "&count=25&after="+after;
				}else{
					 url = null;
				}
				//process all posts for this page
				for (JSONObject post : (List<JSONObject>)data.get("children")){
					 processPost(post);
				}
			}
			
			endDate.setTime(endDate.getTime()-(long)chunkInDays*24*60*60*1000);
			urlForPeriod = searchURL+"&q=timestamp:"+(endDate.getTime()/1000 - (long)chunkInDays*24*60*60)+".."+endDate.getTime()/1000; 
			counter++;
		}
		
	
		//now posts List contains permalinks for all posts so it's time to retrieve comments
		System.out.println("=======================================================================");
		System.out.println("Collecting information about comments..");
		//split posts List onto threadCount List's and for each of then start comment parser in separate thread
		
		Thread[] threads =  new Thread[threadCount]; 
		for(int i=0; i<threadCount; i++){
			int size = posts.size()/threadCount+1;
			List<String> postsToProcess = Arrays.asList(Arrays.copyOfRange(posts.toArray(new String[posts.size()]), i*size, Math.min((i+1)*size, posts.size())));
			CommentParser commentParser = new CommentParser(postsToProcess, dbName);
			Thread thread = new Thread(commentParser);
			
			thread.start();
			threads[i]=thread;

		}
		
		for (int i=0; i<threadCount; i++){
			threads[i].join();
		}
		

		//All data is collected now. Creating report
		System.out.println("=======================================================================");
		System.out.println("Preparing report..");		
		prepareReport();
		
		//and finish
		DBConn.commit();
		DBConn.close();
		System.out.println("=======================================================================");
		System.out.println("Finished!");		
		

	}
	
	
	

	//Creates HTML report with posts and comments statistics.
	private static void prepareReport() throws SQLException{
		PrintWriter writer;
		String htmlReport= "";
		ResultSet rs;
		
		htmlReport+="<!DOCTYPE html><html><head><meta charset=\"utf-8\" /><title>Report for /r/"+subRedditName+"</title></head><body>";
		htmlReport+="<h1 align=\"center\">Report for /r/"+subRedditName+"</h1>";
		htmlReport+="<div><h2>General information</h2></div>";
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		htmlReport+="<div style=\"width:30%\">";
		htmlReport+="<div><span style=\"font-weight: bold\">Period:</span>"
				+ "<span style=\"float: right\">"+sdf.format(new Date(endOfPeriod.getTime() - (long)chunks*chunkInDays*24*60*60*1000))+" - "+sdf.format(endOfPeriod)+"</span></div>";
		rs = DBConn.createStatement().executeQuery("Select count(*) from posts");
		rs.next();
		htmlReport+="<div><span style=\"font-weight: bold\">Total posts:</span><span style=\"float: right\">"+rs.getLong(1)+	"</span></div>";
		rs.close();

		rs = DBConn.createStatement().executeQuery("Select count(*) from comments");
		rs.next();
		htmlReport+="<div><span style=\"font-weight: bold\">Total comments:</span><span style=\"float: right\">"+rs.getLong(1)+	"</span></div>";
		rs.close();

		rs = DBConn.createStatement().executeQuery("select count(*) from  ( select author  from posts union select author from comments);");
		rs.next();
		htmlReport+="<div><span style=\"font-weight: bold\">Users participated:</span><span style=\"float: right\">"+rs.getLong(1)+	"</span></div>";
		rs.close();

		htmlReport+="<br><div><span style=\"font-weight: bold\">Posts treshold:</span><span style=\"float: right\">"+postsTreshold+"</span></div>";
		htmlReport+="<div><span style=\"font-weight: bold\">Comments treshold:</span><span style=\"float: right\">"+commentsTreshold+"</span></div>";
		htmlReport+="</div>";
		
		htmlReport+="<div><h2>Posts statistics</h2></div>";
		
		htmlReport+="<div><h3>1. Posts created:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, count(*) from posts group by author  having count(*) >=" + postsTreshold +" order by count(*) desc", false);
		
		htmlReport+="<div><h3>2. Posts carma total:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, sum (ups) from posts group by author  having count(*) >=" + postsTreshold+" order by sum(ups) desc", false);
		
		htmlReport+="<div><h3>3. Posts carma average:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, 1.00*sum(ups)/count(*) from posts group by author having count(*) >=" + postsTreshold+" order by 1.00*sum(ups)/count(*) desc", false);
		
		htmlReport+="<div><h3>4. Comments total for created posts:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, sum(num_comments) from posts group by author  having count(*) >=" + postsTreshold+" order by sum(num_comments) desc", false);
		
		htmlReport+="<div><h3>5. Comments average for created posts:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, 1.00*sum(num_comments)/count(*) from posts group by author  having count(*) >=" + postsTreshold+" order by 1.00*sum(num_comments)/count(*) desc", false);
		
		htmlReport+="<div><h3>6. Comments by user total:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, count(*) from comments group by author  having count(*) >=" + commentsTreshold +" order by count(*) desc", false);
		
		htmlReport+="<div><h3>7. Comments carma total:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, sum (score) from comments group by author  having count(*) >=" + commentsTreshold+" order by sum(score) desc", false);
		
		htmlReport+="<div><h3>8. Comments carma average:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, 1.00*sum(score)/count(*)  from comments group by author  having count(*) >=" + commentsTreshold+" order by 1.00*sum(score)/count(*)  desc", false);
		
		htmlReport+="<div><h3>9. Comments anti-carma average:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" author, 1.00*sum(score)/count(*)  from comments group by author  having count(*) >=" + commentsTreshold+" order by 1.00*sum(score)/count(*)", false);
		
		htmlReport+="<div><h3>10. Popular flairs:</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+" link_flair_text, count(*) from posts group by link_flair_text  having count(*) >=" + postsTreshold +" order by count(*) desc", false);
		
		htmlReport+="<div><h2>Remarkable posts and comments</h2></div>";
		
		htmlReport+="<div><h3>1. Top rated posts</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+"'<a href=\"'+permalink+'\">'+permalink+'</a>', ups from posts order by ups desc", true);
		
		htmlReport+="<div><h3>2. Top rated comments</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+"'<a href=\"'+permalink+'\">'+permalink+'</a>', score from comments order by score desc", true);
		
		htmlReport+="<div><h3>3. Top anti-rated comments</h3></div>";
		htmlReport+= getRecordsetAsHTML("select top "+showTopCount+"'<a href=\"'+permalink+'\">'+permalink+'</a>', score from comments order by score", true);
		
		
		
		htmlReport+="<br><div style=\"text-align:right; font-size:small\">If you have found an error, please contact /r/abooka.</div>";
		
		
		
		
		
		
		htmlReport+="</body><html>";
		
		try {
			writer = new PrintWriter(reportDir+"/report.html", "UTF-8");
			writer.print(htmlReport);

			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	
	
	
	
	private static String getRecordsetAsHTML(String sql, boolean isLink) throws SQLException{
		String result = "";
		int length = 0;
		ResultSet rs = DBConn.createStatement().executeQuery(sql);
		
		while(rs.next()){
			result+="<div style = \"border-style: ridge;\"><span style=\"font-weight: bold\">"+rs.getString(1)+"</span>"
					+ "<span style=\"float: right\">"+rs.getString(2)+"</span></div>";
			length = Math.max(length, rs.getString(1).length());
		}
		result+="</div>";
		
		
		if (isLink){
			result="<div style=\"width:"+(length-5)*5+"px;font-family: monospace;font-size: 14px;\">"+result;
		}else{
			result="<div style=\"width:"+(length+5)*10+"px;font-family: monospace;font-size: 14px;\">"+result;
		}
		return result;
	}
	
	
	
	
	
	
	

	
	private static Properties readProperties(){
    	Properties prop = new Properties();
    	InputStream input = null;
    	try {
    		input = new FileInputStream("config.properties");
    		prop.load(input);
   	        System.out.println("Properties loaded : "+prop);
    	} catch (IOException ex) {
    		ex.printStackTrace();
        } finally{
        	if(input!=null){
        		try {
				input.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
        	}
        }		
		return prop;  
	}

	//Creates simple database
	private static void initDB () throws SQLException{
		Statement stmt = DBConn.createStatement();		
		stmt.execute("drop table if exists comments;");
		DBConn.createStatement().execute("create table comments (post varchar(255), permalink varchar(255), author varchar(255),"
		+ " comment_start varchar(255), score int);");		
		stmt.execute("drop table if exists posts;");
		stmt.execute("create table posts (permalink varchar(255), link_flair_text varchar(255),"
		 		+ " author varchar(255), ups int, num_comments int, created_utc date);");

		DBConn.commit();		
	}
	
	//Collects information about post, stores it to DB and saves permalink into posts List for retrieving comments
	private static void processPost(JSONObject post) throws SQLException{
		JSONObject data = (JSONObject)post.get("data");
		
		String permalink = "https://www.reddit.com" + data.get("permalink");
		permalink=permalink.replace("?ref=search_posts", "");
		posts.add(permalink);
		System.out.println(permalink);

		String threadSQL = "insert into posts (permalink, link_flair_text, author, ups, num_comments, created_utc) values ('"+ 
				permalink +"','"+
				data.get("link_flair_text") +"','"+
				data.get("author") +"',"+
				data.get("ups") +","+
				data.get("num_comments") +",'"+
				new java.sql.Date(((Double)data.get("created_utc")).longValue()*1000) +"');";
		DBConn.createStatement().execute(threadSQL);
		DBConn.commit();
	}

}
