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
import java.util.*;
import java.util.regex.*;
import javax.swing.JOptionPane;

import com.sun.jna.Platform;


public class MaxMemDetector implements Runnable {

  // minimum memory requirement in MB
  protected final int MIN_MEMORY = 512;
  // reasonable upper bound for -Xmx in MB (the 64-bit JVM would allow you to allocate several TBs)
  protected final int MAX_MEMORY = 8*1024;
  // stop the nested intervals algorithm if max - min <= ACCURACY
  protected final int ACCURACY = 50;
  // command line to launch JVM and run MemTest
  protected String javaCmdLine[] = null;
  // the application's root folder
  protected String appRootFolder = System.getProperty("spato.app-dir");
  // current/recommended -Xmx value in megabytes.
  protected long oldValue = Runtime.getRuntime().maxMemory()/1024/1024;
  protected long newValue = Runtime.getRuntime().maxMemory()/1024/1024;
  // 32-bit vs 64-bit */
  protected int bits = Platform.is64Bit() ? 64 : 32;
  // configuration file and contents
  protected File cfgFileIn = null, cfgFileOut = null;
  protected Vector<String> cfgFileLines = new Vector<String>();
  // line no. (zero-based), and start/end index of oldValue in configuration file
  protected int lineNo = -1, start, end;
  // should we use the WinRun4J launcher to run the tests?
  protected boolean useWinRun4J = Platform.isWindows();

  protected void findJava() {
    // find java executable
    String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
    if (Platform.isWindows()) java += ".exe";
    System.out.println("--- java = " + java);
    if (!new File(java).exists()) throw new RuntimeException("could not find java executable");
    // set classpath to MemTest.class
    String classpath = new File(System.getProperty("spato.tmp-dir"), "SPaTo_Prelude.jar").getAbsolutePath();
    System.out.println("--- classpath = " + classpath);
    // construct proper command line with placeholder for max-mem value at index 1
    javaCmdLine = Platform.isMac()
      ? new String[] { java, "-Xmx????m", "-d" + bits, "-cp", classpath, "MemTest" }
      : new String[] { java, "-Xmx????m", "-cp", classpath, "MemTest" };
  }

  protected void findConfigFile() {
    if (Platform.isLinux()) cfgFileOut = new File(appRootFolder, "lib/config.sh");
    if (Platform.isMac()) cfgFileOut = new File(appRootFolder, "Contents/Info.plist");
    if (Platform.isWindows()) cfgFileOut = new File(appRootFolder, "lib\\SPaTo_Visual_Explorer.ini");
    if (cfgFileOut == null) throw new RuntimeException("unsupported platform");
    cfgFileIn = new File(cfgFileOut.getAbsolutePath() + ".orig");
    if (!cfgFileIn.exists()) throw new RuntimeException(cfgFileIn + " does not exist");
    System.out.println("--- original config file is at " + cfgFileIn);
  }

  protected void loadConfiguration() {
    // read original configuration file and cache its contents
    loadConfiguration(cfgFileIn, true);
    // if output file exists, then read the (previously optimized) -Xmx value from there
    loadConfiguration(cfgFileOut, false);
  }
  protected void loadConfiguration(File cfgFile, boolean cacheContent) {
    BufferedReader reader = null;
    boolean foundXmx = false;
    try {
      String line = null; int i = -1;
      Pattern regex = Pattern.compile("-Xmx([0-9]+)m");
      // read file and search for -Xmx value
      reader = new BufferedReader(new FileReader(cfgFile));
      while ((line = reader.readLine()) != null) {
        i++;
        if (cacheContent) cfgFileLines.add(line);
        if (foundXmx) continue;  // no need to do more regex matches
        Matcher m = regex.matcher(line);
        if (m.find()) {  // Eureka!
          oldValue = Integer.parseInt(m.group(1));
          if (cacheContent) { lineNo = i; start = m.start(1); end = m.end(1); }
          foundXmx = true;
        }
      }
    } catch (Exception e) {
      throw new RuntimeException("error reading from " + cfgFile, e);
    } finally {
      try { reader.close(); } catch (Exception e) {}
    }
    if (!foundXmx) throw new RuntimeException("-Xmx([0-9]+)m not found in " + cfgFile);
  }

