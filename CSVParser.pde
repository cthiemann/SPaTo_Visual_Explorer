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

public class CSVParser {
  // FIXME: handle header/footer lines
  // FIXME: handle line breaks in enclosed fields

  protected String lines[] = null;

  public static final char WHITESPACE = 0;
  public static final char GUESS = (char)-1;
  protected char delim = ',';  // field delimiter
  protected char enclose = '"';  // (optional) field enclosing char
  protected char escape = '\\';  // escape char for enclose char

  protected int numCols = 0;
  protected int numHeaderRows = 0;
  protected int numFooterRows = 0;

  protected String fields[][] = null;


  public CSVParser(String lines[]) { this(lines, GUESS); }
  public CSVParser(String lines[], char delim) { this(lines, delim, GUESS); }
  public CSVParser(String lines[], char delim, char enclose) { this(lines, delim, enclose, GUESS); }
  public CSVParser(String lines[], char delim, char enclose, char escape) {
    this.lines = lines;
    setParameters(delim, enclose, escape);
  }

  public void setDelim(char delim) { this.delim = delim; this.fields = null; checkParameters(); }
  public char getDelim() { return delim; }

  public void setEnclose(char enclose) { this.enclose = enclose; this.fields = null; checkParameters(); }
  public char getEnclose() { return enclose; }

  public void setEscape(char escape) { this.escape = escape; this.fields = null; checkParameters(); }
  public char getEscape() { return escape; }

  public void setParameters(char delim, char enclose, char escape) {
    this.delim = delim; this.enclose = enclose; this.escape = escape; this.fields = null; checkParameters(); }

  public String[][] getFields() { if (fields == null) parse(); return fields; }

  public float[][] getFloatMatrix() {
    if (fields == null) parse();
    float result[][] = new float[lines.length][numCols];
    for (int i = 0; i < lines.length; i++)
      for (int j = 0; j < numCols; j++)
        result[i][j] = parseFloat(fields[i][j]);
    return result;
  }

  public int[][] getIndexMatrix() { return getIntMatrix(-1); }
  public int[][] getIntMatrix() { return getIntMatrix(0); }
  public int[][] getIntMatrix(int delta) {
    if (fields == null) parse();
    int result[][] = new int[lines.length][numCols];
    for (int i = 0; i < lines.length; i++)
      for (int j = 0; j < numCols; j++)
        result[i][j] = parseInt(fields[i][j]) + delta;
    return result;
  }

  public String[] getColumn(int j) {
    if (fields == null) parse();
    String result[] = new String[lines.length];
    for (int i = 0; i < lines.length; i++)
      result[i] = fields[i][j];
    return result;
  }

  public float[] getFloatColumn(int j) { return parseFloat(getColumn(j)); }

  public int[] getIndexColumn(int j) { return getIntColumn(j, -1); }
  public int[] getIntColumn(int j) { return parseInt(getColumn(j)); }
  public int[] getIntColumn(int j, int delta) {
    if (fields == null) parse();
    int result[] = new int[lines.length];
    for (int i = 0; i < lines.length; i++)
      result[i] = parseInt(fields[i][j]) + delta;
    return result;
  }

  public String[] getRow(int i) { if (fields == null) parse(); return fields[i]; }

  public float[] getFloatRow(int i) { return parseFloat(getRow(i)); }

  public int[] getIndexRow(int i) { return getIntRow(i, -1); }
  public int[] getIntRow(int i) { return parseInt(getRow(i)); }
  public int[] getIntRow(int i, int delta) {
    if (fields == null) parse();
    int result[] = new int[numCols];
    for (int j = 0; j < numCols; j++)
      result[j] = parseInt(fields[i][j]) + delta;
    return result;
  }

  protected void parse() {
    this.fields = new String[lines.length][];
    for (int i = 0; i < lines.length; i++)
      fields[i] = parseRecord(lines[i]);
  }

  protected String[] parseRecord(String record) { return parseRecord(record, delim, enclose, escape); }

  protected String[] parseRecord(String record, char delim, char enclose, char escape) {
    Vector<String> result = new Vector<String>();  // return value
    String currentField = "";  // current field
    boolean enclosed = false;
    boolean escaped = false;
    for (int i = 0; i < record.length(); i++) {
      char c = record.charAt(i);
      if (((delim == WHITESPACE) ? Character.isWhitespace(c) : (c == delim)) && !enclosed) {
        result.add(currentField);
        currentField = "";
      } else if ((c == escape) && (i < record.length() - 1) && (record.charAt(i+1) == enclose)) {
        escaped = true;
      } else if ((c == enclose) && !escaped) {
        enclosed = !enclosed;
      } else {
        currentField += c;
        escaped = false;
      }
    }
    result.add(currentField);
    return result.toArray(new String[numCols]);
  }

  protected void checkParameters() {
    if (lines.length > 2) {
      String testA = lines[lines.length/2];
      String testB = lines[lines.length/2-1];
      for (char testDelim : (delim == GUESS) ? new char[] { ',', '\t', WHITESPACE, ';', '$' } : new char[] { delim }) {
        for (char testEnclose : (enclose == GUESS) ? new char[] { '"', '\'' } : new char[] { enclose }) {
          for (char testEscape : (escape == GUESS) ? new char[] { '\\', testEnclose } : new char[] { escape }) {
            int sizeA = parseRecord(testA, testDelim, testEnclose, testEscape).length;
            int sizeB = parseRecord(testB, testDelim, testEnclose, testEscape).length;
            if ((sizeA > 1) && (sizeA == sizeB)) {
              delim = testDelim;
              enclose = testEnclose;
              escape = testEscape;
              numCols = sizeA;
              return;  // done
            }
          }
        }
      }
    }
    // nothing worked, reset all parameters to GUESS, yields single-column data
    delim = enclose = escape = GUESS;
    numCols = 1;
  }

}