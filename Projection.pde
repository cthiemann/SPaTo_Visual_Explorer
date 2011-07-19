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

static abstract class Projection {
  int N = 0;
  float x[] = null, y[] = null;
  float minx, maxx, miny, maxy;
  float cx, cy, w, h, sx, sy;  // center, and width and height of the used space, and scaling

  Projection() {}
  Projection(int N) { this.N = N; x = new float[N]; y = new float[N]; }

  void setScalingToFitWithin(float width, float height) {
    // fit within dimensions with preserving aspect ratio of the data
    sx = sy = min((w == 0) ? 1e-5 : width/w, (h == 0) ? 1e-5 : height/h);
  }

  void beginData() {
    minx = miny = Float.POSITIVE_INFINITY;
    maxx = maxy = Float.NEGATIVE_INFINITY;
    cx = cy = w = h = 0;
    sx = sy = 1;
  }

  void setPoint(int i, float x, float y) {
    this.x[i] = x;
    this.y[i] = y;
    if (!Float.isInfinite(x) && !Float.isInfinite(y)) {
      minx = min(minx, x);
      maxx = max(maxx, x);
      miny = min(miny, y);
      maxy = max(maxy, y);
    }
  }

  void endData() {
    cx = (minx + maxx)/2;
    cy = (miny + maxy)/2;
    w = maxx - minx;
    h = maxy - miny;
  }

  void setPoints(float x[], float y[]) {
    beginData();
    for (int i = 0; i < N; i++)
      setPoint(i, x[i], y[i]);
    endData();
  }
}

static class MapProjectionFactory {
  static String[] productNames = { "LonLat", "LonLat Roll", "Albers" };

  static boolean canProduce(String name) {
    if (name == null) return false;
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }
  static String getDefaultProduct() { return productNames[0]; }

  static Projection produce(String name, int N) {
    if (name == null) return null;
    if (name.equals("LonLat")) return new LonLatProjection(N);
    if (name.equals("LonLat Roll")) return new LonLatProjection(N, true);
    if (name.equals("Albers")) return new AlbersProjection(N);
    return null;
  }
}

static class TomProjectionFactory {
  static String[] productNames = { "linear", "radial" };

  static boolean canProduce(String name) {
    if (name == null) return false;
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }
  static String getDefaultProduct() { return productNames[0]; }

  static Projection produce(String name, int N) {
    if (name == null) return null;
    if (name.equals("linear")) return new LinearProjection(N);
    if (name.equals("radial")) return new RadialProjection(N);
    return null;
  }
}

/**********************************************************************
 * Projections used with tomogram layouts
 **********************************************************************/

// Linear projection
static class LinearProjection extends Projection {
  LinearProjection(int NN) { super(NN); }
  void setPoint(int i, float r, float phi) { super.setPoint(i, -(phi - PI), -r); }
  void setScalingToFitWithin(float width, float height) {
    // aspect ratio is not important in LinearProjection
    sx = (w == 0) ? 1e-5 : width/w; sy = (h == 0) ? 1e-5 : height/h; }
}

// Polar coordinates to cartesian
static class RadialProjection extends Projection {
  RadialProjection(int NN) { super(NN); }
  void setPoint(int i, float r, float phi) { super.setPoint(i, -r*cos(phi), -r*sin(phi)); }
  void endData() { w = 2*max(abs(minx), abs(maxx)); h = 2*max(abs(miny), abs(maxy)); }
}

/**********************************************************************
 * Projections used with geographic maps
 **********************************************************************/

// Flat map projection
static class LonLatProjection extends Projection {
  boolean complete = false;  // whether the projection will always scale such that the whole longitude spectrum is shown
  LonLatProjection(int NN) { this(NN, false); }
  LonLatProjection(int NN, boolean complete) { super(NN); this.complete = complete; }
  void setPoint(int i, float lat, float lon) { super.setPoint(i, lon, -lat); }
  void endData() { super.endData(); if (complete) { cx = 0; w = 360; } }
}

// Albers projection
static class AlbersProjection extends Projection {
  // float phi0 = 38*PI/180, lam0 = -100*PI/180, phi1 = 23*PI/180, phi2 = 50*PI/180;  // for the continental US
  float lat[] = null, lon[] = null;  // temp arrays (need all lat/lon data to determine Albers parameters)

  AlbersProjection(int NN) { super(NN); }

  void beginData() { super.beginData(); lat = new float[N]; lon = new float[N]; }

  // collect points in temporary arrays
  void setPoint(int i, float lat, float lon) { this.lat[i] = lat; this.lon[i] = lon; }

  // determine parameters and project the points
  void endData() {
    float minlat = min(lat), maxlat = max(lat), dlat = maxlat - minlat;
    float minlon = min(lon), maxlon = max(lon);
    float phi0 = (minlat + maxlat)/2*PI/180, lam0 = (minlon + maxlon)/2*PI/180;  // projection origin
    float phi1 = (minlat + dlat/6)*PI/180, phi2 = (maxlat - dlat/6)*PI/180;  // standard parallels
    float n = 0.5*(sin(phi1) + sin(phi2));
    float C = cos(phi1)*cos(phi1) + 2*n*sin(phi1);
    float rho0 = sqrt(C - 2*n*sin(phi0))/n;
    float rho, theta;
    for (int i = 0; i < N; i++) {
      rho = sqrt(C - 2*n*sin(lat[i]*PI/180))/n;
      theta = n*(lon[i]*PI/180 - lam0);
      super.setPoint(i, rho*sin(theta), -(rho0 - rho*cos(theta)));
    }
    super.endData();
    lat = null; lon = null;
  }
}



