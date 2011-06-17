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

import processing.pdf.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.prefs.Preferences;
import java.text.SimpleDateFormat;

String version = "1.2.2";
String versionDebug = "beta";
String versionTimestamp = "20110604T220000";
String versionDate = new SimpleDateFormat("MMMM d, yyyy", Locale.US).format(parseISO8601(versionTimestamp));

ExecutorService worker = Executors.newSingleThreadExecutor();
Preferences prefs = Preferences.userRoot().node("/net/spato/SPaTo_Visual_Explorer");
boolean canHandleOpenFileEvents = false;  // indicates that GUI and workspace are ready to open files

float t, tt, dt;  // this frame's time, last frame's time, and delta between the two
boolean screenshot = false;  // if true, draw() will render one frame to PDF
boolean layoutshot = false;  // if true (and screenshot == true), draw() will output coordinates of the current node positions

boolean resizeRequest = false;
int resizeWidth, resizeHeight;

void setup() {
  setupPlatformMagic();
  checkForUpdates(false);
  // start caching PDF fonts in a new thread (otherwise the program might stall for up
  // to a minute or more when taking the first screenshot)
  Thread th = new Thread(new Runnable() {
    public void run() { PGraphicsPDF.listFonts(); }
  }, "PDF Font Caching");
  th.setPriority(Thread.MIN_PRIORITY);
  th.start();
  // setup window
  int w = prefs.getInt("window.width", 1280);
  int h = prefs.getInt("window.height", 720);
  if (w > screen.width) { w = screen.width; h = 9*w/16; }
  if (h > screen.height) { h = (int)round(0.9*screen.height); w = 16*h/9; }
  size(w, h);
  frame.setTitle("SPaTo Visual Explorer " + version + ((versionDebug.length() > 0) ? " (" + versionDebug + ")" : ""));
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
  addMouseWheelListener(new java.awt.event.MouseWheelListener() { 
    public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) { 
      mouseWheel(evt.getWheelRotation()); } });
  smooth();
  randomSeed(second() + 60*minute() + 3600*hour());
  // setup GUI
  guiSetup();
  guiUpdate();
  // go
  tt = millis()/1000.;
  if (prefs.getBoolean("workspace.auto-recover", false))
    replaceWorkspace(XMLElement.parse(prefs.get("workspace", "<workspace />")));
  canHandleOpenFileEvents = true;
}

void focusGained() { loop(); }
void focusLost() { if (!fireworks) noLoop(); }

void draw() {
  if (resizeRequest) { resizeRenderer(resizeWidth, resizeHeight); resizeRequest = false; }  // handle resize (duplicated from PApplet.run())
  t = millis()/1000.; dt = t - tt; tt = t;
  if (fireworks && fw.alpha == 1) { fw.draw(); return; }
  // prepare drawing
  if (screenshot) {
    String filename = "SVE_screenshot_" + (new SimpleDateFormat("yyyyMMdd'T'HHmmss").format(new Date())) + ".pdf";
    filename = System.getProperty("user.home") + ((platform == MACOSX) ? "/Desktop" : "") + File.separator + filename;
    if (layoutshot) doc.view.writeLayout(filename.substring(0, filename.length() - 4) + "_layout.txt");
    beginRecord(PDF, filename);
    fastNodes = false;  // always draw circles in screenshots
    console.logNote("Recording screenshot to " + filename);
  } else
    background(255);
  // draw
  if (doc != null)
    doc.view.draw();
  // post-drawing stuff
  if (screenshot) {
    endRecord();
    screenshot = false;
  }
  guiFastUpdate();
  if (searchMsg != null) {
    textFont(fnMedium); noStroke();
    fill(searchMsg.startsWith("E") ? 255 : 0, 0, 0);
    TransparentGUI.Point p = tfSearch.getLocationOnScreen();
    text(searchMsg.substring(1), p.x, p.y + tfSearch.getHeight() + 15);
  }
  // fireworks transition drawing and finishing
  if (fireworks) {
    fw.draw();
    if (fw.finished && (fw.alpha == 0))
      disposeFireworks();
  }
}

boolean dragged = false;

void mouseReleased() {
  if (fireworks) return;
  if (dragged) { dragged = false; return; }
  if (mouseEvent.isConsumed()) return;
  if (doc == null) return;
  switch (mouseButton) {
    case LEFT:
      doc.view.setRootNode(doc.view.ih); break;
    case RIGHT:
      if ((doc.view.viewMode == SVE2View.VIEW_MAP) && btnTom.isEnabled())
        btnTom.handleMouseClicked();
      else if ((doc.view.viewMode == SVE2View.VIEW_TOM) && btnMap.isEnabled())
        btnMap.handleMouseClicked();
  }
}

