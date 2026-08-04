cd /d %~dp0
set Path=%~dp0minimal-bilibilidown-jre\bin\;%Path%
set Path=%~dp0runtime\bin\;%Path%
java -Dfile.encoding=utf-8 -Dhttps.protocols=TLSv1.2 -jar INeedBiliAV.jar
pause
