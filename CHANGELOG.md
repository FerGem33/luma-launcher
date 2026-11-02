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
- Samsung-style swipeable widget pages hosted with `AppWidgetHost`, with a page
  indicator and a button to add pages.
- Widget picker with previews, grouped by app, with a search field that filters by app name or widget name.
  Handles the bind-permission and widget-configuration flow.
  - Previews use the same sources One UI does: generated previews, preview layouts, static
    images, then the app icon. Samsung preview layouts that need the widget app's own view classes
    are loaded with that app's code, only for preinstalled (system) apps.
  - Providers that are indistinguishable to the user (same name, preview and size) are shown once.
  - Samsung widgets that declare several sizes (2x1, 4x1, 2x2, 4x2) are listed once per size,
    each with a preview made for that size; picking one adds the widget at that size.
- Widget grid: each page is a 4-column grid of square cells.
  - New widgets start at their default size and take the first free spot;
    a toast is shown when the page is full.
  - Long-press a widget to select it; keep dragging to move it (snaps to cells).
  - A selected widget shows edge handles to resize it in whole cells, within the
    limits the widget declares (resize direction and minimum size).
  - A selected widget offers Remove and, for widgets that support it, Settings
    (re-opens the widget's settings screen, including Samsung's own "Widget settings" screens).
  - Widgets are clipped to rounded corners, like One UI Home.
  - Long-press empty space for a page menu (add widget, remove page).
- Home button resets to the home page and closes the drawer, picker and any selection.
- Pinned apps and widget layout persisted in `SharedPreferences`.

### Changed

- Widget layout is stored in grid cells (`pages_v2`) instead of full-width heights.
  Widgets added with the earlier list layout are released on first launch and must be re-added.

### Fixed

- Samsung widgets (Weather, Clock/Alarm, ...) now get the same private host options One UI Home
  passes (grid span, size code, style), so their settings screens offer every choice, such as
  Weather's daily/hourly forecast. Options are also pushed with a direct `updateAppWidgetOptions` call,
  because `updateAppWidgetSize` drops extra keys.
- Widgets that declare their settings screen only in Samsung's `semConfigureActivity`
  metadata (e.g. Alarm) now show a Settings button.
- Widgets no longer keep the default padding Android adds around older widgets, which left their
  background inset from the cell.
- Crash when a widget was shown (`ArrayMap is immutable`): widget size updates were
  given the immutable `Bundle.EMPTY`.
- Widgets showing "Can't show content" (notably Jetpack Glance widgets such as
  weather widgets): the full size options, including the list of sizes, are now
  supplied when a widget is bound and whenever it is resized.
- A long-press on a widget without moving could also launch the widget's app;
  the final touch-up of a long-press is now swallowed.

### Known limitations

- Black background only (no wallpaper) and no settings screen.
- Work-profile and Secure Folder apps are not listed.
- Compose BOM is pinned to `2025.10.00`; newer versions require `compileSdk` 37.
