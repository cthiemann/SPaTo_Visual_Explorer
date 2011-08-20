package net.spato.sve.app;


import com.sun.jna.*;
import com.sun.jna.ptr.*;
import com.sun.jna.win32.*;
import edu.northwestern.rocs.jnmatlib.*;
import java.applet.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.Dimension;
import java.awt.dnd.*;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.Image;
import java.io.*;
import java.lang.reflect.Array;
import java.net.*;
import java.security.DigestInputStream;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.text.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.prefs.Preferences;
import java.util.regex.*;
import java.util.Vector;
import java.util.zip.*;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import javax.swing.*;
import javax.swing.filechooser.FileFilter;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.xhtmlrenderer.simple.*;
import processing.core.*;
import processing.pdf.*;
import processing.xml.*;
import tGUI.*;

public class SPaTo_Visual_Explorer extends PApplet {

public static SPaTo_Visual_Explorer INSTANCE = null;  // FIXME: this is probably quite evil...
private static final long serialVersionUID = 0l;

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

public void setup() {
  INSTANCE = this;
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
  if (w > screenWidth) { w = screenWidth; h = 9*w/16; }
  if (h > screenHeight) { h = round(0.9f*screenHeight); w = 16*h/9; }
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
  tt = millis()/1000.f;
  if (prefs.getBoolean("workspace.auto-recover", false))
    replaceWorkspace(XMLElement.parse(prefs.get("workspace", "<workspace />")));
  canHandleOpenFileEvents = true;
}

public void focusGained() { loop(); }
public void focusLost() { if (!fireworks) noLoop(); }

public void draw() {
  if (resizeRequest) { resizeRenderer(resizeWidth, resizeHeight); resizeRequest = false; }  // handle resize (duplicated from PApplet.run())
  t = millis()/1000.f; dt = t - tt; tt = t;
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

public void mouseReleased() {
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
    case '{': linkLineWidth = max(0.25f, linkLineWidth - 0.25f); console.logNote("Link line width is now " + linkLineWidth); return;
    case '}': linkLineWidth = min(1, linkLineWidth + 0.25f); console.logNote("Link line width is now " + linkLineWidth); return;
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

public void checkForUpdates(boolean force) {
  if (force) prefs.remove("update.skip");
  if (prefs.getBoolean("update.check", true) || force)
    new Updater(force).start();
}

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

class DataImportWizard extends TFrame {

  TConsole console;
  File file = null;
  String str = null;
  ExecutorService worker = null;
  BufferedReader reader = null;
  String lines[] = null;

  DataImportWizard() {
    super(SPaTo_Visual_Explorer.this.gui, "Data Import Wizard");
    setActionEventHandler(this);
    guiSetup();
    worker = Executors.newSingleThreadExecutor();
  }

  DataImportWizard(File file) {
    this(); setTitle(file.getName());
    this.file = file;
    try {
      reader = new BufferedReader(new FileReader(file));
    } catch (Exception e) {
      console.logError("Could not open file for reading: ", e);
      e.printStackTrace();
    }
  }

  DataImportWizard(String str) {
    this(); setTitle("Pasted/dropped text data");
    this.str = str;
    try {
      reader = new BufferedReader(new StringReader(str));
    } catch (Exception e) {
      console.logError("Could not read text data: ", e);
      e.printStackTrace();
    }
  }

  public void guiSetup() {
    setBounds(100, 100, 300, 200);
    console = gui.createConsole("import");
    console.setAlignment(TConsole.ALIGN_LEFT);
    console.setFancy(false);
    add(console, TBorderLayout.SOUTH);
  }

  public void start() {
    validate(); gui.add(this); gui.requestFocus(this);
    worker.submit(new Runnable() {
      public void run() {
        readData();  // or... read first line(s) and detect data type (e.g. GraphML etc)
        TabulatedData data = new TabulatedData(lines);
      }
    });
  }

  public void readData() {
    console.logProgress("Reading data").indeterminate();
    lines = new String[1024];
    int NL = 0;
    try {
      while ((lines[NL] = reader.readLine()) != null)
        if (++NL >= lines.length)
          lines = expand(lines);
    } catch (IOException e) {
      console.abortProgress("Error while reading data: ", e);
      e.printStackTrace();
      lines = null;
    }
    lines = subset(lines, 0, NL);
    console.finishProgress();
    console.logNote("Got " + NL + " lines of data");
  }

  public void finish() {
    worker.shutdown();
    gui.remove(this);
  }

  public void actionPerformed(String cmd) {
    //
  }

}
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




TWindow winDropStatus = null;

public void setupDropTarget() {
  winDropStatus = new TWindow(gui);
  winDropStatus.setFocusable(false);
  winDropStatus.setBackground(color(225, 225, 175, 225));
  winDropStatus.setPadding(15);
  //
  DropTargetListener dtl = new DropTargetAdapter() {
    public void dragEnter(DropTargetDragEvent event) {
//      showDropStatus(event.toString(), event.getTransferable());
      event.acceptDrag(DnDConstants.ACTION_COPY);
    }
    public void dragExit(DropTargetEvent event) {
//      hideDropStatus();
    }
    public void drop(DropTargetDropEvent event) {
      if (canAccept(event.getTransferable())) {
        event.acceptDrop(DnDConstants.ACTION_COPY);
        handleTransferable(event.getTransferable());
//        showDropStatus(event.toString(), event.getTransferable());
//        hideDropStatus();
        event.dropComplete(true);
      } else {
        event.rejectDrop();
      }
      if (!focused) redraw();  // go back into CPU-cycle saving mode, but erase drop status
    }
  };
  //
  new DropTarget(this, DnDConstants.ACTION_COPY, dtl, true);
}

public boolean canAccept(Transferable t) {
  return true;//t.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
}

public void showDropStatus(String str, Transferable t) {
  str += "\n\nFlavors:\n";
  for (DataFlavor f : t.getTransferDataFlavors())
    str += "    " + f + "\n";
  Reader reader = null;
  BufferedReader bufreader = null;
  try {
    DataFlavor flavor = DataFlavor.selectBestTextFlavor(t.getTransferDataFlavors());
    str += "\n\nFlavor: " + flavor + "\n";
    reader = flavor.getReaderForText(t);
    str += "\nReader: " + reader + "\n";
    bufreader = new BufferedReader(reader);
    str += "BufferedReader: " + bufreader + "\n\n";
    String line; int i = 0;
    while ((line = bufreader.readLine()) != null)
      str += "[" + i + "] " + line + "\n";
  } catch (Exception e) {
    e.printStackTrace();
  } finally {
    try { bufreader.close(); } catch (Exception e) {}
  }
  try {
    @SuppressWarnings("unchecked")  // FIXME: can we avoid "unchecked cast" here?
    java.util.List<File> ff = (java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor);
    str += "\n\nList: " + ff + "\n\n";
    for (Object f : ff)
      str += f + "\n";
  } catch (Exception e) {
    str += "  XXX  " + e.getMessage();
  }
  try {
    str += "\n\nString: ";
    String s = (String)t.getTransferData(DataFlavor.stringFlavor);
    str += s + "\n\n";
  } catch (Exception e) {
    str += "  XXX  " + e.getMessage();
  }
  //
  println("==========================================================");
  print(str);
  println("==========================================================");
  synchronized (gui) {
    winDropStatus.removeAll();
    winDropStatus.add(gui.createLabel(str));
    TComponent.Dimension d = winDropStatus.getPreferredSize();
    winDropStatus.setBounds(width/2 - d.width/2, height/2 - d.height/2, d.width, d.height);
    gui.add(winDropStatus);
  }
}

public void hideDropStatus() {
  gui.remove(winDropStatus);
}

@SuppressWarnings("unchecked")  // FIXME: can we avoid "unchecked cast" at ff = ...getTransferData(...)?
public boolean handleTransferable(Transferable t) {
  // handle dropped files
  if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
    File ff[];
    try {
      ff = ((java.util.List<File>)t.getTransferData(DataFlavor.javaFileListFlavor)).toArray(new File[0]);
    } catch (Exception e) {
      console.logError("Error while getting the names of the dropped/pasted files: ", e);
      e.printStackTrace();
      return false;
    }
    handleDroppedFiles(ff);
    return true;
  }
  // handle dropped or pasted text
  if (t.isDataFlavorSupported(DataFlavor.stringFlavor)) {
    String str = null;
    try {
      str = (String)t.getTransferData(DataFlavor.stringFlavor);
    } catch (Exception e) {
      console.logError("Error while retrieving the dropped/pasted text: ", e);
      e.printStackTrace();
      return false;
    }
    if (str.startsWith("file://")) {  // Linux reports file drops as lists of file URLs
      File ff[] = new File[0];
      for (String line : split(str, "\n")) {
        line = trim(line);
        if ((line.length() > 0) && line.startsWith("file://"))
          ff = (File[])append(ff, new File(line.substring(7)));
      }
      handleDroppedFiles(ff);
    } else
      new DataImportWizard(str).start();
    return true;
  }
  // don't know what to do
  return false;
}

public void handleDroppedFiles(File ff[]) {
  for (File f : ff) {
    if (f.getName().endsWith(".spato"))
      openDocument(f);
    else if (f.getName().endsWith(".mat"))
      ;//FIXME//new Thread(new MatFileImport(f)).start();
    else
      new DataImportWizard(f).start();
  }
}



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

boolean fireworks = false;
Fireworks fw = null;

public void startFireworks() {
  gui.setVisible(false);
  gui.setEnabled(false);
  fw = new Fireworks();
  fireworks = true;
  fw.setup();
}

public void disposeFireworks() {
  fireworks = false;
  fw = null;
  gui.setEnabled(true);
  gui.setVisible(true);
}

// How to make this class:
//  1) Copy'n'paste all PDE files from /Users/ct/Processing/Fireworks
//  2) Check setup() and out-comment inappropriate stuff (the size(...) should stay)
//  3) Replace "exit()" at the end of draw() with "alpha = tAlpha = 0;"
//  4) Out-comment "background(255);" in draw()
class Fireworks {

  Launcher ll[] = null;
  float t0, tt, t, dt, t0max, tFinish = Float.NaN;

  boolean started = false, finished = false;
  float scale = 4;
  float alpha = 0;
  float grav = -9.81f;

  int colors[] = new int[] {
    color(255,   0,   0),  // red
    color(255, 150,  20),  // orange
    color(255, 225,  20),  // yellow
    //color(160, 250, 160),  // light green
    color( 50, 255, 50),  // solid green
    //color( 15, 250, 160),  // cyan
    color(  0, 127, 255),  // light blue
    color(180, 130, 250),  // light purple
    color(200,  40, 220),  // solid purple
  };


//  int T = 0;
//  int millis() { return super.millis() + T*1000; }

  public void setup() {
    randomSeed(second() + 60*minute() + 3600*hour());
  //  size(1280, 720);
    size(800, 800*9/16);
//    frameRate(30);
//    smooth();
    t0 = tt = millis()/1000.f;
  //  T = 40;
    ll = new Launcher[] {
      new RocketLauncher(30, t0 + 2, 18),
      new TrailingRocketLauncher(10, t0 + 27, 8, 4, 75, true),
      new TrailingRocketLauncher(11, t0 + 35, 6, 3, 75, true),
      new TrailingRocketLauncher(11, t0 + 35, 6, 3, 75, false),
      new ShellLauncher(10, t0 + 36, 5, true, color(250)),//, 240, 200)),
      new ShellLauncher(20, t0 + 42, 20),
      new ShellLauncher(5, t0 + 47, 15, true),
      new ShellLauncher(30, t0 + 52, 10),
      new ShellLauncher(1, t0 + 61, 1, true),
    };
    t0max = 0; for (Launcher l : ll) t0max = max(t0max, l.t0);
  }

  public void draw() {
    t = millis()/1000.f; dt = t - tt; tt = t;
    if (alpha == 1)
      background(0);
    else {
      //background(255);
      noStroke(); fill(0, 255*alpha); rect(0, 0, width, height);
    }
    //if (frameCount % 30 == 0) println((t - t0) + "   " + frameRate);
    float tAlpha = alpha;
    if (!started) {
      if (alpha < .999f) tAlpha = 1; else { alpha = tAlpha = 1; started = true; }
    } else if (started && !finished) {
      noFill();
      pushMatrix();
      translate(width/2, height);
      scale(scale*width/1280, -scale*height/720);
      finished = (t > t0max);  // will be overriden if any of the launchers is still busy
      for (Launcher l : ll) {
        l.draw();
        if (!l.finished)
          finished = false;
      }
      popMatrix();
    } else {  // finished
      if (Float.isNaN(tFinish)) tFinish = t;
      if (t > tFinish + 5) tAlpha = 0;  // fade out five seconds after last shell burned out
      if (alpha < 0.001f) alpha = tAlpha = 0;
    }
    alpha += 3*(tAlpha - alpha)*min(dt, 1/3.f);
  }
  class Particle {
    float t0, x, y, vx, vy, ax, ay;  // creation time, position, velocity, acceleration
    float f = .1f;  // friction
    int c;  // color
    float alpha = 1;
    boolean solid = false, slowDecay = false;
    boolean sparkling = false, isSparkling = false;

    Particle(float t0, float x0, float y0, float vx0, float vy0, int c) {
      this(t0, x0, y0, vx0, vy0, 0, 0, c); }
    Particle(float t0, float x0, float y0, float vx0, float vy0, float ax0, float ay0, int c) {
      this.t0 = t0; x = x0; y = y0; vx = vx0; vy = vy0; ax = ax0; ay = ay0; this.c = c; }

    public void draw() {
      int cc = c;
      vx += ax*dt; vy += (ay + grav)*dt;
      vx += -f*vx*dt; vy += -f*vy*dt;
      x += vx*dt; y += vy*dt;
      if (!solid) {
        alpha -= (t - t0)*random(slowDecay ? .25f : 1)*dt;
        cc = lerpColor(color(255), c, min(1, (.5f + .5f*noise(x, y, t/10))*(t - t0)/.75f));
      }
      if (sparkling) {
        if (t - t0 > random(1, 2)) isSparkling = true;
        if (alpha < 0) alpha = random(0, 1);
        if (isSparkling && (random(1) < .25f)) cc = color(255);
        if (t - t0 > random(3.5f, 4.5f)) sparkling = false;
      }
      alpha = max(0, alpha);
      stroke(cc, 255*alpha);
      point(x, y);
    }
  }


  class Launcher {
    Shell rr[] = new Shell[0];
    boolean finished = true;
    float t0;

    Launcher(float t0) { this.t0 = t0; }

    public void draw() {
      finished = true;
      for (Shell r : rr) {
        if (t < r.t0) continue;
        r.draw();
        if (!r.finished)
          finished = false;
      }
    }

  }

  class ShellLauncher extends Launcher {
    ShellLauncher(int N, float t0, float deltat) { this(N, t0, deltat, false); }
    ShellLauncher(int N, float t0, float deltat, boolean sparkling) { this(N, t0, deltat, sparkling, 0); }
    ShellLauncher(int N, float t0, float deltat, boolean sparkling, int c) {
      super(t0);
      rr = new Shell[N];
      for (int i = 0; i < N; i++) {
        float x0 = 3*round((sparkling ? 5 : 10)*random(-1, 1)) + random(-1,1);
        float vx = (sparkling ? 5 : 10)*random(-1, 1);
        rr[i] = new Shell(random(t0, t0 + deltat), x0, 0, vx, random(45, 55), sparkling, c);
      }
    }
  }

  class RocketLauncher extends Launcher {
    RocketLauncher(int N, float t0, float deltat) {
      super(t0);
      rr = new Shell[N];
      for (int i = 0; i < N; i++)
        rr[i] = new Skyrocket(random(t0, t0 + deltat), random(-2, 2), 0,
                              random(-2, 2), random(15, 25), -1.5f*grav);
    }
  }

  class TrailingRocketLauncher extends Launcher {
    TrailingRocketLauncher(int N, float t0, float deltat, int Ni, float v0) {
      this(N, t0, deltat, Ni, v0, true); }
    TrailingRocketLauncher(int N, float t0, float deltat, int Ni, float v0, boolean ltr) {
      super(t0);
      rr = new Shell[N*Ni]; int index = 0;
      float dt = deltat/(Ni*(N + 1));  // time between individual rocket launches
      float dx = 10;
      float x0max = dx*(N-1)/2.f;
      for (int ii = 0; ii < Ni; ii++) {
        for (int i = 0; i < N; i++) {
          float rt0 = t0 + (ii*(N-1) + i)*dt;
          float x0 = dx*((ltr ? i : (N-1-i)) - (N-1)/2.f);
          if (Ni > 1) x0 += (ltr ? -1 : 1)*.25f*dx;
          float angle = PI/5 * x0/x0max;
          float v = v0*random(0.95f, 1.05f);
          rr[index] = new TrailingSkyrocket(rt0,  x0, 0, v*sin(angle), v*cos(angle), -.5f*grav);
          rr[index].t1 = .5f;
          index++;
        }
        ltr = !ltr;
      }
    }
  }
  class Shell extends Particle {
    boolean mortarLaunch = true;
    Particle pp[] = null;
    float t1, x0;
    boolean exploded = false;
    boolean finished = false;

    Shell(float t0, float x0, float y0, float vx0, float vy0) {
      this(t0, x0, y0, vx0, vy0, false); }
    Shell(float t0, float x0, float y0, float vx0, float vy0, boolean sparkling) {
      this(t0, x0, y0, vx0, vy0, sparkling, 0); }
    Shell(float t0, float x0, float y0, float vx0, float vy0, boolean sparkling, int c) {
      super(t0, x0, y0, vx0, vy0, color(20)); f /= 10; solid = true;
      t1 = random(3, 4);
      this.x0 = x0;
      createParticles(sparkling, c);
    }

    public void createParticles(boolean sparkling, int c) {
      pp = new Particle[floor(sparkling ? random(150, 200) : random(100, 150))];
      if (c == 0) c = colors[floor(random(0, colors.length))];
      float size = sparkling ? random(30, 31) : random(8, 10);
      for (int i = 0; i < pp.length; i++) {
        float theta = random(-PI, PI);
        float phi = random(0, PI);
        float nv = size*random(0.9f, 1.0f);
        float nvx = nv*sin(theta)*cos(phi);
        float nvy = nv*cos(theta);//sin(theta)*sin(phi);
  //          c = random(0, 1) < 0.8 ? colors[0] : color(255);
        pp[i] = new Particle(0, 0, -100, nvx, nvy, c);
        pp[i].sparkling = sparkling;
        if (sparkling) pp[i].f *= 5;
      }
    }

    public void draw() {
      // shell physics
      alpha = 1;
      super.draw();
      // trigger explosion
      if (!exploded && (t - t0 > t1)) {
        for (Particle p : pp) { p.t0 = t; p.x = x; p.y = y; p.vx += vx; p.vy += vy; }
        exploded = true;
      }
      // draw crown particles
      if (exploded) {
        finished = true;
        for (Particle p : pp) {
          p.draw();
          if (p.alpha > 1/255.f) finished = false;
        }
      }
      // draw launch
      if (mortarLaunch && (t - t0 < .25f)) {
        float nu = (1 - (t - t0)/.25f);
        stroke(lerpColor(color(255), color(255, 127, 0), nu), 255*nu);
        point(x0, 2/scale);
      }
    }
  }


  class Skyrocket extends Shell {
    Particle trail[] = new Particle[50]; int trailPointer = 0; float trailLast = 0;
    float a;

    class TrailParticle extends Particle {
      float tau = .1f;
      TrailParticle(float t0, float x0, float y0, float vx0, float vy0) {
        super(t0, x0, y0, vx0, vy0, color(255)); alpha = 0.3f; f *= 10; }
      public void draw() {
        if (t - t0 < 1*tau)      c = lerpColor(color(255, 255, 255), color(255, 255,   0), (t - t0)/tau);
        else if (t - t0 < 2*tau) c = lerpColor(color(255, 255,   0), color(255,   0,   0), (t - t0)/tau - 1);
        else if (t - t0 < 3*tau) c = lerpColor(color(255,   0,   0), color(127, 127, 127), (t - t0)/tau - 2);
        else                     c = color(127, 127, 127);
        alpha -= 0.2f*dt;
        if (random(0, 10) < t - t0) alpha -= 0.05f;
        alpha = max(0, min(1, alpha));
        vx += random(-1, 1)*20*dt;
            vx += ax*dt; vy += (ay + grav)*dt;
            vx += -f*vx*dt; vy += -f*vy*dt;
            x += vx*dt; y += vy*dt;
            stroke(c, 255*alpha); noFill();
            point(x, y);
      }
    }

    Skyrocket(float t0, float x0, float y0, float vx0, float vy0, float a) {
      super(t0, x0, y0, vx0, vy0); this.a = a;
      mortarLaunch = false;
      createParticles();
    }

    public void createParticles() {
      pp = new Particle[floor(random(50, 150))];
      int c = colors[floor(random(0, colors.length))];
      float size = random(5, 10);
      for (int i = 0; i < pp.length; i++) {
        float theta = random(-PI, PI);
        float phi = random(0, PI);
        float nv = size*random(0.5f, 1.5f);
        float nvx = nv*sin(theta)*cos(phi);
        float nvy = nv*cos(theta);//sin(theta)*sin(phi);
  //          c = random(0, 1) < 0.8 ? colors[0] : color(255);
        pp[i] = new Particle(0, 0, -100, nvx, nvy, c);
      }
    }

    public void generateTrail() {
      if ((a > 0) && (t > trailLast + .05f)) {
        trail[trailPointer] = new TrailParticle(t, x, y, vx, vy);
        trailPointer = (trailPointer + 1) % trail.length;
        trailLast = t;
      }
    }

    public void draw() {
      // draw tail
      for (int i = 0; i < trail.length; i++) {
        if (trail[i] == null) continue;
        trail[i].draw();
        if (trail[i].alpha < 1/255.f) trail[i] = null;
      }
      // propulsion
      if (!exploded) {
        if (t - t0 > t1 - 1) a = 0;
        float v = sqrt(vx*vx + vy*vy);
        ax = a*vx/v; ay = a*vy/v;
      }
      // generate trail
      generateTrail();
      // physics & draw
      super.draw();
    }
  }


  // does not explode; the trail is the effect
  class TrailingSkyrocket extends Skyrocket {
    float initTrailRate, trailRate = -1;

    TrailingSkyrocket(float t0, float x0, float y0, float vx0, float vy0, float a) {
      super(t0, x0, y0, vx0, vy0, a); f = 0;
      pp = new Particle[0];  // no crown
      trail = new Particle[500];
      trailRate = initTrailRate = 7*sqrt(sq(vx0) + sq(vy0));
    }

    public void generateTrail() {
      if (a == 0) trailRate = max(0, trailRate - .66f*initTrailRate*dt);
      int N = floor(trailRate*dt);
      if (random(1) < trailRate*dt - N) N++;
      for (int i = 0; i < N; i++) {
        float vx0 = 5*sqrt(sqrt(t - t0))*random(-1, 1);
        float vy0 = 5*sqrt(sqrt(t - t0))*random(-1, 1);
        trail[trailPointer] = new Particle(t, x - vx*dt*i/N, y - vy*dt*i/N, vx0, vy0, 0, -0.9f*grav,
          (random(1) < .25f) ? color(255) : color(255, 225, 64));
        trail[trailPointer].alpha = 0.3f;
        trail[trailPointer].slowDecay = true;
        trail[trailPointer].f *= 10;
        trailPointer = (trailPointer + 1) % trail.length;
        trailLast = t;
      }
    }

    public void draw() {
      super.draw();
      finished = (a == 0);
      for (Particle p : trail)
        if ((p != null) && (p.alpha > 1/255.f))
          finished = false;
    }

  }

}
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








HashMap<String,Object> layoutCache = new HashMap<String,Object>();  // FIXME: layout handling is still screwed up...

class Layout {
  Projection proj;
  Scaling scal;
  String projection, scaling, order;
  int NN = -1;
  int pred[][];  // input data
  float phi[][];  // posphi[r][i] is angular position of node i if r is root
  int r = -1;  // currently loaded slice

  class Cache {
    Vector<Vector<Integer>> children;
    int numTotalChildren[];
    float sortValue[];

    Cache(int r, float val[], boolean sortRecursively) {
      children = new Vector<Vector<Integer>>();
      for (int i = 0; i < NN; i++) children.add(new Vector<Integer>());
      numTotalChildren = new int[NN];
      if (val != null) sortValue = new float[NN];
      // setup children vector
      for (int i = 0; i < NN; i++)
        if (pred[r][i] >= 0)
          children.get(pred[r][i]).add(new Integer(i));  // i is a child of pred[r][i] (by definition)
      // setup numTotalChildren and sortValue vectors
      setupRecursively(r, val, sortRecursively);
      // sort children vector
      if (val != null)
        for (int i = 0; i < NN; i++)
          Collections.sort(children.get(i), new Comparator<Integer>() { public int compare(Integer o1, Integer o2) {
            float v1 = sortValue[o1.intValue()], v2 = sortValue[o2.intValue()];
            return (v1 < v2) ? -1 : (v1 > v2) ? +1 : 0; } });
    }

    public void setupRecursively(int j, float val[], boolean sortRecursively) {
      numTotalChildren[j] = children.get(j).size();
      if (val != null) sortValue[j] = val[j];
      float sortTmp = 0;
      for (int ii = 0; ii < children.get(j).size(); ii++) {
        int i = children.get(j).get(ii).intValue();
        setupRecursively(i, val, sortRecursively);
        numTotalChildren[j] += numTotalChildren[i];
        if (sortRecursively) sortTmp += sortValue[i];
      }
      if (sortRecursively) sortValue[j] += sortTmp/10000;
    }
  }

  Layout(int[][] pred, String spec) {
    this.pred = pred;
    NN = pred.length;
    parseSpecification(spec);
    setupProjection(projection);
    setupScaling(scaling);
    String hash = spec + "##" + pred.toString();
    if (!layoutCache.containsKey(hash)) {
      phi = new float[NN][NN];
      float sortData[] = getSortData();
      calculateLayout(sortData);
      layoutCache.put(hash, phi);
    }
    phi = (float[][])layoutCache.get(hash);
  }

  public String getSpecification() { return projection + "_" + scaling + (!order.equals("unsorted") ? "__" + order : ""); }

  public void setupProjection(String projection) {
    if (!TomProjectionFactory.canProduce(projection)) {
      console.logWarning("Unknown tomogram projection " + projection + ", using default");
      projection = TomProjectionFactory.getDefaultProduct();
    }
    proj = TomProjectionFactory.produce(projection, NN);
    this.projection = projection;
  }

  public void setupScaling(String scaling) { setupScaling(scaling, 1); }
  public void setupScaling(String scaling, float x0) {
    if (!ScalingFactory.canProduce(scaling)) {
      console.logWarning("Unknown scaling " + scaling + ", using default");
      scaling = ScalingFactory.getDefaultProduct();
    }
    scal = ScalingFactory.produce(scaling, NN);
    if (scaling.equals("log")) ((LogScaling)scal).x0 = x0;
    this.scaling = scaling;
  }

  public void updateProjection(int r, float D[][]) { proj.setPoints(scal.f(D[r]), phi[r]); this.r = r; }

  public void parseSpecification(String spec) {
    order = "unsorted";
    String pieces[] = split(spec, "__");
    if (pieces.length > 1) order = pieces[1];
    pieces = split(pieces[0], '_');
    projection = pieces[0];
    scaling = (pieces.length > 1) ? pieces[1] : "id";
  }

  public float[] getSortData() {
    return null;  // FIXME: re-implement this feature...
    /*if (order.equals("unsorted")) return null;
    try {
      String pieces[] = split(order, '_');
      SVE2View.Dataset ds = null;
      for (int d = 0; d < view.ND; d++)
        if (view.data[d].xml.getString("id").equals(pieces[0]))
          ds = view.data[d];
      if (ds == null) throw new Exception("Dataset " + pieces[0] + " not found");
      if (pieces.length < 2) throw new Exception("Quantity not specified");
      SVE2View.Data data = null;
      for (int q = 0; q < ds.NQ; q++)
        if (ds.data[q].xml.getString("id").equals(pieces[1]))
          data = ds.data[q];
      return data.data;
    } catch (Exception e) {
      console.logWarning("" + e.getMessage() + ", defaulting to unsorted layout");
      order = "unsorted";
      return null;
    }*/
  }

  public void calculateLayout(float sortData[]) {
    console.logProgress("Calculating layout " + getSpecification());
    for (int r = 0; r < NN; r++) {
      Cache cache = new Cache(r, sortData, order.endsWith("_r"));
      phi[r][r] = PI;  // we do this to move the root in the middle in LinearProjection
      calculateLayoutRecursively(cache, r, r, 0, 0, 2*PI);
      console.updateProgress(r, NN);
    }
    console.finishProgress();
  }

  // cache, root node, current layout node, tree depth of node j, min and max angles
  public void calculateLayoutRecursively(Cache cache, int r, int j, int d, float phimin, float phimax) {
    int sumNTC = cache.numTotalChildren[j], cumNTC = 0;  // sum of total children, cumulative sum
    Vector<Integer> I = cache.children.get(j);
    for (int ii = 0; ii < I.size(); ii++) {
      int i = I.get(ii).intValue();
      float iphimin = phimin + (phimax - phimin)*cumNTC/sumNTC;
      cumNTC += cache.numTotalChildren[i] + 1;  // sum of i's children plus i itself
      float iphimax = phimin + (phimax - phimin)*cumNTC/sumNTC;
      phi[r][i] = (iphimin + iphimax)/2;
      //doc.slices[r].d[i] = d;
      calculateLayoutRecursively(cache, r, i, d + 1, iphimin, iphimax);
    }
  }
}


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
    return Class.forName("net.spato.sve.app." + platform + "Magic").
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

static abstract class Projection {
  int N = 0;
  float x[] = null, y[] = null;
  float minx, maxx, miny, maxy;
  float cx, cy, w, h, sx, sy;  // center, and width and height of the used space, and scaling

  Projection() {}
  Projection(int N) { this.N = N; x = new float[N]; y = new float[N]; }

  public void setScalingToFitWithin(float width, float height) {
    // fit within dimensions with preserving aspect ratio of the data
    sx = sy = min((w == 0) ? 1e-5f : width/w, (h == 0) ? 1e-5f : height/h);
  }

  public void beginData() {
    minx = miny = Float.POSITIVE_INFINITY;
    maxx = maxy = Float.NEGATIVE_INFINITY;
    cx = cy = w = h = 0;
    sx = sy = 1;
  }

  public void setPoint(int i, float x, float y) {
    this.x[i] = x;
    this.y[i] = y;
    if (!Float.isInfinite(x) && !Float.isInfinite(y)) {
      minx = min(minx, x);
      maxx = max(maxx, x);
      miny = min(miny, y);
      maxy = max(maxy, y);
    }
  }

  public void endData() {
    cx = (minx + maxx)/2;
    cy = (miny + maxy)/2;
    w = maxx - minx;
    h = maxy - miny;
  }

  public void setPoints(float x[], float y[]) {
    beginData();
    for (int i = 0; i < N; i++)
      setPoint(i, x[i], y[i]);
    endData();
  }
}

static class MapProjectionFactory {
  static String[] productNames = { "LonLat", "LonLat Roll", "Albers" };

  public static boolean canProduce(String name) {
    if (name == null) return false;
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }
  public static String getDefaultProduct() { return productNames[0]; }

  public static Projection produce(String name, int N) {
    if (name == null) return null;
    if (name.equals("LonLat")) return new LonLatProjection(N);
    if (name.equals("LonLat Roll")) return new LonLatProjection(N, true);
    if (name.equals("Albers")) return new AlbersProjection(N);
    return null;
  }
}

static class TomProjectionFactory {
  static String[] productNames = { "linear", "radial" };

  public static boolean canProduce(String name) {
    if (name == null) return false;
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }
  public static String getDefaultProduct() { return productNames[0]; }

  public static Projection produce(String name, int N) {
    if (name == null) return null;
    if (name.equals("linear")) return new LinearProjection(N);
    if (name.equals("radial")) return new RadialProjection(N);
    return null;
  }
}

/**********************************************************************
 * Projections used with tomogram layouts
 **********************************************************************/

// Linear projection
static class LinearProjection extends Projection {
  LinearProjection(int NN) { super(NN); }
  public void setPoint(int i, float r, float phi) { super.setPoint(i, -(phi - PI), -r); }
  public void setScalingToFitWithin(float width, float height) {
    // aspect ratio is not important in LinearProjection
    sx = (w == 0) ? 1e-5f : width/w; sy = (h == 0) ? 1e-5f : height/h; }
}

// Polar coordinates to cartesian
static class RadialProjection extends Projection {
  RadialProjection(int NN) { super(NN); }
  public void setPoint(int i, float r, float phi) { super.setPoint(i, -r*cos(phi), -r*sin(phi)); }
  public void endData() { w = 2*max(abs(minx), abs(maxx)); h = 2*max(abs(miny), abs(maxy)); }
}

/**********************************************************************
 * Projections used with geographic maps
 **********************************************************************/

// Flat map projection
static class LonLatProjection extends Projection {
  boolean complete = false;  // whether the projection will always scale such that the whole longitude spectrum is shown
  LonLatProjection(int NN) { this(NN, false); }
  LonLatProjection(int NN, boolean complete) { super(NN); this.complete = complete; }
  public void setPoint(int i, float lat, float lon) { super.setPoint(i, lon, -lat); }
  public void endData() { super.endData(); if (complete) { cx = 0; w = 360; } }
}

// Albers projection
static class AlbersProjection extends Projection {
  // float phi0 = 38*PI/180, lam0 = -100*PI/180, phi1 = 23*PI/180, phi2 = 50*PI/180;  // for the continental US
  float lat[] = null, lon[] = null;  // temp arrays (need all lat/lon data to determine Albers parameters)

  AlbersProjection(int NN) { super(NN); }

  public void beginData() { super.beginData(); lat = new float[N]; lon = new float[N]; }

  // collect points in temporary arrays
  public void setPoint(int i, float lat, float lon) { this.lat[i] = lat; this.lon[i] = lon; }

  // determine parameters and project the points
  public void endData() {
    float minlat = min(lat), maxlat = max(lat), dlat = maxlat - minlat;
    float minlon = min(lon), maxlon = max(lon);
    float phi0 = (minlat + maxlat)/2*PI/180, lam0 = (minlon + maxlon)/2*PI/180;  // projection origin
    float phi1 = (minlat + dlat/6)*PI/180, phi2 = (maxlat - dlat/6)*PI/180;  // standard parallels
    float n = 0.5f*(sin(phi1) + sin(phi2));
    float C = cos(phi1)*cos(phi1) + 2*n*sin(phi1);
    float rho0 = sqrt(C - 2*n*sin(phi0))/n;
    float rho, theta;
    for (int i = 0; i < N; i++) {
      rho = sqrt(C - 2*n*sin(lat[i]*PI/180))/n;
      theta = n*(lon[i]*PI/180 - lam0);
      super.setPoint(i, rho*sin(theta), -(rho0 - rho*cos(theta)));
    }
    super.endData();
    lat = null; lon = null;
  }
}



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







int untitledCounter = 0;  // counter for uniquely numbering untitled documents

/*
 * Visual Explorer Document / Data Provider
 */

class SVE2Document {

  XMLElement xmlDocument = new XMLElement("document");
  SVE2View view = null;

  File file = null;  // corresponding file on disk
  String name = null;  // short name (basename of file or "<untitled%d>")
  boolean compressed = false;  // is this document in a zip archive or in a directory?
  ZipFile zipfile = null;
  ZipOutputStream zipout = null;

  boolean modified = false;

  HashMap<XMLElement,BinaryThing> blobCache = new HashMap<XMLElement,BinaryThing>();

  SVE2Document() { this(null); }
  SVE2Document(File file) { setFile(file); this.view = new SVE2View(this); }

  /*
   * Metadata Accessors
   */

  public File getFile() { return file; }
  public void setFile(File file) {
    // assume it's a zip archive if it's not a directory; create zip files by default
    setFile(file, (file == null) || !file.isDirectory());
  }
  public void setFile(File file, boolean compressed) {
    try { this.file = file.getCanonicalFile(); }  // try to normalize file path (remove "../" etc.)
    catch (Exception e) { this.file = file; }  // otherwise use original file
    this.compressed = compressed;
    this.name = null;  // reset name
  }

  public boolean isCompressed() { return compressed; }
  public void setCompressed(boolean compressed) { this.compressed = compressed; }
  public void setCompressed() { setCompressed(true); }

  public boolean isModified() { return modified; }

  public String getName() {
    if (name == null) {
      if (file != null) {
        name = file.getName();  // strip directory part
        if (name.endsWith(".spato")) name = name.substring(0, name.length()-6);  // strip .spato extension
      } else
        name = "<untitled" + (++untitledCounter) + ">";
    }
    return name;
  }

  public String getTitle() {
    XMLElement xmlTitle = xmlDocument.getChild("title");
    if (xmlTitle != null) {
      String title = xmlTitle.getContent();
      if (title != null)
        return trim(title);
    }
    return "Untitled Network";
  }

  public String getDescription() {
    XMLElement xmlDescription = xmlDocument.getChild("description");
    if (xmlDescription != null) {
      String desc = xmlDescription.getContent();
      if (desc != null)
        return join(trim(split(desc, '\n')), '\n');
    }
    return "";
  }

  /*
   * Data Accessors
   */

  public XMLElement getNodes() { return xmlDocument.getChild("nodes"); }
  public XMLElement getNode(int i) { return getChild("nodes/node[" + i + "]"); }
  public int getNodeCount() { return getChildren("nodes/node").length; }

  public XMLElement getAlbum() { return xmlDocument == null ? null : xmlDocument.getChild("album"); }  // FIXME: concurrency problems...
  public XMLElement[] getAlbums() { return xmlDocument.getChildren("album"); }
  public XMLElement getAlbum(String id) { return getChild("album[@id=" + id + "]"); }

  public XMLElement getLinks() { return xmlDocument.getChild("links"); }

  public XMLElement getSlices() { return xmlDocument.getChild("slices"); }

  public XMLElement[] getDatasets() { return xmlDocument.getChildren("dataset"); }
  public XMLElement getDataset(String id) { return getChild("dataset[@id=" + id + "]"); }

  public XMLElement[] getAllQuantities() { return getChildren("dataset/data"); }
  public XMLElement[] getQuantities() { return getChildren("dataset[@selected]/data"); }
  public XMLElement[] getQuantities(XMLElement xmlDataset) { return xmlDataset.getChildren("data"); }
  public XMLElement getQuantity(XMLElement xmlDataset, String id) {
    return getChild(xmlDataset, "data[@id=" + id + "]"); }

  public XMLElement[] getDistanceQuantities() { //return getChildren("dataset/data[@blobtype=float[N][N]]"); }
    XMLElement res[] = new XMLElement[0];
    for (XMLElement xmlData : getAllQuantities())
      if (getSelectedSnapshot(xmlData).getString("blobtype", "").equals("float[N][N]"))
        res = (XMLElement[])append(res, xmlData);
    return res;
  }

  public XMLElement getSelectedDataset() { return getChild("dataset[@selected]"); }
  public XMLElement getSelectedQuantity() { return getChild("dataset[@selected]/data[@selected]"); }
  public XMLElement getSelectedQuantity(XMLElement xmlDataset) {
    return getChild(xmlDataset, "data[@selected]"); }

  public void setSelectedDataset(XMLElement xmlDataset) {
    XMLElement xmlOldDataset = getSelectedDataset();
    if ((xmlDataset != null) && (xmlDataset == xmlOldDataset))
      return;  // nothing to do
    if (xmlOldDataset != null)  // unselect previously selected dataset
      xmlOldDataset.remove("selected");
    if (xmlDataset != null) {  // select new dataset and make sure some quantity in it is selected
      xmlDataset.setBoolean("selected", true);
      if (getSelectedQuantity(xmlDataset) == null)
        setSelectedQuantity(xmlDataset.getChild("data"));
    }
    view.setNodeColoringData(getSelectedQuantity());
  }

  public void setSelectedQuantity(XMLElement xmlData) {
    XMLElement xmlOldData = getSelectedQuantity();
    if ((xmlData != null) && (xmlData == xmlOldData))
      return;  // nothing to do
    if ((xmlOldData != null) && ((xmlData == null) || (xmlOldData.getParent() == xmlData.getParent())))
      xmlOldData.remove("selected");  // only unselect previous quantity if it's in the same dataset
    if (xmlData != null) {  // select new quantity and make sure the correct dataset is selected
      xmlData.setBoolean("selected", true);
      setSelectedDataset(xmlData.getParent());
    }
    view.setNodeColoringData(xmlData);
    guiUpdateNodeColoring();
  }

  public XMLElement getDistanceQuantity() { return getChild("dataset/data[@distmat]"); }

  public void setDistanceQuantity(XMLElement xmlData) {
    if ((xmlData != null) && (xmlData == view.xmlDistMat))
      return;  // nothing to do
    if (view.xmlDistMat != null) view.xmlDistMat.remove("distmat");
    view.setDistanceMatrix(xmlData);
    if (xmlData != null) xmlData.setBoolean("distmat", true);
    guiUpdateProjection();
  }

  /*
   * Snapshot Handling
   */

  public XMLElement getSelectedSnapshot(XMLElement xml) { return getSelectedSnapshot(xml, true); }
  public XMLElement getSelectedSnapshot(XMLElement xml, boolean recursive) {
    if ((xml == null) || (xml.getChild("snapshot") == null))
      return xml;  // the snapshots are not strong in this one...
    XMLElement result = null;
    String album = xml.getChild("snapshot").getString("album");
    if (album == null) {  // this is a snapshot series, which means it's easy to find the selected snapshot
      result = getChild(xml, "snapshot[@selected]");
      if (result == null) {  // looks like we're missing a 'selected' attribute...
        result = xml.getChild("snapshot");  // ... so select the first one as default
        if (result != null) xml.setBoolean("selected", true);
      }
    } else {  // ... otherwise we have to do some more work
      XMLElement xmlAlbum = getChild("album[@id=" + album + "]");
      if (xmlAlbum == null)  // this should not happen...
        console.logError("Could not find album \u2018" + album + "\u2019, referenced in " + xml.getName() +
          " \u201C" + xml.getString("name", xml.getString("id")) + "\u201D");
      else {
        XMLElement xmlSnapshot = getChild(xmlAlbum, "snapshot[@selected]");
        if (xmlSnapshot == null) {  // no snapshot is selected, try to select first one
          xmlSnapshot = xmlAlbum.getChild("snapshot");
          if (xmlSnapshot != null) xmlSnapshot.setBoolean("selected", true);
        }
        if (xmlSnapshot != null)
          result = getChild(xml, "snapshot[@id=" + xmlSnapshot.getString("id") + "]");
      }
    }
    return recursive ? getSelectedSnapshot(result) : result;
  }

  /** Returns the XML element containing the appropriate anonymous snapshot series (if any) of <code>xml</code>. */
  public XMLElement getSelectedSnapshotSeriesContainer(XMLElement xml) {
    while ((xml != null) && (xml.getChild("snapshot") != null) && (xml.getChild("snapshot").getString("album") != null))
      xml = getSelectedSnapshot(xml, false);
    return ((xml == null) || (xml.getChild("snapshot") == null)) ? null : xml;
  }

  /** Returns the index of the currently selected snapshot in an album or an anonymous snapshot series. */
  public int getSelectedSnapshotIndex(XMLElement xml) {
    if (xml == null) return -1;
    if (!xml.getName().equals("album"))
      xml = getSelectedSnapshotSeriesContainer(xml);
    XMLElement snapshots[] = xml.getChildren("snapshot");
    if (snapshots == null) return -1;
    for (int i = 0; i < snapshots.length; i++)
      if (snapshots[i].getBoolean("selected"))
        return i;
    if (snapshots.length > 0) {  // no snapshot marked as selected?
      snapshots[0].setBoolean("selected", true);  // then mark the first one
      return 0;
    }
    return -1;
  }

  public void setSelectedSnapshot(XMLElement xml, int index) { setSelectedSnapshot(xml, index, false); }
  public void setSelectedSnapshot(XMLElement xml, int index, boolean relative) {
    boolean isAlbum = xml.getName().equals("album");
    if (!isAlbum)
      xml = getSelectedSnapshotSeriesContainer(xml);
    // find currently selected snapshot
    XMLElement snapshots[] = xml.getChildren("snapshot");
    if (snapshots == null) return;  // should not happen
    int selectedIndex = 0;
    for (int i = snapshots.length - 1; i >= 0; i--) {
      if (snapshots[i].getBoolean("selected")) selectedIndex = i;
      snapshots[i].remove("selected");
    }
    // update currently selected snapshot
    selectedIndex = relative ? selectedIndex + index : index;
    while (selectedIndex < 0) selectedIndex += snapshots.length;
    while (selectedIndex >= snapshots.length) selectedIndex -= snapshots.length;
    snapshots[selectedIndex].setBoolean("selected", true);
    // update view and GUI
    if (isAlbum || (xml == getLinks())) { view.setLinks(getLinks()); }
    if (isAlbum || (xml == getSlices())) {
      view.setSlices(getSlices()); view.setTomLayout();
      view.setDistanceMatrix(getDistanceQuantity());
      /* FIXME: layout handling is bad... */ }
    if (isAlbum || (xml == getSelectedQuantity())) { view.setNodeColoringData(getSelectedQuantity()); guiUpdateNodeColoring(); }
    if (isAlbum || (xml == getDistanceQuantity())) { view.setDistanceMatrix(getDistanceQuantity()); guiUpdateProjection(); }
  }

  public XMLElement getColormap(XMLElement xml) {
    xml = getSelectedSnapshot(xml);
    while ((xml != null) && (xml.getChild("colormap") == null) && (xml.getName().equals("snapshot")))
      xml = xml.getParent();
    return (xml != null) ? xml.getChild("colormap") : null;
  }

  /*
   * Binary Cache Accessors
   */

  public BinaryThing getBlob(XMLElement xml) {
    xml = getSelectedSnapshot(xml);
    if (xml == null) return null;
    BinaryThing blob = null;
    if (!blobCache.containsKey(xml)) {
      String xmlPretty = xml.getString("name", "");
      if (xmlPretty.length() > 0) xmlPretty = " \u201C" + xmlPretty + "\u201D";
      xmlPretty = xml.getName() + xmlPretty;
      String name = xml.getString("blob");
      InputStream stream = null;
      try {
        TConsole.Message msg = console.logProgress((name != null)
          ? "Loading " + xmlPretty + " from blob " + name
          : "Parsing " + xmlPretty);
        if (name != null) {
          if ((stream = createDocPartInput("blobs" + File.separator + name)) != null)
            blob = BinaryThing.loadFromStream(new DataInputStream(stream), msg);
        } else
          blob = BinaryThing.parseFromXML(xml, msg);
        console.finishProgress();
      } catch (Exception e) {
        console.abortProgress("Error: ", e);
      }
      blobCache.put(xml, blob);
    }
    blob = blobCache.get(xml);
    if (blob != null)
      xml.setString("blobtype", getBlobType(blob));
    return blob;
  }

  /* This is used to ensure a set of blobs is loaded. */
  public void loadBlobs(XMLElement xml) { loadBlobs(new XMLElement[] { xml }); }
  public void loadBlobs(XMLElement xml[]) {
    if (xml == null) return;
    for (int i = 0; i < xml.length; i++) {
      XMLElement snapshots[] = xml[i].getChildren("snapshot");
      if ((snapshots != null) && (snapshots.length > 0))
        loadBlobs(snapshots);  // load all snapshots (xml[i] holds no valid data)
      else
        getBlob(xml[i]);  // getBlob will load/parse the data if not already cached
    }
  }

  public void setBlob(XMLElement xml, Object blob) { setBlob(xml, blob, false); }
  public void setBlob(XMLElement xml, Object blob, boolean persistent) {
    if ((xml == null) || (blob == null)) return;
    BinaryThing bt = new BinaryThing(blob);
    blobCache.put(xml, bt);
    xml.setString("blobtype", getBlobType(bt));
    if (persistent) {
      String blobname = xml.getString("id", generateID(xml.getString("label")));
      XMLElement tmp = xml;
      while ((tmp != null) && (tmp.getName().equals("snapshot")))
        blobname = (tmp = tmp.getParent()).getString("id", generateID()) + "_" + blobname;
      xml.setString("blob", blobname);
    } else
      xml.remove("blob");
  }

  /* This functions removes all stuff from the xml element that can be reproduced from the elements blob. */
  // FIXME: this is not used at the moment; offer a choiceQuantity context menu item to call this
  public void stripXMLData(XMLElement xml) {
    XMLElement child = null;
    if (xml.getName().equals("slices"))
      while ((child = xml.getChild("slice")) != null)
        xml.removeChild(child);
    if (xml.getName().equals("data"))
      while ((child = xml.getChild("values")) != null)
        xml.removeChild(child);
    // FIXME: handle links and snapshots
  }

  /* This returns a short description of the blob type/shape. */
  public String getBlobType(BinaryThing blob) {
    String s = blob.toString();
    s = s.replaceAll("([^0-9])" + getNodeCount() + "([^0-9])", "$1N$2");
    s = s.replaceAll("([^0-9])[23456789][0-9]*([^0-9])", "$1M$2");  // FIXME: replaces M for any number
    return s;
  }

  /*
   * Document Modification
   */

  public void addDataset(XMLElement xmlDataset) {
    xmlDocument.removeChild(xmlDataset);  // avoid having it in there twice
    if (xmlDataset.getString("id") == null)
      xmlDataset.setString("id", generateID());
    xmlDocument.addChild(xmlDataset);
    guiUpdateNodeColoring();
  }

  public void removeDataset(XMLElement xmlDataset) {
    for (XMLElement xmlData : xmlDataset.getChildren("data"))
      removeQuantity(xmlData);
    if (xmlDataset.getBoolean("selected"))
      setSelectedDataset((XMLElement)previousOrNext(getDatasets(), xmlDataset));
    xmlDataset.getParent().removeChild(xmlDataset);
    guiUpdateNodeColoring();
  }

  public void addQuantity(XMLElement xmlDataset, XMLElement xmlData) { addQuantity(xmlDataset, xmlData, null); }
  public void addQuantity(XMLElement xmlDataset, XMLElement xmlData, Object blob) {
    xmlDataset.removeChild(xmlData);  // make sure we won't add an already existing quantity
    if (xmlData.getString("id") == null) xmlData.setString("id", generateID());
    xmlDataset.addChild(xmlData);
    if (blob != null) doc.setBlob(xmlData, blob, true);
    guiUpdateNodeColoring();
    guiUpdateProjection();
  }

  public void removeQuantity(XMLElement xmlData) {
    if (xmlData == view.xmlDistMat)
      view.setDistanceMatrix((XMLElement)previousOrNext(getDistanceQuantities(), xmlData));
    xmlData.getParent().removeChild(xmlData);
    if (xmlData.getBoolean("selected"))
      setSelectedQuantity((XMLElement)previousOrNext(getQuantities(), xmlData));
  }

  /*
   * Loading/Saving Functions
   */

  public Runnable newLoadingTask() {
    return new Runnable() {
      public void run() { loadFromDisk(); }
    };
  }

  public Runnable newSavingTask() {
    return new Runnable() {
      public void run() { saveToDisk(); }
    };
  }

  public void loadFromDisk(File file) { setFile(file); loadFromDisk(); }
  public void loadFromDisk() {
    TConsole.Message msg = console.logInfo("Loading from " + file.getAbsolutePath()).sticky();
    // open zipfile or make sure the directory exists
    try {
      if (compressed) zipfile = new ZipFile(file);
      else if (!file.exists()) throw new IOException("directory not found");
    } catch (Exception e) {
      console.logError("Error opening '" + file.getAbsolutePath() + "': ", e);
      console.popSticky();
      return;
    }
    // read XML document
    if ((xmlDocument = addAutoIDs(readMultiPartXML("document.xml"))) != null) {
      // setup nodes and map projection
      XMLElement xmlNodes = getNodes();
      if ((xmlNodes == null) || (getNodeCount() == 0))
        console.logError("No nodes found");
      else {
        view.setNodes(xmlNodes);
        view.setMapProjection(xmlNodes.getChild("projection"));
      }
      guiUpdateAlbumControls();
      // setup links
      view.setLinks(getLinks());
      if (getLinks() != null)
        loadBlobs(getLinks());  // make sure all links snapshots are loaded
      // setup data
      view.setNodeColoringData(getSelectedQuantity());
      loadBlobs(getAllQuantities());  // make sure all data is loaded
      guiUpdateNodeColoring();
      // setup slices
      view.setSlices(getSlices());
      loadBlobs(getSlices());  // make sure all slices snapshots are loaded
      generateLayouts(getSlices());  // FIXME
      // setup tomogram layout
      view.tomLayouts = xmlDocument.getString("tomLayouts", null);  // FIXME: layouts should be specified by <layout> tags or something
      view.setTomLayout();
      view.setDistanceMatrix(getDistanceQuantity());
      guiUpdateProjection();
    }
    // clean-up and done
    if (compressed) { try { zipfile.close(); } catch (Exception e) {}; zipfile = null; }
    console.popSticky();
    msg.text += " \u2013 done";
  }
  public void generateLayouts(XMLElement xml) {  // FIXME
    if (xml.getChild("snapshot") != null)  // FIXME
      for (XMLElement snapshot : xml.getChildren("snapshot"))  // FIXME
        generateLayouts(snapshot);  // FIXME
    else  // FIXME
      new Layout(getBlob(xml).getIntArray(), "radial_id");  // FIXME: the horror!
  }  // FIXME

  public void saveToDisk() {
    TConsole.Message msg = console.logInfo("Saving to " + file.getAbsolutePath()).sticky();
    // open zipout or make sure the directory exists
    try {
      if (compressed) {
        if (file.exists() && file.isDirectory())
          if (!clearDirectory(file) || !file.delete())  // remove directory to be able to create regular file
            throw new IOException("is a directory and could not be deleted");
        zipout = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(file)));
        zipout.setLevel(9);
      } else {
        if (file.exists() && !file.isDirectory())
          if (!file.delete())  // remove regular file to be able to create directory
            throw new IOException("file already exists and could not be deleted");
        file.mkdirs();
        if (!clearDirectory(file))
          throw new IOException("could not clear the directory");
      }
    } catch (Exception e) {
      console.logError("Error opening '" + file.getAbsolutePath() + "': ", e);
      console.popSticky();
      return;
    }
    // write XML document
    writeMultiPartXML("document.xml", xmlDocument);
    // write persistent blobs
    Iterator<XMLElement> xmlit = blobCache.keySet().iterator();
    while (xmlit.hasNext()) {
      XMLElement xml = xmlit.next();
      String name = xml.getString("blob");
      if (name == null) continue;  // not a persistant BinaryThing
      if (!compressed) new File(file, "blobs").mkdirs();
      OutputStream stream = createDocPartOutput("blobs" + File.separator + name);
      if (stream != null) try {
        String xmlPretty = xml.getString("name", "");
        if (xmlPretty.length() > 0) xmlPretty = " \u201C" + xmlPretty + "\u201D";
        xmlPretty = xml.getName() + xmlPretty;
        TConsole.Message msgBlob = console.logProgress("Saving " + xmlPretty + " to blob " + name);
        blobCache.get(xml).saveToStream(new DataOutputStream(stream), msgBlob);
        console.finishProgress();
        if (!compressed) stream.close();
      } catch (Exception e) { console.abortProgress("Error saving blob " + name + ": ", e); }
    }
    // clean-up and done
    if (compressed) { try { zipout.close(); } catch (Exception e) {}; zipout = null; }
    console.popSticky();
    msg.text += " \u2013 done";
  }

