# LuminaReader

An offline e-book reader for Android, built with Kotlin and Jetpack Compose. Reads
**EPUB**, **PDF** and **plain text**, with reading themes, annotations and text-to-speech
narration.

## Features

- 📚 **Library** with grid/list views, search, sorting, reading-status tabs and favourites
- 📖 **Reflowable reader** for EPUB and TXT, with paginated or continuous-scroll modes
- 📄 **PDF viewer** that renders at your screen's resolution, re-rendering when you pinch
  to zoom so text stays sharp
- ✍️ **Word-level selection** on any format, with speak, define, highlight, note, copy
  and share
- 🎨 Four reading themes (Light, Sepia, Night, OLED), four fonts, adjustable size, line
  spacing, margins and a warm night-light tint
- 🔖 Bookmarks, colour-coded highlights and attached notes, all stored locally
- 🎧 Text-to-speech narration with sentence navigation, speed control and a sleep timer
- 📊 Reading statistics: time, pages, streaks and achievements
- 🔒 Optional PIN lock

Everything works offline. The app makes no network requests.

## Tech stack

| Area | Choice |
| --- | --- |
| Language | Kotlin 2.2 |
| UI | Jetpack Compose, Material 3 |
| Persistence | Room (books, bookmarks, highlights, sessions) + SharedPreferences (settings) |
| PDF | `android.graphics.pdf.PdfRenderer`, plus platform text extraction on API 35+ |
| EPUB | In-house ZIP/OPF/XHTML parser (no third-party dependency) |
| Tests | JUnit 4, Robolectric, Roborazzi screenshot tests |

## Getting started

### Prerequisites

- Android Studio (Ladybug or newer)
- JDK 21
- Android SDK with API 36 installed
- A device or emulator running **Android 7.0 (API 24)** or newer

### Build and run

```bash
git clone https://github.com/K0986/LuminaReader.git
cd LuminaReader

./gradlew :app:assembleDebug      # build the debug APK
./gradlew :app:installDebug       # install onto a connected device
./gradlew :app:testDebugUnitTest  # run the unit tests
```

The Gradle wrapper is checked in, so no local Gradle installation is required. Debug
builds are signed with the SDK's auto-generated debug keystore — there is nothing to
configure.

### Release builds

Release signing is read from the environment and is only wired up when a keystore is
actually present:

```bash
export KEYSTORE_PATH=/path/to/upload-key.jks
export KEY_ALIAS=upload
export STORE_PASSWORD=...
export KEY_PASSWORD=...

./gradlew :app:assembleRelease
```

Without those variables the release variant still builds (unsigned), which keeps CI
able to verify that R8 shrinking succeeds.

## Project structure

```
app/src/main/java/com/example/
├── data/
│   ├── local/       # Room database and DAOs
│   ├── model/       # Entities and settings
│   ├── parser/      # EPUB, PDF and TXT parsing; PDF rendering and text extraction
│   ├── repository/  # Books, settings and reading sessions
│   └── sample/      # Bundled sample library
├── domain/
│   ├── dictionary/  # Word lookup and sharing
│   └── tts/         # Text-to-speech narration
└── ui/
    ├── navigation/
    ├── screens/     # library, reader, notes, stats, settings
    └── theme/
```

## A note on PDF text extraction

Selecting, searching and narrating text in a PDF needs a *text layer*: the characters the
document declares, as opposed to the pixels it draws.

- On **API 35 and newer** the app uses the platform's own extraction
  (`PdfRenderer.Page.getTextContents`), which applies each font's encoding and
  `/ToUnicode` map. This is accurate, including for ligatures and non-Latin scripts.
- On **older releases** it falls back to parsing the page's content streams directly. This
  handles the common cases but is approximate for unusual font encodings.
- Some pages genuinely have no text layer at all — scans, and image-only pages. The reader
  says so rather than pretending otherwise.

If you need font-correct extraction on every supported API level, the natural next step is
to add [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) or a PDFium binding,
at the cost of a larger APK.

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Make your change, and add a test for it
4. Check it builds: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
5. Open a pull request

CI runs the debug build, the unit tests, lint and a release build on every pull request.

## Licence

MIT — see [LICENSE](LICENSE).

---

Built with ❤️ by K0986
