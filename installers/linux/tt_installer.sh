#!/bin/bash
# Tiny Topics Installer
# Copyright (c) 2026 John Rokicki KC1VMZ
# 
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# http://www.kc1vmz.com
# 
TT_UPGRADE=N
TT_OS=$(uname)
if [ "$TT_OS" = "Linux" ]; then
  echo "Welcome to the Tiny Topics installer for Linux"
  echo "Copyright (c) 2026 John Rokicki KC1VMZ - Licensed under GPL v3"
  echo "https://www.kc1vmz.com"

  if grep -q TT_INSTALL_DIR /etc/environment; then
    echo "A previous version ofTiny Topics was detected."
    TT_UPGRADE=Y
    read -e -i $TT_UPGRADE -p "Do you wish to upgrade Tiny Topics?: " TT_UPGRADE
    if [[ "$TT_UPGRADE" =~ ^[Nn]$ ]]; then
      echo "Tiny Topics installation will now exit."
      exit 1
    fi
  fi

  # default environment variables
  TT_VERSION=1.0.1
  TT_INSTALL_DIR=~/tinytopics
  TT_DB_DIR=~/tinytopics/db
  TT_TEMP_DIR=~/tinytopics/tmp
  TT_READ_HTTP_PORT=8088
  TT_ADMIN_HTTP_PORT=8089
  TT_INSTALL_SERVICES=Y

  if [[ "$TT_INSTALL_SERVICES" =~ ^[Yy]$ ]]; then
    read -e -i $TT_VERSION -p "What version of Tiny Topics?: " TT_VERSION
    if [[ "$TT_UPGRADE" =~ ^[Nn]$ ]]; then
      read -e -i $TT_INSTALL_DIR -p "Where should Tiny Topics be installed?: " TT_INSTALL_DIR
      read -e -i $TT_DB_DIR -p "Where should Tiny Topics database space be located?: " TT_DB_DIR
      read -e -i $TT_TEMP_DIR -p "Where should Tiny Topics temp space be located?: " TT_TEMP_DIR
      read -e -i $TT_READ_HTTP_PORT -p "What port should the HTTP service listen on?: " TT_READ_HTTP_PORT
      read -e -i $TT_ADMIN_HTTP_PORT -p "What port should the admin HTTP service listen on?: " TT_ADMIN_HTTP_PORT
      read -e -i $TT_INSTALL_SERVICES -p "Do you want Tiny Topics to be configured as services and started at boot time (Y/n)?: " TT_INSTALL_SERVICES
    else
      TT_INSTALL_DIR=$(grep "^TT_INSTALL_DIR=" /etc/environment | sed 's/^TT_INSTALL_DIR=//' | tr -d '"')
      TT_INSTALL_DIR=$TT_INSTALL_DIR
    fi
  fi

  sudo cp /etc/environment /etc/environment.pre_tt_install

  JAVA_VER=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | awk -F '.' '{sub("^$", "0", $2); print $1$2}')
  if [ "$JAVA_VER" -ge 21 ]; then
    # fall through
    echo "Java 21+ verified"
  else
    echo "Java 21 or greater not installed - exiting."
    sudo rm /etc/environment.pre_tt_install
    exit 1
  fi

  TT_INSTALL_DIR=$TT_INSTALL_DIR
  if [[ "$TT_UPGRADE" =~ ^[Nn]$ ]]; then
    echo "TT_INSTALL_DIR=$TT_INSTALL_DIR" | sudo tee -a /etc/environment >  /dev/null
    echo "TT_DB_DIR=$TT_DB_DIR" | sudo tee -a /etc/environment >  /dev/null
    echo "TT_TEMP_DIR=$TT_TEMP_DIR" | sudo tee -a /etc/environment >  /dev/null
    echo "TT_READ_HTTP_PORT=$TT_READ_HTTP_PORT" | sudo tee -a /etc/environment >  /dev/null
    echo "TT_ADMIN_HTTP_PORT=$TT_ADMIN_HTTP_PORT" | sudo tee -a /etc/environment >  /dev/null
    mkdir $TT_INSTALL_DIR
    sudo mkdir $TT_TEMP_DIR
    sudo chown $USER $TT_TEMP_DIR
    sudo mkdir $TT_DB_DIR
    sudo chown $USER $TT_DB_DIR
  fi

  echo "Retrieving Tiny Topics binary"
  cd $TT_INSTALL_DIR
  TT_SRC_URL_ROOT=https://github.com/kc1vmz/tinytopics/releases/download/v$TT_VERSION
  wget -q -O tinytopics-$TT_VERSION.jar $TT_SRC_URL_ROOT/tinytopics-$TT_VERSION.jar
  TT_SRC_URL_ROOT=

  chmod +x tinytopics-$TT_VERSION.jar

  if [[ "$TT_UPGRADE" =~ ^[Yy]$ ]]; then
    if [ -n "/etc/systemd/system/tinytopics.service+x" ]; then
      TT_INSTALL_SERVICES=Y
    else
      TT_INSTALL_SERVICES=N
    fi
  fi

  if [[ "$TT_INSTALL_SERVICES" =~ ^[Yy]$ ]]; then
    if [[ "$TT_UPGRADE" =~ ^[Nn]$ ]]; then
      echo '[Unit]' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Description=Tiny Topics Server' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo '[Service]' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'User='$LOGNAME | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'WorkingDirectory='$TT_INSTALL_DIR | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Type=simple' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Restart=always' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'SuccessExitStatus=143' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'RemainAfterExit=yes' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'ExecStart=java -jar '$TT_INSTALL_DIR'/tinytopics-'$TT_VERSION'.jar' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Environment=TT_DB_DIR='$TT_DB_DIR | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Environment=TT_TEMP_DIR='$TT_TEMP_DIR | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Environment=TT_READ_HTTP_PORT='$TT_READ_HTTP_PORT | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Environment=SERVER_PORT='$TT_READ_HTTP_PORT | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Environment=TT_ADMIN_HTTP_PORT='$TT_ADMIN_HTTP_PORT | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'Environment=TT_INSTALL_DIR='$TT_INSTALL_DIR | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo '[Install]' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      echo 'WantedBy=multi-user.target' | sudo tee -a /etc/systemd/system/tinytopics.service >  /dev/null
      sudo systemctl daemon-reload
      sudo systemctl enable tinytopics
      sudo systemctl start tinytopics
      echo "Tiny Topics running as background service."
    else
      sudo systemctl stop tinytopics
      sudo sed -i 's/tinytopics-.*.jar/tinytopics-'$TT_VERSION'.jar/g' /etc/systemd/system/tinytopics.service
      sudo systemctl daemon-reload
      sudo systemctl start tinytopics
      echo "Tiny Topics restarted."
    fi
  else
    echo "Environment variables have been added to /etc/environment.  Make sure to reboot or run 'source /etc/environment' to add them into any new terminal."
    echo "You can run Tiny Topics using the  'java -jar $TT_INSTALL_DIR/tinytopics-$TT_VERSION.jar' command"
  fi

  # cleanup environment
  TT_INSTALL_DIR=
  TT_TEMP_DIR=
  TT_VERSION=
  TT_INSTALL_SERVICES=
  TT_DB_DIR=
  TT_READ_HTTP_PORT=
  TT_ADMIN_HTTP_PORT=
  TT_UPGRADE=
elif [ "$TT_OS" = "Darwin" ]; then
  echo "This is a Mac Machine - not yet supported"
  exit 1
else
  echo "Unsupported OS -" $TT_OS
  exit 1
fi

echo "Tiny Topics installation complete."