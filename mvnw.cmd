@REM ----------------------------------------------------------------------------
@REM Licensed to the Apache Software Foundation (ASF) under one
@REM or more contributor license agreements.  See the NOTICE file
@REM distributed with this work for additional information
@REM regarding copyright ownership.  The ASF licenses this file
@REM to you under the Apache License, Version 2.0 (the
@REM "License"); you may not use this file except in compliance
@REM with the License.  You may obtain a copy of the License at
@REM
@REM    http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM Unless required by applicable law or agreed to in writing,
@REM software distributed under the License is distributed on an
@REM "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
@REM KIND, either express or implied.  See the License for the
@REM specific language governing permissions and limitations
@REM under the License.
@REM ----------------------------------------------------------------------------

@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script
@REM
@REM Required ENV vars:
@REM JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars
@REM M2_HOME - location of maven's installed home dir
@REM MAVEN_BATCH_ECHO - set to 'on' to enable the echoing of the batch commands
@REM MAVEN_BATCH_PAUSE - set to 'on' to wait for a keystroke before ending
@REM MAVEN_OPTS - parameters passed to the Java VM when running Maven
@REM     e.g. to debug Maven itself, use
@REM set MAVEN_OPTS=-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
@REM MAVEN_SKIP_RC - flag to disable loading of mavenrc files
@REM ----------------------------------------------------------------------------

@setlocal

set ERROR_CODE=0

@REM To isolate internal variables from possible post scripts, we use another setlocal
@setlocal

@REM ==== START VALIDATION ====
if not "%JAVA_HOME%" == "" goto OkJHome

echo.
echo Error: JAVA_HOME not found in your environment. >&2
echo Please set the JAVA_HOME variable in your environment to match the >&2
echo location of your Java installation. >&2
echo.
goto error

:OkJHome
if exist "%JAVA_HOME%\bin\java.exe" goto init

echo.
echo Error: JAVA_HOME is set to an invalid directory. >&2
echo JAVA_HOME = "%JAVA_HOME%" >&2
echo Please set the JAVA_HOME variable in your environment to match the >&2
echo location of your Java installation. >&2
echo.
goto error

@REM ==== END VALIDATION ====

:init

@REM Find the project base dir, i.e. the directory that contains the folder ".mvn".
@REM Fallback to current working directory if not found.

set MAVEN_PROJECTBASEDIR=%MAVEN_BASEDIR%
IF "%MAVEN_PROJECTBASEDIR%"=="" goto findBaseDir

pushd "%MAVEN_PROJECTBASEDIR%"
goto endDetectBaseDir

:findBaseDir
set MAVEN_PROJECTBASEDIR=%CD%
setlocal enabledelayedexpansion

:findBaseDir_loop
if "!MAVEN_PROJECTBASEDIR!"=="" goto endDetectBaseDir
if exist "!MAVEN_PROJECTBASEDIR!\.mvn" goto baseDirFound
cd ..
set MAVEN_PROJECTBASEDIR=!CD!
goto findBaseDir_loop

:baseDirFound
cd /d "%MAVEN_PROJECTBASEDIR%"

:endDetectBaseDir
if "%MAVEN_PROJECTBASEDIR%"=="" (
  set MAVEN_PROJECTBASEDIR=%CD%
) else (
  pushd "%MAVEN_PROJECTBASEDIR%"
)

if not "%MAVEN_PROJECTBASEDIR%"=="" (
  set MAVEN_BASEDIR=%MAVEN_PROJECTBASEDIR%
)

@REM Setup the command line

set CLASSWORLDS_LAUNCHER=org.codehaus.plexus.classworlds.launcher.Launcher

@REM Since MAVEN_BATCH_ECHO is set to 'on', echo the command being executed
@echo off
@if "%MAVEN_BATCH_ECHO%" == "on"  echo %MAVEN_CMD_LINE_ARGS%

