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
import java.io.PrintWriter;

import net.spato.sve.app.SPaTo_Visual_Explorer;


public class SPaTo_Version {

  public static void println(PrintWriter writer, String str) {
    writer.println(str);
    System.out.println(str);
  }

  public static void main(String args[]) {
    try {
      PrintWriter writer = new PrintWriter("build" + File.separator + "version.properties");
      String version = SPaTo_Visual_Explorer.VERSION;
      println(writer, "version.update=" + version);
      version = version.split("_")[0];
      println(writer, "version.download=" + version);
      String debug = SPaTo_Visual_Explorer.VERSION_DEBUG;
      println(writer, "version.long=" + SPaTo_Visual_Explorer.VERSION + ((debug.length() > 0) ? " (" + debug + ")" : ""));
      println(writer, "version.date=" + SPaTo_Visual_Explorer.VERSION_TIMESTAMP);
      writer.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

}
