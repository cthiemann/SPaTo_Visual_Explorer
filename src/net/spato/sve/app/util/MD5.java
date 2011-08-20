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

import java.io.File;
import java.io.FileInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;


public class MD5 {
  public static String digest(String file) {
    if (!new File(file).exists()) return null;
    try {
      MessageDigest md5 = MessageDigest.getInstance("md5");
      DigestInputStream dis = new DigestInputStream(new FileInputStream(file), md5);
      byte buf[] = new byte[8*1024];
      while (dis.read(buf, 0, buf.length) > 0) /* do nothing while md5 digests as data streams in */;
      dis.close();
      return String.format("%032x", new java.math.BigInteger(1, md5.digest()));
    } catch (Exception e) {
      throw new RuntimeException("failed to calculate MD5 checksum of " + file, e);
    }
  }
}

