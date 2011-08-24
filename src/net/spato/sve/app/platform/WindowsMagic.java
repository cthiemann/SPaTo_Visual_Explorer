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

import java.io.File;
import javax.swing.UIManager;

import net.spato.sve.app.SPaTo_Visual_Explorer;
import org.boris.winrun4j.ActivationListener;
import org.boris.winrun4j.DDE;
import org.boris.winrun4j.FileAssociation;
import org.boris.winrun4j.FileAssociations;
import org.boris.winrun4j.FileVerb;


public class WindowsMagic extends PlatformMagic implements ActivationListener {

  public WindowsMagic(SPaTo_Visual_Explorer app, String args[]) {
    super(app, args);
    System.out.println(">>> WindowsMagic class loaded");
    // set correct icon
    app.frame.setIconImage(java.awt.Toolkit.getDefaultToolkit().createImage(
      new File(System.getProperty("spato.app-dir"), "lib\\spato-16x16.png").getAbsolutePath()));
    // setup file associations
    try {
      setupFileAssociations();
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("!!! Failed to setup file associations");
    }
    // set system look & feel
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception e) {
      e.printStackTrace();
    }
    // add DDE listeners and process command line arguments
    DDE.addActivationListener(this);
    for (String filename : args)
      openFile(new File(filename));
  }

  public void ready() {
    super.ready();
    DDE.ready();
  }

  protected void setupFileAssociations() throws Exception {
    FileAssociation fa = null;
    String exeFile = new File(System.getProperty("spato.app-dir"), "SPaTo Visual Explorer.exe").getCanonicalPath();
    FileVerb verb = new FileVerb("open");
    verb.setCommand("\"" + exeFile + "\" \"%1\"");
    // register .spato
    System.out.println("--- registering .spato file association");
    fa = new FileAssociation(".spato");
    fa.setName("net.spato.document");
    fa.setDescription("SPaTo Document");
    //fa.setIcon(exeFile + ",0");
    fa.put(verb);
    FileAssociations.CURRENT_USER.delete(fa);
    FileAssociations.CURRENT_USER.save(fa);
    // register .sve
    System.out.println("--- registering .sve file association");
    fa = new FileAssociation(".sve");
    fa.setName("net.spato.workspace");
    fa.setDescription("SPaTo Workspace");
    //fa.setIcon(exeFile + ",0");
    fa.put(verb);
    FileAssociations.CURRENT_USER.delete(fa);
    FileAssociations.CURRENT_USER.save(fa);
  }

  public void bringToFront() {
    final SPaTo_Visual_Explorer app = this.app;
    java.awt.EventQueue.invokeLater(new Runnable() {
      public void run() {
        app.frame.setVisible(true);
        app.frame.setExtendedState(app.frame.getExtendedState() & ~java.awt.Frame.ICONIFIED);
        app.frame.setAlwaysOnTop(true);
        app.frame.toFront();
        app.frame.requestFocus();
        app.requestFocusInWindow();
        app.frame.setAlwaysOnTop(false);
      }
    });
  }

  public void activate(String cmd) {
    String filename = cmd.trim();
    if (filename.startsWith("\"") && filename.endsWith("\""))
      filename = filename.substring(1, filename.length() - 1);
    // activate application window
    bringToFront();
    // try to open the file
    javax.swing.JOptionPane.showMessageDialog(app.frame, "ACTIVATE " + filename, "Open File", javax.swing.JOptionPane.INFORMATION_MESSAGE);
    if (!filename.equals(""))
      super.openFile(new File(filename));
  }

}