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

boolean fireworks = false;
Fireworks fw = null;

void startFireworks() {
  gui.setVisible(false);
  gui.setEnabled(false);
  fw = new Fireworks();
  fireworks = true;
  fw.setup();
}

void disposeFireworks() {
  fireworks = false;
  fw = null;
  gui.setEnabled(true);
  gui.setVisible(true);
}

// How to make this class:
//  1) Copy'n'paste all PDE files from /Users/ct/Processing/Fireworks
//  2) Check setup() and out-comment inappropriate stuff (the size(...) should stay)
//  3) Replace "exit()" at the end of draw() with "alpha = tAlpha = 0;"
//  4) Out-comment "background(255);" in draw()
class Fireworks {

  Launcher ll[] = null;
  float t0, tt, t, dt, t0max, tFinish = Float.NaN;

  boolean started = false, finished = false;
  float scale = 4;
  float alpha = 0;
  float grav = -9.81;

  color colors[] = new color[] {
    color(255,   0,   0),  // red
    color(255, 150,  20),  // orange
    color(255, 225,  20),  // yellow
    //color(160, 250, 160),  // light green
    color( 50, 255, 50),  // solid green
    //color( 15, 250, 160),  // cyan
    color(  0, 127, 255),  // light blue
    color(180, 130, 250),  // light purple
    color(200,  40, 220),  // solid purple
  };


//  int T = 0;
//  int millis() { return super.millis() + T*1000; }

  void setup() {
    randomSeed(second() + 60*minute() + 3600*hour());
  //  size(1280, 720);
    size(800, 800*9/16);
//    frameRate(30);
//    smooth();
    t0 = tt = millis()/1000.;
  //  T = 40;
    ll = new Launcher[] {
      new RocketLauncher(30, t0 + 2, 18),
      new TrailingRocketLauncher(10, t0 + 27, 8, 4, 75, true),
      new TrailingRocketLauncher(11, t0 + 35, 6, 3, 75, true),
      new TrailingRocketLauncher(11, t0 + 35, 6, 3, 75, false),
      new ShellLauncher(10, t0 + 36, 5, true, color(250)),//, 240, 200)),
      new ShellLauncher(20, t0 + 42, 20),
      new ShellLauncher(5, t0 + 47, 15, true),
      new ShellLauncher(30, t0 + 52, 10),
      new ShellLauncher(1, t0 + 61, 1, true),
    };
    t0max = 0; for (Launcher l : ll) t0max = max(t0max, l.t0);
  }

  void draw() {
    t = millis()/1000.; dt = t - tt; tt = t;
    if (alpha == 1)
      background(0);
    else {
      //background(255);
      noStroke(); fill(0, 255*alpha); rect(0, 0, width, height);
    }
    //if (frameCount % 30 == 0) println((t - t0) + "   " + frameRate);
    float tAlpha = alpha;
    if (!started) {
      if (alpha < .999) tAlpha = 1; else { alpha = tAlpha = 1; started = true; }
    } else if (started && !finished) {
      noFill();
      pushMatrix();
      translate(width/2, height);
      scale(scale*width/1280, -scale*height/720);
      finished = (t > t0max);  // will be overriden if any of the launchers is still busy
      for (Launcher l : ll) {
        l.draw();
        if (!l.finished)
          finished = false;
      }
      popMatrix();
    } else {  // finished
      if (Float.isNaN(tFinish)) tFinish = t;
      if (t > tFinish + 5) tAlpha = 0;  // fade out five seconds after last shell burned out
      if (alpha < 0.001) alpha = tAlpha = 0;
    }
    alpha += 3*(tAlpha - alpha)*min(dt, 1/3.);
  }
  class Particle {
    float t0, x, y, vx, vy, ax, ay;  // creation time, position, velocity, acceleration
    float f = .1;  // friction
    color c;  // color
    float alpha = 1;
    boolean solid = false, slowDecay = false;
    boolean sparkling = false, isSparkling = false;
  
    Particle(float t0, float x0, float y0, float vx0, float vy0, color c) {
      this(t0, x0, y0, vx0, vy0, 0, 0, c); }
    Particle(float t0, float x0, float y0, float vx0, float vy0, float ax0, float ay0, color c) {
      this.t0 = t0; x = x0; y = y0; vx = vx0; vy = vy0; ax = ax0; ay = ay0; this.c = c; }
  
