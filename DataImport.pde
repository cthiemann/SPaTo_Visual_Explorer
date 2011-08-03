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

class DataImportWizard extends TFrame {

  TConsole console;
  File file = null;
  String str = null;
  ExecutorService worker = null;
  BufferedReader reader = null;
  String lines[] = null;

  DataImportWizard() {
    super(SPaTo_Visual_Explorer.this.gui, "Data Import Wizard");
    setActionEventHandler(this);
    guiSetup();
    worker = Executors.newSingleThreadExecutor();
  }

  DataImportWizard(File file) {
    this(); setTitle(file.getName());
    this.file = file;
    try {
      reader = new BufferedReader(new FileReader(file));
    } catch (Exception e) {
      console.logError("Could not open file for reading: ", e);
      e.printStackTrace();
    }
  }

  DataImportWizard(String str) {
    this(); setTitle("Pasted/dropped text data");
    this.str = str;
    try {
      reader = new BufferedReader(new StringReader(str));
    } catch (Exception e) {
      console.logError("Could not read text data: ", e);
      e.printStackTrace();
    }
  }

  void guiSetup() {
    setBounds(100, 100, 300, 200);
    console = gui.createConsole("import");
    console.setAlignment(TConsole.ALIGN_LEFT);
    console.setFancy(false);
    add(console, TBorderLayout.SOUTH);
  }

  void start() {
    validate(); gui.add(this); gui.requestFocus(this);
    worker.submit(new Runnable() {
      public void run() {
        readData();  // or... read first line(s) and detect data type (e.g. GraphML etc)
        TabulatedData data = new TabulatedData(lines);
      }
    });
  }

  void readData() {
    console.logProgress("Reading data").indeterminate();
    lines = new String[1024];
    int NL = 0;
    try {
      while ((lines[NL] = reader.readLine()) != null)
        if (++NL >= lines.length)
          lines = expand(lines);
    } catch (IOException e) {
      console.abortProgress("Error while reading data: ", e);
      e.printStackTrace();
      lines = null;
    }
    lines = subset(lines, 0, NL);
    console.finishProgress();
    console.logNote("Got " + NL + " lines of data");
  }

  void finish() {
    worker.shutdown();
    gui.remove(this);
  }

  void actionPerformed(String cmd) {
    //
  }

}
