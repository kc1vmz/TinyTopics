@echo off
rem Tiny Topics Installer
rem Copyright (c) 2026 John Rokicki KC1VMZ
rem 
rem This program is free software: you can redistribute it and/or modify
rem it under the terms of the GNU General Public License as published by
rem the Free Software Foundation, either version 3 of the License, or
rem (at your option) any later version.
rem 
rem This program is distributed in the hope that it will be useful,
rem but WITHOUT ANY WARRANTY; without even the implied warranty of
rem MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
rem GNU General Public License for more details.
rem 
rem You should have received a copy of the GNU General Public License
rem along with this program.  If not, see <https://www.gnu.org/licenses/>.
rem
rem http://www.kc1vmz.com
rem 

setlocal enabledelayedexpansion

echo Welcome to the Tiny Topics installer for Microsoft Windows.
echo Copyright (c) 2026 John Rokicki KC1VMZ - Licensed under GPL v3
echo https://www.kc1vmz.com
echo.
echo This installer expects pre-requisite software to be installed - Java 21+
echo Please install these pre-requisites prior to installation.
echo Java can be found at https://www.java.com/en/download/manual.jsp
echo.
pause
SET TT_INSTALL_DIR=%USERPROFILE%\TinyTopics
SET TT_TEMP_DIR=%TEMP%\TinyTopics
SET TT_DB_DIR=%TT_TEMP_DIR%\db
SET TT_VERSION=1.0.1
SET TT_PORT=8088
SET TT_ADMIN_PORT=8089

REM Prompt the user for input
SET /P "TT_VERSION=What version of Tiny Topics? (Default: %TT_VERSION%): "
SET /P "TT_INSTALL_DIR=Where should Tiny Topics be installed? (Default: %TT_INSTALL_DIR%): "
SET /P "TT_TEMP_DIR=Where should Tiny Topics database space be located? (Default: %TT_DB_DIR%): "
SET /P "TT_TEMP_DIR=Where should Tiny Topics temp space be located? (Default: %TT_TEMP_DIR%): "
SET /P "TT_PORT=What port should the HTTP service listen on? (Default: %TT_PORT%): "
SET /P "TT_ADMIN_PORT=What port should the admin HTTP service listen on? (Default: %TT_ADMIN_PORT%): "

pushd .

echo Checking for pre-requisite - Java 21
rem Step 1: Check if Java is installed at all
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo [ERROR] Java is not installed or not added to your system PATH.
    goto done
)
rem Step 2: Extract the major version number
rem This runs 'java -version', grabs the first line, and parses the version string.
for /f "tokens=3" %%g in ('java -version 2^>^&1 ^| findstr /i "version"') do (
    set "java_version=%%g"
)
rem Remove surrounding quotes from the version string (e.g., "17.0.1" -> 17.0.1)
set "java_version=%java_version:"=%"
rem Extract the main major version number before the first dot
for /f "delims=." %%v in ("!java_version!") do (
    set "major_version=%%v"
)
rem Handle legacy naming convention (e.g., Java 8 outputs "1.8", so major is 1)
if "!major_version!"=="1" (
    for /f "tokens=2 delims=." %%v in ("!java_version!") do (
        set "major_version=%%v"
    )
)
rem Step 3: Validate if version is 21 or greater
if !major_version! lss 21 (
    echo [ERROR] Java version is too old. Required: 21 or greater.
    goto done
)

echo Starting installation

echo Creating directories
mkdir %TT_INSTALL_DIR%
mkdir %TT_TEMP_DIR%
mkdir %TT_DB_DIR%

cd %TT_INSTALL_DIR%

echo Downloading built components for version %TT_VERSION% from Github
SET TT_SRC_URL_ROOT=https://github.com/kc1vmz/tinytopics/releases/download/v%TT_VERSION%
curl -L -o tinytopics-%TT_VERSION%.jar %TT_SRC_URL_ROOT%\tinytopics-%TT_VERSION%.jar
SET TT_SRC_URL_ROOT=

echo Creating startup script
echo @ECHO OFF>> tt_start.bat
echo pushd .>> tt_start.bat
echo cd %TT_INSTALL_DIR%>> tt_start.bat
echo REM Set Environment Variables>> tt_start.bat
echo SET TT_DB_DIR=%TT_DB_DIR%>> tt_start.bat
echo SET TT_TEMP_DIR=%TT_TEMP_DIR%>> tt_start.bat
echo SET SERVER_PORT=%TT_PORT%>> tt_start.bat
echo SET TT_READ_HTTP_PORT=%TT_PORT%>> tt_start.bat
echo SET TT_ADMIN_HTTP_PORT=%TT_ADMIN_PORT%>> tt_start.bat
echo SET TT_INSTALL_DIR=%TT_INSTALL_DIR%>> tt_start.bat
echo REM Start Processes>> tt_start.bat
echo start java -jar tinytopics-%TT_VERSION%.jar>> tt_start.bat
echo echo Tiny Topics started>> tt_start.bat
echo popd>> tt_start.bat

echo Finishing up
popd

SET TT_INSTALL_DIR=
SET TT_TEMP_DIR=
SET TT_VERSION=
SET TT_TEMP_DIR=

echo.
echo Installation complete.
echo.
echo Run 'tt_start.bat' to start Tiny Topics.
exit /b 0

:done
exit /b 1