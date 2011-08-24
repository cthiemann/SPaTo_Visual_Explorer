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

import com.apple.eawt.AppEvent;
import com.apple.eawt.Application;
import com.apple.eawt.OpenFilesHandler;
import net.spato.sve.app.SPaTo_Visual_Explorer;
import net.spato.sve.app.SPaToDocument;


/**
 * This class deals with all the Mac-related goodies, specifically opening documents
 * when double-clicked in the Finder.  It should be instantiated using reflection,
 * so that the class does not get loaded if we are not running on a Mac platform
 * (otherwise the above import statements may cause things to break).
 *
 * http://developer.apple.com/library/mac/#documentation/Java/Conceptual/Java14Development/00-Intro/JavaDevelopment.html
 * http://developer.apple.com/library/mac/documentation/Java/Reference/JavaSE6_AppleExtensionsRef/api/com/apple/eawt/package-summary.html
 */
public class MacMagic extends PlatformMagic implements OpenFilesHandler {

  protected String defaultTitle = null;

  public MacMagic(SPaTo_Visual_Explorer app, String args[]) {
    super(app, args);
    System.out.println(">>> MacMagic class loaded");
    Application.getApplication().setOpenFileHandler(this);
  }

  public void update() {
    super.update();
    SPaToDocument doc = app.doc;
    boolean showDoc = ((doc != null) && !app.fireworks);
    if (defaultTitle == null) defaultTitle = app.frame.getTitle();
    app.frame.setTitle(showDoc ? ((doc.getFile() != null) ? doc.getFile().getAbsolutePath() : doc.getName()) : defaultTitle);
    app.jframe.getRootPane().putClientProperty("Window.documentFile", showDoc ? doc.getFile() : null);
    app.jframe.getRootPane().putClientProperty("Window.documentModified", showDoc && doc.isModified());
  }

  public void openFiles(AppEvent.OpenFilesEvent event) {
    for (File f : event.getFiles())
      super.openFile(f);
  }

}
