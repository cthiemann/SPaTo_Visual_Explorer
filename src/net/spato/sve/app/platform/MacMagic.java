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
import java.util.List;

import com.apple.eawt.AppEvent;
import com.apple.eawt.Application;
import com.apple.eawt.OpenFilesHandler;
import net.spato.sve.app.SPaTo_Visual_Explorer;


/**
 * This class deals with all the Mac-related goodies, specifically opening documents
 * when double-clicked in the Finder.  It should be instantiated using reflection,
 * so that the class does not get loaded if we are not running on a Mac platform
 * (otherwise the above import statements may cause things to break).
 *
 * http://developer.apple.com/library/mac/#documentation/Java/Conceptual/Java14Development/00-Intro/JavaDevelopment.html
 * http://developer.apple.com/library/mac/documentation/Java/Reference/JavaSE6_AppleExtensionsRef/api/com/apple/eawt/package-summary.html
 */
public class MacMagic implements OpenFilesHandler {

  protected SPaTo_Visual_Explorer app = null;

  public MacMagic(SPaTo_Visual_Explorer app) {
    this.app = app;
    Application.getApplication().setOpenFileHandler(this);
  }

  public void openFiles(AppEvent.OpenFilesEvent event) {
    // wait for application setup to be done (causes otherwise NullPointerExceptions otherwise)
    while (!app.canHandleOpenFileEvents) try { Thread.sleep(25); } catch (Exception e) {}
    // check for valid file type and load the files
    for (File f : event.getFiles()) {
      if (f.getName().endsWith(".spato"))
        app.workspace.openDocument(f);
      else if (f.getName().endsWith(".sve"))
        app.workspace.openWorkspace(f);
      else
        // FIXME: in-app error message?
        javax.swing.JOptionPane.showMessageDialog(null, "Unknown file type: " + f,
          "Open Files", javax.swing.JOptionPane.ERROR_MESSAGE);
    }
  }

}
