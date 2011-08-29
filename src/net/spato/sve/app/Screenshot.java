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

package net.spato.sve.app;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import processing.core.PApplet;
import processing.core.PGraphics;


public class Screenshot {

  protected SPaTo_Visual_Explorer app = null;
  protected SPaToView view = null;
  protected File dir = null;
  protected String filename = null;
  protected boolean savePDF, savePNG, saveLayout;
  protected int width, height;

  public Screenshot(SPaTo_Visual_Explorer app) {
    this.app = app;
    this.view = app.doc.view;
    // get output directory
    File dir = new File(System.getProperty("user.home"));  // default: save in user's home directory...
    if (new File(dir, "Desktop").exists()) dir = new File(dir, "Desktop");  // ... or better yet: on their desktop
    dir = new File(app.prefs.get("screenshot.directory", dir.getAbsolutePath()));  // or whatever they chose
    // generate file basename
    filename = "SVE_screenshot_" + (new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date()));
    filename = dir.getAbsolutePath() + File.separator + filename;
    // read screenshot settings
    savePDF = app.prefs.getBoolean("screenshot.save.pdf", true);
    savePNG = app.prefs.getBoolean("screenshot.save.png", false);
    saveLayout = app.prefs.getBoolean("screenshot.save.layout", false);
    width = app.prefs.getInt("screenshot.width", 0);
    if (width == 0) width = app.width;
    height = app.prefs.getInt("screenshot.height", 0);
    if (height == 0) height = app.height;
  }

  public void save() {
    SPaToView.fastNodes = false;  // always draw circles in screenshots
    if (savePDF) savePDF();
    if (savePNG) savePNG();
    if (saveLayout) saveLayout();
  }

  protected void savePDF() {
    app.console.logNote("Recording screenshot to " + filename + ".pdf");
    PGraphics g = app.createGraphics(width, height, PApplet.PDF, filename + ".pdf");
    g.beginDraw(); view.draw(g, false); g.endDraw();
    g.dispose();
  }

  protected void savePNG() {
    PGraphics g = app.createGraphics(width, height, PApplet.JAVA2D);
    g.beginDraw(); g.smooth(); view.draw(g, false); g.endDraw();
    g.save(filename + ".png");
    g.dispose();
    app.console.logNote("Saved screenshot to " + filename + ".png");
  }

  protected void saveLayout() {
    if (!savePDF && !savePNG) {
      // make sure node positions are correct -- FIXME: break up SPaToView.draw() into smaller functions
      PGraphics g = app.createGraphics(width, height, PApplet.JAVA2D);
      g.beginDraw(); view.draw(g, false); g.endDraw();
      g.dispose();
    }
    view.writeLayout(filename + "_layout.txt");
    app.console.logNote("Node layout written to " + filename + "_layout.txt");
  }

}
