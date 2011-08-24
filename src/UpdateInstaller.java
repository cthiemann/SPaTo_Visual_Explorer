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


/**
 * This class is part of SPaTo_Prelude.jar but resides in the default package.
 * Up until version 1.2.2 the self-update mechanism in SPaTo_Prelude ("Munchhausen trick")
 * was buggy (i.e., dysfunctional).  Versions before 1.2.2 will run UpdateInstaller (from
 * the default package), while later versions run net.spato.sve.prelude.UpdateInstaller.
 * To save the user from manually updating the software, we use this class to "hijack"
 * the faulty SPaTo_Prelude and provide a "fake" UpdateInstaller that calls the proper
 * UpdateInstaller and restarts the application.
 */
public class UpdateInstaller implements Runnable {

  private static final boolean isMac = System.getProperty("os.name").startsWith("Mac");
  private static final boolean isWin = System.getProperty("os.name").startsWith("Windows");
  private static final boolean isLin = !isMac && !isWin;  // quartum non datur

  private static final File updateCacheFolder =
    isLin ? new File(System.getProperty("user.home"), ".spato/update")
          : new File(System.getProperty("spato.app-dir"), isMac ? "Contents/Resources/update" : "update");

  public void run() {
    // run the real update installer
    net.spato.sve.prelude.UpdateInstaller updater = new net.spato.sve.prelude.UpdateInstaller();
    updater.setPostInstallTaskHook(new Runnable() {
      public void run() {
        // and since we're at it, let's clean up some stuff
        if (isLin) new File(System.getProperty("spato.app-dir"), "lib/config.sh").delete();
        if (isLin) new File(System.getProperty("spato.app-dir"), "lib/config.sh.orig").delete();
        if (isMac) new File(System.getProperty("spato.app-dir"), "Contents/MacOS/ApplicationUpdateWrapper").delete();
        if (isMac) new File(System.getProperty("spato.app-dir"), "Contents/MacOS/JavaApplicationStub64").delete();
        if (isMac) new File(System.getProperty("spato.app-dir"), "Contents/Info.plist.orig").delete();
        if (isWin) new File(System.getProperty("spato.app-dir"), "lib\\SPaTo_Prelude.ini").delete();
        if (isWin) new File(System.getProperty("spato.app-dir"), "lib\\SPaTo_Visual_Explorer.ini").delete();
        if (isWin) new File(System.getProperty("spato.app-dir"), "lib\\SPaTo_Visual_Explorer.ini.orig").delete();
      }
    });
    updater.run();
    // launch application (on MacOSX and Linux the old launch scripts will do this)
    if (isWin) new net.spato.sve.prelude.ApplicationLauncher().run();
  }

}
