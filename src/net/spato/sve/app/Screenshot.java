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

import java.awt.event.ActionEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

import net.spato.sve.app.util.*;
import processing.core.PApplet;
import processing.core.PGraphics;
import de.cthiemann.tGUI.*;


public class Screenshot {

  protected SPaTo_Visual_Explorer app = null;
  protected SPaToView view = null;
  protected String basename = null;
  protected static SettingsWindow sw = null;  // make sure that only one settings window is visible at a time

  public Screenshot(SPaTo_Visual_Explorer app) {
    this.app = app;
    this.view = app.doc.view;
  }

  public void save() {
    SPaToView.fastNodes = false;  // always draw circles in screenshots
    if (getSavePDF()) savePDF();
    if (getSavePNG()) savePNG();
    if (getSaveLayout()) saveLayout();
  }

  public void showSettingsWindow() {
    if (sw == null)
      sw = new SettingsWindow();
    sw.requestFocus();
  }

  protected String getBasename() {
    if (basename == null) {
      basename = "SPaTo_screenshot_" + (new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date()));
      basename = getOutputDirectory().getAbsolutePath() + File.separator + basename;
    }
    return basename;
  }

  protected void savePDF() {
    app.console.logNote("Recording screenshot to " + getBasename() + ".pdf");
    PGraphics g = app.createGraphics(getWidth(), getHeight(), PApplet.PDF, getBasename() + ".pdf");
    g.beginDraw(); view.draw(g, false); g.endDraw();
    g.dispose();
  }

  protected void savePNG() {
    PGraphics g = app.createGraphics(getWidth(), getHeight(), PApplet.JAVA2D);
    g.beginDraw(); g.smooth(); view.draw(g, false); g.endDraw();
    g.save(getBasename() + ".png");
    g.dispose();
    app.console.logNote("Saved screenshot to " + getBasename() + ".png");
  }

  protected void saveLayout() {
    if (!getSavePDF() && !getSavePNG()) {
      // make sure node positions are correct -- FIXME: break up SPaToView.draw() into smaller functions
      PGraphics g = app.createGraphics(getWidth(), getHeight(), PApplet.JAVA2D);
      g.beginDraw(); view.draw(g, false); g.endDraw();
      g.dispose();
    }
    view.writeLayout(getBasename() + "_layout.txt");
    app.console.logNote("Node layout written to " + getBasename() + "_layout.txt");
  }

  public File getOutputDirectory() {
    File dir = new File(System.getProperty("user.home"));  // default: save in user's home directory...
    if (new File(dir, "Desktop").exists()) dir = new File(dir, "Desktop");  // ... or better yet: on their desktop
    return new File(app.prefs.get("screenshot.directory", dir.getAbsolutePath()));  // or whatever they chose
  }
  public void setOutputDirectory(File f) { app.prefs.put("screenshot.directory", f.getAbsolutePath()); }

  public boolean getSavePDF() { return app.prefs.getBoolean("screenshot.save.pdf", true); }
  public void setSavePDF(boolean b) { app.prefs.putBoolean("screenshot.save.pdf", b); }

  public boolean getSavePNG() { return app.prefs.getBoolean("screenshot.save.png", false); }
  public void setSavePNG(boolean b) { app.prefs.putBoolean("screenshot.save.png", b); }

  public boolean getSaveLayout() { return app.prefs.getBoolean("screenshot.save.layout", false); }
  public void setSaveLayout(boolean b) { app.prefs.putBoolean("screenshot.save.layout", b); }

  public int getWidth() { int w = app.prefs.getInt("screenshot.width", 0); return (w > 0) ? w : app.width; }
  public void setWidth(int w) { app.prefs.putInt("screenshot.width", w); }

  public int getHeight() { int h = app.prefs.getInt("screenshot.height", 0); return (h > 0) ? h : app.height; }
  public void setHeight(int h) { app.prefs.putInt("screenshot.height", h); }

  // FIXME: all the accessors should be static as they modify preferences rather than Screenshot members
  // FIXME: implement static SPaTo_Visual_Explorer.getInstance()
  // FIXME: things are getting rather messy in SettingsWindow

  public class SettingsWindow extends TFrame {

    protected TButton btnOutput;
    protected TCheckBox cbSavePDF, cbSavePNG, cbSaveLayout;
    protected NonSpammingTextField tfWidth, tfHeight;
    protected TLabel lblX;
    protected TCheckBox cbUseWindowSize;
    protected TButton btnSave, btnCancel;

    public class NonSpammingTextField extends TTextField {
      protected int numDigits;
      public NonSpammingTextField(TransparentGUI gui, String actionCommandPrefix, int numDigits) {
        super(gui, actionCommandPrefix);
        this.numDigits = numDigits;
        setActionEventHandler(this);
      }
      public TComponent.Dimension getMinimumSize() {
        TComponent.Dimension d = super.getMinimumSize();
        d.width = Screenshot.this.app.g.textWidth("0")*numDigits;
        return d;
      }
      public void actionPerformed(String cmd) { /* ignore */ }
      public void handleFocusLost() {
        super.handleFocusLost();
        SettingsWindow.this.actionPerformed(getActionCommand() + "##textChanged");
      }
    }

    public SettingsWindow() {
      super(app.gui, "Screenshot Settings");
      //
      cbSavePDF = gui.createCheckBox("PDF");
      cbSavePDF.setActionCommand("savePDF");
      cbSavePDF.setToolTip("Save the current visualization as a scalable vector image.");
      cbSavePNG = gui.createCheckBox("PNG");
      cbSavePNG.setActionCommand("savePNG");
      cbSavePNG.setToolTip("Save the current visualization as a bitmap (with transparent background).");
      cbSaveLayout = gui.createCheckBox("Layout");
      cbSaveLayout.setActionCommand("saveLayout");
      cbSaveLayout.setToolTip("Save the current node positions to a text file.");
      btnOutput = gui.createButton("");
      btnOutput.setActionCommand("output");
      btnOutput.setAlignment(TButton.ALIGN_LEFT);
      btnOutput.setToolTip("All selected output files will be saved to this directory.");
      add(gui.createCompactGroup(new TComponent[] {
        gui.createLabel("Save"), cbSavePDF, cbSavePNG, cbSaveLayout,
        gui.createLabel("to"), btnOutput },
        new TComponent.Spacing(10, 0)), TBorderLayout.NORTH);
      //
      tfWidth = new NonSpammingTextField(gui, "width", 4);
      lblX = gui.createLabel("x");
      tfHeight = new NonSpammingTextField(gui, "height", 4);
      cbUseWindowSize = gui.createCheckBox("use current window size");
      cbUseWindowSize.setActionCommand("winsize");
      add(gui.createCompactGroup(new TComponent[] {
        gui.createLabel("Size:"), tfWidth, lblX, tfHeight,
        gui.createLabel(" "), cbUseWindowSize },
        new TComponent.Spacing(10, 0)), TBorderLayout.NORTH);
      //
      TPanel p = gui.createPanel(new TBorderLayout()); p.setMargin(10, 0);
      btnSave = gui.createButton("Save");
      btnSave.setActionCommand("close##save");
      btnSave.setToolTip("Save selected output files.\n" +
        "Afterwards, you can use Shift-S to take another screenshot with the same settings.");
      TButton btnCancel = gui.createButton("Cancel");
      btnCancel.setActionCommand("close##cancel");
      p.add(gui.createPanel(new TComponent[] {
        gui.createCompactGroup(new TComponent[] { btnCancel }),
        gui.createCompactGroup(new TComponent[] { btnSave }) }), TBorderLayout.EAST);
      add(p, TBorderLayout.SOUTH);
      //
      setActionEventHandler(this);
      setLocation(50, 150);
      //
      gui.add(this);
      update();
    }

    public void update() {
      btnOutput.setText(Screenshot.this.getOutputDirectory().getAbsolutePath());
      cbSavePDF.setSelected(Screenshot.this.getSavePDF());
      cbSavePNG.setSelected(Screenshot.this.getSavePNG());
      cbSaveLayout.setSelected(Screenshot.this.getSaveLayout());
      cbUseWindowSize.setSelected((app.prefs.getInt("screenshot.width", 0) == 0) || (app.prefs.getInt("screenshot.height", 0) == 0));
      tfWidth.setText("" + Screenshot.this.getWidth());
      tfWidth.setEnabled(!cbUseWindowSize.isSelected());
      tfHeight.setText("" + Screenshot.this.getHeight());
      tfHeight.setEnabled(!cbUseWindowSize.isSelected());
      lblX.setEnabled(!cbUseWindowSize.isSelected());
      setSize(getPreferredSize());
    }

    public void actionPerformed(String cmd) {
      if (cmd.equals("savePDF"))
        Screenshot.this.setSavePDF(!Screenshot.this.getSavePDF());
      else if (cmd.equals("savePNG"))
        Screenshot.this.setSavePNG(!Screenshot.this.getSavePNG());
      else if (cmd.equals("saveLayout"))
        Screenshot.this.setSaveLayout(!Screenshot.this.getSaveLayout());
      else if (cmd.equals("output")) {
        File dir = FileDialogUtils.selectDirectory(app, Screenshot.this.getOutputDirectory());
        if ((dir != null) && dir.exists())
          Screenshot.this.setOutputDirectory(dir);
      } else if (cmd.startsWith("width##"))
        Screenshot.this.setWidth(PApplet.parseInt(tfWidth.getText(), Screenshot.this.getWidth()));
      else if (cmd.startsWith("height##"))
        Screenshot.this.setHeight(PApplet.parseInt(tfHeight.getText(), Screenshot.this.getHeight()));
      else if (cmd.equals("winsize")) {
        Screenshot.this.setWidth(cbUseWindowSize.isSelected() ? 0 : Screenshot.this.getWidth());
        Screenshot.this.setHeight(cbUseWindowSize.isSelected() ? 0 : Screenshot.this.getHeight());
        if (tfWidth.isFocusOwner()) gui.requestFocus(this);
        if (tfHeight.isFocusOwner()) gui.requestFocus(this);
      } else if (cmd.startsWith("close##")) {
        if (cmd.endsWith("##save")) Screenshot.this.save();
        Screenshot.this.app.gui.remove(this);
        Screenshot.this.sw = null;
      }
      update();
    }

    protected int lastWidth = Screenshot.this.app.width, lastHeight = Screenshot.this.app.height;

    public void draw(PGraphics g) {
      if (cbUseWindowSize.isSelected() && ((app.width != lastWidth) || (app.height != lastHeight))) {
        update();
        lastWidth = app.width;
        lastHeight = app.height;
      }
      super.draw(g);
    }

  }

}
