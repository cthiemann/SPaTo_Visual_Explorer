/*
 * Copyright 2011 Christian Thiemann <christian@spato.net>
 * Developed at Northwestern University <http://rocs.northwestern.edu>
 *
 * This file is part of the SPaTo Visual Explorer (SPaTo).
 *
 * SPaTo is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SPaTo is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SPaTo.  If not, see <http://www.gnu.org/licenses/>.
 */

import javax.swing.JFrame;
import javax.swing.UIManager;

JFrame jframe = null;
String defaultTitle = null;
Object macmagic = null;  // holds a MacMagic instance if platform is MACOSX
Object winmagic = null;  // holds a WindowsMagic instance if platform is MACOSX

// called by setup()
public void setupPlatformMagic() {
  if (platform == MACOSX) macmagic = loadMagicClass("Mac");
  if (platform == WINDOWS) winmagic = loadMagicClass("Windows");
  if (platform == WINDOWS) setSystemLookAndFeel();
}

// called by guiFastUpdate()
public void updatePlatformMagic() {
  if (platform == MACOSX) {
    boolean showDoc = ((doc != null) && !fireworks);
    if (defaultTitle == null) defaultTitle = frame.getTitle();
    frame.setTitle(showDoc ? ((doc.getFile() != null) ? doc.getFile().getAbsolutePath() : doc.getName()) : defaultTitle);
    jframe.getRootPane().putClientProperty("Window.documentFile", showDoc ? doc.getFile() : null);
    jframe.getRootPane().putClientProperty("Window.documentModified", showDoc && doc.isModified());
  }
}

public Object loadMagicClass(String platform) {
  try {
    return Class.forName(platform + "Magic").
      getConstructor(new Class[] { SPaTo_Visual_Explorer.class }).
      newInstance(new Object[] { this });
  } catch (Exception e) {
    e.printStackTrace();
    return null;
  }
}

public boolean setSystemLookAndFeel() {
  try {
    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    return true;
  } catch (Exception e) {
    e.printStackTrace();
    return false;
  }
}

// overriding main() to create the applet within a JFrame (needed to do much of the Mac magic).
public static void main(String args[]) {
  SPaTo_Visual_Explorer applet = new SPaTo_Visual_Explorer();
  if (platform == MACOSX) {
    // Wrap the applet into a JFrame for maximum MacMagic!
    // setup various stuff (see PApplet.runSketch)
    applet.sketchPath = System.getProperty("user.dir");
    applet.args = (args.length > 1) ? subset(args, 1) : new String[0];
    for (int i = 0; i < args.length; i++) {
      if (args[i].startsWith(ARGS_SKETCH_FOLDER))
        applet.sketchPath = args[i].substring(args[i].indexOf('=') + 1);
    }
    // setup shiny JFrame
    applet.frame = applet.jframe = new JFrame();
    applet.jframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    applet.jframe.setLayout(new BorderLayout());
    applet.jframe.add(applet, BorderLayout.CENTER);
    // ... and go!
    applet.init();
    while (applet.defaultSize && !applet.finished)  // see PApplet.runSketch
      try { Thread.sleep(5); } catch (InterruptedException e) {}
    applet.jframe.pack();
    applet.jframe.setLocation((applet.screenWidth - applet.width)/2, (applet.screenHeight - applet.height)/2);
    if (applet.displayable())
      applet.jframe.setVisible(true);
    applet.requestFocus();
  } else {
    // pass the constructed applet to PApplet.runSketch
    PApplet.runSketch(new String[] { "SPaTo_Visual_Explorer" }, applet);
    if (platform == LINUX) try {
      String filename = System.getProperty("spato.app-dir") + "/lib/SPaTo_Visual_Explorer.png";
      applet.frame.setIconImage(Toolkit.getDefaultToolkit().createImage(filename));
    } catch (Exception e) { /* ignore */ }
  }
  // handle possible command-line arguments
  if (args.length > 1) {
    // wait for applet to initialize
    while (!applet.canHandleOpenFileEvents) try { Thread.sleep(25); } catch (Exception e) {}
    // parse command line for files
    File ff[] = new File[args.length - 1];
    for (int i = 1; i < args.length; i++)
      ff[i-1] = new File(args[i]);
    applet.handleDroppedFiles(ff);
  }
}