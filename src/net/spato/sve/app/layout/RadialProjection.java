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
 * Radial projection for tomogram view
 */
public class RadialProjection extends Projection {

  RadialProjection(int NN) { super(NN); }

  public void setPoint(int i, float r, float phi) {
    super.setPoint(i, -r*PApplet.cos(phi), -r*PApplet.sin(phi));
  }

  public void endData() {
    w = 2*PApplet.max(PApplet.abs(minx), PApplet.abs(maxx));
    h = 2*PApplet.max(PApplet.abs(miny), PApplet.abs(maxy));
  }

}
