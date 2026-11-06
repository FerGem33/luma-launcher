# Changelog

All notable changes to this project are documented here.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and commits follow [Conventional Commits](https://www.conventionalcommits.org/).

## [Unreleased]

### Added

- Settings → Default launcher: shows whether Luma is the phone's home app and, if not, asks Android to make it so
  (the system's "Set as default home app" dialog); it re-checks when you come back.
- Day / Night themes: a switch on the Appearance screen turns the theme into a day theme and a night theme, each
  picked from the presets, with the times at which day and night begin (default 07:00 and 20:00). The theme flips
  on time while Luma is open, including the "Time's up" page's colours, and is re-checked as soon as you
  change a time or the switch. Times are set with a picker in Luma's own style (hour and minute wheels).
- Swiping up from the right-hand strip of the screen (the last third of its width) opens the app drawer with the
  search field focused and the keyboard open. A swipe up elsewhere opens it as before.
- Friction on distracting apps: mark apps in Settings → Distracting apps, and opening one (from home, the
  drawer or search) first shows a quiet full-screen pause with a 10-second countdown. "Not now" is always
  available; "Open for N min" appears when the countdown ends. Back or Home cancels. The list has a search field, which moves above the time settings while you type so results stay visible.
- Time per pass: passing the pause lets you open and close that app freely for a set number of minutes
  (1 to 60, default 5, set in Settings → Distracting apps) without the pause again.
- Optional time limit: with Luma's accessibility service turned on, a distracting app is covered by a "Time's up"
  page with a Close button (which sends you home) when its time runs out, and by the same pause as Luma's own
  (countdown, "Not now", "Open for N min") when it is opened without passing it, from a notification or
  another launcher.
  The service only reads which app is in front.
- Initial launcher scaffold (Kotlin, Jetpack Compose, Gradle 9.3.1, AGP 9.1.0; minSdk 29, targetSdk 36).
- Niagara-style home page: clock and date, with a text-only list of pinned apps.
  Long-press a pinned app and drag to reorder the list; long-press and let go without moving for
  unpin, app info and uninstall.
- Swipe-up app drawer: alphabetical list with letter headers, a draggable A–Z
  fast-scroll bar, and a bottom search field (the Go key launches the first match).
- Samsung-style swipeable widget pages hosted with `AppWidgetHost`. Pages can sit on either side of
  home: a `+` at each end of the page dots adds one, and the home page's dot is a ring.
- Widget picker with previews, grouped by app, with a search field that filters by app name or widget name.
  Tap a widget to add it in the first free spot, or long-press and drag it onto the page behind the
  picker: the cells it would take are highlighted (red when blocked) and it is added where you let go.
  Handles the bind-permission and widget-configuration flow.
  - Previews use the same sources One UI does: generated previews, preview layouts, static
    images, then the app icon. Samsung preview layouts that need the widget app's own view classes
    are loaded with that app's code, only for preinstalled or platform-signed (Samsung) apps.
  - Widgets are shown as cards, like One UI Home: the widget's name, then a preview per size it
    offers at true relative scale, with the size underneath.
  - Providers an app marks "hide from picker" (legacy and duplicate registrations) are not listed,
    and providers that are indistinguishable to the user (same name, preview and size) are shown once.
  - Samsung widgets that declare several sizes (2x1, 4x1, 2x2, 4x2) are listed once per size,
    each with a preview made for that size; picking one adds the widget at that size.
- Widget grid: each page is a 4-column grid of square cells.
  - New widgets start at their default size and take the first free spot;
    a toast is shown when the page is full.
  - Long-press a widget to select it; keep dragging to move it (snaps to cells). A faint grid and the
    target cells are shown while it moves.
  - A selected widget has a border you can grab anywhere along its edges or corners to resize it in
    whole cells, within the limits the widget declares (resize direction and minimum size). The border
    stretches with your finger like a rubber band, then springs back to the snapped size; the grid is
    shown while resizing. The border uses the theme's text colour so it shows on light and dark pages.
  - A selected widget offers Remove and, for widgets that support it, Settings
    (re-opens the widget's settings screen, including Samsung's own "Widget settings" screens).
  - Widgets are clipped to rounded corners, like One UI Home.
  - Long-press empty space for a page menu (add widget, remove page).
- Themes: predefined looks, each bundling colours, background, typeface, text alignment and clock/date
  format. Paper, Paper Warm (beige) and Paper Dark (monospace, left-aligned, 24-hour clock; Paper is the
  default), Graphite (true black, hairline sans, 12-hour clock) and Custom (your own wallpaper, left-aligned
  sans, 24-hour clock, soft text shadow). A theme applies to the home page, app drawer, widget picker,
  settings, menus, page dots and the status/navigation bar icons.
- Settings (long-press empty space on the home page) is a menu. Appearance leads to the theme choice:
  pick a theme from thumbnails drawn in each theme's own fonts and colours. The choice is persisted. For Custom, choose one wallpaper or several from
  the gallery (with several, one is shown per day); images are copied into the app, downscaled.
- Bundled typefaces: Outfit and IBM Plex Mono (see `LICENSES-fonts.md`).
- Popup menus (app actions, home page, widget page) share one flat, themed design: thin outline, no
  shadow, the theme's font, an icon on every row and a muted red for destructive actions.
- Selected widgets show round icon buttons (remove, settings) instead of text buttons.
- App drawer search: results are anchored just above the search field with the best match nearest it, and
  names that start with the query rank before names that merely contain it.
- The window shows the system wallpaper, for themes that use it.
- App icon: a four-pointed light with four rays on cream, as an adaptive icon (vector background and
  foreground, plus a single-colour layer for themed icons on Android 13+).
- App icons: a setting (off by default) that shows each app's icon beside its name in the home list and
  the drawer, on the side the theme aligns its text to.
- Home button resets to the home page and closes the drawer, picker and any selection.
- Pinned apps and widget layout persisted in `SharedPreferences`.

### Changed

- App icons all share one shape, a rounded square: an adaptive icon is redrawn inside the same mask, and an older
  icon is shrunk onto a backdrop of its own average colour.
- Settings now open from the first row of the app drawer ("Luma Settings", also found by searching
  "luma" or "settings") instead of a menu after long-pressing the home page, which no longer has a menu.
- The "Long-press to add a widget" hint on empty widget pages is gone; long-press empty space for the
  page menu.
- The app is renamed from Lumen to Luma: app label, application id (`dev.luma.launcher`), source package,
  code identifiers and preferences file. The new id installs as a separate app, so pinned apps, widgets,
  the chosen theme and wallpapers from a Lumen install are not carried over.
- Widget layout is stored in grid cells (`pages_v2`) instead of full-width heights.
  Widgets added with the earlier list layout are released on first launch and must be re-added.

### Fixed

- The pause screen puts the keyboard away when it opens straight after typing in the drawer's search.
- App icons could stay on the wrong app: after an uninstall, or while the search list changed, a row
  reused for another app kept the previous app's icon. Icons are now tied to their app, drawer rows are
  keyed by app, and the icon cache is cleared when the app list changes.
- Drawer search now starts from its best match, nearest the search field, instead of keeping the previous
  scroll position.
- Widget picker previews could show another widget's preview after searching (rows were reused for
  different widgets and kept the old preview view); rows are now keyed and previews rebuilt per widget.
- Widgets whose configuration is optional (Digital Wellbeing's Screen time, for one) were thrown away
  after their settings screen: Samsung's screen reports "cancelled" even when you tap Save. Such widgets
  are now kept whatever the screen reports.
- Removing a widget page that has widgets now asks first, since its widgets are removed with it.
- Samsung widgets (Weather, Clock/Alarm, ...) now get the same private host options One UI Home
  passes (grid span, size code, style), so their settings screens offer every choice, such as
  Weather's daily/hourly forecast. Options are also pushed with a direct `updateAppWidgetOptions` call,
  because `updateAppWidgetSize` drops extra keys.
- Widgets that declare their settings screen only in Samsung's `semConfigureActivity`
  metadata (e.g. Alarm) now show a Settings button.
- Samsung preview layouts from Samsung Health, SmartThings (Routines, Now, Screen remote) and others
  failed to inflate because the inflater tried to build layout params for the root view from its
  `@dimen` size; they are now inflated without a parent. Layouts that need the widget app's own view
  classes (Clock, Calendar, Weather, Device care, ...) were rejected by the "system apps only" check,
  but many Samsung apps are updated, non-system installs; platform-signed apps now qualify too.
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
