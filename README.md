# 🚌 BusLive

Live Israeli buses on a map, stop arrivals, line timetables, trip planning and get-off alerts.
Native Android (Kotlin + Jetpack Compose). Fast, light, **no ads, no tracking, no analytics, no account**.

Hebrew + English (full RTL), light + dark mode, works offline with cached stops and lines.

---

## 📦 Get the APK

### Option A – build in the cloud (no SDK needed)
Every push runs **GitHub Actions → “Build APK”** (`.github/workflows/android.yml`).
Open the run → **Artifacts**:

| Artifact | What |
|---|---|
| `buslive-debug-apk` | `app-debug.apk` (one file, all CPUs, ~75 MB) |
| `buslive-release-apks` | minified APKs per CPU. Most phones: **`app-arm64-v8a-release.apk` (~17 MB)**. `app-universal-release.apk` works everywhere. |

You can also start it by hand: Actions → Build APK → *Run workflow*.

### Option B – build locally

```bash
git clone https://github.com/markovo1234/buslive.git
cd buslive
./gradlew assembleDebug          # Windows: gradlew.bat assembleDebug
```

**APK path:** `app/build/outputs/apk/debug/app-debug.apk`

Light release build (minified, signed with the debug key so it installs directly):

```bash
./gradlew assembleRelease
# app/build/outputs/apk/release/app-arm64-v8a-release.apk   ← most phones
# app/build/outputs/apk/release/app-universal-release.apk   ← any phone
```

Unit tests: `./gradlew testDebugUnitTest`

#### Requirements
* **JDK 17 or newer** (tested with 21)
* **Android SDK** with platform **API 37** (AGP downloads platform + build-tools by itself once licenses are accepted)
* Internet for the first build (Gradle 9.8.2 wrapper and dependencies download automatically)

<details>
<summary><b>Install JDK + Android SDK – Ubuntu / Debian</b></summary>

```bash
sudo apt update && sudo apt install -y openjdk-21-jdk unzip wget
mkdir -p ~/Android/Sdk/cmdline-tools && cd ~/Android/Sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip
unzip commandlinetools-linux-*_latest.zip && mv cmdline-tools latest
echo 'export ANDROID_HOME=$HOME/Android/Sdk' >> ~/.bashrc
echo 'export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools' >> ~/.bashrc
source ~/.bashrc
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
```
Then in the project: `echo "sdk.dir=$ANDROID_HOME" > local.properties`
</details>

<details>
<summary><b>macOS</b></summary>

```bash
brew install --cask temurin@21 android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties
```
</details>

<details>
<summary><b>Windows</b></summary>

Easiest: install **Android Studio** (it brings a JDK and the SDK), open the project once, let it sync, then
`gradlew.bat assembleDebug`. Or install Temurin JDK 21 + the command-line tools and run the same `sdkmanager`
commands as above in PowerShell, then put `sdk.dir=C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk` in `local.properties`.
</details>

### Install on the phone
* **USB:** enable *Developer options → USB debugging*, then `adb install -r app/build/outputs/apk/debug/app-debug.apk`
* **File:** copy the APK to the phone, open it, allow *Install unknown apps* for your file manager.

Debug and release builds have different app ids (`com.buslive.app.debug` / `com.buslive.app`), so both can be installed side by side.

---

## 📱 Screens & features

### 🗺️ Map (start screen)
* Map centered on you (asks politely; without permission it opens at your last spot or Tel Aviv).
* **Live buses**: line number badge, colored by operator, arrow rotated by heading. Markers **glide smoothly** between updates.
* **Freshness**: “Live buses: N · updated X sec ago · GPS X min ago”. Markers older than 3 min are faded, older than 7 min hidden.
* Auto-refresh every 15 s **only while the map is visible** (stops in background). Data saver: it first checks a 1 KB
  “new data?” call and only downloads positions when the server has a new snapshot or you moved the map.
* Filter chips: **All buses / My lines / one line** (with the route drawn and its stops).
* Tap a bus → sheet: line, operator, destination, last update, speed, **next stops with planned + live ETA**,
  **Follow this bus** (camera tracks it), show line, schedule, 🔔 *alert me at this stop*.
* **Stops** appear from zoom 13 (clustered at mid zoom, single stops + names when zoomed in).
* Tap a stop → sheet: name + code, ⭐, every line with its **next arrivals** (planned + **LIVE · estimate** when a live bus
  of that trip is on the way), remind me 5 min before, schedule, show line on map, directions.
* Long-press the map → dropped pin: directions to here, save as Home / Work / place.
* ⓘ legend explains live vs. planned data.

### 📍 Near me
Closest stops (distance + walking minutes) with the next buses per line, live tags, pull to refresh, auto refresh 30 s.

### 🔎 Search (top of the map)
One box for **line number, stop name, stop code, place or address**. Results grouped **Lines / Stops / Places**.
Places use Nominatim politely (600 ms debounce, 3+ letters, max 1 request/s, cached, real User-Agent). Recent searches.

### ⭐ Favorites
Home / Work / custom places (tap → directions), favorite stops as a **live dashboard**, favorite lines.
⋮ on a stop → *Show on home-screen widget*.

