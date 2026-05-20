@echo off
REM =============================================================================
REM Nacos Stress Test Runner (Windows)
REM =============================================================================
setlocal enabledelayedexpansion

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
set "JAR_NAME=nacos-stresstest-1.0.0.jar"
set "JAR_PATH=%PROJECT_DIR%\target\%JAR_NAME%"

REM --- Find Java ---
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
        set "JAVA=%JAVA_HOME%\bin\java.exe"
        goto :check_version
    )
)

where java >nul 2>&1
if %errorlevel% equ 0 (
    set "JAVA=java"
    goto :check_version
)

echo ERROR: Java not found. Set JAVA_HOME or add java to PATH. >&2
exit /b 1

:check_version
REM --- Check Java version (minimum 11) ---
for /f "tokens=3" %%v in ('"%JAVA%" -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER_RAW=%%~v"
)
for /f "delims=." %%a in ("%JAVA_VER_RAW%") do set "JAVA_MAJOR=%%a"

if %JAVA_MAJOR% lss 11 (
    echo ERROR: Java 11+ required, found Java %JAVA_MAJOR%. >&2
    exit /b 1
)

REM --- Find JAR ---
if not exist "%JAR_PATH%" (
    echo ERROR: %JAR_NAME% not found at %JAR_PATH% >&2
    echo        Run 'mvn clean package' in %PROJECT_DIR% first. >&2
    exit /b 1
)

REM --- Run ---
echo Using Java %JAVA_MAJOR%: %JAVA%
echo Running: %JAR_PATH%
echo ---
"%JAVA%" -jar "%JAR_PATH%" %*
