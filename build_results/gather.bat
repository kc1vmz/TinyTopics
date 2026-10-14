@echo off
rd /s/q target
mkdir target
echo Gathering files for upload to Github

copy ..\README.md target
copy ..\installers\linux\tt_installer.sh target
copy ..\installers\linux\tt_uninstaller.sh target
copy ..\installers\windows\tt_installer.bat target
copy ..\target\tinytopics-1.0.1.jar target
