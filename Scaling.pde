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

static abstract class Scaling {
  int N = 0;
  float sx[];  // scaled values
  Scaling(int N) { this.N = N; sx = new float[N]; }
  abstract float[] f(float[] x);
}

static class ScalingFactory {
  static String[] productNames = { "id", "sqrt", "log" };

  static boolean canProduce(String name) {
    for (int i = 0; i < productNames.length; i++)
      if (name.equals(productNames[i]))
        return true;
    return false;
  }
  static String getDefaultProduct() { return productNames[0]; }

  static Scaling produce(String name, int N) {
    if (name.equals("id")) return new IdScaling(N);
    if (name.equals("sqrt")) return new SqrtScaling(N);
    if (name.equals("log")) return new LogScaling(N);
    return null;
  }
}

static class IdScaling extends Scaling {
  IdScaling(int N) { super(N); }
  float[] f(float[] x) { for (int i = 0; i < N; i++) sx[i] = x[i]; return sx; }
}

static class SqrtScaling extends Scaling {
  SqrtScaling(int N) { super(N); }
  float[] f(float[] x) { for (int i = 0; i < N; i++) sx[i] = sqrt(x[i]); return sx; }
}

static class LogScaling extends Scaling {
  float x0 = 1;
  LogScaling(int N) { super(N); }
  LogScaling(int N, float x0) { super(N); this.x0 = x0; }
  float[] f(float[] x) {
    for (int i = 0; i < N; i++)
      sx[i] = (x[i] == 0) ? 0 : log(x[i]/x0);
    return sx;
  }
}