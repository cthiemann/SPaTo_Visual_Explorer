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

import java.util.Vector;
import java.util.Collections;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

HashMap<String,Object> layoutCache = new HashMap<String,Object>();  // FIXME: layout handling is still screwed up...

class Layout {
  Projection proj;
  Scaling scal;
  String projection, scaling, order;
  int NN = -1;
  int pred[][];  // input data
  float phi[][];  // posphi[r][i] is angular position of node i if r is root
  int r = -1;  // currently loaded slice

  class Cache {
    Vector children[];
    int numTotalChildren[];
    float sortValue[];

    Cache(int r, float val[], boolean sortRecursively) {
      children = new Vector[NN];
      for (int i = 0; i < NN; i++) children[i] = new Vector();
      numTotalChildren = new int[NN];
      if (val != null) sortValue = new float[NN];
      // setup children vector
      for (int i = 0; i < NN; i++)
        if (pred[r][i] >= 0)
          children[pred[r][i]].add(new Integer(i));  // i is a child of s.pred[i] (by definition)
      // setup numTotalChildren and sortValue vectors
      setupRecursively(r, val, sortRecursively);
      // sort children vector
      if (val != null)
        for (int i = 0; i < NN; i++)
          Collections.sort(children[i], new Comparator() { int compare(Object o1, Object o2) {
            float v1 = sortValue[((Integer)o1).intValue()], v2 = sortValue[((Integer)o2).intValue()];
            return (v1 < v2) ? -1 : (v1 > v2) ? +1 : 0; } });
    }

    void setupRecursively(int j, float val[], boolean sortRecursively) {
      numTotalChildren[j] = children[j].size();
      if (val != null) sortValue[j] = val[j];
      float sortTmp = 0;
      for (int ii = 0; ii < children[j].size(); ii++) {
        int i = ((Integer)children[j].get(ii)).intValue();
        setupRecursively(i, val, sortRecursively);
        numTotalChildren[j] += numTotalChildren[i];
        if (sortRecursively) sortTmp += sortValue[i];
      }
      if (sortRecursively) sortValue[j] += sortTmp/10000;
    }
  }

  Layout(int[][] pred, String spec) {
    this.pred = pred;
    NN = pred.length;
    parseSpecification(spec);
    setupProjection(projection);
    setupScaling(scaling);
    String hash = spec + "##" + pred.toString();
    if (!layoutCache.containsKey(hash)) {
      phi = new float[NN][NN];
      float sortData[] = getSortData();
      calculateLayout(sortData);
      layoutCache.put(hash, phi);
    }
    phi = (float[][])layoutCache.get(hash);
  }

  String getSpecification() { return projection + "_" + scaling + (!order.equals("unsorted") ? "__" + order : ""); }

  void setupProjection(String projection) {
    if (!TomProjectionFactory.canProduce(projection)) {
      console.logWarning("Unknown tomogram projection " + projection + ", using default");
      projection = TomProjectionFactory.getDefaultProduct();
    }
    proj = TomProjectionFactory.produce(projection, NN);
    this.projection = projection;
  }

  void setupScaling(String scaling) { setupScaling(scaling, 1); }
  void setupScaling(String scaling, float x0) {
    if (!ScalingFactory.canProduce(scaling)) {
      console.logWarning("Unknown scaling " + scaling + ", using default");
      scaling = ScalingFactory.getDefaultProduct();
    }
    scal = ScalingFactory.produce(scaling, NN);
    if (scaling.equals("log")) ((LogScaling)scal).x0 = x0;
    this.scaling = scaling;
  }

  void updateProjection(int r, float D[][]) { proj.setPoints(scal.f(D[r]), phi[r]); this.r = r; }

  void parseSpecification(String spec) {
    order = "unsorted";
    String pieces[] = split(spec, "__");
    if (pieces.length > 1) order = pieces[1];
    pieces = split(pieces[0], '_');
    projection = pieces[0];
    scaling = (pieces.length > 1) ? pieces[1] : "id";
  }

  float[] getSortData() {
    return null;  // FIXME: re-implement this feature...
    /*if (order.equals("unsorted")) return null;
    try {
      String pieces[] = split(order, '_');
      SVE2View.Dataset ds = null;
      for (int d = 0; d < view.ND; d++)
        if (view.data[d].xml.getString("id").equals(pieces[0]))
          ds = view.data[d];
      if (ds == null) throw new Exception("Dataset " + pieces[0] + " not found");
      if (pieces.length < 2) throw new Exception("Quantity not specified");
      SVE2View.Data data = null;
      for (int q = 0; q < ds.NQ; q++)
        if (ds.data[q].xml.getString("id").equals(pieces[1]))
          data = ds.data[q];
      return data.data;
    } catch (Exception e) {
      console.logWarning("" + e.getMessage() + ", defaulting to unsorted layout");
      order = "unsorted";
      return null;
    }*/
  }

  void calculateLayout(float sortData[]) {
    console.logProgress("Calculating layout " + getSpecification());
    for (int r = 0; r < NN; r++) {
      Cache cache = new Cache(r, sortData, order.endsWith("_r"));
      phi[r][r] = PI;  // we do this to move the root in the middle in LinearProjection
      calculateLayoutRecursively(cache, r, r, 0, 0, 2*PI);
      console.updateProgress(r, NN);
    }
    console.finishProgress();
  }

  // cache, root node, current layout node, tree depth of node j, min and max angles
  void calculateLayoutRecursively(Cache cache, int r, int j, int d, float phimin, float phimax) {
    int sumNTC = cache.numTotalChildren[j], cumNTC = 0;  // sum of total children, cumulative sum
    Vector I = cache.children[j];
    for (int ii = 0; ii < I.size(); ii++) {
      int i = ((Integer)I.get(ii)).intValue();
      float iphimin = phimin + (phimax - phimin)*cumNTC/sumNTC;
      cumNTC += cache.numTotalChildren[i] + 1;  // sum of i's children plus i itself
      float iphimax = phimin + (phimax - phimin)*cumNTC/sumNTC;
      phi[r][i] = (iphimin + iphimax)/2;
      //doc.slices[r].d[i] = d;
      calculateLayoutRecursively(cache, r, i, d + 1, iphimin, iphimax);
    }
  }
}


