#!/bin/bash

/usr/bin/getent group rtemailer > /dev/null || /usr/sbin/groupadd rtemailer
/usr/bin/getent passwd rtemailer > /dev/null || /usr/sbin/useradd -r -g rtemailer -s /bin/bash -c 'rtemailer user' rtemailer
