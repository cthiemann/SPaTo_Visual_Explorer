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

package net.spato.sve.prelude;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;

import processing.core.PApplet;
import processing.xml.StdXMLBuilder;
import processing.xml.StdXMLParser;
import processing.xml.StdXMLReader;
import processing.xml.XMLElement;
import processing.xml.XMLValidator;


public class UpdateInstaller implements Runnable {

  private static final boolean isMac = System.getProperty("os.name").startsWith("Mac");
  private static final boolean isWin = System.getProperty("os.name").startsWith("Windows");
  private static final boolean isLin = !isMac && !isWin;  // quartum non datur

  private static final File updateCacheFolder =
    isLin ? new File(System.getProperty("user.home"), ".spato/update")
          : new File(System.getProperty("spato.app-dir"), isMac ? "Contents/Resources/update" : "update");

  private static void printOut(String msg) { System.out.println("+++ SPaTo Update Installer: " + msg); }
  private static void printErr(String msg) { System.err.println("!!! SPaTo Update Installer: " + msg); }

  private static void rmdir(File f) {
    if (f.getName().equals(".") || f.getName().equals("..")) return;  // don't delete funny directories
    if (f.isDirectory()) for (File ff : f.listFiles()) rmdir(ff);  // recursively empty directory
    f.delete();  // delete file or directory
  }

  private static void setExecutable(File f) throws Exception {
    try {
      Runtime.getRuntime().exec(new String[] { "chmod", "+x", f.getAbsolutePath() });
    } catch (Exception e) {
      throw new Exception("chmod on " + f + " failed", e);
    }
  }

  private XMLElement loadIndex(File indexFile) throws Exception {
    printOut("loading " + indexFile);
    BufferedReader reader = null;
    XMLElement index = null;
    try {
      reader = new BufferedReader(new FileReader(indexFile));
      StdXMLParser parser = new StdXMLParser();    // XML parsing code copied
      parser.setBuilder(new StdXMLBuilder());      // from XMLElement.parse()
      parser.setValidator(new XMLValidator());     // so that we can correctly
      parser.setReader(new StdXMLReader(reader));  // handle exceptions here
      index = (XMLElement)parser.parse();          // instead of ignoring them.
    } catch (Exception e) {
      throw new Exception("error reading the INDEX from " + indexFile, e);
    } finally {
      try { reader.close(); } catch (Exception e) {}
    }
    printOut("found INDEX for version " + index.getChild("release").getString("version"));
    return index;
  }

  protected void moveUpdatesIntoPlace(XMLElement index) throws Exception {
    for (XMLElement file : index.getChildren("file")) {
      XMLElement remote = file.getChild("remote"), local = file.getChild("local");
      // check if all information is present
      if ((remote == null) || (remote.getString("path") == null) ||
          (local == null) || (local.getString("path") == null))
        throw new Exception("malformed file record:\n" + file);
      // move file into place, if an update for it exists
      File src = new File(updateCacheFolder, remote.getString("path").replace('/', File.separatorChar));
      File dst = new File(System.getProperty("spato.app-dir"), local.getString("path"));
      if (src.exists()) {
        printOut("installing updated " + local.getString("path"));
        if (dst.exists())
          dst.delete();
        if (!src.renameTo(dst))
          throw new Exception("renaming " + src + " to " + dst + " failed");
        if (file.getBoolean("executable") && !isWin)
          setExecutable(dst);
      }
    }
    // touch the application directory (Mac OS X caches the Info.plist and needs to be poked to see changes to it)
    new File(System.getProperty("spato.app-dir")).setLastModified(new Date().getTime());
  }

  public void run() {
    File indexFile = new File(updateCacheFolder, "INDEX");
    if (indexFile.exists()) try {
      moveUpdatesIntoPlace(loadIndex(indexFile));
    } catch (Exception e) {
      printErr("something went wrong");
      e.printStackTrace();
    }
    rmdir(updateCacheFolder);
  }

}
