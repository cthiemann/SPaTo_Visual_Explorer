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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.event.KeyEvent;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.prefs.Preferences;
import javax.swing.JFrame;

import net.spato.sve.app.data.*;
import net.spato.sve.app.layout.*;
import net.spato.sve.app.platform.*;
import net.spato.sve.app.util.*;
import processing.core.PApplet;
import processing.pdf.PGraphicsPDF;
import processing.xml.XMLElement;
import de.cthiemann.tGUI.*;


@SuppressWarnings("serial")
public class SPaTo_Visual_Explorer extends PApplet {

  public static final String VERSION = "1.2.3_01";
  public static final String VERSION_DEBUG = "beta";
  public static final String VERSION_TIMESTAMP = "20110831T160000";
  public static final String VERSION_DATE = new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(parseISO8601(VERSION_TIMESTAMP));

  public ExecutorService worker = Executors.newSingleThreadExecutor();  // FIXME: public?
  public Preferences prefs = Preferences.userRoot().node("/net/spato/SPaTo_Visual_Explorer");  // FIXME: should not be public
  public boolean canHandleOpenFileEvents = false;  // indicates that GUI and workspace are ready to open files  // FIXME: get rid of this variable
  protected String cmdLineArgs[] = new String[0];

  float t, tt, dt;  // this frame's time, last frame's time, and delta between the two

  boolean resizeRequest = false;
  int resizeWidth, resizeHeight;

  PlatformMagic platformMagic = null;
  public DataTransferHandler dataTransferHandler = null;  // FIXME: public?
  public Workspace workspace = null;  // FIXME: public?
  public SPaToDocument doc = null;  // FIXME: get rid of this variable
  public SPaToGUI gui = null;  // FIXME: public?
  public TConsole console;  // FIXME: get rid of this variable

  public void setup() {
    randomSeed(second() + 60*minute() + 3600*hour());
    platformMagic = PlatformMagic.createInstance(this, cmdLineArgs);
    workspace = new Workspace(this);
    checkForUpdates(false);
    // setup window
    setupWindow();
    // setup GUI
    gui = new SPaToGUI(this);
    console = gui.console;
    gui.update();
    // go
    smooth();
    tt = millis()/1000.f;
    if (prefs.getBoolean("workspace.auto-recover", false))
      workspace.replaceWorkspace(XMLElement.parse(prefs.get("workspace", "<workspace />")));
    canHandleOpenFileEvents = true;  // FIXME
    platformMagic.ready();
  }