  public InputStream createDocPartInput(String name) {
    InputStream stream = null;
    if (compressed) {
      name = name.replace(File.separatorChar, '/');  // always use / as file separator in zip files
      try { stream = zipfile.getInputStream(zipfile.getEntry(name)); }
      catch (Exception e) { stream = null; }
    } else
      stream = createInput(new File(file, name).getAbsolutePath());
    if (stream == null)
      console.logError("Could not find or read '" + name + "' in '" + file.getAbsolutePath() + "'");
    return new BufferedInputStream(stream);
  }

  public OutputStream createDocPartOutput(String name) {
    OutputStream stream = null;
    if (compressed) {
      name = name.replace(File.separatorChar, '/');  // always use / as file separator in zip files
      try { zipout.putNextEntry(new ZipEntry(name)); stream = zipout; }
      catch (Exception e) { stream = null; }
    } else
      stream = createOutput(new File(file, name).getAbsolutePath());
    if (stream == null)
      console.logError("Error opening '" + name + "' in '" + file.getAbsolutePath() + "' for writing");
    return compressed ? stream : new BufferedOutputStream(stream);  // zipout is already buffered
  }

  public Reader createDocPartReader(String name) {
    try { return new InputStreamReader(createDocPartInput(name)); } catch (Exception e) { return null; } }
  public Writer createDocPartWriter(String name) {
    try { return new OutputStreamWriter(createDocPartOutput(name)); } catch (Exception e) { return null; } }

