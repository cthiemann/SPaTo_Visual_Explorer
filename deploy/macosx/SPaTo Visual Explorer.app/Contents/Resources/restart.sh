#!/bin/sh

APP_PACKAGE="${0%/Contents/*}"

if [ "${APP_PACKAGE:0:1}" != "/" ]; then
  APP_PACKAGE="$PWD/$APP_PACKAGE"
fi

osascript <<EOF
  tell application "$APP_PACKAGE" to quit
  tell application "System Events"
    repeat until not (exists process "$APP_PACKAGE")
      delay 0.1
    end repeat
  end tell
  tell application "$APP_PACKAGE" to activate
EOF