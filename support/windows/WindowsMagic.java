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
import org.boris.winrun4j.*;

public class WindowsMagic {

  protected SPaTo_Visual_Explorer app = null;

  protected void setupFileAssociations() throws Exception {
    FileAssociation fa = null;
    String exeFile = new File(System.getProperty("spato.app-dir"), "SPaTo Visual Explorer.exe").getCanonicalPath();
    FileVerb verb = new FileVerb("open");
    verb.setCommand("\"" + exeFile + "\" \"%1\"");
    // register .spato
    System.out.println("--- generating .spato file association");
    fa = new FileAssociation(".spato");
    fa.setName("net.spato.document");
    fa.setDescription("SPaTo Document");
    //fa.put(verb);
    FileAssociations.CURRENT_USER.delete(fa);
    FileAssociations.CURRENT_USER.save(fa);
    // register .sve
    System.out.println("--- generating .sve file association");
    fa = new FileAssociation(".sve");
    fa.setName("net.spato.workspace");
    fa.setDescription("SPaTo Workspace");
    //fa.put(verb);
    FileAssociations.CURRENT_USER.delete(fa);
    FileAssociations.CURRENT_USER.save(fa);
  }

  public WindowsMagic(SPaTo_Visual_Explorer app) {
    this.app = app;
    // set correct icon
    app.frame.setIconImage(java.awt.Toolkit.getDefaultToolkit().createImage(
      new File(System.getProperty("spato.app-dir"), "lib\\spato-16x16.png").getAbsolutePath()));
    // setup file associations
    try {
      setupFileAssociations();
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("!!! Failed to setup file associations or frame icon");
    }
  }

}