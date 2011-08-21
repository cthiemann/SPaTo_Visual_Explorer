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

package net.spato.sve.app.util;

import java.awt.FileDialog;
import java.io.File;
import java.io.FilenameFilter;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import processing.core.PApplet;
import net.spato.sve.app.SPaTo_Visual_Explorer;


public class FileDialogUtils {

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
  public static class FilenameFileFilterAdapter implements FilenameFilter {
    private FileFilter ff = null;
    public FilenameFileFilterAdapter(FileFilter ff) { this.ff = ff; }
    public boolean accept(File dir, String name) { return ff.accept(new File(dir, name)); }
  }

  // And also nice: JFileChooser on Windows does not display directories at all if they are rejected  by
  // the FileFilter, which means you cannot change into them, which means you most probably cannot reach
  // any of the files of interest.  So, this class accepts all directories before asking the real filter.
  // The selectFiles method will sort out selected, non-acceptable directories afterwards.
  public static class WindowsFileFilterAdapter extends FileFilter {
    private FileFilter ff = null;
    public WindowsFileFilterAdapter(FileFilter ff) { this.ff = ff; }
    public String getDescription() { return ff.getDescription(); }
    public boolean accept(File f) { return f.isDirectory() ? true : ff.accept(f); }
  }

  public static File ensureExtension(String ext, File file) {
    if ((ext == null) || (file == null)) return null;
    return file.getName().endsWith("." + ext) ? file : new File(file.getAbsolutePath() + "." + ext);
  }

  public static final int OPEN = 0;
  public static final int OPENMULTIPLE = 1;
  public static final int SAVE = 2;

  public static File selectFile(SPaTo_Visual_Explorer app, int mode) {
    return selectFile(app, mode, null, null, null);
  }
  public static File selectFile(SPaTo_Visual_Explorer app, int mode, String title) {
    return selectFile(app, mode, title, null, null);
  }
  public static File selectFile(SPaTo_Visual_Explorer app, int mode, String title, FileFilter ff) {
    return selectFile(app, mode, title, ff, null);
  }
  public static File selectFile(SPaTo_Visual_Explorer app, int mode, String title, FileFilter ff, File selectedFile) {
    File res[] = selectFiles(app, mode, title, ff, selectedFile);
    return ((res != null) && (res.length > 0)) ? res[0] : null;
  }

  private static File selectFilesResult[];  // FIXME: there must be a better way than using this static variable

  public static File[] selectFiles(SPaTo_Visual_Explorer app, int mode) {
    return selectFiles(app, mode, null, null, null);
  }
  public static File[] selectFiles(SPaTo_Visual_Explorer app, int mode, String title) {
    return selectFiles(app, mode, title, null, null);
  }
  public static File[] selectFiles(SPaTo_Visual_Explorer app, int mode, String title, FileFilter ff) {
    return selectFiles(app, mode, title, ff, null);
  }
  public static File[] selectFiles(final SPaTo_Visual_Explorer app, final int mode, final String title,
                                   final FileFilter ff, final File selectedFile) {
    try {
      selectFilesResult = null;
      SwingUtilities.invokeAndWait(new Runnable() {
        public void run() {
          File lastDir = new File(app.prefs.get("workspace.lastDirectory", ""));
          if (lastDir.getAbsolutePath().equals("")) lastDir = null;
          if (app.platform == PApplet.MACOSX) {
            // use FileDialog instead of JFileChooser as per Apple recommendation
            FileDialog fd = new FileDialog(app.getParentFrame(),
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
            if (ff != null)
              fc.setFileFilter((app.platform == PApplet.WINDOWS) ? new WindowsFileFilterAdapter(ff) : ff);
            // run dialog
            if (fc.showDialog(app.getParentFrame(), (mode == SAVE) ? "Save" : "Open") == JFileChooser.APPROVE_OPTION) {
              // save current directory to preferences
              File dir = fc.getCurrentDirectory();
              app.prefs.put("workspace.lastDirectory", (dir != null) ? dir.getAbsolutePath() : null);
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
                  selectFilesResult = (File[])PApplet.append(selectFilesResult, files[i]);
            }
          }
        }
      });
      return selectFilesResult;
    } catch (Exception e) {
      app.console.logError("Something went wrong: ", e);
      e.printStackTrace();
      return null;
    }
  }

}
