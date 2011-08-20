
VERSION ?= dev


apps: app.win app.lin app.mac

packages:
	rm -f application.macosx/SPaTo_Visual_Explorer.dmg
	cd support/macosx; ./create_dmg.sh; cd ..
	mv application.macosx/SPaTo_Visual_Explorer.dmg SPaTo_Visual_Explorer_$(VERSION).dmg
	cd application.linux; tar cvzf ../SPaTo_Visual_Explorer_$(VERSION).tar.gz SPaTo_Visual_Explorer; cd ..
	cd application.windows; zip -r -9 ../SPaTo_Visual_Explorer_$(VERSION).zip SPaTo\ Visual\ Explorer; cd ..


APP_PACKAGE = application.macosx/SPaTo_Visual_Explorer.app

app.mac: application.macosx
	rm -rf $(APP_PACKAGE)/Contents/Resources/update
	rm -rf $(APP_PACKAGE)/Contents/Resources/Java/tmp
	cp -p support/macosx/Info.plist $(APP_PACKAGE)/Contents
	cp -p support/macosx/Info.plist $(APP_PACKAGE)/Contents/Info.plist.orig
	cp -p support/macosx/ApplicationUpdateWrapper $(APP_PACKAGE)/Contents/MacOS
	cp -p support/macosx/*icns $(APP_PACKAGE)/Contents/Resources
	rm -f $(APP_PACKAGE)/Contents/Resources/sketch.icns
	make -C support/prelude
	cp -p support/prelude/SPaTo_Prelude.{class,jar} $(APP_PACKAGE)/Contents/Resources/Java
	cp -p $(WIN_PACKAGE)/lib/SPaTo_Visual_Explorer.jar $(APP_PACKAGE)/Contents/Resources/Java


LIN_PACKAGE = application.linux/SPaTo_Visual_Explorer

app.lin: application.linux
	-cp application.linux/SPaTo_Visual_Explorer application.linux/SPaTo_Visual_Explorer.sh
	rm -rf $(LIN_PACKAGE)
	mkdir -p $(LIN_PACKAGE)/lib
	cp -p application.linux/lib/*.jar $(LIN_PACKAGE)/lib
	cp -p $(WIN_PACKAGE)/lib/SPaTo_Visual_Explorer.jar $(LIN_PACKAGE)/lib
	cp -p support/linux/SPaTo_Visual_Explorer $(LIN_PACKAGE)
	cp -p support/linux/config.sh $(LIN_PACKAGE)/lib
	cp -p support/linux/config.sh $(LIN_PACKAGE)/lib/config.sh.orig
	cp -p support/linux/SPaTo_Visual_Explorer.png $(LIN_PACKAGE)/lib
	cp -p support/linux/SPaTo_Visual_Explorer.desktop $(LIN_PACKAGE)/lib
	cp -p support/linux/spato-mime.xml $(LIN_PACKAGE)/lib
	cp -p support/linux/application-x-spato.png $(LIN_PACKAGE)/lib
	cp -p support/linux/application-x-spato-uncompressed.png $(LIN_PACKAGE)/lib
	cp -p support/linux/application-x-spato-workspace.png $(LIN_PACKAGE)/lib
	make -C support/prelude
	cp -p support/prelude/SPaTo_Prelude.{class,jar} $(LIN_PACKAGE)/lib


WIN_PACKAGE = application.windows/SPaTo\ Visual\ Explorer

app.win: application.windows
	rm -rf $(WIN_PACKAGE)
	mkdir -p $(WIN_PACKAGE)/lib
	cp -p application.windows/lib/*.jar $(WIN_PACKAGE)/lib
	cp -p support/windows/SPaTo\ Visual\ Explorer.{exe,ini} $(WIN_PACKAGE)
	cp -p support/windows/SPaTo_{Prelude,Visual_Explorer}.ini $(WIN_PACKAGE)/lib
	cp -p support/windows/SPaTo_Visual_Explorer.ini $(WIN_PACKAGE)/lib/SPaTo_Visual_Explorer.ini.orig
	cp -p support/windows/WinRun4J.jar $(WIN_PACKAGE)/lib
	cp -p support/windows/spato-16x16.png $(WIN_PACKAGE)/lib
	make -C support/prelude
	cp -p support/prelude/SPaTo_Prelude.{class,jar} $(WIN_PACKAGE)/lib
	make -C support/windows WindowsMagic.class
	jar uf $(WIN_PACKAGE)/lib/SPaTo_Visual_Explorer.jar -C support/windows WindowsMagic.class


PASSWORD = `security -q find-internet-password -g -r "ftp " -a ftp1032083-spato -s wp255.webpack.hosteurope.de 2>&1 | grep password: | awk '{ print $$2; }' | awk -F '"' '{ print $$2; }'`
FTPSITE = ftp://ftp1032083-spato:$(PASSWORD)@wp255.webpack.hosteurope.de/www/download

upload: application.linux/SPaTo_Visual_Explorer.tar.gz application.macosx/SPaTo_Visual_Explorer.dmg application.windows/SPaTo_Visual_Explorer.zip
	ftp -u $(FTPSITE)/SPaTo_Visual_Explorer_$(VERSION).tar.gz application.linux/SPaTo_Visual_Explorer.tar.gz
	ftp -u $(FTPSITE)/SPaTo_Visual_Explorer_$(VERSION).dmg application.macosx/SPaTo_Visual_Explorer.dmg
	ftp -u $(FTPSITE)/SPaTo_Visual_Explorer_$(VERSION).zip application.windows/SPaTo_Visual_Explorer.zip


#
# from support/Makefile
#

VERSION ?= dev
PASSWORD = `security -q find-internet-password -g -r "ftp " -a ftp1032083-spato -s wp255.webpack.hosteurope.de 2>&1 | grep password: | awk '{ print $$2; }' | awk -F '"' '{ print $$2; }'`
PRIVKEYPASS = `security -q find-generic-password -g -a spato.update 2>&1 | grep password: | awk '{ print $$2; }' | awk -F '"' '{ print $$2; }'`

update: SPaTo_Update_Builder.class
	make -C .. apps
	java -cp .:../application.macosx/SPaTo_Visual_Explorer.app/Contents/Resources/Java/SPaTo_Visual_Explorer.jar:../application.macosx/SPaTo_Visual_Explorer.app/Contents/Resources/Java/core.jar SPaTo_Update_Builder $(PRIVKEYPASS)

SPaTo_Update_Builder.class: SPaTo_Update_Builder.java
	javac -cp ../application.macosx/SPaTo_Visual_Explorer.app/Contents/Resources/Java/SPaTo_Visual_Explorer.jar:../application.macosx/SPaTo_Visual_Explorer.app/Contents/Resources/Java/core.jar $?

symlink:
	curl http://update.spato.net/symlink.php?latest=$(VERSION)\&magic=$(PASSWORD)


PROCESSING_SVN_ROOT = /Users/ct/Software/processing_r7148

updateApp:
	rm -rf Processing.app
	cp -a $(PROCESSING_SVN_ROOT)/build/macosx/work/Processing.app .
	rm -rf Processing.app/Contents/Resources/Java/{examples,reference,tools}
	rm -rf Processing.app/Contents/Resources/Java/libraries/{dxf,javascript,minim,net,opengl,serial,video}


