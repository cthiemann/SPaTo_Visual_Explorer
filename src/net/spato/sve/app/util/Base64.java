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

package net.spato.sve.app.util;


public class Base64 {

  private static char map[] = new char[64];
  static {
    for (int i = 00; i < 26; i++) map[i] = (char)('A' + (i - 00));
    for (int i = 26; i < 52; i++) map[i] = (char)('a' + (i - 26));
    for (int i = 52; i < 62; i++) map[i] = (char)('0' + (i - 52));
    map[62] = '+'; map[63] = '/';
  }

  // this is incredibly inefficient, but ok for our purposes (only decoding short hashes and keys)
  private static int unmap(char c) {
    for (int i = 0; i < 64; i++) if (map[i] == c) return i;
    throw new IllegalArgumentException("value " + c + " is not in the map");
  }

  public static String encode(byte data[]) { return encode(data, 76); }
  public static String encode(byte data[], int wrapColumn) {
    StringBuffer result = new StringBuffer();
    int input[] = new int[3];
    int output[] = new int[4];
    int lineLen = 0;
    for (int i = 0; i < data.length; i += 3) {
      int len = Math.min(3, data.length - i);
      input[0] = 0xff & (int)data[i+0];
      input[1] = (len > 1) ? (0xff & (int)data[i+1]) : 0;
      input[2] = (len > 2) ? (0xff & (int)data[i+2]) : 0;
      output[0] = (0xfc & input[0]) >> 2;  // get highest 6 bits of first input byte
      output[1] = ((0x03 & input[0]) << 4) + ((0xf0 & input[1]) >> 4);  // get lowest 2 bits of first and highest 4 bits of second input byte
      output[2] = ((0x0f & input[1]) << 2) + ((0xc0 & input[2]) >> 6);  // lowest 4 bits of second and hightes 2 bits of third input byte
      output[3] = (0x3f & input[2]);  // lowest 6 bits of third input byte
      result.append(map[output[0]]);
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
      result.append(map[output[1]]);
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
      result.append(len > 1 ? map[output[2]] : '=');
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
      result.append(len > 2 ? map[output[3]] : '=');
      if (++lineLen == wrapColumn) { result.append('\r'); result.append('\n'); lineLen = 0; }
    }
    return result.toString();
  }

  public static byte[] decode(String data) {
    String lines[] = data.split("\n");
    data = ""; for (String line : lines) data += line.trim();
    if (data.length() % 4 != 0) throw new IllegalArgumentException("input length is not a multiple of four");
    int reslen = data.length()*3/4;
    if (data.charAt(data.length() - 1) == '=') { reslen--; data = data.substring(0, data.length() - 1); }
    if (data.charAt(data.length() - 1) == '=') { reslen--; data = data.substring(0, data.length() - 1); }
    if (data.charAt(data.length() - 1) == '=') throw new IllegalArgumentException("input has to many padding characters");
    byte result[] = new byte[reslen];
    int input[] = new int[4];
    int output[] = new int[3];
    int ii = 0;
    for (int i = 0; i < data.length(); i += 4) {
      int len = Math.min(4, data.length() - i);
      input[0] = unmap(data.charAt(i));
      input[1] = unmap(data.charAt(i + 1));
      input[2] = (len > 2) ? unmap(data.charAt(i + 2)) : 0;
      input[3] = (len > 3) ? unmap(data.charAt(i + 3)) : 0;
      output[0] = (input[0] << 2) + ((0x30 & input[1]) >> 4);
      output[1] = ((0x0f & input[1]) << 4) + ((0x3c & input[2]) >> 2);
      output[2] = ((0x03 & input[2]) << 6) + input[3];
      result[ii++] = (byte)output[0];
      if (len > 2) result[ii++] = (byte)output[1];
      if (len > 3) result[ii++] = (byte)output[2];
    }
    return result;
  }

}
