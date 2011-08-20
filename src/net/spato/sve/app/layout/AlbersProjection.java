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


/**
 * Albers projection for map view
 */
public class AlbersProjection extends Projection {

  private float lat[] = null, lon[] = null;  // temp arrays (need all lat/lon data to determine Albers parameters)

  public AlbersProjection(int NN) { super(NN); }

  public void beginData() {
    super.beginData();
    lat = new float[N];
    lon = new float[N];
  }

  // collect points in temporary arrays
  public void setPoint(int i, float lat, float lon) {
    this.lat[i] = lat;
    this.lon[i] = lon;
  }

  // determine parameters and project the points
  public void endData() {
    float minlat = PApplet.min(lat), maxlat = PApplet.max(lat), dlat = maxlat - minlat;
    float minlon = PApplet.min(lon), maxlon = PApplet.max(lon);
    float phi0 = (minlat + maxlat)/2*PApplet.PI/180, lam0 = (minlon + maxlon)/2*PApplet.PI/180;  // projection origin
    float phi1 = (minlat + dlat/6)*PApplet.PI/180, phi2 = (maxlat - dlat/6)*PApplet.PI/180;  // standard parallels
    float n = 0.5f*(PApplet.sin(phi1) + PApplet.sin(phi2));
    float C = PApplet.cos(phi1)*PApplet.cos(phi1) + 2*n*PApplet.sin(phi1);
    float rho0 = PApplet.sqrt(C - 2*n*PApplet.sin(phi0))/n;
    float rho, theta;
    for (int i = 0; i < N; i++) {
      rho = PApplet.sqrt(C - 2*n*PApplet.sin(lat[i]*PApplet.PI/180))/n;
      theta = n*(lon[i]*PApplet.PI/180 - lam0);
      super.setPoint(i, rho*PApplet.sin(theta), -(rho0 - rho*PApplet.cos(theta)));
    }
    super.endData();
    lat = null; lon = null;
  }
}
