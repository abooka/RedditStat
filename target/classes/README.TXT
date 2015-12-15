1. What is it? 
- It's a small program for scraping data about posts and comments for specified subreddit. See https://www.reddit.com/ 
if you have no idea what "subreddit" is. 
Program generates HSQL database with collected data and prepares small report with some statistical information - total 
number of comments for period of time, users, who created the biggest amount of posts, most rated posts, etc.

2. Why do I need this?
-  Probably you don't. But I think it's funny.

3. How can I use it?
- First of all you need Java installed on your computer. Then download .zip distributive, extract contents, 
enter parameters into config.properties file (see below), start Run.bat or Run.sh depending on operation system to start 
collection of data. After finishing the work you could find report and DB in corresponding report folder, generated 
by program (with name like "Report_2015.12.09-00:05:17").

4. How to configure config.properties file?
- File config.properties contains following parameters:

~ "subRedditName" - name of your subreddit.
~ "endOfPeriod", "chunkInDays" and "periodInChunks" - These parameters are for definition of period, for which you want 
to collect data. It's a bit overcomplicated because Reddit search results are limited to 1000 results. 
In most cases it will be OK if you set chunkInDays=1, periodInChunks=<Duration of period in days>, and 
endOfPeriod = <End of search period. Format - dd.MM.yyyy HH:mm:ss>. 
~ "postsTreshold" users with less then postsTreshold posts created for period will not be shown at report charts 
related to posts.
~ "commentsTreshold" users with less then commentsTreshold comments created for period will not be shown at report charts 
related to comments.
~ "showTopCount" - size of report charts;
~ "threadCount" - quantity of threads for comments processing. Probably, default value is OK. 
~ "httpRequestIntervalInMills" - minimum interval between consequent requests. Reddit policy is: "Make no more than thirty 
requests per minute" (https://github.com/reddit/reddit/wiki/API), so minimum value is 2000;

5. How can I execute my own query?
- You can run HSQL Database Manager with command: "java -jar lib/hsqldb-2.3.3.jar". Then select Standalone DB type and 
enter DB URL - something like "jdbc:hsqldb:file:/home/myName/SubRedditStat/Report_2015.12.09-01:23:28/redditdb». 
DB structure is quite simple and self-explanatory. For more details about HSQL DB see http://hsqldb.org/

6. I have found a bug!
- Please, contact /r/abooka.
