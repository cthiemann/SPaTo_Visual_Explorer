
COMPILE_CLASSPATH = .:../../application.macosx/SPaTo_Visual_Explorer.app/Contents/Resources/Java/core.jar:../../code/jna.jar:../windows/WinRun4J.jar

prelude: SPaTo_Prelude.class SPaTo_Prelude.jar

SPaTo_Prelude.jar: MaxMemDetector.class MemTest.class UpdateInstaller.class WindowsPrelude.class
	jar cvf $@ $^

%.class: SPaTo_Prelude.java MaxMemDetector.java MemTest.java UpdateInstaller.java WindowsPrelude.java
	javac -cp "$(COMPILE_CLASSPATH)" -source 1.5 -target 1.5 $^

run: SPaTo_Prelude.jar SPaTo_Prelude.class
	java $(JAVA_OPTS) -cp "." -Dapp-dir=../../application.macosx/SPaTo_Visual_Explorer.app SPaTo_Prelude
