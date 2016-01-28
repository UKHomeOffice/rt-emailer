#!/bin/bash

/bin/chown -R rtemailer:rtemailer /user/share/rt-emailer
/bin/chown -R rtemailer:rtemailer /var/log/rt-emailer
/bin/chown -R rtemailer:rtemailer /var/run/rt-emailer
/bin/chmod 755 /etc/init.d/rt-emailer
