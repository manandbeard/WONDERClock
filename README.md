# WonderClock

A modern, fully customizable clock widget for Android.

Six clock faces, every colour, gradients, outlines, glows, the date in any
format, and a row of extras — battery, next alarm, a second time zone, a
countdown, a progress bar. Everything is editable per widget, and the settings
screen previews your changes using the exact renderer the home screen uses, so
what you design is literally what you get.

## Faces

| Face | What it looks like |
| --- | --- |
| **Digital** | `10:24` on one line, with optional AM/PM raised, lowered, inline, or on its own line |
| **Stacked** | Hours over minutes, sized as one block |
| **Analog** | Seven dial styles (ticks, fine ticks, dots, numbers, roman numerals, quarters, bare) and five hand styles |
| **Words** | "it is half past ten", balanced across up to four lines, or read out exactly ("ten twenty three") |
| **Letter grid** | The classic 11×10 matrix with the relevant words lit up |
| **Binary** | Binary-coded decimal in dots, squares, or rings, with optional decimal labels |

## What you can change

**Type** — thirteen system font families, bold, italic, size, letter spacing,
and a size dial on top of the automatic fit.

**Colour** — a full HSV picker with an alpha channel and a hex field on every
single colour. Separate hour / minute / separator colours, two-stop gradients
at any angle, solid / outline / solid-plus-outline fills, and a drop shadow or
glow with its own blur, offset and colour. One switch swaps every hue for the
wallpaper palette on Android 12+ while keeping your transparency.

**Background** — none, solid, or gradient, with corner radius from square to
a full circle, and a border.

**Date** — any `DateTimeFormatter` pattern (with a row of live-previewed
presets), above or below the time, its own colour, size, font, weight,
capitalisation and tracking.

**Extras** — pick a module for the row above and the row below: date, weekday,
battery, next alarm, week number, a second time zone, day or year progress, a
countdown to a date, or your own text.

**Progress bar** — tracks the hour, day, week, month, year, or battery, drawn
as a bar, dots, or segments.

**Behaviour** — a per-widget time zone, and a tap action (clock, alarms,
timers, calendar, this widget's settings, or any installed app). Give the
bottom of the widget a *different* action and the lower third becomes its own
tap target.

**Presets** — eighteen finished looks to start from, plus your own saved
presets, plus copy/paste of any config as JSON.

## Building

Requires JDK 17 and the Android SDK (compileSdk 35).

```bash
./gradlew assembleDebug
```

Install with `./gradlew installDebug`, then add a widget from the launcher's
widget list ("WonderClock · Digital", "· Analog" or "· Words") or use the
**Add a widget** button in the app.

## How it works

`ClockRenderer.render(config, widthPx, heightPx, env)` draws a clock into a
`Bitmap` and is the only thing in the project that knows what a clock looks
like. The widget pushes that bitmap through `RemoteViews.setImageViewBitmap`;
the settings screen draws the same bitmap into a Compose `Image`. There is no
second preview implementation that could drift out of sync.

Rendering is a pure function. Everything time- or device-dependent — the
instant, the battery level, the next alarm, the locale, the wallpaper palette —
arrives in a `RenderEnv`, which is also what lets previews show plausible
sample data on a device that has no alarm set.

Sizes in a config are stored as *fractions* rather than dp — padding as a
fraction of the short side, the date row as a fraction of the content height,
outline width as a fraction of the text size. A config therefore looks like
itself at 2×2 and at 5×5. Text is measured once at a reference size and scaled,
which lands an exact fit in one pass because every one of those quantities is
linear in the text size.

On Android 12+ the widget publishes a `RemoteViews` per launcher-reported size,
so rotating or resizing swaps layouts without a round trip to the app.

### Updates and battery

Widgets redraw once a minute, driven by a one-shot `AlarmManager` alarm that
re-arms itself. It uses `RTC` rather than `RTC_WAKEUP`: there is no reason to
wake a sleeping phone to redraw something nobody is looking at. The framework's
own half-hourly update is the backstop, and a reboot, time change, time-zone
change, locale change or new alarm all trigger an immediate redraw.

Granting the optional exact-alarm permission makes the clock flip exactly on
the minute; without it the widget still works, it just drifts by the slop the
system allows itself. The app links straight to that setting.

**Seconds are deliberately not offered.** A widget that ticked every second
would cost far more battery than it is worth, and a second hand that only moved
once a minute would look broken.

## Project layout

```
data/     ClockConfig (the whole model), ConfigStore (per-widget JSON in
          SharedPreferences), Presets
render/   ClockRenderer + the faces, RenderKit (text fitting and drawing),
          Palette (dynamic colour), TimeText, WordClock
widget/   AppWidgetProviders, WidgetUpdater, TickScheduler, receivers,
          TapActions, WidgetPinner
ui/       MainActivity, ConfigActivity, Compose screens and controls
```

`minSdk` is 26, so `java.time` is used directly with no desugaring.

## Licence

MIT.
