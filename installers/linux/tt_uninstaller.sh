#!/bin/bash
TT_OS=$(uname)
if [ "$TT_OS" = "Linux" ]; then
  echo "Welcome to the Tiny Topics UNinstaller for Linux"

  # default environment variables
  TT_CONFIRM_UNINSTALL=Y

  read -e -i $TT_CONFIRM_UNINSTALL -p "Do you want to uninstall Tiny Topics (Y/n)?: " TT_CONFIRM_UNINSTALL

  if [[ "$TT_CONFIRM_UNINSTALL" =~ ^[Yy]$ ]]; then
    # fall through
    echo Starting uninstall of Tiny Topics
  else
    #any other answer - exit
    exit 1
  fi

  echo Backing up /etc/environment
  sudo cp /etc/environment /etc/environment.pre_tt_uninstall

  echo Removing environment variables from /etc/environment
  sudo sed -i '/TT_INSTALL_DIR/d' /etc/environment
  sudo sed -i '/TT_TEMP_DIR/d' /etc/environment
  sudo sed -i '/TT_DB_DIR/d' /etc/environment

  echo Removing services
  sudo systemctl stop tinytopics
  sudo systemctl disable tinytopics
  sudo rm /etc/systemd/system/tinytopics.service
  sudo systemctl daemon-reload

  if [ -n "${TT_INSTALL_DIR+x}" ]; then
    echo Deleting Tiny Topics application
    sudo rm tinytopics*.jar
    echo Not deleting ${TT_INSTALL_DIR} - delete independently.
  else
    echo Tiny Topics installation directory unknown - delete independently.
  fi

  if [ -n "${TT_TEMP_DIR+x}" ]; then
    echo Not deleting ${TT_TEMP_DIR} - delete independently.
  else
    echo Tiny Topics temporary directory unknown - delete independently.
  fi

  if [ -n "${TT_DB_DIR+x}" ]; then
    echo Not deleting ${TT_DB_DIR} - delete independently.
  else
    echo Tiny Topics temporary directory unknown - delete independently.
  fi

  TT_CONFIRM_UNINSTALL=

elif [ "$TT_OS" = "Darwin" ]; then
  echo "This is a Mac Machine - not yet supported"
  exit 1
else
  echo "Unsupported OS -" $TT_OS
  exit 1
fi

echo "Tiny Topics uninstallation complete."