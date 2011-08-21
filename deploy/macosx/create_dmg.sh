#!/bin/sh

# http://stackoverflow.com/questions/96882/how-do-i-create-a-nice-looking-dmg-for-mac-os-x-using-command-line-tools

if [ -e build/macosx/SPaTo_Visual_Explorer.dmg ]; then exit; fi

echo "Creating build/macosx/SPaTo_Visual_Explorer.dmg ..."

# create read/write DMG
hdiutil create -srcfolder build/macosx -volname "SPaTo Visual Explorer" -fs HFS+ -fsargs "-c c=64,a=16,e=16" -format UDRW -size 10m build/tmp.dmg

# mount DMG
device=$(hdiutil attach -readwrite -noverify -noautoopen build/tmp.dmg | egrep '^/dev/' | sed 1q | awk '{print $1}')
sleep 2

# copy background picture
mkdir -p /Volumes/SPaTo\ Visual\ Explorer/.background
cp deploy/macosx/background.png /Volumes/SPaTo\ Visual\ Explorer/.background/background.png

# fix permissions
chmod -Rf go-w /Volumes/SPaTo\ Visual\ Explorer

# set visual appearance
echo '
   tell application "Finder"
     tell disk "'SPaTo Visual Explorer'"
           open
           set current view of container window to icon view
           set toolbar visible of container window to false
           set statusbar visible of container window to false
           set the bounds of container window to {100, 100, 782, 533}
           set theViewOptions to the icon view options of container window
           set arrangement of theViewOptions to not arranged
           set icon size of theViewOptions to 96
           set background picture of theViewOptions to file ".background:'background.png'"
           make new alias file at container window to POSIX file "/Applications" with properties {name:"Applications"}
           set position of item "'SPaTo Visual Explorer.app'" of container window to {220, 216}
           set position of item "Applications" of container window to {462, 216}
           close
           open
           update without registering applications
           delay 2
           eject
     end tell
   end tell
' | osascript

# compress
sync
sync
hdiutil detach ${device}
hdiutil convert build/tmp.dmg -format UDZO -imagekey zlib-level=9 -o build/macosx/SPaTo_Visual_Explorer.dmg
rm -f build/tmp.dmg

