# Stick Run

A line-art endless runner in the spirit of the Chrome dino game. A stick figure
runs, cacti and birds come at it, and it gets faster until you miss one.

Everything on screen — the runner, the obstacles, the clouds, the moon — is
drawn with canvas strokes. No images, no libraries, no build step.

## Play

Open `index.html` in a browser. That's it.

To serve it over HTTP instead (handy on mobile over a LAN):

```
npx http-server . -p 8080
```

## Controls

| Action  | Keys                        | Touch / mouse            |
| ------- | --------------------------- | ------------------------ |
| Jump    | `Space`, `↑`, `W`           | Tap the upper canvas     |
| Duck    | `↓`, `S`                    | Tap the lower third      |
| Pause   | `P`                         | —                        |
| Mute    | `M`                         | —                        |
| Restart | `Space` or `Enter` when out | Tap                      |

Holding jump goes higher — a tap peaks around 65px, a full hold around 125px.
Ducking in mid-air drops you fast, which is the quick way back to the ground.

## How it plays

- Speed ramps from 330 px/s to 820 px/s over about a minute, and obstacle gaps
  scale with it so the spacing stays clearable.
- Cacti come in clusters of one to three, short or tall.
- Birds appear past 320 points in three lanes: low ones must be jumped,
  chest-height ones can be ducked or jumped, and high ones pass overhead — so
  jumping at one is how it gets you.
- Day flips to night every 900 points.
- Your best score is kept in `localStorage`.

## Layout

| File         | What's in it                                            |
| ------------ | ------------------------------------------------------- |
| `index.html` | Canvas and the control hint line                        |
| `style.css`  | Page chrome, with a dark-mode variant                   |
| `game.js`    | The whole game: state, physics, drawing, input          |

`game.js` is one IIFE, split into commented sections (constants, state,
obstacles, update, draw, input, loop). The tuning constants are all at the top.

Physics runs on a fixed 1/120s timestep inside a frame-rate-independent loop,
so the game behaves the same on a 60Hz and a 144Hz display, and a stalled tab
can't fling the runner through an obstacle.
