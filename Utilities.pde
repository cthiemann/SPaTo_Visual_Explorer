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

import java.awt.FileDialog;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.SwingUtilities;
import java.security.MessageDigest;
import java.security.DigestInputStream;


Date parseISO8601(String timestamp) {
  try { return new SimpleDateFormat("yyyyMMdd'T'HHmmss").parse(timestamp); }
  catch (Exception e) { e.printStackTrace(); return null; }
}

static class MD5 {
  static String digest(String file) {
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
  
  static String encode(byte data[]) { return encode(data, 76); }
  static String encode(byte data[], int wrapColumn) {
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
  
  static byte[] decode(String data) {
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

static FileFilter createFileFilter(final String ext, final String desc) {
  return createFileFilter(new String[] { ext }, desc); }
static FileFilter createFileFilter(final String exts[], final String desc) {
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

static File ensureExtension(String ext, File file) {
  if ((ext == null) || (file == null)) return null;
  return file.getName().endsWith("." + ext) ? file : new File(file.getAbsolutePath() + "." + ext);
}

static final int OPEN = 0;
static final int OPENMULTIPLE = 1;
static final int SAVE = 2;

File selectFile(int mode) { return selectFile(mode, null, null, null); }
File selectFile(int mode, String title) { return selectFile(mode, title, null, null); }
File selectFile(int mode, String title, FileFilter ff) { return selectFile(mode, title, ff, null); }
File selectFile(int mode, String title, FileFilter ff, File selectedFile) {
  File res[] = selectFiles(mode, title, ff, selectedFile);
  return ((res != null) && (res.length > 0)) ? res[0] : null;
}

File selectFilesResult[];
File[] selectFiles(int mode) { return selectFiles(mode, null, null, null); }
File[] selectFiles(int mode, String title) { return selectFiles(mode, title, null, null); }
File[] selectFiles(int mode, String title, FileFilter ff) { return selectFiles(mode, title, ff, null); }
File[] selectFiles(final int mode, final String title, final FileFilter ff, final File selectedFile) {
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
