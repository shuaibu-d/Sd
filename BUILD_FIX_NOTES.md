# SD Studio Kotlin build fixes

This update addresses the Kotlin compiler errors reported by GitHub Actions:

1. `ExportPolishOverlay.kt`: replaced the invalid `Typeface.MEDIUM` style constant with `Typeface.NORMAL` while retaining the `sans-serif-medium` typeface family.
2. `PlaceholderScreens.kt`: removed the obsolete placeholder `BrandScreen()` implementation. The real Brand Kit screen in `BrandScreen.kt` remains the single implementation used by navigation.
3. `BrandScreen.kt`: moved the selected palette value into the composable function scope so it is available both to the palette list and the edit-colour dialog. This also resolves the cascading type inference, `hex`, and experimental API diagnostics at the dialog update line.

The changes are source-level fixes for the errors in the supplied Actions log. A complete Gradle/Android SDK build was not run in this environment, so the next GitHub Actions run is required to verify compilation and reveal any subsequent errors.
