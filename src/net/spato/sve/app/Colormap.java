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

package net.spato.sve.app;

import processing.core.PApplet;
import processing.core.PGraphics;
import processing.xml.XMLElement;
import tGUI.TChoice;
import tGUI.TComponent;


// FIXME: This should be refactored such that different colormaps are different subclasses of Colormap

public class Colormap {

  protected SPaTo_Visual_Explorer app = null;

  private XMLElement xml = null;
  private int NDC = 20;  // number of colors in "discrete"
  private int colormap = 0;
  private boolean logscale = false;
  private float minval = 0, maxval = 1;

  public final String colormaps[] = { "default", "jet", "bluered", "grayredblue", "thresholded", "discrete" };

  public Colormap(SPaTo_Visual_Explorer app) { this(app, null); }
  public Colormap(SPaTo_Visual_Explorer app, XMLElement xml) { this(app, xml, 0, 1); }
  public Colormap(SPaTo_Visual_Explorer app, XMLElement xml, float defaultMin, float defaultMax) {
    this(app,
         xml == null ? "default"  : xml.getString("name", "default"),
         xml == null ? false      : xml.getBoolean("log"),
         xml == null ? defaultMin : xml.getFloat("minval", defaultMin),
         xml == null ? defaultMax : xml.getFloat("maxval", defaultMax));
    this.xml = xml;
  }
  public Colormap(SPaTo_Visual_Explorer app, String cm, boolean log) { this(app, cm, log, 0, 1); }
  public Colormap(SPaTo_Visual_Explorer app, String cm, boolean log, float minval, float maxval) {
    this.app = app;
    setColormap(cm);
    logscale = log;
    setBounds(minval, maxval);
  }

  public String getColormapName() { return colormaps[colormap]; }
  public void setColormap(String cm) {
    for (int i = 0; i < colormaps.length; i++)
      if (colormaps[i].equals(cm))
        colormap = i;
    if (xml != null)
      xml.setString("name", colormaps[colormap]);
  }

  public boolean isLogscale() { return logscale; }
  public void setLogscale(boolean log) { logscale = log; if (xml != null) xml.setBoolean("log", log); }

  public float getMinVal() { return minval; }
  public float getMaxVal() { return maxval; }
  public void setMinVal(float minval) { this.minval = minval; if (xml != null) xml.setFloat("minval", minval); }
  public void setMaxVal(float maxval) { this.maxval = maxval; if (xml != null) xml.setFloat("maxval", maxval);  }
  public void setBounds(float minval, float maxval) { setMinVal(minval); setMaxVal(maxval); }

  public int getColor(float val) { return getColor(val, minval, maxval); }
  public int getColor(float val, float minval, float maxval) {
    val = PApplet.constrain(val, minval, maxval);
    // discrete
    if (colormap == 5) return getColorDiscrete((int)val);
    // apply log() if requested
    if (logscale) {
      val = PApplet.log(val);
      minval = minval > 0 ? PApplet.log(minval) : Float.NaN;
      maxval = PApplet.log(maxval);
    }
    // thresholded
    if (colormap == 4) return getColorDiscrete((int)(val/(maxval/PApplet.parseFloat(NDC))));
    // continuous colormaps
    float v = Float.isNaN(minval) ? val/maxval : (val - minval)/(maxval - minval);
    if (Float.isNaN(v)) return app.color(0);
    // blue to red colormap
    if (colormap == 2) return app.color(255*v, 0, 255*(1-v));
    // gray to red to blue colormap
    if (colormap == 3) {
      if (v < 0.5f) return app.color(127 + 127*v/0.5f, 127 - 127*v/0.5f, 127 - 127*v/0.5f);  // gray to red
                    return app.color(255 - 255*(v - 0.5f)/0.5f, 0, 255*(v - 0.5f)/0.5f);  // red to blue
    }
    // jet colormap
    if (colormap == 1) {
      if (v < .125f) return app.color(0, 0, 128 + 127*v/.125f);
      if (v < .375f) return app.color(0, 255*(v-.125f)/.250f, 255);
      if (v < .625f) return app.color(255*(v-.375f)/.250f, 255, 255*(1 - (v-.375f)/.250f));
      if (v < .875f) return app.color(255, 255*(1 - (v-.625f)/.250f), 0);
                     return app.color(255 - 127*(v-.875f)/.125f, 0, 0);
    }
    // return color from default colormap
    if (v < .25f) return app.color(255, 255*4*v, 0);
    if (v < .5f)  return app.color(255*(1 - 4*(v-.25f)), 255, 0);
    if (v < .75f) return app.color(0, 255, 255*4*(v-.5f));
                  return app.color(0, 255*(1 - 4*(v-.75f)), 255);
  }

  public int getColorDiscrete(int v) {
    switch ((v-1) % 20) {
      case  2: return app.color(255,   0,   0);
      case  1: return app.color(191, 191,   0);
      case  0: return app.color(  0, 191,   0);
      case  5: return app.color(  0, 191, 191);
      case  4: return app.color(  0,   0, 255);
      case  3: return app.color(255,   0, 255);
      case  6: return app.color(255, 127,   0);
      case  7: return app.color(127, 127, 127);
      case  8: return app.color(127,   0, 255);
      case  9: return app.color(  0,   0,   0);

      case 12: return app.color(127,   0,   0);
      case 11: return app.color( 80,  80,   0);
      case 10: return app.color(  0,  80,   0);
      case 15: return app.color(  0,  80,  80);
      case 14: return app.color(  0,   0, 127);
      case 13: return app.color(127,   0, 127);
      case 16: return app.color(127,  63,   0);
      case 17: return app.color( 63,  63,  63);
      case 18: return app.color( 63,   0, 127);
      case 19: return app.color(191, 191, 191);
    }
    return app.color(0);  // should never happen...
  }


  class Renderer extends TChoice.StringRenderer {
    public TComponent.Dimension getPreferredSize(TChoice c, Object o, boolean inMenu) {
      TComponent.Dimension d = super.getPreferredSize(c, o, inMenu);
      d.width += 305; return d;
    }
    public void draw(TChoice c, PGraphics g, Object o, TComponent.Rectangle bounds, boolean inMenu) {
      bounds.x += 305; bounds.width -= 305;
      super.draw(c, g, o, bounds, inMenu);
      bounds.x -= 305; bounds.width += 305;
      int cm = colormap;  // save original colormap
      setColormap((String)o);
      g.noFill();
      for (int i = 0; i < 300; i++) {
        g.stroke(getColor((colormap == 5) ? i*NDC/300.f : i, 0, 300));
        g.line(bounds.x + i, bounds.y + .25f*bounds.height, bounds.x + i, bounds.y + .75f*bounds.height);
      }
      colormap = cm;  // restore original colormap
    }
  }

}
