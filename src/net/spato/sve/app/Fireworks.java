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


public class Fireworks {

  PApplet app = null;

  public Fireworks(PApplet app) { this.app = app; }

  Launcher ll[] = null;
  float t0, tt, t, dt, t0max, tFinish = Float.NaN;

  boolean started = false, finished = false;
  float scale = 4;
  float alpha = 0;
  float grav = -9.81f;

  int colors[] = new int[] {
    app.color(255,   0,   0),  // red
    app.color(255, 150,  20),  // orange
    app.color(255, 225,  20),  // yellow
    //color(160, 250, 160),  // light green
    app.color( 50, 255, 50),  // solid green
    //color( 15, 250, 160),  // cyan
    app.color(  0, 127, 255),  // light blue
    app.color(180, 130, 250),  // light purple
    app.color(200,  40, 220),  // solid purple
  };

//  int T = 0;
//  int millis() { return super.millis() + T*1000; }

  public void setup() {
    app.randomSeed(app.second() + 60*app.minute() + 3600*app.hour());
  //  size(1280, 720);
    app.size(800, 800*9/16);
//    frameRate(30);
//    smooth();
    t0 = tt = app.millis()/1000.f;
  //  T = 40;
    ll = new Launcher[] {
      new RocketLauncher(30, t0 + 2, 18),
      new TrailingRocketLauncher(10, t0 + 27, 8, 4, 75, true),
      new TrailingRocketLauncher(11, t0 + 35, 6, 3, 75, true),
      new TrailingRocketLauncher(11, t0 + 35, 6, 3, 75, false),
      new ShellLauncher(10, t0 + 36, 5, true, app.color(250)),//, 240, 200)),
      new ShellLauncher(20, t0 + 42, 20),
      new ShellLauncher(5, t0 + 47, 15, true),
      new ShellLauncher(30, t0 + 52, 10),
      new ShellLauncher(1, t0 + 61, 1, true),
    };
    t0max = 0; for (Launcher l : ll) t0max = app.max(t0max, l.t0);
  }

  public void draw() {
    t = app.millis()/1000.f; dt = t - tt; tt = t;
    if (alpha == 1)
      app.background(0);
    else {
      //background(255);
      app.noStroke(); app.fill(0, 255*alpha); app.rect(0, 0, app.width, app.height);
    }
    //if (frameCount % 30 == 0) println((t - t0) + "   " + frameRate);
    float tAlpha = alpha;
    if (!started) {
      if (alpha < .999f) tAlpha = 1; else { alpha = tAlpha = 1; started = true; }
    } else if (started && !finished) {
      app.noFill();
      app.pushMatrix();
      app.translate(app.width/2, app.height);
      app.scale(scale*app.width/1280, -scale*app.height/720);
      finished = (t > t0max);  // will be overriden if any of the launchers is still busy
      for (Launcher l : ll) {
        l.draw();
        if (!l.finished)
          finished = false;
      }
      app.popMatrix();
    } else {  // finished
      if (Float.isNaN(tFinish)) tFinish = t;
      if (t > tFinish + 5) tAlpha = 0;  // fade out five seconds after last shell burned out
      if (alpha < 0.001f) alpha = tAlpha = 0;
    }
    alpha += 3*(tAlpha - alpha)*app.min(dt, 1/3.f);
  }


  class Particle {
    float t0, x, y, vx, vy, ax, ay;  // creation time, position, velocity, acceleration
    float f = .1f;  // friction
    int c;  // color
    float alpha = 1;
    boolean solid = false, slowDecay = false;
    boolean sparkling = false, isSparkling = false;

    Particle(float t0, float x0, float y0, float vx0, float vy0, int c) {
      this(t0, x0, y0, vx0, vy0, 0, 0, c); }
    Particle(float t0, float x0, float y0, float vx0, float vy0, float ax0, float ay0, int c) {
      this.t0 = t0; x = x0; y = y0; vx = vx0; vy = vy0; ax = ax0; ay = ay0; this.c = c; }

    public void draw() {
      int cc = c;
      vx += ax*dt; vy += (ay + grav)*dt;
      vx += -f*vx*dt; vy += -f*vy*dt;
      x += vx*dt; y += vy*dt;
      if (!solid) {
        alpha -= (t - t0)*app.random(slowDecay ? .25f : 1)*dt;
        cc = app.lerpColor(app.color(255), c, app.min(1, (.5f + .5f*app.noise(x, y, t/10))*(t - t0)/.75f));
      }
      if (sparkling) {
        if (t - t0 > app.random(1, 2)) isSparkling = true;
        if (alpha < 0) alpha = app.random(0, 1);
        if (isSparkling && (app.random(1) < .25f)) cc = app.color(255);
        if (t - t0 > app.random(3.5f, 4.5f)) sparkling = false;
      }
      alpha = app.max(0, alpha);
      app.stroke(cc, 255*alpha);
      app.point(x, y);
    }
  }


  class Launcher {
    Shell rr[] = new Shell[0];
    boolean finished = true;
    float t0;

