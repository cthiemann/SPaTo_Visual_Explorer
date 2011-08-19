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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Arrays;
import processing.core.PApplet;
import processing.xml.XMLElement;
import tGUI.TConsole;


public class BinaryThing {

  public static final Class supportedTypes[] = { Integer.TYPE, Float.TYPE, SparseMatrix.class };

  protected Class type = null;
  protected int size[] = null;
  protected Object blob = null;

  public BinaryThing() {}
  public BinaryThing(Object blob) { setBlob(blob); }

  public Class getType() { return type; }
  public int[] getSize() { return size; }
  public int getSize(int i) { return ((size != null) && (i >= 0) && (i < size.length)) ? size[i] : 0; }
  public boolean is(Class type, int size[]) { return (blob != null) && (this.type == type) && Arrays.equals(this.size, size); }
  public boolean isInt2(int NN) { return is(Integer.TYPE, new int[] { NN, NN }); }
  public boolean isFloat1(int NN) { return is(Float.TYPE, new int[] { 1, NN }); }
  public boolean isFloat2(int NN) { return is(Float.TYPE, new int[] { NN, NN }); }
  public boolean isSparse(int NN) { return (type == SparseMatrix.class) && (getSparseMatrix().N == NN); }

  public Object getBlob() { return blob; }
  public int[][] getIntArray() { return (blob != null) && (type == Integer.TYPE) && (size.length == 2) ? (int[][])blob : null; }
  public float[][] getFloatArray() { return (blob != null) && (type == Float.TYPE) && (size.length == 2) ? (float[][])blob : null; }
  public SparseMatrix getSparseMatrix() { return (blob instanceof SparseMatrix) ? (SparseMatrix)blob : null; }

  public void setBlob(Object blob) {
    this.type = null;
    this.size = null;
    this.blob = blob;
    if (blob == null) return;
    type = blob.getClass();
    size = new int[0];
    while (type.isArray()) {
      type = type.getComponentType();
      size = PApplet.append(size, (blob == null) ? 0 : Array.getLength(blob));
      blob = (Array.getLength(blob) > 0) ? Array.get(blob, 0) : null;  // we've saved the original blob to this.blob already
      // FIXME: bad things would happen if the sub-arrays are not all of the same length, but I am too lazy to check here...
    }
  }

  public static BinaryThing parseFromXML(XMLElement xml) throws Exception { return parseFromXML(xml, null); }
  public static BinaryThing parseFromXML(XMLElement xml, TConsole.Message msg) throws Exception {
    if (xml == null) return null;
    BinaryThing res = new BinaryThing();
    res.type = null; res.size = null; res.blob = null;
    XMLElement tmp[] = null;  // will hold the slice/values/source children
    XMLElement xmlp = xml;  // the first non-snapshot ancestor
    while (xmlp.getName().equals("snapshot"))
      if ((xmlp = xmlp.getParent()) == null)
        throw new Exception("choked on orphaned snapshot");
    // determine type
    if (xmlp.getName().equals("slices")) {
      res.type = Integer.TYPE;
      tmp = xml.getChildren("slice");
    } else if (xmlp.getName().equals("data")) {
      res.type = Float.TYPE;
      tmp = xml.getChildren("values");
    } else if (xmlp.getName().equals("links")) {
      res.type = SparseMatrix.class;
      tmp = xml.getChildren("source");
    } else
      throw new Exception("unsupported XML element \u2018" + xmlp.getName() + "\u2019");
    if (((tmp == null) || (tmp.length == 0)) && (res.type != SparseMatrix.class))
      throw new Exception("no values found");
    // determine size and create blob
    int N = tmp.length, NN = 0;
    if (res.type == SparseMatrix.class) {
      N = xml.getInt("size");
      for (int i = 0; i < tmp.length; i++) {
        N = PApplet.max(N, tmp[i].getInt("index"));
        XMLElement tmp2[] = tmp[i].getChildren("target");
        for (int j = 0; j < tmp2.length; j++)
          N = PApplet.max(N, tmp2[j].getInt("index", 0));
        if (msg != null) msg.updateProgress(i, 2*N);
      }
      res.blob = new SparseMatrix(N);
    } else {
      if (tmp[0].getContent() == null)
        throw new Exception("no values in first " + tmp[0].getName() + " element");
      NN = PApplet.splitTokens(PApplet.trim(tmp[0].getContent())).length;
      if (!((N == NN) || (N == 1)))
        throw new Exception("wrong number of " + tmp[0].getName() + " elements");
      res.size = new int[] { N, NN };
      res.blob = Array.newInstance(res.type, res.size);
    }
    // parse values
    boolean filled[] = new boolean[N];  // track which indices have been filled already
    String indexAttr = (res.type == SparseMatrix.class) ? "index" : "root";
    for (int i = 0; i < N; i++) {
      String inElemStr = " in " + tmp[i].getName() + " element " + (i+1);
      int ii = (N == 1) ? 0 : tmp[i].getInt(indexAttr, 0) - 1;
      if ((ii < 0) || (ii >= N))
        throw new Exception("invalid " + indexAttr + " " + tmp[i].getString(indexAttr) + inElemStr);
      if (filled[ii])
        throw new Exception("duplicate " + indexAttr + " value" + inElemStr);
      if (res.type == SparseMatrix.class) {
        SparseMatrix matrix = (SparseMatrix)res.blob;
        XMLElement tmp2[] = tmp[i].getChildren("target");
        matrix.index[ii] = new int[tmp2.length];
        matrix.value[ii] = new float[tmp2.length];
        boolean filled2[] = new boolean[N];
        for (int j = 0; j < tmp2.length; j++) {
          String inElemStr2 = inElemStr + ", " + tmp2[j].getName() + " " + (j+1);
          int jj = matrix.index[ii][j] = tmp2[j].getInt("index", 0) - 1;
          if ((jj < 0) || (jj >= N))
            throw new Exception("invalid index " + tmp2[j].getString("index") + inElemStr2);
          if (filled2[jj])
            throw new Exception("duplicate index value" + inElemStr2);
          if (Float.isNaN(matrix.value[ii][j] = tmp2[j].getFloat("weight", Float.NaN)))
            throw new Exception("invalid weight value " + tmp[j].getString("weight") + inElemStr2);
        }
        if (msg != null) msg.updateProgress(i + N, 2*N);
      } else {
        if (tmp[i].getContent() == null)
          throw new Exception("no values" + inElemStr);
        Object row = parseLine(tmp[i].getContent(), ' ', res.type);
        if (Array.getLength(row) != NN)
          throw new Exception("wrong number of values" + inElemStr);
        Array.set(res.blob, ii, row);
        if (msg != null) msg.updateProgress(i, N);
      }
    }
    // post-process if necessary
    if (xml.getName().equals("slices")) {
      int pred[][] = (int[][])res.blob;
      for (int r = 0; r < NN; r++)
        for (int i = 0; i < NN; i++)
          pred[r][i]--;  // indices are 1-based in XML, but 0-based in binary
    }
    return res;
  }