### 🧩 Home-screen widget
“Next buses” widget (Glance) for one favorite stop: line, destination, time (● = live estimate). Tap ⟳ to refresh;
it also refreshes every 15 min in the background.

### 🕐 Line screen
Both directions (and alternatives) as tabs, **Today / Tomorrow / pick any date**, full stop list with the planned times
of the chosen trip, departures grid by hour, **live buses shown along the stop list**, ⭐, show on map, 🔔 alert at a stop.
Future dates are not published by the API, so a future day shows the same weekday of an earlier week (clearly labeled).

### 🗺️ Plan (A → B)
From **my location** (default), a saved place, stop or address → to a destination (search, saved place, or map long-press).
Leave now or at a time. Options sorted by arrival time with **walk / wait / ride minutes, stop count, total time,
transfers**, map preview, recent trips.
* Built-in planner: **direct buses** (stop near A and a later stop near B on the same trip → always the right direction)
  and **one-transfer** trips (same or nearby stop, 2 min buffer).
* Optional **OpenTripPlanner**: *Settings → Trip planner server*, enter e.g. `http://192.168.1.10:8080`. BusLive calls
  OTP 2's GraphQL API (`/otp/gtfs/v1`, fallback `/otp/routers/default/index/graphql`) and falls back to the built-in planner
  if the server does not answer. *Test* plans a sample Tel Aviv trip.

### 🧭 Live directions
**Start** on a trip → foreground service with an ongoing notification (works with the screen locked):
walk to the stop (distance + minutes) → wait (planned or live bus ETA + bus distance) → on board (**stops left + ETA**)
→ **“Get ready – 2 stops left”** and **“Get off now!”** notifications with vibration, from your GPS vs. the stop list.
Battery friendly: GPS every 30 s when far, 10 s mid-range, 3 s close to the stop.
Quick mode: 🔔 next to any stop in a bus sheet or line screen = *alert me at stop X* without planning.
**Share my trip**: line, stop and ETA via the Android share sheet.

### ⚙️ Settings
Language (system / English / עברית), theme (system / light / dark), stops & lines cache status + refresh,
**download the map around you for offline use** (~9 km, zoom 10–15), OTP server URL, about & credits.

### ✨ Also
Arrival reminders, recent searches + recent trips, offline banner, loading / empty / error states with retry,
offline map region, freshness legend, adaptive app icon (bus inside a location pin) + splash screen,
content descriptions, large-text friendly layouts.

---

## 🛰️ Data sources & credits

* **Open Bus Stride API** by **The Public Knowledge Workshop (הסדנא לידע ציבורי / Hasadna)** –
  <https://open-bus-stride-api.hasadna.org.il> – planned GTFS + real-time SIRI from the Israel Ministry of Transport. Thank you! 🙏
* Map tiles: **OpenFreeMap** (liberty / dark styles), © OpenMapTiles, © OpenStreetMap contributors. Rendering: **MapLibre**.
* Place search: **Nominatim** / © OpenStreetMap contributors.

Verified endpoints, parameters and quirks (with real sample responses) are documented in
[`docs/API_NOTES.md`](docs/API_NOTES.md). Highlights:
* SIRI positions arrive once a minute and ~2–4 minutes late → the app asks for the last 6 minutes and labels age honestly.
* Some rows are dated 2038 → every query has an upper time bound.
* No GTFS shapes endpoint → route lines are drawn through the stops.
* Only today's and past timetables exist.
* Live ETA = remaining distance along the route ÷ (planned speed mixed with the bus's reported speed). Always labeled *estimate*.

Not included (no free official source found): ticket payment, Rav-Kav, crowding, official service alerts
(the MOT's own feeds need a registration key).

---

## 🏗️ Tech

Kotlin 2.4 · Jetpack Compose + Material 3 · MVVM + Coroutines/Flow · Hilt · Retrofit + OkHttp + kotlinx.serialization ·
Room · DataStore · WorkManager · Glance · MapLibre Android 13 · FusedLocationProvider · AGP 9.5 · Gradle 9.8 ·
minSdk 26, target/compile SDK 37.

```
app/src/main/java/com/buslive/app/
├── data/      api (Stride, Nominatim) · db (Room) · prefs (DataStore) · repo (static, live, schedule, places, favorites, location, offline map)
├── domain/    models · ArrivalEstimator · planner (TripPlanner, LocalTripPlanner, OtpTripPlanner) · utils (time zone, geo)
├── service/   GuidanceService + GuidanceManager · alerts · reminders · daily sync worker · widget
└── ui/        map · stop · nearby · search · favorites · line · planner · guidance · settings · theme · navigation
```

Time zone is `Asia/Jerusalem` everywhere. Stops + lines are cached in Room and refreshed daily (WorkManager); the app
works offline with the cache.

## 🔒 Privacy
No ads, no analytics, no crash reporting, no account. Location and favorites never leave the phone. The only network
calls go to the Stride API, OpenFreeMap tiles, Nominatim (when you search a place) and your own OTP server (if set).
