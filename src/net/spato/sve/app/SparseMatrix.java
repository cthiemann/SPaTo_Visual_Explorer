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

import java.lang.reflect.Array;

import processing.core.PApplet;


public class SparseMatrix {

  // FIXME: implement set/get methods
  public int N;  // FIXME: should this be generalized to hold non-square sparse matrices as well?
  public int index[][];
  public float value[][];

  public SparseMatrix(int N) {
    this.N = N;
    index = new int[N][]; value = new float[N][];
    for (int i = 0; i < N; i++) { index[i] = new int[0]; value[i] = new float[0]; }
  }

  public SparseMatrix(float data[][]) {
    this(data.length);
    for (int i = 0; i < N; i++) {
      for (int j = 0; j < N; j++) {
        if (data[i][j] == 0) continue;
        index[i] = PApplet.append(index[i], j);
        value[i] = PApplet.append(value[i], data[i][j]);
      }
    }
  }

  public SparseMatrix(int N, int ii[], int jj[]) { this(N, ii, jj, null); }
  public SparseMatrix(int N, int ii[], int jj[], float val[]) {
    this(N);
    for (int l = 0; l < ii.length; l++) {
      index[ii[l]] = PApplet.append(index[ii[l]], jj[l]);
      value[ii[l]] = PApplet.append(value[ii[l]], (val != null) ? val[l] : 1f);
    }
  }

  public SparseMatrix(int ii[], int jj[]) { this(ii, jj, null); }
  public SparseMatrix(int ii[], int jj[], float val[]) {
    N = 0;
    for (int l = 0; l < ii.length; l++)
      N = PApplet.max(N, PApplet.max(ii[l], jj[l]) + 1);
    index = new int[N][]; value = new float[N][];
    for (int i = 0; i < N; i++) {
      index[i] = new int[0];
      value[i] = new float[0];
    }
    for (int l = 0; l < ii.length; l++) {
      index[ii[l]] = PApplet.append(index[ii[l]], jj[l]);
      value[ii[l]] = PApplet.append(value[ii[l]], (val != null) ? val[l] : 1f);
    }
  }

  public float[][] getFullMatrix() {
    float result[][] = (float[][])Array.newInstance(Float.TYPE, new int[] { N, N });
    for (int i = 0; i < N; i++)
      for (int j = 0; j < index[i].length; j++)
        result[i][index[i][j]] = value[i][j];
    return result;
  }
}
