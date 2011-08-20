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
import java.io.PrintWriter;
import java.util.Vector;
import java.util.zip.ZipFile;
import javax.swing.filechooser.FileFilter;
import net.spato.sve.app.util.FileDialogUtils;
import processing.xml.XMLElement;
import processing.xml.XMLWriter;


public class Workspace {

  protected SPaTo_Visual_Explorer app = null;

  protected File workspaceFile = null;
  public boolean showWorkspaceRecoveryButton = true;  // FIXME: should not be public

  protected Vector<SPaToDocument> docs = new Vector<SPaToDocument>();  // all loaded documents
  public SPaToDocument doc = null;  // current document  // FIXME: public?

  public Workspace(SPaTo_Visual_Explorer app) {
    this.app = app;
  }

  /*
   * Document Management
   */

  // This FileFilter accepts:
  //   1) directories ending with ".spato" if they contain a document.xml
  //   2) files ending with ".spato" if they are zip files
  //   3) files named "document.xml" if they are in directory ending with ".spato"
  // It's probably a good idea to allow case (3), because selecting directories might
  // seem unusally awkward for some users (it's still possible, though).
  public static final FileFilter ffDocuments = new FileFilter() {
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
    File result[] = FileDialogUtils.selectFiles(FileDialogUtils.OPENMULTIPLE, "Open document", ffDocuments);
    for (int i = 0; i < result.length; i++)            // normalize filename if some
      if (result[i].getName().equals("document.xml"))  // blabla.spato/document.xml
        result[i] = result[i].getParentFile();         // was selected
    return result;
  }
  public File selectDocumentFileToWrite() { return selectDocumentFileToWrite(null); }
  public File selectDocumentFileToWrite(File selectedFile) {
    return FileDialogUtils.ensureExtension("spato",
      FileDialogUtils.selectFile(FileDialogUtils.SAVE, "Save document", ffDocuments, selectedFile));
  }

  public void newDocument() {
    SPaToDocument newdoc = new SPaToDocument(app);
    docs.add(newdoc);
    switchToNetwork(newdoc);
  }

  public void openDocument() { openDocuments(selectDocumentFilesToOpen()); }
  public void openDocument(File file) { if (file != null) openDocuments(new File[] { file }); }
  public void openDocuments(File files[]) {
    if (files == null) return;
    for (File f : files) {
      SPaToDocument newdoc = null;
      for (SPaToDocument d : docs)
        if (d.getFile().equals(f))
          newdoc = d;  // this document is already open
      if (newdoc == null) {  // load if not already open
        newdoc = new SPaToDocument(app, f);
        docs.add(newdoc);
        app.worker.submit(newdoc.newLoadingTask());
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
    for (SPaToDocument d : docs)
      if ((d != doc) && d.getFile().equals(file))
        docs.remove(d);  // prevent duplicate entries in docs
    if ((file == null) || forceSelect) {
      if ((file = selectDocumentFileToWrite(file)) == null) return;
      boolean compressed = !file.exists() || !file.isDirectory();  // save compressed by default
      doc.setFile(file, compressed);
      updateWorkspacePref();
    }
    app.worker.submit(doc.newSavingTask());
  }

  public boolean switchToNetwork(int i) { return switchToNetwork(((i < 0) || (i >= docs.size())) ? docs.get(i) : null); }
  public boolean switchToNetwork(String name) {
    SPaToDocument newdoc = null;
    for (int i = 0; i < docs.size(); i++)
      if (name.equals(docs.get(i).getName()))
        newdoc = docs.get(i);
    return switchToNetwork(newdoc);
  }
  public boolean switchToNetwork(SPaToDocument newdoc) {
    app.searchMatchesValid = false;
    app.searchMatches = null;
    doc = newdoc;
    app.doc = newdoc;  // FIXME
    app.guiUpdate();
    updateWorkspacePref();
    return doc != null;
  }

  /*
   * Workspace Management
   */

  public static final FileFilter ffWorkspace = FileDialogUtils.createFileFilter("sve", "SVE Workspaces");

  public File selectWorkspaceFileToOpen() { return FileDialogUtils.selectFile(FileDialogUtils.OPEN, "Open workspace", ffWorkspace); }
  public File selectWorkspaceFileToWrite() { return selectWorkspaceFileToWrite(null); }
  public File selectWorkspaceFileToWrite(File selectedFile) {
    return FileDialogUtils.ensureExtension("sve",
      FileDialogUtils.selectFile(FileDialogUtils.SAVE, "Save workspace", ffWorkspace, selectedFile));
  }

  public void openWorkspace() { openWorkspace(selectWorkspaceFileToOpen()); }
  public void openWorkspace(File file) {
    if ((file == null) || file.equals(workspaceFile)) return;
    try {
      replaceWorkspace(new XMLElement(app, file.getAbsolutePath()));
    } catch (Exception e) {
      app.console.logError("Error reading workspace from " + file.getAbsolutePath() + ": ", e);
      workspaceFile = null;
      return;
    }
    app.console.logInfo("Opened workspace " + file.getAbsolutePath());
    workspaceFile = file;
  }

  public void saveWorkspace() { saveWorkspace(false); }
  public void saveWorkspace(boolean forceSelect) {
    File file = workspaceFile;
    if ((file == null) || forceSelect)
      file = selectWorkspaceFileToWrite(file);
    if (file == null) return;
    try {
      PrintWriter writer = app.createWriter(file.getAbsolutePath());
      writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
      new XMLWriter(writer).write(XMLElement.parse(app.prefs.get("workspace", "<workspace />")), true);
      writer.close();
    } catch (Exception e) {
      app.console.logError("Error saving workspace to " + file.getAbsolutePath() + ": ", e);
      return;
    }
    app.console.logInfo("Workspace saved to " + file.getAbsolutePath());
    workspaceFile = file;
  }

  public void updateWorkspacePref() {
    String workspace = "";
    for (SPaToDocument d : docs)
      if (d.getFile() != null)
        workspace += "<document src=\"" + d.getFile().getAbsolutePath() + "\"" +
          ((d == doc) ? " selected=\"true\"" : "")  + " />";
    workspace = "<workspace>" + workspace + "</workspace>";
    app.prefs.put("workspace", workspace);
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
      SPaToDocument newdoc = new SPaToDocument(app, new File(src));
      docs.add(newdoc);
      app.worker.submit(newdoc.newLoadingTask());
      if (xmlDocument.getBoolean("selected"))
        switchToNetwork(newdoc);
    }
    if ((doc == null) && (docs.size() > 0)) switchToNetwork(docs.get(0));
    updateWorkspacePref();
    app.guiUpdate();
  }

}