    Launcher(float t0) { this.t0 = t0; }

    public void draw() {
      finished = true;
      for (Shell r : rr) {
        if (t < r.t0) continue;
        r.draw();
        if (!r.finished)
          finished = false;
      }
    }

  }


  class ShellLauncher extends Launcher {
    ShellLauncher(int N, float t0, float deltat) { this(N, t0, deltat, false); }
    ShellLauncher(int N, float t0, float deltat, boolean sparkling) { this(N, t0, deltat, sparkling, 0); }
    ShellLauncher(int N, float t0, float deltat, boolean sparkling, int c) {
      super(t0);
      rr = new Shell[N];
      for (int i = 0; i < N; i++) {
        float x0 = 3*app.round((sparkling ? 5 : 10)*app.random(-1, 1)) + app.random(-1,1);
        float vx = (sparkling ? 5 : 10)*app.random(-1, 1);
        rr[i] = new Shell(app.random(t0, t0 + deltat), x0, 0, vx, app.random(45, 55), sparkling, c);
      }
    }
  }


  class RocketLauncher extends Launcher {
    RocketLauncher(int N, float t0, float deltat) {
      super(t0);
      rr = new Shell[N];
      for (int i = 0; i < N; i++)
        rr[i] = new Skyrocket(app.random(t0, t0 + deltat), app.random(-2, 2), 0,
                              app.random(-2, 2), app.random(15, 25), -1.5f*grav);
    }
  }


  class TrailingRocketLauncher extends Launcher {
    TrailingRocketLauncher(int N, float t0, float deltat, int Ni, float v0) {
      this(N, t0, deltat, Ni, v0, true); }
    TrailingRocketLauncher(int N, float t0, float deltat, int Ni, float v0, boolean ltr) {
      super(t0);
      rr = new Shell[N*Ni]; int index = 0;
      float dt = deltat/(Ni*(N + 1));  // time between individual rocket launches
      float dx = 10;
      float x0max = dx*(N-1)/2.f;
      for (int ii = 0; ii < Ni; ii++) {
        for (int i = 0; i < N; i++) {
          float rt0 = t0 + (ii*(N-1) + i)*dt;
          float x0 = dx*((ltr ? i : (N-1-i)) - (N-1)/2.f);
          if (Ni > 1) x0 += (ltr ? -1 : 1)*.25f*dx;
          float angle = PApplet.PI/5 * x0/x0max;
          float v = v0*app.random(0.95f, 1.05f);
          rr[index] = new TrailingSkyrocket(rt0,  x0, 0, v*app.sin(angle), v*app.cos(angle), -.5f*grav);
          rr[index].t1 = .5f;
          index++;
        }
        ltr = !ltr;
      }
    }
  }


  class Shell extends Particle {
    boolean mortarLaunch = true;
    Particle pp[] = null;
    float t1, x0;
    boolean exploded = false;
    boolean finished = false;

    Shell(float t0, float x0, float y0, float vx0, float vy0) {
      this(t0, x0, y0, vx0, vy0, false); }
    Shell(float t0, float x0, float y0, float vx0, float vy0, boolean sparkling) {
      this(t0, x0, y0, vx0, vy0, sparkling, 0); }
    Shell(float t0, float x0, float y0, float vx0, float vy0, boolean sparkling, int c) {
      super(t0, x0, y0, vx0, vy0, app.color(20)); f /= 10; solid = true;
      t1 = app.random(3, 4);
      this.x0 = x0;
      createParticles(sparkling, c);
    }

    public void createParticles(boolean sparkling, int c) {
      pp = new Particle[app.floor(sparkling ? app.random(150, 200) : app.random(100, 150))];
      if (c == 0) c = colors[app.floor(app.random(0, colors.length))];
      float size = sparkling ? app.random(30, 31) : app.random(8, 10);
      for (int i = 0; i < pp.length; i++) {
        float theta = app.random(-PApplet.PI, PApplet.PI);
        float phi = app.random(0, PApplet.PI);
        float nv = size*app.random(0.9f, 1.0f);
        float nvx = nv*app.sin(theta)*app.cos(phi);
        float nvy = nv*app.cos(theta);//sin(theta)*sin(phi);
  //          c = app.random(0, 1) < 0.8 ? colors[0] : app.color(255);
        pp[i] = new Particle(0, 0, -100, nvx, nvy, c);
        pp[i].sparkling = sparkling;
        if (sparkling) pp[i].f *= 5;
      }
    }

    public void draw() {
      // shell physics
      alpha = 1;
      super.draw();
      // trigger explosion
      if (!exploded && (t - t0 > t1)) {
        for (Particle p : pp) { p.t0 = t; p.x = x; p.y = y; p.vx += vx; p.vy += vy; }
        exploded = true;
      }
      // draw crown particles
      if (exploded) {
        finished = true;
        for (Particle p : pp) {
          p.draw();
          if (p.alpha > 1/255.f) finished = false;
        }
      }
      // draw launch
      if (mortarLaunch && (t - t0 < .25f)) {
        float nu = (1 - (t - t0)/.25f);
        app.stroke(app.lerpColor(app.color(255), app.color(255, 127, 0), nu), 255*nu);
        app.point(x0, 2/scale);
      }
    }
  }


