# Ilia Companion — Android app

A native Android app that mirrors your desktop companion pet in real time:
a draggable floating overlay (same permission class Messenger's chat heads
use) that shows your pet's current activity, reacts to taps, and a stats
screen with your level/XP/streak. Talks to your own Cloudflare relay (see
`../cloud/README.md`) over WebSocket.

**Scope** — worth knowing up front:
- The floating pet has native art for six companions: the four desktop
  themes (Clawd, Cozy Cat, Calico, Cloudling) plus two Android-first ones
  (Sprig the frog and Pippin the penguin, `pet_sprig_*`/`pet_pippin_*`
  drawables), each with per-activity states matching the desktop's
  displayHintMap. Companions can wear accessories (`cosmetic_*` drawables,
  anchored per theme via `PetThemes.hatAnchorFor`), and the floating pet's
  size is adjustable from the main screen (48–144dp slider).
- iOS is not supported and can't be, by design: iOS has no API for a
  persistent overlay over other apps. Android allows it via the same
  "draw over other apps" permission Messenger uses.
- Sync freshness is real-time (WebSocket push), not polling.

## One-time setup

1. **Deploy the cloud relay first** — see `../cloud/README.md`. You'll need
   its URL (e.g. `https://ilia-companion-sync.you.workers.dev`).
2. **Open this `android/` folder in Android Studio** (free, from
   developer.android.com/studio). Android Studio will detect the missing
   Gradle wrapper jar and offer to generate it automatically on first open —
   accept that prompt. (If you'd rather use the command line and already
   have Gradle installed, run `gradle wrapper --gradle-version 8.7` once
   from this directory to generate `gradlew`/`gradlew.bat` yourself.)
3. Let Gradle sync (downloads dependencies: AndroidX, Material, OkHttp).
4. Connect your Android phone via USB with USB debugging enabled (Settings
   > About phone > tap Build number 7 times > Developer options > USB
   debugging), or use an emulator, and click Run in Android Studio. This
   installs a debug build, already signed with Android Studio's own debug
   key — no extra setup needed for personal use on your own device.

## Pairing to your desktop

1. In the desktop app: Settings > Companion > Phone Sync > enable it, paste
   your relay URL, click "Generate Pairing Code".
2. In the Android app: paste the same relay URL and pairing code, tap Save.
3. Tap "Start Floating Pet" — grant the overlay permission when prompted
   (Android will show a system settings screen; toggle it on and go back).
4. The pet appears as a small floating bubble you can drag anywhere on
   screen and tap to poke. It updates live as your desktop companion's
   activity changes.

## Building a release APK (optional, for installing without a USB cable)

Debug builds (Android Studio's default Run button) are perfectly fine for
personal use in the meantime -- this section is only if you want a signed
release APK you can install without a USB cable / Android Studio each time.

1. Generate a local signing keystore once (keep it safe — same key is
   needed to update the app later):
   ```
   keytool -genkeypair -v -keystore ilia-release.keystore -alias ilia -keyalg RSA -keysize 2048 -validity 10000
   ```
2. Add these to your **global** `~/.gradle/gradle.properties` (not this
   repo, so the keystore path/passwords never get committed):
   ```
   ILIA_RELEASE_STORE_FILE=/absolute/path/to/ilia-release.keystore
   ILIA_RELEASE_STORE_PASSWORD=your-store-password
   ILIA_RELEASE_KEY_ALIAS=ilia
   ILIA_RELEASE_KEY_PASSWORD=your-key-password
   ```
3. Build: `./gradlew assembleRelease` (or Android Studio's Build > Generate
   Signed App Bundle/APK). The signed APK lands in
   `app/build/outputs/apk/release/app-release.apk` — copy it to your phone
   and tap to install (you'll need to allow "install unknown apps" for
   whichever app you copied it through, e.g. Files or a browser).

There's no Play Store listing (that needs a paid Google Play developer
account) — sideloading the APK directly is the intended install path for
personal use.

## Architecture notes

- `RelayClient.kt` — OkHttp WebSocket client, mirrors
  `scripts/companion-cloud-sync.js`'s reconnect/backoff behavior for
  consistency with the desktop side.
- `OverlayService.kt` — foreground service owning both the floating
  `WindowManager` overlay view and the one WebSocket connection; keeps
  running independently of whether `MainActivity` is open.
- `MainActivity.kt` — pairing form, overlay permission flow, and a live
  stats screen (binds to `OverlayService` while visible).
- Tapping the floating pet sends a cosmetic-only `{"type":"reaction"}`
  message back through the relay to the desktop (see
  `handleCloudReaction` in `scripts/companion-watcher.js`) — never mutates
  real XP/level, which stays desktop-authoritative.
