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

import java.util.Vector;
import processing.core.PApplet;


public class TabulatedData {
  // FIXME: handle header/footer lines
  // FIXME: handle line breaks in enclosed fields
  // FIXME: handle proper escape guessing

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

  public static final int UNKNOWN = 0;
  public static final int STRING = 1;
  public static final int FLOAT = 2;
  public static final int INT = 3;
  protected int typeMatrix = 0;
  protected int typeColumn[] = null;
  protected int typeRow[] = null;


  public TabulatedData(String lines[]) { this(lines, GUESS); }
  public TabulatedData(String lines[], char delim) { this(lines, delim, GUESS); }
  public TabulatedData(String lines[], char delim, char enclose) { this(lines, delim, enclose, GUESS); }
  public TabulatedData(String lines[], char delim, char enclose, char escape) {
    this.lines = lines;
    setParameters(delim, enclose, escape);
  }

  public void setDelim(char delim) { this.delim = delim; parse(); }
  public char getDelim() { return delim; }

  public void setEnclose(char enclose) { this.enclose = enclose; parse(); }
  public char getEnclose() { return enclose; }

  public void setEscape(char escape) { this.escape = escape; parse(); }
  public char getEscape() { return escape; }

  public void setParameters(char delim, char enclose, char escape) {
    this.delim = delim; this.enclose = enclose; this.escape = escape; parse(); }

  public String[][] getFields() { return fields; }
  public int getNumCols() { return numCols; }
  public int getNumRows() { return lines.length - numHeaderRows - numFooterRows; }

  public boolean isSquareMatrix() { return getNumCols() == getNumRows(); }
  public boolean isIntMatrix() { return checkMatrixType() == INT; }
  public boolean isNumericMatrix() { return checkMatrixType() >= FLOAT; }
  public String getMatrixType() { return typeString(checkMatrixType()); }

  public boolean isIntColumn(int j) { return checkColumnType(j) == INT; }
  public boolean isNumericColumn(int j) { return checkColumnType(j) >= FLOAT; }
  public String getColumnType(int j) { return typeString(checkColumnType(j)); }

  public boolean isIntRow(int j) { return checkRowType(j) == INT; }
  public boolean isNumericRow(int j) { return checkRowType(j) >= FLOAT; }
  public String getRowType(int j) { return typeString(checkRowType(j)); }

  public float[][] getFloatMatrix() {
    if (fields == null) parse();
    float result[][] = new float[lines.length][numCols];
    for (int i = 0; i < lines.length; i++)
      for (int j = 0; j < numCols; j++)
        result[i][j] = PApplet.parseFloat(fields[i][j]);
    return result;
  }

  public int[][] getIndexMatrix() { return getIntMatrix(-1); }
  public int[][] getIntMatrix() { return getIntMatrix(0); }
  public int[][] getIntMatrix(int delta) {
    if (fields == null) parse();
    int result[][] = new int[lines.length][numCols];
    for (int i = 0; i < lines.length; i++)
      for (int j = 0; j < numCols; j++)
        result[i][j] = PApplet.parseInt(fields[i][j]) + delta;
    return result;
  }

  public String[] getColumn(int j) {
    if (fields == null) parse();
    String result[] = new String[lines.length];
    for (int i = 0; i < lines.length; i++)
      result[i] = fields[i][j];
    return result;
  }

  public float[] getFloatColumn(int j) { return PApplet.parseFloat(getColumn(j)); }

  public int[] getIndexColumn(int j) { return getIntColumn(j, -1); }
  public int[] getIntColumn(int j) { return PApplet.parseInt(getColumn(j)); }
  public int[] getIntColumn(int j, int delta) {
    if (fields == null) parse();
    int result[] = new int[lines.length];
    for (int i = 0; i < lines.length; i++)
      result[i] = PApplet.parseInt(fields[i][j]) + delta;
    return result;
  }

  public String[] getRow(int i) { if (fields == null) parse(); return fields[i]; }

  public float[] getFloatRow(int i) { return PApplet.parseFloat(getRow(i)); }

  public int[] getIndexRow(int i) { return getIntRow(i, -1); }
  public int[] getIntRow(int i) { return PApplet.parseInt(getRow(i)); }
  public int[] getIntRow(int i, int delta) {
    if (fields == null) parse();
    int result[] = new int[numCols];
    for (int j = 0; j < numCols; j++)
      result[j] = PApplet.parseInt(fields[i][j]) + delta;
    return result;
  }