void mouseDragged() {
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

void keyPressed() {
  if (fireworks) {
    if (keyCode == ESC) disposeFireworks();
    return;
  }
  isAltDown = keyEvent.isAltDown();
  if (keyEvent.isConsumed()) return;
  if (keyEvent.isControlDown() || keyEvent.isMetaDown()) {
    if (keyEvent.isAltDown()) {  // FIXME: All this could be handled by TPopupMenu
      switch (keyEvent.getKeyCode()) {
        case KeyEvent.VK_O: actionPerformed("workspace##open"); return;
        case KeyEvent.VK_S: if (docs.size() > 0) actionPerformed("workspace##save" + (keyEvent.isShiftDown() ? "As" : "")); return;
        case KeyEvent.VK_F: startFireworks(); return;
      }
      return;
    }
    switch (keyEvent.getKeyCode()) {
      case KeyEvent.VK_N: actionPerformed("document##new"); return;
      case KeyEvent.VK_O: actionPerformed("document##open"); return;
      case KeyEvent.VK_S: if (doc != null) actionPerformed("document##save" + (keyEvent.isShiftDown() ? "As" : "")); return;
      case KeyEvent.VK_W: if (doc != null) actionPerformed("document##close"); return;
    }
    return;
  }
  switch (key) {
    case '{': linkLineWidth = max(0.25, linkLineWidth - 0.25); console.logNote("Link line width is now " + linkLineWidth); return;
    case '}': linkLineWidth = min(1, linkLineWidth + 0.25); console.logNote("Link line width is now " + linkLineWidth); return;
  }
  if (doc == null) return;
  if (keyEvent.isAltDown()) switch (keyCode) {
    case 91/*{*/: doc.view.nodeSizeFactor = max(0.05, doc.view.nodeSizeFactor/1.5); console.logNote("Node size factor is now " + doc.view.nodeSizeFactor); return;
    case 93/*}*/: doc.view.nodeSizeFactor = min(2.00, doc.view.nodeSizeFactor*1.5); console.logNote("Node size factor is now " + doc.view.nodeSizeFactor); return;
  }
  switch (key) {
    case '=': doc.view.zoom[doc.view.viewMode] = 1; doc.view.xoff[doc.view.viewMode] = doc.view.yoff[doc.view.viewMode] = 0; return;
    case '[': changeZoom(.5); return;
    case ']': changeZoom(2); return;
    case 's': screenshot = true; return;
    case 'S': screenshot = true; layoutshot = true; return;
    case 'r': // random walk
      //if (doc.view.hasLinks && (doc.view.links.index[doc.view.r].length > 0))
      //  doc.view.setRootNode(doc.view.links.index[doc.view.r][floor(random(doc.view.links.index[doc.view.r].length))]);
      //else
        doc.view.setRootNode(floor(random(doc.view.NN)));
      return;
    case '@': if (doc.view.hasNodes && (doc.view.ih != -1)) doc.view.nodes[doc.view.ih].showLabel = !doc.view.nodes[doc.view.ih].showLabel; return;
  }
  if (doc.getAlbum() != null) switch (key) {
    case 'A': doc.setSelectedSnapshot(doc.getAlbum(), -1, true); guiUpdateAlbumControls(); return;
    case 'a': doc.setSelectedSnapshot(doc.getAlbum(), +1, true); guiUpdateAlbumControls(); return;
  }
  if ((doc.getSelectedQuantity() != null) &&
      (doc.getSelectedSnapshot(doc.getSelectedQuantity()) != doc.getSelectedQuantity())) switch (key) {
    case '<': doc.setSelectedSnapshot(doc.getSelectedQuantity(), -10, true); return;
    case '>': doc.setSelectedSnapshot(doc.getSelectedQuantity(), +10, true); return;
  }
}

void keyReleased() {
  isAltDown = keyEvent.isAltDown();
}

// Overriding exit() to prevent program being closed by ESC or Ctrl/Cmd-W. This is somewhat stupidly hacked...
void exit() {
  if ((keyEvent != null) &&
      ((key == KeyEvent.VK_ESCAPE) || ((keyEvent.getModifiers() == MENU_SHORTCUT) && (keyEvent.getKeyCode() == 'W'))))
    return;  // looks like exit() was called upon hitting these keys; but we don't want that
  super.exit();
}

void mouseWheel(int delta) {
  if (fireworks) return;
  if ((gui.componentAtMouse == null) && (gui.componentMouseClicked == null))
    changeZoom((delta < 0) ? (1 - .05*delta) : 1/(1 + .05*delta));
}

void changeZoom(float f) {
  if (doc == null) return;
  f = max(f, 1/doc.view.zoom[doc.view.viewMode]);  // do not allow zoom < 1
  f = min(f, 10/doc.view.zoom[doc.view.viewMode]);  // do not allow zoom > 10
  doc.view.zoom[doc.view.viewMode] *= f;
  doc.view.xoff[doc.view.viewMode] *= f;
  doc.view.yoff[doc.view.viewMode] *= f;
}

void checkForUpdates(boolean force) {
  if (force) prefs.remove("update.skip");
  if (prefs.getBoolean("update.check", true) || force)
    new Updater(force).start();
}
