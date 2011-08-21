#!/bin/sh
security -q find-generic-password -g -a spato.update 2>&1 | grep password: | awk '{ print $2; }' | awk -F '"' '{ print $2; }'
