set "tomcatDir=%CATALINA_HOME%"

:: start tomcat
start "" cmd /c "%tomcatDir%/bin/startup.bat"

:: start webpage
timeout /t 5 > nul
start "" "http://localhost:8080/gp3/index.html"