set ARGS=%*

if "%ARGS%" != "" (
    gradlew.bat run --args="%ARGS%"
) else (
    gradlew.bat run
)
