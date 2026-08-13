@echo off
setlocal EnableExtensions

set "APP_HOME=%~dp0"
set "WRAPPER_JAR=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"
set "WRAPPER_URL=https://services.gradle.org/distributions/gradle-9.3.1-wrapper.jar"
set "WRAPPER_SHA256=b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13"

if not exist "%WRAPPER_JAR%" (
  echo Downloading the Gradle wrapper runtime...
  powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference='Stop'; Invoke-WebRequest -Uri '%WRAPPER_URL%' -OutFile '%WRAPPER_JAR%'; if ((Get-FileHash -Algorithm SHA256 '%WRAPPER_JAR%').Hash.ToLowerInvariant() -ne '%WRAPPER_SHA256%') { Remove-Item -LiteralPath '%WRAPPER_JAR%'; throw 'Gradle wrapper checksum verification failed.' }"
  if errorlevel 1 exit /b 1
)

if defined JAVA_HOME (
  set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_EXE=java.exe"
)

"%JAVA_EXE%" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
