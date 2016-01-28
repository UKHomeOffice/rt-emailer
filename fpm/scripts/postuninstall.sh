#!/bin/bash

if [ $1 = "remove" ]; then
  echo "deleting user data on uninstall"
  /usr/bin/getent passwd rtemailer > /dev/null && /usr/sbin/userdel rtemailer || /bin/true
  /usr/bin/getent group rtemailer > /dev/null && /usr/sbin/groupdel rtemailer || /bin/true
fi

exit 0
