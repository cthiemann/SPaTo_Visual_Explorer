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

package net.spato.sve.app.platform;

import java.io.File;
import java.util.LinkedList;

import net.spato.sve.app.SPaTo_Visual_Explorer;
import processing.core.PApplet;


public class PlatformMagic {

  protected SPaTo_Visual_Explorer app = null;
  protected boolean ready = false;
  protected LinkedList<File> fileQueue = new LinkedList<File>();

  protected PlatformMagic(SPaTo_Visual_Explorer app, String args[]) {
    this.app = app;
  }

  public static PlatformMagic createInstance(SPaTo_Visual_Explorer app, String args[]) {
    String classname = null;
    switch (PApplet.platform) {
      case PApplet.LINUX:   classname = "LinuxMagic"; break;
      case PApplet.MACOSX:  classname = "MacMagic"; break;
      case PApplet.WINDOWS: classname = "WindowsMagic"; break;
    }
    // instantiate appropriate platform magic class (we need to use reflection here to avoid
    // the classes being loaded on the wrong platform – otherwise we would run into problems
    // with platform-specific packages like com.apple.eawt or org.boris.winrun4j)
    if (classname != null) try {
      return (PlatformMagic)
        Class.forName("net.spato.sve.app.platform." + classname).
          getConstructor(new Class[] { SPaTo_Visual_Explorer.class, new String[0].getClass() }).
            newInstance(new Object[] { app, args });
    } catch (Exception e) {
      e.printStackTrace();
    }
    // return the default instance
    return new PlatformMagic(app, args);
  }

  public void ready() {
    this.ready = true;
    while (!fileQueue.isEmpty())
      openFile(fileQueue.poll());
  }

  public void update() {}

  public void bringToFront() {
    final SPaTo_Visual_Explorer app = this.app;
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        app.frame.setVisible(true);
        app.frame.setExtendedState(app.frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        app.frame.setAlwaysOnTop(true);
        app.frame.toFront();
        app.frame.requestFocus();
        app.requestFocusInWindow();
        app.frame.setAlwaysOnTop(false);
      }
    });
  }

  public void openFile(File f) {
    if (ready) {  // FIXME: thread-safety?
      // try to open file
      if (f.getName().endsWith(".spato"))
        app.workspace.openDocument(f);
      else if (f.getName().endsWith(".sve"))
        app.workspace.openWorkspace(f);
      else
        // FIXME: in-app error message?
        javax.swing.JOptionPane.showMessageDialog(null, "Unknown file type: " + f,
          "Open File", javax.swing.JOptionPane.ERROR_MESSAGE);
    } else {
      // queue request for later (will be processed once ready() gets called)
      fileQueue.add(f);
    }
  }

}
