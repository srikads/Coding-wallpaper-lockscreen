# CodeWall

An Android live wallpaper (home **and** lock screen) that shows your day as live, syntax-highlighted JSON.

```jsonc
// ~/today.json
{
  "date": "Wed, 07 Oct 2026",
  "time": "14:32:07",
  "weather": {
    "temp": 21.4,
    "feels_like": 20.8,
    "unit": "°C",
    "condition": "partly_cloudy",
    "high": 24, "low": 15, ...
  },
  "tasks": [
    { "done": false, "title": "Review PR #42" },
    { "done": true, "title": "Gym" }
  ],
  "events": ["09:30 Standup", "16:00 1:1"],
  "battery": { "level": 82, "charging": false, "temp_c": 29.5 },
  "quote": "fix: it was DNS"
}▌
```

The seconds tick live, the text types itself in when the screen turns on, and a line flashes when its value changes.

## Features

| Field | Source | Permission |
|---|---|---|
| `date`, `time` (with seconds, 12/24h, custom date pattern) | system clock | none |
| `weather` (temp, feels like, condition, high/low, humidity, wind, rain chance, sunrise/sunset) | [Open-Meteo](https://open-meteo.com) | approximate location, or manual coordinates |
| `tasks` (today, tomorrow, until done, every day) | in-app task list | none |
| `events` (today) and `next` (countdown) | device calendar | calendar (read) |
| `alarm` | next system alarm | none |
| `battery`, `device` (RAM, storage, uptime) | system | none |
| `steps` (today) | hardware step counter | physical activity |
| `playing` (title, artist) | active media session | notification access |
| `network` (type, VPN, local IP) | system | none |
| `quote` (quotes, dev jokes, commit messages) | bundled offline | none |

### Customization

- **Fields**: turn each one on or off, reorder them, rename the JSON keys, and choose whether it shows on the lock screen. Tasks, events, network and now playing are hidden on the lock screen by default.
- **Themes**: Dracula, Monokai, One Dark, Matrix, AMOLED black.
- **Font**: size, bold, or import your own `.ttf`/`.otf` (JetBrains Mono, Fira Code, …).
- **Screen areas**: separate code areas for the home and lock screen. The lock-screen default (30%–70% of the height) sits between the Pixel clock and the fingerprint icon. The quote can move to a footer slot below the fingerprint (81%–91%), either on the lock screen only or everywhere. "Compact objects" puts nested objects on one line so more fits.
- **Layout**: vertical position, side padding, left or centered alignment, line numbers, and the header comment. Text wraps and shrinks to fit the screen.
- **Animations**: typing effect, blinking cursor, highlight on change, parallax.
- **Template mode**: write any code shape you like with `{{placeholders}}`:

  ```ts
  const today = {
    time: "{{time}}",
    weather: "{{weather.temp}}° {{weather.condition}}",
    tasks: {{tasks}},
  };
  ```

## Privacy

CodeWall is built to keep your data on your phone.

- No accounts, analytics, ads, crash reporting or trackers.
- Backups and device transfer are turned off (`allowBackup=false` plus data extraction rules).
- The **only** network request is the weather forecast, sent over HTTPS to `api.open-meteo.com`. It carries your latitude and longitude **rounded to 1 decimal (about 11 km)** and nothing else. If you set manual coordinates, GPS is never used. If you turn weather off, the app makes no network requests at all.
- Calendar is read-only. Notification access is used only to read the current media session; notification content is ignored.
- Cleartext HTTP is blocked by the network security config.

## Lock screen

Android doesn't let third-party apps replace the lock screen. CodeWall is a live wallpaper instead. When you tap **Set as live wallpaper**, pick **Home and lock screen** in the system dialog, and the same live view, seconds included, shows behind your lock screen. Fields marked "hidden on lock" disappear while the device is locked and type themselves back in when you unlock.

> Some OEM skins (for example certain MIUI/HyperOS and One UI versions) only allow static images on the lock screen. On those devices the live wallpaper shows on the home screen only.

## Build

Requirements: JDK 17 and the Android SDK (API 35).

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # JVM unit tests for the rendering core
```

GitHub Actions builds debug and release APKs on every push; download them from the run's artifacts. Pushing a `v*` tag attaches the APK to a GitHub release.

Min SDK 26 (Android 8.0) · Kotlin · Jetpack Compose (settings UI) · Canvas (wallpaper rendering).

## Project layout

```
app/src/main/java/com/srikads/codewall/
  core/       pure Kotlin: JSON model, printer, template engine, themes, config, tasks (unit-tested)
  data/       on-device data sources and the Open-Meteo client
  render/     Canvas renderer, shared controller, in-app preview view
  wallpaper/  WallpaperService engine
  ui/         Compose settings app (Preview, Fields, Style, Tasks)
```

## Author

**srikads** · MIT License
