# GoGuardian Android

> Safety-first ride booking demo for Android, built in Java with Firebase and Google Maps.

![Android](https://img.shields.io/badge/Android-Java-3DDC84?style=for-the-badge&logo=android)
![Firebase](https://img.shields.io/badge/Firebase-Realtime%20DB%20%2B%20Auth-FFCA28?style=for-the-badge&logo=firebase)
![Maps](https://img.shields.io/badge/Google%20Maps-Enabled-4285F4?style=for-the-badge&logo=googlemaps)

## Overview

GoGuardian is a college-project Android app that simulates a modern ride-booking platform with a strong safety layer. It includes rider flows, admin flows, live map-based trip screens, SOS actions, wallet-style screens, and Firebase-backed sign-in for a polished demo experience.

Note: This repository contains a student prototype developed for a Mobile Programming Lab course and used as an internal demonstration of women's-safety-focused features. It is not a production app.

## Highlights

- Rider mode and admin mode in one APK
- Firebase Authentication with email/password and Google sign-in
- Google Maps-powered booking and live tracking UI
- SOS, safety badges, claims, wallet, and ride history screens
- Material Design 3 styling with a dark, professional look
- Demo admin account support for the project showcase

## Safety Features (Women-focused)

GoGuardian emphasizes rider safety with a set of features designed to protect women while travelling:

- **SOS / Emergency Alert:** A prominent in-app SOS action that immediately alerts emergency contacts and flags the ride in the admin dashboard.
- **Live Location Sharing:** Real-time trip tracking so trusted contacts can follow a trip until it completes.
- **Verified Drivers & Safety Badges:** Visual safety badges on driver profiles and simulated vetting states in the demo to indicate trust level.
- **Pre-ride ETA & Cancellation Fees:** Clear ETA, driver details, and cancellation rules to reduce uncertainty before and during trips.
- **In-app Reporting & Admin Monitoring:** Easy reporting for safety issues and an admin dashboard for reviewing and acting on flagged rides.
- **Privacy-first Design:** No personal credentials or API keys are stored in the repo; sensitive configuration is local-only.

These elements target common safety concerns (unknown drivers, lack of trip visibility, slow emergency response) and are implemented in the demo as an extensible foundation for production hardening.

## Tech Stack

- Java
- Android SDK
- Material Components / Material Design 3
- Firebase Authentication
- Firebase Realtime Database
- Google Maps SDK for Android
- Places API
- OkHttp
- RecyclerView, ViewBinding, Data Binding, Bottom Sheets

## Project Structure

- `app/src/main/java/com/goguardian/ui/auth` - login, onboarding, splash, welcome
- `app/src/main/java/com/goguardian/ui/rider` - rider home, booking, tracking, profile, SOS, receipt, rating
- `app/src/main/java/com/goguardian/ui/admin` - admin dashboard, users, rides, claims, broadcast
- `app/src/main/java/com/goguardian/ui/common` - shared screens and utilities
- `app/src/main/java/com/goguardian/util` - routing, fare, marker, haptic helpers
- `app/src/main/res` - layouts, drawables, menus, animations, themes

## Setup

Quick checklist to run the project locally (no secrets in repo):

1. Install Android Studio and Android SDK (recommended SDK: API 34).
2. Open the project in Android Studio.
3. Create a `local.properties` in the repo root (this file is ignored by git).

   Example `local.properties` (DO NOT commit):

   ```properties
   sdk.dir=C:/Users/YOUR_USER/AppData/Local/Android/Sdk
   MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
   ADMIN_EMAIL=you@example.com
   ```

4. Add your Firebase config file locally (not tracked):

   - Place your `google-services.json` into the `app/` folder.
   - This file contains Firebase project identifiers and is intentionally ignored by the repo.

5. Enable Firebase Authentication on your Firebase project (Email/Password and Google sign-in).
6. In Android Studio: `File → Sync Project with Gradle Files` then build/run.

Notes:
- The Maps API key is injected at build time via `local.properties` (`MAPS_API_KEY`).
- The demo admin address is read from `local.properties` as `ADMIN_EMAIL` and embedded into `BuildConfig.ADMIN_EMAIL` at build time.

## Important Security Notes

- `local.properties` is ignored by git; never commit it.
- `app/google-services.json` is ignored by git and must be provided locally for Firebase features.
- Keep API keys and service account files out of the repository. If you need to share setup instructions, provide `local.properties.example` instead of real keys.

## Admin Login

The project supports a configurable demo admin account. Do NOT add any personal email to the repository.

How it works:

- Set `ADMIN_EMAIL` in `local.properties` (example above).
- At build time the value is embedded as `BuildConfig.ADMIN_EMAIL` and used by the app to enable admin flows.

If you want to distribute the project for others to try, provide instructions for them to create their own `local.properties` and `google-services.json` for their Firebase project.

## GitHub Push Ready Checklist

- No generated build folders committed
- No local SDK paths committed
- No Firebase or Maps secrets committed
- Clean `.gitignore`
- README included for setup and presentation

## Development notes

- If you need a local template file for others, create `local.properties.example` containing example keys and instructions, but do not include real keys.
- The project intentionally ignores build artifacts, SDK paths, and Firebase config to avoid leaking secrets.

## Contributing

- Fork the repo and open a pull request for any non-trivial changes.
- Keep secrets out of commits. If a secret is accidentally committed, contact the maintainer to coordinate a history rewrite.

## License

This project is provided as-is for demo/educational use. Add a license file if you plan to publish.

## Build

```bash
./gradlew assembleDebug
```

On Windows, use:

```powershell
.\gradlew assembleDebug
```

## Notes

This project is a demo prototype, so some journeys, driver data, and admin analytics are simulated for presentation and college submission purposes.