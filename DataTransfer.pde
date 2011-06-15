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

import java.awt.dnd.*;
import java.awt.datatransfer.*;

TWindow winDropStatus = null;

void setupDropTarget() {
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

boolean canAccept(Transferable t) {
  return true;//t.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
}

void showDropStatus(String str, Transferable t) {
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
    java.util.List ff = (java.util.List)t.getTransferData(DataFlavor.javaFileListFlavor);
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

void hideDropStatus() {
  gui.remove(winDropStatus);
}

boolean handleTransferable(Transferable t) {
  // handle dropped files
  if (t.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
    File ff[];
    try {
      ff = (File[])((java.util.List)t.getTransferData(DataFlavor.javaFileListFlavor)).toArray(new File[0]);
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

void handleDroppedFiles(File ff[]) {
  for (File f : ff) {
    if (f.getName().endsWith(".spato"))
      openDocument(f);
    else if (f.getName().endsWith(".mat"))
      ;//FIXME//new Thread(new MatFileImport(f)).start();
    else
      new DataImportWizard(f).start();
  }
}



