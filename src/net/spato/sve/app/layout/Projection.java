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


public abstract class Projection {

  public int N = 0;
  public float x[] = null, y[] = null;
  public float minx, maxx, miny, maxy;
  public float cx, cy, w, h, sx, sy;  // center, and width and height of the used space, and scaling

  public Projection() {}
  public Projection(int N) { this.N = N; x = new float[N]; y = new float[N]; }

  public void setScalingToFitWithin(float width, float height) {
    // fit within dimensions with preserving aspect ratio of the data
    sx = sy = PApplet.min((w == 0) ? 1e-5f : width/w, (h == 0) ? 1e-5f : height/h);
  }

  public void beginData() {
    minx = miny = Float.POSITIVE_INFINITY;
    maxx = maxy = Float.NEGATIVE_INFINITY;
    cx = cy = w = h = 0;
    sx = sy = 1;
  }

  public void setPoint(int i, float x, float y) {
    this.x[i] = x;
    this.y[i] = y;
    if (!Float.isInfinite(x) && !Float.isInfinite(y)) {
      minx = PApplet.min(minx, x);
      maxx = PApplet.max(maxx, x);
      miny = PApplet.min(miny, y);
      maxy = PApplet.max(maxy, y);
    }
  }

  public void endData() {
    cx = (minx + maxx)/2;
    cy = (miny + maxy)/2;
    w = maxx - minx;
    h = maxy - miny;
  }

  public void setPoints(float x[], float y[]) {
    beginData();
    for (int i = 0; i < N; i++)
      setPoint(i, x[i], y[i]);
    endData();
  }
}
