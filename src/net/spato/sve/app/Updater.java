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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

import net.spato.sve.app.util.*;
import org.xhtmlrenderer.simple.FSScrollPane;
import org.xhtmlrenderer.simple.XHTMLPanel;
import processing.core.PApplet;
import processing.xml.StdXMLBuilder;
import processing.xml.StdXMLParser;
import processing.xml.StdXMLReader;
import processing.xml.XMLElement;
import processing.xml.XMLException;
import processing.xml.XMLValidator;


public class Updater extends Thread {

  protected SPaTo_Visual_Explorer app = null;

  boolean force = false;
  String updateURL = System.getProperty("spato.update.url", "http://update.spato.net/latest/");
  String releaseNotesURL = System.getProperty("spato.release-notes.url", "http://update.spato.net/release-notes/");
  String indexName = null;
  String appRootFolder = null;
  String cacheFolder = null;
  XMLElement index = null;
  String updateVersion = null;
  // public key for file verification (base64-encoded)
  String pubKey64 =
    "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCrWkHeVPecXQeOd2" +
    "C3K4UUzgBqXYJwfGNKZnLp17wy/45nH7/llxBKR7eioJPdYCauxQ8M" +
    "nuArSltlIV9AnBKxb8h28xoBsEx1ek04jvJEtd93Bw7ILa3eF4MDGl" +
    "ZxwPnmTaTICIVUXtiZveOHDl1dQBKvinyU8fe3Xi7+j9klnwIDAQAB";

  public Updater(SPaTo_Visual_Explorer app, boolean force) {
    setPriority(Thread.MIN_PRIORITY);
    this.app = app;
    this.force = force;
  }

  public void printOut(String msg) { System.out.println("+++ SPaTo Updater: " + msg); }
  public void printErr(String msg) { System.err.println("!!! SPaTo Updater: " + msg); }

  public void setupEnvironment() {
    printOut("updateURL = " + updateURL);
    // determine which INDEX file to download
    switch (PApplet.platform) {
      case PApplet.LINUX:   indexName = "INDEX.linux"; break;
      case PApplet.MACOSX:  indexName = "INDEX.macosx"; break;
      case PApplet.WINDOWS: indexName = "INDEX.windows"; break;
      default:              throw new RuntimeException("unsupported platform");
    }
    printOut("indexName = " + indexName);
    // check application root folder
    appRootFolder = System.getProperty("spato.app-dir");
    if ((appRootFolder == null) || !new File(appRootFolder).exists())
      throw new RuntimeException("invalid application root folder: " + appRootFolder);
    if (!appRootFolder.endsWith(File.separator)) appRootFolder += File.separator;
    printOut("appRootFolder = " + appRootFolder);
    // check update cache folder
    switch (PApplet.platform) {
      case PApplet.LINUX:   cacheFolder = System.getProperty("user.home") + "/.spato/update"; break;
      case PApplet.MACOSX:  cacheFolder = appRootFolder + "Contents/Resources/update"; break;
      default:              cacheFolder = appRootFolder + "update"; break;
    }
    if ((cacheFolder == null) || !new File(cacheFolder).exists() && !new File(cacheFolder).mkdirs())
      throw new RuntimeException("could not create cache folder: " + cacheFolder);
    if (!cacheFolder.endsWith(File.separator)) cacheFolder += File.separator;
    printOut("cacheFolder = " + cacheFolder);
  }

