# NuvioBridge

NuvioBridge is a personal Android TV / Google TV bridge that opens compatible launcher recommendations directly in **Nuvio**.

## What it does

- Detects compatible movie and show recommendation clicks from the TV launcher.
- Resolves the selected title through TMDB.
- Opens the matching movie or series directly in Nuvio.
- Requires no subscription, payment account, Stripe backend, or license server.

## Requirements

- Android TV / Google TV device.
- Nuvio installed on the device.
- A TMDB API key for the build.

## Build configuration

For local builds, add your TMDB key to `local.properties`:

```properties
TMDB_API_KEY=your_tmdb_api_key
```

CI can also read the key from the `TMDB_API_KEY` environment variable.

## Build

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is generated at:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub CI runs the unit tests, assembles the debug APK, and uploads it as the `NuvioBridge-debug` artifact.

## Enable the Accessibility service

After installing the APK, open NuvioBridge and choose **Enable NuvioBridge in Accessibility**.

On some sideloaded Android TV / Google TV devices, Android may block Accessibility until restricted settings are allowed for the app.

## Direct Nuvio deep-link check

A known movie link can be tested through ADB:

```bash
adb shell am start -a android.intent.action.VIEW -d "nuvio://movie/tt0371746"
```

If that opens Nuvio correctly, the Nuvio deep-link handler is working independently of the recommendation-detection layer.

## TMDB attribution

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Scope

NuvioBridge is a navigation/automation utility and does not host, provide, distribute, or control audiovisual content or content sources.
