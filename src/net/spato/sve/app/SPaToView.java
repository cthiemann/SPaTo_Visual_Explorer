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

import net.spato.sve.app.layout.*;
import processing.core.PApplet;
import processing.core.PGraphics;
import processing.xml.XMLElement;
import tGUI.TConsole;


public class SPaToView {

  public static float linkLineWidth = 0.25f;
  public static boolean fastNodes = false;
  protected SPaTo_Visual_Explorer app = null;

  SPaToDocument doc = null;

  public SPaToView(SPaTo_Visual_Explorer app, SPaToDocument doc) { this.app = app; this.doc = doc; }

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
      String pieces[] = PApplet.split(node.getString("location", app.random(-1,1) + "," + app.random(-1,1)), ',');
      poslong = PApplet.parseFloat(pieces[0]);
      poslat = PApplet.parseFloat(pieces[1]);
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
          value[NL] = logValues ? PApplet.log(sm.value[i][l]) : sm.value[i][l];
          minval = PApplet.min(minval, value[NL]);
          maxval = PApplet.max(maxval, value[NL]);
          NL++;
        }
      }
      // sort
      if (asyncSort) app.worker.submit(new Runnable() { public void run() { sort(); } });
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
  public boolean hasNodes = false;
  public int NN = -1, ih = -1;  // number of nodes, currently hovered node
  Node nodes[] = null;
  // links
  public boolean hasLinks = false;
  XMLElement xmlLinks = null;
  SparseMatrix links = null;
  SortedLinkList loglinks = null;
  // map layout
  public boolean hasMapLayout = false;
  XMLElement xmlProjection = null;
  Projection projMap = null;
  // node coloring data
  public boolean hasData = false;
  XMLElement xmlData = null;
  Colormap colormap = null;
  final int DATA_1D = 0, DATA_2D = 1;
  int datatype = DATA_1D;
  float data[][] = null;
  // slices
  public boolean hasSlices = false;
  XMLElement xmlSlices = null;
  int r = -1;  // current root node
  int pred[][] = null;  // predecessor vectors
  SparseMatrix salience = null;  // salience matrix (fraction of slices in which each link participates)
  // tomogram layouts
  public boolean hasTomLayout = false;
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

  public void setLinks(XMLElement xmlLinks) { setLinks(xmlLinks, app.console); }
  public void setLinks(XMLElement xmlLinks, TConsole console) {
    hasLinks = false;
    this.xmlLinks = xmlLinks;
    if (!hasNodes || (xmlLinks == null)) return;
    BinaryThing blob = doc.getBlob(xmlLinks);
    if (blob == null) { app.console.logError("Data for links \u201C" + xmlLinks.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
    if (!blob.isSparse(NN)) { app.console.logError("Data format error (expected SparseMatrix[" + NN + "]): " + blob); return; }
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
        app.console.logWarning("Correcting indices in the sparse weight matrix to zero-based");
        for (int i = 0; i < NN; i++)
          for (int l = 0; l < links.index[i].length; l++)
            links.index[i][l]--;
      }
    }
    // END work-around
    loglinks = new SortedLinkList(links, true, true, true);
    hasLinks = true;
  }

  public void setSlices(XMLElement xmlSlices) { setSlices(xmlSlices, app.console); }
  public void setSlices(XMLElement xmlSlices, TConsole console) {
    hasSlices = false;
    if (!hasNodes || ((xmlSlices == null) && (!hasLinks))) return;
    // prepare data structure
    pred = new int[NN][NN];
    D = new float[NN][NN];
    // read data
    if (xmlSlices != null) {
      BinaryThing blob = doc.getBlob(xmlSlices);
      if (blob == null) { app.console.logError("Data for slices \u201C" + xmlSlices.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
      if (!blob.isInt2(NN)) { app.console.logError("Data format error (expected int[" + NN + "][" + NN + "]): " + blob); return; }
      pred = blob.getIntArray();
    } else {
      // calculate shortest path trees from scratch
      boolean inverse = xmlLinks.getBoolean("inverse");
      app.console.logProgress("Calculating shortest-path trees");
      for (int r = 0; r < NN; r++) {
        Dijkstra.calculateShortestPathTree(links.index, links.value, r, pred[r], D[r], inverse);
        app.console.updateProgress(r, NN);
      }
      app.console.finishProgress();
      // process data
      minD = Float.POSITIVE_INFINITY;
      maxD = Float.NEGATIVE_INFINITY;
      float mean = 0; int meanCount = 0;
      for (int r = 0; r < NN; r++) {
        maxD = PApplet.max(maxD, PApplet.max(D[r]));
        for (int i = 0; i < NN; i++) {
          if (r == i) continue;
          minD = PApplet.min(minD, D[r][i]);
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
    app.console.logProgress("Calculating salience matrix");
    int abundance[][] = new int[NN][NN];
    float salienceFull[][] = new float[NN][NN];
    for (int root = 0; root < NN; root++) {
      for (int i = 0; i < NN; i++)
        if (pred[root][i] != -1)
          abundance[i][pred[root][i]]++;
      app.console.updateProgress(root, 2*NN);
    }
    for (int i = 0; i < NN; i++) {
      for (int j = 0; j < NN; j++)
        salienceFull[i][j] = salienceFull[j][i] = (float)(abundance[i][j] + abundance[j][i])/NN;
      app.console.updateProgress(NN+i, 2*NN);
    }
    salience = new SparseMatrix(salienceFull);  // FIXME: performance? (SparseMatrix uses a lot of append())
    app.console.finishProgress();
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
    if (blob == null) { app.console.logError("Data for quantity \u201C" + xmlData.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
    if (blob.isFloat1(NN)) datatype = DATA_1D;
    else if (blob.isFloat2(NN)) datatype = DATA_2D;
    else { app.console.logError("Data format error (expected float[1][" + NN + "] or float[" + NN + "][" + NN + "]): " + blob); return; }
    data = blob.getFloatArray();
    // process values
    float mindata = Float.POSITIVE_INFINITY;
    float maxdata = Float.NEGATIVE_INFINITY;
    for (int root = 0; root < data.length; root++) {
      for (int j = 0; j < data[root].length; j++) {
        if (Float.isInfinite(data[root][j]) || Float.isNaN(data[root][j])) continue;
        mindata = PApplet.min(mindata, data[root][j]);
        maxdata = PApplet.max(maxdata, data[root][j]);
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
    String layoutNames[] = PApplet.split(tomLayouts, ' ');
    layouts = new Layout[NL = layoutNames.length];
    for (int l = 0; l < NL; l++)
      layouts[l] = new Layout(pred, layoutNames[l]);
    layouts[this.l = 0].updateProjection(r, D);
    hasTomLayout = true;
  }

  public void setDistanceMatrix(XMLElement xmlData) { setDistanceMatrix(xmlData, app.console); }
  public void setDistanceMatrix(XMLElement xmlData, TConsole console) {
    if (!hasTomLayout) return;
    xmlDistMat = xmlData;
    if (xmlData != null) {
      // read values
      BinaryThing blob = doc.getBlob(xmlData);
      if (blob == null) { app.console.logError("Data for quantity \u201C" + xmlData.getString("name", "<unnamed>") + "\u201D is corrupt"); return; }
      if (!blob.isFloat2(NN)) { app.console.logError("Data format error (expected float[" + NN + "][" + NN + "]): " + blob); return; }
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
          minD = PApplet.min(minD, D[root][j]);
        if (!Float.isInfinite(D[root][j]) && !Float.isNaN(D[root][j]))
          maxD = PApplet.max(maxD, D[root][j]);
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
  float viewWidth = app.width;
  float aNodes = 0, aLinks = 0, aSkeleton = 0, aNeighbors = 0, aNetwork = 0, aLabels = 0;

  public void draw(PGraphics g) {
    if (!hasNodes || (!hasMapLayout && !hasTomLayout)) return;  // nothing to draw
    if ((showNeighbors || showNetwork) && !hasLinks) {
      showNeighbors = false; aNeighbors = 0; showNetwork = false; aNetwork = 0; }  // can't draw full network
    Projection p = ((viewMode == VIEW_MAP) || !hasTomLayout) ? projMap : layouts[0].proj;
    boolean linksVisible = (showLinks && !showSkeleton && !showNeighbors && !showNetwork) ||
      (showSkeleton && showLinksWithSkeleton) || (showNeighbors && showLinksWithNeighbors) || (showNetwork && showLinksWithNetwork);
    aNodes += 3*((showNodes ? 1 : 0) - aNodes)*PApplet.min(app.dt, 1/3.f);
    aLinks += 3*((linksVisible ? 1 : 0) - aLinks)*PApplet.min(app.dt, 1/3.f);
    aSkeleton += 3*((showSkeleton ? 1 : 0) - aSkeleton)*PApplet.min(app.dt, 1/3.f);
    aNeighbors += 3*((showNeighbors ? 1 : 0) - aNeighbors)*PApplet.min(app.dt, 1/3.f);
    aNetwork += 3*((showNetwork ? 1 : 0) - aNetwork)*PApplet.min(app.dt, 1/3.f);
    aLabels += 3*((showLabels ? 1 : 0) - aLabels)*PApplet.min(app.dt, 1/3.f);
    // get current data
    float[] val = null;
    if (hasData) {
      switch (datatype) {
        case DATA_1D: val = data[0]; break;
        case DATA_2D:
          if (!app.isAltDown && (r > -1)) val = data[r];
          if (app.isAltDown && (ih > -1)) val = data[ih];
          break;
      }
    }
    if ((xmlDistMat == null) && (viewMode == VIEW_TOM)) {
      minD = 1;
      for (int i = 0; i < NN; i++)
        maxD = PApplet.max(maxD, D[r][i] = (pred[r][i] == -1) ? 0 : D[r][pred[r][i]] + 1);
      layouts[l].updateProjection(r, D);
    }
    // update node positions and determine hovered node
    boolean wrap = ((viewMode == VIEW_MAP) && xmlProjection.getString("name").equals("LonLat Roll"));
    if ((tmpx == null) || (tmpx.length != NN)) { tmpx = new float[NN]; tmpy = new float[NN]; }
    p.setScalingToFitWithin(wrap ? g.width : .9f*g.width, .9f*g.height);
    if (wrap) {  // FIXME: wrapping should be handled by the projection
      float targetViewWidth = (wrap ? g.width : .9f*g.width)*zoom[viewMode];  // width of the scaled data (used for wrapping)
      viewWidth += 3*(targetViewWidth - viewWidth)*PApplet.min(app.dt, 1/3.f);
      while (xoff[viewMode] > +viewWidth) { xoff[viewMode] -= viewWidth; for (int i = 0; i < NN; i++) nodes[i].x -= viewWidth; }
      while (xoff[viewMode] < -viewWidth) { xoff[viewMode] += viewWidth; for (int i = 0; i < NN; i++) nodes[i].x += viewWidth; }
    }
    float mind = g.width*g.height;
    ih = -1;  // currently hovered node
    for (int i = 0; i < NN; i++) {
      boolean invis = (viewMode == VIEW_TOM) && (pred[i][r] == -1) && (i != r);
      float tx = invis ? nodes[i].x : p.sx*(p.x[i] - p.cx)*zoom[viewMode] + xoff[viewMode] + g.width/2;
      float ty = invis ? nodes[i].y : p.sy*(p.y[i] - p.cy)*zoom[viewMode] + yoff[viewMode] + g.height/2;
      float ta = invis ? 0 : 1;
      nodes[i].x = Float.isNaN(nodes[i].x) ? tx : nodes[i].x + 3*(tx - nodes[i].x)*PApplet.min(app.dt, 1/3.f);
      nodes[i].y = Float.isNaN(nodes[i].y) ? ty : nodes[i].y + 3*(ty - nodes[i].y)*PApplet.min(app.dt, 1/3.f);
      nodes[i].a += 3*(ta - nodes[i].a)*PApplet.min(app.dt, 1/3.f);
      if (wrap) {
        tmpx[i] = nodes[i].x; tmpy[i] = nodes[i].y;
        if (nodes[i].x - g.width/2 > +viewWidth/2) nodes[i].x -= viewWidth;
        if (nodes[i].x - g.width/2 < -viewWidth/2) nodes[i].x += viewWidth;
      }
      float d = PApplet.dist(nodes[i].x, nodes[i].y, app.mouseX, app.mouseY);
      if (!invis &&
          (app.gui.componentAtMouse == null) && (app.gui.componentMouseClicked == null) &&
          (d < 50) && (d < mind) &&
          (!app.searchMatchesValid || !app.tfSearch.isFocusOwner() ||
           app.searchMatches[i] || (app.searchMatchesChild[i] > 0))) {
        mind = d; ih = i; }
    }
    // draw links
    if (hasSlices && ((aLinks > 1/192.f) || (aSkeleton > 1/192.f) || (aNeighbors > 1/192.f) || (aNetwork > 1/192.f))) {
      g.noFill(); g.strokeWeight(linkLineWidth);
      // update search matches
      if (app.searchMatchesValid) {
        // update branch matching flags (would need to be recursive, but we are sloppy here)
        // 1: node i matches search
        // 0: node i does not match and we don't know of any children who match
        // 2: node i does not match but we used to have matching children
        for (int i = 0; i < NN; i++)
          app.searchMatchesChild[i] = app.searchMatches[i] ? 1 : 2*app.searchMatchesChild[i];
        // if we think that node i has a matching child, tell the parent of node i
        for (int i = 0; i < NN; i++)
          if ((app.searchMatchesChild[i] > 0) && (pred[r][i] != -1))
            app.searchMatchesChild[pred[r][i]] = 1;
        // if any of the nodes with status 2 has not been set to 1 by now, there is no matching child
        for (int i = 0; i < NN; i++)
          if (app.searchMatchesChild[i] == 2)
            app.searchMatchesChild[i] = 0;
      }
      // visualize salience matrix
      if (aSkeleton > 1/192.f) {
        for (int i = 0; i < NN; i++) {
          for (int l = 0; l < salience.index[i].length; l++) {
            int j = salience.index[i][l];
            if (j >= i) continue;  // avoid drawing duplicate links
            if (salience.value[i][l] == 0) continue;  // not a salient link
            g.stroke(192*(1 - salience.value[i][l]), 192*aSkeleton);
            // FIXME: the following code is copy'n'pasted...
            if (!wrap || (PApplet.abs(nodes[i].x - nodes[j].x) < viewWidth/2))
              g.line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
            else {
              if (nodes[i].x < nodes[j].x) {
                g.line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
                g.line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              } else {
                g.line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
                g.line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
              }
            }
          }
        }
      }
      // show direct neighbors of current root node
      if (aNeighbors > 1/192.f) synchronized (loglinks) {  // sync against SortedLinkList.sort()
        int i0 = app.isAltDown ? ih : r;  // show neighbors of hovered node if Alt is held down
        for (int l = 0; l < loglinks.NL; l++) {
          int i = loglinks.src[l], j = loglinks.dst[l];
          if ((i != i0) && (j != i0)) continue;
          g.stroke(192*(1 - (loglinks.value[l] - loglinks.minval)/(loglinks.maxval - loglinks.minval)), 192*aNeighbors);
          // FIXME: the following code is copy'n'pasted...
          if (!wrap || (PApplet.abs(nodes[i].x - nodes[j].x) < viewWidth/2))
            g.line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
          else {
            if (nodes[i].x < nodes[j].x) {
              g.line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
              g.line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
            } else {
              g.line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              g.line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
            }
          }
        }
      }
      // show full network
      if (aNetwork > 1/192.f) synchronized (loglinks) {  // sync against SortedLinkList.sort()
        for (int l = 0; l < loglinks.NL; l++) {
          int i = loglinks.src[l], j = loglinks.dst[l];
          g.stroke(192*(1 - (loglinks.value[l] - loglinks.minval)/(loglinks.maxval - loglinks.minval)), 192*aNetwork);
          // FIXME: the following code is copy'n'pasted...
          if (!wrap || (PApplet.abs(nodes[i].x - nodes[j].x) < viewWidth/2))
            g.line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
          else {
            if (nodes[i].x < nodes[j].x) {
              g.line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
              g.line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
            } else {
              g.line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              g.line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
            }
          }
        }
      }
      // draw links in selected slice
      if (aLinks > 1/192.f) {
        float aSNN = PApplet.max(aSkeleton, PApplet.max(aNeighbors, aNetwork));
        if (!showLinksWithSkeleton && !showLinksWithNeighbors && !showLinksWithNetwork && !(aSNN > 1 - 1/192.f)) aSNN = 0;
        float aN = (showLinksWithNetwork || (aNetwork > 1 - 1/192.f)) ? aNetwork : 0;
        g.strokeWeight(PApplet.min(1, linkLineWidth + aN*aLinks));  // strongly emphasize slice if drawing over full network
        g.stroke(64 + 128*aSNN, 64*(1 - aSNN), 64*(1 - aSNN), 192*aLinks + 63*aSNN);
        for (int i = 0; i < NN; i++) {
          int j = pred[r][i];
          if (j == -1) continue;  // don't draw links from disconnected (or root) nodes
          if (app.searchMatchesValid) {
            float alphafactor = (app.searchMatches[i] || (app.searchMatchesChild[i] > 0)) ? 1.25f : 0.25f;
            g.stroke(64 + 128*aSNN, 64*(1 - aSNN), 64*(1 - aSNN), PApplet.min(255, (192*aLinks + 63*aSNN)*alphafactor));
            g.strokeWeight((alphafactor > 1) ? 1 : 0.25f);
          }
          if (!wrap || (PApplet.abs(nodes[i].x - nodes[j].x) < viewWidth/2))
            g.line(nodes[i].x, nodes[i].y, nodes[j].x, nodes[j].y);
          else {
            if (nodes[i].x < nodes[j].x) {
              g.line(nodes[i].x, nodes[i].y, nodes[j].x - viewWidth, nodes[j].y);
              g.line(nodes[i].x + viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
            } else {
              g.line(nodes[i].x - viewWidth, nodes[i].y, nodes[j].x, nodes[j].y);
              g.line(nodes[i].x, nodes[i].y, nodes[j].x + viewWidth, nodes[j].y);
            }
          }
        }
      }
      g.strokeWeight(1);
    }
    // draw nodes
    g.rectMode(PApplet.CENTER);
    float nodeSize_target = nodeSizeFactor*PApplet.sqrt(PApplet.min(g.width, g.height))*PApplet.sqrt(zoom[viewMode]);
    nodeSize += 3*(nodeSize_target - nodeSize)*PApplet.min(app.dt, 1/3.f);
    if (aNodes > 1/192.f) {
      g.noStroke();
      for (int i = 0; i < NN; i++) {
        float alphafactor = !app.searchMatchesValid ? 1 : (app.searchMatches[i] ? 1.25f : 0.125f);
        g.fill((val == null) ? 127 : colormap.getColor(val[i]), 192*aNodes*nodes[i].a*alphafactor);
        if (fastNodes)
          g.rect(nodes[i].x + .5f, nodes[i].y + .5f, 3*nodeSize/4, 3*nodeSize/4);
        else
          g.ellipse(nodes[i].x, nodes[i].y, nodeSize, nodeSize);
      }
    }
    // mark root node and hovered node (these are shown even if showNodes is false)
    if (r > -1) {
      g.stroke(255, 0, 0); g.noFill();
      if (fastNodes) g.rect(nodes[r].x, nodes[r].y, 3*nodeSize/4 + .5f, 3*nodeSize/4 + .5f);
      else g.ellipse(nodes[r].x, nodes[r].y, nodeSize + .5f, nodeSize + .5f);
    }
    if (ih > -1) {
      g.stroke(200, 0, 0); g.noFill();
      if (fastNodes) g.rect(nodes[ih].x, nodes[ih].y, 3*nodeSize/4 + .5f, 3*nodeSize/4 + .5f);
      else g.ellipse(nodes[ih].x, nodes[ih].y, nodeSize + .5f, nodeSize + .5f);
    }
    g.rectMode(PApplet.CORNER);
    // draw labels
    g.textFont(/*fnSmall*/app.fnMedium);
    g.textAlign(PApplet.LEFT, PApplet.BASELINE);
    g.noStroke();
    for (int i = 0; i < NN; i++) {
      if ((i == r) || (i == ih) || ((aLabels > 1/255.f) && nodes[i].showLabel && (!app.searchMatchesValid || app.searchMatches[i]))) {
        g.fill(0, 255*aLabels*nodes[i].a);
        g.text(nodes[i].label + (((i == ih) && (val != null)) ? " (" + format(val[i]) + ")" : ""),
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
      String res = PApplet.nfc(val, nd);
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
    app.saveStrings(filename, lines);
  }

}
