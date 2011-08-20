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
import net.spato.sve.app.data.*;
import net.spato.sve.app.layout.*;
import net.spato.sve.app.util.*;
import net.spato.sve.app.platform.*;
import org.xhtmlrenderer.simple.*;
import processing.core.*;
import processing.pdf.*;
import processing.xml.*;
import tGUI.*;


@SuppressWarnings("serial")
public class SPaTo_Visual_Explorer extends PApplet {

public static SPaTo_Visual_Explorer INSTANCE = null;  // FIXME: this is probably quite evil...

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

public ExecutorService worker = Executors.newSingleThreadExecutor();  // FIXME: public?
public Preferences prefs = Preferences.userRoot().node("/net/spato/SPaTo_Visual_Explorer");  // FIXME: should not be public
public boolean canHandleOpenFileEvents = false;  // indicates that GUI and workspace are ready to open files  // FIXME: should not be public

float t, tt, dt;  // this frame's time, last frame's time, and delta between the two
boolean screenshot = false;  // if true, draw() will render one frame to PDF
boolean layoutshot = false;  // if true (and screenshot == true), draw() will output coordinates of the current node positions

boolean resizeRequest = false;
int resizeWidth, resizeHeight;

PlatformMagic platformMagic = null;
public DataTransferHandler dataTransferHandler = null;  // FIXME: public?
public Workspace workspace = null;  // FIXME: public?
public SPaToDocument doc = null;  // FIXME: get rid of this variable

public void setup() {
  INSTANCE = this;
  platformMagic = new PlatformMagic();
  workspace = new Workspace(this);
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
    workspace.replaceWorkspace(XMLElement.parse(prefs.get("workspace", "<workspace />")));
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
    SPaToView.fastNodes = false;  // always draw circles in screenshots
    console.logNote("Recording screenshot to " + filename);
  } else
    background(255);
  // draw
  if (doc != null)
    doc.view.draw(g);
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
      if ((doc.view.viewMode == SPaToView.VIEW_MAP) && btnTom.isEnabled())
        btnTom.handleMouseClicked();
      else if ((doc.view.viewMode == SPaToView.VIEW_TOM) && btnMap.isEnabled())
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
        case KeyEvent.VK_S: if (workspace.docs.size() > 0) actionPerformed("workspace##save" + (keyEvent.isShiftDown() ? "As" : "")); return;
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


public boolean fireworks = false;  // FIXME: public?
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


public JFrame jframe = null;  // FIXME: should not be public
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
    applet.dataTransferHandler.handleDroppedFiles(ff);
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



public TransparentGUI gui;  // FIXME: should not be public
public TConsole console;  // FIXME: this should not be public
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

public boolean searchMatchesValid = false;  // FIXME: public?
public boolean searchMatches[] = null;  // this is true for nodes which are matched by the search phrase  // FIXME: public?
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
  dataTransferHandler = new DataTransferHandler(this);
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
  if (workspace.docs.size() > 0) {
    choiceNetwork.add(workspace.docs.toArray());
    if (doc != null)
      choiceNetwork.select(workspace.docs.indexOf(doc));
  }
  choiceNetwork.getContextMenu().setEnabled("document##save", doc != null);
  choiceNetwork.getContextMenu().setEnabled("document##saveAs", doc != null);
  choiceNetwork.getContextMenu().setEnabled("document##compressed", doc != null);
  choiceNetwork.getContextMenu().getItem("document##compressed").setText(
    ((doc != null) && doc.compressed) ? "Save uncompressed" : "Save compressed");
  choiceNetwork.getContextMenu().setEnabled("document##close", doc != null);
  choiceNetwork.getContextMenu().setEnabled("workspace##save", workspace.docs.size() > 0);
  choiceNetwork.getContextMenu().setEnabled("workspace##saveAs", workspace.docs.size() > 0);
  if ((btnWorkspaceRecovery != null) && !workspace.showWorkspaceRecoveryButton) {
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
  choiceMapProjection.getParent().setVisibleAndEnabled((doc != null) && (doc.view.viewMode == SPaToView.VIEW_MAP));
  choiceTomProjection.getParent().setVisibleAndEnabled((doc != null) && (doc.view.viewMode == SPaToView.VIEW_TOM));
  choiceDataset.getParent().setVisibleAndEnabled(doc != null);
  btnNodes.getParent().setVisibleAndEnabled(doc != null);
  sldAlbumSnapshot.getParent().setVisibleAndEnabled((doc != null) && (doc.getAlbum() != null));
  lblStatus.setText(((doc == null) || (doc.view.ih == -1)) ? "" : doc.view.nodes[doc.view.ih].name);
  platformMagic.update();
  if (doc == null) return;
  choiceMapProjection.setEnabled(doc.view.hasMapLayout);
  choiceTomProjection.setEnabled(doc.view.hasTomLayout);
  choiceTomScaling.setEnabled(doc.view.hasTomLayout);
  choiceDistMat.setEnabled(doc.view.hasTomLayout);
  btnMap.setEnabled(doc.view.hasMapLayout);
  btnTom.setEnabled(doc.view.hasTomLayout);
  switch (doc.view.viewMode) {
    case SPaToView.VIEW_MAP: btnMap.getButtonGroup().setSelected(btnMap); break;
    case SPaToView.VIEW_TOM: btnTom.getButtonGroup().setSelected(btnTom); break;
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
  if (frameRate < 15) SPaToView.fastNodes = true;
  if (frameRate > 30) SPaToView.fastNodes = false;
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
    if (argv[1].equals("open")) workspace.openWorkspace();
    if (argv[1].equals("save")) workspace.saveWorkspace();
    if (argv[1].equals("saveAs")) workspace.saveWorkspace(true);
    if (argv[1].equals("recover")) workspace.replaceWorkspace(XMLElement.parse(prefs.get("workspace", "<workspace />")));
  } else if (argv[0].equals("document")) {
    if (argv[1].equals("new")) workspace.newDocument();
    if (argv[1].equals("open")) workspace.openDocument();
    if (argv[1].equals("save")) workspace.saveDocument();
    if (argv[1].equals("saveAs")) workspace.saveDocument(true);
    if (argv[1].equals("compressed")) { doc.setCompressed(!doc.isCompressed()); workspace.saveDocument(); }
    if (argv[1].equals("close")) workspace.closeDocument();
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
    workspace.switchToNetwork(argv[1]);
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
    doc.view.viewMode = SPaToView.VIEW_MAP;
  else if (argv[0].equals("Tom"))
    doc.view.viewMode = SPaToView.VIEW_TOM;
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
  class SPaToDocumentRenderer extends StringRenderer {
    public boolean getEnabled(Object o) { return ((SPaToDocument)o).view.hasMapLayout || ((SPaToDocument)o).view.hasTomLayout; }
    public String getActionCommand(Object o) { return ((SPaToDocument)o).getName(); }
    public TComponent.Dimension getPreferredSize(TChoice c, Object o, boolean inMenu) {
      SPaToDocument doc = (SPaToDocument)o;
      TComponent.Dimension d = super.getPreferredSize(c, doc.getName(), inMenu);
      if (inMenu) {
        textFont(gui.style.getFont());
        String desc = !getEnabled(o) ? " (loading...)" : " \u2013 " + doc.getTitle();
        d.width += 5 + textWidth(desc);
      }
      return d;
    }
    public void draw(TChoice c, PGraphics g, Object o, TComponent.Rectangle bounds, boolean inMenu) {
      SPaToDocument doc = (SPaToDocument)o;
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
    super(gui, actionCmdPrefix); setRenderer(new SPaToDocumentRenderer()); }
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











public Date parseISO8601(String timestamp) {
  try { return new SimpleDateFormat("yyyyMMdd'T'HHmmss").parse(timestamp); }
  catch (Exception e) { e.printStackTrace(); return null; }
}

// FIXME: get rid of this function
public Frame getParentFrame() {
  checkParentFrame();
  return parentFrame;
}

}
