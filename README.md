# GoGuardian Android

> Safety-first ride booking demo for Android, built in Java with Firebase and Google Maps.

![Android](https://img.shields.io/badge/Android-Java-3DDC84?style=for-the-badge&logo=android)
![Firebase](https://img.shields.io/badge/Firebase-Realtime%20DB%20%2B%20Auth-FFCA28?style=for-the-badge&logo=firebase)
![Maps](https://img.shields.io/badge/Google%20Maps-Enabled-4285F4?style=for-the-badge&logo=googlemaps)

## Overview

GoGuardian is a college-project Android app that simulates a modern ride-booking platform with a strong safety layer. It includes rider flows, admin flows, live map-based trip screens, SOS actions, wallet-style screens, and Firebase-backed sign-in for a polished demo experience.

## Highlights

- Rider mode and admin mode in one APK
- Firebase Authentication with email/password and Google sign-in
- Google Maps-powered booking and live tracking UI
- SOS, safety badges, claims, wallet, and ride history screens
- Material Design 3 styling with a dark, professional look
- Demo admin account support for the project showcase

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

1. Open the project in Android Studio.
2. Create a `local.properties` file in the repo root if it does not exist.
3. Add your Android SDK path and Maps key:

   ```properties
   sdk.dir=C:\Users\YOUR_USER\AppData\Local\Android\Sdk
   MAPS_API_KEY=your_google_maps_api_key
   ```

4. Add your Firebase config file at:

   ```
   app/google-services.json
   ```

5. Make sure Firebase Authentication is enabled for:
   - Email / Password
   - Google

6. Sync Gradle and run the app.

## Important Security Notes

- `local.properties` is ignored and must never be committed.
- `app/google-services.json` is intentionally ignored because it contains Firebase app configuration and keys.
- Use your own Firebase project and Maps API key before building for real use.

## Admin Login

The project is designed for a single demo admin account:

- `ayushsingh2262@gmail.com`

## GitHub Push Ready Checklist

- No generated build folders committed
- No local SDK paths committed
- No Firebase or Maps secrets committed
- Clean `.gitignore`
- README included for setup and presentation

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