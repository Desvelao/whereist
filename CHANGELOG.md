# Changelog

All notable changes to this project are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and the project uses
[Semantic Versioning](https://semver.org/). Add every user-visible change under `Unreleased`; the release
workflow publishes the matching section as the GitHub Release notes.

## [Unreleased]

## [0.1.0] - 2026-11-16

### Changed
- Release notes now include the signing certificate SHA-256 digest, and the README explains how to verify a download.

### Fixed
- Importing a backup now cancels the old reminder alarms, no longer leaves photos half-copied, and merges places with the same name under one parent.
- Reminders are no longer lost when notifications are off (they stay, and the reminder dialog warns about it), and they are rescheduled after an app update.
- The widget's last-saved line now updates after every save, put-back, rename, delete or import.
- An item inside a sub-place of its usual place is no longer marked "Out of place".
- Search now also finds items by their note and usual spot.
- Saving a location for an item that was just deleted no longer crashes.
- Importing a file that is not a backup now says so; "Here" answers faster indoors.
- The place chip on an item's detail now has a full-size touch target.

### Changed
- The timer is now just a "Reminder" and is no longer described as a parking timer, so it suits any item. Its
  notification channel is now called Reminders.
- The place is now shown next to the location text in an item's history, in the widget's last-saved line and in the
  reminder notification (it was only shown when there was no text).
- New look, answer first: each item shows where it is as the biggest line, with its name above and GPS accuracy
  and age below; items are grouped into Pinned and Everything else; Here is a filled pill; the save sheet, item
  detail (photo header, status chips, coordinates, Navigate, history timeline), the saved bar, the quick-save
  panel and the widget were redrawn. Tap an item's icon to open its details. Manrope and Inter are bundled.

### Added
- Appearance setting: follow the system, or always light or dark.
- German, French and Italian translations, and a Language select in Settings (system language by default, English
  when the app does not have it).
- The new-item form can set the item's current location (text and/or place) and its usual place, and has a
  "Save and add another" button to add several items in a row. It also has Here (GPS) and Add photo buttons.
- Usual place: give an item a usual place and/or spot when you edit it. When it is saved somewhere else it is marked
  “Out of place”, Home can filter to those, and Put back on its detail saves the usual place in one tap. Backups now
  include it (older backups still import).
- Compass: an item's detail shows an arrow and the distance back to its saved GPS spot (foreground location only; the
  arrow needs a compass sensor, otherwise only the distance shows).
- Share: send an item's location as text (with a maps link for GPS saves) through the system share sheet.
- Places: reusable, nested places such as Home › Garage › Shelf B. Pick or create one when you save where something
  is (a place alone is enough, or combine it with text like “second shelf”), and see it on the item. Browse
  Places from the home screen: opening Garage lists everything stored in it and in the places inside it. Rename,
  move or delete places; deleting one keeps the things' text and moves its inner places up a level. Search also
  matches place names. Existing locations are untouched and can be filed under a place later.
- Backups now include places (older backups still import).
- Items: add, rename, pin, archive, restore and delete things, each with an optional emoji icon.
- Save where something is with a free-text location (and an optional note); past places are offered as one-tap suggestions.
- Home list shows every item with its current location and how long ago it was saved; search ignores case and accents.
- Item detail with the full history of locations and an Undo for the latest save.
- Settings with archived items and an About section.
- GPS: a per-item "Capture GPS" switch adds a Here button that saves the current position and its accuracy. If the
  accuracy is worse than 30 m (underground car parks), the app asks for a photo or a note instead of trusting the pin.
- Photos from the system camera (no camera permission) and notes can be added to any save; the item detail shows
  them, with Navigate (opens your maps app) and Copy coordinates for GPS locations.
- Backup: export everything (items, history, photos) as one zip file and import it again from Settings.
- Location permission (foreground only) is asked the first time Here is used.
- Quick save: a home-screen widget and an app shortcut save a GPS location for your chosen item with one tap. They
  open a short foreground screen that takes the fix, so the app never needs background location. Choose the item in
  Settings; by default it is the first item with Capture GPS.
- Reminders: after a save, set a reminder (30 min to 3 h, or your own minutes) and get a notification when it
  runs out, with a Navigate action for GPS locations. Reminders survive a reboot and can be cancelled from the item.
- Project scaffold: empty Compose app with Room, English and Spanish strings and a map-pin launcher icon.
- Fully offline: no internet permission, analytics or ads.

[Unreleased]: https://github.com/Desvelao/whereist/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/Desvelao/whereist/releases/tag/v0.1.0
