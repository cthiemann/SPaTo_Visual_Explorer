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

import java.io.*;
import java.security.KeyStore;
import java.security.Signature;
import java.security.PrivateKey;
import processing.xml.*;

public class SPaTo_Update_Builder {

  public static final String SITE = "/Users/ct/Sites/spato.net/update/";
  public static String VERSION = null;
  public PrivateKey privKey = null;
  public XMLElement xmlReleaseNotes = null;
  private static char password[] = null;

  SPaTo_Update_Builder() throws Exception {
    xmlReleaseNotes = loadXML("release-notes/RELEASE_NOTES.xml");
    if (password == null) {
      javax.swing.JPasswordField pw = new javax.swing.JPasswordField(30);
      javax.swing.JOptionPane.showConfirmDialog(null, pw,
        "Enter private key password", javax.swing.JOptionPane.OK_CANCEL_OPTION);
      password = pw.getPassword();
    }
    privKey = loadPrivateKey("keystore", "spato.update", password);
  }

  public XMLElement loadXML(String filename) throws Exception {
    Reader reader = new FileReader(filename);
    XMLElement xml = XMLElement.parse(reader);
    reader.close();
    if (xml == null) throw new Exception("XMLElement.parse returned null");
    return xml;
  }

  public void saveXML(XMLElement xml, String filename) throws Exception {
    PrintWriter writer = new PrintWriter(filename);
    if (!xml.write(writer)) throw new Exception("XMLElement.write returned false");
    writer.close();
  }

  public PrivateKey loadPrivateKey(String keystore, String alias, char password[]) throws Exception {
    InputStream is = new FileInputStream(keystore);
    KeyStore store = KeyStore.getInstance("JKS");
    store.load(is, password);
    is.close();
    return (PrivateKey)store.getKey(alias, password);
  }

  public static void copyFile(String srcFilename, String dstFilename) throws Exception {
    if (new File(dstFilename).isDirectory()) dstFilename += "/" + new File(srcFilename).getName();
    System.out.println("Copying " + srcFilename + " to " + dstFilename);
    InputStream is = new FileInputStream(srcFilename);
    OutputStream os = new FileOutputStream(dstFilename);
    byte buf[] = new byte[8*1024]; int len;
    while ((len = is.read(buf)) > 0)
      os.write(buf, 0, len);
    is.close(); os.close();
    new File(dstFilename).setLastModified(new File(srcFilename).lastModified());
  }

  public static String commonJars[] = new String[] {
    "SPaTo_Visual_Explorer.jar", "SPaTo_Prelude.class", "SPaTo_Prelude.jar",
    "core.jar", "itext.jar", "jna.jar", "jnmatlib.jar", "pdf.jar", "tGUI.jar", "xhtmlrenderer.jar" };

  public void copyFiles(String platform) throws Exception {
    File dir = new File(SITE + VERSION + "/" + platform);
    //if (dir.exists()) throw new Exception("directory exists: " + dir);
    if (!dir.exists() && !dir.mkdirs()) throw new Exception("could not create directory " + dir);
    //
    if (platform.equals("common")) {
      //
      for (String jar : commonJars)
        copyFile("../application.windows/SPaTo Visual Explorer/lib/" + jar, dir.getAbsolutePath());
      //
    } else if (platform.equals("linux")) {
      //
      copyFile("INDEX.linux", SITE + VERSION + "/INDEX.linux");
      for (String f : new String[] {
          "SPaTo_Visual_Explorer", "SPaTo_Visual_Explorer.desktop",
          "SPaTo_Visual_Explorer.png", "application-x-spato.png",
          "application-x-spato-uncompressed.png", "application-x-spato-workspace.png",
          "spato-mime.xml", "config.sh" })
            copyFile("linux/" + f, dir.getAbsolutePath());
          copyFile("linux/config.sh", dir + "/config.sh.orig");
      //
    } else if (platform.equals("macosx")) {
      //
      copyFile("INDEX.macosx", SITE + VERSION + "/INDEX.macosx");
      for (String f : new String[] {
          "ApplicationUpdateWrapper", "spato.icns",
          "spato-document.icns", "spato-document-uncompressed.icns", "spato-workspace.icns" })
        copyFile("macosx/" + f, dir.getAbsolutePath());
      copyFile("macosx/Info.plist", dir + "/Info.plist.orig");
      //
    } else if (platform.equals("windows")) {
      //
      copyFile("INDEX.windows", SITE + VERSION + "/INDEX.windows");
      for (String f : new String[] { "SPaTo_Prelude.ini", "spato-16x16.png", "splash.gif", "WinRun4J.jar" })
        copyFile("windows/" + f, dir.getAbsolutePath());
      copyFile("windows/SPaTo_Visual_Explorer.ini", dir + "/SPaTo_Visual_Explorer.ini.orig");
      copyFile("windows/SPaTo Visual Explorer.exe", dir + "/_SPaTo_Visual_Explorer.exe");
      copyFile("windows/SPaTo Visual Explorer.ini", dir + "/_SPaTo_Visual_Explorer.ini");
      //
    }
  }

  public void createIndex(String platform) throws Exception {
    System.out.println("Creating INDEX." + platform);
    XMLElement xmlIndex = loadXML(SITE + VERSION + "/INDEX." + platform);
    // update release notes
    XMLElement xmlRelease = xmlIndex.getChild("release");
    if (xmlRelease != null) xmlIndex.removeChild(xmlRelease);
    xmlRelease = xmlReleaseNotes.getChild("release");
    xmlIndex.insertChild(xmlRelease, 0);
    // update files
    for (XMLElement xmlFile : xmlIndex.getChildren("file")) {
      File f = new File(SITE + VERSION, xmlFile.getChild("remote").getString("path"));
      // update size and MD5
      xmlFile.getChild("remote").setString("size", "" + f.length());
      xmlFile.getChild("remote").setString("md5", SPaTo_Visual_Explorer.MD5.digest(f.getAbsolutePath()));
      // update RSA signature
      XMLElement xmlSignature = xmlFile.getChild("signature");
      if (xmlSignature == null) xmlFile.addChild(xmlSignature = new XMLElement("signature"));
      Signature sig = Signature.getInstance("MD5withRSA");
      sig.initSign(privKey);
      InputStream is = new FileInputStream(f);
      byte buf[] = new byte[8*1024]; int len;
      while ((len = is.read(buf)) > 0)
        sig.update(buf, 0, len);
      // pretty-format signature
      String sigStr = "\n";
      for (String line : SPaTo_Visual_Explorer.Base64.encode(sig.sign(), 43).split("\r\n"))
        sigStr += "      " + line + "\n";
      sigStr += "    ";
      xmlSignature.setContent(sigStr);
    }
    // save index
    saveXML(xmlIndex, SITE + VERSION + "/INDEX." + platform);
  }

  public static void main(String args[]) {
    try {
      if (args.length > 0) password = args[0].toCharArray();
      SPaTo_Update_Builder update = new SPaTo_Update_Builder();
      if (!new File(SITE).exists()) throw new Exception("directory does not exist: " + SITE);
      System.out.println("SITE = " + SITE);
      VERSION = update.xmlReleaseNotes.getChild("release").getString("version");
      System.out.println("VERSION = " + VERSION);
      update.copyFiles("common");
      for (String platform : new String[] { "linux", "macosx", "windows" }) {
        update.copyFiles(platform);
        update.createIndex(platform);
      }
      for (String f : new File("release-notes").list())
        copyFile("release-notes/" + f, SITE + "/release-notes");
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}