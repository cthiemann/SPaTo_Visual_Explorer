#!/bin/bash

if [ "$1" == "--install" ]; then
  DIR="$PWD"
  SCRIPT="$2"
  APPDIR="$(dirname "$SCRIPT")"
  cd "$APPDIR/lib"
  mkdir -p ~/.local/share/applications
  touch ~/.local/share/applications/mimeapps.list
  xdg-mime install spato-mime.xml
  xdg-icon-resource install --context mimetypes --size 128 application-x-spato.png
  xdg-icon-resource install --context mimetypes --size 128 application-x-spato-uncompressed.png
  xdg-icon-resource install --context mimetypes --size 128 application-x-spato-workspace.png
  xdg-icon-resource install --context apps --size 128 --novendor SPaTo_Visual_Explorer.png
  cp SPaTo_Visual_Explorer.desktop tmp.desktop
  echo "Exec=\"$SCRIPT\" %F" >> SPaTo_Visual_Explorer.desktop
  xdg-desktop-menu install --novendor SPaTo_Visual_Explorer.desktop
  mv tmp.desktop SPaTo_Visual_Explorer.desktop
  xdg-mime default SPaTo_Visual_Explorer.desktop application/x-spato
  xdg-mime default SPaTo_Visual_Explorer.desktop application/x-spato-uncompressed
  xdg-mime default SPaTo_Visual_Explorer.desktop application/x-spato-workspace
  cd "$DIR"
fi
