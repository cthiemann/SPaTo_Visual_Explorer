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

// FIXME: This should be refactored such that different colormaps are different subclasses of Colormap

class Colormap {
  XMLElement xml = null;
  int NDC = 20;  // number of colors in "discrete"
  int colormap = 0;
  boolean logscale = false;
  float minval = 0, maxval = 1;
  
  String colormaps[] = { "default", "jet", "bluered", "grayredblue", "thresholded", "discrete" };
  
  Colormap() { this(null); }
  Colormap(XMLElement xml) { this(xml, 0, 1); }
  Colormap(XMLElement xml, float defaultMin, float defaultMax) {
    this(xml == null ? "default"  : xml.getString("name", "default"),
         xml == null ? false      : xml.getBoolean("log"),
         xml == null ? defaultMin : xml.getFloat("minval", defaultMin),
         xml == null ? defaultMax : xml.getFloat("maxval", defaultMax));
    this.xml = xml;
  }
  Colormap(String cm, boolean log) { this(cm, log, 0, 1); }
  Colormap(String cm, boolean log, float minval, float maxval) {
    setColormap(cm); logscale = log; setBounds(minval, maxval); }
  
  String getColormapName() { return colormaps[colormap]; }
  void setColormap(String cm) {
    for (int i = 0; i < colormaps.length; i++)
      if (colormaps[i].equals(cm))
        colormap = i;
    if (xml != null) 
      xml.setString("name", colormaps[colormap]);
  }
  
  boolean isLogscale() { return logscale; }
  void setLogscale(boolean log) { logscale = log; if (xml != null) xml.setBoolean("log", log); }
  
  float getMinVal() { return minval; }
  float getMaxVal() { return maxval; }
  void setMinVal(float minval) { this.minval = minval; if (xml != null) xml.setFloat("minval", minval); }
  void setMaxVal(float maxval) { this.maxval = maxval; if (xml != null) xml.setFloat("maxval", maxval);  }
  void setBounds(float minval, float maxval) { setMinVal(minval); setMaxVal(maxval); }
  
  color getColor(float val) { return getColor(val, minval, maxval); }
  color getColor(float val, float minval, float maxval) {
    val = constrain(val, minval, maxval);
    // discrete
    if (colormap == 5) return getColorDiscrete((int)val);
    // apply log() if requested
    if (logscale) { val = log(val); minval = minval > 0 ? log(minval) : Float.NaN; maxval = log(maxval); }
    // thresholded
    if (colormap == 4) return getColorDiscrete((int)(val/(maxval/float(NDC))));
    // continuous colormaps
    float v = Float.isNaN(minval) ? val/maxval : (val - minval)/(maxval - minval);
    if (Float.isNaN(v)) return color(0);
    // blue to red colormap
    if (colormap == 2) return color(255*v, 0, 255*(1-v));
    // gray to red to blue colormap
    if (colormap == 3) {
      if (v < 0.5) return color(127 + 127*v/0.5, 127 - 127*v/0.5, 127 - 127*v/0.5);  // gray to red
                   return color(255 - 255*(v - 0.5)/0.5, 0, 255*(v - 0.5)/0.5);  // red to blue
    }
    // jet colormap
    if (colormap == 1) {
      if (v < .125) return color(0, 0, 128 + 127*v/.125);
      if (v < .375) return color(0, 255*(v-.125)/.250, 255);
      if (v < .625) return color(255*(v-.375)/.250, 255, 255*(1 - (v-.375)/.250));
      if (v < .875) return color(255, 255*(1 - (v-.625)/.250), 0);
                    return color(255 - 127*(v-.875)/.125, 0, 0);
    }
    // return color from default colormap
    if (v < .25) return color(255, 255*4*v, 0);
    if (v < .5)  return color(255*(1 - 4*(v-.25)), 255, 0);
    if (v < .75) return color(0, 255, 255*4*(v-.5));
                 return color(0, 255*(1 - 4*(v-.75)), 255);
  }

  color getColorDiscrete(int v) {
    switch ((v-1) % 20) {
      case  2: return color(255,   0,   0);
      case  1: return color(191, 191,   0);
      case  0: return color(  0, 191,   0);
      case  5: return color(  0, 191, 191);
      case  4: return color(  0,   0, 255);
      case  3: return color(255,   0, 255);
      case  6: return color(255, 127,   0);
      case  7: return color(127, 127, 127);
      case  8: return color(127,   0, 255);
      case  9: return color(  0,   0,   0);
  
      case 12: return color(127,   0,   0);
      case 11: return color( 80,  80,   0);
      case 10: return color(  0,  80,   0);
      case 15: return color(  0,  80,  80);
      case 14: return color(  0,   0, 127);
      case 13: return color(127,   0, 127);
      case 16: return color(127,  63,   0);
      case 17: return color( 63,  63,  63);
      case 18: return color( 63,   0, 127);
      case 19: return color(191, 191, 191);
    }
    return color(0);  // should never happen...
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