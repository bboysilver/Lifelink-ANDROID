# Third-party notices

LifeLink does not bundle advertising, analytics, cloud, payment, or location SDKs.
The Android app and its build/test tooling use the following open-source projects.

- AndroidX libraries and Android Gradle Plugin — Apache License 2.0
- Kotlin and kotlinx.coroutines — Apache License 2.0
- Gradle — Apache License 2.0
- JUnit 4 — Eclipse Public License 1.0
- Robolectric — MIT License
- Pillow (store-asset generator only; not shipped in the app) — HPND License

The generated store artwork under `store-assets/play` was created specifically for
this project from the project-owned source images under `store-assets/source`.
No font file is redistributed. `tools/generate_store_assets.py` discovers a locally
installed Korean system font, or uses paths explicitly supplied through
`LIFELINK_FONT_REGULAR` and `LIFELINK_FONT_BOLD`.

This notice is informational. The license text distributed by each upstream project
remains authoritative.
