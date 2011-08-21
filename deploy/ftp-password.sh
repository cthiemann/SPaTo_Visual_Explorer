#!/bin/sh
security -q find-internet-password -g -r "ftp " -a ftp1032083-spato -s wp255.webpack.hosteurope.de 2>&1 | grep password: | awk '{ print $2; }' | awk -F '"' '{ print $2; }'
