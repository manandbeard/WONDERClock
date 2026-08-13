# Stick Figure Fight

A tiny two-player fighting game written in plain JavaScript — no libraries, no build
step, one file. Made as a classroom example.

## How to play

Open `index.html` in a web browser.

| | Move left | Move right | Punch |
|---|---|---|---|
| Player 1 | `A` | `D` | `W` |
| Player 2 | `←` | `→` | `↑` |

Each fighter is drawn with three lines (legs, body, arms) and has three hearts.
Land a punch while you are next to your opponent and they lose a heart. Lose all
three and the other player wins. Press `R` to play again.

## How the code is organized

`index.html` holds everything, split into five commented sections:

1. **Setup** — the canvas, the constants, and the two player objects.
2. **Keyboard** — a `keys` object that remembers which keys are held down.
3. **Game rules** — moving, punching, and losing hearts.
4. **Drawing** — the three lines that make a stick figure, plus the hearts.
5. **The game loop** — update, draw, repeat, about 60 times a second.

## Things for students to try

- Change `SPEED` or `REACH` at the top and see how the game feels.
- Give each player five hearts instead of three.
- Make the punch push the opponent further back.
- Add a kick on a second key that takes two hearts but is slower.
- Add jumping with gravity.
- Draw a face on the head, or give each fighter a hat.
