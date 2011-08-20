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


public class ScalingFactory {

  public static final String[] productNames = { "id", "sqrt", "log" };

  public static boolean canProduce(String name) {
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }

  public static String getDefaultProduct() { return productNames[0]; }

  public static Scaling produce(String name, int N) {
    if (name.equals("id")) return new IdScaling(N);
    if (name.equals("sqrt")) return new SqrtScaling(N);
    if (name.equals("log")) return new LogScaling(N);
    return null;
  }

}

