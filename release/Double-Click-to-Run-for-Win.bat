cd /d %~dp0
:: 如果存在内置JRE，优先使用
set Path=%~dp0minimal-bilibilidown-jre\bin;%Path%
set Path=%~dp0runtime\bin\;%Path%
:: 启动应用
start javaw -Dfile.encoding=utf-8 -Dbilibili.prop.log=false -Dhttps.protocols=TLSv1.2 -jar INeedBiliAV.jar
