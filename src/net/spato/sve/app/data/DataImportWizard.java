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

package net.spato.sve.app.data;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import processing.core.PApplet;
import net.spato.sve.app.SPaTo_Visual_Explorer;
import tGUI.*;


public class DataImportWizard extends TFrame {

  protected TConsole console;
  protected File file = null;
  protected String str = null;
  protected ExecutorService worker = null;
  protected BufferedReader reader = null;
  protected String lines[] = null;

  private DataImportWizard() {
    super(SPaTo_Visual_Explorer.INSTANCE.gui, "Data Import Wizard");
    setActionEventHandler(this);
    guiSetup();
    worker = Executors.newSingleThreadExecutor();
  }

  public DataImportWizard(File file) {
    this(); setTitle(file.getName());
    this.file = file;
    try {
      reader = new BufferedReader(new FileReader(file));
    } catch (Exception e) {
      console.logError("Could not open file for reading: ", e);
      e.printStackTrace();
    }
  }

  public DataImportWizard(String str) {
    this(); setTitle("Pasted/dropped text data");
    this.str = str;
    try {
      reader = new BufferedReader(new StringReader(str));
    } catch (Exception e) {
      console.logError("Could not read text data: ", e);
      e.printStackTrace();
    }
  }

  public void guiSetup() {
    setBounds(100, 100, 300, 200);
    console = gui.createConsole("import");
    console.setAlignment(TConsole.ALIGN_LEFT);
    console.setFancy(false);
    add(console, TBorderLayout.SOUTH);
  }

  public void start() {
    validate(); gui.add(this); gui.requestFocus(this);
    worker.submit(new Runnable() {
      public void run() {
        readData();  // or... read first line(s) and detect data type (e.g. GraphML etc)
        TabulatedData data = new TabulatedData(lines);
      }
    });
  }

  public void readData() {
    console.logProgress("Reading data").indeterminate();
    lines = new String[1024];
    int NL = 0;
    try {
      while ((lines[NL] = reader.readLine()) != null)
        if (++NL >= lines.length)
          lines = PApplet.expand(lines);
    } catch (IOException e) {
      console.abortProgress("Error while reading data: ", e);
      e.printStackTrace();
      lines = null;
    }
    lines = PApplet.subset(lines, 0, NL);
    console.finishProgress();
    console.logNote("Got " + NL + " lines of data");
  }

  public void finish() {
    worker.shutdown();
    gui.remove(this);
  }

  public void actionPerformed(String cmd) {
    //
  }

}
