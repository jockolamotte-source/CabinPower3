# Cabin Power 3 — fresh Android install

**New standalone app.** Android application ID `com.cabinpower.solar3`. This is NOT an update to Cabin Power or Cabin Power 2. It installs alongside them and has its own encrypted login and local history.

## Build using Code on the Go (Samsung)

1. In My Files > Downloads, extract `CabinPower3-FreshInstall-CodeOnTheGo.zip`.
2. Open Code on the Go > Open existing project, selecting the `CabinPower3Fresh` folder (the folder that directly contains `settings.gradle`).
3. Wait for Gradle to initialize; run/build the `app` module.
4. Install and launch **Cabin Power 3** from the app drawer.
5. Log in to Enerwise with your CURRENT password (rotate previously exposed credentials).
6. Allow notifications if you want alert banners; check Alerts and Settings in the app.

**Do not** open any `.gradle`, `app/build`, or previous extracted folder. The ZIP intentionally excludes build outputs and caches.

## Features

- Real readings using authenticated read-only calls to the observed Enerwise cloud endpoint.
- Home, Trends, Alerts, Device, Settings pages.
- Local SQLite sampling, with graphs for 1 / 7 / 30 / 90 days, and CSV export.
- Encrypted credentials in Android Keystore (unique app storage).
- Refresh every 30 seconds while open; Android periodic background polling with minimum 15-minute interval (subject to Doze and power settings).
- Low SOC, temperature, and offline alerts (subject to background execution and Android notification permission).
- Uses no Raspberry Pi or separate server. Internet/Enerwise cloud is required.

## Technical identifiers

- Application ID and Java namespace: `com.cabinpower.solar3`
- App label: `Cabin Power 3`
- Version: 3.0.0; versionCode: 1
- INTERNET + ACCESS_NETWORK_STATE permissions declared.
- AGP 9.3.1 / Gradle 9.6.1; Java 17 source compatibility.

## Limitations

The ZIP contains **source**, not a compiled APK. This environment cannot build/test an Android APK. Code on the Go must compile it. Background schedule is not guaranteed to run precisely every 15 minutes; the OS may defer or stop jobs. Historical graphs start recording after the first successful fetch. Remote changes to inverter settings are not implemented.

## Troubleshooting

- **Project initialization failed:** verify you selected the new top-level `CabinPower3Fresh` folder and not a previous project. Find the first red Gradle error and share it.
- **HTTP 401:** sign in with a fresh password, not an old access token.
- **SecurityException:** confirm you built this version, and inspect Logcat for the first exception/stack trace (especially package and permission).
- **No graphs:** a new app starts with an empty local database and needs successful new readings.

## GitHub notes

This source includes the inverter **station and DTU identifiers** in `app/src/main/java/com/cabinpower/solar3/EnerwiseClient.java`. They are not login credentials, but still reveal identifiers tied to a personal installation. Keep the repository **private** unless you replace them with configurable values.

Never commit an Enerwise password, Bearer access token, refresh token, or signing key. The `.gitignore` excludes typical local Android build artifacts and keystores. GitHub Actions provides a debug APK build workflow under `.github/workflows/build-apk.yml` (not yet tested end-to-end).
