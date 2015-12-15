package net.reddit.statistics;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;



//class for accessing reddit. Important for fulfilling requirement "no more 30 requests per minute"
public class RedditAccessor {
	
	private static final int minInterval = 2000;
	private static volatile Date lastAccess;
	private static RedditAccessor instance;
	private int interval;
	
	private RedditAccessor(){
		interval = minInterval;
		lastAccess = new Date();
	}
	public static RedditAccessor getInstance(){
		if(instance==null){
			instance =new  RedditAccessor();

		}
		return instance;		
	}
	public void setInterval(int newInterval){
		interval=Math.max(minInterval, newInterval);
	}
	

	
	//Just retrieves page, without bothering reddit too much
	public String getJSONbyURL(String url) throws IOException{
		
		HttpURLConnection con;
		String USER_AGENT = "Java:net.reddit.statistics:1.0.0 (by /r/abooka)";
		URL obj = new URL(url);		
		
		brake();
		lastAccess = new Date();
		con = (HttpURLConnection) obj.openConnection();
 		con.setRequestMethod("GET");
 		con.setRequestProperty("User-Agent", USER_AGENT);
 		

 		
 		
		int retry=0;
		while(con.getResponseCode()!=200){
			con.disconnect();
			brake();
			lastAccess = new Date();
			con = (HttpURLConnection) obj.openConnection();
			retry++;
			if (retry>10){
				System.out.println("Problems with accessing url "+url+". Check internet connection.");
			}
		}


		BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
		String inputLine;
		StringBuffer response = new StringBuffer();
 
		while ((inputLine = in.readLine()) != null) {
			response.append(inputLine);
		}
		in.close();
		con.disconnect();
		return response.toString();		

	}

	//Slows down requests flow if needed
	private void brake(){
		while((new Date()).getTime()-lastAccess.getTime()<interval){
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}		
	}
	

}