  public boolean checkAndFetch() {  // returns true if update is available
    int count = 0, totalSize = 0;
    BufferedReader reader = null;
    // fetch the index
    try {
      // reader creation copied from PApplet so we can catch exceptions
      InputStream is = new URL(updateURL + indexName).openStream();
      reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
      // XML parsing copied from XMLElement so we can catch exceptions
      index = new XMLElement();
      StdXMLParser parser = new StdXMLParser();
      parser.setBuilder(new StdXMLBuilder(index));
      parser.setValidator(new XMLValidator());
      parser.setReader(new StdXMLReader(reader));
      parser.parse();
    } catch (XMLException xmle) {
      index = null;
      throw new RuntimeException("Not a valid XML file: " + updateURL + indexName + "<br>" +
        "Are you properly connected to the interwebs?");
    } catch (Exception e) {  // FIXME: react to specific exceptions
      index = null;
      throw new RuntimeException("could not download " + indexName, e);
    } finally {
      try { reader.close(); } catch (Exception e) { }
    }
    // check whether the user wants to ignore this update
    try { updateVersion = index.getChild("release").getString("version"); } catch (Exception e) {}
    printOut("INDEX is for version " + updateVersion);
    if ((updateVersion != null) && updateVersion.equals(app.prefs.get("update.skip", null))) {
      printOut("user requested to skip this version");
      return false;
    } else
      app.prefs.remove("update.skip");
    // delete possibly existing locally cached index
    new File(cacheFolder + "INDEX").delete();
    // setup signature verification
    Signature sig = null;
    try {
      sig = Signature.getInstance("MD5withRSA");
      X509EncodedKeySpec spec = new X509EncodedKeySpec(Base64.decode(pubKey64));
      sig.initVerify(KeyFactory.getInstance("RSA").generatePublic(spec));
    } catch (Exception e) { throw new RuntimeException("failed to setup signature verification", e); }
    // iterate over all file records
    for (XMLElement file : index.getChildren("file")) {
      XMLElement remote = file.getChild("remote"), local = file.getChild("local");
      // check if all information is present
      if ((remote == null) || (remote.getString("path") == null) || (remote.getString("md5") == null) ||
          (local == null) || (local.getString("path") == null))
        throw new RuntimeException("malformed file record: " + file);
      // check for signature and decode
      byte signature[] = null;
      if ((file.getChild("signature") == null) || (file.getChild("signature").getContent() == null))
        throw new RuntimeException("missing file signature: " + file);
      try { signature = Base64.decode(file.getChild("signature").getContent()); }
      catch (Exception e) { throw new RuntimeException("error decoding signature: " + file, e); }
      // download update file if necessary
      local.setString("md5", "" + MD5.digest(appRootFolder + local.getString("path")));  // "" forces "null" if md5 returns null
      if (!remote.getString("md5").equals(local.getString("md5"))) {
        count++;  // count number of outdated files
        String cacheFilename = cacheFolder + remote.getString("path").replace('/', File.separatorChar);
        if (remote.getString("md5").equals(MD5.digest(cacheFilename)))
          printOut(remote.getString("path") + " is outdated, but update is already cached");
        else {
          printOut(remote.getString("path") + " is outdated, downloading update (" + remote.getInt("size") + " bytes)");
          byte buf[] = new byte[remote.getInt("size", 0)]; int len = 0;
          InputStream is = null;
          try {
            is = new URL(updateURL + remote.getString("path")).openStream();
            while (len < buf.length)
              len += is.read(buf, len, buf.length - len);
          } catch (Exception e) {
            printErr("download failed"); e.printStackTrace(); return false;
          } finally {
            try { is.close(); } catch (Exception e) {}
          }
          try { sig.update(buf); if (!sig.verify(signature)) throw new Exception("signature verification failure"); }
          catch (Exception e) { printErr("failed to verify file"); e.printStackTrace(); return false; }
          app.saveBytes(cacheFilename, buf);
          totalSize += remote.getInt("size");  // keep track of total download volume
          if (!remote.getString("md5").equals(MD5.digest(cacheFilename)))
            throw new RuntimeException("md5 mismatch: " + file);
        }
      }
    }
    // clean up and return
    if (count > 0) {
      printOut("updates available for " + count + " files, downloaded " + totalSize + " bytes");
      return true;
    } else {
      printOut("no updates available");
      new File(cacheFolder).delete();
      return false;
    }
  }

  public String[] getRestartCmd() {
    switch (PApplet.platform) {
      case PApplet.LINUX:
        return new String[] { System.getProperty("spato.exec"), "--restart" };
      case PApplet.MACOSX:
        return new String[] { appRootFolder + "Contents/Resources/restart.sh" };
      case PApplet.WINDOWS:
        return new String[] { appRootFolder + "lib\\restart.bat", appRootFolder,
                              ">", appRootFolder + "lib\\restart_to_update.log", "2>&1" };
      default:
        return null;
    }
  }

  final static int NOTHING = -1, IGNORE = 0, INSTALL = 1, RESTART = 2;

