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
import javax.swing.JOptionPane;

public class WindowsPrelude implements Runnable {
  
  // the application's root folder
  protected String appRootFolder = System.getProperty("spato.app-dir");
  
  protected void launchApplication() {
    if (!appRootFolder.endsWith(File.separator)) appRootFolder += File.separator;
    String cmdLine[] = new String[] {
      appRootFolder + "SPaTo Visual Explorer.exe",
      "--WinRun4J:ExecuteINI", appRootFolder + "lib\\SPaTo_Visual_Explorer.ini" };
    String cmdLineStr = ""; for (String piece : cmdLine) cmdLineStr += piece + " ";
    try {
      System.out.println(cmdLineStr);
      Runtime.getRuntime().exec(cmdLine);;
    } catch (Exception e) {
      e.printStackTrace();
      JOptionPane.showMessageDialog(null,
        "Failed to execute application launcher:\n" +
        "\n" +
        cmdLineStr + "\n",
        "A very unfortunate event has occured",
        JOptionPane.ERROR_MESSAGE);
    }
  }

  public void run() {
    launchApplication();
  }
  
}