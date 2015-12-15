package net.reddit.statistics;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

public class CommentParser implements Runnable{
	
	Connection DBConn;
	JSONParser parser = new JSONParser();
	List<String> posts;
	
	
	//Receives list of post URL's and DB name then collects information about 
	//corresponding comments and stores it into DB
	CommentParser(List<String> posts, String dbName) throws SQLException{
		this.posts=posts;
		DBConn = DriverManager.getConnection("jdbc:hsqldb:file:"+dbName+";shutdown=true;hsqldb.write_delay=false", "SA", "");	
	}
	

	public void run() {
		//just collect comments for each post
		for(String postURL: posts){
			try {
				JSONArray comments = (JSONArray) parser.parse(RedditAccessor.getInstance().getJSONbyURL(postURL+".json"));
				JSONObject data = (JSONObject)((JSONObject)comments.get(1)).get("data");
				JSONArray children =  (JSONArray)data.get("children");
				processComments(children, postURL);
				DBConn.commit();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		try {
			DBConn.commit();
			DBConn.close();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	
	void processComments(List<JSONObject> comments, String postURL) throws ParseException, IOException, SQLException, org.json.simple.parser.ParseException{

		PreparedStatement stmt = DBConn.prepareStatement("insert into comments (post, permalink, author, score, "
				+ "comment_start) values (?,?,?,?,?);");	
		//process all 1-st level comments or other objects
		for(JSONObject comment: comments ){
			String kind = (String)comment.get("kind");
			JSONObject data = (JSONObject)comment.get("data");
			//if it's comment - store it
			if(kind.equals("t1")){
				String comment_start = (String)data.get("body");
				if(comment_start.length()>100){
					comment_start= comment_start.substring(0, 100);
				}
				comment_start= comment_start.replace("'", "");
				System.out.println(postURL+data.get("id"));;
				stmt.setString(1, postURL);
				stmt.setString(2, postURL+data.get("id"));
				stmt.setString(3, ""+data.get("author"));
				stmt.setLong(4, (Long)data.get("score"));
				stmt.setString(5, comment_start);
				stmt.execute();
				//then recursively process replies
				if(!data.get("replies").equals("")){
					JSONObject innerData = (JSONObject)((JSONObject)data.get("replies")).get("data");
					processComments((List<JSONObject>) innerData.get("children"), postURL);
				}
			//if it's "more" object - process it in a special way	
			}else if(kind.equals("more")){
				for(String childId: (List<String>)data.get("children")){
					processMoreComments(postURL, childId);
				}
			//if something else - then panic.	
			}else{
				System.out.println("!!!!!!!!!!!!!!!!!!Unexpected kind: "+kind);
			}
			
		}
	}
	
	//gets "more" URL and processes comments for it
	private void processMoreComments(String postURL, String id) throws ParseException, IOException, SQLException, org.json.simple.parser.ParseException{
		JSONArray comments = (JSONArray) parser.parse(RedditAccessor.getInstance().getJSONbyURL(postURL+id+".json"));
		JSONObject obj = (JSONObject)comments.get(1);
		obj = (JSONObject)obj.get("data");
		processComments( (JSONArray)obj.get("children"), postURL);
		
	}

	

}