  protected String[] prepareWinRun4JMemTest(long mem) {
    PrintWriter ini = null;
    try {
      File iniFile = File.createTempFile("spatoMemTest", ".ini");
      iniFile.deleteOnExit();
      ini = new PrintWriter(iniFile);
      ini.println("working.directory = " + new File(appRootFolder).getAbsolutePath());
      ini.println("classpath.1 = lib\\SPaTo_Prelude.jar");
      ini.println("main.class = MemTest");
      ini.println("vmarg.1 = -Xmx" + mem + "m");
      ini.println("vm.version.min = 1.5");
      ini.println("log.level = error");
      ini.println("[ErrorMessages]");
      ini.println("show.popup = false");
      return new String[] { appRootFolder + "\\SPaTo Visual Explorer.exe",
        "--WinRun4J:ExecuteINI", iniFile.getAbsolutePath() };
    } catch (Exception e) {
      throw new RuntimeException("failed to prepare WinRun4J-based test", e);
    } finally {
      try { ini.close(); } catch (Exception e) {}
    }
  }

  protected boolean runTest(long mem) {
    try {
      System.out.print("runTest(" + mem + ")");
      if (useWinRun4J) javaCmdLine = prepareWinRun4JMemTest(mem);
      else javaCmdLine[1] = "-Xmx" + mem + "m";
      Process p = new ProcessBuilder(javaCmdLine).redirectErrorStream(true).start();
      int res = useWinRun4J ? -1 : p.waitFor();  // WinRun4J stalls when called as a child process (wtf?)
      BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
      String line = null;
      while ((line = reader.readLine()) != null) {
        System.out.println("    " + line);
        if (useWinRun4J && line.startsWith("MemTest: max memory is")) { res = 0; p.destroy(); }
        if (useWinRun4J && line.startsWith(" [err] Failed to start")) { res = 1; p.destroy(); }
      }
      reader.close();
      System.out.println((res == 0) ? "    >>> " + mem + " PASSED" : "    !!! " + mem + " FAILED");
      return res == 0;
    } catch (Exception e) {
      throw new RuntimeException("could not start JVM process or read its output", e);
    }
  }

  protected void detectMaximumHeapSize() {
    System.err.println("using " + bits + "-bit JVM");
    System.err.println("current max memory = " + oldValue);
    long min = oldValue;
    long max = oldValue*110/100;
    long minmin = MIN_MEMORY;  // minimum memory required
    long maxmax = MAX_MEMORY;  // upper bound (the 64-bit JVM would let you allocate several TBs)
    System.out.println("minmin = " + minmin);
    System.out.println("maxmax = " + maxmax);
    // run the tests
    if (!runTest(max) && runTest(min))  // this is the most common case (we cannot improve significantly
      newValue = min;        // because the optimal value has already been determined in an earlier run)
    else if (!runTest(minmin))  // this is very bad
      throw new RuntimeException("minmin-fail");
    else if (runTest(maxmax))  // this is very good
      newValue = maxmax;
    else {  // the hardest case: determine proper bounds and converge using nested intervals
      while ((min > minmin) && !runTest(min)) { max = min; min = Math.max(min/2, minmin); }
      while ((max < maxmax) && runTest(max)) { min = max; max = Math.min(2*max, maxmax); }
      while (max - min > ACCURACY) {
        long mem = min + (max - min)/2;
        if (runTest(mem)) min = mem; else max = mem;
      }
      newValue = min*95/100;  // the maximum posssible memory seems to slightly vary between calls... (wtf?)
      if (Platform.isWindows()) newValue = newValue*90/100;  // Windows sucks FIXME: find safe value here...
    }
    System.err.println("recommended max memory = " + newValue);
  }

  protected void saveConfiguration() {
    PrintWriter writer = null;
    try {
      writer = new PrintWriter(cfgFileOut);
      for (int i = 0; i < cfgFileLines.size(); i++) {
        String line = cfgFileLines.get(i);
        if (i == lineNo)
          line = line.substring(0, start) + newValue + line.substring(end);
        writer.println(line);
      }
    } catch (Exception e) {
      throw new RuntimeException("error writing to " + cfgFileOut, e);
    } finally {
      try { writer.close(); } catch (Exception e) {}
    }
  }

  public void run() {
    try {
      findJava();
      findConfigFile();
      loadConfiguration();
      detectMaximumHeapSize();
      saveConfiguration();
    } catch (Exception e) {
      e.printStackTrace();
      if (e.getMessage().equals("minmin-fail"))
        JOptionPane.showMessageDialog(null,
          "Failed to allocate " + MIN_MEMORY + " MB of memory.\n" +
          "You might not be able to run SPaTo Visual Explorer.",
          "A very unfortunate event has occured",
          JOptionPane.ERROR_MESSAGE);
      else
        JOptionPane.showMessageDialog(null,
          "Detecting the maximum possible Java heap space has failed.\n" +
          "You might not be able to open very large data files.\n" +
          "\n" +
          e.getClass().getName() + ": " + e.getMessage(),
          "An unfortunate event has occured",
          JOptionPane.ERROR_MESSAGE);
    }
  }

}
