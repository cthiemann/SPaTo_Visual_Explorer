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

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;


public class SPaTo_Prelude implements Runnable {

  // start time
  protected static final long t0 = System.currentTimeMillis();
  // are we on a Mac? or Windows?
  protected static final boolean isMac = System.getProperty("os.name").startsWith("Mac");
  protected static final boolean isWindows = System.getProperty("os.name").startsWith("Windows");
  // name of log file into which to divert stdout and stderr
  protected static final String logFilename = System.getProperty("spato.logfile");
  // stream to log file
  protected PrintStream out = null;

  // application's root folder
  protected static final String appRootFolder = System.getProperty("spato.app-dir");
  // folder containing the production jars
  protected static final File jarFolder = new File(appRootFolder, isMac ? "Contents/Resources/Java" : "lib");
  // update cache folder
  protected static final File cacheFolder = new File(appRootFolder, isMac ? "Contents/Resources/update" : isWindows ? "update" : System.getProperty("user.home") + "/.spato/update");
  // update cache folder containing the prelude jars
  protected static final File cacheJarFolder = new File(cacheFolder, "common");

  // name of main prelude class (this class)
  protected static final String preludeClass = "SPaTo_Prelude";
  // list of jar files needed to run all tasks
  protected static final String preludeJars[] = { "SPaTo_Prelude.jar", "core.jar", "jna.jar" };
  // list of tasks to run from preludeJars (class names)
  protected static final String taskNames[] = isWindows
    ? new String[] { "UpdateInstaller", "WindowsPrelude" }
    : new String[] { "UpdateInstaller" };
    // ? new String[] { "UpdateInstaller", "MaxMemDetector", "WindowsPrelude" }
    // : new String[] { "UpdateInstaller", "MaxMemDetector" };


  protected void setup() {
    // divert stdout and stderr into log file, if requested
    if (logFilename != null) try {
      out = new PrintStream(new FileOutputStream(logFilename), true);
      System.setOut(out);
      System.setErr(out);
    } catch (Exception e) {
      e.printStackTrace();
      System.err.println("failed to redirect output to " + logFilename);
    }
  }

  protected void processCommandLine(String args[]) {
    for (int i = 0; i < args.length - 1; i++) {
      if (args[i].equals("sleep")) {  // sleep for some time
        try {
          int secs = Integer.parseInt(args[i+1].trim());
          System.out.println("going to sleep for some " + secs + " seconds");
          Thread.sleep(1000*secs);
        } catch (Exception e) {}
      }
    }
  }

  protected boolean doTheMunchhausenTrick() {
    File update = new File(cacheJarFolder, preludeClass + ".class");
    if (update.exists()) {
      // ...and I escaped the swamp by pulling myself up by my own hair...
      System.out.println("+++ SPaTo Prelude: loading update for " + preludeClass + ".class");
      try {
        ClassLoader cl = new URLClassLoader(new URL[] { update.toURI().toURL() });
        ((Runnable)cl.loadClass(preludeClass).newInstance()).run();
        return true;
      } catch (Exception e) { e.printStackTrace(); return false; }
    } else
      return false;  // we are up-to-date and can run the tasks ourselves
  }

  protected void copyFile(File src, File dst) throws Exception {
    FileInputStream fis = new FileInputStream(src);
    FileOutputStream fos = new FileOutputStream(dst);
    byte buf[] = new byte[8*1024];
    int len = 0;
    while ((len = fis.read(buf)) > 0)
      fos.write(buf, 0, len);
    fos.close();
    fis.close();
  }

  public void run() {
    try {
      ClassLoader cl = null;
      // setup temp folder for prelude code
      File tmp = File.createTempFile("spato", "");
      if (!tmp.delete()) throw new RuntimeException("could not delete " + tmp);
      if (!tmp.mkdir()) throw new RuntimeException("could not create directory " + tmp);
      tmp.deleteOnExit();
      System.setProperty("spato.tmp-dir", tmp.getAbsolutePath());  // some tasks might make use of this
      // load jars
      boolean hasUpdate = new File(cacheFolder, "INDEX").exists();
      URL urls[] = new URL[preludeJars.length];
      for (int i = 0; i < preludeJars.length; i++) {
        File oldJar = new File(jarFolder, preludeJars[i]);
        File newJar = new File(cacheJarFolder, preludeJars[i]);
        File tmpJar = new File(tmp, preludeJars[i]);
        System.out.println("+++ SPaTo Prelude: loading " + preludeJars[i] + (hasUpdate && newJar.exists() ? " (updated)" : ""));
        copyFile(hasUpdate && newJar.exists() ? newJar : oldJar, tmpJar);
        urls[i] = tmpJar.toURI().toURL();
        tmpJar.deleteOnExit();
      }
      cl = new URLClassLoader(urls);
      // run tasks
      for (int i = 0; i < taskNames.length; i++) {
        System.out.println("========== " + taskNames[i] + " ==========");
        try { ((Runnable)cl.loadClass(taskNames[i]).newInstance()).run(); }
        catch (Exception e) { e.printStackTrace(); System.err.println("!!! Failed to run " + taskNames[i]); }
      }
    } catch (Exception e) { e.printStackTrace(); }
  }

  protected void waveGoodbye() {
    long t1 = System.currentTimeMillis();
    System.out.println("========== End of Prelude (" + (t1 - t0) + " ms) ==========");
    try { out.close(); } catch (Exception e) {}
  }

  public static void main(String args[]) {
    SPaTo_Prelude prelude = new SPaTo_Prelude();
    prelude.setup();
    prelude.processCommandLine(args);
    if (!prelude.doTheMunchhausenTrick())
      prelude.run();
    prelude.waveGoodbye();
  }

}
