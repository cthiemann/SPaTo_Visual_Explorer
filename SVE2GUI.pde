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

import java.awt.event.KeyEvent;

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

void guiSetup() {
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
    { "New document\u2026", "document##new" },
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
  console.logNote("Copyright (C) 2008–2011 by Christian Thiemann").tE = tE;
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

void guiSetupHotkeys() {
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

void guiUpdate() {
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

void guiFastUpdate() {
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

void guiUpdateProjection() {
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

void guiUpdateNodeColoring() {
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
  if (doc.view.hasData) btnColormapLog.setSelected(doc.view.colormap.logscale);
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

void guiUpdateAlbumControls() {
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

void guiUpdateSearchMatches() {
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

void actionPerformed(String cmd) {
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
    doc.view.layouts[0].setupScaling(argv[1], doc.view.minD/1.25);  // FIXME: when does this insanity end?
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
  String getActionCommand(Object o) {
    XMLElement xml = (XMLElement)o;
    String str = xml.getString("id");
    if (includeDataset && xml.getName().equals("data") && (xml.getParent() != null))
      str = xml.getParent().getString("id") + "##" + str;
    return str;
  }
  String getSnapshotLabel(XMLElement xml) {
    String res = xml.getString("label");
    if (res == null) try {
      res = doc.getChild(doc.getChild("album[@id=" + xml.getString("album") + "]"), "snapshot[@selected]").getString("label");
    } catch (Exception e) { /* ignore any NullPointerExceptions or other stuff */ }
    return res;
  }
  // FIXME: maybe the string should be cached? (and erased on invalidate())
  String getString(Object o, boolean inMenu) {
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
    boolean getEnabled(Object o) { return ((SVE2Document)o).view.hasMapLayout || ((SVE2Document)o).view.hasTomLayout; }
    String getActionCommand(Object o) { return ((SVE2Document)o).getName(); }
    TComponent.Dimension getPreferredSize(TChoice c, Object o, boolean inMenu) {
      SVE2Document doc = (SVE2Document)o;
      TComponent.Dimension d = super.getPreferredSize(c, doc.getName(), inMenu);
      if (inMenu) {
        textFont(gui.style.getFont());
        String desc = !getEnabled(o) ? " (loading...)" : " – " + doc.getTitle();
        d.width += 5 + textWidth(desc);
      }
      return d;
    }
    void draw(TChoice c, PGraphics g, Object o, TComponent.Rectangle bounds, boolean inMenu) {
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
        String desc = !getEnabled(o) ? " (loading...)" : " – " + doc.getTitle();
        text(desc, x, y);
      }
    }
  }
  TComponent rollOverComponent = null;
  TChoiceWithRollover(TransparentGUI gui, String actionCmdPrefix) {
    super(gui, actionCmdPrefix); setRenderer(new SVE2DocumentRenderer()); }
  void handleMouseEntered() { super.handleMouseEntered(); if (rollOverComponent != null) rollOverComponent.setVisible(true); }
  void handleMouseExited() { super.handleMouseExited(); if (rollOverComponent != null) rollOverComponent.setVisible(false); }
}

class NetworkDetailPanel extends TComponent {
  NetworkDetailPanel(TransparentGUI gui) { super(gui); setPadding(5, 10); setMargin(0);
    setBackgroundColor(gui.style.getBackgroundColorForCompactGroups()); }
  TComponent.Dimension getMinimumSize() {
    if ((doc == null) || !doc.view.hasNodes) return new TComponent.Dimension(0, 0);
    textFont(fnLarge);
    float width = textWidth(doc.getTitle()), height = textAscent() + 1.5*textDescent();
    textFont(fnMedium);
    String networkMeta[] = split(doc.getDescription(), '\n');
    for (int i = 0; i < networkMeta.length; i++) {
      width = max(width, textWidth(networkMeta[i]));
      height += textAscent() + 1.5*textDescent();
    }
    return new TComponent.Dimension(width, height);
  }
  void draw(PGraphics g) {
    if ((doc == null) || !doc.view.hasNodes) return;
    super.draw(g);
    float x = bounds.x + padding.left, y = bounds.y + padding.top;
    g.textAlign(LEFT, BASELINE);
    g.textFont(fnLarge);
    g.noStroke();
    g.fill(0);
    y += g.textAscent() + .5*g.textDescent();
    g.text(doc.getTitle(), x, y);
    y += g.textDescent();
    g.fill(127);
    g.textFont(fnMedium);
    String networkMeta[] = split(doc.getDescription(), '\n');
    for (int i = 0; i < networkMeta.length; i++) {
      y += g.textAscent() + .5*g.textDescent();
      text(networkMeta[i], x, y);
      y += g.textDescent();
    }
  }
}

class InPlaceRenamingTextField extends TTextField {
  TChoice choice = null;
  XMLElement xml = null;
  
  InPlaceRenamingTextField(TransparentGUI gui, TChoice choice) { super(gui); this.choice = choice; }
  
  TComponent.Dimension getPreferredSize() {
    return new TComponent.Dimension(max(choice.getWidth(), 50), choice.getHeight()); }
  
  void show() {
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
  
  void draw(PGraphics g) {
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
