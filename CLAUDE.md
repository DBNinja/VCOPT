# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

VCOPT (OpenPrintTag Writer) is an Android NFC application for reading, writing, and generating standardized data tags for 3D printing materials (filament spools). It implements the OpenPrintTag specification, storing material metadata (brand, type, temperatures, colors, certifications) on NFC tags using CBOR encoding and NDEF formatting.

## Build Commands

```bash
./gradlew assembleDebug    # Build debug APK (outputs to app/build/outputs/apk/debug/)
./gradlew assembleRelease  # Build release APK
./gradlew test             # Run unit tests (in app/src/test/)
./gradlew installDebug     # Install to connected device
./gradlew clean            # Clean build directory
```

Note: Requires `ANDROID_HOME` environment variable set (e.g., `/Users/<user>/Library/Android/sdk`).

## Architecture

**Package:** `org.openprinttag.app` (namespace), `org.openprinttag` (code)

### Core Components

- **MainActivity** - Entry point handling NFC tag detection, reading, writing, and file import/export. Uses coroutines for async NFC operations with read/write mode toggle. Decodes and displays human-readable tag data.
- **GeneratorActivity** - Material data editor with complex UI for configuring material properties. Implements smart tag selection logic (implies/hints system) and returns generated binary data to MainActivity.
- **NfcHelper** - Abstraction layer for NFC operations supporting both NfcA and NfcV tag technologies. Handles page-based reading (pages 4-129).
- **OpenPrintTagModel** (`model/`) - Data model with nested regions (Meta, Main, Aux, URL). Uses Kotlinx.serialization with custom serializers and `@SerialName` for CBOR integer key mapping. `MainRegion` and `AuxRegion` are top-level classes, not nested.
- **Serializer** (`model/`) - Bidirectional CBOR serialization with NDEF record formatting. MIME type: `application/vnd.openprinttag`. Key methods: `serialize()`, `deserialize()`, `generateDualRecordBin()`.

### Data Configuration

YAML files in `app/src/main/assets/data/` define enum maps loaded at runtime:
- `material_class_enum.yaml` - Material classes (FFF, SLA, SLS)
- `material_type_enum.yaml` - Material types (PLA, PETG, ABS, TPU, etc.)
- `tags_enum.yaml` - Material property tags with implies/hints relationships
- `material_certifications_enum.yaml` - Certifications (FDA, REACH, etc.)
- `tag_categories_enum.yaml` - Tag categories with display names and emojis

### Data Flow

**Write flow:**
1. MainActivity -> GeneratorActivity (with optional cached tag data for pre-fill)
2. GeneratorActivity loads YAML configs -> builds dynamic UI
3. User fills form -> clicks Generate
4. Serializer encodes to CBOR -> returns binary to MainActivity
5. User taps NFC tag -> NfcHelper writes data

**Read flow:**
1. User taps NFC tag -> NfcHelper reads raw bytes
2. Serializer.deserialize() parses CBOR -> OpenPrintTagModel
3. MainActivity displays human-readable format (brand, material, temps, etc.)

## Coding Conventions

- **Language**: Kotlin 2.2.0 with Java 17 toolchain
- **Classes**: PascalCase (e.g., `OpenPrintTagModel`)
- **Functions/Variables**: camelCase (e.g., `generateTag`, `nfcHelper`)
- **Layout Files**: snake_case (e.g., `activity_main.xml`)
- **View Binding**: Used throughout - avoid `findViewById`
- **Coroutines**: Dispatchers.IO for NFC/file operations, Dispatchers.Main for UI
- **Theming**: Use Material3 theme attributes (`?attr/colorOnSurface`) instead of hardcoded colors

## Testing

- Unit tests in `app/src/test/java/org/openprinttag/`
- `testOptions.returnDefaultValues = true` in build.gradle for Android mocks
- Focus on covering `model/` logic (Serializer, OpenPrintTagModel)
- Run with `./gradlew test`

## Tech Stack

- Kotlin 2.2.0 / JVM toolchain 17
- Min SDK 25 / Target SDK 35
- Kotlinx.serialization + Jackson CBOR
- SnakeYAML for config parsing
- Material Design 3 with explicit Light/Dark themes (`values/styles.xml`, `values-night/styles.xml`)
