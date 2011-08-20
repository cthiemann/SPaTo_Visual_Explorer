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

import javax.swing.UIManager;
import net.spato.sve.app.SPaToDocument;
import net.spato.sve.app.SPaTo_Visual_Explorer;
import processing.core.PApplet;


public class PlatformMagic {

  protected String defaultTitle = null;
  protected Object macmagic = null;  // holds a MacMagic instance if platform is MACOSX
  protected Object winmagic = null;  // holds a WindowsMagic instance if platform is WINDOWS

  public PlatformMagic() {
    if (PApplet.platform == PApplet.MACOSX)
      macmagic = loadMagicClass("Mac");
    if (PApplet.platform == PApplet.WINDOWS) {
      winmagic = loadMagicClass("Windows");
      setSystemLookAndFeel();
    }
  }

  public void update() {
    if (PApplet.platform == PApplet.MACOSX) {
      SPaToDocument doc = SPaTo_Visual_Explorer.INSTANCE.doc;
      boolean showDoc = ((doc != null) && !SPaTo_Visual_Explorer.INSTANCE.fireworks);
      if (defaultTitle == null) defaultTitle = SPaTo_Visual_Explorer.INSTANCE.frame.getTitle();
      SPaTo_Visual_Explorer.INSTANCE.frame.setTitle(showDoc ? ((doc.getFile() != null) ? doc.getFile().getAbsolutePath() : doc.getName()) : defaultTitle);
      SPaTo_Visual_Explorer.INSTANCE.jframe.getRootPane().putClientProperty("Window.documentFile", showDoc ? doc.getFile() : null);
      SPaTo_Visual_Explorer.INSTANCE.jframe.getRootPane().putClientProperty("Window.documentModified", showDoc && doc.isModified());
    }
  }

  protected Object loadMagicClass(String platform) {
    try {
      return Class.forName("net.spato.sve.app.platform." + platform + "Magic").
        getConstructor(new Class[] { SPaTo_Visual_Explorer.class }).
        newInstance(new Object[] { SPaTo_Visual_Explorer.INSTANCE });
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }

  protected boolean setSystemLookAndFeel() {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
      return true;
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
  }

}