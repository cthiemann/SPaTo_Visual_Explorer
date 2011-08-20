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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.util.HashMap;
import java.util.Iterator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import net.spato.sve.app.layout.*;
import processing.core.PApplet;
import processing.xml.StdXMLBuilder;
import processing.xml.StdXMLParser;
import processing.xml.StdXMLReader;
import processing.xml.XMLElement;
import processing.xml.XMLValidator;
import processing.xml.XMLWriter;
import tGUI.*;


public class SPaToDocument {

  protected static int untitledCounter = 0;  // counter for uniquely numbering untitled documents
  protected SPaTo_Visual_Explorer app = null;

  XMLElement xmlDocument = new XMLElement("document");
  SPaToView view = null;

  File file = null;  // corresponding file on disk
  String name = null;  // short name (basename of file or "<untitled%d>")
  boolean compressed = false;  // is this document in a zip archive or in a directory?
  ZipFile zipfile = null;
  ZipOutputStream zipout = null;

  boolean modified = false;

  HashMap<XMLElement,BinaryThing> blobCache = new HashMap<XMLElement,BinaryThing>();

  public SPaToDocument(SPaTo_Visual_Explorer app) { this(app, null); }

  public SPaToDocument(SPaTo_Visual_Explorer app, File file) {
    this.app = app;
    setFile(file);
    this.view = new SPaToView(app, this);
  }

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
        return PApplet.trim(title);
    }
    return "Untitled Network";
  }

  public String getDescription() {
    XMLElement xmlDescription = xmlDocument.getChild("description");
    if (xmlDescription != null) {
      String desc = xmlDescription.getContent();
      if (desc != null)
        return PApplet.join(PApplet.trim(PApplet.split(desc, '\n')), '\n');
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
        res = (XMLElement[])PApplet.append(res, xmlData);
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
    app.gui.updateNodeColoring();
  }

  public XMLElement getDistanceQuantity() { return getChild("dataset/data[@distmat]"); }

  public void setDistanceQuantity(XMLElement xmlData) {
    if ((xmlData != null) && (xmlData == view.xmlDistMat))
      return;  // nothing to do
    if (view.xmlDistMat != null) view.xmlDistMat.remove("distmat");
    view.setDistanceMatrix(xmlData);
    if (xmlData != null) xmlData.setBoolean("distmat", true);
    app.gui.updateProjection();
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
        app.console.logError("Could not find album \u2018" + album + "\u2019, referenced in " + xml.getName() +
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
    if (isAlbum || (xml == getSelectedQuantity())) { view.setNodeColoringData(getSelectedQuantity()); app.gui.updateNodeColoring(); }
    if (isAlbum || (xml == getDistanceQuantity())) { view.setDistanceMatrix(getDistanceQuantity()); app.gui.updateProjection(); }
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
        TConsole.Message msg = app.console.logProgress((name != null)
          ? "Loading " + xmlPretty + " from blob " + name
          : "Parsing " + xmlPretty);
        if (name != null) {
          if ((stream = createDocPartInput("blobs" + File.separator + name)) != null)
            blob = BinaryThing.loadFromStream(new DataInputStream(stream), msg);
        } else
          blob = BinaryThing.parseFromXML(xml, msg);
        app.console.finishProgress();
      } catch (Exception e) {
        app.console.abortProgress("Error: ", e);
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
    app.gui.updateNodeColoring();
  }

  public void removeDataset(XMLElement xmlDataset) {
    for (XMLElement xmlData : xmlDataset.getChildren("data"))
      removeQuantity(xmlData);
    if (xmlDataset.getBoolean("selected"))
      setSelectedDataset((XMLElement)previousOrNext(getDatasets(), xmlDataset));
    xmlDataset.getParent().removeChild(xmlDataset);
    app.gui.updateNodeColoring();
  }

  public void addQuantity(XMLElement xmlDataset, XMLElement xmlData) { addQuantity(xmlDataset, xmlData, null); }
  public void addQuantity(XMLElement xmlDataset, XMLElement xmlData, Object blob) {
    xmlDataset.removeChild(xmlData);  // make sure we won't add an already existing quantity
    if (xmlData.getString("id") == null) xmlData.setString("id", generateID());
    xmlDataset.addChild(xmlData);
    if (blob != null) setBlob(xmlData, blob, true);
    app.gui.updateNodeColoring();
    app.gui.updateProjection();
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
    TConsole.Message msg = app.console.logInfo("Loading from " + file.getAbsolutePath()).sticky();
    // open zipfile or make sure the directory exists
    try {
      if (compressed) zipfile = new ZipFile(file);
      else if (!file.exists()) throw new IOException("directory not found");
    } catch (Exception e) {
      app.console.logError("Error opening '" + file.getAbsolutePath() + "': ", e);
      app.console.popSticky();
      return;
    }
    // read XML document
    if ((xmlDocument = addAutoIDs(readMultiPartXML("document.xml"))) != null) {
      // setup nodes and map projection
      XMLElement xmlNodes = getNodes();
      if ((xmlNodes == null) || (getNodeCount() == 0))
        app.console.logError("No nodes found");
      else {
        view.setNodes(xmlNodes);
        view.setMapProjection(xmlNodes.getChild("projection"));
      }
      app.gui.updateAlbumControls();
      // setup links
      view.setLinks(getLinks());
      if (getLinks() != null)
        loadBlobs(getLinks());  // make sure all links snapshots are loaded
      // setup data
      view.setNodeColoringData(getSelectedQuantity());
      loadBlobs(getAllQuantities());  // make sure all data is loaded
      app.gui.updateNodeColoring();
      // setup slices
      view.setSlices(getSlices());
      loadBlobs(getSlices());  // make sure all slices snapshots are loaded
      generateLayouts(getSlices());  // FIXME
      // setup tomogram layout
      view.tomLayouts = xmlDocument.getString("tomLayouts", null);  // FIXME: layouts should be specified by <layout> tags or something
      view.setTomLayout();
      view.setDistanceMatrix(getDistanceQuantity());
      app.gui.updateProjection();
    }
    // clean-up and done
    if (compressed) { try { zipfile.close(); } catch (Exception e) {}; zipfile = null; }
    app.console.popSticky();
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
    TConsole.Message msg = app.console.logInfo("Saving to " + file.getAbsolutePath()).sticky();
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
      app.console.logError("Error opening '" + file.getAbsolutePath() + "': ", e);
      app.console.popSticky();
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
        TConsole.Message msgBlob = app.console.logProgress("Saving " + xmlPretty + " to blob " + name);
        blobCache.get(xml).saveToStream(new DataOutputStream(stream), msgBlob);
        app.console.finishProgress();
        if (!compressed) stream.close();
      } catch (Exception e) { app.console.abortProgress("Error saving blob " + name + ": ", e); }
    }
    // clean-up and done
    if (compressed) { try { zipout.close(); } catch (Exception e) {}; zipout = null; }
    app.console.popSticky();
    msg.text += " \u2013 done";
  }

  public InputStream createDocPartInput(String name) {
    InputStream stream = null;
    if (compressed) {
      name = name.replace(File.separatorChar, '/');  // always use / as file separator in zip files
      try { stream = zipfile.getInputStream(zipfile.getEntry(name)); }
      catch (Exception e) { stream = null; }
    } else
      stream = app.createInput(new File(file, name).getAbsolutePath());
    if (stream == null)
      app.console.logError("Could not find or read '" + name + "' in '" + file.getAbsolutePath() + "'");
    return new BufferedInputStream(stream);
  }

  public OutputStream createDocPartOutput(String name) {
    OutputStream stream = null;
    if (compressed) {
      name = name.replace(File.separatorChar, '/');  // always use / as file separator in zip files
      try { zipout.putNextEntry(new ZipEntry(name)); stream = zipout; }
      catch (Exception e) { stream = null; }
    } else
      stream = app.createOutput(new File(file, name).getAbsolutePath());
    if (stream == null)
      app.console.logError("Error opening '" + name + "' in '" + file.getAbsolutePath() + "' for writing");
    return compressed ? stream : new BufferedOutputStream(stream);  // zipout is already buffered
  }

  public Reader createDocPartReader(String name) {
    try { return new InputStreamReader(createDocPartInput(name)); } catch (Exception e) { return null; } }
  public Writer createDocPartWriter(String name) {
    try { return new OutputStreamWriter(createDocPartOutput(name)); } catch (Exception e) { return null; } }

  /* Copy of processing.xml.XMLElement.parseFromReader(...), modified to fit our needs (i.e., catch parsing
   * exceptions and return null instead of silently ignoring and returning a crippled document). */
  public XMLElement readXML(String name) {
    Reader reader = new File(name).isAbsolute() ? app.createReader(name) : createDocPartReader(name);
    if (reader == null) return null;
    XMLElement xml = new XMLElement();
    try {
      app.console.logProgress("Parsing " + name).indeterminate();
      StdXMLParser parser = new StdXMLParser();
      parser.setBuilder(new StdXMLBuilder(xml));
      parser.setValidator(new XMLValidator());
      parser.setReader(new StdXMLReader(reader));
      parser.parse();
      app.console.finishProgress();
    } catch (Exception e) {
      app.console.abortProgress("XML parsing error in " + name + ": ", e);
      xml = null;
    }
    try { reader.close(); } catch (Exception e) { }
    return xml;
  }

  public void writeXML(String name, XMLElement xml) {
    Writer writer = new File(name).isAbsolute() ? app.createWriter(name) : createDocPartWriter(name);
    if (writer == null) return;
    try {
      app.console.logProgress("Writing " + name).indeterminate();
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      new XMLWriter(writer).write(xml, true);
      if (!compressed) writer.close();  // we're directly writing into the zipfile, which will be closed by saveToDisk
      app.console.finishProgress();
    } catch (Exception e) {
      app.console.abortProgress("XML writing error in " + name + ": ", e);
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
        app.console.logError(child.getName() + ": no match found in " + src);
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
      map.put(src, (XMLElement[])PApplet.append(map.get(src), child));
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
      digest[i] = (byte)PApplet.parseInt(app.random(256));
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
        result = (XMLElement[])PApplet.concat(result, getChildren(tmp[i], subpath));
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
        result = (XMLElement[])PApplet.append(result, xml[i]);
      }
      return result;
    } else
      return xml;
  }

}
