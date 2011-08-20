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

import processing.core.PApplet;
import processing.xml.StdXMLBuilder;
import processing.xml.StdXMLParser;
import processing.xml.StdXMLReader;
import processing.xml.XMLElement;
import processing.xml.XMLValidator;


public class UpdateInstaller implements Runnable {

  // the application's root folder
  protected String appRootFolder = System.getProperty("spato.app-dir");
  // the update cache folder and index
  protected File cacheFolder = null;
  protected XMLElement index = null;

  protected void checkForIndex() {
    cacheFolder = (PApplet.platform == PApplet.MACOSX) ? new File(appRootFolder, "Contents/Resources/update")
                : (PApplet.platform == PApplet.WINDOWS) ? new File(appRootFolder, "update")
                : new File(System.getProperty("user.home") + "/.spato/update");
    File indexFile = new File(cacheFolder, "INDEX");
    BufferedReader reader = null;
    if (indexFile.exists()) try {
      System.out.println("parsing index from " + indexFile);
      reader = new BufferedReader(new FileReader(indexFile));
      StdXMLParser parser = new StdXMLParser();    // XML parsing code copied
      parser.setBuilder(new StdXMLBuilder());      // from XMLElement.parse()
      parser.setValidator(new XMLValidator());     // so that we can correctly
      parser.setReader(new StdXMLReader(reader));  // handle exceptions here
      index = (XMLElement)parser.parse();          // instead of ignoring them.
    } catch (Exception e) {
      throw new RuntimeException("error reading the INDEX from " + indexFile, e);
    } finally {
      try { reader.close(); } catch (Exception e) {}
    }
  }

  protected void moveUpdatesIntoPlace() {
    for (XMLElement file : index.getChildren("file")) {
      XMLElement remote = file.getChild("remote"), local = file.getChild("local");
      // check if all information is present
      if ((remote == null) || (remote.getString("path") == null) || (remote.getString("md5") == null) ||
          (local == null) || (local.getString("path") == null))
        throw new RuntimeException("malformed file record:\n" + file);
      File src = new File(cacheFolder, remote.getString("path").replace('/', File.separatorChar));
      File dst = new File(appRootFolder, local.getString("path"));
      // move file into place
      if (!src.exists()) continue;  // not part of this update...
      System.out.println("moving " + remote.getString("path") + " to " + local.getString("path"));
      if (dst.exists()) dst.delete();
      if (!src.renameTo(dst))
        throw new RuntimeException("moving " + src + " to " + dst + " failed");
      if (file.getBoolean("executable") && (PApplet.platform != PApplet.WINDOWS)) {
        System.out.println("setting executable flag on " + local.getString("path"));
        try { Runtime.getRuntime().exec(new String[] { "chmod", "+x", dst.getAbsolutePath() }); }
        catch (Exception e) { throw new RuntimeException("chmod failed", e); }
      }
    }
  }

  protected void deleteRecursively(File f) {
    if (f.isDirectory())
      for (File ff : f.listFiles())
        if (!ff.getName().equals(".") && !ff.getName().equals(".."))
          deleteRecursively(ff);
    if (!f.delete())
      System.err.println("Warning: could not delete " + f);
  }

  public void run() {
    try {
      checkForIndex();
      if (index == null)
        System.out.println("Software is up-to-date (no INDEX in " + cacheFolder + ")");
      else {
        moveUpdatesIntoPlace();
        deleteRecursively(cacheFolder);
      }
    } catch (Exception e) { e.printStackTrace(); }
  }

}
