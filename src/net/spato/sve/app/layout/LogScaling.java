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

import processing.core.PApplet;


public class LogScaling extends Scaling {
  float x0 = 1;
  LogScaling(int N) { super(N); }
  LogScaling(int N, float x0) { super(N); this.x0 = x0; }
  public float[] f(float[] x) {
    for (int i = 0; i < N; i++)
      sx[i] = (x[i] == 0) ? 0 : PApplet.log(x[i]/x0);  // FIXME: is 0 really a good default value?
    return sx;
  }
}