  public void setupWindow() {
    frame.setTitle("SPaTo Visual Explorer " + VERSION + ((VERSION_DEBUG.length() > 0) ? " (" + VERSION_DEBUG + ")" : ""));
    // set window size
    int w = prefs.getInt("window.width", 1280);  // technically, that's not the window size
    int h = prefs.getInt("window.height", 720);  // but the size of the PApplet (but what the heck...)
    if (w > screenWidth) { w = screenWidth; h = 9*w/16; }
    if (h > screenHeight) { h = round(0.9f*screenHeight); w = 16*h/9; }
    size(w, h);
    // set window location
    int x = prefs.getInt("window.posx", screenWidth/2 - w/2);
    int y = prefs.getInt("window.posy", screenHeight/2 - h/2);
    x = max(x, 0); y = max(y, 0);
    x = min(x, screenWidth - frame.getWidth());
    y = min(y, screenHeight - frame.getHeight());
    frame.setLocation(x, y);
    // set up event handlers
    frame.setResizable(true);
    for (java.awt.event.ComponentListener cl : getComponentListeners())
      if (cl.getClass().getName().startsWith("processing.core.PApplet"))
        removeComponentListener(cl);  // ouch, all of this is so hacked...
    addComponentListener(new java.awt.event.ComponentAdapter() {
      public void componentResized(java.awt.event.ComponentEvent e) {
        java.awt.Component comp = e.getComponent();
        // ensure minimum size
        int cw = comp.getWidth(), ch = comp.getHeight(), fw = frame.getWidth(), fh = frame.getHeight();
        int minWidth = 500, minHeight = 500;
        if ((cw < minWidth) || (ch < minHeight)) {
          if (cw < minWidth) { fw += (minWidth - cw); cw = minWidth; }
          if (ch < minHeight) { fh += (minHeight - ch); ch = minHeight; }
          comp.setPreferredSize(new Dimension(cw, ch));
          frame.pack();
        }
        // request applet resizing (duplicating code from PApplet)
        resizeRequest = true; resizeWidth = cw; resizeHeight = ch;
        // save new size in preferences
        prefs.putInt("window.width", cw); prefs.putInt("window.height", ch);
      }
    });
    frame.addComponentListener(new java.awt.event.ComponentAdapter() {
      public void componentMoved(java.awt.event.ComponentEvent e) {
        prefs.putInt("window.posx", e.getComponent().getLocation().x);
        prefs.putInt("window.posy", e.getComponent().getLocation().y);
      }
    });
    addMouseWheelListener(new java.awt.event.MouseWheelListener() {
      public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
        mouseWheel(evt.getWheelRotation()); } });
  }

  public void focusGained() { loop(); }
  public void focusLost() { if (!fireworks) noLoop(); }

  public void draw() {
    if (resizeRequest) { resizeRenderer(resizeWidth, resizeHeight); resizeRequest = false; }  // handle resize (duplicated from PApplet.run())
    t = millis()/1000.f; dt = t - tt; tt = t;
    if (fireworks && fw.alpha == 1) { fw.draw(); return; }
    // draw
    background(255);
    if (doc != null)
      doc.view.draw(g);
    gui.update(true);
    // fireworks transition drawing and finishing
    if (fireworks) {
      fw.draw();
      if (fw.finished && (fw.alpha == 0))
        disposeFireworks();
    }
  }

  boolean dragged = false;

  public void mouseReleased() {
    if (fireworks) return;
    if (dragged) { dragged = false; return; }
    if (mouseEvent.isConsumed()) return;
    if (doc == null) return;
    switch (mouseButton) {
      case LEFT:
        doc.view.setRootNode(doc.view.ih); break;
      case RIGHT:
        if ((doc.view.viewMode == SPaToView.VIEW_MAP) && gui.btnTom.isEnabled())
          gui.btnTom.handleMouseClicked();
        else if ((doc.view.viewMode == SPaToView.VIEW_TOM) && gui.btnMap.isEnabled())
          gui.btnMap.handleMouseClicked();
    }
  }

  public void mouseDragged() {
    if (fireworks) return;
    dragged = true;
    if (mouseEvent.isConsumed()) return;
    if (doc == null) return;
    if (mouseButton == LEFT) {
      doc.view.xoff[doc.view.viewMode] += mouseX - pmouseX;
      doc.view.yoff[doc.view.viewMode] += mouseY - pmouseY;
    } else if (mouseButton == RIGHT) {
      float refX = width/2 + doc.view.xoff[doc.view.viewMode];
      float refY = height/2 + doc.view.yoff[doc.view.viewMode];
      changeZoom(dist(refX, refY, mouseX, mouseY)/dist(refX, refY, pmouseX, pmouseY));
    }
  }

  boolean isAltDown = false;

  public void keyPressed() {
    if (fireworks) {
      if (keyCode == ESC) disposeFireworks();
      return;
    }
    isAltDown = keyEvent.isAltDown();
    if (keyEvent.isConsumed()) return;
    if (keyEvent.isControlDown() || keyEvent.isMetaDown()) {
      if (keyEvent.isAltDown()) {  // FIXME: All this could be handled by TPopupMenu
        switch (keyEvent.getKeyCode()) {
          case KeyEvent.VK_O: gui.actionPerformed("workspace##open"); return;
          case KeyEvent.VK_S: if (workspace.docs.size() > 0) gui.actionPerformed("workspace##save" + (keyEvent.isShiftDown() ? "As" : "")); return;
          case KeyEvent.VK_F: startFireworks(); return;
        }
        return;
      }
      switch (keyEvent.getKeyCode()) {
        case KeyEvent.VK_N: gui.actionPerformed("document##new"); return;
        case KeyEvent.VK_O: gui.actionPerformed("document##open"); return;
        case KeyEvent.VK_S: if (doc != null) gui.actionPerformed("document##save" + (keyEvent.isShiftDown() ? "As" : "")); return;
        case KeyEvent.VK_W: if (doc != null) gui.actionPerformed("document##close"); return;
      }
      return;
    }
    switch (key) {
      case '{': SPaToView.linkLineWidth = max(0.25f, SPaToView.linkLineWidth - 0.25f); console.logNote("Link line width is now " + SPaToView.linkLineWidth); return;
      case '}': SPaToView.linkLineWidth = min(1, SPaToView.linkLineWidth + 0.25f); console.logNote("Link line width is now " + SPaToView.linkLineWidth); return;
    }
    if (doc == null) return;
    if (keyEvent.isAltDown()) switch (keyCode) {
      case 91/*{*/: doc.view.nodeSizeFactor = max(0.05f, doc.view.nodeSizeFactor/1.5f); console.logNote("Node size factor is now " + doc.view.nodeSizeFactor); return;
      case 93/*}*/: doc.view.nodeSizeFactor = min(2.00f, doc.view.nodeSizeFactor*1.5f); console.logNote("Node size factor is now " + doc.view.nodeSizeFactor); return;
    }
    switch (key) {
      case '=': doc.view.zoom[doc.view.viewMode] = 1; doc.view.xoff[doc.view.viewMode] = doc.view.yoff[doc.view.viewMode] = 0; return;
      case '[': changeZoom(.5f); return;
      case ']': changeZoom(2); return;
      case 's': new Screenshot(this).showSettingsWindow(); return;
      case 'S': new Screenshot(this).save(); return;
      case 'r': // random walk
        //if (doc.view.hasLinks && (doc.view.links.index[doc.view.r].length > 0))
        //  doc.view.setRootNode(doc.view.links.index[doc.view.r][floor(random(doc.view.links.index[doc.view.r].length))]);
        //else
          doc.view.setRootNode(floor(random(doc.view.NN)));
        return;
      case '@': if (doc.view.hasNodes && (doc.view.ih != -1)) doc.view.nodes[doc.view.ih].showLabel = !doc.view.nodes[doc.view.ih].showLabel; return;
    }
    if (doc.getAlbum() != null) switch (key) {
      case 'A': doc.setSelectedSnapshot(doc.getAlbum(), -1, true); gui.updateAlbumControls(); return;
      case 'a': doc.setSelectedSnapshot(doc.getAlbum(), +1, true); gui.updateAlbumControls(); return;
    }
    if ((doc.getSelectedQuantity() != null) &&
        (doc.getSelectedSnapshot(doc.getSelectedQuantity()) != doc.getSelectedQuantity())) switch (key) {
      case '<': doc.setSelectedSnapshot(doc.getSelectedQuantity(), -10, true); return;
      case '>': doc.setSelectedSnapshot(doc.getSelectedQuantity(), +10, true); return;
    }
  }

  public void keyReleased() {
    isAltDown = keyEvent.isAltDown();
  }

  // Overriding exit() to prevent program being closed by ESC or Ctrl/Cmd-W. This is somewhat stupidly hacked...
  public void exit() {
    if ((keyEvent != null) &&
        ((key == KeyEvent.VK_ESCAPE) || ((keyEvent.getModifiers() == MENU_SHORTCUT) && (keyEvent.getKeyCode() == 'W'))))
      return;  // looks like exit() was called upon hitting these keys; but we don't want that
    super.exit();
  }

  public void mouseWheel(int delta) {
    if (fireworks) return;
    if ((gui.componentAtMouse == null) && (gui.componentMouseClicked == null))
      changeZoom((delta < 0) ? (1 - .05f*delta) : 1/(1 + .05f*delta));
  }

  public void changeZoom(float f) {
    if (doc == null) return;
    f = max(f, 1/doc.view.zoom[doc.view.viewMode]);  // do not allow zoom < 1
    f = min(f, 10/doc.view.zoom[doc.view.viewMode]);  // do not allow zoom > 10
    doc.view.zoom[doc.view.viewMode] *= f;
    doc.view.xoff[doc.view.viewMode] *= f;
    doc.view.yoff[doc.view.viewMode] *= f;
  }

  public void actionPerformed(String cmd) {
    gui.actionPerformed(cmd);
  }

  public void checkForUpdates(boolean force) {
    // on first start, ask user for permission to check for updates
    if (prefs.get("update.check", null) == null) {
      int res = javax.swing.JOptionPane.showConfirmDialog(frame,
        "Do you want me to automatically check for updates?", "SPaTo Updater",
        javax.swing.JOptionPane.YES_NO_OPTION);
      prefs.putBoolean("update.check", res == javax.swing.JOptionPane.YES_OPTION);
    }
    // check for updates if requested
    if (force) prefs.remove("update.skip");
    if (prefs.getBoolean("update.check", true) || force)
      new Updater(this, force).start();
  }


  public boolean fireworks = false;  // FIXME: public?
  Fireworks fw = null;

  public void startFireworks() {
    gui.setVisible(false);
    gui.setEnabled(false);
    fw = new Fireworks(this);
    fireworks = true;
    fw.setup();
  }

  public void disposeFireworks() {
    fireworks = false;
    fw = null;
    gui.setEnabled(true);
    gui.setVisible(true);
  }

  public static Date parseISO8601(String timestamp) {
    try { return new SimpleDateFormat("yyyyMMdd'T'HHmmss").parse(timestamp); }
    catch (Exception e) { e.printStackTrace(); return null; }
  }

  // FIXME: get rid of this function
  public Frame getParentFrame() {
    checkParentFrame();
    return parentFrame;
  }

  public JFrame jframe = null;  // FIXME: should not be public
  // overriding main() to create the applet within a JFrame (needed to do much of the Mac magic).
  public static void main(String args[]) {
    if (args.length > 0) {
      println("cmd line args:");
      for (int i = 0; i < args.length; i++)
        println("  [" + i + "] " + args[i]);
    }
    SPaTo_Visual_Explorer applet = new SPaTo_Visual_Explorer();
    applet.cmdLineArgs = args;
    if (platform == MACOSX) {
      // Wrap the applet into a JFrame for maximum MacMagic!
      // setup various stuff (see PApplet.runSketch)
      applet.sketchPath = System.getProperty("user.dir");
      applet.args = args;
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
      if (applet.displayable())
        applet.jframe.setVisible(true);
      applet.requestFocus();
    } else {
      // pass the constructed applet to PApplet.runSketch
      PApplet.runSketch(new String[] { "SPaTo_Visual_Explorer" }, applet);
    }
  }

}
