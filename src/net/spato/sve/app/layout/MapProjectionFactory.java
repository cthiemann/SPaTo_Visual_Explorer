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

package net.spato.sve.app.layout;


public class MapProjectionFactory {

  public static final String[] productNames = { "LonLat", "LonLat Roll", "Albers" };

  public static boolean canProduce(String name) {
    if (name == null) return false;
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }
  public static String getDefaultProduct() { return productNames[0]; }

  public static Projection produce(String name, int N) {
    if (name == null) return null;
    if (name.equals("LonLat")) return new LonLatProjection(N);
    if (name.equals("LonLat Roll")) return new LonLatProjection(N, true);
    if (name.equals("Albers")) return new AlbersProjection(N);
    return null;
  }

}

