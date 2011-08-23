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

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;


public class ApplicationLauncher implements Runnable {

  private static final boolean isMac = System.getProperty("os.name").startsWith("Mac");
  private static final boolean isWin = System.getProperty("os.name").startsWith("Windows");
  private static final boolean isLin = !isMac && !isWin;  // quartum non datur

  private static final File jarFolder =
    new File(System.getProperty("spato.app-dir"), isMac ? "Contents/Resources/Java" : "lib");

  private static final String commonJars[] = {
    "SPaTo_Visual_Explorer.jar", "tGUI.jar",
    "core.jar", "itext.jar", "pdf.jar",
    "xhtmlrenderer.jar"
  };

  public static String args[] = new String[0];

  private ClassLoader createClassLoader() throws java.net.MalformedURLException {
    URL urls[] = new URL[commonJars.length + (isWin ? 1 : 0)];
    for (int i = 0; i < commonJars.length; i++)
      urls[i] = new File(jarFolder, commonJars[i]).toURI().toURL();
    if (isWin)
      urls[urls.length-1] = new File(jarFolder, "WinRun4J.jar").toURI().toURL();
    return new URLClassLoader(urls);
  }

  public void run() {
    try {
      ClassLoader cl = createClassLoader();
      Thread.currentThread().setContextClassLoader(cl);
      cl.loadClass("net.spato.sve.app.SPaTo_Visual_Explorer").
        getMethod("main", new Class[] { args.getClass() }).
          invoke(null, new Object[] { args });
    } catch (Exception e) {
      System.err.println("!!! Could not launch application");
      e.printStackTrace();
      javax.swing.JOptionPane.showMessageDialog(null,
        "Could not launch appplication.", "SPaTo Visual Explorer",
        javax.swing.JOptionPane.ERROR_MESSAGE);
    }
  }

}
