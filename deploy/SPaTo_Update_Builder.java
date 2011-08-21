/*
 * Copyright 2011 Christian Thiemann <christian@spato.net>
 * Developed at Northwestern University <http://rocs.northwestern.edu>
 *
 * This file is part of the deployment infrastructure for
 * SPaTo Visual Explorer (SPaTo).
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;

import net.spato.sve.app.SPaTo_Visual_Explorer;
import net.spato.sve.app.util.Base64;
import processing.xml.XMLElement;


public class SPaTo_Update_Builder {

  private static String fixPath(String path) {
    return path.replace('/', File.separatorChar).replace('\\', File.separatorChar);
  }

  private static PrivateKey getPrivateKey(String keystore, String alias, String password) throws Exception {
    char pass[] = null;
    // get password
    if ((password != null) && (password.length() > 0) && !password.equals("${private.key.password}"))
      pass = password.toCharArray();
    else {
      JPasswordField pw = new JPasswordField(30);
      int res = JOptionPane.showConfirmDialog(null, pw, "Enter private key password", JOptionPane.OK_CANCEL_OPTION);
      pass = pw.getPassword();
      if ((res != JOptionPane.OK_OPTION) || (pass == null) || (pass.length == 0))
        throw new Exception("No password provided!");
    }
    // get key
    FileInputStream fis = new FileInputStream(keystore);
    KeyStore store = KeyStore.getInstance("JKS");
    store.load(fis, pass);
    fis.close();
    return (PrivateKey)store.getKey(alias, pass);
  }

  private static XMLElement getReleaseInfo(String releaseNotes) throws Exception {
    XMLElement xmlRelease = loadXML(releaseNotes).getChild(0);
    while (xmlRelease.getChildCount() > 0) xmlRelease.removeChild(0);  // purge the actual release notes
    System.out.println(xmlRelease);
    if (!xmlRelease.getString("version").equals(SPaTo_Visual_Explorer.VERSION))
      throw new Exception("Version mismatch: " + xmlRelease.getString("version") + " != " + SPaTo_Visual_Explorer.VERSION);
    if (!xmlRelease.getString("debug").equals(SPaTo_Visual_Explorer.VERSION_DEBUG))
      throw new Exception("Version debug mismatch: " + xmlRelease.getString("debug") + " != " + SPaTo_Visual_Explorer.VERSION_DEBUG);
    if (!xmlRelease.getString("date").equals(SPaTo_Visual_Explorer.VERSION_TIMESTAMP))
      throw new Exception("Version date mismatch: " + xmlRelease.getString("date") + " != " + SPaTo_Visual_Explorer.VERSION_TIMESTAMP);
    return xmlRelease;
  }

  private static XMLElement loadXML(String filename) throws Exception {
    FileReader reader = new FileReader(filename);
    XMLElement xml = XMLElement.parse(reader);
    reader.close();
    if (xml == null) throw new Exception("XMLElement.parse returned null for " + filename);
    return xml;
  }

  private static void saveXML(XMLElement xml, String filename) throws Exception {
    PrintWriter writer = new PrintWriter(filename);
    if (!xml.write(writer)) throw new Exception("XMLElement.write returned false writing to " + filename);
    writer.close();
  }

  public static String serializeDigest(byte digest[]) {
    return String.format("%032x", new java.math.BigInteger(1, digest));
  }

  public static XMLElement serializeSignature(byte signature[]) {
    XMLElement xmlSignature = new XMLElement("signature");
    String sigStr = "";
    for (String line : Base64.encode(signature, 43).split("\r\n"))
      sigStr += "      " + line + "\n";
    xmlSignature.setContent("\n" + sigStr + "    ");
    return xmlSignature;
  }

  public static void processIndex(String filename, String srcdir, String dstdir,
                                  XMLElement xmlRelease, PrivateKey privKey) throws Exception {
    // load index and add release info
    XMLElement xmlIndex = loadXML(filename);
    xmlIndex.insertChild(xmlRelease, 0);
    System.out.println("Processing " + xmlIndex.getString("index") + " (" + xmlIndex.getChildren("file").length + " files)");
    // set up crypto-stuff
    byte buf[] = new byte[8*1024];
    MessageDigest md5 = MessageDigest.getInstance("md5");
    Signature sig = Signature.getInstance("MD5withRSA");
    sig.initSign(privKey);
    // copy files to update directory (dstdir) and add MD5 checksums and signatures to index
    for (XMLElement xmlFile : xmlIndex.getChildren("file")) {
      File fsrc = new File(srcdir, fixPath(xmlFile.getChild("local").getString("path")));
      File fdst = new File(dstdir, fixPath(xmlFile.getChild("remote").getString("path")));
      fdst.getParentFile().mkdirs();  // make sure the destination directory exists
      // feed the file contents through MD5 and RSA while copying...
      FileInputStream fis = new FileInputStream(fsrc);
      FileOutputStream fos = new FileOutputStream(fdst);
      int n, size = 0;
      while ((n = fis.read(buf)) > 0) {
        size += n;
        md5.update(buf, 0, n);
        sig.update(buf, 0, n);
        fos.write(buf, 0, n);
      }
      fis.close(); fos.close();
      // copy last-modified timestamp
      fdst.setLastModified(fsrc.lastModified());
      // add file size, digest and signature to the index
      xmlFile.getChild("remote").setString("size", "" + size);
      xmlFile.getChild("remote").setString("md5", serializeDigest(md5.digest()));
      xmlFile.addChild(serializeSignature(sig.sign()));
    }
    // save index file
    saveXML(xmlIndex, dstdir + File.separator + xmlIndex.getString("index"));
  }

  public static void saveVersionProperties(XMLElement xmlRelease, String filename) throws Exception {
    PrintWriter writer = new PrintWriter(filename);
    String version = xmlRelease.getString("version");
    writer.println("version.update=" + version);
    version = version.split("_")[0];
    writer.println("version.download=" + version);
    writer.close();
  }

  public static void main(String args[]) {
    try {
      // load private key and release info
      PrivateKey privKey = getPrivateKey(fixPath("deploy/keystore"), "spato.update", (args.length > 0) ? args[0] : null);
      XMLElement xmlRelease = getReleaseInfo(fixPath("docs/release-notes/RELEASE_NOTES.xml"));
      // copy files into place and add crypto-stuff to indices
      String dstdir = fixPath("build/update");
      processIndex(fixPath("deploy/INDEX.linux"), fixPath("build/linux/SPaTo Visual Explorer"), dstdir, xmlRelease, privKey);
      processIndex(fixPath("deploy/INDEX.macosx"), fixPath("build/macosx/SPaTo Visual Explorer.app"), dstdir, xmlRelease, privKey);
      processIndex(fixPath("deploy/INDEX.windows"), fixPath("build/windows/SPaTo Visual Explorer"), dstdir, xmlRelease, privKey);
      // output version into a property file for ant to use
      saveVersionProperties(xmlRelease, fixPath("build/version.properties"));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
