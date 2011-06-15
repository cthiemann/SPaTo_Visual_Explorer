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

File[] selectDocumentFilesToOpen() {
  File result[] = selectFiles(OPENMULTIPLE, "Open document", ffDocuments);
  for (int i = 0; i < result.length; i++)            // normalize filename if some
    if (result[i].getName().equals("document.xml"))  // blabla.spato/document.xml
      result[i] = result[i].getParentFile();         // was selected
  return result;
}
File selectDocumentFileToWrite() { return selectDocumentFileToWrite(null); }
File selectDocumentFileToWrite(File selectedFile) {
  return ensureExtension("spato", selectFile(SAVE, "Save document", ffDocuments, selectedFile)); }

void newDocument() {
  SVE2Document newdoc = new SVE2Document();
  docs.add(newdoc);
  switchToNetwork(newdoc);
//  new LinksImportWizard(newdoc).start();
}

void openDocument() { openDocuments(selectDocumentFilesToOpen()); }
void openDocument(File file) { if (file != null) openDocuments(new File[] { file }); }
void openDocuments(File files[]) {
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

void closeDocument() {
  int i = docs.indexOf(doc);
  if (i == -1) return;
  docs.remove(i);
  switchToNetwork((docs.size() > 0) ? docs.get(i % docs.size()) : null);
  //worker.remove(doc);  // cancel any jobs in the worker thread that are related to this document
}

void saveDocument() { saveDocument(false); }
void saveDocument(boolean forceSelect) {
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

boolean switchToNetwork(int i) { return switchToNetwork(((i < 0) || (i >= docs.size())) ? docs.get(i) : null); }
boolean switchToNetwork(String name) {
  SVE2Document newdoc = null;
  for (int i = 0; i < docs.size(); i++)
    if (name.equals(docs.get(i).getName()))
      newdoc = docs.get(i);
  return switchToNetwork(newdoc);
}
boolean switchToNetwork(SVE2Document newdoc) {
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

File selectWorkspaceFileToOpen() { return selectFile(OPEN, "Open workspace", ffWorkspace); }
File selectWorkspaceFileToWrite() { return selectWorkspaceFileToWrite(null); }
File selectWorkspaceFileToWrite(File selectedFile) {
  return ensureExtension("sve", selectFile(SAVE, "Save workspace", ffWorkspace, selectedFile)); }

void openWorkspace() { openWorkspace(selectWorkspaceFileToOpen()); }
void openWorkspace(File file) {
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

void saveWorkspace() { saveWorkspace(false); }
void saveWorkspace(boolean forceSelect) {
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

void updateWorkspacePref() {
  String workspace = "";
  for (SVE2Document d : docs)
    if (d.getFile() != null)
      workspace += "<document src=\"" + d.getFile().getAbsolutePath() + "\"" +
        ((d == doc) ? " selected=\"true\"" : "")  + " />";
  workspace = "<workspace>" + workspace + "</workspace>";
  prefs.put("workspace", workspace);
  showWorkspaceRecoveryButton = false;
}

void replaceWorkspace(XMLElement workspace) {
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
