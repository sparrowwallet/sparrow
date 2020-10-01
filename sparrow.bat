@echo off
set ARGS=%*

if "%ARGS%" == "" (
    gradlew.bat run    
) else (
	gradlew.bat run --args="%ARGS%"
)