  protected String[] parseRecord(String record, char delim, char enclose, char escape) {
    Vector<String> result = new Vector<String>();  // return value
    String currentField = "";  // current field
    boolean enclosed = false;
    boolean escaped = false;
    for (int i = 0; i < record.length(); i++) {
      char c = record.charAt(i);
      if ((c == delim) && !enclosed) {
        if ((delim != ' ') || (!currentField.equals(""))) {  // treat multiple spaces as one separator
          result.add(currentField);
          currentField = "";
        }
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

  protected String[][] parse(String lines[], char delim, char enclose, char escape, int minNumCols) {
    // parse one record that most probably is neither header nor footer and check number of columns
    int numCols = parseRecord(lines[lines.length/2], delim, enclose, escape).length;
    if (numCols < minNumCols) return null;
    // parse all records and make sure they all have the same number of columns
    String fields[][] = new String[lines.length][];
    for (int i = 0; i < lines.length; i++) {
      fields[i] = parseRecord(lines[i], delim, enclose, escape);
      if (fields[i].length != numCols) return null;
    }
    return fields;
  }

  protected void parse() {
    // reset all processed data
    this.fields = null;
    typeMatrix = 0;
    typeColumn = null;
    typeRow = null;
    numCols = 0;
    // check if we actually have to do anything
    if ((lines == null) || (lines.length == 0)) return;
    // cycle through all parameters that are to be GUESSed, saving the "best" parsing result
    char _delim = this.delim, _enclose = this.enclose, _escape = this.escape;
    for (char enclose : (_enclose == GUESS) ? new char[] { '"', '\'' } : new char[] { _enclose }) {
      for (char escape : (_escape == GUESS) ? new char[] { '\\', enclose } : new char[] { _escape }) {
        for (char delim : (_delim == GUESS) ? new char[] { '\t', ' ', ',', ';', '$' } : new char[] { _delim }) {
          // try to parse into more columns than we already found (at least 2)
          String fields[][] = parse(lines, delim, enclose, escape, PApplet.max(2, numCols + 1));
          if (fields != null) {
            // these seem to be good parameters, save them into the class member variables
            // (yes, using the same names for local and class variables is pretty awesome fun)
            this.fields = fields;
            this.delim = delim;
            this.enclose = enclose;
            this.escape = escape;
            numCols = fields[0].length;
          }
        }
      }
    }
    // if the guessing didn't yield anything appropriate, then treat this as 1-column data
    if (this.fields == null) {
      this.fields = new String[lines.length][1];
      for (int i = 0; i < lines.length; i++)
        this.fields[i][0] = lines[i];
      numCols = 1;
    }
    // create proper type caches
    typeColumn = new int[numCols];
    typeRow = new int[lines.length];
  }

  protected int checkMatrixType() {
    if (typeMatrix == 0) {
      typeMatrix = INT;
      for (int j = 0; j < numCols; j++)
        typeMatrix = PApplet.min(typeMatrix, checkColumnType(j));
    }
    return typeMatrix;
  }

  protected int checkColumnType(int j) {
    if (typeColumn[j] == 0) {
      typeColumn[j] = INT;
      for (int i = 0; i < lines.length; i++) {
        if (typeColumn[j] == INT) try { Integer.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeColumn[j] = FLOAT; }
        if (typeColumn[j] == FLOAT) try { Float.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeColumn[j] = STRING; break; }
      }
    }
    return typeColumn[j];
  }

  protected int checkRowType(int i) {
    if (typeRow[i] == 0) {
      typeRow[i] = INT;
      for (int j = 0; j < numCols; j++) {
        if (typeRow[i] == INT) try { Integer.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeRow[i] = FLOAT; }
        if (typeRow[i] == FLOAT) try { Float.valueOf(fields[i][j]); } catch (NumberFormatException e) { typeRow[i] = STRING; break; }
      }
    }
    return typeRow[i];
  }

  protected String typeString(int type) {
    switch (type) {
      case STRING: return "string";
      case FLOAT: return "numeric";
      case INT: return "integer";
      default: return "unknown";
    }
  }

}