  public int showReleaseNotesDialog(boolean canRestart) {
    // construct URL request
    String url = releaseNotesURL + "?version=" + app.VERSION + "&index=" + indexName;
    // setup HTML renderer for release notes
    XHTMLPanel htmlView = new XHTMLPanel();
    try { htmlView.setDocument(url); }
    catch (Exception e) { throw new RuntimeException("could not fetch release notes from " + url, e); }
    JScrollPane scrollPane = new FSScrollPane(htmlView);
    scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
    scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    // compose everything in a panel
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.add(new JLabel("An update is available and can be applied the next time you start SPaTo Visual Explorer."), BorderLayout.NORTH);
    panel.add(scrollPane, BorderLayout.CENTER);
    panel.add(new JLabel("<html>You are currently running version <b>" + app.VERSION + "</b> (" + app.VERSION_DATE + ").</html>"), BorderLayout.SOUTH);
    panel.setPreferredSize(new Dimension(600, 400));
    panel.setMinimumSize(new Dimension(300, 200));
    // add the auto-check checkbox
    JCheckBox cbAutoUpdate = new JCheckBox("Automatically check for updates in the future",
      app.prefs.getBoolean("update.check", true));
    JPanel panel2 = new JPanel(new BorderLayout(0, 20));
    panel2.add(panel, BorderLayout.CENTER);
    panel2.add(cbAutoUpdate, BorderLayout.SOUTH);
    // setup the options
    Object options[] = canRestart
      ? new Object[] { "Restart now", "Restart later", "Skip this update" }
      : new Object[] { "Awesome!", "Skip this update" };
    // show the dialog
    int result = JOptionPane.showOptionDialog(app.frame, panel2, "Good news, everyone!",
      JOptionPane.INFORMATION_MESSAGE, JOptionPane.YES_NO_CANCEL_OPTION, null,
      options, options[0]);
    // save the auto-check selection
    app.prefs.putBoolean("update.check", cbAutoUpdate.isSelected());
    // return the proper action constant
    if (result == (canRestart ? 2 : 1)) return IGNORE;  // skip this update
    if (result == (canRestart ? 1 : 0)) return INSTALL;  // install on next application launch
    if (result == 0 && canRestart) return RESTART;  // install now
    return NOTHING;  // this will cause to do nothing (no kidding!)
  }

  public void askAndAct() {
    while (app.fireworks) try { Thread.sleep(5000); } catch (Exception e) {}
    String cmd[] = getRestartCmd();
    int action = showReleaseNotesDialog(cmd != null);
    // check if the user wants to ignore this update
    if (action == IGNORE)
      app.prefs.put("update.skip", updateVersion);
    // save the INDEX into the update cache folder to indicate that the update should be installed
    if ((action == INSTALL) || (action == RESTART))
      index.write(app.createWriter(cacheFolder + "INDEX"));
    // restart application if requested
    if (action == RESTART) try {
      new ProcessBuilder(cmd).start();
      app.exit();  // FIXME: unsaved documents?
    } catch (Exception e) {  // catch this one here to give a slightly more optimistic error message
      printErr("could not restart application"); e.printStackTrace();
      JOptionPane.showMessageDialog(app.frame,
        "<html>The restart application could not be lauched:<br><br>" +
        PApplet.join(cmd, " ") + "<br>" + e.getClass().getName() + ": " + e.getMessage() + "<br><br>" +
        "However, the update should install automatically when you manually restart the application.</html>",
        "Slightly disappointing news",
        JOptionPane.ERROR_MESSAGE);
    }
  }

  public void run() {
    try {
      setupEnvironment();
      if (checkAndFetch())
        askAndAct();
      else if (force)
        JOptionPane.showMessageDialog(app.frame,
          "No updates available", "Update", JOptionPane.INFORMATION_MESSAGE);
    } catch (Exception e) {
      printErr("Something's wrong. Stack trace follows..."); e.printStackTrace();
      // prepare error dialog
      JPanel panel = new JPanel(new BorderLayout(0, 20));
      String str = "<html>Something went wrong while checking for updates.<br><br>" +
        e.getMessage().substring(0, 1).toUpperCase() + e.getMessage().substring(1);
      if (e.getCause() != null)
        str += "\ndue to " + e.getCause().getClass().getName() + ": " + e.getCause().getMessage();
      str += "</html>";
      panel.add(new JLabel(str), BorderLayout.CENTER);
      JCheckBox cbAutoUpdate = new JCheckBox("Automatically check for updates in the future",
        app.prefs.getBoolean("update.check", true));
      panel.add(cbAutoUpdate, BorderLayout.SOUTH);
      // show dialog
      JOptionPane.showMessageDialog(app.frame, panel, "Bollocks!", JOptionPane.ERROR_MESSAGE);
      // save the auto-check selection
      app.prefs.putBoolean("update.check", cbAutoUpdate.isSelected());
    }
  }

}
