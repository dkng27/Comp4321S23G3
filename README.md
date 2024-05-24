This is a re-upload of a group project repo, which was originally hosted on a temporary private Gitea server.
With thanks to my fellow university schoolmates Anthony Tse and Miki Ho at HKUST!
<br>

# Group 3 Search Engine Project - gp3Search
![](./title2.png)

## Resources Used:
1. `jdbm-1.0.jar` This is the jdbm library used in lab 1.
2. `htmlparser.jar` This is the htmlparser used in lab 2.
3. `IRUtilities.jar` This is the Porter implementation from lab 3; We've jar'd it to make it more portable. The Stemmer class was moved into the source folder and compiled alongside the spider.
4. `jsoup-1.17.2.jar` The JSoup library is also used for parsing html. It provides functionalities not available in htmlparser, *i.e.*, getting http headers and page size.
5. `jakarta.servlet-api-5.0.0.jar` The jakarta library to create a servlet connecting the frontend search webpage with the search engine instance.
6. `bootstrap 4.0` for simplifying writing the frontend webpage

## For Grader:
Please observe the following when running the program:
* Windows
* Java version >= 17 SE (18 should be fine)
* Tomcat 10 with environment variables `%CATALINA_HOME%` and `%JAVA_HOME%` hooked up properly (refer to lab4)

A script `build.bat` is provided to automate the compilation of the search engine and moving everything to the tomcat `webapp` folder.
It will tell you so once it finishes, then you can run `start.bat` to start the search engine.