@REM wrap the command line arguments (not the command itself):
@REM active the next two lines for Windows NT CMD.exe
@setlocal enabledelayedexpansion
for /F "usebackq delims=*" %%F in ('findstr /r /M "^:endInit" %0') do set "endMarker=%%F" & setlocal disableDelayedExpansion & goto parseArgs

:computeDrivePrefix
pushd %1\.
set "DRIVE_PREFIX=%CD:~0,2%"
popd
goto endDetectBaseDir

@REM Get command-line arguments, handling Windows variants
if not "%OS%" == "Windows_NT" goto manualArgs
if "%PROCESSOR_ARCHITECTURE%"=="AMD64" goto manualArgs

setlocal enabledelayedexpansion
for /F "tokens=*" %%a in ('findstr /b /R "^:endInit" "%0"') do (
  set "DEBUG_OFFSET=!DEBUG_OFFSET!" & goto endInit
)
echo findstr not found
goto error

:manualArgs
@setlocal
setlocal enabledelayedexpansion

:parseArgs
if "x%~1" == "x" goto endInit
set "arg=%~1"
if "%arg:~0,2%"=="-D" (
  set "prop=-D%arg:~2%"
  shift
  goto parseArgs
) else (
  goto endInit
)

:endInit
set "MAVEN_CMD_LINE_ARGS=%*"

setlocal

if "%MAVEN_PROJECTBASEDIR%"=="" (
  set MAVEN_PROJECTBASEDIR=%CD%
)

@REM Provide a "standardized" way to retrieve the CLI args that will
@REM work with both Windows and non-Windows executions.
ENDLOCAL & SET MAVEN_CMD_LINE_ARGS=%MAVEN_CMD_LINE_ARGS%

if not exist "%MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.jar" (
  if "%MVNW_VERBOSE%" == "" (
    echo Couldn't find .mvn\wrapper\maven-wrapper.jar, downloading it ...
  )
  if not "%MVNW_REPOURL%" == "" (
    set "DOWNLOAD_URL=%MVNW_REPOURL%/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
  ) else (
    set "DOWNLOAD_URL=https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar"
  )

  powershell -Command "&{"^
    "$webclient = new-object System.Net.WebClient;"^
    "if (-not ([string]::IsNullOrEmpty('%MVNW_USERNAME%') -and [string]::IsNullOrEmpty('%MVNW_PASSWORD%'))) {"^
    "$webclient.Credentials = new-object System.Net.NetworkCredential('%MVNW_USERNAME%', '%MVNW_PASSWORD%');"^
    "}"^
    "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; $webclient.DownloadFile('%DOWNLOAD_URL%', '%MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.jar')"^
    "}"
  if "%ERRORLEVEL%" == "0" (
    if "%MVNW_VERBOSE%" == "" (
      echo Downloaded .mvn\wrapper\maven-wrapper.jar
    )
  ) else (
    echo Error downloading .mvn\wrapper\maven-wrapper.jar
    exit /b 1
  )
)
@REM End of extension

@REM Provide a "standardized" way to retrieve the CLI args that will
@REM work with both Windows and non-Windows executions.
set MAVEN_CMD_LINE_ARGS=%*

%MAVEN_JAVA_EXE% ^
  %JVM_CONFIG_MAVEN_PROPS% ^
  -classpath %MAVEN_BASEDIR%\.mvn\wrapper\maven-wrapper.jar ^
  "-Dmaven.home=%M2_HOME%" ^
  "-Dmaven.multiModuleProjectDirectory=%MAVEN_PROJECTBASEDIR%" ^
  "-Dmaven.projectBasedir=%MAVEN_PROJECTBASEDIR%" ^
  "-Dclassworlds.conf=%MAVEN_BASEDIR%\.mvn\wrapper\m2.conf" ^
  org.apache.maven.wrapper.BootstrapMainStarter ^
  %MAVEN_CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
set ERROR_CODE=1

:end
@endlocal & exit /b %ERROR_CODE%

