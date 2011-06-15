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
  // ATTN: does not support line breaks in enclosed fields
  
  protected String lines[] = null;
  
  public static final char WHITESPACE = 0;
  protected char delim = ',';  // field deliminator
  protected char enclose = '"';  // (optional) field enclosing char
  protected char escape = '\\';  // escape char for enclose char
  protected char decim = '.';  // decimal point
  
  public CSVParser(String lines[]) { this.lines = lines; }
  
  public String[][] getFields() { return getFields(delim, enclose, escape); }

  public String[][] getFields(char delim, char enclose, char escape) {
    String fields[][] = new String[lines.length][];
    for (int i = 0; i < lines.length; i++)
      fields[i] = parseRecord(lines[i]).toArray(new String[0]);
    return fields;
  }
  
  protected Vector<String> parseRecord(String record) { return parseRecord(record, delim, enclose, escape); }
  
  protected Vector<String> parseRecord(String record, char delim, char enclose, char escape) {
    Vector<String> fields = new Vector<String>();  // return value
    String currentField = "";  // current field
    boolean enclosed = false;
    boolean escaped = false;
    for (int i = 0; i < record.length(); i++) {
      char c = record.charAt(i);
      if (((delim == WHITESPACE) ? Character.isWhitespace(c) : (c == delim)) && !enclosed) {
        fields.add(currentField);
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
    fields.add(currentField);
    return fields;
  }
    
}