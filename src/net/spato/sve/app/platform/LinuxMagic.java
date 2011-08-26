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

package net.spato.sve.app.platform;

import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import javax.swing.UIManager;

import net.spato.sve.app.SPaTo_Visual_Explorer;


public class LinuxMagic extends PlatformMagic {

  public LinuxMagic(SPaTo_Visual_Explorer app, String args[]) {
    super(app, args);
    System.out.println(">>> LinuxMagic class loaded");
    // set frame icon
    app.frame.setIconImage(Toolkit.getDefaultToolkit().createImage(
      System.getProperty("spato.app-dir") + "/lib/SPaTo_Visual_Explorer.png"));
    // install XDG stuff
    System.out.println("--- installing desktop menu item and mime-types");
    new Thread(new Runnable() {
      public void run() {
        try { Thread.sleep(2500); } catch (Exception e) {}  // try not to interfere too much with start-up
        try {
          Process p = new ProcessBuilder(
            new String[] { "lib/xdg.sh", "--install", System.getProperty("spato.exec") }).
              redirectErrorStream(true).start();
          BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
          String line = null;
          while ((line = reader.readLine()) != null)
            System.out.println("--- [xdg.sh] " + line);
          try { reader.close(); } catch (Exception e) {}
          p.waitFor();
        } catch (Exception e) {
          System.err.println("!!! failed to install desktop menu item or mime-types");
        }
      }
    }, "lib/xdg.sh").start();
    // set system look & feel
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }
    // process command line arguments
    for (String filename : args)
      openFile(new File(filename));
  }

}
