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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;


public class SPaTo_Prelude implements Runnable {

  public static final String VERSION = "20110823T180000";

  private static final boolean isMac = System.getProperty("os.name").startsWith("Mac");
  private static final boolean isWin = System.getProperty("os.name").startsWith("Windows");
  private static final boolean isLin = !isMac && !isWin;  // quartum non datur

  private static final File jarFolder =
    new File(System.getProperty("spato.app-dir"), isMac ? "Contents/Resources/Java" : "lib");

  private static final File updateCacheFolder =
    isLin ? new File(System.getProperty("user.home"), ".spato/update")
          : new File(System.getProperty("spato.app-dir"), isMac ? "Contents/Resources/update" : "update");

  public static String args[] = new String[0];  // command line arguments

  private static void printOut(String msg) { System.out.println("+++ SPaTo Prelude: " + msg); }
  private static void printErr(String msg) { System.err.println("!!! SPaTo Prelude: " + msg); }

  private static void copy(File src, File dst) throws IOException {
    FileInputStream fis = new FileInputStream(src);
    FileOutputStream fos = new FileOutputStream(dst);
    byte buf[] = new byte[8*1024]; int len = 0;
    while ((len = fis.read(buf)) > 0)
      fos.write(buf, 0, len);
    fos.close(); fis.close();
  }

  /*
   * Bootstrapping mechanism (set up log file and check for updated SPaTo_Prelude.class)
   */

  private static OutputStream initLog() {
    OutputStream os = null;
    if (System.getProperty("spato.logfile") != null) {
      try {
        os = new FileOutputStream(System.getProperty("spato.logfile"));
        PrintStream ps = new PrintStream(os, true);
        System.setOut(ps); System.setErr(ps);
      } catch (IOException e) {
        printErr("failed to redirect output to " + System.getProperty("spato.logfile"));
      }
    }
    return os;
  }

  private static void bootstrap() {
    printOut("version " + VERSION + " [bootstrap]");
    File update = new File(updateCacheFolder, "common" + File.separator + "SPaTo_Prelude.class");
    if (update.exists() && new File(updateCacheFolder, "INDEX").exists()) {
      printOut("loading updated SPaTo_Prelude");
      Runnable prelude = null;
      // load updated class
      try {
        URL urls[] = new URL[] { new File(updateCacheFolder, "common").toURI().toURL() };
        ClassLoader cl = new URLClassLoader(urls, null);
        prelude = (Runnable)cl.loadClass("SPaTo_Prelude").newInstance();
      } catch (Exception e) {
        printErr("could not instantiate updated SPaTo_Prelude");
        printErr(e.getClass().getName() + ": " + e.getMessage());
        javax.swing.JOptionPane.showMessageDialog(null,
          "<html>Updating SPaTo Visual Explorer has failed horribly.<br><br>" +
          "Please delete the application and manually download<br>" +
          "the lastest version from http://www.spato.net/",
          "SPaTo Updater", javax.swing.JOptionPane.ERROR_MESSAGE);
        return;
      }
      // try to hand down command line arguments
      try {
        prelude.getClass().getField("args").set(prelude, args);
      } catch (Exception e) {
        printErr("could not pass command line arguments to updated SPaTo_Prelude");
      }
      // run updated class
      prelude.run();
    } else {
      // run an instance of this class
      new SPaTo_Prelude().run();
    }
  }

  public static void main(String args[]) {
    SPaTo_Prelude.args = args;
    OutputStream out = initLog();
    bootstrap();
    try { out.close(); } catch (Exception e) {}
  }

  /*
   * Main prelude stuff (can be overridden by an updated SPaTo_Prelude.class)
   */

  private static final String preludeJars[] = { "SPaTo_Prelude.jar", "core.jar" };

  private ClassLoader createClassLoader() throws Exception {
    boolean hasUpdate = new File(updateCacheFolder, "INDEX").exists();
    // create temporary directory if needed
    File tmpDir = null;
    if (hasUpdate) {
      tmpDir = File.createTempFile("spato", "");
      if (!tmpDir.delete()) throw new IOException("could not delete " + tmpDir);
      if (!tmpDir.mkdir()) throw new IOException("could not create directory " + tmpDir);
      tmpDir.deleteOnExit();
    }
    // collect jar files (copy updates into tmpdir)
    URL urls[] = new URL[preludeJars.length];
    for (int i = 0; i < preludeJars.length; i++) {
      File oldJar = new File(jarFolder, preludeJars[i]);
      File newJar = new File(updateCacheFolder, "common" + File.separator + preludeJars[i]);
      File tmpJar = new File(tmpDir, preludeJars[i]);
      if (hasUpdate && newJar.exists()) {
        copy(newJar, tmpJar);
        tmpJar.deleteOnExit();
        printOut("using updated " + preludeJars[i]);
        urls[i] = tmpJar.toURI().toURL();
      } else
        urls[i] = oldJar.toURI().toURL();
    }
    // finished
    return new URLClassLoader(urls);
  }

  private void run(ClassLoader cl, String className) throws Exception {
    Runnable task = (Runnable)cl.loadClass("net.spato.sve.prelude." + className).newInstance();
    try { task.getClass().getField("args").set(task, args); } catch (Exception e) {}
    task.run();
  }

  public void run() {
    printOut("version " + VERSION + " [run]");
    try {
      ClassLoader cl = createClassLoader();
      run(cl, "UpdateInstaller");
      run(cl, "ApplicationLauncher");
    } catch (Exception e) {
      printErr("something went wrong");
      e.printStackTrace();
    }
  }

}
