@rem SPDX-License-Identifier: GPL-3.0-only
@rem Copyright (C) 2026 WaEnhancer contributors
@echo off
setlocal

set "APP_HOME=%~dp0"
if defined JAVA_HOME (
  set "JAVA_COMMAND=%JAVA_HOME%\bin\java.exe"
) else (
  set "JAVA_COMMAND=java.exe"
)

"%JAVA_COMMAND%" %JAVA_OPTS% %GRADLE_OPTS% -Dorg.gradle.appname=gradlew -classpath "%APP_HOME%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
