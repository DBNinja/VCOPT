# Repository Guidelines

## Project Structure & Module Organization

This is an Android project written in Kotlin. The repository structure follows the standard Android Gradle plugin conventions:

- **Source Code**: `app/src/main/java/org/openprinttag/` contains the core application logic, including Activities, Models, and Utilities.
- **Resources**: `app/src/main/res/` holds UI layouts, strings, and other assets.
- **Tests**: `app/src/test/` contains unit tests (e.g., `SerializerUnitTest.kt`).
- **Build Configuration**: Root `build.gradle` and `app/build.gradle` define dependencies and build settings.

## Build, Test, and Development Commands

Use the Gradle Wrapper for consistent execution across environments.

- **Build Project**:
  ```bash
  ./gradlew assembleDebug
  ```
  Compiles the code and generates a debug APK in `app/build/outputs/apk/debug/`.

- **Run Tests**:
  ```bash
  ./gradlew test
  ```
  Executes unit tests located in `app/src/test/`.

- **Clean Project**:
  ```bash
  ./gradlew clean
  ```
  Removes the `build/` directory to ensure a fresh build.

## Coding Style & Naming Conventions

- **Language**: Kotlin is the primary language. Ensure compatibility with Kotlin 2.2.0 and Java 21.
- **Formatting**: Follow standard Kotlin coding conventions.
- **Naming**:
  - **Classes**: PascalCase (e.g., `OpenPrintTagModel`).
  - **Functions/Variables**: camelCase (e.g., `generateTag`, `nfcHelper`).
  - **Layout Files**: snake_case (e.g., `activity_main.xml`).
- **View Binding**: The project uses View Binding. Avoid `findViewById` where possible.

## Testing Guidelines

- **Framework**: JUnit is used for unit testing.
- **Location**: Place unit tests in `app/src/test/java/org/openprinttag/`.
- **Requirement**: Ensure core logic in `model/` and `util/` (like serializers and byte utilities) is covered by tests.

## Commit & Pull Request Guidelines

- **Commit Messages**: Use clear, imperative present-tense messages (e.g., "Fix serialization bug", "Add NFC helper").
- **Pull Requests**: Provide a brief description of the changes. If the PR involves UI changes, include a screenshot or video description if possible.

