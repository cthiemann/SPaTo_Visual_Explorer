

rem Wait for application to exit and kill it if it takes to long


ping -n 2 -w 1000 127.0.0.1
taskkill /f /im "SPaTo Visual Explorer.exe"



rem Move updates into place


move /y "%1\lib\_SPaTo_Visual_Explorer.exe" "%1\SPaTo Visual Explorer.exe"
move /y "%1\lib\_WinRun4J.jar" "%1\lib\WinRun4J.jar"



rem Restart application


"%1\SPaTo Visual Explorer.exe"