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


/**
 * Flat lon/lat projection for map view
 */
public class LonLatProjection extends Projection {

  private boolean complete = false;  // whether the projection will always scale such that the whole longitude spectrum is shown

  public LonLatProjection(int NN) { this(NN, false); }

  public LonLatProjection(int NN, boolean complete) { super(NN); this.complete = complete; }

  public void setPoint(int i, float lat, float lon) {
    super.setPoint(i, lon, -lat);
  }

  public void endData() {
    super.endData();
    if (complete) { cx = 0; w = 360; }
  }

}

