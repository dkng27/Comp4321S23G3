@Echo off

set "tomcatDir=%CATALINA_HOME%"
set "webappsDir=%tomcatDir%\webapps"
set "projectDir=%~dp0"
set "projectName=gp3"
set "classesDir=%projectDir%classes"
set "libsDir=%projectDir%libs"

:: compile the java classes into the webapp/WEB-INF/classes directory
javac -d %projectName%\WEB-INF\classes -cp "libs\*" src\main\java\searchEngine\*

:: copy the webapp into tomcat directory
xcopy "%projectName%" "%webappsDir%\%projectName%\" /s /e /y

:: copy the libs into webappsDir/WEB-INF/lib
xcopy "%libsDir%\*" "%webappsDir%\%projectName%\WEB-INF\lib\" /s /y

cls

echo The search engine is built. You can now run "start.bat" to start it.
pause