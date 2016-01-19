package net.reddit.statistics;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

public class Report {
	
	


	private Connection DBConn;
	private String htmlReport = "";
	private String redditPostReport = "";
	private String subRedditName;
	private Date endOfPeriod; 
	private int chunks; 
	private int chunkInDays;
	private int commentsTreshold;
	private int postsTreshold;
	private int showTopCount;
	
	
	public Report(String subRedditName, Date endOfPeriod, int chunks, int chunkInDays, int commentsTreshold, int postsTreshold, int showTopCount, Connection DBConn){
		this.subRedditName = subRedditName;
		this.chunkInDays = chunkInDays;
		this.chunks = chunks;
		this.endOfPeriod = endOfPeriod;
		this.commentsTreshold = commentsTreshold;
		this.postsTreshold = postsTreshold;
		this.showTopCount = showTopCount;
		this.DBConn = DBConn;
	}
	
	
	
	

	
	//Creates reports with posts and comments statistics.
	public void prepareReport(String reportDir, String locale) throws SQLException{
		

    	Properties l = getLocaleLexems(locale);
		PrintWriter writer;
		ResultSet rs;

		
		htmlReport+="<!DOCTYPE html><html><head><meta charset=\"utf-8\" /><title>"+ l.getProperty("Report_for") +
			subRedditName+"</title></head><body>";
		htmlReport+="<h1 align=\"center\">"+ l.getProperty("Report_for")+subRedditName+"</h1>";
		redditPostReport+="**"+ l.getProperty("Report_for")+subRedditName+"**\n\n";
		
		htmlReport+="<div><h2>"+l.getProperty("General_information")+"</h2></div>";
		redditPostReport+="**"+l.getProperty("General_information")+"**\n\n";	
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss");
		String period = sdf.format(new Date(endOfPeriod.getTime() - (long)chunks*chunkInDays*24*60*60*1000))+" - "+sdf.format(endOfPeriod);

		htmlReport+="<div style=\"width:30%\">";
		htmlReport+="<div><span style=\"font-weight: bold\">"+l.getProperty("Period")+"</span>"
				+ "<span style=\"float: right\">"+period+"</span></div>";
		redditPostReport+=l.getProperty("Period")+" "+period+"\n\n";	
		
		rs = DBConn.createStatement().executeQuery("Select count(*) from posts");
		rs.next();
		htmlReport+="<div><span style=\"font-weight: bold\">"+l.getProperty("Total_posts")+"</span><span style=\"float: right\">"+rs.getLong(1)+	"</span></div>";
		redditPostReport+=l.getProperty("Total_posts")+" "+rs.getLong(1)+"\n\n";	
		rs.close();

		rs = DBConn.createStatement().executeQuery("Select count(*) from comments");
		rs.next();
		htmlReport+="<div><span style=\"font-weight: bold\">"+l.getProperty("Total_comments")+"</span><span style=\"float: right\">"+rs.getLong(1)+	"</span></div>";
		redditPostReport+=l.getProperty("Total_comments") + " "+rs.getLong(1)+"\n\n";	
		rs.close();

		rs = DBConn.createStatement().executeQuery("select count(*) from  ( select author  from posts union select author from comments);");
		rs.next();
		htmlReport+="<div><span style=\"font-weight: bold\">"+l.getProperty("Users_participated")+"</span><span style=\"float: right\">"+rs.getLong(1)+	"</span></div>";
		redditPostReport+=l.getProperty("Users_participated")+" "+rs.getLong(1)+"\n\n";	
		rs.close();

		htmlReport+="<br><div><span style=\"font-weight: bold\">"+l.getProperty("Posts_threshold")+"</span><span style=\"float: right\">"+postsTreshold+"</span></div>";
		redditPostReport+=l.getProperty("Posts_threshold")+" "+postsTreshold+"\n\n";	

		htmlReport+="<div><span style=\"font-weight: bold\">"+l.getProperty("Comments_threshold")+"</span><span style=\"float: right\">"+commentsTreshold+"</span></div>";
		htmlReport+="</div>";
		redditPostReport+=l.getProperty("Comments_threshold")+" "+commentsTreshold+"\n\n";	
		
		htmlReport+="<div><h2>"+l.getProperty("Posts_statistics")+"</h2></div>";		
		redditPostReport+="**"+l.getProperty("Posts_statistics")+"**\n\n";	
		
		htmlReport+="<div><h3>1. "+l.getProperty("Posts_created")+"</h3></div>";
		redditPostReport+="1 "+l.getProperty("Posts_created")+"\n\n";	
		redditPostReport+= l.getProperty("user_post")+"\n";
		getTableData("select top "+showTopCount+" author, count(*) from posts group by author  having count(*) >=" + postsTreshold +" order by count(*) desc");
		
		htmlReport+="<div><h3>2. "+l.getProperty("Posts_carma_total")+"</h3></div>";
		redditPostReport+="2 "+l.getProperty("Posts_carma_total")+"\n\n";	
		redditPostReport+= l.getProperty("user_carma")+"\n";
		getTableData("select top "+showTopCount+" author, sum (ups) from posts group by author  having count(*) >=" + postsTreshold+" order by sum(ups) desc");
		
		htmlReport+="<div><h3>3. "+l.getProperty("Posts_carma_average")+"</h3></div>";
		redditPostReport+="3 "+l.getProperty("Posts_carma_average")+"\n\n";	
		redditPostReport+= l.getProperty("user_avg_carma")+"\n";
		getTableData("select top "+showTopCount+" author, 1.00*sum(ups)/count(*) from posts group by author having count(*) >=" + postsTreshold+" order by 1.00*sum(ups)/count(*) desc");
		
		htmlReport+="<div><h3>4. "+l.getProperty("Comments_total_for_posts")+"</h3></div>";
		redditPostReport+="4 "+l.getProperty("Comments_total_for_posts")+"\n\n";	
		redditPostReport+= l.getProperty("user_comments")+"\n";
		getTableData("select top "+showTopCount+" author, sum(num_comments) from posts group by author  having count(*) >=" + postsTreshold+" order by sum(num_comments) desc");
		
		htmlReport+="<div><h3>5. "+l.getProperty("Comments_average_for_posts")+"</h3></div>";
		redditPostReport+="5 "+l.getProperty("Comments_average_for_posts")+"\n\n";	
		redditPostReport+= l.getProperty("user_avg_comments")+"\n";
		getTableData("select top "+showTopCount+" author, 1.00*sum(num_comments)/count(*) from posts group by author  having count(*) >=" + postsTreshold+" order by 1.00*sum(num_comments)/count(*) desc");
		
		htmlReport+="<div><h3>6. "+l.getProperty("Comments_by_user_total")+"</h3></div>";
		redditPostReport+="6 "+l.getProperty("Comments_by_user_total")+"\n\n";	
		redditPostReport+= l.getProperty("user_comments")+"\n";
		getTableData("select top "+showTopCount+" author, count(*) from comments group by author  having count(*) >=" + commentsTreshold +" order by count(*) desc");
		
		htmlReport+="<div><h3>7. "+l.getProperty("Comments_carma_total")+"</h3></div>";
		redditPostReport+="7 "+l.getProperty("Comments_carma_total")+"\n\n";	
		redditPostReport+= l.getProperty("user_carma")+"\n";
		getTableData("select top "+showTopCount+" author, sum (score) from comments group by author  having count(*) >=" + commentsTreshold+" order by sum(score) desc");
		
		htmlReport+="<div><h3>8. "+l.getProperty("Comments_carma_avg")+"</h3></div>";
		redditPostReport+="8 "+l.getProperty("Comments_carma_avg")+"\n\n";	
		redditPostReport+= l.getProperty("user_avg_carma")+"\n";
		getTableData("select top "+showTopCount+" author, 1.00*sum(score)/count(*)  from comments group by author  having count(*) >=" + commentsTreshold+" order by 1.00*sum(score)/count(*)  desc");
		
		htmlReport+="<div><h3>9. "+l.getProperty("Comments_anti-carma_avg")+"</h3></div>";
		redditPostReport+="9 "+l.getProperty("Comments_anti-carma_avg")+"\n\n";	
		redditPostReport+= l.getProperty("user_avg_carma")+"\n";
		getTableData("select top "+showTopCount+" author, 1.00*sum(score)/count(*)  from comments group by author  having count(*) >=" + commentsTreshold+" order by 1.00*sum(score)/count(*)");
		
		htmlReport+="<div><h3>10. "+l.getProperty("Popular_flairs")+"</h3></div>";
		redditPostReport+="10 "+l.getProperty("Popular_flairs")+"\n\n";	
		redditPostReport+= l.getProperty("flair_usage")+"\n";
		getTableData("select top "+showTopCount+" link_flair_text, count(*) from posts group by link_flair_text  having count(*) >=" + postsTreshold +" order by count(*) desc");
		
		htmlReport+="<div><h2>"+l.getProperty("Remarkable_posts_and_comments")+"</h2></div>";
		redditPostReport+="**"+l.getProperty("Remarkable_posts_and_comments")+"**\n\n";	
		
		htmlReport+="<div><h3>1. "+l.getProperty("Top_rated_posts")+"</h3></div>";
		redditPostReport+="1 "+l.getProperty("Top_rated_posts")+"\n\n";	
		redditPostReport+= l.getProperty("post_raiting")+"\n";
		getTableDataForLinks("select top "+showTopCount+" permalink, ups from posts order by ups desc");
		
		htmlReport+="<div><h3>2. "+l.getProperty("Top_rated_comments")+"</h3></div>";
		redditPostReport+="2 "+l.getProperty("Top_rated_comments")+"\n\n";	
		redditPostReport+= l.getProperty("comment_raiting")+"\n";
		getTableDataForLinks("select top "+showTopCount+" permalink, score from comments order by score desc");
		
		htmlReport+="<div><h3>3. "+l.getProperty("Top_anti-rated_comments")+"</h3></div>";
		redditPostReport+="3 "+l.getProperty("Top_anti-rated_comments")+"\n\n";	
		redditPostReport+= l.getProperty("comment_raiting")+"\n";
		getTableDataForLinks("select top "+showTopCount+" permalink, score from comments order by score");
		
		
		
		htmlReport+="<br><div style=\"text-align:right; font-size:small\">"+l.getProperty("if_error")+"</div>";
		redditPostReport+="\n\n*"+l.getProperty("if_error")+"*";	
		
		
		htmlReport+="</body><html>";
		
		try {
			writer = new PrintWriter(reportDir+"/report.html", "UTF-8");
			writer.print(htmlReport);
			writer.close();
			
			writer = new PrintWriter(reportDir+"/redditPost.txt", "UTF-8");
			writer.print(redditPostReport);
			writer.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
	}
	
	
	
	
	//creates "general" chart, based on given SQL request
	private void getTableData(String sql) throws SQLException{
		String result = "";
		int length = 0;
		ResultSet rs = DBConn.createStatement().executeQuery(sql);
		
		redditPostReport+= ":--|:--\n";
		while(rs.next()){
			result+="<div style = \"border-style: ridge;\"><span style=\"font-weight: bold\">"+rs.getString(1)+"</span>"
					+ "<span style=\"float: right\">"+rs.getString(2)+"</span></div>";
			redditPostReport+= rs.getString(1)+"|"+rs.getString(2)+"\n";
			
			length = Math.max(length, rs.getString(1).length());
		}
		result+="</div>";

		result="<div style=\"width:"+(length+5)*10+"px;font-family: monospace;font-size: 14px;\">"+result;
		htmlReport+=result;
	}
	
	//creates chart with links, based on given SQL request
	private void getTableDataForLinks(String sql) throws SQLException{
		String result = "";
		int length = 0;
		ResultSet rs = DBConn.createStatement().executeQuery(sql);
		
		redditPostReport+= ":--|:--\n";
		while(rs.next()){
			result+="<div style = \"border-style: ridge;\">" +
					"<span style=\"font-weight: bold\"><a href=\""+rs.getString(1)+"\">"+rs.getString(1)+"</a></span>"
					+ "<span style=\"float: right\">"+rs.getString(2)+"</span></div>";
			redditPostReport+= rs.getString(1)+"|"+rs.getString(2)+"\n";
			
			length = Math.max(length, rs.getString(1).length());
		}
		result+="</div>";
		result="<div style=\"width:"+(length-5)*10+"px;font-family: monospace;font-size: 14px;\">"+result;
		htmlReport+=result;
	}	
	
	
	//loads language-dependent strings
	private Properties getLocaleLexems(String locale){
    	Properties strings = new Properties();
    	InputStream input = null;
    	try {
    		input = new FileInputStream("resources"+File.separator+"locale."+locale.trim());
    		InputStreamReader isr = new InputStreamReader(input, "UTF-8");
    		strings.load(isr);
		} catch (FileNotFoundException e) {
    		try {
				input = new FileInputStream("resources"+File.separator+"locale.EN");
	    		InputStreamReader isr = new InputStreamReader(input, "UTF-8");				
	    		strings.load(isr);
			} catch (IOException ex) {ex.printStackTrace();}
		} catch (IOException e) {
			e.printStackTrace();
		}finally{
        	if(input!=null){
        		try {
				input.close();
				
        		} catch (IOException e) {e.printStackTrace();}
        	}
        }	
    	return strings;
	}
		
	
}
