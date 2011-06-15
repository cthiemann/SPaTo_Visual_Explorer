
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

