# ClipVault Privacy Policy

**Last updated:** October 2, 2026

## Overview

ClipVault is a clipboard manager for Android that stores all data locally on your device. The app does not collect, transmit, or sell personal data. There are no analytics SDKs, crash reporters, or third-party trackers.

## Data stored locally

ClipVault saves a history of text you copy. Each entry contains:

- Copied text content
- Timestamp
- Content type classification (text, URL, email, phone)
- Pin status
- Source label (how the entry was captured)
- Cached URL title (if you tapped a URL entry)

Data is stored in a private SQLite database (`clipboard.db`) within the app's sandboxed storage. Other apps cannot access it.

Clips that their source app marks as sensitive (Android 13+ password managers set `EXTRA_IS_SENSITIVE`) are never saved — ClipVault reads the flag only to skip them.

Android backup is disabled for ClipVault (`allowBackup=false`): your history and settings are never uploaded to Google's cloud backup and are never included in device-to-device transfer tools.

## Network access

The app makes no network requests except in one case: when you tap a URL entry, the app may fetch the page title via HTTP/HTTPS to display it with that entry. This request goes directly to the URL's server. No proxy, no logging, no ClipVault servers involved.

## Permissions

| Permission | Purpose |
|------------|---------|
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Run the clipboard monitoring service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Run the optional floating bubble |
| `POST_NOTIFICATIONS` | Show monitoring status on Android 13+ |
| `RECEIVE_BOOT_COMPLETED` | Restart monitoring after device reboot |
| `WAKE_LOCK` | Keep monitoring active during device sleep |
| `SYSTEM_ALERT_WINDOW` | Display the optional floating bubble (off by default) |
| `VIBRATE` | Haptic feedback for interactions |
| `BIND_ACCESSIBILITY_SERVICE` | Optional: detect copy actions in other apps (off by default) |

The app never reads your clipboard for advertising, profiling, or any purpose other than saving entries when monitoring is enabled.

## User controls

- Pause or stop monitoring via the in-app toggle
- Delete individual entries, bulk delete, or clear all history
- Disable the floating bubble in Settings
- Disable the accessibility service in Settings or system Accessibility settings
- Uninstall the app to remove all stored data

## Children

The app is not directed at children under 13.

## Policy changes

This document will be updated if data practices change. Material changes will be noted in release notes.

## Open source

ClipVault is open source. Inspect the code to verify these claims.

## Contact

Open an issue on the project's GitHub repository.
