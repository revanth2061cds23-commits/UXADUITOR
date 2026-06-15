# UX Audit Tool — Product Requirements Document

**Android App + Figma Plugin**

| Field | Value |
|---|---|
| Version | 1.0 — Initial Release |
| Status | Draft |
| Platform | Android 8.0+ (API 26+) / Figma Plugin |
| Stack | Flutter + Kotlin (Platform Channel) + TypeScript |
| Backend | Supabase (Storage + Realtime + Postgres) |
| Sync Model | Post-session batch upload (not real-time during capture) |

---

## Table of Contents

1. [Overview](#1-overview)
2. [Scope — v1.0](#2-scope--v10)
3. [System Architecture](#3-system-architecture)
4. [Android App — Feature Specifications](#4-android-app--feature-specifications)
5. [Figma Plugin — Feature Specifications](#5-figma-plugin--feature-specifications)
6. [Permissions and Security](#6-permissions-and-security)
7. [Technical Stack Reference](#7-technical-stack-reference)
8. [Acceptance Criteria](#8-acceptance-criteria)
9. [Open Questions](#9-open-questions)
10. [Future Roadmap](#10-future-roadmap)

---

## 1. Overview

### 1.1 Problem Statement

UX auditors currently face a fragmented, manual workflow when capturing interaction evidence on real Android devices. The standard process — taking screenshots manually, transferring files via WhatsApp or Google Drive, downloading on desktop, and arranging frames by hand in Figma — introduces friction at every step, loses contextual data such as where the user tapped and in what sequence, and can take hours per audit session.

This tool eliminates that workflow entirely by capturing the interaction automatically during the session and pushing a structured, ordered flow into Figma once the session ends.

### 1.2 Product Vision

A two-part ecosystem: an Android app that runs silently during a live audit session, capturing screenshots and tap coordinates automatically on every interaction, then syncing the complete ordered flow to Figma in a single batch when the session ends. A Figma plugin receives that batch and renders it as a structured flow on the canvas — one screen after another in the exact order the auditor navigated.

### 1.3 Core Workflow (End to End)

1. Auditor opens the app and creates a new named session
2. App displays a **3-2-1 countdown** on screen, then signals **GO** — capture is now active
3. Auditor switches to the app being audited and navigates naturally — every tap silently captures a screenshot plus coordinates
4. A floating **End Session** button remains visible throughout — always accessible, never intrusive
5. When the audit is complete, the auditor taps **End Session**
6. App uploads all captured screenshots and metadata to Supabase in sequence order
7. The Figma plugin receives the complete session and renders every screen as a Figma frame, arranged left to right in the exact navigation order
8. Auditor sees the full user flow on the Figma canvas, ready to annotate

### 1.4 Target Users

| User Type | Context | Primary Need |
|---|---|---|
| UX Auditors | Agency or in-house, auditing live apps | Capture structured flows without interrupting the audit |
| UX Researchers | Usability testing sessions | Record interaction evidence with tap-level precision |
| Product Designers | Reviewing competitor or own app flows | Get ordered flows into Figma instantly for annotation |

---

## 2. Scope — v1.0

### 2.1 In Scope

- Android app: session creation, 3-2-1 countdown start, auto-capture on every tap, floating End Session button, post-session batch upload
- Android app: tap coordinate recording per screenshot, optional quick tag per screen, screen recording mode
- Figma plugin: session import via code, batch frame layout in navigation order, tap indicator layer toggle
- Backend: Supabase relay — Postgres metadata, Storage buckets, Realtime channel for upload progress
- Authentication: session-code pairing between app and plugin (no full auth in v1)

### 2.2 Out of Scope (v1)

- Real-time / live sync to Figma during capture (sync happens only after session ends)
- iOS companion app
- Team or multi-user collaboration
- In-Figma annotation panel or severity tagging
- AI-generated audit reports
- Web dashboard
- Offline-first architecture (upload requires connection after session)

---

## 3. System Architecture

### 3.1 Components

| Component | Technology | Responsibility |
|---|---|---|
| App UI layer | Flutter / Dart | Session management, countdown, FAB overlay, session review, upload progress |
| App capture layer | Kotlin via Platform Channel | AccessibilityService (tap events), MediaProjection (screenshots + recording), ForegroundService |
| Local storage | SQLite (on-device) | Stores captured screenshots and metadata locally during session — source of truth until upload |
| Backend | Supabase | Postgres metadata, Storage buckets (PNG / MP4), Realtime channel for upload progress |
| Figma plugin | TypeScript, Figma Plugin API | Session code input, batch frame renderer, tap layer, import progress |

### 3.2 Sync Model — Post-Session Batch

> **Critical design decision:** No data is uploaded to Supabase during the capture phase.

All screenshots and metadata are stored locally on the device in SQLite during the session. This keeps the capture loop fast (no network latency on every tap), works on weak connections, and gives the auditor a local review step before pushing to Figma.

Upload happens in a single batch when the auditor taps **End Session**. Screenshots are pushed in `sequence_index` order so Figma always receives them in the correct navigation sequence.

### 3.3 Data Flow

1. Auditor creates session in app — session record written to local SQLite and to Supabase (`session_code` generated)
2. Countdown completes — AccessibilityService and MediaProjection activated
3. For every tap: Kotlin layer fires event with coordinates, captures frame, writes to local SQLite with `sequence_index++`
4. **All data stays local during capture — no network calls**
5. Auditor taps End Session FAB — capture stops, ForegroundService ends
6. App begins batch upload: iterates SQLite records in sequence order, uploads PNG to Supabase Storage, inserts row to `screens` table
7. Upload progress shown to auditor (e.g. `Uploading 12 / 47 screens`)
8. On completion, Supabase Realtime fires a `session_complete` event on the session channel
9. Figma plugin (if open and paired) receives the event and triggers batch frame rendering
10. Plugin fetches all screens for the session in sequence order, renders frames left to right on the Figma canvas

### 3.4 Database Schema

#### `sessions` table

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid PRIMARY KEY` | Auto-generated |
| `session_code` | `varchar(6) UNIQUE` | Human-readable pairing code shown in app, entered in plugin |
| `name` | `text` | Auditor-assigned session name |
| `device_model` | `text` | e.g. Pixel 7 |
| `screen_width_px` | `integer` | Physical screen width in pixels |
| `screen_height_px` | `integer` | Physical screen height in pixels |
| `status` | `enum: active \| uploading \| complete \| failed` | Updated as session progresses |
| `created_at` | `timestamptz` | Auto |
| `completed_at` | `timestamptz NULLABLE` | Set when upload batch finishes |

#### `screens` table

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid PRIMARY KEY` | Auto-generated |
| `session_id` | `uuid REFERENCES sessions` | Foreign key |
| `sequence_index` | `integer` | 0-based tap order within the session. Determines left-to-right order in Figma |
| `image_url` | `text` | Supabase Storage public URL (PNG) |
| `tap_x_pct` | `float` | Tap X normalized to screen width (0.0 = left edge, 1.0 = right edge) |
| `tap_y_pct` | `float` | Tap Y normalized to screen height (0.0 = top, 1.0 = bottom) |
| `tag` | `text NULLABLE` | Optional auditor label for this screen |
| `captured_at` | `timestamptz` | Timestamp of the tap event |

#### `recordings` table

| Column | Type | Notes |
|---|---|---|
| `id` | `uuid PRIMARY KEY` | Auto-generated |
| `session_id` | `uuid REFERENCES sessions` | Foreign key |
| `video_url` | `text` | Supabase Storage URL (.mp4) |
| `duration_seconds` | `integer` | Total recording length |
| `mic_audio` | `boolean` | Whether microphone audio was captured |
| `created_at` | `timestamptz` | Auto |

---

## 4. Android App — Feature Specifications

### 4.1 Session Creation

**Description**
The auditor creates a named session before capture begins. The session is the container for all screenshots, tap data, and recordings from one audit run.

**Screens**
- Home screen: list of past sessions showing name, date, screen count, status badge (Active / Complete / Failed)
- New session screen: session name text input (required), app being tested label (optional free text), device info auto-populated (model, OS version, screen resolution)

**Behaviour**
- On Create: session record is written to both local SQLite and Supabase
- A 6-character alphanumeric session code is auto-generated and shown prominently — this is what the auditor types into the Figma plugin to pair
- After creation the auditor is taken directly to the countdown screen

---

### 4.2 Countdown Start Sequence

**Description**
When a session is started, the app displays a full-screen 3-2-1 countdown before capture activates. This gives the auditor time to switch to the app being audited and clearly signals when tracking begins.

**Visual Behaviour**
- Full-screen overlay with dark semi-transparent background
- Large centred numeral: `3` → `2` → `1` → `GO`
- Each numeral displayed for exactly 1 second with a fade or scale animation
- `GO` state shown for 0.8 seconds then the overlay dismisses automatically
- At the moment `GO` appears: AccessibilityService is activated, MediaProjection is live, capture begins

**Pre-Countdown Permission Gate**
Before the countdown starts, the app checks that both required permissions are granted. If either is missing the countdown does not begin.

- **MediaProjection:** system consent dialog shown on this screen if not already granted for this session
- **AccessibilityService:** if not enabled, a bottom sheet explains what it does and deep-links to Android Settings > Accessibility

---

### 4.3 Auto-Screenshot on Every Tap

**Description**
The core capture feature. Every time the auditor taps anywhere on the screen while navigating the target app, a screenshot is automatically saved along with the exact coordinates of that tap. No manual shutter button is involved during the session.

**Technical Implementation**
- Android `AccessibilityService` listens for `TYPE_TOUCH_INTERACTION_START` events system-wide
- On each event: records normalized coordinates — `tap_x_pct = event.x / screen_width_px`, `tap_y_pct = event.y / screen_height_px` — both stored as float 0.0 to 1.0
- Immediately triggers `MediaProjection` to capture the current screen frame as a PNG
- Screenshot and metadata written to **local SQLite only** — no network call at capture time
- `sequence_index` increments by 1 for each tap, starting at 0 — this is the definitive ordering for Figma layout
- A `ForegroundService` keeps capture running while the auditor navigates other apps. Android OS displays a persistent notification — this is a non-negotiable system requirement

**Capture Indicator**
- A small pulsing dot overlay (16dp circle, brand colour) is visible at all times during an active capture
- The dot briefly flashes white on each successful capture to confirm the screenshot was taken

**FLAG_SECURE Screens**
- Some apps (banking, payment) set `FLAG_SECURE` which blocks MediaProjection — these produce a black frame
- The app detects black frames and labels them `secured_screen` in SQLite rather than discarding them silently
- In session review these frames are shown with a lock icon and a note: *Screen capture blocked by app*

**Permissions Required**
- `BIND_ACCESSIBILITY_SERVICE` — declared in manifest, user enables once in Android Settings
- `MediaProjection` consent — system dialog, granted once per session before countdown
- `FOREGROUND_SERVICE` — declared in manifest, runtime on Android 9+
- `SYSTEM_ALERT_WINDOW` — for the capture indicator dot overlay

**Play Store Note**
AccessibilityService use requires Google Play review and justification. For v1, distribution via a closed testing track or direct APK bypasses this. The service reads **only touch event coordinates** — it does not read screen content, text fields, clipboard, or keyboard input.

---

### 4.4 Floating End Session Button (FAB)

**Description**
Once capture is active, a floating button remains visible on top of all other apps at all times. This is the auditor's only control during the session — tapping it ends the session and begins the upload.

**Visual Specification**
- Floating Action Button, 56dp diameter, positioned at bottom-right of screen with 24dp margin
- Icon: stop square symbol
- Label: `End Session` — visible on first appearance, collapses to icon-only after 3 seconds
- Brand accent colour with sufficient contrast against any background
- Implemented as a `TYPE_APPLICATION_OVERLAY` system window — appears above all other apps

**Behaviour**
- **Single tap:** shows a confirmation bottom sheet — *End session and sync to Figma?* with Cancel and End Session buttons — prevents accidental taps
- **On confirmation:** capture stops immediately, ForegroundService ends, FAB dismisses, app comes to foreground showing upload progress screen
- **Long press (3 seconds):** emergency end without confirmation — for cases where the confirmation sheet is not accessible

**Draggable Position**
- The FAB is draggable so the auditor can reposition it if it overlaps critical UI in the app being audited
- Last position is remembered for the session

---

### 4.5 Quick Tag Input

**Description**
After each screenshot is captured, the auditor can optionally type a brief label for that screen. This tag appears as the Figma frame name.

**Behaviour**
- A subtle slide-up panel (64dp tall) appears at the bottom of the screen after each capture, overlaid on the target app
- Contains a single text input: `Label this screen...` (placeholder)
- Auto-dismisses after 4 seconds if not touched — does not block the audit flow
- If the auditor taps the field: keyboard opens, they type and confirm with the keyboard action button
- Tag is written to SQLite alongside the screenshot record
- Tags can be edited in the session review view before upload
- Default Figma frame name when no tag is set: `Screen 1`, `Screen 2`, etc. (1-based)

---

### 4.6 Post-Session Upload

**Description**
After the auditor confirms End Session, the app uploads all locally captured data to Supabase in a single ordered batch. This is the only point where the network is used.

**Upload Sequence**
1. App reads all records from local SQLite for the session, ordered by `sequence_index ASC`
2. For each screen: upload PNG to Supabase Storage, then insert row to `screens` table with `image_url` and all metadata
3. Progress shown to auditor: `Uploading 12 / 47 screens` with a progress bar
4. On full completion: session `status` updated to `complete` in both SQLite and Supabase
5. Supabase Realtime publishes a `session_complete` event on the session channel

**Error Handling**
- Individual screen upload failures are retried up to 3 times with exponential backoff
- If an upload fails after 3 retries, it is marked `failed` in SQLite and skipped — upload continues with remaining screens
- Summary shown at end: `45 screens synced, 2 failed` with a **Retry Failed** button
- Local SQLite data is never deleted — it is the source of truth and can always be re-uploaded

**Network Requirements**
- Upload requires an active internet connection
- If offline when End Session is tapped: *No connection — your session is saved. Upload when connected.*
- Upload can be triggered manually from the session detail view at any time

---

### 4.7 Session Review

**Description**
The auditor can review, reorder, and delete screens before or after upload from the session detail screen.

**Behaviour**
- Scrollable thumbnail grid in sequence order
- Each thumbnail shows: sequence number badge, tag label (if set), tap indicator dot at relative position
- Tap a thumbnail to open full-screen preview with tag edit and tap coordinate displayed
- Long press enables multi-select mode for bulk delete
- Drag-to-reorder: updates `sequence_index` for all affected records
- Reordering before upload changes the order in Figma; reordering after upload does not retroactively update Figma (a re-import is required)
- Upload status per screen: `Pending` / `Uploaded` / `Failed`

---

### 4.8 Screen Recording Mode

**Description**
A separate capture mode where the auditor records the full session as a video file. Useful for capturing transitions, gestures, animations, and micro-interactions that screenshots cannot convey.

**Technical Implementation**
- Uses `MediaProjection` + `MediaRecorder`: MP4 output, H.264 video, AAC audio (microphone only)
- System audio from third-party apps is blocked by Android OS — this is a platform restriction, not a product limitation. Mic audio for think-aloud commentary is supported
- Recording runs via the same `ForegroundService` as screenshot mode — persistent notification shows elapsed recording time
- Video file written to app-private local storage during recording

**Controls**
- Recording mode is selected on the New Session screen before countdown
- In v1: session is either screenshot mode **or** recording mode, not both simultaneously
- The same FAB (End Session) is used to stop recording

**Figma Integration**
- Recording shows as a card in the Figma plugin session import view
- Card shows: duration, file size, upload timestamp
- **Open Recording** button opens the MP4 URL in the browser — Figma canvas does not embed video natively

**Constraints**
- Minimum stable API: Android 8.0 (API 26)
- App warns if estimated file size will exceed 500MB based on elapsed time

---

## 5. Figma Plugin — Feature Specifications

### 5.1 Session Pairing

**Description**
The Figma plugin connects to a completed session using the 6-character session code generated in the Android app. No login or OAuth is required in v1.

**Behaviour**
- Plugin opens to a single input field: `Enter session code`
- On valid code: fetches session metadata — displays session name, device model, screen count, status
- Status badge: `Uploading` (session still syncing from phone) or `Ready to import`
- If status is `Uploading`: plugin shows a progress indicator and polls until status changes to `complete`
- Plugin maintains a session history list showing the last 10 sessions accessed

---

### 5.2 Batch Frame Layout

**Description**
When the auditor triggers an import, the plugin fetches all screens for the session and renders them as Figma frames on the canvas — arranged left to right in the exact navigation order captured on the device.

**Import Trigger**
- Import button appears once session status is `complete`
- Auditor chooses the target Figma page or the plugin creates a new page named after the session
- Import runs as a single operation — all frames placed before the plugin returns control to the user

**Layout Specification**

| Property | Value | Notes |
|---|---|---|
| Frame size | Device screen dimensions | `screen_width_px` × `screen_height_px` from session metadata |
| Frame order | `sequence_index ASC` | Left to right — exact navigation order from the device |
| Frame spacing | 80px horizontal gap | Between consecutive screen frames |
| Frame name | Tag label if set, else `Screen N` (1-based) | Editable in Figma after import |
| Screenshot fill | Image fill on the frame | Fetched from Supabase Storage URL |
| Group container | Figma Group wrapping all frames | Named after the session name |
| Vertical position | All frames on the same Y baseline | Top edges aligned |

**Import Progress**
- Plugin panel shows a progress bar during import: `Importing screen 12 of 47`
- Individual frames appear on canvas as each one is placed — auditor sees the flow building left to right in real time

---

### 5.3 Tap Indicator Layer

**Description**
Each imported screen frame can optionally display a visual tap indicator showing exactly where the auditor tapped, derived from the normalized coordinates in the metadata.

**Rendering Logic**
- **Position:** `tap_x_pct × frame_width` = X; `tap_y_pct × frame_height` = Y — from the frame's top-left origin
- **Shape:** circle, 24px diameter, fill `#FF3B30` at 60% opacity
- **Sequence label:** 10px text placed 16px to the right of the circle showing the tap sequence number
- Each indicator placed on a child layer named `Tap Indicator` inside its frame

**Toggle Control**
- Plugin panel includes a toggle: **Show tap indicators**
- Toggle applies globally across all frames in the session group
- Off state: `Tap Indicator` layers hidden (visibility `false`) — not deleted, can be re-enabled at any time
- On state: layers made visible

---

### 5.4 Recording Card

**Description**
If the session includes a screen recording, the plugin displays it in the import panel alongside the screen list.

**Behaviour**
- Card shows: duration, file size, capture date
- **Open Recording** button opens the MP4 URL in the browser
- **Place on Canvas** button (optional) creates an annotation frame on the Figma canvas with session name, duration, and a link to the video — useful as a reference node in the audit flow

---

## 6. Permissions and Security

### 6.1 Permissions Table

| Permission | When Requested | Purpose |
|---|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | First session setup | Detect tap events and coordinates in other apps |
| `MediaProjection` consent | Before each session countdown | Capture screen frames and video |
| `FOREGROUND_SERVICE` | App install / first launch | Keep capture running while auditor uses other apps |
| `INTERNET` | App install | Post-session upload to Supabase |
| `RECORD_AUDIO` | Recording mode only, first use | Capture microphone audio for think-aloud commentary |
| `SYSTEM_ALERT_WINDOW` | First session setup | Show floating End Session FAB and capture indicator over other apps |

### 6.2 Privacy Notes

- All captured data is stored first on-device (SQLite + local file system), then in the user's Supabase project — no third-party data processing
- The `AccessibilityService` reads **only touch event coordinates (x, y)** — it does not read screen content, text fields, clipboard, or keyboard input
- Session codes expire and become read-only after 7 days
- No user account or personal data is required in v1
- Local SQLite data persists until the user explicitly deletes a session from the app

---

## 7. Technical Stack Reference

### 7.1 Stack Overview

| Layer | Technology | Key Libraries / APIs |
|---|---|---|
| App UI | Flutter (Dart) | `riverpod` (state), `go_router` (navigation), `supabase_flutter`, `sqflite` (local SQLite) |
| Capture layer | Kotlin (Platform Channel) | `MediaProjectionManager`, `AccessibilityService`, `MediaRecorder`, `ForegroundService`, `WindowManager` (FAB overlay) |
| Backend | Supabase | Postgres, Storage buckets, Realtime WebSocket, Row Level Security (RLS) |
| Figma plugin | TypeScript | Figma Plugin API, `@supabase/supabase-js`, `figma.createFrame`, `figma.createImage`, `figma.createEllipse` |

### 7.2 Flutter–Kotlin Platform Channel Contract

The Flutter UI layer communicates with the Kotlin capture layer via named channels. The following interface must be implemented by the Kotlin side.

| Method / Event | Channel Type | Direction | Payload |
|---|---|---|---|
| `requestPermissions()` | `MethodChannel` | Flutter → Kotlin | none |
| `startCapture(sessionId: String)` | `MethodChannel` | Flutter → Kotlin | `sessionId` |
| `stopCapture()` | `MethodChannel` | Flutter → Kotlin | none |
| `startRecording(sessionId: String)` | `MethodChannel` | Flutter → Kotlin | `sessionId` |
| `stopRecording()` | `MethodChannel` | Flutter → Kotlin | returns `localFilePath: String` |
| `showFAB()` / `hideFAB()` | `MethodChannel` | Flutter → Kotlin | none |
| `onScreenCaptured` | `EventChannel` | Kotlin → Flutter | `{ imagePath, tapXPct, tapYPct, timestamp, sequenceIndex }` |
| `onFABEndTapped` | `EventChannel` | Kotlin → Flutter | none — triggers end session flow in Flutter |

---

## 8. Acceptance Criteria

| Feature | Acceptance Criterion | Priority |
|---|---|---|
| Countdown start | Full-screen 3-2-1-GO sequence plays correctly. Capture does not begin before GO. Both permissions confirmed before countdown starts. | P0 — Must have |
| Auto-capture on tap | Screenshot captured within 500ms of every tap in the target app. Coordinates stored as float 0.0–1.0. Sequence index increments correctly. | P0 — Must have |
| FAB visibility | End Session FAB visible above all other apps throughout session. Draggable. Confirmation sheet shown on single tap. | P0 — Must have |
| Post-session batch upload | All screens uploaded in `sequence_index` order after End Session confirmed. Progress shown. Individual failures retried up to 3 times. | P0 — Must have |
| Figma frame layout | One frame per screenshot, sized to device dimensions, arranged left to right in `sequence_index` order. Frame names match tags or `Screen N` fallback. | P0 — Must have |
| Tap indicator placement | Tap indicator placed within 2% of true normalized coordinate on the Figma frame. Toggle shows and hides the layer without deleting it. | P0 — Must have |
| Session code pairing | Entering the 6-character code in the Figma plugin successfully connects to the correct session within 2 seconds. | P0 — Must have |
| Screen recording | Recording starts/stops on demand. MP4 uploaded and accessible from Figma plugin. Mic audio captured when granted. | P1 — Should have |
| Upload resilience | If network drops mid-upload, incomplete uploads resume correctly on reconnect. No duplicate frames in Figma on retry. | P1 — Should have |
| FLAG_SECURE detection | Black frames detected and labelled `secured_screen` rather than silently discarded. Shown with lock icon in session review. | P1 — Should have |
| Offline end session | If no network when End Session is tapped, session is saved locally with a clear message and manual upload trigger available. | P1 — Should have |

---

## 9. Open Questions

| Question | Options | Recommendation |
|---|---|---|
| Play Store distribution for v1 — how to handle AccessibilityService review? | A) Closed test track APK  B) Internal test track  C) Full Play review | A or B — ship fast, avoid review delay. Document justification for later submission. |
| Quick tag panel — show after every tap or only when manually triggered? | A) Show after every tap with 4s dismiss  B) Manual trigger only  C) Optional setting | A — auto-appear with dismiss timer captures context without blocking. Make it a setting later. |
| Supabase project ownership — shared infra or user-provided keys? | A) Single shared project  B) User provides own Supabase keys | A for v1 — simpler to build and support. Revisit for enterprise in v2. |
| Can screenshot mode and recording mode run simultaneously? | A) Mutually exclusive  B) Both run together | A for v1 — running both adds Kotlin complexity. Offer combined mode in v1.1. |
| FAB default position — bottom-right or bottom-left? | A) Bottom-right  B) Bottom-left  C) User sets on first session | A — bottom-right is the Android FAB convention. Draggable so user can move it. |

---

## 10. Future Roadmap

| Version | Planned Additions |
|---|---|
| v1.1 | Combined screenshot + recording mode. Swipe gesture capture (start point, end point, direction vector rendered as an arrow layer in Figma). Preset tag categories (Navigation, Empty State, Error, Loading, Success). |
| v1.2 | In-app session review with ability to trim, reorder, and annotate before upload. Quick export as PDF flow document. |
| v2.0 | iOS companion app — manual screenshot import and session sync. No auto-capture due to iOS sandbox restrictions. Same Figma plugin backend. |
| v2.0 | Team sessions — multiple devices contributing to the same session. Useful for comparative audits. |
| v2.1 | AI-assisted findings — generate draft heuristic observations from the captured flow using the Anthropic API. |
| v3.0 | Full in-Figma audit panel: severity labels, heuristic violation tags, WCAG checklist, exportable audit report as PDF. |

---

*UX Audit Tool — PRD v1.0 | Android + Figma Plugin | For LLM-assisted development*