  public static BinaryThing parseFromText(String lines[], char sep) throws Exception {
    return parseFromText(lines, sep, Float.TYPE, null); }
  public static BinaryThing parseFromText(String lines[], char sep, TConsole.Message msg) throws Exception {
    return parseFromText(lines, sep, Float.TYPE, msg); }
  public static BinaryThing parseFromText(String lines[], char sep, Class type) throws Exception {
    return parseFromText(lines, sep, type, null); }
  public static BinaryThing parseFromText(String lines[], char sep, Class type, TConsole.Message msg) throws Exception {
    if (type == SparseMatrix.class)
      return parseSparseFromText(lines, sep, msg);
    if ((lines == null) || (lines.length == 0) ||
        (lines[0] == null) || (Array.getLength(parseLine(lines[0], sep, type)) == 0))
      throw new Exception("no data to parse");
    BinaryThing res = new BinaryThing();
    res.type = type;
    res.size = new int[] { lines.length, Array.getLength(parseLine(lines[0], sep, type)) };
    res.blob = Array.newInstance(res.type, res.size);
    int i = -1;
    try {
      for (i = 0; i < res.size[0]; i++) {
        Object row = parseLine(lines[i], sep, res.type);
        if (Array.getLength(row) != res.size[1]) throw new Exception("wrong number of elements");
        Array.set(res.blob, i, row);
        if (msg != null) msg.updateProgress(i, res.size[0]);
      }
    } catch (Exception e) { throw new Exception("line " + (i+1) + ": " + e.getMessage()); }
    return res;
  }

  private static Object parseLine(String line, char sep, Class type) throws Exception {
    String pieces[] = (sep == ' ') ?
      PApplet.splitTokens(PApplet.trim(line), PApplet.WHITESPACE) : PApplet.split(line, sep);
    Object result = Array.newInstance(type, new int[] { pieces.length });
    int i = -1;
    try {
      for (i = 0; i < pieces.length; i++)
        if (type == Integer.TYPE) Array.set(result, i, Integer.valueOf(pieces[i]).intValue());
        else Array.set(result, i, Float.valueOf(pieces[i]).floatValue());
    } catch (Exception e) { throw new Exception("not a number: " + pieces[i]); }
    return result;
  }

