/*
 * Stick Run - a line-art endless runner in the spirit of the Chrome dino game.
 * Everything on screen is drawn with strokes on a 2D canvas; no images, no deps.
 */
(function () {
  'use strict';

  // ---------------------------------------------------------------- constants

  var W = 900;                 // logical canvas width
  var H = 320;                 // logical canvas height
  var GROUND_Y = 258;          // y of the ground line
  var PLAYER_X = 96;           // the runner never moves horizontally

  // Tuned so a full jump peaks around 127px (~0.34s up, ~0.29s down) and a
  // quick tap peaks around 65px. Tall obstacles top out at 72px.
  var GRAVITY = 3000;          // px/s^2 while falling
  var JUMP_V = -740;           // initial jump velocity
  var HOLD_SCALE = 0.72;       // lighter gravity while the jump key is held
  var CUT_V = -520;            // velocity kept when the jump is released early
  var FAST_FALL = 3.4;         // gravity multiplier while ducking mid-air

  var START_SPEED = 330;       // px/s
  var MAX_SPEED = 820;         // reached after ~55s
  var ACCEL = 9;               // px/s of extra speed per second survived

  var STAND_W = 24, STAND_H = 50;
  var DUCK_W = 44, DUCK_H = 28;

  var BIRD_SCORE = 320;        // birds start showing up at this score
  var NIGHT_EVERY = 900;       // score interval between day/night flips

  var DAY = { bg: '#ffffff', ink: '#25252a', far: '#c3c3c9' };
  var NIGHT = { bg: '#15151a', ink: '#ececf2', far: '#4a4a55' };

  // ------------------------------------------------------------------- canvas

  var canvas = document.getElementById('game');
  var ctx = canvas.getContext('2d');

  function resize() {
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    ctx.lineJoin = 'round';
    ctx.lineCap = 'round';
  }
  resize();
  window.addEventListener('resize', resize);

  // -------------------------------------------------------------------- audio

  var audio = { ctx: null, muted: false };

  function beep(freq, duration, type, gain) {
    if (audio.muted) return;
    if (!audio.ctx) {
      var AC = window.AudioContext || window.webkitAudioContext;
      if (!AC) return;
      audio.ctx = new AC();
    }
    if (audio.ctx.state === 'suspended') audio.ctx.resume();
    var t = audio.ctx.currentTime;
    var osc = audio.ctx.createOscillator();
    var vol = audio.ctx.createGain();
    osc.type = type || 'square';
    osc.frequency.setValueAtTime(freq, t);
    vol.gain.setValueAtTime(gain || 0.05, t);
    vol.gain.exponentialRampToValueAtTime(0.0001, t + duration);
    osc.connect(vol).connect(audio.ctx.destination);
    osc.start(t);
    osc.stop(t + duration);
  }

  // -------------------------------------------------------------------- state

  var STATE = { READY: 0, RUNNING: 1, OVER: 2, PAUSED: 3 };

  var game, hiScore = 0;

  try {
    hiScore = parseInt(localStorage.getItem('stickrun.hi'), 10) || 0;
  } catch (e) { /* private mode - just play without a saved score */ }

  function reset() {
    game = {
      state: STATE.READY,
      time: 0,
      distance: 0,
      score: 0,
      speed: START_SPEED,
      nextSpawn: 620,
      obstacles: [],
      clouds: seedClouds(),
      pebbles: seedPebbles(),
      dust: [],
      runPhase: 0,
      night: 0,          // 0 = day, 1 = night (animated)
      nightTarget: 0,
      flashUntil: 0,
      milestone: 0,
      player: {
        y: 0,            // height above the ground line
        vy: 0,
        onGround: true,
        ducking: false,
        dead: false,
        deadSpin: 0
      }
    };
  }

  function seedClouds() {
    var out = [];
    for (var i = 0; i < 4; i++) {
      out.push({ x: rand(0, W), y: rand(68, 138), s: rand(0.7, 1.3) });
    }
    return out;
  }

  function seedPebbles() {
    var out = [];
    for (var i = 0; i < 26; i++) {
      out.push({ x: rand(0, W), y: GROUND_Y + rand(4, 20), len: rand(3, 14) });
    }
    return out;
  }

  function rand(a, b) { return a + Math.random() * (b - a); }
  function randInt(a, b) { return Math.floor(rand(a, b + 1)); }
  function lerp(a, b, t) { return a + (b - a) * t; }

  function mixHex(c1, c2, t) {
    var a = parseInt(c1.slice(1), 16), b = parseInt(c2.slice(1), 16);
    var r = Math.round(lerp((a >> 16) & 255, (b >> 16) & 255, t));
    var g = Math.round(lerp((a >> 8) & 255, (b >> 8) & 255, t));
    var bl = Math.round(lerp(a & 255, b & 255, t));
    return 'rgb(' + r + ',' + g + ',' + bl + ')';
  }

  // ---------------------------------------------------------------- obstacles

  // Ground spikes: 1-3 stalks, each a vertical line with a pair of short arms.
  function makeSpikes() {
    var tall = Math.random() < 0.45;
    var count = tall ? randInt(1, 2) : randInt(1, 3);
    var h = tall ? rand(52, 72) : rand(30, 44);
    var spacing = tall ? 20 : 15;
    var w = (count - 1) * spacing + 18;
    return {
      kind: 'spike',
      x: W + 40,
      w: w,
      h: h,
      count: count,
      spacing: spacing,
      armY: rand(0.35, 0.6),
      pad: 4
    };
  }

  // Three flight lanes, each asking for a different reaction:
  //   34  - low, has to be jumped (ducking still clips it)
  //   58  - chest height, duck under it or jump it
  //  100  - high enough to run straight under, so jumping into it is the trap
  function makeBird() {
    var r = Math.random();
    var alt = r < 0.45 ? 34 : (r < 0.85 ? 58 : 100);
    return {
      kind: 'bird',
      x: W + 40,
      w: 42,
      h: 26,
      alt: alt,
      flap: Math.random() * Math.PI * 2,
      pad: 6
    };
  }

  function obstacleBox(o) {
    if (o.kind === 'bird') {
      return { x: o.x + o.pad, y: GROUND_Y - o.alt - o.h / 2, w: o.w - o.pad * 2, h: o.h };
    }
    return { x: o.x + o.pad, y: GROUND_Y - o.h, w: o.w - o.pad * 2, h: o.h };
  }

  function playerBox() {
    var p = game.player;
    var w = p.ducking && p.onGround ? DUCK_W : STAND_W;
    var h = p.ducking && p.onGround ? DUCK_H : STAND_H;
    return { x: PLAYER_X - w / 2 + 3, y: GROUND_Y - p.y - h, w: w - 6, h: h };
  }

  function overlaps(a, b) {
    return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
  }

  // ------------------------------------------------------------------- update

  function update(dt) {
    var p = game.player;

    if (game.state === STATE.OVER) {
      p.deadSpin = Math.min(1, p.deadSpin + dt * 6);
      if (p.y > 0 || p.vy < 0) {
        p.vy += GRAVITY * dt;
        p.y = Math.max(0, p.y - p.vy * dt);
      }
      stepDust(dt);
      return;
    }

    if (game.state !== STATE.RUNNING) {
      game.time += dt;
      stepClouds(dt, 0.25);
      return;
    }

    game.time += dt;
    game.speed = Math.min(MAX_SPEED, START_SPEED + ACCEL * game.time);
    game.distance += game.speed * dt;
    game.score = Math.floor(game.distance / 12);

    // Milestone blip + high-score bookkeeping.
    if (game.score >= game.milestone + 100) {
      game.milestone = Math.floor(game.score / 100) * 100;
      game.flashUntil = game.time + 0.9;
      beep(880, 0.07, 'square', 0.04);
    }

    // Day/night.
    game.nightTarget = Math.floor(game.score / NIGHT_EVERY) % 2;
    game.night += (game.nightTarget - game.night) * Math.min(1, dt * 2.8);

    // Vertical motion.
    var g = GRAVITY;
    if (p.vy < 0 && input.jumpHeld) g *= HOLD_SCALE;
    if (p.ducking && !p.onGround) g *= FAST_FALL;
    p.vy += g * dt;
    p.y -= p.vy * dt;

    if (p.y <= 0) {
      if (!p.onGround) {
        spawnDust(6);
        beep(180, 0.05, 'sine', 0.03);
      }
      p.y = 0;
      p.vy = 0;
      p.onGround = true;
    } else {
      p.onGround = false;
    }

    game.runPhase += dt * (game.speed / 34);

    // Scenery.
    stepClouds(dt, 0.22);
    for (var i = 0; i < game.pebbles.length; i++) {
      var pb = game.pebbles[i];
      pb.x -= game.speed * dt;
      if (pb.x < -20) {
        pb.x = W + rand(0, 120);
        pb.y = GROUND_Y + rand(4, 20);
        pb.len = rand(3, 14);
      }
    }
    stepDust(dt);

    // Obstacles.
    game.nextSpawn -= game.speed * dt;
    if (game.nextSpawn <= 0) {
      var canBird = game.score > BIRD_SCORE && Math.random() < 0.3;
      game.obstacles.push(canBird ? makeBird() : makeSpikes());
      var gap = game.speed * rand(0.62, 1.15) + rand(40, 130);
      game.nextSpawn = Math.max(190, gap);
    }

    for (var j = game.obstacles.length - 1; j >= 0; j--) {
      var o = game.obstacles[j];
      o.x -= game.speed * dt;
      if (o.kind === 'bird') {
        o.x -= game.speed * 0.14 * dt;   // birds close a little faster
        o.flap += dt * 12;
      }
      if (o.x + o.w < -30) game.obstacles.splice(j, 1);
    }

    // Collision.
    var pb2 = playerBox();
    for (var k = 0; k < game.obstacles.length; k++) {
      if (overlaps(pb2, obstacleBox(game.obstacles[k]))) {
        die();
        break;
      }
    }
  }

  function stepClouds(dt, factor) {
    for (var i = 0; i < game.clouds.length; i++) {
      var c = game.clouds[i];
      c.x -= game.speed * factor * c.s * dt;
      if (c.x < -140) {
        c.x = W + rand(20, 200);
        c.y = rand(68, 138);
        c.s = rand(0.7, 1.3);
      }
    }
  }

  function spawnDust(n) {
    for (var i = 0; i < n; i++) {
      game.dust.push({
        x: PLAYER_X + rand(-10, 10),
        y: GROUND_Y - rand(0, 4),
        vx: rand(-70, -170),
        vy: rand(-40, -110),
        life: 1,
        r: rand(1.5, 3.5)
      });
    }
  }

  function stepDust(dt) {
    for (var i = game.dust.length - 1; i >= 0; i--) {
      var d = game.dust[i];
      d.x += d.vx * dt;
      d.y += d.vy * dt;
      d.vy += 240 * dt;
      d.life -= dt * 1.9;
      if (d.life <= 0) game.dust.splice(i, 1);
    }
  }

  function die() {
    game.state = STATE.OVER;
    game.player.dead = true;
    game.player.vy = -260;
    spawnDust(10);
    beep(220, 0.12, 'sawtooth', 0.05);
    setTimeout(function () { beep(120, 0.25, 'sawtooth', 0.05); }, 110);
    if (game.score > hiScore) {
      hiScore = game.score;
      try { localStorage.setItem('stickrun.hi', String(hiScore)); } catch (e) { /* ignore */ }
    }
  }

  // --------------------------------------------------------------------- draw

  function theme() {
    var t = game.night;
    // The background crossfades, but the ink snaps across near the midpoint.
    // Lerping both together drags everything through the same mid-grey and
    // the scene briefly disappears.
    var s = smoothstep(0.42, 0.58, t);
    return {
      bg: mixHex(DAY.bg, NIGHT.bg, t),
      ink: mixHex(DAY.ink, NIGHT.ink, s),
      far: mixHex(DAY.far, NIGHT.far, s)
    };
  }

  function smoothstep(a, b, x) {
    var t = Math.max(0, Math.min(1, (x - a) / (b - a)));
    return t * t * (3 - 2 * t);
  }

  function draw() {
    var th = theme();

    ctx.fillStyle = th.bg;
    ctx.fillRect(0, 0, W, H);

    ctx.strokeStyle = th.far;
    ctx.lineWidth = 1.5;
    drawSkyBody(th);
    for (var i = 0; i < game.clouds.length; i++) drawCloud(game.clouds[i]);

    ctx.strokeStyle = th.ink;
    ctx.lineWidth = 2;
    drawGround(th);

    for (var j = 0; j < game.obstacles.length; j++) {
      var o = game.obstacles[j];
      if (o.kind === 'bird') drawBird(o); else drawSpikes(o);
    }

    drawDust(th);
    drawPlayer(th);
    drawHud(th);
  }

  function drawSkyBody(th) {
    // Sun by day, moon by night - a single circle that hollows out at night.
    var x = W - 120, y = 92, r = 15;
    ctx.save();
    ctx.globalAlpha = 0.75;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.stroke();
    if (game.night > 0.15) {
      ctx.globalAlpha = game.night;
      ctx.fillStyle = th.bg;
      ctx.beginPath();
      ctx.arc(x + 8, y - 5, r, 0, Math.PI * 2);
      ctx.fill();
      ctx.beginPath();
      ctx.arc(x + 8, y - 5, r, 0, Math.PI * 2);
      ctx.stroke();
      // a few stars
      ctx.beginPath();
      for (var i = 0; i < 7; i++) {
        var sx = ((i * 137) % (W - 100)) + 40 - (game.distance * 0.02 % W);
        if (sx < 0) sx += W;
        var sy = 30 + (i * 53) % 90;
        ctx.moveTo(sx - 3, sy);
        ctx.lineTo(sx + 3, sy);
        ctx.moveTo(sx, sy - 3);
        ctx.lineTo(sx, sy + 3);
      }
      ctx.stroke();
    }
    ctx.restore();
  }

  function drawCloud(c) {
    var x = c.x, y = c.y, s = c.s;
    ctx.beginPath();
    ctx.arc(x, y, 11 * s, Math.PI * 0.9, Math.PI * 2.05);
    ctx.arc(x + 18 * s, y - 6 * s, 14 * s, Math.PI * 1.05, Math.PI * 2.0);
    ctx.arc(x + 38 * s, y, 10 * s, Math.PI * 1.1, Math.PI * 2.1);
    ctx.lineTo(x - 10 * s, y);
    ctx.stroke();
  }

  function drawGround(th) {
    ctx.beginPath();
    ctx.moveTo(0, GROUND_Y);
    ctx.lineTo(W, GROUND_Y);
    ctx.stroke();

    ctx.save();
    ctx.strokeStyle = th.far;
    ctx.lineWidth = 1.5;
    ctx.beginPath();
    for (var i = 0; i < game.pebbles.length; i++) {
      var p = game.pebbles[i];
      ctx.moveTo(p.x, p.y);
      ctx.lineTo(p.x + p.len, p.y);
    }
    ctx.stroke();
    ctx.restore();
  }

  // Saguaro-ish: a trunk plus L-shaped arms that elbow outward then turn up.
  function drawSpikes(o) {
    ctx.lineWidth = 2.2;
    ctx.beginPath();
    for (var i = 0; i < o.count; i++) {
      var x = o.x + 9 + i * o.spacing;
      var h = o.h * (i === 0 ? 1 : 0.74 + (i % 2) * 0.18);
      ctx.moveTo(x, GROUND_Y);
      ctx.lineTo(x, GROUND_Y - h);

      // Arms go on even stalks only, so neighbouring stalks never tangle.
      if (i % 2 === 0 && h > 30) {
        var ay = GROUND_Y - h * o.armY;
        ctx.moveTo(x, ay);
        ctx.lineTo(x - 7, ay);
        ctx.lineTo(x - 7, ay - Math.min(13, h * 0.3));
        if (o.count === 1) {
          var by = ay + h * 0.18;
          ctx.moveTo(x, by);
          ctx.lineTo(x + 7, by);
          ctx.lineTo(x + 7, by - Math.min(11, h * 0.26));
        }
      }
    }
    ctx.stroke();
  }

  function drawBird(o) {
    var x = o.x + o.w / 2;
    var y = GROUND_Y - o.alt;
    var f = Math.sin(o.flap);
    var wing = 12 * f;
    ctx.lineWidth = 2;
    ctx.beginPath();
    // body
    ctx.moveTo(x - 12, y);
    ctx.lineTo(x + 10, y);
    // beak
    ctx.moveTo(x + 10, y);
    ctx.lineTo(x + 17, y + 3);
    // wings
    ctx.moveTo(x - 2, y);
    ctx.lineTo(x - 12, y - wing);
    ctx.moveTo(x - 2, y);
    ctx.lineTo(x + 6, y - wing * 0.8);
    // tail
    ctx.moveTo(x - 12, y);
    ctx.lineTo(x - 19, y - 5);
    ctx.stroke();
    // eye
    ctx.beginPath();
    ctx.arc(x + 7, y - 2, 1.2, 0, Math.PI * 2);
    ctx.stroke();
  }

  function drawDust(th) {
    ctx.save();
    ctx.strokeStyle = th.far;
    ctx.lineWidth = 1.5;
    for (var i = 0; i < game.dust.length; i++) {
      var d = game.dust[i];
      ctx.globalAlpha = Math.max(0, d.life) * 0.8;
      ctx.beginPath();
      ctx.arc(d.x, d.y, d.r, 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.restore();
  }

  // The runner: a head, a spine, two arms, two legs - all strokes.
  function drawPlayer(th) {
    var p = game.player;
    var baseY = GROUND_Y - p.y;

    ctx.save();
    ctx.translate(PLAYER_X, baseY);
    ctx.strokeStyle = th.ink;
    ctx.lineWidth = 2.4;

    if (p.dead) {
      ctx.rotate(p.deadSpin * 0.75);
      drawStanding(0, 0.4, true);
    } else if (p.ducking && p.onGround) {
      drawDucking();
    } else if (!p.onGround) {
      drawAirborne(p.vy);
    } else {
      drawStanding(game.runPhase, 1, false);
    }
    ctx.restore();
  }

  function head(cx, cy, dead) {
    ctx.beginPath();
    ctx.arc(cx, cy, 7, 0, Math.PI * 2);
    ctx.stroke();
    if (dead) {
      // Thin, small crosses - at head scale anything heavier fills the circle.
      ctx.save();
      ctx.lineWidth = 1.4;
      ctx.beginPath();
      ctx.moveTo(cx - 4.5, cy - 3); ctx.lineTo(cx - 1.5, cy);
      ctx.moveTo(cx - 1.5, cy - 3); ctx.lineTo(cx - 4.5, cy);
      ctx.moveTo(cx + 1.5, cy - 3); ctx.lineTo(cx + 4.5, cy);
      ctx.moveTo(cx + 4.5, cy - 3); ctx.lineTo(cx + 1.5, cy);
      ctx.stroke();
      ctx.restore();
    } else {
      ctx.beginPath();
      ctx.arc(cx + 3, cy - 1.5, 1.1, 0, Math.PI * 2);
      ctx.stroke();
    }
  }

  function drawStanding(phase, swing, dead) {
    var s = Math.sin(phase) * swing;
    var c = Math.cos(phase) * swing;
    var hipY = -22, shoulderY = -38;
    // A constant splay so the legs still read as two at phase 0 (the idle
    // pose on the start screen, and the collapsed pose after a crash).
    var splay = 3;

    head(0, -46, dead);
    ctx.beginPath();
    ctx.moveTo(0, -39);
    ctx.lineTo(1, hipY);
    // arms
    ctx.moveTo(0.5, shoulderY + 2);
    ctx.lineTo(9 * c + 2, shoulderY + 13 - 4 * s);
    ctx.moveTo(0.5, shoulderY + 2);
    ctx.lineTo(-9 * c + 2, shoulderY + 13 + 4 * s);
    // legs
    ctx.moveTo(1, hipY);
    ctx.lineTo(9 * s + splay, hipY + 12);
    ctx.lineTo(9 * s + splay + (s > 0 ? 3 : -1), 0);
    ctx.moveTo(1, hipY);
    ctx.lineTo(-9 * s - splay, hipY + 12);
    ctx.lineTo(-9 * s - splay + (s > 0 ? -1 : 3), 0);
    ctx.stroke();
  }

  function drawAirborne(vy) {
    var rising = vy < 0;
    var tuck = rising ? 1 : -1;

    head(0, -46, false);
    ctx.beginPath();
    ctx.moveTo(0, -39);
    ctx.lineTo(1, -22);
    // arms thrown up on the way up, out on the way down
    ctx.moveTo(0.5, -36);
    ctx.lineTo(11, -36 - 9 * tuck);
    ctx.moveTo(0.5, -36);
    ctx.lineTo(-10, -36 - 7 * tuck);
    // legs: tucked while rising, reaching while falling
    ctx.moveTo(1, -22);
    ctx.lineTo(11, -14 + 2 * tuck);
    ctx.lineTo(14, -3 - 4 * tuck);
    ctx.moveTo(1, -22);
    ctx.lineTo(-8, -12 - 4 * tuck);
    ctx.lineTo(-11, -1 - 2 * tuck);
    ctx.stroke();
  }

  function drawDucking() {
    var s = Math.sin(game.runPhase * 1.3) * 4;

    head(16, -20, false);
    ctx.beginPath();
    // flattened spine
    ctx.moveTo(10, -21);
    ctx.lineTo(-14, -16);
    // arm reaching forward
    ctx.moveTo(4, -20);
    ctx.lineTo(16, -9);
    // scrambling legs
    ctx.moveTo(-14, -16);
    ctx.lineTo(-6 + s, -8);
    ctx.lineTo(-2 + s, 0);
    ctx.moveTo(-14, -16);
    ctx.lineTo(-16 - s, -7);
    ctx.lineTo(-12 - s, 0);
    ctx.stroke();
  }

  function drawHud(th) {
    ctx.save();
    ctx.fillStyle = th.ink;
    ctx.strokeStyle = th.ink;
    ctx.font = '600 16px ui-monospace, Menlo, Consolas, monospace';
    ctx.textBaseline = 'top';

    var score = pad(game.score);
    ctx.textAlign = 'right';
    if (hiScore > 0) {
      ctx.globalAlpha = 0.45;
      ctx.fillText('HI ' + pad(hiScore), W - 96, 22);
      ctx.globalAlpha = 1;
    }
    // Blink the score briefly on each 100-point milestone.
    if (game.time < game.flashUntil && Math.floor(game.time * 8) % 2 === 0) {
      ctx.globalAlpha = 0.25;
    }
    ctx.fillText(score, W - 24, 22);
    ctx.globalAlpha = 1;

    ctx.textAlign = 'center';
    if (game.state === STATE.READY) {
      ctx.font = '600 15px ui-monospace, Menlo, Consolas, monospace';
      ctx.globalAlpha = 0.75 + Math.sin(game.time * 4) * 0.25;
      ctx.fillText('PRESS SPACE OR TAP TO RUN', W / 2, 118);
      ctx.globalAlpha = 1;
    } else if (game.state === STATE.PAUSED) {
      ctx.font = '600 18px ui-monospace, Menlo, Consolas, monospace';
      ctx.fillText('PAUSED', W / 2, 112);
      ctx.font = '600 13px ui-monospace, Menlo, Consolas, monospace';
      ctx.globalAlpha = 0.6;
      ctx.fillText('PRESS P TO RESUME', W / 2, 138);
      ctx.globalAlpha = 1;
    } else if (game.state === STATE.OVER) {
      ctx.font = '600 20px ui-monospace, Menlo, Consolas, monospace';
      ctx.fillText('G A M E  O V E R', W / 2, 100);
      ctx.font = '600 13px ui-monospace, Menlo, Consolas, monospace';
      ctx.globalAlpha = 0.6;
      ctx.fillText(
        game.score >= hiScore && game.score > 0
          ? 'NEW BEST - SPACE OR TAP TO RUN AGAIN'
          : 'SPACE OR TAP TO RUN AGAIN',
        W / 2, 130
      );
      ctx.globalAlpha = 1;
    }

    if (audio.muted) {
      ctx.textAlign = 'left';
      ctx.globalAlpha = 0.4;
      ctx.font = '600 12px ui-monospace, Menlo, Consolas, monospace';
      ctx.fillText('MUTED', 24, 24);
      ctx.globalAlpha = 1;
    }
    ctx.restore();
  }

  function pad(n) {
    var s = String(n);
    while (s.length < 5) s = '0' + s;
    return s;
  }

  // -------------------------------------------------------------------- input

  var input = { jumpHeld: false };

  function jump() {
    var p = game.player;
    if (game.state === STATE.READY) {
      game.state = STATE.RUNNING;
      game.time = 0;
    }
    if (game.state !== STATE.RUNNING) return;
    if (p.onGround) {
      p.vy = JUMP_V;
      p.onGround = false;
      p.ducking = false;
      spawnDust(4);
      beep(520, 0.07, 'square', 0.04);
    }
  }

  function press() {
    if (game.state === STATE.OVER) {
      // Small delay so the death frame is readable before a restart.
      if (game.player.deadSpin > 0.35) { reset(); jump(); }
      return;
    }
    jump();
  }

  function setDuck(on) {
    if (game.state !== STATE.RUNNING) return;
    game.player.ducking = on;
  }

  window.addEventListener('keydown', function (e) {
    var k = e.key;
    if (k === ' ' || k === 'Spacebar' || k === 'ArrowUp' || k === 'w' || k === 'W') {
      e.preventDefault();
      if (!input.jumpHeld) press();
      input.jumpHeld = true;
    } else if (k === 'ArrowDown' || k === 's' || k === 'S') {
      e.preventDefault();
      setDuck(true);
    } else if (k === 'p' || k === 'P') {
      if (game.state === STATE.RUNNING) game.state = STATE.PAUSED;
      else if (game.state === STATE.PAUSED) game.state = STATE.RUNNING;
    } else if (k === 'm' || k === 'M') {
      audio.muted = !audio.muted;
    } else if (k === 'Enter' && game.state === STATE.OVER) {
      reset();
    }
  });

  window.addEventListener('keyup', function (e) {
    var k = e.key;
    if (k === ' ' || k === 'Spacebar' || k === 'ArrowUp' || k === 'w' || k === 'W') {
      input.jumpHeld = false;
      var p = game.player;
      if (p.vy < CUT_V) p.vy = CUT_V;   // release early, hop shorter
    } else if (k === 'ArrowDown' || k === 's' || k === 'S') {
      setDuck(false);
    }
  });

  // Pointer: top two thirds of the canvas jump, bottom third ducks.
  canvas.addEventListener('pointerdown', function (e) {
    e.preventDefault();
    canvas.setPointerCapture(e.pointerId);
    var r = canvas.getBoundingClientRect();
    var y = (e.clientY - r.top) / r.height;
    if (y > 0.68 && game.state === STATE.RUNNING) {
      setDuck(true);
    } else {
      input.jumpHeld = true;
      press();
    }
  });

  function releasePointer() {
    if (input.jumpHeld) {
      input.jumpHeld = false;
      var p = game.player;
      if (p.vy < CUT_V) p.vy = CUT_V;
    }
    setDuck(false);
  }
  canvas.addEventListener('pointerup', releasePointer);
  canvas.addEventListener('pointercancel', releasePointer);
  canvas.addEventListener('contextmenu', function (e) { e.preventDefault(); });

  window.addEventListener('blur', function () {
    input.jumpHeld = false;
    if (game.state === STATE.RUNNING) game.state = STATE.PAUSED;
  });

  // --------------------------------------------------------------------- loop

  var last = 0;

  function frame(now) {
    if (!last) last = now;
    // Clamp so a background tab or a slow frame can't teleport the runner
    // through an obstacle.
    var dt = Math.min((now - last) / 1000, 0.05);
    last = now;

    // Fixed-step integration keeps physics identical across refresh rates.
    var step = 1 / 120;
    var acc = dt;
    while (acc > 0) {
      var s = Math.min(step, acc);
      update(s);
      acc -= s;
    }

    draw();
    requestAnimationFrame(frame);
  }

  reset();
  requestAnimationFrame(frame);
})();
