# Android app (native)

A native Kotlin + Jetpack Compose client. Unlike the desktop shell — a window
onto the deployed web app — this one draws its own screens and talks to the same
backend the web app does: `POST /api/rpc/<command>` against the Cloudflare
Worker, authenticated with the same `session` cookie.

**The backend is untouched by this module.** No new endpoints, no Android-only
API. That is deliberate: money math lives in Rust on the server, and the phone
renders what it is told, exactly like the web frontend does.

## What that costs

This is a second UI. Every feature from here on has to be built twice — once in
React for web/desktop/iPhone, once in Compose for Android — and the two can
drift. The web app stays the reference implementation: when they disagree, the
web app is right.

## Layout

```
app/src/main/java/com/asura/finanzas/
  BrokeApp.kt          three long-lived objects, wired by hand (no DI framework)
  MainActivity.kt      splash → AppRoot
  data/
    Rpc.kt             POST /api/rpc/<command>; mirrors src/lib/api.ts
    SessionCookieJar   persists the one session cookie so the phone stays signed in
    JsonCache.kt       last-synced response per screen, for opening without signal
    BrokeRepository    the app's only door to the backend
    Models.kt          wire shapes; mirror src/lib/types.ts
  ui/
    theme/             the web app's "neon glass" tokens, ported to Compose
    components/        glass card, hero amount, load/error states
    auth · dashboard · wallets · transactions · settings
```

`Money.kt` formats integer cents and does nothing else. If you ever find
yourself adding, scaling or converting money in Kotlin, stop: that belongs in
`finanzas-core`.

## Auth

The worker's CSRF check only validates `Origin` when the header is present
(`worker/src/auth`: `check_origin`), so a native client that sends none
authenticates with the same cookie flow as the browser. Nothing was relaxed
server-side to make this work.

## Offline

Today: reads fall back to the last synced copy when the request never reaches
the server, behind a visible "sin conexión" marker — the same deal the web app's
persisted query cache makes. Writes are still online-only.

Full offline capture is not built yet. When it is, the server already supports
the hard half: `add_income` / `add_expense` / `add_transfer` accept a `clientId`
that makes a retry idempotent (unique index on `transactions.client_id`), which
is what the web outbox uses.

## Signing key

The release keystore is **not in this repository** (it is public). It lives at
`~/.local/share/finanzas-android/broke-release.jks`, password in `password.txt`
next to it.

> Back that directory up. Android identifies an app by its signing key: lose it
> and no future APK can install as an *update* over the ones people already have.

CI reads the same key from repository secrets:

| Secret | Contents |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 broke-release.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | the keystore password |
| `ANDROID_KEY_ALIAS` | `broke` |

## Building

Releases are built by `.github/workflows/release.yml` on a version tag and
attached to the GitHub Release as `Broke_<version>_android.apk`. The version
comes from `package.json` — `versionName` verbatim, `versionCode` packed as
`major*10000 + minor*100 + patch` (2.39.0 → 23900).

Locally (needs JDK 17–21 and the Android SDK; JDK 26 is too new for AGP 8.7):

```sh
cd android
JAVA_HOME=/usr/lib/jvm/java-21-openjdk ./gradlew assembleDebug

# signed release
JAVA_HOME=/usr/lib/jvm/java-21-openjdk \
BROKE_KEYSTORE="$HOME/.local/share/finanzas-android/broke-release.jks" \
BROKE_KEYSTORE_PASSWORD="$(cat $HOME/.local/share/finanzas-android/password.txt)" \
./gradlew assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

Point a debug build at a local worker with
`./gradlew assembleDebug -PbrokeApiBase=http://10.0.2.2:8787` (10.0.2.2 is the
host as seen from the emulator). Install with `adb install -r <apk>`.

`lintVital` is off for release builds: AGP 8.7's lint worker crashes with
`NoClassDefFoundError: com/intellij/psi/*` on the JDKs we build with, which would
fail every release. Run it on demand with `./gradlew lintDebug`.

## Icons

Generated from `public/icon-512.png` and `public/icon-512-maskable.png`, so the
launcher icon matches the PWA's — legacy `mipmap-*/ic_launcher.png` (48→192 px),
adaptive `ic_launcher_foreground.png` on a 108 dp canvas, and the splash
`drawable-*/splash.png`.