    void draw() {
      color cc = c;
      vx += ax*dt; vy += (ay + grav)*dt;
      vx += -f*vx*dt; vy += -f*vy*dt;
      x += vx*dt; y += vy*dt;
      if (!solid) {
        alpha -= (t - t0)*random(slowDecay ? .25 : 1)*dt;
        cc = lerpColor(color(255), c, min(1, (.5 + .5*noise(x, y, t/10))*(t - t0)/.75));
      }
      if (sparkling) {
        if (t - t0 > random(1, 2)) isSparkling = true;
        if (alpha < 0) alpha = random(0, 1);
        if (isSparkling && (random(1) < .25)) cc = color(255);
        if (t - t0 > random(3.5, 4.5)) sparkling = false;
      }
      alpha = max(0, alpha);
      stroke(cc, 255*alpha);
      point(x, y);
    }
  }


  class Launcher {
    Shell rr[] = new Shell[0];
    boolean finished = true;
    float t0;
  
    Launcher(float t0) { this.t0 = t0; }
  
    void draw() {
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
    ShellLauncher(int N, float t0, float deltat, boolean sparkling, color c) {
      super(t0);
      rr = new Shell[N];
      for (int i = 0; i < N; i++) {
        float x0 = 3*round((sparkling ? 5 : 10)*random(-1, 1)) + random(-1,1);
        float vx = (sparkling ? 5 : 10)*random(-1, 1);
        rr[i] = new Shell(random(t0, t0 + deltat), x0, 0, vx, random(45, 55), sparkling, c);
      }
    }
  }

  class RocketLauncher extends Launcher {
    RocketLauncher(int N, float t0, float deltat) {
      super(t0);
      rr = new Shell[N];
      for (int i = 0; i < N; i++)
        rr[i] = new Skyrocket(random(t0, t0 + deltat), random(-2, 2), 0,
                              random(-2, 2), random(15, 25), -1.5*grav);
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
      float x0max = dx*(N-1)/2.;
      for (int ii = 0; ii < Ni; ii++) {
        for (int i = 0; i < N; i++) {
          float rt0 = t0 + (ii*(N-1) + i)*dt;
          float x0 = dx*((ltr ? i : (N-1-i)) - (N-1)/2.);
          if (Ni > 1) x0 += (ltr ? -1 : 1)*.25*dx;
          float angle = PI/5 * x0/x0max;
          float v = v0*random(0.95, 1.05);
          rr[index] = new TrailingSkyrocket(rt0,  x0, 0, v*sin(angle), v*cos(angle), -.5*grav);
          rr[index].t1 = .5;
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
    Shell(float t0, float x0, float y0, float vx0, float vy0, boolean sparkling, color c) {
      super(t0, x0, y0, vx0, vy0, color(20)); f /= 10; solid = true;
      t1 = random(3, 4);
      this.x0 = x0;
      createParticles(sparkling, c);
    }
  
    void createParticles(boolean sparkling, color c) {
      pp = new Particle[floor(sparkling ? random(150, 200) : random(100, 150))];
      if (c == 0) c = colors[floor(random(0, colors.length))];
      float size = sparkling ? random(30, 31) : random(8, 10);
      for (int i = 0; i < pp.length; i++) {
        float theta = random(-PI, PI);
        float phi = random(0, PI);
        float nv = size*random(0.9, 1.0);
        float nvx = nv*sin(theta)*cos(phi);
        float nvy = nv*cos(theta);//sin(theta)*sin(phi);
  //          c = random(0, 1) < 0.8 ? colors[0] : color(255);
        pp[i] = new Particle(0, 0, -100, nvx, nvy, c);
        pp[i].sparkling = sparkling;
        if (sparkling) pp[i].f *= 5;
      }
    }
  
    void draw() {
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
          if (p.alpha > 1/255.) finished = false;
        }
      }
      // draw launch
      if (mortarLaunch && (t - t0 < .25)) {
        float nu = (1 - (t - t0)/.25);
        stroke(lerpColor(color(255), color(255, 127, 0), nu), 255*nu);
        point(x0, 2/scale);
      }
    }
  }


