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

import java.awt.event.KeyEvent;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.spato.sve.app.data.*;
import net.spato.sve.app.layout.*;
import processing.core.PApplet;
import processing.core.PFont;
import processing.core.PGraphics;
import processing.xml.XMLElement;
import tGUI.*;


public class SPaToGUI extends TransparentGUI {

  protected SPaTo_Visual_Explorer app = null;

  public TConsole console;  // FIXME: should not be public
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

  public SPaToGUI(SPaTo_Visual_Explorer app) {
    super(app);
    this.app = app;
    fnSmall = createFont("GillSans", fnsizeSmall);
    fnMedium = createFont("GillSans", fnsizeMedium);
    fnLarge = createFont("GillSans", fnsizeLarge);
    fnLargeBold = createFont("GillSans-Bold", fnsizeLarge);
    // setup top panel
    TPanel panel = createPanel(new TBorderLayout());
    panel.setPadding(5);
    choiceMapProjection = createChoice("projMap##");
    choiceMapProjection.add(MapProjectionFactory.productNames);
    choiceTomProjection = createChoice("projTom##");
    choiceTomProjection.add(TomProjectionFactory.productNames);
    choiceTomScaling = createChoice("scal##");
    choiceTomScaling.add(ScalingFactory.productNames);
    choiceDistMat = createChoice("distMat##", true);
    choiceDistMat.setNoneString("Tree Depth");
    choiceDistMat.setRenderer(new XMLElementRenderer(true));
    panel.add(createCompactGroup(new TComponent[] {
      createLabel("Projection:"), choiceMapProjection }), TBorderLayout.EAST);
    panel.add(createCompactGroup(new TComponent[] {
      createLabel("Projection:"), choiceTomProjection, choiceTomScaling, choiceDistMat }), TBorderLayout.EAST);
    //
    tfSearch = createTextField("search");
    tfSearch.setEmptyText("Search");
    btnRegexpSearch = createToggleButton("RegExp", prefs.getBoolean("search.regexp", false));
    panel.add(createCompactGroup(new TComponent[] { tfSearch, btnRegexpSearch }), TBorderLayout.EAST);
    //
    btnNodes = createToggleButton("Nodes", true);
    btnLinks = createToggleButton("Links", true);
    btnSkeleton = createToggleButton("Skeleton", false);
    btnNeighbors = createToggleButton("Neighbors", false); btnNeighbors.setVisible(false);  // enabled but invis for now...
    btnNetwork = createToggleButton("Network", false); btnNetwork.setVisible(false);  // enabled but invis for now...
    btnLabels = createToggleButton("Labels", true);
    TPanel linksGroup = createCompactGroup(new TComponent[] { btnLinks, btnSkeleton, btnNeighbors, btnNetwork }, new TComponent.Spacing(0));
    linksGroup.setBorderColor(app.color(200, 0, 0));
    panel.add(createCompactGroup(new TComponent[] {
      createLabel("Show:"), btnNodes, linksGroup, btnLabels }), TBorderLayout.WEST);
    //
    lblAlbumName = createLabel("Album:");
    btnAlbumPrevSnapshot = createButton("<"); btnAlbumPrevSnapshot.setActionCommand("snapshot##album##prev");
    btnAlbumNextSnapshot = createButton(">"); btnAlbumNextSnapshot.setActionCommand("snapshot##album##next");
    sldAlbumSnapshot = createSlider("snapshot##album");
    lblAlbumSnapshot = createLabel("Snapshot");
    panel.add(createCompactGroup(new TComponent[] {
      lblAlbumName, btnAlbumPrevSnapshot, sldAlbumSnapshot, btnAlbumNextSnapshot, lblAlbumSnapshot }), TBorderLayout.WEST);
    //
    panel.add(createLabel(""), TBorderLayout.WEST);  // place-holder to keep choiceNetwork in place
    //
    add(panel, TBorderLayout.NORTH);
    // setup network and map/tom switcher
    panel = createPanel(new TBorderLayout());
    panel.setPadding(5, 10);
    choiceNetwork = new TChoiceWithRollover(gui, "network##"); choiceNetwork.setFont(fnLargeBold);
    choiceNetwork.setEmptyString("right-click here");
    choiceNetwork.setContextMenu(createPopupMenu(new String[][] {
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
    panel.add(createCompactGroup(new TComponent[] { choiceNetwork }, 5), TBorderLayout.WEST);
    btnMap = createToggleButton("Map"); btnMap.setFont(fnLargeBold);
    btnTom = createToggleButton("Tom"); btnTom.setFont(fnLargeBold);
    new TButtonGroup(new TToggleButton[] { btnMap, btnTom });
    panel.add(createCompactGroup(new TComponent[] { btnMap, btnTom }, 5), TBorderLayout.EAST);
    add(panel, TBorderLayout.NORTH);
    // setup workspace recovery button
    XMLElement xmlWorkspace = XMLElement.parse(prefs.get("workspace", "<workspace />"));
    if ((xmlWorkspace != null) && (xmlWorkspace.getChildren("document").length > 0)) {
      btnWorkspaceRecovery = createButton("Recover previous workspace");
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
      panel = createPanel(new TBorderLayout());
      panel.setPadding(5, 10);
      panel.add(createCompactGroup(new TComponent[] { btnWorkspaceRecovery }, 2), TBorderLayout.WEST);
      add(panel, TBorderLayout.NORTH);
    }
    // setup bottom panel
    panel = createPanel(new TBorderLayout());
    panel.setPadding(5);
    choiceDataset = createChoice("dataset##", true);
    choiceDataset.setEmptyString("\u2014 right-click to create new dataset \u2014");
    choiceDataset.setRenderer(new XMLElementRenderer());
    // choiceDataset.setContextMenu(createPopupMenu(new String[][] {
    //   { "New", "dataset####new" },
    //   { "Rename", "dataset####rename" },
    //   { "Delete", "dataset####delete" }
    // }));
    choiceQuantity = createChoice("quantity##", true);
    choiceQuantity.setRenderer(new XMLElementRenderer());
    // choiceQuantity.setContextMenu(createPopupMenu(new String[][] {
    //   { "Import\u2026", "quantity####import" },
    //   { "Rename", "quantity####rename" },
    //   { "Delete", "quantity####delete" }
    // }));
    choiceColormap = createChoice("colormap##");
    choiceColormap.setEmptyString("\u2014 no quantity selected \u2014");
    btnColormapLog = createToggleButton("log");
      btnQuantityPrevSnapshot = createButton("<"); btnQuantityPrevSnapshot.setActionCommand("snapshot##quantity##prev");
      btnQuantityNextSnapshot = createButton(">"); btnQuantityNextSnapshot.setActionCommand("snapshot##quantity##next");
      sldQuantitySnapshot = createSlider("snapshot##quantity");
      // FIXME: snapshot controls still wiggle due to stupid XMLElementRenderer
    panel.add(createCompactGroup(new TComponent[] {
      createLabel("Node Coloring:"), choiceDataset, createLabel("/"), choiceQuantity,
      createCompactGroup(new TComponent[] { btnQuantityPrevSnapshot, sldQuantitySnapshot, btnQuantityNextSnapshot }),
      createLabel("/"), choiceColormap, btnColormapLog }), TBorderLayout.WEST);
    add(panel, TBorderLayout.SOUTH);
    // node coloring quantity snapshot controls
    // btnQuantityPrevSnapshot = createButton("<"); btnQuantityPrevSnapshot.setActionCommand("snapshot##quantity##prev");
    // btnQuantityNextSnapshot = createButton(">"); btnQuantityNextSnapshot.setActionCommand("snapshot##quantity##next");
    // sldQuantitySnapshot = createSlider("snapshot##quantity");
    // panel.add(createCompactGroup(new TComponent[] { btnQuantityPrevSnapshot, sldQuantitySnapshot, btnQuantityNextSnapshot }), TBorderLayout.WEST);
    // TWindow win = new TWindow(gui, new TCompactGroupLayout(0));
    // win.setMargin(1, 3);
    // win.add(btnQuantityPrevSnapshot);
    // win.add(sldQuantitySnapshot);
    // win.add(btnQuantityNextSnapshot);
    // win.setVisible(false);
    // add(win);
    // status bar
    lblStatus = createLabel("");
    lblStatus.setAlignment(TLabel.ALIGN_RIGHT);
    lblStatus.setFont(createFont("GillSans-Bold", 10));
    panel.add(lblStatus, TBorderLayout.CENTER);
    // network details panel
    panel = createPanel(new TBorderLayout());
    panel.setPadding(0, 10);
    networkDetail = new NetworkDetailPanel(gui);
    networkDetail.setVisible(false);
    choiceNetwork.rollOverComponent = networkDetail;
    panel.add(networkDetail, TBorderLayout.NORTH);
    add(panel, TBorderLayout.WEST);
    // setup console
    console = createConsole(app.versionDebug.equals("alpha"));
    int tE = 5000;
    console.logInfo("SPaTo Visual Explorer").tE = tE;
    console.logNote("Version " + app.version + ((app.versionDebug.length() > 0) ? " " + app.versionDebug : "") + " (" + app.versionDate + ")").tE = tE;
    if (app.versionDebug.equals("alpha")) console.logError("This is an alpha version \u2013 don't use it unless you know what you are doing").tE = tE;
    else if (app.versionDebug.equals("beta")) console.logWarning("This is a beta version \u2013 expect unexpected behavior").tE = tE;
    console.logNote("Copyright (C) 2008\u20132011 by Christian Thiemann").tE = tE;
    console.logNote("Research on Complex Systems, Northwestern University").tE = tE;
    console.logDebug("--------------------------------------------------------");
    console.logDebug("[OS] " + System.getProperty("os.name") + " " + System.getProperty("os.version") + " (" + System.getProperty("os.arch") + ")");
    console.logDebug("[JRE] " + System.getProperty("java.runtime.name") + " " + System.getProperty("java.runtime.version"));
    console.logDebug("[JVM] " + System.getProperty("java.vm.name") + " " + System.getProperty("java.vm.version") + " (" + System.getProperty("java.vm.vendor") + ") [" + (com.sun.jna.Platform.is64Bit() ? "64" : "32") + "-bit]");
    console.logDebug("[path] " + System.getenv(((PApplet.platform != PApplet.WINDOWS) ? ((PApplet.platform == PApplet.MACOSX) ? "DY" : "") + "LD_LIBRARY_" : "") + "PATH"));
    console.logDebug("[mem] max: " + (Runtime.getRuntime().maxMemory()/1024/1024) + " MB");
    // if (!JNMatLib.isLoaded() && (versionDebug.length() > 0)) console.logError("[JNMatLib] " + JNMatLib.getError().getMessage());
    console.logDebug("--------------------------------------------------------");
    add(console);
    // setup hotkeys and drop target
    setupHotkeys();
    app.dataTransferHandler = new DataTransferHandler(app);
  }

  private void setupHotkeys() {
    tfSearch.setHotKey(KeyEvent.VK_F, PApplet.MENU_SHORTCUT);
    if (btnWorkspaceRecovery != null) btnWorkspaceRecovery.setHotKey(KeyEvent.VK_R, PApplet.MENU_SHORTCUT);
    else btnRegexpSearch.setHotKey(KeyEvent.VK_R, PApplet.MENU_SHORTCUT);
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
    choiceDataset.setHotKeyChar(PApplet.TAB);
    choiceDataset.setShortcutChars(new char[] { 'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p' });
    choiceQuantity.setHotKeyChar('`');
    choiceQuantity.setShortcutChars(new char[] { '1', '2', '3', '4', '5', '6', '7', '8', '9', '0' });
    choiceColormap.setHotKeyChar('c');
    btnColormapLog.setHotKey(KeyEvent.VK_C, KeyEvent.SHIFT_MASK);
    btnQuantityPrevSnapshot.setHotKey(KeyEvent.VK_COMMA);
    btnQuantityNextSnapshot.setHotKey(KeyEvent.VK_PERIOD);
  }

  public void update() { update(false); }
  public void update(boolean fast) {
    SPaToDocument doc = app.doc;
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
    app.platformMagic.update();
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
    if (app.frameRate < 15) SPaToView.fastNodes = true;
    if (app.frameRate > 30) SPaToView.fastNodes = false;
    if (app.versionDebug.equals("beta")) {
      if (app.frameRate < 14) console.setShowDebug(true);
      if (app.frameRate > 20) console.setShowDebug(false);
    }
    // update search matches if necessary
    if (!searchMatchesValid)
      updateSearchMatches();
    // thorough updates
    if (!fast) {
      updateWorkspace();
      updateProjection();
      updateNodeColoring();
      updateAlbumControls();
    }
  }

  public void updateWorkspace() {
    choiceNetwork.removeAll();
    if (app.workspace.docs.size() > 0) {
      choiceNetwork.add(app.workspace.docs.toArray());
      if (app.workspace.doc != null)
        choiceNetwork.select(app.workspace.docs.indexOf(app.workspace.doc));
    }
    choiceNetwork.getContextMenu().setEnabled("document##save", app.workspace.doc != null);
    choiceNetwork.getContextMenu().setEnabled("document##saveAs", app.workspace.doc != null);
    choiceNetwork.getContextMenu().setEnabled("document##compressed", app.workspace.doc != null);
    choiceNetwork.getContextMenu().getItem("document##compressed").setText(
      ((app.workspace.doc != null) && app.workspace.doc.compressed) ? "Save uncompressed" : "Save compressed");
    choiceNetwork.getContextMenu().setEnabled("document##close", app.workspace.doc != null);
    choiceNetwork.getContextMenu().setEnabled("workspace##save", app.workspace.docs.size() > 0);
    choiceNetwork.getContextMenu().setEnabled("workspace##saveAs", app.workspace.docs.size() > 0);
    if ((btnWorkspaceRecovery != null) && !app.workspace.showWorkspaceRecoveryButton) {
      remove(btnWorkspaceRecovery.getParent().getParent());  // remove the button from the GUI
      btnWorkspaceRecovery.setHotKey(0);  // release hotkey
      btnWorkspaceRecovery = null;  // ... and we don't need that anymore
      btnRegexpSearch.setHotKey(KeyEvent.VK_R, PApplet.MENU_SHORTCUT);  // re-bind hotkey to Regexp toggle
    }
  }

  public void updateProjection() {
    if (app.workspace.doc == null) return;
    if (app.workspace.doc.view.hasMapLayout)
      choiceMapProjection.select(app.workspace.doc.view.xmlProjection.getString("name"));
    if (app.workspace.doc.view.hasTomLayout) {
      choiceTomProjection.select(app.workspace.doc.view.layouts[app.workspace.doc.view.l].projection);
      choiceTomScaling.select(app.workspace.doc.view.layouts[app.workspace.doc.view.l].scaling);
      choiceDistMat.removeAll();
      choiceDistMat.add(app.workspace.doc.getDistanceQuantities());
      choiceDistMat.select(app.workspace.doc.view.xmlDistMat);
    }
  }

  public void updateNodeColoring() {
    SPaToDocument doc = app.workspace.doc;
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
      sldQuantitySnapshot.setPreferredWidth(PApplet.max(75, PApplet.min(app.width-50, snapshots.length-1)));
      win.setVisibleAndEnabled(true);
      TComponent.Dimension d = win.getPreferredSize();
      validate();
      TComponent.Point p = choiceQuantity.getLocationOnScreen();
      float x = PApplet.max(3, p.x - btnQuantityPrevSnapshot.getPreferredSize().width/2);
      x = PApplet.min(app.width - 3 - win.getWidth(), x);
      float y = p.y - d.height;  // FIXME: what's all this stuff here?
  //      win.setBounds(x, y, d.width, d.height);
    }
  }

  public void updateAlbumControls() {
    SPaToDocument doc = app.workspace.doc;
    if (doc == null) return;  // done
    XMLElement xmlAlbum = doc.getAlbum();
    if (xmlAlbum == null) return;  // done
    lblAlbumName.setText(xmlAlbum.getString("name", xmlAlbum.getString("id", "Unnamed Album")) + ":");
    XMLElement snapshots[] = xmlAlbum.getChildren("snapshot");
    sldAlbumSnapshot.setValueBounds(0, snapshots.length-1);
    sldAlbumSnapshot.setValue(doc.getSelectedSnapshotIndex(xmlAlbum));
    sldAlbumSnapshot.setPreferredWidth(PApplet.max(100, PApplet.min(app.width/2, snapshots.length-1)));
    XMLElement snapshot = doc.getSelectedSnapshot(xmlAlbum);
    lblAlbumSnapshot.setText("[" + snapshot.getString("label", snapshot.getString("id", "Unnamed Snapshot")) + "]");
  }

  public void updateSearchMatches() {
    SPaToDocument doc = app.workspace.doc;
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
    SPaToDocument doc = app.workspace.doc;
    String argv[] = PApplet.split(cmd, "##");
    if (argv[0].equals("workspace")) {
      if (argv[1].equals("open")) app.workspace.openWorkspace();
      if (argv[1].equals("save")) app.workspace.saveWorkspace();
      if (argv[1].equals("saveAs")) app.workspace.saveWorkspace(true);
      if (argv[1].equals("recover")) app.workspace.replaceWorkspace(XMLElement.parse(prefs.get("workspace", "<workspace />")));
    } else if (argv[0].equals("document")) {
      if (argv[1].equals("new")) app.workspace.newDocument();
      if (argv[1].equals("open")) app.workspace.openDocument();
      if (argv[1].equals("save")) app.workspace.saveDocument();
      if (argv[1].equals("saveAs")) app.workspace.saveDocument(true);
      if (argv[1].equals("compressed")) { doc.setCompressed(!doc.isCompressed()); app.workspace.saveDocument(); }
      if (argv[1].equals("close")) app.workspace.closeDocument();
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
      app.workspace.switchToNetwork(argv[1]);
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
        updateNodeColoring();
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
      app.checkForUpdates(true);
    update();
  }

  public void draw(PGraphics g) {
    super.draw(g);
    if (searchMsg != null) {
      g.textFont(fnMedium); g.noStroke();
      g.fill(searchMsg.startsWith("E") ? 255 : 0, 0, 0);
      Point p = tfSearch.getLocationOnScreen();
      g.text(searchMsg.substring(1), p.x, p.y + tfSearch.getHeight() + 15);
    }
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
      if (res == null) try {  // FIXME: using app.doc here is sketchy...
        res = app.doc.getChild(app.doc.getChild("album[@id=" + xml.getString("album") + "]"), "snapshot[@selected]").getString("label");
      } catch (Exception e) { /* ignore any NullPointerExceptions or other stuff */ }
      return res;
    }
    // FIXME: maybe the string should be cached? (and erased on invalidate())
    public String getString(Object o, boolean inMenu) {
      XMLElement xml = (XMLElement)o;
      String str = xml.getString("name", xml.getString("id"));
      if (includeDataset && inMenu && xml.getName().equals("data") && (xml.getParent() != null))
        str = xml.getParent().getString("name", xml.getParent().getString("id")) + ": " + str;
      XMLElement snapshot = app.workspace.doc.getSelectedSnapshot(xml);
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
          app.textFont(style.getFont());  // FIXME: obtain PGraphics context
          String desc = !getEnabled(o) ? " (loading...)" : " \u2013 " + doc.getTitle();
          d.width += 5 + app.textWidth(desc);
        }
        return d;
      }
      public void draw(TChoice c, PGraphics g, Object o, TComponent.Rectangle bounds, boolean inMenu) {
        SPaToDocument doc = (SPaToDocument)o;
        String name = doc.getName();
        g.noStroke();
        g.textFont(c.getFont());
        g.fill(getEnabled(o) ? c.getForeground() : g.color(127));
        g.textAlign(g.LEFT, g.BASELINE);
        float x = bounds.x;
        float y = bounds.y + bounds.height - g.textDescent();
        float h = g.textAscent() + g.textDescent();
        if (bounds.height > h) y -= (bounds.height - h)/2;
        g.text(name, x, y);
        if (inMenu) {
          x += g.textWidth(name) + 5;
          g.textFont(style.getFont());
          String desc = !getEnabled(o) ? " (loading...)" : " \u2013 " + doc.getTitle();
          g.text(desc, x, y);
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
      setBackgroundColor(style.getBackgroundColorForCompactGroups()); }
    public TComponent.Dimension getMinimumSize() {
      SPaToDocument doc = app.workspace.doc;
      if ((doc == null) || !doc.view.hasNodes) return new TComponent.Dimension(0, 0);
      app.textFont(fnLarge);  // FIXME: we should have a PGraphics context here...
      float width = app.textWidth(doc.getTitle()), height = app.textAscent() + 1.5f*app.textDescent();
      app.textFont(fnMedium);
      String networkMeta[] = PApplet.split(doc.getDescription(), '\n');
      for (int i = 0; i < networkMeta.length; i++) {
        width = PApplet.max(app.width, app.textWidth(networkMeta[i]));
        height += app.textAscent() + 1.5f*app.textDescent();
      }
      return new TComponent.Dimension(width, height);
    }
    public void draw(PGraphics g) {
      SPaToDocument doc = app.workspace.doc;
      if ((doc == null) || !doc.view.hasNodes) return;
      super.draw(g);
      float x = bounds.x + padding.left, y = bounds.y + padding.top;
      g.textAlign(g.LEFT, g.BASELINE);
      g.textFont(fnLarge);
      g.noStroke();
      g.fill(0);
      y += g.textAscent() + .5f*g.textDescent();
      g.text(doc.getTitle(), x, y);
      y += g.textDescent();
      g.fill(127);
      g.textFont(fnMedium);
      String networkMeta[] = PApplet.split(doc.getDescription(), '\n');
      for (int i = 0; i < networkMeta.length; i++) {
        y += g.textAscent() + .5f*g.textDescent();
        g.text(networkMeta[i], x, y);
        y += g.textDescent();
      }
    }
  }

  class InPlaceRenamingTextField extends TTextField {
    TChoice choice = null;
    XMLElement xml = null;

    InPlaceRenamingTextField(TransparentGUI gui, TChoice choice) { super(gui); this.choice = choice; }

    public TComponent.Dimension getPreferredSize() {
      return new TComponent.Dimension(PApplet.max(choice.getWidth(), 50), choice.getHeight()); }

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

}