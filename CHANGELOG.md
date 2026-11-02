# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and commits follow [Conventional Commits](https://www.conventionalcommits.org/).

## [Unreleased]

### Added

- Initial launcher scaffold (Kotlin, Jetpack Compose, Gradle 9.3.1, AGP 9.1.0; minSdk 29, targetSdk 36).
- Niagara-style home page: clock and date, with a text-only list of pinned apps.
  Long-press a pinned app for unpin, app info and uninstall.
- Swipe-up app drawer: alphabetical list with letter headers, a draggable A–Z
  fast-scroll bar, and a bottom search field (the Go key launches the first match).
- Samsung-style swipeable widget pages hosted with `AppWidgetHost`:
  - widget picker with previews, grouped by app;
  - bind-permission and widget-configuration flow;
  - edit mode to resize or remove widgets and to remove pages;
  - page indicator with a button to add pages.
- Home button resets to the home page and closes the drawer, picker and edit mode.
- Pinned apps and widget layout persisted in `SharedPreferences`.

### Known limitations

- Black background only (no wallpaper) and no settings screen.
- Widgets are full width with adjustable height; there is no grid layout.
- Work-profile and Secure Folder apps are not listed.
- Compose BOM is pinned to `2025.10.00`; newer versions require `compileSdk` 37.