  class Skyrocket extends Shell {
    Particle trail[] = new Particle[50]; int trailPointer = 0; float trailLast = 0;
    float a;
  
    class TrailParticle extends Particle {
      float tau = .1;
      TrailParticle(float t0, float x0, float y0, float vx0, float vy0) {
        super(t0, x0, y0, vx0, vy0, color(255)); alpha = 0.3; f *= 10; }
      void draw() {
        if (t - t0 < 1*tau)      c = lerpColor(color(255, 255, 255), color(255, 255,   0), (t - t0)/tau);
        else if (t - t0 < 2*tau) c = lerpColor(color(255, 255,   0), color(255,   0,   0), (t - t0)/tau - 1);
        else if (t - t0 < 3*tau) c = lerpColor(color(255,   0,   0), color(127, 127, 127), (t - t0)/tau - 2);
        else                     c = color(127, 127, 127);
        alpha -= 0.2*dt;
        if (random(0, 10) < t - t0) alpha -= 0.05;
        alpha = max(0, min(1, alpha));
        vx += random(-1, 1)*20*dt;
            vx += ax*dt; vy += (ay + grav)*dt;
            vx += -f*vx*dt; vy += -f*vy*dt;
            x += vx*dt; y += vy*dt;
            stroke(c, 255*alpha); noFill();
            point(x, y);
      }
    }
  
    Skyrocket(float t0, float x0, float y0, float vx0, float vy0, float a) {
      super(t0, x0, y0, vx0, vy0); this.a = a;
      mortarLaunch = false;
      createParticles();
    }
  
    void createParticles() {
      pp = new Particle[floor(random(50, 150))];
      color c = colors[floor(random(0, colors.length))];
      float size = random(5, 10);
      for (int i = 0; i < pp.length; i++) {
        float theta = random(-PI, PI);
        float phi = random(0, PI);
        float nv = size*random(0.5, 1.5);
        float nvx = nv*sin(theta)*cos(phi);
        float nvy = nv*cos(theta);//sin(theta)*sin(phi);
  //          c = random(0, 1) < 0.8 ? colors[0] : color(255);
        pp[i] = new Particle(0, 0, -100, nvx, nvy, c);
      }
    }
  
    void generateTrail() {
      if ((a > 0) && (t > trailLast + .05)) {
        trail[trailPointer] = new TrailParticle(t, x, y, vx, vy);
        trailPointer = (trailPointer + 1) % trail.length;
        trailLast = t;
      }
    }
  
    void draw() {
      // draw tail
      for (int i = 0; i < trail.length; i++) {
        if (trail[i] == null) continue;
        trail[i].draw();
        if (trail[i].alpha < 1/255.) trail[i] = null;
      }
      // propulsion
      if (!exploded) {
        if (t - t0 > t1 - 1) a = 0;
        float v = sqrt(vx*vx + vy*vy);
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
      trailRate = initTrailRate = 7*sqrt(sq(vx0) + sq(vy0));
    }
  
    void generateTrail() {
      if (a == 0) trailRate = max(0, trailRate - .66*initTrailRate*dt);
      int N = floor(trailRate*dt);
      if (random(1) < trailRate*dt - N) N++;
      for (int i = 0; i < N; i++) {
        float vx0 = 5*sqrt(sqrt(t - t0))*random(-1, 1);
        float vy0 = 5*sqrt(sqrt(t - t0))*random(-1, 1);
        trail[trailPointer] = new Particle(t, x - vx*dt*i/N, y - vy*dt*i/N, vx0, vy0, 0, -0.9*grav,
          (random(1) < .25) ? color(255) : color(255, 225, 64));
        trail[trailPointer].alpha = 0.3;
        trail[trailPointer].slowDecay = true;
        trail[trailPointer].f *= 10;
        trailPointer = (trailPointer + 1) % trail.length;
        trailLast = t;
      }
    }
  
    void draw() {
      super.draw();
      finished = (a == 0);
      for (Particle p : trail)
        if ((p != null) && (p.alpha > 1/255.))
          finished = false;
    }
  
  }

}