  /* Copy of processing.xml.XMLElement.parseFromReader(...), modified to fit our needs (i.e., catch parsing
   * exceptions and return null instead of silently ignoring and returning a crippled document). */
  public XMLElement readXML(String name) {
    Reader reader = new File(name).isAbsolute() ? createReader(name) : createDocPartReader(name);
    if (reader == null) return null;
    XMLElement xml = new XMLElement();
    try {
      console.logProgress("Parsing " + name).indeterminate();
      StdXMLParser parser = new StdXMLParser();
      parser.setBuilder(new StdXMLBuilder(xml));
      parser.setValidator(new XMLValidator());
      parser.setReader(new StdXMLReader(reader));
      parser.parse();
      console.finishProgress();
    } catch (Exception e) {
      console.abortProgress("XML parsing error in " + name + ": ", e);
      xml = null;
    }
    try { reader.close(); } catch (Exception e) { }
    return xml;
  }

  public void writeXML(String name, XMLElement xml) {
    Writer writer = new File(name).isAbsolute() ? createWriter(name) : createDocPartWriter(name);
    if (writer == null) return;
    try {
      console.logProgress("Writing " + name).indeterminate();
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      new XMLWriter(writer).write(xml, true);
      if (!compressed) writer.close();  // we're directly writing into the zipfile, which will be closed by saveToDisk
      console.finishProgress();
    } catch (Exception e) {
      console.abortProgress("XML writing error in " + name + ": ", e);
    }
  }

  /* This function reads the XML document found in file specified by name.  It then traverses
   * all child tags and wherever it finds a "src" attribute it will interpret its value as
   * a URI (absolute or relative to the document base) of an additional XML file.  That file is
   * parsed and searched for a tag (either top-level or child of top-level) that matches the
   * tag in the original document in name and (if existent) "id" attribute value.  If a match is
   * found, the tag from the external document is merged into the tag in the original document,
   * that is, tag attributes are added and the tag's children are appended to the original tag's
   * children.  The returned XMLElement is the merged full document.
   */
  public XMLElement readMultiPartXML(String name) {
    XMLElement doc = readXML(name);
    if (doc == null) return null;
    for (int i = 0; i < doc.getChildCount(); i++) {
      XMLElement child = doc.getChild(i);
      // load external XML document
      String src = child.getString("src");
      if (src == null) continue;
      XMLElement idoc = readXML(src);
      if (idoc == null) continue;
      // find the matching node to merge into the original document
      XMLElement merge = equalNameAndID(child, idoc, true) ? idoc : null;
      if (merge == null)
        for (int j = 0; j < idoc.getChildCount(); j++)
          if (equalNameAndID(child, idoc.getChild(j), false))
            merge = idoc.getChild(j);
      if (merge == null) {
        console.logError(child.getName() + ": no match found in " + src);
        continue;
      }
      // merge node with 'child'
      String[] attr = merge.listAttributes();
      for (int j = 0; j < attr.length; j++)
        if (!attr[j].equals("src"))
          child.setString(attr[j], merge.getString(attr[j]));
      XMLElement[] children = merge.getChildren();
      for (int j = 0; j < children.length; j++)
        child.addChild(children[j]);
    }
    return doc;
  }

  /* Being the counterpart to readMultiPartXML, this function traverses the children of the document doc
   * to extract all parts which should go into a separate file.  The first child with a "src" attribute
   * that is not already recorded in the map will be replaced by a new element that has the same tag name,
   * same "id" attribute (if applicable), and the same "src" attribute (as a hint for subsequent reading from disk).
   * The "src" value and the extracted element are added to the hash map and the function calls itself recursively.
   * Afterwards, the removed element is re-added into the document.  If no such children are found,
   * the remaining document is written to the file specified by name, and all extracted children to their
   * respective files. */
  public void writeMultiPartXML(String name, XMLElement doc) { writeMultiPartXML(name, doc, null); }
  public void writeMultiPartXML(String name, XMLElement doc, HashMap<String,XMLElement[]> map) {
    if (doc == null) return;
    if (map == null) map = new HashMap<String,XMLElement[]>();
    int iChild = -1; XMLElement child = null; String src = null;
    for (int i = 0; i < doc.getChildCount(); i++) {
      child = doc.getChild(i);
      src = child.getString("src");
      if ((src != null) && notInMap(map, src, child)) { iChild = i; break; }
    }
    if (iChild == -1) {
      // write doc to name
      writeXML(name, doc);
      // write all extracted children to their respective files
      Object srcs[] = map.keySet().toArray();
      for (int i = 0; i < srcs.length; i++) {
        XMLElement[] elems = map.get(srcs[i]);
        XMLElement out = new XMLElement("includes");
        for (int j = 0; j < elems.length; j++) elems[j].remove("src");
        if (elems.length == 1)
          out = elems[0];  // no need to wrap into another element if we write only one anyway
        else for (int j = 0; j < elems.length; j++)
          out.addChild(elems[j]);
        writeXML((String)srcs[i], out);
        for (int j = 0; j < elems.length; j++) elems[j].setString("src", (String)srcs[i]);
      }
    } else {
      // add child to map
      if (!map.containsKey(src)) map.put(src, new XMLElement[0]);
      map.put(src, (XMLElement[])append(map.get(src), child));
      // extract from doc
      XMLElement childPlaceholder = new XMLElement(child.getName());
      if (child.getString("id") != null)
        childPlaceholder.setString("id", child.getString("id"));
      childPlaceholder.setString("src", src);
      doc.insertChild(childPlaceholder, iChild);
      doc.removeChild(child);
      // recurse on doc
      writeMultiPartXML(name, doc, map);
      // re-insert child
      doc.insertChild(child, iChild);
      doc.removeChild(childPlaceholder);
    }
  }
  public boolean notInMap(HashMap<String,XMLElement[]> map, String src, XMLElement child) {
    if (!map.containsKey(src)) return true;
    XMLElement elems[] = map.get(src);
    for (int i = 0; i < elems.length; i++) if (equalNameAndID(elems[i], child, false)) return false;
    return true;
  }

  /* This function traverses the XML document and adds auto-generated id attribute values
   * to all links, slices, dataset, and data tags, if they are missing their id attribute.
   * Note that this function will alter its argument doc (and return a reference to it). */
  public XMLElement addAutoIDs(XMLElement doc) { return addAutoIDs(doc, ""); }
  public XMLElement addAutoIDs(XMLElement doc, String hashPrefix) {
    if (doc == null) return null;
    String tags[] = { "links", "slices", "dataset", "data" };
    for (int t = 0; t < tags.length; t++) {
      XMLElement elems[] = doc.getChildren(tags[t]);
      for (int i = 0; i < elems.length; i++) {
        if (elems[i].getString("id") == null)
          elems[i].setString("id", generateID(hashPrefix + elems[i].getString("name", "")));
        if (tags[t].equals("dataset"))
          addAutoIDs(elems[i], elems[i].getString("id"));
      }
    }
    return doc;
  }

  /*
   * Small Helper Functions
   */

  /* Returns true if the names of a and b match (or are both null) and if the "id" attributes
   * of a and b match.  If laxID is true, the test still passes if a has an id attribute but
   * b does not. */
  public boolean equalNameAndID(XMLElement a, XMLElement b, boolean laxID) {
    return (((b.getName() == null) && (a.getName() == null)) ||
      ((b.getName() != null) && b.getName().equals(a.getName()))) &&
      (((b.getString("id") == null) && (laxID || (a.getString("id") == null))) ||
      ((b.getString("id") != null) && b.getString("id").equals(a.getString("id"))));
  }

  /* This function will always return a most-probably unique 8-digit hex-character sequence.
   * It does so by returning the first 8 characters of the MD5 hash of the argument name,
   * or a random sequence if name is null, an empty string, or something goes wrong with MD5. */
  public String generateID() { return generateID(null); }
  public String generateID(String name) {
    byte[] digest = new byte[4];
    // generate random 8-byte sequence as a backup
    for (int i = 0; i < 4; i++)
      digest[i] = (byte)PApplet.parseInt(random(256));
    // try calculating MD5 hash if name argument is sane
    if ((name != null) && !name.equals(""))
      try { digest = java.security.MessageDigest.getInstance("MD5").digest(name.getBytes()); } catch (Exception e) { }
    // serialize and return whatever we got
    String id = "";
    for (int i = 0; i < 4; i++)
      id += String.format("%02x", digest[i] & 0xff);
    return id;
  }

  /* Recursively removes the contents of the specified directory. */
  public boolean clearDirectory(File f) throws Exception {
    if (!f.isDirectory()) return false;
    File ff[] = f.listFiles();
    for (int i = 0; i < ff.length; i++) {
      if (ff[i].isDirectory() && !clearDirectory(ff[i])) return false;
      if (!ff[i].delete()) return false;
    }
    return true;
  }

  /* If needle is in the array haystack, then the element before needle is returned.
   * If needle is the first element in haystack, the element after needle is returned.
   * If haystack only contains needle, null is returned. */
  public Object previousOrNext(Object haystack, Object needle) {
    if ((Array.getLength(haystack) > 1) && (Array.get(haystack, 0) == needle))
      return Array.get(haystack, 1);  // return second element if needle is first
    for (int i = 1; i < Array.getLength(haystack); i++)
      if (Array.get(haystack, i) == needle)
        return Array.get(haystack, i - 1);  // return element before needle
    return null;  // needle was not in haystack
  }

