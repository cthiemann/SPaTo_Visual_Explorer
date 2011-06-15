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

static class Dijkstra {

  /**
   * Fibonacci Heap
   * http://en.wikipedia.org/wiki/Fibonacci_heap
   * http://www.cs.princeton.edu/~wayne/cs423/lectures/fibonacci-4up.pdf
   * http://www.cs.princeton.edu/~wayne/cs423/fibonacci/FibonacciHeapAlgorithm.html
   * This version only implements what we need for Dijkstra.
   * Also, only non-negative integers can be "elements" of the heap.
   */
  static class FibonacciHeap extends Object {
    static class Node {
      int element;
      float key;
      boolean marked;
      int degree;
      Node prev, next, child, parent;  // only one child is pointed to;
      // its siblings are organized in a double-linked list via prev/next
      Node(int element, float key) { this.element = element; this.key = key;
        marked = false; degree = 0;
        next = this; prev = this; child = null; parent = null; }
    }
    
    int N, Nt;  // number of nodes and number of trees
    Node nodes[];  // wasteful array to quickly find an element by its "element" value
    Node min;  // minimum node (the root of the tree)
    
    FibonacciHeap(int maxElement) {
      N = Nt = 0;
      min = null;
      nodes = new Node[maxElement+1];
    }
    
    void insert(int element, float key) {
      Node n = new Node(element, key);
      nodes[element] = n;  // link in nodes array for quick access
      if (min == null)  // if heap is empty, this element will be the new minimum
        min = n;
      else {  // otherwise add to the left of min and update min
        n.next = min;
        n.prev = min.prev;
        min.prev = n;
        n.prev.next = n;
        if (key < min.key)
          min = n;
      }
      N++; Nt++;
    }
    
    int deleteMin() {
      if (min == null) return -1;  // nothing to do
      // calculate max degree
      int maxdegree = (int)Math.ceil(Math.log(N)/Math.log(1.619));
      // REMOVE MIN
      int element = min.element;  // save return value
      N--; Nt--;  // we'll remove min, thus one root node less in heap
      Nt += min.degree;  // but we'll add so many new root nodes
      // if heap is empty now, set min to null and return
      if (N == 0) { min = null; return element; }
      // update root list: replace min with its children
      if (min.next == min)  // min was the only tree left
        min = min.child;  // min's children are the new root list
      else {
        if (min.child == null) {
          // simply remove min
          min.prev.next = min.next;
          min.next.prev = min.prev;
        } else {
          // link last child to right neighbor of min
          min.child.prev.next = min.next;
          min.next.prev = min.child.prev;
          // link first child to left neighbor of min
          min.child.prev = min.prev;
          min.prev.next = min.child;
        }
      }
      // compile a list of root nodes
      Node roots[] = new Node[Nt];
      for (int i = 0; i < Nt; i++) {
        min = min.next;  // get next root node
        roots[i] = min;  // and add it to the array
        roots[i].parent = null;  // this overrides all min childrens' parent pointers
        roots[i].marked = false;  // root nodes should not be marked
      }
      roots[0].prev = roots[Nt-1]; roots[Nt-1].next = roots[0];  // close cycle again
      min = roots[0];  // updating min: first guess...
      // CONSOLIDATE
      Node A[] = new Node[maxdegree+1];  // degree pointer array
      for (int i = 0; i < A.length; i++)
        A[i] = null;
      for (int i = 0; i < roots.length; i++) {
        Node x = roots[i];
        while (A[x.degree] != null) {  // merge x and y
          Node y = A[x.degree];
          A[x.degree] = null;
          // make sure that x has the lower key of the two
          if (y.key < x.key) { Node tmp = y; y = x; x = tmp; }
          if (min == y) min = x;  // this can happen if y.key == x.key; make sure min stays a pointer to a root
          // remove y from root list
          y.prev.next = y.next; y.next.prev = y.prev; Nt--;
          // make y a child of x and unmark it
          y.parent = x;
          y.marked = false;
          if (x.child == null)
            x.child = y.next = y.prev = y;
          else {
            y.next = x.child.next;
            y.next.prev = y;
            x.child.next = y;
            y.prev = x.child;
          }
          x.degree++;
        }
        A[x.degree] = x;
        if (x.key < min.key)
          min = x;
      }
      // remove min from easy-access list and return
      nodes[element] = null;
      return element;
    }
    
    void decreaseKey(int element, float key) {
      Node z = nodes[element];
      // if element is not on heap or new key is not lower, then there's nothing to do
      if ((z == null) || (z.key <= key)) return;
      // decrease key of z
      z.key = key;
      // update tree structure if necessary
      if ((z.parent != null) && (z.key < z.parent.key)) {
        Node x = z;
        do {
          // CUT
          Node y = x.parent;
          // remove x from the child list of y
          if (x.next == x)  // only child?
            y.child = null;
          else {
            if (y.child == x) y.child = x.next;
            x.prev.next = x.next;
            x.next.prev = x.prev;
          }
          y.degree--;
          // add x to root list
          x.next = min.next; x.next.prev = x;
          x.prev = min; min.next = x;
          Nt++;
          // update accounting
          x.marked = false;
          x.parent = null;
          // CASCADING-CUT decision
          if (y.marked)  // if parent was already marked
            x = y;       // now y will cut in the next iteration (if it's not a root node already)
          else if (y.parent != null)  // if parent was not marked before and is not a root node...
            y.marked = true;  // ...mark it and leave x as it is (thus x.parent == null and the while-loop will exit)
        } while (x.parent != null);
      }
      // update min pointer
      if (z.key < min.key)
        min = z;
    }
    
    boolean isEmpty() { return min == null; }
  }

  static void calculateShortestPathTree(int edges[][], float weights[][], int i0, int pred[], float dist[]) {
    calculateShortestPathTree(edges, weights, i0, pred, dist, false); }
  // if useInv is true, 1/weights will be used
  static void calculateShortestPathTree(int edges[][], float weights[][], int i0, int pred[], float dist[], boolean useInv) {
    int N = edges.length;
    boolean optimal[] = new boolean[N];
    FibonacciHeap Q = new FibonacciHeap(N);
    for (int i = 0; i < N; i++) {
      dist[i] = Float.POSITIVE_INFINITY;
      pred[i] = -1;
      optimal[i] = false;
      Q.insert(i, dist[i]);
    }
    dist[i0] = 0;
    Q.decreaseKey(i0, 0);
    while (!Q.isEmpty()) {
      int u = Q.deleteMin();
      optimal[u] = true;
      int edgesu[] = edges[u];
      float weightsu[] = weights[u];
      float distu = dist[u];
      for (int n = 0; n < edgesu.length; n++) {
        int v = edgesu[n];
        if (optimal[v]) continue;
        float alt = distu + (useInv ? 1/weightsu[n] : weightsu[n]);
        if (alt < dist[v]) {
          dist[v] = alt;
          Q.decreaseKey(v, alt);
          pred[v] = u;
        }
      }
    }
  }
  
}
