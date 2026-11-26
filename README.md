# AutoBot Controller - Full Android Automation

Android Accessibility Service and Scheduler engine to automate app launches, button taps, gestures, and media uploads (e.g. YouTube & Instagram) for personal use.

## Features:
- Scheduled Task Execution with `AlarmManager`
- Auto App Launch (`com.google.android.youtube`, `com.instagram.android`)
- Tap via Coordinates (`dispatchGesture`) and Screen Text matching
- Automated Input Text injection for Titles/Captions
- Background execution with Battery Optimization bypass

## Automated Cloud Build:
Pushing to GitHub triggers `.github/workflows/build-apk.yml` to compile the debug APK and publish it under **Releases** and **Artifacts**.
