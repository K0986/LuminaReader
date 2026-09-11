# LuminaReader

A modern, offline-first book reader for Android: EPUB, PDF and plain text, with Kindle-style
reading themes, annotations, narration and reading statistics.

## Features

- 📚 **Library** — grid/list shelves, search, sort, favourites, reading status and a resume card
- 📖 **Reading** — paginated or continuous scroll, themes (Light, Sepia, Night, OLED), font family
  and size, line spacing, margins, warm night light, screen brightness
- 🖼️ **PDF** — original page rendering with pinch-to-zoom, or reflowed text mode
- ✍️ **Word selection** — long-press any word in a book *or directly on a PDF page*, extend the
  selection, then speak, define, highlight, note, copy or share it
- 🔎 **Whole-book search** — including inside large PDFs
- 🎧 **Narration** — sentence-by-sentence text-to-speech with speed control and a sleep timer
- 🔗 **Open from anywhere** — "Open with" and the system share sheet, one file or many
- 📊 **Insights** — reading time, streaks and achievements

## How PDFs work

A PDF describes marks on a page, not sentences, so Lumina reads a book's *text layer* once, up
front, with visible progress:

```
Library ──► open PDF ──► "Preparing …"  ──► reader
                          │  extract page text (resumable, one page at a time)
                          └─ persisted to the pdf_page_text table
```

After that pass, page text is served from the database, which is what makes reflowed text mode,
narration and whole-book search instant instead of re-parsing on every swipe.

Text extraction uses the platform PDF text layer where available
(`PdfRenderer.Page#getTextContents`, `#selectContent` and `#searchText` on Android 15+, and
`PdfRendererPreV` on Android 12+ with SDK extension 13). Those APIs also report glyph rectangles,
which is how words can be selected directly on the rendered page. On older devices the app falls
back to its own content-stream parser, which can read words but not their positions, so selection
happens in text mode instead.

## Tech stack

- **Language**: Kotlin, coroutines and Flow
- **UI**: Jetpack Compose, Material 3, Navigation Compose
- **Storage**: Room (with committed schemas and real migrations), SharedPreferences for settings
- **PDF**: `android.graphics.pdf` for rendering, text and selection
- **Testing**: JUnit, Robolectric, Roborazzi screenshot tests

## Getting started

### Prerequisites

- Android Studio (latest stable) or the Android SDK command line tools
- JDK 21
- Android SDK platform 36

### Build and run

```bash
git clone https://github.com/K0986/LuminaReader.git
cd LuminaReader

# Debug builds are signed with a local keystore that is not committed:
keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 365 \
  -dname "CN=Android Debug,O=Android,C=US"

./gradlew testDebugUnitTest   # unit + Robolectric tests
./gradlew assembleDebug       # APK
./gradlew installDebug        # install on a connected device
```

Screenshot tests are recorded with `./gradlew recordRoborazziDebug`; the images live in
`app/src/test/screenshots`.

## Project structure

```
app/src/main/java/com/example/
├── data/
│   ├── local/       # Room database, DAOs
│   ├── model/       # Entities and settings
│   ├── parser/      # EPUB, TXT and PDF parsing
│   │   └── pdf/     # PDF text layer: engines, geometry, models
│   └── repository/  # Books, settings, sessions, PDF text index
├── domain/          # Text-to-speech, dictionary lookup
├── ui/
│   ├── screens/     # library, reader, notes, stats, settings
│   └── theme/
└── util/            # Incoming share/open intents
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Run `./gradlew testDebugUnitTest` before pushing
4. Open a pull request

## License

MIT.