  private static BinaryThing parseSparseFromText(String lines[], char sep, TConsole.Message msg) throws Exception {
    if ((lines == null) || (lines.length % 2 != 0))
      throw new Exception("no data or odd number of lines");
    SparseMatrix matrix = new SparseMatrix(lines.length/2);
    int i = -1;
    try {
      for (i = 0; i < lines.length/2; i++) {
        matrix.index[i] = (int[])parseLine(lines[2*i+0], sep, Integer.TYPE);
        for (int j = 0; j < matrix.index[i].length; j++)
          if (--matrix.index[i][j] < 0)  // indices are 1-based in text files
            throw new Exception("invalid index: " + (matrix.index[i][j]+1));
        matrix.value[i] = (float[])parseLine(lines[2*i+1], sep, Float.TYPE);
        if (matrix.index[i].length != matrix.value[i].length)
          throw new Exception("index/value length mismatch");
      }
    } catch (Exception e) { throw new Exception("row " + (i+1) + ": " + e.getMessage()); }
    return new BinaryThing(matrix);
  }

  public static BinaryThing loadFromStream(DataInputStream in) throws Exception { return loadFromStream(in, null); }
  public static BinaryThing loadFromStream(DataInputStream in, TConsole.Message msg) throws Exception {
    BinaryThing res = new BinaryThing();
    res.blob = null; res.type = null; res.size = null;
    // read type
    int tmp = in.readInt();
    if ((tmp < 0) || (tmp >= supportedTypes.length))
      throw new Exception("unknown data type");
    res.type = supportedTypes[tmp];
    // read array size
    if (res.type == SparseMatrix.class)
      res.blob = new SparseMatrix(in.readInt());
    else {
      res.size = new int[0];
      while ((tmp = in.readInt()) != -1)
        res.size = PApplet.append(res.size, tmp);
    }
    // read data
    if (res.type == SparseMatrix.class) {
      SparseMatrix matrix = res.getSparseMatrix();
      for (int i = 0; i < matrix.N; i++) {
        int M = in.readInt();
        matrix.index[i] = (int[])loadFromStream(in, new int[M]);
        matrix.value[i] = (float[])loadFromStream(in, new float[M]);
        if (msg != null) msg.updateProgress(i, matrix.N);
      }
    } else {
      res.blob = Array.newInstance(res.type, res.size);
      for (int i = 0; i < res.size[0]; i++) {
        Array.set(res.blob, i, loadFromStream(in, Array.get(res.blob, i)));
        if (msg != null) msg.updateProgress(i, res.size[0]);
      }
    }
    return res;
  }
  private static Object loadFromStream(DataInputStream in, Object o) throws Exception {
    // read single value if o is not an array
    if (!o.getClass().isArray()) {
      if (o.getClass().getComponentType() == Integer.TYPE) return in.readInt();
      if (o.getClass().getComponentType() == Float.TYPE) return in.readFloat();
      return null;
    }
    // fill 1-D array with values from stream
    Class type = o.getClass().getComponentType();
    if (!type.isArray()) {
      byte bytes[] = new byte[4*Array.getLength(o)];
      ByteBuffer bbuf = ByteBuffer.wrap(bytes);
      int off = 0, n;
      while (off < bytes.length)
        if ((n = in.read(bytes, off, bytes.length - off)) == -1)
          throw new IOException("unexpected end-of-file");
        else off += n;
      if (type == Integer.TYPE) bbuf.asIntBuffer().get((int[])o);
      else if (type == Float.TYPE) bbuf.asFloatBuffer().get((float[])o);
      return o;
    }
    // recursively fill multi-dim array
    for (int i = 0; i < Array.getLength(o); i++)
      Array.set(o, i, loadFromStream(in, Array.get(o, i)));
    return o;
  }