  /* Evaluates an XPath expression on xmlDocument. Only understands a limited subet of XPath! */
  public XMLElement[] getChildren(String path) { return getChildren(xmlDocument, path); }
  public XMLElement[] getChildren(XMLElement xml, String path) {
    XMLElement result[] = new XMLElement[0];
    if ((xml == null) || (path == null))
      return result;
    String name = path, conds = "", subpath = null; int p;
    if ((p = path.indexOf('/')) > -1) {
      name = path.substring(0, p);
      subpath = path.substring(p+1);
    }
    if ((p = name.indexOf('[')) > -1) {
      conds = name.substring(p);
      name = name.substring(0, p);
    }
    XMLElement tmp[] = name.equals("*") ? xml.getChildren() : xml.getChildren(name);
    int level = 0, i0 = 0;
    for (int i = 0; i < conds.length(); i++) {
      if (conds.charAt(i) == '[')
        if (level++ == 0) i0 = i;
      if (conds.charAt(i) == ']')
        if (--level == 0)
          tmp = xpathFilter(tmp, conds.substring(i0 + 1, i));
    }
    if (subpath != null) {
      for (int i = 0; i < tmp.length; i++)
        result = (XMLElement[])concat(result, getChildren(tmp[i], subpath));
    } else
      result = tmp;
    return result;
  }

  public XMLElement getChild(String path) { return getChild(xmlDocument, path); }
  public XMLElement getChild(XMLElement xml, String path) {
    XMLElement result[] = getChildren(xml, path);
    return ((result != null) && (result.length > 0)) ? result[0] : null;
  }

  public XMLElement[] xpathFilter(XMLElement xml[], String condition) {
    if (xml == null)
      return new XMLElement[0];
    if ((xml.length == 0) || (condition == null) || (condition.length() == 0))
      return xml;
    if (condition.charAt(0) == '@') {
      int p = condition.indexOf('=');
      String attr = (p > -1) ? condition.substring(1, p) : condition.substring(1);
      String value = (p > -1) ? condition.substring(p+1) : null;  // FIXME: '/" unwrapping
      XMLElement result[] = new XMLElement[0];
      for (int i = 0; i < xml.length; i++) {
        if (xml[i].getString(attr) == null) continue;
        if ((value != null) && !value.equals(xml[i].getString(attr))) continue;
        result = (XMLElement[])append(result, xml[i]);
      }
      return result;
    } else
      return xml;
  }

}

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



TransparentGUI gui;
TConsole console;
TTextField tfSearch;
TToggleButton btnRegexpSearch;
TChoiceWithRollover choiceNetwork;
TButton btnWorkspaceRecovery;
TChoice choiceMapProjection, choiceTomProjection, choiceTomScaling, choiceDistMat;
TChoice choiceDataset, choiceQuantity, choiceColormap;
TToggleButton btnColormapLog;
TToggleButton btnNodes, btnLinks, btnSkeleton, btnNeighbors, btnNetwork, btnLabels;
TToggleButton btnMap, btnTom;
NetworkDetailPanel networkDetail;
TLabel lblStatus;
// snapshot controls for node coloring quantity
TButton btnQuantityPrevSnapshot, btnQuantityNextSnapshot;
TSlider sldQuantitySnapshot;
// snapshot controls for album
TLabel lblAlbumName, lblAlbumSnapshot;
TButton btnAlbumPrevSnapshot, btnAlbumNextSnapshot;
TSlider sldAlbumSnapshot;

int fnsizeSmall = 10, fnsizeMedium = 12, fnsizeLarge = 14;
PFont fnSmall, fnMedium, fnLarge, fnLargeBold;
boolean fastNodes = false;

boolean searchMatchesValid = false;
boolean searchMatches[] = null;  // this is true for nodes which are matched by the search phrase
int searchMatchesChild[] = null;  // this is 1 for nodes which have any node in their branch that matches the search phrase
String searchMsg = null;
int searchUniqueMatch = -1;