  class Skyrocket extends Shell {
    Particle trail[] = new Particle[50]; int trailPointer = 0; float trailLast = 0;
    float a;

    class TrailParticle extends Particle {
      float tau = .1f;
      TrailParticle(float t0, float x0, float y0, float vx0, float vy0) {
        super(t0, x0, y0, vx0, vy0, app.color(255)); alpha = 0.3f; f *= 10; }
      public void draw() {
        if (t - t0 < 1*tau)      c = app.lerpColor(app.color(255, 255, 255), app.color(255, 255,   0), (t - t0)/tau);
        else if (t - t0 < 2*tau) c = app.lerpColor(app.color(255, 255,   0), app.color(255,   0,   0), (t - t0)/tau - 1);
        else if (t - t0 < 3*tau) c = app.lerpColor(app.color(255,   0,   0), app.color(127, 127, 127), (t - t0)/tau - 2);
        else                     c = app.color(127, 127, 127);
        alpha -= 0.2f*dt;
        if (app.random(0, 10) < t - t0) alpha -= 0.05f;
        alpha = app.max(0, app.min(1, alpha));
        vx += app.random(-1, 1)*20*dt;
        vx += ax*dt; vy += (ay + grav)*dt;
        vx += -f*vx*dt; vy += -f*vy*dt;
        x += vx*dt; y += vy*dt;
        app.stroke(c, 255*alpha); app.noFill();
        app.point(x, y);
      }
    }

    Skyrocket(float t0, float x0, float y0, float vx0, float vy0, float a) {
      super(t0, x0, y0, vx0, vy0); this.a = a;
      mortarLaunch = false;
      createParticles();
    }

    public void createParticles() {
      pp = new Particle[app.floor(app.random(50, 150))];
      int c = colors[app.floor(app.random(0, colors.length))];
      float size = app.random(5, 10);
      for (int i = 0; i < pp.length; i++) {
        float theta = app.random(-PApplet.PI, PApplet.PI);
        float phi = app.random(0, PApplet.PI);
        float nv = size*app.random(0.5f, 1.5f);
        float nvx = nv*app.sin(theta)*app.cos(phi);
        float nvy = nv*app.cos(theta);//sin(theta)*sin(phi);
  //          c = app.random(0, 1) < 0.8 ? colors[0] : app.color(255);
        pp[i] = new Particle(0, 0, -100, nvx, nvy, c);
      }
    }

    public void generateTrail() {
      if ((a > 0) && (t > trailLast + .05f)) {
        trail[trailPointer] = new TrailParticle(t, x, y, vx, vy);
        trailPointer = (trailPointer + 1) % trail.length;
        trailLast = t;
      }
    }

    public void draw() {
      // draw tail
      for (int i = 0; i < trail.length; i++) {
        if (trail[i] == null) continue;
        trail[i].draw();
        if (trail[i].alpha < 1/255.f) trail[i] = null;
      }
      // propulsion
      if (!exploded) {
        if (t - t0 > t1 - 1) a = 0;
        float v = app.sqrt(vx*vx + vy*vy);
        ax = a*vx/v; ay = a*vy/v;
      }
      // generate trail
      generateTrail();
      // physics & draw
      super.draw();
    }
  }


  // does not explode; the trail is the effect
  class TrailingSkyrocket extends Skyrocket {
    float initTrailRate, trailRate = -1;

    TrailingSkyrocket(float t0, float x0, float y0, float vx0, float vy0, float a) {
      super(t0, x0, y0, vx0, vy0, a); f = 0;
      pp = new Particle[0];  // no crown
      trail = new Particle[500];
      trailRate = initTrailRate = 7*app.sqrt(app.sq(vx0) + app.sq(vy0));
    }

    public void generateTrail() {
      if (a == 0) trailRate = app.max(0, trailRate - .66f*initTrailRate*dt);
      int N = app.floor(trailRate*dt);
      if (app.random(1) < trailRate*dt - N) N++;
      for (int i = 0; i < N; i++) {
        float vx0 = 5*app.sqrt(app.sqrt(t - t0))*app.random(-1, 1);
        float vy0 = 5*app.sqrt(app.sqrt(t - t0))*app.random(-1, 1);
        trail[trailPointer] = new Particle(t, x - vx*dt*i/N, y - vy*dt*i/N, vx0, vy0, 0, -0.9f*grav,
          (app.random(1) < .25f) ? app.color(255) : app.color(255, 225, 64));
        trail[trailPointer].alpha = 0.3f;
        trail[trailPointer].slowDecay = true;
        trail[trailPointer].f *= 10;
        trailPointer = (trailPointer + 1) % trail.length;
        trailLast = t;
      }
    }

    public void draw() {
      super.draw();
      finished = (a == 0);
      for (Particle p : trail)
        if ((p != null) && (p.alpha > 1/255.f))
          finished = false;
    }

  }

}
