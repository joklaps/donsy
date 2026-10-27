# Donsy

**Clear your mind. Find your next step.**

Donsy is a privacy-first, offline-first Android app that helps people capture thoughts, turn them into manageable tasks and make progress without feeling overwhelmed. It's for everyone, and especially friendly to students and to people who get easily overwhelmed (ADD/ADHD): approachable, non-judgmental, never childish.

Its defining question: *"What is a useful next step for me right now?"*

## Principles

- **Capture first, organize later.** A thought takes one tap; sorting it out is optional.
- **One next step, not a wall of tasks.** Big tasks are broken into small steps; Home suggests one.
- **No guilt.** Nothing is "overdue", unprocessed thoughts never nag, and every suggestion can be declined.
- **Private by design.** Everything works offline, with no account, backend or analytics. The app doesn't even request internet access.
- **For everyone.** Screen-reader labels, dynamic text sizes, meaning never carried by color alone, and translatable from day one (English and Dutch so far).

## What it does today

| Area | Features |
| --- | --- |
| **Brain Dump** | Quick capture, typed or dictated with the keyboard's microphone; search, sort by date, edit, delete with Undo; turn a thought into a task. |
| **Tasks** | Routines / To do / Not for now / Done; due date with an optional time, estimate, effort, importance; ordered steps (drag or accessibility actions) and built-in step templates; archive done tasks. |
| **Routines** | Tasks that come back every day, on weekdays or on picked days, with a reminder at the time you choose and a Done action in the notification. |
| **Home** | One explainable next-step suggestion, with "I have about…", an optional energy check-in, Show another, Not now and "I'd rather choose myself". |
| **Focus** | Sessions of the lengths you choose, on a step, a task or nothing in particular; pause, resume, finish early or cancel without touching the task; a countdown notification; recovery after the app is closed, the phone restarts or the clock changes. |
| **Settings** | Language, focus lengths, precise end alerts; export and restore (optionally with a passphrase), Recently deleted, Archive and automatic archiving. |


## Building

Requirements: JDK 21 and the Android SDK (compile SDK 36). The app runs on Android 10 (API 29) and up.

```sh
export JAVA_HOME=/path/to/jdk-21
./gradlew check                 # tests, lint, hardcoded-string and release-permission checks
./gradlew :app:installDebug     # install on a connected device or emulator
./gradlew :app:bundleRelease    # Play bundle (signed when keystore.properties is present)
```

The project builds in IntelliJ IDEA 2026.2 and Android Studio. IntelliJ's Android plugin currently caps the Android Gradle plugin at 9.1 and the compile SDK at 36.

## Project structure

```
domain/   Pure Kotlin: models, repository interfaces, business rules (recommendation engine)
data/     Room database, DataStore preferences, repository implementations
app/      Jetpack Compose UI (Material 3), navigation, ViewModels, Hilt wiring
```

The rules live in `domain`, with no Android dependencies, so they're fast to test and the same everywhere. Kotlin, Jetpack Compose, Room, DataStore, Hilt; MVVM with repositories.

## License

Copyright © 2026 Jo Klaps.

Donsy is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version. It is distributed in the hope that it will be useful, but without any warranty. See [LICENSE](LICENSE) for the full text.

In short: anyone may use, study, share and change Donsy. Whoever passes it on, changed or not, for free or for a fee, must also pass on the full source code under this same license. Donsy can't be turned into a closed app.

Source files carry an `SPDX-License-Identifier: GPL-3.0-or-later` line. Everything in this repository is covered by the license, except the name and logo below.

### Name and logo

The license covers the code, not the name. "Donsy" and the Donsy logo and app icon are not licensed under the GPL. If you publish a changed version, give it a different name and icon, so nobody mistakes it for the official app, which is free.