public void guiSetup() {
  gui = new TransparentGUI(this);
  fnSmall = gui.createFont("GillSans", fnsizeSmall);
  fnMedium = gui.createFont("GillSans", fnsizeMedium);
  fnLarge = gui.createFont("GillSans", fnsizeLarge);
  fnLargeBold = gui.createFont("GillSans-Bold", fnsizeLarge);
  // setup top panel
  TPanel panel = gui.createPanel(new TBorderLayout());
  panel.setPadding(5);
  choiceMapProjection = gui.createChoice("projMap##");
  choiceMapProjection.add(MapProjectionFactory.productNames);
  choiceTomProjection = gui.createChoice("projTom##");
  choiceTomProjection.add(TomProjectionFactory.productNames);
  choiceTomScaling = gui.createChoice("scal##");
  choiceTomScaling.add(ScalingFactory.productNames);
  choiceDistMat = gui.createChoice("distMat##", true);
  choiceDistMat.setNoneString("Tree Depth");
  choiceDistMat.setRenderer(new XMLElementRenderer(true));
  panel.add(gui.createCompactGroup(new TComponent[] {
    gui.createLabel("Projection:"), choiceMapProjection }), TBorderLayout.EAST);
  panel.add(gui.createCompactGroup(new TComponent[] {
    gui.createLabel("Projection:"), choiceTomProjection, choiceTomScaling, choiceDistMat }), TBorderLayout.EAST);
  //
  tfSearch = gui.createTextField("search");
  tfSearch.setEmptyText("Search");
  btnRegexpSearch = gui.createToggleButton("RegExp", prefs.getBoolean("search.regexp", false));
  panel.add(gui.createCompactGroup(new TComponent[] { tfSearch, btnRegexpSearch }), TBorderLayout.EAST);
  //
  btnNodes = gui.createToggleButton("Nodes", true);
  btnLinks = gui.createToggleButton("Links", true);
  btnSkeleton = gui.createToggleButton("Skeleton", false);
  btnNeighbors = gui.createToggleButton("Neighbors", false); btnNeighbors.setVisible(false);  // enabled but invis for now...
  btnNetwork = gui.createToggleButton("Network", false); btnNetwork.setVisible(false);  // enabled but invis for now...
  btnLabels = gui.createToggleButton("Labels", true);
  TPanel linksGroup = gui.createCompactGroup(new TComponent[] { btnLinks, btnSkeleton, btnNeighbors, btnNetwork }, new TComponent.Spacing(0));
  linksGroup.setBorderColor(color(200, 0, 0));
  panel.add(gui.createCompactGroup(new TComponent[] {
    gui.createLabel("Show:"), btnNodes, linksGroup, btnLabels }), TBorderLayout.WEST);
  //
  lblAlbumName = gui.createLabel("Album:");
  btnAlbumPrevSnapshot = gui.createButton("<"); btnAlbumPrevSnapshot.setActionCommand("snapshot##album##prev");
  btnAlbumNextSnapshot = gui.createButton(">"); btnAlbumNextSnapshot.setActionCommand("snapshot##album##next");
  sldAlbumSnapshot = gui.createSlider("snapshot##album");
  lblAlbumSnapshot = gui.createLabel("Snapshot");
  panel.add(gui.createCompactGroup(new TComponent[] {
    lblAlbumName, btnAlbumPrevSnapshot, sldAlbumSnapshot, btnAlbumNextSnapshot, lblAlbumSnapshot }), TBorderLayout.WEST);
  //
  panel.add(gui.createLabel(""), TBorderLayout.WEST);  // place-holder to keep choiceNetwork in place
  //
  gui.add(panel, TBorderLayout.NORTH);
  // setup network and map/tom switcher
  panel = gui.createPanel(new TBorderLayout());
  panel.setPadding(5, 10);
  choiceNetwork = new TChoiceWithRollover(gui, "network##"); choiceNetwork.setFont(fnLargeBold);
  choiceNetwork.setEmptyString("right-click here");
  choiceNetwork.setContextMenu(gui.createPopupMenu(new String[][] {
//    { "New document\u2026", "document##new" },
    { "Open document\u2026", "document##open" },
    { "Save document", "document##save" },
    { "Save document as\u2026", "document##saveAs" },
    { "Save uncompressed", "document##compressed" },
    { "Close document", "document##close" },
    null,
    { "Open workspace\u2026", "workspace##open" },
    { "Save workspace", "workspace##save" },
    { "Save workspace as\u2026", "workspace##saveAs" },
    null,
    { "Check for updates", "update" }
  }));
  panel.add(gui.createCompactGroup(new TComponent[] { choiceNetwork }, 5), TBorderLayout.WEST);
  btnMap = gui.createToggleButton("Map"); btnMap.setFont(fnLargeBold);
  btnTom = gui.createToggleButton("Tom"); btnTom.setFont(fnLargeBold);
  new TButtonGroup(new TToggleButton[] { btnMap, btnTom });
  panel.add(gui.createCompactGroup(new TComponent[] { btnMap, btnTom }, 5), TBorderLayout.EAST);
  gui.add(panel, TBorderLayout.NORTH);
  // setup workspace recovery button
  XMLElement xmlWorkspace = XMLElement.parse(prefs.get("workspace", "<workspace />"));
  if ((xmlWorkspace != null) && (xmlWorkspace.getChildren("document").length > 0)) {
    btnWorkspaceRecovery = gui.createButton("Recover previous workspace");
    btnWorkspaceRecovery.setActionCommand("workspace##recover");
    String tooltip = "Attempts to reload all documents that were open\n" +
      "when the application was closed the last time:\n";
    XMLElement xmlDocument[] = xmlWorkspace.getChildren("document");
    for (int i = 0; i < xmlDocument.length; i++) {
      tooltip += "  " + (i+1) + ".  " + xmlDocument[i].getString("src") + "\n";
      // File f = new File(xmlDocument[i].getString("src"));
      // tooltip += (i+1) + ".  ";
      // tooltip += "[color=127,127,127]" + f.getParent() + File.separator + "[/color]";
      // String name = f.getName();
      // String ext = null;
      // if (name.endsWith(".spato")) { name = name.substring(0, name.length() - 7); ext = ".spato"; }
      // tooltip += name;
      // if (ext != null) tooltip += "[color=127,127,127]" + ext + "[/color]";
      // tooltip += "\n";
    }
    btnWorkspaceRecovery.setToolTip(tooltip);
    btnWorkspaceRecovery.getToolTip().setID("workspace##recover");
    panel = gui.createPanel(new TBorderLayout());
    panel.setPadding(5, 10);
    panel.add(gui.createCompactGroup(new TComponent[] { btnWorkspaceRecovery }, 2), TBorderLayout.WEST);
    gui.add(panel, TBorderLayout.NORTH);
  }
  // setup bottom panel
  panel = gui.createPanel(new TBorderLayout());
  panel.setPadding(5);
  choiceDataset = gui.createChoice("dataset##", true);
  choiceDataset.setEmptyString("\u2014 right-click to create new dataset \u2014");
  choiceDataset.setRenderer(new XMLElementRenderer());
  // choiceDataset.setContextMenu(gui.createPopupMenu(new String[][] {
  //   { "New", "dataset####new" },
  //   { "Rename", "dataset####rename" },
  //   { "Delete", "dataset####delete" }
  // }));
  choiceQuantity = gui.createChoice("quantity##", true);
  choiceQuantity.setRenderer(new XMLElementRenderer());
  // choiceQuantity.setContextMenu(gui.createPopupMenu(new String[][] {
  //   { "Import\u2026", "quantity####import" },
  //   { "Rename", "quantity####rename" },
  //   { "Delete", "quantity####delete" }
  // }));
  choiceColormap = gui.createChoice("colormap##");
  choiceColormap.setEmptyString("\u2014 no quantity selected \u2014");
  btnColormapLog = gui.createToggleButton("log");
    btnQuantityPrevSnapshot = gui.createButton("<"); btnQuantityPrevSnapshot.setActionCommand("snapshot##quantity##prev");
    btnQuantityNextSnapshot = gui.createButton(">"); btnQuantityNextSnapshot.setActionCommand("snapshot##quantity##next");
    sldQuantitySnapshot = gui.createSlider("snapshot##quantity");
    // FIXME: snapshot controls still wiggle due to stupid XMLElementRenderer
  panel.add(gui.createCompactGroup(new TComponent[] {
    gui.createLabel("Node Coloring:"), choiceDataset, gui.createLabel("/"), choiceQuantity,
    gui.createCompactGroup(new TComponent[] { btnQuantityPrevSnapshot, sldQuantitySnapshot, btnQuantityNextSnapshot }),
    gui.createLabel("/"), choiceColormap, btnColormapLog }), TBorderLayout.WEST);
  gui.add(panel, TBorderLayout.SOUTH);
  // node coloring quantity snapshot controls
  // btnQuantityPrevSnapshot = gui.createButton("<"); btnQuantityPrevSnapshot.setActionCommand("snapshot##quantity##prev");
  // btnQuantityNextSnapshot = gui.createButton(">"); btnQuantityNextSnapshot.setActionCommand("snapshot##quantity##next");
  // sldQuantitySnapshot = gui.createSlider("snapshot##quantity");
  // panel.add(gui.createCompactGroup(new TComponent[] { btnQuantityPrevSnapshot, sldQuantitySnapshot, btnQuantityNextSnapshot }), TBorderLayout.WEST);
  // TWindow win = new TWindow(gui, new TCompactGroupLayout(0));
  // win.setMargin(1, 3);
  // win.add(btnQuantityPrevSnapshot);
  // win.add(sldQuantitySnapshot);
  // win.add(btnQuantityNextSnapshot);
  // win.setVisible(false);
  // gui.add(win);
  // status bar
  lblStatus = gui.createLabel("");
  lblStatus.setAlignment(TLabel.ALIGN_RIGHT);
  lblStatus.setFont(gui.createFont("GillSans-Bold", 10));
  panel.add(lblStatus, TBorderLayout.CENTER);
  // network details panel
  panel = gui.createPanel(new TBorderLayout());
  panel.setPadding(0, 10);
  networkDetail = new NetworkDetailPanel(gui);
  networkDetail.setVisible(false);
  choiceNetwork.rollOverComponent = networkDetail;
  panel.add(networkDetail, TBorderLayout.NORTH);
  gui.add(panel, TBorderLayout.WEST);
  // setup console
  console = gui.createConsole(versionDebug.equals("alpha"));
  int tE = 5000;
  console.logInfo("SPaTo Visual Explorer").tE = tE;
  console.logNote("Version " + version + ((versionDebug.length() > 0) ? " " + versionDebug : "") + " (" + versionDate + ")").tE = tE;
  if (versionDebug.equals("alpha")) console.logError("This is an alpha version \u2013 don't use it unless you know what you are doing").tE = tE;
  else if (versionDebug.equals("beta")) console.logWarning("This is a beta version \u2013 expect unexpected behavior").tE = tE;
  console.logNote("Copyright (C) 2008\u20132011 by Christian Thiemann").tE = tE;
  console.logNote("Research on Complex Systems, Northwestern University").tE = tE;
  console.logDebug("--------------------------------------------------------");
  console.logDebug("[OS] " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
  console.logDebug("[JRE] " + System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version"));
  console.logDebug("[JVM] " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ") [" + (com.sun.jna.Platform.is64Bit() ? "64" : "32") + "-bit]");
  console.logDebug("[path] " + System.getenv(((platform != WINDOWS) ? ((platform == MACOSX) ? "DY" : "") + "LD_LIBRARY_" : "") + "PATH"));
  console.logDebug("[mem] max: " + (Runtime.getRuntime().maxMemory()/1024/1024) + " MB");
  // if (!JNMatLib.isLoaded() && (versionDebug.length() > 0)) console.logError("[JNMatLib] " + JNMatLib.getError().getMessage());
  console.logDebug("--------------------------------------------------------");
  gui.add(console);
  // setup hotkeys and drop target
  guiSetupHotkeys();
  setupDropTarget();
}

public void guiSetupHotkeys() {
  tfSearch.setHotKey(KeyEvent.VK_F, MENU_SHORTCUT);
  if (btnWorkspaceRecovery != null) btnWorkspaceRecovery.setHotKey(KeyEvent.VK_R, MENU_SHORTCUT);
  else btnRegexpSearch.setHotKey(KeyEvent.VK_R, MENU_SHORTCUT);
  btnNodes.setHotKey(KeyEvent.VK_N);
  btnLinks.setHotKey(KeyEvent.VK_L);
  btnSkeleton.setHotKey(KeyEvent.VK_B);
  btnNeighbors.setHotKey(KeyEvent.VK_E);
  btnNetwork.setHotKey(KeyEvent.VK_F);
  btnLabels.setHotKey(KeyEvent.VK_L, KeyEvent.SHIFT_MASK);
  //
  btnMap.setHotKey(KeyEvent.VK_V);
  btnTom.setHotKey(KeyEvent.VK_V);
  choiceDistMat.setHotKeyChar('d');
  choiceNetwork.setHotKeyChar('~');
  choiceDataset.setHotKeyChar(TAB);
  choiceDataset.setShortcutChars(new char[] { 'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p' });
  choiceQuantity.setHotKeyChar('`');
  choiceQuantity.setShortcutChars(new char[] { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' });
  choiceColormap.setHotKeyChar('c');
  btnColormapLog.setHotKey(KeyEvent.VK_C, KeyEvent.SHIFT_MASK);
  btnQuantityPrevSnapshot.setHotKey(KeyEvent.VK_COMMA);
  btnQuantityNextSnapshot.setHotKey(KeyEvent.VK_PERIOD);
}

public void guiUpdate() {
  guiFastUpdate();
  choiceNetwork.removeAll();
  if (docs.size() > 0) {
    choiceNetwork.add(docs.toArray());
    if (doc != null)
      choiceNetwork.select(docs.indexOf(doc));
  }
  choiceNetwork.getContextMenu().setEnabled("document##save", doc != null);
  choiceNetwork.getContextMenu().setEnabled("document##saveAs", doc != null);
  choiceNetwork.getContextMenu().setEnabled("document##compressed", doc != null);
  choiceNetwork.getContextMenu().getItem("document##compressed").setText(
    ((doc != null) && doc.compressed) ? "Save uncompressed" : "Save compressed");
  choiceNetwork.getContextMenu().setEnabled("document##close", doc != null);
  choiceNetwork.getContextMenu().setEnabled("workspace##save", docs.size() > 0);
  choiceNetwork.getContextMenu().setEnabled("workspace##saveAs", docs.size() > 0);
  if ((btnWorkspaceRecovery != null) && !showWorkspaceRecoveryButton) {
    gui.remove(btnWorkspaceRecovery.getParent().getParent());  // remove the button from the GUI
    btnWorkspaceRecovery.setHotKey(0);  // release hotkey
    btnWorkspaceRecovery = null;  // ... and we don't need that anymore
    btnRegexpSearch.setHotKey(KeyEvent.VK_R, MENU_SHORTCUT);  // re-bind hotkey to Regexp toggle
  }
  guiUpdateProjection();
  guiUpdateNodeColoring();
  guiUpdateAlbumControls();
}

public void guiFastUpdate() {
  // update visibility of components
  btnNodes.getParent().setVisibleAndEnabled(doc != null);
  tfSearch.getParent().setVisibleAndEnabled(doc != null);
  tfSearch.setEmptyText("Search" + (btnRegexpSearch.isSelected() ? " (RegExp)" : ""));
  btnMap.getParent().setVisibleAndEnabled(doc != null);
  choiceMapProjection.getParent().setVisibleAndEnabled((doc != null) && (doc.view.viewMode == SVE2View.VIEW_MAP));
  choiceTomProjection.getParent().setVisibleAndEnabled((doc != null) && (doc.view.viewMode == SVE2View.VIEW_TOM));
  choiceDataset.getParent().setVisibleAndEnabled(doc != null);
  btnNodes.getParent().setVisibleAndEnabled(doc != null);
  sldAlbumSnapshot.getParent().setVisibleAndEnabled((doc != null) && (doc.getAlbum() != null));
  lblStatus.setText(((doc == null) || (doc.view.ih == -1)) ? "" : doc.view.nodes[doc.view.ih].name);
  updatePlatformMagic();
  if (doc == null) return;
  choiceMapProjection.setEnabled(doc.view.hasMapLayout);
  choiceTomProjection.setEnabled(doc.view.hasTomLayout);
  choiceTomScaling.setEnabled(doc.view.hasTomLayout);
  choiceDistMat.setEnabled(doc.view.hasTomLayout);
  btnMap.setEnabled(doc.view.hasMapLayout);
  btnTom.setEnabled(doc.view.hasTomLayout);
  switch (doc.view.viewMode) {
    case SVE2View.VIEW_MAP: btnMap.getButtonGroup().setSelected(btnMap); break;
    case SVE2View.VIEW_TOM: btnTom.getButtonGroup().setSelected(btnTom); break;
  }
  btnNodes.setSelected(doc.view.showNodes);
  btnLinks.setSelected((doc.view.showLinks && !doc.view.showSkeleton && !doc.view.showNeighbors && !doc.view.showNetwork) ||
    (doc.view.showSkeleton && doc.view.showLinksWithSkeleton) ||
    (doc.view.showNeighbors && doc.view.showLinksWithNeighbors) ||
    (doc.view.showNetwork && doc.view.showLinksWithSkeleton));
  btnLinks.getParent().setBorder(doc.view.showSkeleton || doc.view.showNeighbors || doc.view.showNetwork ? 1 : 0);
  btnSkeleton.setSelected(doc.view.showSkeleton);
  btnNeighbors.setSelected(doc.view.showNeighbors);
  btnNetwork.setSelected(doc.view.showNetwork);
  btnLabels.setSelected(doc.view.showLabels);
  if (frameRate < 15) fastNodes = true;
  if (frameRate > 30) fastNodes = false;
  if (versionDebug.equals("beta")) {
    if (frameRate < 14) console.setShowDebug(true);
    if (frameRate > 20) console.setShowDebug(false);
  }
  // update search matches if necessary
  if (!searchMatchesValid)
    guiUpdateSearchMatches();
}

public void guiUpdateProjection() {
  if (doc == null) return;
  if (doc.view.hasMapLayout)
    choiceMapProjection.select(doc.view.xmlProjection.getString("name"));
  if (doc.view.hasTomLayout) {
    choiceTomProjection.select(doc.view.layouts[doc.view.l].projection);
    choiceTomScaling.select(doc.view.layouts[doc.view.l].scaling);
    choiceDistMat.removeAll();
    choiceDistMat.add(doc.getDistanceQuantities());
    choiceDistMat.select(doc.view.xmlDistMat);
  }
}

public void guiUpdateNodeColoring() {
  if (doc == null) { choiceDataset.getParent().setVisibleAndEnabled(false); return; }
  choiceDataset.getParent().setVisibleAndEnabled(true);
  //
  choiceDataset.removeAll();
  choiceDataset.add(doc.getDatasets());
  choiceDataset.select(doc.getSelectedDataset());
  // choiceDataset.getContextMenu().setEnabled("dataset####rename", choiceDataset.getSelectedItem() != null);
  // choiceDataset.getContextMenu().setEnabled("dataset####delete", choiceDataset.getSelectedItem() != null);
  //
  choiceQuantity.removeAll();
  if (doc.getSelectedDataset() != null) {
    choiceQuantity.add(doc.getQuantities());
    choiceQuantity.select(doc.getSelectedQuantity());
  }
  // choiceQuantity.getContextMenu().setEnabled("quantity####import", choiceDataset.getSelectedItem() != null);
  // choiceQuantity.getContextMenu().setEnabled("quantity####rename", choiceQuantity.getSelectedItem() != null);
  // choiceQuantity.getContextMenu().setEnabled("quantity####delete", choiceQuantity.getSelectedItem() != null);
  choiceQuantity.setEmptyString((doc.getSelectedDataset() == null)
    ? "\u2014 no dataset selected \u2014" : "\u2014 right-click to import data \u2014");
  //
  choiceColormap.removeAll();
  if (doc.view.hasData) {
    choiceColormap.setRenderer(doc.view.colormap.new Renderer());
    choiceColormap.add(doc.view.colormap.colormaps);
    choiceColormap.select(doc.view.colormap.getColormapName());
  }
  //
  btnColormapLog.setVisibleAndEnabled(doc.view.hasData && !doc.view.colormap.getColormapName().equals("discrete"));
  if (doc.view.hasData) btnColormapLog.setSelected(doc.view.colormap.isLogscale());
  //
//  TWindow win = (TWindow)sldQuantitySnapshot.getParent();
  TPanel win = (TPanel)sldQuantitySnapshot.getParent();
  win.setVisibleAndEnabled(false);
  XMLElement series = doc.getSelectedSnapshotSeriesContainer(doc.getSelectedQuantity());
  if (series != null) {
    XMLElement snapshots[] = series.getChildren("snapshot");
    sldQuantitySnapshot.setValueBounds(0, snapshots.length-1);
    sldQuantitySnapshot.setValue(doc.getSelectedSnapshotIndex(series));
    sldQuantitySnapshot.setPreferredWidth(max(75, min(width-50, snapshots.length-1)));
    win.setVisibleAndEnabled(true);
    TComponent.Dimension d = win.getPreferredSize();
    gui.validate();
    TComponent.Point p = choiceQuantity.getLocationOnScreen();
    float x = max(3, p.x - btnQuantityPrevSnapshot.getPreferredSize().width/2);
    x = min(width - 3 - win.getWidth(), x);
    float y = p.y - d.height;
//      win.setBounds(x, y, d.width, d.height);
  }
}

public void guiUpdateAlbumControls() {
  if (doc == null) return;  // done
  XMLElement xmlAlbum = doc.getAlbum();
  if (xmlAlbum == null) return;  // done
  lblAlbumName.setText(xmlAlbum.getString("name", xmlAlbum.getString("id", "Unnamed Album")) + ":");
  XMLElement snapshots[] = xmlAlbum.getChildren("snapshot");
  sldAlbumSnapshot.setValueBounds(0, snapshots.length-1);
  sldAlbumSnapshot.setValue(doc.getSelectedSnapshotIndex(xmlAlbum));
  sldAlbumSnapshot.setPreferredWidth(max(100, min(width/2, snapshots.length-1)));
  XMLElement snapshot = doc.getSelectedSnapshot(xmlAlbum);
  lblAlbumSnapshot.setText("[" + snapshot.getString("label", snapshot.getString("id", "Unnamed Snapshot")) + "]");
}

public void guiUpdateSearchMatches() {
  searchMsg = null;
  searchUniqueMatch = -1;
  if ((doc == null) || !doc.view.hasNodes) return;
  String searchPhrase = tfSearch.getText();
  if (searchPhrase.length() == 0) {
    searchMatches = null;
    return;
  }
  if (searchMatches == null) {
    searchMatches = new boolean[doc.view.NN];
    searchMatchesChild = new int[doc.view.NN];
  }
  if (btnRegexpSearch.isSelected()) {
    try {
      Pattern p = Pattern.compile(searchPhrase);
      if (p.matcher("").find()) throw new PatternSyntaxException("Expression matches empty string", searchPhrase, -1);
      for (int i = 0; i < doc.view.NN; i++)
        searchMatches[i] = p.matcher(doc.view.nodes[i].label).find() || p.matcher(doc.view.nodes[i].name).find();
    } catch (PatternSyntaxException e) {
      searchMsg = "E" + e.getDescription();
      if (e.getIndex() > -1)
        searchMsg += ": " + e.getPattern().substring(0, e.getIndex());
    }
  } else {
    boolean caseSensitive = !searchPhrase.equals(searchPhrase.toLowerCase());  // ignore case if no upper-case letter present
    for (int i = 0; i < doc.view.NN; i++)
      searchMatches[i] =
        (caseSensitive
         ? doc.view.nodes[i].label.contains(searchPhrase)
         : doc.view.nodes[i].label.toLowerCase().contains(searchPhrase))
        ||
        (caseSensitive
         ? doc.view.nodes[i].name.contains(searchPhrase)
         : doc.view.nodes[i].name.toLowerCase().contains(searchPhrase));
  }
  searchMatchesValid = true;
  for (int i = 0; i < doc.view.NN; i++)
    if (searchMatches[i])
      if (searchUniqueMatch == -1) searchUniqueMatch = i;
      else { searchUniqueMatch = -1; break; }
  if (searchUniqueMatch > -1)
    searchMsg = "M" + doc.view.nodes[searchUniqueMatch].name;
}

public void actionPerformed(String cmd) {
  String argv[] = split(cmd, "##");
  if (argv[0].equals("workspace")) {
    if (argv[1].equals("open")) openWorkspace();
    if (argv[1].equals("save")) saveWorkspace();
    if (argv[1].equals("saveAs")) saveWorkspace(true);
    if (argv[1].equals("recover")) replaceWorkspace(XMLElement.parse(prefs.get("workspace", "<workspace />")));
  } else if (argv[0].equals("document")) {
    if (argv[1].equals("new")) newDocument();
    if (argv[1].equals("open")) openDocument();
    if (argv[1].equals("save")) saveDocument();
    if (argv[1].equals("saveAs")) saveDocument(true);
    if (argv[1].equals("compressed")) { doc.setCompressed(!doc.isCompressed()); saveDocument(); }
    if (argv[1].equals("close")) closeDocument();
  } else if (argv[0].equals("search")) {
    searchMatchesValid = false;
    if (argv[1].equals("enterKeyPressed") && (searchUniqueMatch > -1)) {
      doc.view.setRootNode(searchUniqueMatch);
      tfSearch.setText("");
    }
  } else if (argv[0].equals("RegExp")) {
    searchMatchesValid = false;
    prefs.putBoolean("search.regexp", btnRegexpSearch.isSelected());
  } else if (argv[0].equals("network"))
    switchToNetwork(argv[1]);
  else if (argv[0].equals("projMap"))
    doc.view.setMapProjection(argv[1]);
  else if (argv[0].equals("projTom")) {
    doc.view.layouts[0].setupProjection(argv[1]);
    if (doc.view.r > -1) doc.view.layouts[0].updateProjection(doc.view.r, doc.view.D);
  } else if (argv[0].equals("distMat"))
    doc.setDistanceQuantity((XMLElement)choiceDistMat.getSelectedItem());
  else if (argv[0].equals("scal")) {
    doc.view.layouts[0].setupScaling(argv[1], doc.view.minD/1.25f);  // FIXME: when does this insanity end?
    if (doc.view.r > -1) doc.view.layouts[0].updateProjection(doc.view.r, doc.view.D);
    if (doc.view.xmlDistMat != null) doc.view.xmlDistMat.setString("scaling", argv[1]);
  } else if (argv[0].equals("Map"))
    doc.view.viewMode = SVE2View.VIEW_MAP;
  else if (argv[0].equals("Tom"))
    doc.view.viewMode = SVE2View.VIEW_TOM;
  else if (argv[0].equals("Nodes"))
    doc.view.showNodes = !doc.view.showNodes;
  else if (argv[0].equals("Links")) {
    if (doc.view.showSkeleton) doc.view.showLinksWithSkeleton = !doc.view.showLinksWithSkeleton;
    else if (doc.view.showNeighbors) doc.view.showLinksWithNeighbors = !doc.view.showLinksWithNeighbors;
    else if (doc.view.showNetwork) doc.view.showLinksWithNetwork = !doc.view.showLinksWithNetwork;
    else doc.view.showLinks = !doc.view.showLinks;
  } else if (argv[0].equals("Skeleton")) {
    doc.view.showSkeleton = !doc.view.showSkeleton;
    if (doc.view.showSkeleton && doc.view.showNetwork) doc.view.showNetwork = false;
    if (doc.view.showSkeleton && doc.view.showNeighbors) doc.view.showNeighbors = false;
  } else if (argv[0].equals("Neighbors")) {
    if (doc.view.hasLinks) {
      doc.view.showNeighbors = !doc.view.showNeighbors;
      if (doc.view.showNeighbors && doc.view.showSkeleton) doc.view.showSkeleton = false;
      if (doc.view.showNeighbors && doc.view.showNetwork) doc.view.showNetwork = false;
    } else
      console.logWarning("Network links not available in data file");
  } else if (argv[0].equals("Network")) {
    if (doc.view.hasLinks) {
      doc.view.showNetwork = !doc.view.showNetwork;
      if (doc.view.showNetwork && doc.view.showSkeleton) doc.view.showSkeleton = false;
      if (doc.view.showNetwork && doc.view.showNeighbors) doc.view.showNeighbors = false;
    } else
      console.logWarning("Full network not available in data file");
  } else if (argv[0].equals("Labels"))
    doc.view.showLabels = !doc.view.showLabels;
  else if (argv[0].equals("dataset")) {
    if (!argv[1].equals(""))
      doc.setSelectedDataset((XMLElement)choiceDataset.getSelectedItem());
    else if (argv[2].equals("new")) {
      XMLElement xmlDataset = new XMLElement("dataset");
      xmlDataset.setString("name", "New Dataset");
      doc.addDataset(xmlDataset);
      doc.setSelectedDataset(xmlDataset);
      guiUpdateNodeColoring();
      actionPerformed("dataset####rename");
    } else if (argv[2].equals("rename"))
      new InPlaceRenamingTextField(gui, choiceDataset).show();
    else if (argv[2].equals("delete"))
      doc.removeDataset((XMLElement)choiceDataset.getSelectedItem());
  } else if (argv[0].equals("quantity")) {
    if (!argv[1].equals(""))
      doc.setSelectedQuantity((XMLElement)choiceQuantity.getSelectedItem());
    // else if (argv[2].equals("import"))
    //   new QuantityImportWizard(doc).start();
    else if (argv[2].equals("rename"))
      new InPlaceRenamingTextField(gui, choiceQuantity).show();
    else if (argv[2].equals("delete"))
      doc.removeQuantity((XMLElement)choiceQuantity.getSelectedItem());
  } else if (argv[0].equals("colormap"))
    doc.view.colormap.setColormap(argv[1]);
  else if (argv[0].equals("log"))
    doc.view.colormap.setLogscale(btnColormapLog.isSelected());
  else if (argv[0].equals("snapshot")) {
    TSlider source = null; XMLElement target = null;
    if (argv[1].equals("album")) { source = sldAlbumSnapshot; target = doc.getAlbum(); }
    else if (argv[1].equals("quantity")) { source = sldQuantitySnapshot; target = doc.getSelectedQuantity(); }
    else return;
    if (argv[2].equals("valueChanged"))
      doc.setSelectedSnapshot(target, source.getValue());
    else if (argv[2].equals("next"))
      doc.setSelectedSnapshot(target, +1, true);
    else if (argv[2].equals("prev"))
      doc.setSelectedSnapshot(target, -1, true);
  } else if (argv[0].equals("update"))
    checkForUpdates(true);
  guiUpdate();
}


class XMLElementRenderer extends TChoice.StringRenderer {
  boolean includeDataset = false;
  XMLElementRenderer() { this(false); }
  XMLElementRenderer(boolean includeDataset) { this.includeDataset = includeDataset; }
  public String getActionCommand(Object o) {
    XMLElement xml = (XMLElement)o;
    String str = xml.getString("id");
    if (includeDataset && xml.getName().equals("data") && (xml.getParent() != null))
      str = xml.getParent().getString("id") + "##" + str;
    return str;
  }
  public String getSnapshotLabel(XMLElement xml) {
    String res = xml.getString("label");
    if (res == null) try {
      res = doc.getChild(doc.getChild("album[@id=" + xml.getString("album") + "]"), "snapshot[@selected]").getString("label");
    } catch (Exception e) { /* ignore any NullPointerExceptions or other stuff */ }
    return res;
  }
  // FIXME: maybe the string should be cached? (and erased on invalidate())
  public String getString(Object o, boolean inMenu) {
    XMLElement xml = (XMLElement)o;
    String str = xml.getString("name", xml.getString("id"));
    if (includeDataset && inMenu && xml.getName().equals("data") && (xml.getParent() != null))
      str = xml.getParent().getString("name", xml.getParent().getString("id")) + ": " + str;
    XMLElement snapshot = doc.getSelectedSnapshot(xml);
    if (!inMenu && (snapshot != null)) {
      String strsnap = "";
      while (snapshot != xml) {
        strsnap = strsnap + " [" + getSnapshotLabel(snapshot) + "]";
        snapshot = snapshot.getParent();
      }
      str += strsnap;
    }
    return str;
  }
}

class TChoiceWithRollover extends TChoice {
  class SVE2DocumentRenderer extends StringRenderer {
    public boolean getEnabled(Object o) { return ((SVE2Document)o).view.hasMapLayout || ((SVE2Document)o).view.hasTomLayout; }
    public String getActionCommand(Object o) { return ((SVE2Document)o).getName(); }
    public TComponent.Dimension getPreferredSize(TChoice c, Object o, boolean inMenu) {
      SVE2Document doc = (SVE2Document)o;
      TComponent.Dimension d = super.getPreferredSize(c, doc.getName(), inMenu);
      if (inMenu) {
        textFont(gui.style.getFont());
        String desc = !getEnabled(o) ? " (loading...)" : " \u2013 " + doc.getTitle();
        d.width += 5 + textWidth(desc);
      }
      return d;
    }
    public void draw(TChoice c, PGraphics g, Object o, TComponent.Rectangle bounds, boolean inMenu) {
      SVE2Document doc = (SVE2Document)o;
      String name = doc.getName();
      noStroke();
      textFont(c.getFont());
      fill(getEnabled(o) ? c.getForeground() : color(127));
      textAlign(g.LEFT, g.BASELINE);
      float x = bounds.x;
      float y = bounds.y + bounds.height - g.textDescent();
      float h = g.textAscent() + g.textDescent();
      if (bounds.height > h) y -= (bounds.height - h)/2;
      text(name, x, y);
      if (inMenu) {
        x += textWidth(name) + 5;
        textFont(gui.style.getFont());
        String desc = !getEnabled(o) ? " (loading...)" : " \u2013 " + doc.getTitle();
        text(desc, x, y);
      }
    }
  }
  TComponent rollOverComponent = null;
  TChoiceWithRollover(TransparentGUI gui, String actionCmdPrefix) {
    super(gui, actionCmdPrefix); setRenderer(new SVE2DocumentRenderer()); }
  public void handleMouseEntered() { super.handleMouseEntered(); if (rollOverComponent != null) rollOverComponent.setVisible(true); }
  public void handleMouseExited() { super.handleMouseExited(); if (rollOverComponent != null) rollOverComponent.setVisible(false); }
}

class NetworkDetailPanel extends TComponent {
  NetworkDetailPanel(TransparentGUI gui) { super(gui); setPadding(5, 10); setMargin(0);
    setBackgroundColor(gui.style.getBackgroundColorForCompactGroups()); }
  public TComponent.Dimension getMinimumSize() {
    if ((doc == null) || !doc.view.hasNodes) return new TComponent.Dimension(0, 0);
    textFont(fnLarge);
    float width = textWidth(doc.getTitle()), height = textAscent() + 1.5f*textDescent();
    textFont(fnMedium);
    String networkMeta[] = split(doc.getDescription(), '\n');
    for (int i = 0; i < networkMeta.length; i++) {
      width = max(width, textWidth(networkMeta[i]));
      height += textAscent() + 1.5f*textDescent();
    }
    return new TComponent.Dimension(width, height);
  }
  public void draw(PGraphics g) {
    if ((doc == null) || !doc.view.hasNodes) return;
    super.draw(g);
    float x = bounds.x + padding.left, y = bounds.y + padding.top;
    g.textAlign(LEFT, BASELINE);
    g.textFont(fnLarge);
    g.noStroke();
    g.fill(0);
    y += g.textAscent() + .5f*g.textDescent();
    g.text(doc.getTitle(), x, y);
    y += g.textDescent();
    g.fill(127);
    g.textFont(fnMedium);
    String networkMeta[] = split(doc.getDescription(), '\n');
    for (int i = 0; i < networkMeta.length; i++) {
      y += g.textAscent() + .5f*g.textDescent();
      text(networkMeta[i], x, y);
      y += g.textDescent();
    }
  }
}

class InPlaceRenamingTextField extends TTextField {
  TChoice choice = null;
  XMLElement xml = null;

  InPlaceRenamingTextField(TransparentGUI gui, TChoice choice) { super(gui); this.choice = choice; }

  public TComponent.Dimension getPreferredSize() {
    return new TComponent.Dimension(max(choice.getWidth(), 50), choice.getHeight()); }

  public void show() {
    xml = (XMLElement)choice.getSelectedItem();
    if (xml == null) return;
    setText(xml.getString("name"));
    setSelection(0, text.length());
    TContainer parent = choice.getParent();
    for (int i = 0; i < parent.getComponentCount(); i++)
      if (parent.getComponent(i) == choice)
        { parent.add(this, choice.getLayoutHint(), i); break; }
    choice.setVisibleAndEnabled(false);
    parent.validate();
    gui.requestFocus(this);
  }

  public void draw(PGraphics g) {
    if (isFocusOwner())
      super.draw(g);
    else {
      if (text.length() > 0)
        xml.setString("name", text);
      choice.setVisibleAndEnabled(true);
      getParent().remove(this);
    }
  }
}
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







float linkLineWidth = 0.25f;

/*
 *  Visual Explorer Document Viewer / Data Structures
 */

class SVE2View {

  SVE2Document doc = null;

  SVE2View(SVE2Document doc) { this.doc = doc; }

  class Node {
    String id, label, name;  // node ID, short label and full name
    float poslat, poslong, w;  // geographical position and strength  // FIXME: remove the node strength stuff and use proper showLabel framework
    boolean showLabel;  // whether or not the node should make cookies on every 2nd Friday of the spring months
    float x, y, a;  // current screen position, and alpha factor (0\u20131)
    Node(XMLElement node) {
      id = node.getString("id");
      label = node.getString("label", (id == null) ? "" : id);
      name = node.getString("name", label);
      w = node.getFloat("strength", 0);
      showLabel = node.getBoolean("showlabel");
      String pieces[] = split(node.getString("location", random(-1,1) + "," + random(-1,1)), ',');
      poslong = parseFloat(pieces[0]);
      poslat = parseFloat(pieces[1]);
      x = Float.NaN;
      y = Float.NaN;
      a = 0;
    }
  }

  class SortedLinkList {
    int NN, NL;  // number of nodes (max value in src[] and dst[]) and number of links
    int src[] = null, dst[] = null;
    float value[] = null;
    float minval, maxval;
    boolean sorted = false;
    SortedLinkList(SparseMatrix sm) { this(sm, false, false, false); }
    SortedLinkList(SparseMatrix sm, boolean logValues) { this(sm, logValues, false, false); }
    SortedLinkList(SparseMatrix sm, boolean logValues, boolean asyncSort) { this(sm, logValues, asyncSort, false); }
    SortedLinkList(SparseMatrix sm, boolean logValues, boolean asyncSort, boolean upperTriangleOnly) {
      NN = sm.N;
      // create arrays
      NL = 0; for (int i = 0; i < NN; i++) NL += sm.index[i].length;
      src = new int[NL]; dst = new int[NL]; value = new float[NL];
      // copy values
      NL = 0; minval = Float.POSITIVE_INFINITY; maxval = Float.NEGATIVE_INFINITY;
      for (int i = 0; i < NN; i++) {
        for (int l = 0; l < sm.index[i].length; l++) {
          if (upperTriangleOnly && (sm.index[i][l] < i)) continue;
          src[NL] = i; dst[NL] = sm.index[i][l];
          value[NL] = logValues ? log(sm.value[i][l]) : sm.value[i][l];
          minval = min(minval, value[NL]);
          maxval = max(maxval, value[NL]);
          NL++;
        }
      }
      // sort
      if (asyncSort) worker.submit(new Runnable() { public void run() { sort(); } });
      else sort();
    }
    public void sort() {
      // Is it stupid to manually implement a sort algorithm here?
      // I don't want to use one Object per link and I'd like to have
      // small synchronized {} brackets so that the drawing routing can
      // already visualize the full network with partially sorted links.
      sorted = false;
      sort(0, NL);
      sorted = true;
    }
    private void sort(int i0, int i1) {
      // [in-place quicksort from wikipedia]
      // * will sort sublist from index i0 to i1 (incl.)
      // * assume no other concurring thread writes to the object,
      //   i.e., concurring read ops are ok, only need to sync write ops;
      //   other threads need to sync read ops as well
      if (i1 <= i0) return;  // only need to sort lists of length > 1
      // select pivot value (median of first/middle/last)
      int ip = i0 + (i1 - i0)/2;  // assume pivot is middle element
      if (((value[i0] > value[ip]) && (value[i0] < value[i1])) || ((value[i0] < value[ip]) && (value[i0] > value[i1])))
        ip = i0;  // first element is median of first/middle/last
      else if (((value[i1] > value[ip]) && (value[i1] < value[i0])) || ((value[i1] < value[ip]) && (value[i1] > value[i0])))
        ip = i1;  // last element is median of first/middle/last
      // partition
      float pivot = value[ip];
      synchronized (this) {  // this could even go inside swap()...
        swap(ip, i1);  // "park" pivot at end
        ip = i0;  // assume pivot will go at the left end
        for (int i = i0; i < i1; i++)
          if (value[i] < pivot)  // if i-th value is smaller than pivot
            swap(i, ip++);  // then add to left list and move pivot one to the right
        swap(ip, i1);  // put pivot value at proper position
      }
      // recurse
      sort(i0, ip - 1);
      sort(ip + 1, i1);
    }
    private void swap(int i, int j) {
      int tmpi; float tmpf;
      tmpi = src[i]; src[i] = src[j]; src[j] = tmpi;
      tmpi = dst[i]; dst[i] = dst[j]; dst[j] = tmpi;
      tmpf = value[i]; value[i] = value[j]; value[j] = tmpf;
    }
  }

  // nodes
  boolean hasNodes = false;
  int NN = -1, ih = -1;  // number of nodes, currently hovered node
  Node nodes[] = null;
  // links
  boolean hasLinks = false;
  XMLElement xmlLinks = null;
  SparseMatrix links = null;
  SortedLinkList loglinks = null;
  // map layout
  boolean hasMapLayout = false;
  XMLElement xmlProjection = null;
  Projection projMap = null;
  // node coloring data
  boolean hasData = false;
  XMLElement xmlData = null;
  Colormap colormap = null;
  final int DATA_1D = 0, DATA_2D = 1;
  int datatype = DATA_1D;
  float data[][] = null;
  // slices
  boolean hasSlices = false;
  XMLElement xmlSlices = null;
  int r = -1;  // current root node
  int pred[][] = null;  // predecessor vectors
  SparseMatrix salience = null;  // salience matrix (fraction of slices in which each link participates)
  // tomogram layouts
  boolean hasTomLayout = false;
  String tomLayouts = null;
  int NL = -1, l = -1;
  Layout layouts[] = null;
  // tomogram distance matrix
  XMLElement xmlDistMat = null;
  float D[][] = null;  // distance matrix
  float minD = Float.POSITIVE_INFINITY, maxD = Float.NEGATIVE_INFINITY;

  // current view parameters
  public final static int VIEW_MAP = 0;
  public final static int VIEW_TOM = 1;
  int viewMode = VIEW_MAP;
  boolean showNodes = true;
  boolean showLinks = true;
  boolean showLinksWithSkeleton = false;
  boolean showLinksWithNeighbors = false;
  boolean showLinksWithNetwork = false;
  boolean showSkeleton = false;
  boolean showNeighbors = false;
  boolean showNetwork = false;
  boolean showLabels = true;
  float zoom[] = { 1, 1 };
  float xoff[] = { 0, 0 };
  float yoff[] = { 0, 0 };
  float nodeSizeFactor = 0.2f;

  public void setNodes(XMLElement xmlNodes) {
    hasNodes = false;
    XMLElement tmp[] = null;
    if ((xmlNodes == null) || ((tmp = xmlNodes.getChildren("node")).length < 1))
      return;
    nodes = new Node[NN = tmp.length];
    for (int i = 0; i < NN; i++)
      nodes[i] = new Node(tmp[i]);
    float maxw = Float.NEGATIVE_INFINITY;
    for (int i = 0; i < NN; i++)
      if (nodes[i].w > maxw) { maxw = nodes[i].w; r = i; }
    for (int i = 0; i < NN; i++)
      nodes[i].showLabel = nodes[i].w > .4f*maxw;
    hasNodes = true;
  }

  public void setMapProjection(XMLElement xmlProjection) {
    hasMapLayout = false;
    if (!hasNodes) return;
    this.xmlProjection = xmlProjection;
    if ((xmlProjection == null) || !MapProjectionFactory.canProduce(xmlProjection.getString("name")))
      setMapProjection(MapProjectionFactory.getDefaultProduct());
    else {
      projMap = MapProjectionFactory.produce(xmlProjection.getString("name"), NN);
      projMap.beginData();
      for (int i = 0; i < NN; i++)
        projMap.setPoint(i, nodes[i].poslat, nodes[i].poslong);
      projMap.endData();
      hasMapLayout = true;
    }
  }

  public void setMapProjection(String name) {
    if (!hasNodes) return;
    if (xmlProjection == null)
      xmlProjection = new XMLElement("projection");
    xmlProjection.setString("name", name);
    setMapProjection(xmlProjection);
  }

  public void setRootNode(int i) {
    if (!hasNodes || (i < 0) || (i >= NN)) return;
    r = i;
    if (hasTomLayout) layouts[l].updateProjection(r, D);
  }

  public void setLinks(XMLElement xmlLinks) { setLinks(xmlLinks, console); }
  public void setLinks(XMLElement xmlLinks, TConsole console) {
    hasLinks = false;
    this.xmlLinks = xmlLinks;
    if (!hasNodes || (xmlLinks == null)) return;
    BinaryThing blob = doc.getBlob(xmlLinks);
    if (blob == null) { console.logError("Data for links \u201C" + xmlLinks.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
    if (!blob.isSparse(NN)) { console.logError("Data format error (expected SparseMatrix[" + NN + "]): " + blob); return; }
    links = blob.getSparseMatrix();
    // BEGIN work-around (save_spato.m used to save matrices with 1-based indices in binary files before June 3, 2011)
    if (xmlLinks.getString("blob") != null) {
      boolean hasZeroIndex = false, hasIllegalIndices = false;
      for (int i = 0; i < NN; i++) {
        for (int l = 0; l < links.index[i].length; l++) {
          if (links.index[i][l] == 0) hasZeroIndex = true;
          if (links.index[i][l] >= NN) hasIllegalIndices = true;
        }
      }
      if (!hasZeroIndex && hasIllegalIndices) {
        console.logWarning("Correcting indices in the sparse weight matrix to zero-based");
        for (int i = 0; i < NN; i++)
          for (int l = 0; l < links.index[i].length; l++)
            links.index[i][l]--;
      }
    }
    // END work-around
    loglinks = new SortedLinkList(links, true, true, true);
    hasLinks = true;
  }

  public void setSlices(XMLElement xmlSlices) { setSlices(xmlSlices, console); }
  public void setSlices(XMLElement xmlSlices, TConsole console) {
    hasSlices = false;
    if (!hasNodes || ((xmlSlices == null) && (!hasLinks))) return;
    // prepare data structure
    pred = new int[NN][NN];
    D = new float[NN][NN];
    // read data
    if (xmlSlices != null) {
      BinaryThing blob = doc.getBlob(xmlSlices);
      if (blob == null) { console.logError("Data for slices \u201C" + xmlSlices.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
      if (!blob.isInt2(NN)) { console.logError("Data format error (expected int[" + NN + "][" + NN + "]): " + blob); return; }
      pred = blob.getIntArray();
    } else {
      // calculate shortest path trees from scratch
      boolean inverse = xmlLinks.getBoolean("inverse");
      console.logProgress("Calculating shortest-path trees");
      for (int r = 0; r < NN; r++) {
        Dijkstra.calculateShortestPathTree(links.index, links.value, r, pred[r], D[r], inverse);
        console.updateProgress(r, NN);
      }
      console.finishProgress();
      // process data
      minD = Float.POSITIVE_INFINITY;
      maxD = Float.NEGATIVE_INFINITY;
      float mean = 0; int meanCount = 0;
      for (int r = 0; r < NN; r++) {
        maxD = max(maxD, max(D[r]));
        for (int i = 0; i < NN; i++) {
          if (r == i) continue;
          minD = min(minD, D[r][i]);
          mean += D[r][i]; meanCount++;
        }
      }
      mean /= meanCount;
      // add SPTs as slices
      xmlSlices = doc.getSlices();
      if (xmlSlices == null)
        xmlSlices = doc.getChild("slices[@name=Shortest-Path Trees]");
      if (xmlSlices == null)
        doc.xmlDocument.addChild(xmlSlices = new XMLElement("slices"));
      xmlSlices.setString("id", "spt");
      xmlSlices.setString("name", "Shortest-Path Trees");
      while (xmlSlices.hasChildren())
        xmlSlices.removeChild(0);
      doc.setBlob(xmlSlices, pred, true);
      // add SPD to distance measures dataset
      XMLElement xmlDataset = doc.getDataset("dist");
      if (xmlDataset == null)  // try by name
        xmlDataset = doc.getChild("dataset[@name=Distance Measures]");
      if (xmlDataset == null)  // create new
        doc.xmlDocument.addChild(xmlDataset = new XMLElement("dataset"));
      xmlDataset.setString("id", "dist");
      xmlDataset.setString("name", "Distance Measures");
      xmlDistMat = doc.getQuantity(xmlDataset, "spd");
      if (xmlDistMat == null)
        xmlDistMat = doc.getChild(xmlDataset, "data[@name=SPD]");
      if (xmlDistMat == null)
        xmlDataset.insertChild(xmlDistMat = new XMLElement("data"), 0);
      xmlDistMat.setString("id", "spd");
      xmlDistMat.setString("name", "SPD");
      while (xmlDistMat.getChild("values") != null)
        xmlDistMat.removeChild(xmlDistMat.getChild("values"));
      while (xmlDistMat.getChild("colormap") != null)
        xmlDistMat.removeChild(xmlDistMat.getChild("colormap"));
      String clog = (meanCount > 0) && (mean < minD + (maxD - minD)/4) ? " log=\"true\"" : "";
      xmlDistMat.addChild(XMLElement.parse(
        String.format("<colormap%s minval=\"%g\" maxval=\"%g\" />", clog, minD, maxD)));
      doc.setBlob(xmlDistMat, D, true);
    }
    // calculate salience matrix
    console.logProgress("Calculating salience matrix");
    int abundance[][] = new int[NN][NN];
    float salienceFull[][] = new float[NN][NN];
    for (int root = 0; root < NN; root++) {
      for (int i = 0; i < NN; i++)
        if (pred[root][i] != -1)
          abundance[i][pred[root][i]]++;
      console.updateProgress(root, 2*NN);
    }
    for (int i = 0; i < NN; i++) {
      for (int j = 0; j < NN; j++)
        salienceFull[i][j] = salienceFull[j][i] = (float)(abundance[i][j] + abundance[j][i])/NN;
      console.updateProgress(NN+i, 2*NN);
    }
    salience = new SparseMatrix(salienceFull);  // FIXME: performance? (SparseMatrix uses a lot of append())
    console.finishProgress();
    // done
    this.xmlSlices = xmlSlices;
    hasSlices = true;
  }

  public void setNodeColoringData(XMLElement xmlData) {
    hasData = false;
    data = null;
    colormap = null;
    this.xmlData = xmlData;
    if (xmlData == null) return;
    // read values
    BinaryThing blob = doc.getBlob(xmlData);
    if (blob == null) { console.logError("Data for quantity \u201C" + xmlData.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
    if (blob.isFloat1(NN)) datatype = DATA_1D;
    else if (blob.isFloat2(NN)) datatype = DATA_2D;
    else { console.logError("Data format error (expected float[1][" + NN + "] or float[" + NN + "][" + NN + "]): " + blob); return; }
    data = blob.getFloatArray();
    // process values
    float mindata = Float.POSITIVE_INFINITY;
    float maxdata = Float.NEGATIVE_INFINITY;
    for (int root = 0; root < data.length; root++) {
      for (int j = 0; j < data[root].length; j++) {
        if (Float.isInfinite(data[root][j]) || Float.isNaN(data[root][j])) continue;
        mindata = min(mindata, data[root][j]);
        maxdata = max(maxdata, data[root][j]);
      }
    }
    // setup colormap
    XMLElement xmlColormap = doc.getColormap(xmlData);
    if (xmlColormap == null)  // make sure there is a <colormap> tag we can write to later
      xmlData.addChild(xmlColormap = new XMLElement("colormap"));
    colormap = new Colormap(xmlColormap, mindata, maxdata);
    //colormap = new Colormap(xmlColormap.getString("name", "default"), xmlColormap.getBoolean("log"), mindata, maxdata);  // FIXME
    // finished
    hasData = true;
  }

  public void setTomLayout() {
    hasTomLayout = false;
    if (!hasNodes || !hasSlices) return;
    if (tomLayouts == null) tomLayouts = "radial_id";
    String layoutNames[] = split(tomLayouts, ' ');
    layouts = new Layout[NL = layoutNames.length];
    for (int l = 0; l < NL; l++)
      layouts[l] = new Layout(pred, layoutNames[l]);
    layouts[this.l = 0].updateProjection(r, D);
    hasTomLayout = true;
  }

  public void setDistanceMatrix(XMLElement xmlData) { setDistanceMatrix(xmlData, console); }
  public void setDistanceMatrix(XMLElement xmlData, TConsole console) {
    if (!hasTomLayout) return;
    xmlDistMat = xmlData;
    if (xmlData != null) {
      // read values
      BinaryThing blob = doc.getBlob(xmlData);
      if (blob == null) { console.logError("Data for quantity \u201C" + xmlData.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
      if (!blob.isFloat2(NN)) { console.logError("Data format error (expected float[" + NN + "][" + NN + "]): " + blob); return; }
      D = blob.getFloatArray();
    } else
      D = null;
    // process values
    if (D == null) D = new float[NN][NN];
    minD = Float.POSITIVE_INFINITY;
    maxD = Float.NEGATIVE_INFINITY;
    for (int root = 0; root < NN; root++) {
      for (int j = 0; j < NN; j++) {
        if ((pred[root][j] == -1) && (root != j))  // ignore values of disconnected nodes
          D[root][j] = Float.POSITIVE_INFINITY;
        if (!Float.isInfinite(D[root][j]) && D[root][j] > 0)  // minD = 0 will mess up the log-scale calibration, so ensure minD > 0
          minD = min(minD, D[root][j]);
        if (!Float.isInfinite(D[root][j]) && !Float.isNaN(D[root][j]))
          maxD = max(maxD, D[root][j]);
      }
    }
    // update projection etc.
    String scaling = null;
    if (xmlData != null) {
      scaling = xmlData.getString("scaling", null);
      if (scaling == null) {
        XMLElement xmlColormap = doc.getColormap(xmlData);
        if (xmlColormap != null) {
          scaling = xmlColormap.getBoolean("log") ? "log" : "id";
          xmlData.setString("scaling", scaling);  // save for later
        }
      }
    }
    if (scaling == null)
      scaling = "id";  // default for tree depth distance
    layouts[l].setupScaling(scaling, minD/1.25f);
    if (r > -1) layouts[l].updateProjection(r, D);
  }


  float a = 0, nodeSize = 0;
  float[] tmpx = null, tmpy = null;
  float viewWidth = width;
  float aNodes = 0, aLinks = 0, aSkeleton = 0, aNeighbors = 0, aNetwork = 0, aLabels = 0;

  public void draw() {
    if (!hasNodes || (!hasMapLayout && !hasTomLayout)) return;  // nothing to draw
    if ((showNeighbors || showNetwork) && !hasLinks) {
      showNeighbors = false; aNeighbors = 0; showNetwork = false; aNetwork = 0; }  // can't draw full network
    Projection p = ((viewMode == VIEW_MAP) || !hasTomLayout) ? projMap : layouts[0].proj;
    boolean linksVisible = (showLinks && !showSkeleton && !showNeighbors && !showNetwork) ||
      (showSkeleton && showLinksWithSkeleton) || (showNeighbors && showLinksWithNeighbors) || (showNetwork && showLinksWithNetwork);
    aNodes += 3*((showNodes ? 1 : 0) - aNodes)*min(dt, 1/3.f);
    aLinks += 3*((linksVisible ? 1 : 0) - aLinks)*min(dt, 1/3.f);
    aSkeleton += 3*((showSkeleton ? 1 : 0) - aSkeleton)*min(dt, 1/3.f);
    aNeighbors += 3*((showNeighbors ? 1 : 0) - aNeighbors)*min(dt, 1/3.f);
    aNetwork += 3*((showNetwork ? 1 : 0) - aNetwork)*min(dt, 1/3.f);
    aLabels += 3*((showLabels ? 1 : 0) - aLabels)*min(dt, 1/3.f);
    // get current data
    float[] val = null;
    if (hasData) {
      switch (datatype) {
        case DATA_1D: val = data[0]; break;
        case DATA_2D:
          if (!isAltDown && (r > -1)) val = data[r];
          if (isAltDown && (ih > -1)) val = data[ih];
          break;
      }
    }
    if ((xmlDistMat == null) && (viewMode == VIEW_TOM)) {
      minD = 1;
      for (int i = 0; i < NN; i++)
        maxD = max(maxD, D[r][i] = (pred[r][i] == -1) ? 0 : D[r][pred[r][i]] + 1);
      layouts[l].updateProjection(r, D);
    }
    // update node positions and determine hovered node
    boolean wrap = ((viewMode == VIEW_MAP) && xmlProjection.getString("name").equals("LonLat Roll"));
    if ((tmpx == null) || (tmpx.length != NN)) { tmpx = new float[NN]; tmpy = new float[NN]; }
    p.setScalingToFitWithin(wrap ? width : .9f*width, .9f*height);
    if (wrap) {  // FIXME: wrapping should be handled by the projection
      float targetViewWidth = (wrap ? width : .9f*width)*zoom[viewMode];  // width of the scaled data (used for wrapping)
      viewWidth += 3*(targetViewWidth - viewWidth)*min(dt, 1/3.f);
      while (xoff[viewMode] > +viewWidth) { xoff[viewMode] -= viewWidth; for (int i = 0; i < NN; i++) nodes[i].x -= viewWidth; }
      while (xoff[viewMode] < -viewWidth) { xoff[viewMode] += viewWidth; for (int i = 0; i < NN; i++) nodes[i].x += viewWidth; }
    }
    float mind = width*height;
    ih = -1;  // currently hovered node
    for (int i = 0; i < NN; i++) {
      boolean invis = (viewMode == VIEW_TOM) && (pred[i][r] == -1) && (i != r);
      float tx = invis ? nodes[i].x : p.sx*(p.x[i] - p.cx)*zoom[viewMode] + xoff[viewMode] + width/2;
      float ty = invis ? nodes[i].y : p.sy*(p.y[i] - p.cy)*zoom[viewMode] + yoff[viewMode] + height/2;
      float ta = invis ? 0 : 1;
      nodes[i].x = Float.isNaN(nodes[i].x) ? tx : nodes[i].x + 3*(tx - nodes[i].x)*min(dt, 1/3.f);
      nodes[i].y = Float.isNaN(nodes[i].y) ? ty : nodes[i].y + 3*(ty - nodes[i].y)*min(dt, 1/3.f);
      nodes[i].a += 3*(ta - nodes[i].a)*min(dt, 1/3.f);
      if (wrap) {
        tmpx[i] = nodes[i].x; tmpy[i] = nodes[i].y;
        if (nodes[i].x - width/2 > +viewWidth/2) nodes[i].x -= viewWidth;
        if (nodes[i].x - width/2 < -viewWidth/2) nodes[i].x += viewWidth;
      }
      float d = dist(nodes[i].x, nodes[i].y, mouseX, mouseY);
      if (!invis &&
          (gui.componentAtMouse == null) && (gui.componentMouseClicked == null) &&
          (d < 50) && (d < mind) &&
          (!searchMatchesValid || !tfSearch.isFocusOwner() ||
           searchMatches[i] || (searchMatchesChild[i] > 0))) {
        mind = d; ih = i; }
    }
    // draw links
    if (hasSlices && ((aLinks > 1/192.f) || (aSkeleton > 1/192.f) || (aNeighbors > 1/192.f) || (aNetwork > 1/192.f))) {
      noFill(); strokeWeight(linkLineWidth);
      // update search matches
      if (searchMatchesValid) {
        // update branch matching flags (would need to be recursive, but we are sloppy here)
        // 1: node i matches search
        // 0: node i does not match and we don't know of any children who match
        // 2: node i does not match but we used to have matching children
        for (int i = 0; i < NN; i++)
          searchMatchesChild[i] = searchMatches[i] ? 1 : 2*searchMatchesChild[i];
        // if we think that node i has a matching child, tell the parent of node i
        for (int i = 0; i < NN; i++)
          if ((searchMatchesChild[i] > 0) && (pred[r][i] != -1))
            searchMatchesChild[pred[r][i]] = 1;
        // if any of the nodes with status 2 has not been set to 1 by now, there is no matching child
        for (int i = 0; i < NN; i++)
          if (searchMatchesChild[i] == 2)
            searchMatchesChild[i] = 0;
      }
      // visualize salience matrix
      if (aSkeleton > 1/192.f) {
        for (int i = 0; i < NN; i++) {
          for (int l = 0; l < salience.index[i].length; l++) {
            int j = salience.index[i][l];
            if (j >= i) continue;  // avoid drawing duplicate links
            if (salience.value[i][l] == 0) continue;  // not a salient link
            stroke(192*(1 - salience.value[i][l]), 192*aSkeleton);
            // FIXME: the following code is copy'n'pasted...
            if (!wrap || (abs(nodes[i].x - nodes[j].x) < viewWidth/2))
              line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
            else {
              if (nodes[i].x < nodes[j].x) {
                line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
                line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              } else {
                line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
                line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
              }
            }
          }
        }
      }
      // show direct neighbors of current root node
      if (aNeighbors > 1/192.f) synchronized (loglinks) {  // sync against SortedLinkList.sort()
        int i0 = isAltDown ? ih : r;  // show neighbors of hovered node if Alt is held down
        for (int l = 0; l < loglinks.NL; l++) {
          int i = loglinks.src[l], j = loglinks.dst[l];
          if ((i != i0) && (j != i0)) continue;
          stroke(192*(1 - (loglinks.value[l] - loglinks.minval)/(loglinks.maxval - loglinks.minval)), 192*aNeighbors);
          // FIXME: the following code is copy'n'pasted...
          if (!wrap || (abs(nodes[i].x - nodes[j].x) < viewWidth/2))
            line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
          else {
            if (nodes[i].x < nodes[j].x) {
              line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
              line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
            } else {
              line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
            }
          }
        }
      }
      // show full network
      if (aNetwork > 1/192.f) synchronized (loglinks) {  // sync against SortedLinkList.sort()
        for (int l = 0; l < loglinks.NL; l++) {
          int i = loglinks.src[l], j = loglinks.dst[l];
          stroke(192*(1 - (loglinks.value[l] - loglinks.minval)/(loglinks.maxval - loglinks.minval)), 192*aNetwork);
          // FIXME: the following code is copy'n'pasted...
          if (!wrap || (abs(nodes[i].x - nodes[j].x) < viewWidth/2))
            line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
          else {
            if (nodes[i].x < nodes[j].x) {
              line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
              line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
            } else {
              line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
            }
          }
        }
      }
      // draw links in selected slice
      if (aLinks > 1/192.f) {
        float aSNN = max(aSkeleton, max(aNeighbors, aNetwork));
        if (!showLinksWithSkeleton && !showLinksWithNeighbors && !showLinksWithNetwork && !(aSNN > 1 - 1/192.f)) aSNN = 0;
        float aN = (showLinksWithNetwork || (aNetwork > 1 - 1/192.f)) ? aNetwork : 0;
        strokeWeight(min(1, linkLineWidth + aN*aLinks));  // strongly emphasize slice if drawing over full network
        stroke(64 + 128*aSNN, 64*(1 - aSNN), 64*(1 - aSNN), 192*aLinks + 63*aSNN);
        for (int i = 0; i < NN; i++) {
          int j = pred[r][i];
          if (j == -1) continue;  // don't draw links from disconnected (or root) nodes
          if (searchMatchesValid) {
            float alphafactor = (searchMatches[i] || (searchMatchesChild[i] > 0)) ? 1.25f : 0.25f;
            stroke(64 + 128*aSNN, 64*(1 - aSNN), 64*(1 - aSNN), min(255, (192*aLinks + 63*aSNN)*alphafactor));
            strokeWeight((alphafactor > 1) ? 1 : 0.25f);
          }
          if (!wrap || (abs(nodes[i].x - nodes[j].x) < viewWidth/2))
            line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
          else {
            if (nodes[i].x < nodes[j].x) {
              line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
              line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
            } else {
              line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
            }
          }
        }
      }
      strokeWeight(1);
    }
    // draw nodes
    rectMode(CENTER);
    float nodeSize_target = nodeSizeFactor*sqrt(min(width, height))*sqrt(zoom[viewMode]);
    nodeSize += 3*(nodeSize_target - nodeSize)*min(dt, 1/3.f);
    if (aNodes > 1/192.f) {
      noStroke();
      for (int i = 0; i < NN; i++) {
        float alphafactor = !searchMatchesValid ? 1 : (searchMatches[i] ? 1.25f : 0.125f);
        fill((val == null) ? 127 : colormap.getColor(val[i]), 192*aNodes*nodes[i].a*alphafactor);
        if (fastNodes)
          rect(nodes[i].x + .5f, nodes[i].y + .5f, 3*nodeSize/4, 3*nodeSize/4);
        else
          ellipse(nodes[i].x, nodes[i].y, nodeSize, nodeSize);
      }
    }
    // mark root node and hovered node (these are shown even if showNodes is false)
    if (r > -1) {
      stroke(255, 0, 0); noFill();
      if (fastNodes) rect(nodes[r].x, nodes[r].y, 3*nodeSize/4 + .5f, 3*nodeSize/4 + .5f);
      else ellipse(nodes[r].x, nodes[r].y, nodeSize + .5f, nodeSize + .5f);
    }
    if (ih > -1) {
      stroke(200, 0, 0); noFill();
      if (fastNodes) rect(nodes[ih].x, nodes[ih].y, 3*nodeSize/4 + .5f, 3*nodeSize/4 + .5f);
      else ellipse(nodes[ih].x, nodes[ih].y, nodeSize + .5f, nodeSize + .5f);
    }
    rectMode(CORNER);
    // draw labels
    textFont(/*fnSmall*/fnMedium);
    textAlign(LEFT, BASELINE);
    noStroke();
    for (int i = 0; i < NN; i++) {
      if ((i == r) || (i == ih) || ((aLabels > 1/255.f) && nodes[i].showLabel && (!searchMatchesValid || searchMatches[i]))) {
        fill(0, 255*aLabels*nodes[i].a);
        text(nodes[i].label + (((i == ih) && (val != null)) ? " (" + format(val[i]) + ")" : ""),
             nodes[i].x + nodeSize/4, nodes[i].y - nodeSize/4);
      }
    }
    // reset nodes.x/y if wrapping is on
    if (wrap)
      for (int i = 0; i < NN; i++)
        { nodes[i].x = tmpx[i]; nodes[i].y = tmpy[i]; }
  }

  public String format(float val) {
    if (val == 0)
      return "0";
    else if ((val > 1 - 1e-7f) && (val < 100000)) {
      int nd = 3; if (val >= 10) nd--; if (val >= 100) nd--; if (val >= 1000) nd--;
      String res = nfc(val, nd);
      if (nd > 0) res = res.replaceAll("0+$", "").replaceAll("\\.$", "");
      return res;
    } else
      return String.format("%.4g", val);
  }

  public void writeLayout(String filename) {
    String lines[] = new String[NN];
    Projection p = ((viewMode == VIEW_MAP) || !hasTomLayout) ? projMap : layouts[0].proj;
    if (p == null)
      lines = new String[] { "Error! No valid node layout available..." };
    else for (int i = 0; i < NN; i++)
      lines[i] = new String(p.x[i] + "\t" + p.y[i] + "\t" + nodes[i].label);
    saveStrings(filename, lines);
  }
}

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

File workspaceFile = null;
boolean showWorkspaceRecoveryButton = true;
SVE2Document doc = null;  // current document
Vector<SVE2Document> docs = new Vector<SVE2Document>();  // all loaded documents

/*
 * Document Management
 */

// This FileFilter accepts:
//   1) directories ending with ".spato" if they contain a document.xml
//   2) files ending with ".spato" if they are zip files
//   3) files named "document.xml" if they are in directory ending with ".spato"
// It's probably a good idea to allow case (3), because selecting directories might
// seem unusally awkward for some users (it's still possible, though).
static FileFilter ffDocuments = new FileFilter() {
 public String getDescription() { return "SPaTo Documents"; }
 public boolean accept(File f) {
   if (f.getName().endsWith(".spato")) {
     if (f.isDirectory()) {  // accept .spato directory if it contains a document.xml
       for (File ff : f.listFiles())
         if (ff.getName().equals("document.xml"))
           return true;
       return false;
     } else  // accept .spato files if it's a zip file
        try { new ZipFile(f); return true; } catch (Exception e) { return false; }
   }
   if (f.getName().equals("document.xml"))  // accept document.xml if it's inside a .spato directory
     return f.getParent().endsWith(".spato");
   return false;
 }
};

public File[] selectDocumentFilesToOpen() {
  File result[] = selectFiles(OPENMULTIPLE, "Open document", ffDocuments);
  for (int i = 0; i < result.length; i++)            // normalize filename if some
    if (result[i].getName().equals("document.xml"))  // blabla.spato/document.xml
      result[i] = result[i].getParentFile();         // was selected
  return result;
}
public File selectDocumentFileToWrite() { return selectDocumentFileToWrite(null); }
public File selectDocumentFileToWrite(File selectedFile) {
  return ensureExtension("spato", selectFile(SAVE, "Save document", ffDocuments, selectedFile)); }

public void newDocument() {
  SVE2Document newdoc = new SVE2Document();
  docs.add(newdoc);
  switchToNetwork(newdoc);
//  new LinksImportWizard(newdoc).start();
}

public void openDocument() { openDocuments(selectDocumentFilesToOpen()); }
public void openDocument(File file) { if (file != null) openDocuments(new File[] { file }); }
public void openDocuments(File files[]) {
  if (files == null) return;
  for (File f : files) {
    SVE2Document newdoc = null;
    for (SVE2Document d : docs)
      if (d.getFile().equals(f))
        newdoc = d;  // this document is already open
    if (newdoc == null) {  // load if not already open
      newdoc = new SVE2Document(f);
      docs.add(newdoc);
      worker.submit(newdoc.newLoadingTask());
    }
    switchToNetwork(newdoc);
  }
}

public void closeDocument() {
  int i = docs.indexOf(doc);
  if (i == -1) return;
  docs.remove(i);
  switchToNetwork((docs.size() > 0) ? docs.get(i % docs.size()) : null);
  //worker.remove(doc);  // cancel any jobs in the worker thread that are related to this document
}

public void saveDocument() { saveDocument(false); }
public void saveDocument(boolean forceSelect) {
  if (doc == null) return;
  File file = doc.getFile();
  for (SVE2Document d : docs)
    if ((d != doc) && d.getFile().equals(file))
      docs.remove(d);  // prevent duplicate entries in docs
  if ((file == null) || forceSelect) {
    if ((file = selectDocumentFileToWrite(file)) == null) return;
    boolean compressed = !file.exists() || !file.isDirectory();  // save compressed by default
    doc.setFile(file, compressed);
    updateWorkspacePref();
  }
  worker.submit(doc.newSavingTask());
}

public boolean switchToNetwork(int i) { return switchToNetwork(((i < 0) || (i >= docs.size())) ? docs.get(i) : null); }
public boolean switchToNetwork(String name) {
  SVE2Document newdoc = null;
  for (int i = 0; i < docs.size(); i++)
    if (name.equals(docs.get(i).getName()))
      newdoc = docs.get(i);
  return switchToNetwork(newdoc);
}
public boolean switchToNetwork(SVE2Document newdoc) {
  searchMatchesValid = false;
  searchMatches = null;
  doc = newdoc;
  guiUpdate();
  updateWorkspacePref();
  return doc != null;
}

/*
 * Workspace Management
 */

static FileFilter ffWorkspace = createFileFilter("sve", "SVE Workspaces");

public File selectWorkspaceFileToOpen() { return selectFile(OPEN, "Open workspace", ffWorkspace); }
public File selectWorkspaceFileToWrite() { return selectWorkspaceFileToWrite(null); }
public File selectWorkspaceFileToWrite(File selectedFile) {
  return ensureExtension("sve", selectFile(SAVE, "Save workspace", ffWorkspace, selectedFile)); }

public void openWorkspace() { openWorkspace(selectWorkspaceFileToOpen()); }
public void openWorkspace(File file) {
  if ((file == null) || file.equals(workspaceFile)) return;
  try {
    replaceWorkspace(new XMLElement(this, file.getAbsolutePath()));
  } catch (Exception e) {
    console.logError("Error reading workspace from " + file.getAbsolutePath() + ": ", e);
    workspaceFile = null;
    return;
  }
  console.logInfo("Opened workspace " + file.getAbsolutePath());
  workspaceFile = file;
}

public void saveWorkspace() { saveWorkspace(false); }
public void saveWorkspace(boolean forceSelect) {
  File file = workspaceFile;
  if ((file == null) || forceSelect)
    file = selectWorkspaceFileToWrite(file);
  if (file == null) return;
  try {
    PrintWriter writer = createWriter(file.getAbsolutePath());
    writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    new XMLWriter(writer).write(XMLElement.parse(prefs.get("workspace", "<workspace />")), true);
    writer.close();
  } catch (Exception e) {
    console.logError("Error saving workspace to " + file.getAbsolutePath() + ": ", e);
    return;
  }
  console.logInfo("Workspace saved to " + file.getAbsolutePath());
  workspaceFile = file;
}

public void updateWorkspacePref() {
  String workspace = "";
  for (SVE2Document d : docs)
    if (d.getFile() != null)
      workspace += "<document src=\"" + d.getFile().getAbsolutePath() + "\"" +
        ((d == doc) ? " selected=\"true\"" : "")  + " />";
  workspace = "<workspace>" + workspace + "</workspace>";
  prefs.put("workspace", workspace);
  showWorkspaceRecoveryButton = false;
}

public void replaceWorkspace(XMLElement workspace) {
  if (workspace == null) return;
  doc = null;
  docs.clear();
  XMLElement xmlDocuments[] = workspace.getChildren("document");
  for (XMLElement xmlDocument : xmlDocuments) {
    String src = xmlDocument.getString("src");
    if (src == null) continue;  // should not happen...
    SVE2Document newdoc = new SVE2Document(new File(src));
    docs.add(newdoc);
    worker.submit(newdoc.newLoadingTask());
    if (xmlDocument.getBoolean("selected"))
      switchToNetwork(newdoc);
  }
  if ((doc == null) && (docs.size() > 0)) switchToNetwork(docs.get(0));
  else updateWorkspacePref();
  guiUpdate();
}
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

static abstract class Scaling {
  int N = 0;
  float sx[];  // scaled values
  Scaling(int N) { this.N = N; sx = new float[N]; }
  public abstract float[] f(float[] x);
}

static class ScalingFactory {
  static String[] productNames = { "id", "sqrt", "log" };

  public static boolean canProduce(String name) {
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }
  public static String getDefaultProduct() { return productNames[0]; }

  public static Scaling produce(String name, int N) {
    if (name.equals("id")) return new IdScaling(N);
    if (name.equals("sqrt")) return new SqrtScaling(N);
    if (name.equals("log")) return new LogScaling(N);
    return null;
  }
}

static class IdScaling extends Scaling {
  IdScaling(int N) { super(N); }
  public float[] f(float[] x) { for (int i = 0; i < N; i++) sx[i] = x[i]; return sx; }
}

static class SqrtScaling extends Scaling {
  SqrtScaling(int N) { super(N); }
  public float[] f(float[] x) { for (int i = 0; i < N; i++) sx[i] = sqrt(x[i]); return sx; }
}

static class LogScaling extends Scaling {
  float x0 = 1;
  LogScaling(int N) { super(N); }
  LogScaling(int N, float x0) { super(N); this.x0 = x0; }
  public float[] f(float[] x) {
    for (int i = 0; i < N; i++)
      sx[i] = (x[i] == 0) ? 0 : log(x[i]/x0);
    return sx;
  }
}
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

public class TabulatedData {
  // FIXME: handle header/footer lines
  // FIXME: handle line breaks in enclosed fields
  // FIXME: handle proper escape guessing

  protected String lines[] = null;

  public static final char WHITESPACE = 0;
  public static final char GUESS = (char)-1;
  protected char delim = ',';  // field delimiter
  protected char enclose = '"';  // (optional) field enclosing char
  protected char escape = '\\';  // escape char for enclose char

  protected int numCols = 0;
  protected int numHeaderRows = 0;
  protected int numFooterRows = 0;

  protected String fields[][] = null;

  public static final int UNKNOWN = 0;
  public static final int STRING = 1;
  public static final int FLOAT = 2;
  public static final int INT = 3;
  protected int typeMatrix = 0;
  protected int typeColumn[] = null;
  protected int typeRow[] = null;


  public TabulatedData(String lines[]) { this(lines, GUESS); }
  public TabulatedData(String lines[], char delim) { this(lines, delim, GUESS); }
  public TabulatedData(String lines[], char delim, char enclose) { this(lines, delim, enclose, GUESS); }
  public TabulatedData(String lines[], char delim, char enclose, char escape) {
    this.lines = lines;
    setParameters(delim, enclose, escape);
  }

  public void setDelim(char delim) { this.delim = delim; parse(); }
  public char getDelim() { return delim; }

  public void setEnclose(char enclose) { this.enclose = enclose; parse(); }
  public char getEnclose() { return enclose; }

  public void setEscape(char escape) { this.escape = escape; parse(); }
  public char getEscape() { return escape; }

  public void setParameters(char delim, char enclose, char escape) {
    this.delim = delim; this.enclose = enclose; this.escape = escape; parse(); }

  public String[][] getFields() { return fields; }
  public int getNumCols() { return numCols; }
  public int getNumRows() { return lines.length - numHeaderRows - numFooterRows; }

  public boolean isSquareMatrix() { return getNumCols() == getNumRows(); }
  public boolean isIntMatrix() { return checkMatrixType() == INT; }
  public boolean isNumericMatrix() { return checkMatrixType() >= FLOAT; }
  public String getMatrixType() { return typeString(checkMatrixType()); }

  public boolean isIntColumn(int j) { return checkColumnType(j) == INT; }
  public boolean isNumericColumn(int j) { return checkColumnType(j) >= FLOAT; }
  public String getColumnType(int j) { return typeString(checkColumnType(j)); }

  public boolean isIntRow(int j) { return checkRowType(j) == INT; }
  public boolean isNumericRow(int j) { return checkRowType(j) >= FLOAT; }
  public String getRowType(int j) { return typeString(checkRowType(j)); }

  public float[][] getFloatMatrix() {
    if (fields == null) parse();
    float result[][] = new float[lines.length][numCols];
    for (int i = 0; i < lines.length; i++)
      for (int j = 0; j < numCols; j++)
        result[i][j] = parseFloat(fields[i][j]);
    return result;
  }

  public int[][] getIndexMatrix() { return getIntMatrix(-1); }
  public int[][] getIntMatrix() { return getIntMatrix(0); }
  public int[][] getIntMatrix(int delta) {
    if (fields == null) parse();
    int result[][] = new int[lines.length][numCols];
    for (int i = 0; i < lines.length; i++)
      for (int j = 0; j < numCols; j++)
        result[i][j] = parseInt(fields[i][j]) + delta;
    return result;
  }

  public String[] getColumn(int j) {
    if (fields == null) parse();
    String result[] = new String[lines.length];
    for (int i = 0; i < lines.length; i++)
      result[i] = fields[i][j];
    return result;
  }

  public float[] getFloatColumn(int j) { return parseFloat(getColumn(j)); }

  public int[] getIndexColumn(int j) { return getIntColumn(j, -1); }
  public int[] getIntColumn(int j) { return parseInt(getColumn(j)); }
  public int[] getIntColumn(int j, int delta) {
    if (fields == null) parse();
    int result[] = new int[lines.length];
    for (int i = 0; i < lines.length; i++)
      result[i] = parseInt(fields[i][j]) + delta;
    return result;
  }

  public String[] getRow(int i) { if (fields == null) parse(); return fields[i]; }

  public float[] getFloatRow(int i) { return parseFloat(getRow(i)); }

  public int[] getIndexRow(int i) { return getIntRow(i, -1); }
  public int[] getIntRow(int i) { return parseInt(getRow(i)); }
  public int[] getIntRow(int i, int delta) {
    if (fields == null) parse();
    int result[] = new int[numCols];
    for (int j = 0; j < numCols; j++)
      result[j] = parseInt(fields[i][j]) + delta;
    return result;
  }

  protected String[] parseRecord(String record, char delim, char enclose, char escape) {
    Vector<String> result = new Vector<String>();  // return value
    String currentField = "";  // current field
    boolean enclosed = false;
    boolean escaped = false;
    for (int i = 0; i < record.length(); i++) {
      char c = record.charAt(i);
      if ((c == delim) && !enclosed) {
        if ((delim != ' ') || (!currentField.equals(""))) {  // treat multiple spaces as one separator
          result.add(currentField);
          currentField = "";
        }
      } else if ((c == escape) && (i < record.length() - 1) && (record.charAt(i+1) == enclose)) {
        escaped = true;
      } else if ((c == enclose) && !escaped) {
        enclosed = !enclosed;
      } else {
        currentField += c;
        escaped = false;
      }
    }
    result.add(currentField);
    return result.toArray(new String[numCols]);
  }

  protected String[][] parse(String lines[], char delim, char enclose, char escape, int minNumCols) {
    // parse one record that most probably is neither header nor footer and check number of columns
    int numCols = parseRecord(lines[lines.length/2], delim, enclose, escape).length;
    if (numCols < minNumCols) return null;
    // parse all records and make sure they all have the same number of columns
    String fields[][] = new String[lines.length][];
    for (int i = 0; i < lines.length; i++) {
      fields[i] = parseRecord(lines[i], delim, enclose, escape);
      if (fields[i].length != numCols) return null;
    }
    return fields;
  }

  protected void parse() {
    // reset all processed data
    this.fields = null;
    typeMatrix = 0;
    typeColumn = null;
    typeRow = null;
    numCols = 0;
    // check if we actually have to do anything
    if ((lines == null) || (lines.length == 0)) return;
    // cycle through all parameters that are to be GUESSed, saving the "best" parsing result
    char _delim = this.delim, _enclose = this.enclose, _escape = this.escape;
    for (char enclose : (_enclose == GUESS) ? new char[] { '"', '\'' } : new char[] { _enclose }) {
      for (char escape : (_escape == GUESS) ? new char[] { '\\', enclose } : new char[] { _escape }) {
        for (char delim : (_delim == GUESS) ? new char[] { '\t', ' ', ',', ';', '$' } : new char[] { _delim }) {
          // try to parse into more columns than we already found (at least 2)
          String fields[][] = parse(lines, delim, enclose, escape, max(2, numCols + 1));
          if (fields != null) {
            // these seem to be good parameters, save them into the class member variables
            // (yes, using the same names for local and class variables is pretty awesome fun)
            this.fields = fields;
            this.delim = delim;
            this.enclose = enclose;
            this.escape = escape;
            numCols = fields[0].length;
          }
        }
      }
    }
    // if the guessing didn't yield anything appropriate, then treat this as 1-column data
    if (this.fields == null) {
      this.fields = new String[lines.length][1];
      for (int i = 0; i < lines.length; i++)
        this.fields[i][0] = lines[i];
      numCols = 1;
    }
    // create proper type caches
    typeColumn = new int[numCols];
    typeRow = new int[lines.length];
  }

  protected int checkMatrixType() {
    if (typeMatrix == 0) {
      typeMatrix = INT;
      for (int j = 0; j < numCols; j++)
        typeMatrix = min(typeMatrix, checkColumnType(j));
    }
    return typeMatrix;
  }

  protected int checkColumnType(int j) {
    if (typeColumn[j] == 0) {
      typeColumn[j] = INT;
      for (int i = 0; i < lines.length; i++) {
        if (typeColumn[j] == INT) try { Integer.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeColumn[j] = FLOAT; }
        if (typeColumn[j] == FLOAT) try { Float.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeColumn[j] = STRING; break; }
      }
    }
    return typeColumn[j];
  }

  protected int checkRowType(int i) {
    if (typeRow[i] == 0) {
      typeRow[i] = INT;
      for (int j = 0; j < numCols; j++) {
        if (typeRow[i] == INT) try { Integer.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeRow[i] = FLOAT; }
        if (typeRow[i] == FLOAT) try { Float.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeRow[i] = STRING; break; }
      }
    }
    return typeRow[i];
  }

  protected String typeString(int type) {
    switch (type) {
      case STRING: return "string";
      case FLOAT: return "numeric";
      case INT: return "integer";
      default: return "unknown";
    }
  }

}
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










class Updater extends Thread {

  boolean force = false;
  String updateURL = "http://update.spato.net/latest/";
  String releaseNotesURL = "http://update.spato.net/release-notes/";
  String indexName = null;
  String appRootFolder = null;
  String cacheFolder = null;
  XMLElement index = null;
  String updateVersion = null;
  // public key for file verification (base64-encoded)
  String pubKey64 =
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrWkHeVPecXQeOd2" +
    "C3K4UUzgBqXYJwfGNKZnLp17wy/45nH7/llxBKR7eioJPdYCauxQ8M" +
    "nuArSltlIV9AnBKxb8h28xoBsEx1ek04jvJEtd93Bw7ILa3eF4MDGl" +
    "ZxwPnmTaTICIVUXtiZveOHDl1dQBKvinyU8fe3Xi7+j9klnwIDAQAB";

  public Updater(boolean force) {
    setPriority(Thread.MIN_PRIORITY);
    this.force = force;
    // if (System.getProperty("spato.app-dir") == null)
    //   System.setProperty("spato.app-dir", "/Users/ct/Documents/Processing/SPaTo/SPaTo_Visual_Explorer/application.macosx/SPaTo_Visual_Explorer.app");
  }

  public void printOut(String msg) { System.out.println("+++ SPaTo Updater: " + msg); }
  public void printErr(String msg) { System.err.println("+++ SPaTo Updater: " + msg); }

  public void setupEnvironment() {
    printOut("updateURL = " + updateURL);
    // determine which INDEX file to download
    switch (platform) {
      case LINUX:   indexName = "INDEX.linux"; break;
      case MACOSX:  indexName = "INDEX.macosx"; break;
      case WINDOWS: indexName = "INDEX.windows"; break;
      default:      throw new RuntimeException("unsupported platform");
    }
    printOut("indexName = " + indexName);
    // check application root folder
    appRootFolder = System.getProperty("spato.app-dir");
    if ((appRootFolder == null) || !new File(appRootFolder).exists())
      throw new RuntimeException("invalid application root folder: " + appRootFolder);
    if (!appRootFolder.endsWith(File.separator)) appRootFolder += File.separator;
    printOut("appRootFolder = " + appRootFolder);
    // check update cache folder
    switch (platform) {
      case LINUX:   cacheFolder = System.getProperty("user.home") + "/.spato/update"; break;
      case MACOSX:  cacheFolder = appRootFolder + "Contents/Resources/update"; break;
      default:      cacheFolder = appRootFolder + "update"; break;
    }
    if ((cacheFolder == null) || !new File(cacheFolder).exists() && !new File(cacheFolder).mkdirs())
      throw new RuntimeException("could not create cache folder: " + cacheFolder);
    if (!cacheFolder.endsWith(File.separator)) cacheFolder += File.separator;
    printOut("cacheFolder = " + cacheFolder);
  }

  public boolean checkAndFetch() {  // returns true if update is available
    int count = 0, totalSize = 0;
    BufferedReader reader = null;
    // fetch the index
    try {
      // reader creation copied from PApplet so we can catch exceptions
      InputStream is = new URL(updateURL + indexName).openStream();
      reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      // XML parsing copied from XMLElement so we can catch exceptions
      index = new XMLElement();
      StdXMLParser parser = new StdXMLParser();
      parser.setBuilder(new StdXMLBuilder(index));
      parser.setValidator(new XMLValidator());
      parser.setReader(new StdXMLReader(reader));
      parser.parse();
    } catch (XMLException xmle) {
      index = null;
      throw new RuntimeException("Not a valid XML file: " + updateURL + indexName + "<br>" +
        "Are you properly connected to the interwebs?");
    } catch (Exception e) {  // FIXME: react to specific exceptions
      index = null;
      throw new RuntimeException("could not download " + indexName, e);
    } finally {
      try { reader.close(); } catch (Exception e) { }
    }
    // check whether the user wants to ignore this update
    try { updateVersion = index.getChild("release").getString("version"); } catch (Exception e) {}
    printOut("INDEX is for version " + updateVersion);
    if ((updateVersion != null) && updateVersion.equals(prefs.get("update.skip", null))) {
      printOut("user requested to skip this version");
      return false;
    } else
      prefs.remove("update.skip");
    // delete possibly existing locally cached index
    new File(cacheFolder + "INDEX").delete();
    // setup signature verification
    Signature sig = null;
    try {
      sig = Signature.getInstance("MD5withRSA");
      X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.decode(pubKey64));
      sig.initVerify(KeyFactory.getInstance("RSA").generatePublic(spec));
    } catch (Exception e) { throw new RuntimeException("failed to setup signature verification", e); }
    // iterate over all file records
    for (XMLElement file : index.getChildren("file")) {
      XMLElement remote = file.getChild("remote"), local = file.getChild("local");
      // check if all information is present
      if ((remote == null) || (remote.getString("path") == null) || (remote.getString("md5") == null) ||
          (local == null) || (local.getString("path") == null))
        throw new RuntimeException("malformed file record: " + file);
      // check for signature and decode
      byte signature[] = null;
      if ((file.getChild("signature") == null) || (file.getChild("signature").getContent() == null))
        throw new RuntimeException("missing file signature: " + file);
      try { signature = Base64.decode(file.getChild("signature").getContent()); }
      catch (Exception e) { throw new RuntimeException("error decoding signature: " + file, e); }
      // download update file if necessary
      local.setString("md5", "" + MD5.digest(appRootFolder + local.getString("path")));  // "" forces "null" if md5 returns null
      if (!remote.getString("md5").equals(local.getString("md5"))) {
        count++;  // count number of outdated files
        String cacheFilename = cacheFolder + remote.getString("path").replace('/', File.separatorChar);
        if (remote.getString("md5").equals(MD5.digest(cacheFilename)))
          printOut(remote.getString("path") + " is outdated, but update is already cached");
        else {
          printOut(remote.getString("path") + " is outdated, downloading update (" + remote.getInt("size") + " bytes)");
          byte buf[] = new byte[remote.getInt("size", 0)];
          InputStream is = null;
          try {
            int read = 0;
            is = new URL(updateURL + remote.getString("path")).openStream();
            while (read < buf.length)
              is.read(buf, read, buf.length - read);
          } catch (Exception e) {
            printErr("download failed"); e.printStackTrace(); return false;
          } finally {
            try { is.close(); } catch (Exception e) {}
          }
          try { sig.update(buf); if (!sig.verify(signature)) throw new Exception("signature verification failure"); }
          catch (Exception e) { printErr("failed to verify file"); e.printStackTrace(); return false; }
          saveBytes(cacheFilename, buf);
          totalSize += remote.getInt("size");  // keep track of total download volume
          if (!remote.getString("md5").equals(MD5.digest(cacheFilename)))
            throw new RuntimeException("md5 mismatch: " + file);
        }
      }
    }
    // clean up and return
    if (count > 0) {
      printOut("updates available for " + count + " files, downloaded " + totalSize + " bytes");
      return true;
    } else {
      printOut("no updates available");
      new File(cacheFolder).delete();
      return false;
    }
  }

  public String[] getRestartCmd() {
    switch (platform) {
      case LINUX:
        return new String[] { appRootFolder + "SPaTo_Visual_Explorer", "--restart" };
      case MACOSX:
        return new String[] { appRootFolder + "Contents/MacOS/ApplicationUpdateWrapper", "--restart" };
      case WINDOWS:
        return new String[] { appRootFolder + "SPaTo Visual Explorer.exe", "sleep", "3" };
      default:
        return null;
    }
  }

  final static int NOTHING = -1, IGNORE = 0, INSTALL = 1, RESTART = 2;

  public int showReleaseNotesDialog(boolean canRestart) {
    // construct URL request
    String url = releaseNotesURL + "?version=" + version + "&index=" + indexName;
    // setup HTML renderer for release notes
    XHTMLPanel htmlView = new XHTMLPanel();
    try { htmlView.setDocument(url); }
    catch (Exception e) { throw new RuntimeException("could not fetch release notes from " + url, e); }
    JScrollPane scrollPane = new FSScrollPane(htmlView);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    // compose everything in a panel
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.add(new JLabel("An update is available and can be applied the next time you start SPaTo Visual Explorer."), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(new JLabel("<html>You are currently running version <b>" + version + "</b> (" + versionDate + ").</html>"), BorderLayout.SOUTH);
    panel.setPreferredSize(new Dimension(600, 400));
    panel.setMinimumSize(new Dimension(300, 200));
    // add the auto-check checkbox
    JCheckBox cbAutoUpdate = new JCheckBox("Automatically check for updates in the future",
      prefs.getBoolean("update.check", true));
    JPanel panel2 = new JPanel(new BorderLayout(0, 20));
    panel2.add(panel, BorderLayout.CENTER);
    panel2.add(cbAutoUpdate, BorderLayout.SOUTH);
    // setup the options
    Object options[] = canRestart
      ? new Object[] { "Restart now", "Restart later", "Skip this update" }
      : new Object[] { "Awesome!", "Skip this update" };
    // show the dialog
    int result = JOptionPane.showOptionDialog(frame, panel2, "Good news, everyone!",
      JOptionPane.INFORMATION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null,
      options, options[0]);
    // save the auto-check selection
    prefs.putBoolean("update.check", cbAutoUpdate.isSelected());
    // return the proper action constant
    if (result == (canRestart ? 2 : 1)) return IGNORE;  // skip this update
    if (result == (canRestart ? 1 : 0)) return INSTALL;  // install on next application launch
    if (result == 0 && canRestart) return RESTART;  // install now
    return NOTHING;  // this will cause to do nothing (no kidding!)
  }

  public void askAndAct() {
    while (fireworks) try { Thread.sleep(5000); } catch (Exception e) {}
    String cmd[] = getRestartCmd();
    int action = showReleaseNotesDialog(cmd != null);
    // check if the user wants to ignore this update
    if (action == IGNORE)
      prefs.put("update.skip", updateVersion);
    // save the INDEX into the update cache folder to indicate that the update should be installed
    if ((action == INSTALL) || (action == RESTART))
      index.write(createWriter(cacheFolder + "INDEX"));
    // restart application if requested
    if (action == RESTART) try {
      new ProcessBuilder(cmd).start();
      exit();  // FIXME: unsaved documents?
    } catch (Exception e) {  // catch this one here to give a slightly more optimistic error message
      printErr("could not restart application"); e.printStackTrace();
      JOptionPane.showMessageDialog(frame,
        "<html>The restart application could not be lauched:<br><br>" +
        PApplet.join(cmd, " ") + "<br>" + e.getClass().getName() + ": " + e.getMessage() + "<br><br>" +
        "However, the update should install automatically when you manually restart the application.</html>",
        "Slightly disappointing news",
        JOptionPane.ERROR_MESSAGE);
    }
  }

  public void run() {
    try {
      setupEnvironment();
      if (checkAndFetch())
        askAndAct();
      else if (force)
        JOptionPane.showMessageDialog(frame,
          "No updates available", "Update", JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      printErr("Something's wrong. Stack trace follows..."); e.printStackTrace();
      // prepare error dialog
      JPanel panel = new JPanel(new BorderLayout(0, 20));
      String str = "<html>Something went wrong while checking for updates.<br><br>" +
        e.getMessage().substring(0, 1).toUpperCase() + e.getMessage().substring(1);
      if (e.getCause() != null)
        str += "\ndue to " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
      str += "</html>";
      panel.add(new JLabel(str), BorderLayout.CENTER);
      JCheckBox cbAutoUpdate = new JCheckBox("Automatically check for updates in the future",
        prefs.getBoolean("update.check", true));
      panel.add(cbAutoUpdate, BorderLayout.SOUTH);
      // show dialog
      JOptionPane.showMessageDialog(frame, panel, "Bollocks!", JOptionPane.ERROR_MESSAGE);
      // save the auto-check selection
      prefs.putBoolean("update.check", cbAutoUpdate.isSelected());
    }
  }

}
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









public Date parseISO8601(String timestamp) {
  try { return new SimpleDateFormat("yyyyMMdd'T'HHmmss").parse(timestamp); }
  catch (Exception e) { e.printStackTrace(); return null; }
}

static class MD5 {
  public static String digest(String file) {
    if (!new File(file).exists()) return null;
    try {
      MessageDigest md5 = MessageDigest.getInstance("md5");
      DigestInputStream dis = new DigestInputStream(new FileInputStream(file), md5);
      byte buf[] = new byte[8*1024];
      while (dis.read(buf, 0, buf.length) > 0) /* do nothing while md5 digests as data streams in */;
      dis.close();
      return String.format("%032x", new java.math.BigInteger(1, md5.digest()));
    } catch (Exception e) {
      throw new RuntimeException("failed to calculate MD5 checksum of " + file, e);
    }
  }
}

static class Base64 {
  private static char map[] = new char[64];
  static {
    for (int i = 00; i < 26; i++) map[i] = (char)('A' + (i - 00));
    for (int i = 26; i < 52; i++) map[i] = (char)('a' + (i - 26));
    for (int i = 52; i < 62; i++) map[i] = (char)('0' + (i - 52));
    map[62] = '+'; map[63] = '/';
  }
  // this is incredibly inefficient, but ok for our purposes (only decoding short hashes and keys)
  private static int unmap(char c) {
    for (int i = 0; i < 64; i++) if (map[i] == c) return i;
    throw new IllegalArgumentException("value " + c + " is not in the map");
  }

  public static String encode(byte data[]) { return encode(data, 76); }
  public static String encode(byte data[], int wrapColumn) {
    StringBuffer result = new StringBuffer();
    int input[] = new int[3];
    int output[] = new int[4];
    int lineLen = 0;
    for (int i = 0; i < data.length; i += 3) {
      int len = Math.min(3, data.length - i);
      input[0] = 0xff & (int)data[i+0];
      input[1] = (len > 1) ? (0xff & (int)data[i+1]) : 0;
      input[2] = (len > 2) ? (0xff & (int)data[i+2]) : 0;
      output[0] = (0xfc & input[0]) >> 2;  // get highest 6 bits of first input byte
      output[1] = ((0x03 & input[0]) << 4) + ((0xf0 & input[1]) >> 4);  // get lowest 2 bits of first and highest 4 bits of second input byte
      output[2] = ((0x0f & input[1]) << 2) + ((0xc0 & input[2]) >> 6);  // lowest 4 bits of second and hightes 2 bits of third input byte
      output[3] = (0x3f & input[2]);  // lowest 6 bits of third input byte
      result.append(map[output[0]]);
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
      result.append(map[output[1]]);
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
      result.append(len > 1 ? map[output[2]] : '=');
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
      result.append(len > 2 ? map[output[3]] : '=');
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
    }
    return result.toString();
  }

  public static byte[] decode(String data) {
    String lines[] = data.split("\n");
    data = ""; for (String line : lines) data += line.trim();
    if (data.length() % 4 != 0) throw new IllegalArgumentException("input length is not a multiple of four");
    int reslen = data.length()*3/4;
    if (data.charAt(data.length() - 1) == '=') { reslen--; data = data.substring(0, data.length() - 1); }
    if (data.charAt(data.length() - 1) == '=') { reslen--; data = data.substring(0, data.length() - 1); }
    if (data.charAt(data.length() - 1) == '=') throw new IllegalArgumentException("input has to many padding characters");
    byte result[] = new byte[reslen];
    int input[] = new int[4];
    int output[] = new int[3];
    int ii = 0;
    for (int i = 0; i < data.length(); i += 4) {
      int len = Math.min(4, data.length() - i);
      input[0] = unmap(data.charAt(i));
      input[1] = unmap(data.charAt(i + 1));
      input[2] = (len > 2) ? unmap(data.charAt(i + 2)) : 0;
      input[3] = (len > 3) ? unmap(data.charAt(i + 3)) : 0;
      output[0] = (input[0] << 2) + ((0x30 & input[1]) >> 4);
      output[1] = ((0x0f & input[1]) << 4) + ((0x3c & input[2]) >> 2);
      output[2] = ((0x03 & input[2]) << 6) + input[3];
      result[ii++] = (byte)output[0];
      if (len > 2) result[ii++] = (byte)output[1];
      if (len > 3) result[ii++] = (byte)output[2];
    }
    return result;
  }
}


/*
 * FileDialog/JFileChooser-related things
 */

public static FileFilter createFileFilter(final String ext, final String desc) {
  return createFileFilter(new String[] { ext }, desc); }
public static FileFilter createFileFilter(final String exts[], final String desc) {
  return new FileFilter() {
    public String getDescription() { return desc; }
    public boolean accept(File f) {
      if (f.isDirectory()) return false;
      for (String ext : exts) if (f.getName().endsWith("." + ext)) return true;
      return false;
    }
  };
}

// Yeah, nice... JFileChooser uses a different way of filtering files (FileFilter) than FileDialog (FilenameFilter).
// This class acts as a FilenameFilter, but with a FileFilter the decision-making backend.
class FilenameFileFilterAdapter implements FilenameFilter {
  FileFilter ff = null;
  FilenameFileFilterAdapter(FileFilter ff) { this.ff = ff; }
  public boolean accept(File dir, String name) { return ff.accept(new File(dir, name)); }
}

// And also nice: JFileChooser on Windows does not display directories at all if they are rejected  by
// the FileFilter, which means you cannot change into them, which means you most probably cannot reach
// any of the files of interest.  So, this class accepts all directories before asking the real filter.
// The selectFiles method will sort out selected, non-acceptable directories afterwards.
class WindowsFileFilterAdapter extends FileFilter {
  FileFilter ff = null;
  WindowsFileFilterAdapter(FileFilter ff) { this.ff = ff; }
  public String getDescription() { return ff.getDescription(); }
  public boolean accept(File f) { return f.isDirectory() ? true : ff.accept(f); }
}

public static File ensureExtension(String ext, File file) {
  if ((ext == null) || (file == null)) return null;
  return file.getName().endsWith("." + ext) ? file : new File(file.getAbsolutePath() + "." + ext);
}

static final int OPEN = 0;
static final int OPENMULTIPLE = 1;
static final int SAVE = 2;

public File selectFile(int mode) { return selectFile(mode, null, null, null); }
public File selectFile(int mode, String title) { return selectFile(mode, title, null, null); }
public File selectFile(int mode, String title, FileFilter ff) { return selectFile(mode, title, ff, null); }
public File selectFile(int mode, String title, FileFilter ff, File selectedFile) {
  File res[] = selectFiles(mode, title, ff, selectedFile);
  return ((res != null) && (res.length > 0)) ? res[0] : null;
}

File selectFilesResult[];
public File[] selectFiles(int mode) { return selectFiles(mode, null, null, null); }
public File[] selectFiles(int mode, String title) { return selectFiles(mode, title, null, null); }
public File[] selectFiles(int mode, String title, FileFilter ff) { return selectFiles(mode, title, ff, null); }
public File[] selectFiles(final int mode, final String title, final FileFilter ff, final File selectedFile) {
  checkParentFrame();
  try {
    selectFilesResult = null;
    SwingUtilities.invokeAndWait(new Runnable() {
      public void run() {
        File lastDir = new File(prefs.get("workspace.lastDirectory", ""));
        if (lastDir.getAbsolutePath().equals("")) lastDir = null;
        if (platform == MACOSX) {
          // Use FileDialog instead of JFileChooser as per Apple recommendation.
          // This is possible again because .spato-directories are Mac bundles now.
          FileDialog fd = new FileDialog(parentFrame,
            (title != null) ? title : (mode == SAVE) ? "Save..." : "Open...",
            (mode == SAVE) ? FileDialog.SAVE : FileDialog.LOAD);
          fd.setFilenameFilter(new FilenameFileFilterAdapter(ff));
          String dirname = null;
          if (selectedFile != null) dirname = selectedFile.getParent();
          else if (lastDir != null) dirname = lastDir.getAbsolutePath();
          fd.setDirectory(dirname);
          fd.setFile((selectedFile != null) ? selectedFile.getAbsolutePath() : null);
          fd.setVisible(true);
          selectFilesResult = new File[0];
          if ((fd.getFile() != null) && ((mode == SAVE) || ff.accept(new File(fd.getDirectory(), fd.getFile()))))
            selectFilesResult = new File[] { new File(fd.getDirectory(), fd.getFile()) };
        } else {
          JFileChooser fc = new JFileChooser();
          // set initial directory and possibly initially selected file
          fc.setCurrentDirectory((selectedFile != null) ? selectedFile.getParentFile() : lastDir);
          if (selectedFile != null)
            fc.setSelectedFile(selectedFile);
          // setup dialog look and feel
          fc.setDialogTitle((title != null) ? title : (mode == SAVE) ? "Save..." : "Open...");
          fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
          fc.setMultiSelectionEnabled(mode == OPENMULTIPLE);  // allow selecting multiple documents to load
          fc.setAcceptAllFileFilterUsed(ff == null);
          if (ff != null) fc.setFileFilter((platform == WINDOWS) ? new WindowsFileFilterAdapter(ff) : ff);
          // run dialog
          if (fc.showDialog(parentFrame, (mode == SAVE) ? "Save" : "Open") == JFileChooser.APPROVE_OPTION) {
            // save current directory to preferences
            File dir = fc.getCurrentDirectory();
            prefs.put("workspace.lastDirectory", (dir != null) ? dir.getAbsolutePath() : null);
            // evaluate selection
            File files[] = fc.isMultiSelectionEnabled()
              ? fc.getSelectedFiles() : new File[] { fc.getSelectedFile() };
            if (mode != SAVE)
              for (int i = 0; i < files.length; i++)
                if ((ff != null) && !ff.accept(files[i]))
                  files[i] = null;
            // transcribe selection into result array
            selectFilesResult = new File[0];
            for (int i = 0; i < files.length; i++)
              if (files[i] != null)
                selectFilesResult = (File[])append(selectFilesResult, files[i]);
          }
        }
      }
    });
    return selectFilesResult;
  } catch (Exception e) {
    console.logError("Something went wrong: ", e);
    e.printStackTrace();
    return null;
  }
}
}