  public void saveToStream(DataOutputStream out) throws Exception { saveToStream(out, null); }
  public void saveToStream(DataOutputStream out, TConsole.Message msg) throws Exception {
    if ((type == null) || (blob == null)) return;
    // write type
    int typeID = -1;
    for (int i = 0; i < supportedTypes.length; i++)
      if (type == supportedTypes[i])
        typeID = i;
    if (typeID == -1)
      throw new Exception("unsupported data type");
    out.writeInt(typeID);
    // write size
    if (blob instanceof SparseMatrix)
      out.writeInt(getSparseMatrix().N);
    else {
      for (int i = 0; i < size.length; i++)
        out.writeInt(size[i]);
      out.writeInt(-1);  // array size terminator
    }
    // write data
    if (blob instanceof SparseMatrix) {
      SparseMatrix matrix = getSparseMatrix();
      for (int i = 0; i < matrix.N; i++) {
        if (matrix.index[i].length != matrix.value[i].length)
          throw new Exception("index/value length mismatch at row " + (i+1));
        out.writeInt(matrix.index[i].length);
        saveToStream(out, matrix.index[i]);
        saveToStream(out, matrix.value[i]);
        if (msg != null) msg.updateProgress(i, matrix.N);
      }
    } else {
      for (int i = 0; i < size[0]; i++) {
        saveToStream(out, Array.get(blob, i));
        if (msg != null) msg.updateProgress(i, size[0]);
      }
    }
  }
  private void saveToStream(DataOutputStream out, Object o) throws Exception {
    Class type = null;
    if (!(type = o.getClass()).isArray()) {  // write single value
      if (type == Integer.TYPE) out.writeInt(((Integer)o).intValue());
      else if (type == Float.TYPE) out.writeFloat(((Float)o).floatValue());
    } else if (!(type = o.getClass().getComponentType()).isArray()) {  // write 1-D array
      byte bytes[] = new byte[4*Array.getLength(o)];
      ByteBuffer bbuf = ByteBuffer.wrap(bytes);
      Buffer buf = (type == Integer.TYPE) ? bbuf.asIntBuffer() :
                   (type == Float.TYPE) ? bbuf.asFloatBuffer() : null;
      if (type == Integer.TYPE) ((IntBuffer)buf).put((int[])o);
      else if (type == Float.TYPE) ((FloatBuffer)buf).put((float[])o);
      out.write(bytes, 0, bytes.length);
    } else {  // recursively write multi-dim array
      for (int i = 0; i < Array.getLength(o); i++)
        saveToStream(out, Array.get(o, i));
    }
  }

  /* If size.length == 2, this will replace the blob with a new array of size { size[1] size[0] },
   * such that newblob[i][j] == oldblob[j][i].  If size.length != 2, nothing happens. FIXME: throw exception? */
  public void transpose() {
    if ((type == null) || (blob == null)) return;
    if (blob instanceof SparseMatrix) {
      BinaryThing bt = new BinaryThing(((SparseMatrix)blob).getFullMatrix());
      bt.transpose();
      blob = new SparseMatrix(bt.getFloatArray());
    } else {
      if ((size == null) || (size.length != 2)) return;
      Object oldblob = blob;
      size = new int[] { size[1], size[0] };
      blob = Array.newInstance(type, size);
      for (int i = 0; i < size[0]; i++)
        for (int j = 0; j < size[1]; j++)
          Array.set(Array.get(blob, i), j,
            Array.get(Array.get(oldblob, j), i));
    }
  }

  /* Reshapes the current data to fit the new size if prod(newsize) == prod(size).  It does so by
   * reshaping into a linear array and then into the new format.  Linearization/delinearization is
   * row-first; i.e. the last dimension will be filled up first. */
  public void reshape(int newsize[]) {
    if ((type == null) || (size == null) || (newsize == null) || (size.length*newsize.length == 0)) return;
    int prodsize = 1; for (int i = 0; i < size.length; i++) prodsize *= size[i];
    int prodnewsize = 1; for (int i = 0; i < newsize.length; i++) prodnewsize *= newsize[i];
    if (prodsize != prodnewsize) return;
    // reshape into linear blob
    Object linear = Array.newInstance(type, new int[] { prodsize });
    linearize(blob, linear);
    // create new blob and delinearize
    blob = Array.newInstance(type, size = newsize);
    delinearize(linear, blob);
  }

  private void linearize(Object src, Object dst) { linearize(src, dst, 0, 0, Array.getLength(dst)); }
  private void linearize(Object src, Object dst, int dim, int offset, int prodsize) {
    prodsize /= size[dim];
    for (int i = 0; i < Array.getLength(src); i++)
      if (dim == size.length-1) Array.set(dst, offset + i, Array.get(src, i));
      else linearize(Array.get(src, i), dst, dim+1, offset + i*prodsize, prodsize);
  }

  private void delinearize(Object src, Object dst) { delinearize(src, dst, 0, 0, Array.getLength(src)); }
  private void delinearize(Object src, Object dst, int dim, int offset, int prodsize) {
    prodsize /= size[dim];
    for (int i = 0; i < Array.getLength(dst); i++)
      if (dim == size.length-1) Array.set(dst, i, Array.get(src, offset + i));
      else delinearize(src, Array.get(dst, i), dim+1, offset + i*prodsize, prodsize);
  }

  public String toString() {
    if ((type == null) || (blob == null))
      return "null";
    if (blob instanceof SparseMatrix)
      return "SparseMatrix(" + getSparseMatrix().N + ")";
    String res = type.getName();
    if (size != null)
      for (int i = 0; i < size.length; i++)
        res += "[" + size[i] + "]";
    return res;
  }

}
