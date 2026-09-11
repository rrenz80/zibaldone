# Zibaldone — Project report

**Status as of 2026-09-11:** v1.10 (build 11), built and delivered as the
`Zibaldone-v1.10.apk` asset of the v1.10 GitHub release.
Build artifact: `app/build/outputs/apk/debug/app-debug.apk`.

> **Name.** Up to v1.8 the app was called *Moodboard App*
> (`com.moodboard.app`). Since v1.9 it is **Zibaldone**
> (`it.zibaldone.app`): a *zibaldone* is the notebook where notes,
> clippings and stray thoughts pile up with no order — a moodboard made
> of paper. References to the old name in the historical sections below
> are left untouched: they describe what happened back then.

> **Language of this document.** The project log was written in Italian
> up to v1.10 and translated to English when the repository was prepared
> for a public, international audience. The git history before v1.10 is
> still in Italian.

---

## 1. Overview

**Zibaldone** is an Android app for building moodboards on an
**infinite canvas** (a whiteboard) with three explicit tools —
**Select · Pen · Eraser** — plus text notes and photos:

- freehand drawing (vector strokes, adjustable width 2–24 dp)
- **partial eraser** with adjustable radius (12–64 dp) and a circle
  preview: it erodes only the part of the stroke it touches
- text notes that are **selectable, movable, resizable (8–160 sp) and
  editable only on a double tap**
- photos imported from the gallery, **selectable, movable, resizable
  (0.25×–6×)**
- **anchored pan/zoom** with two fingers (0.1×–10×) plus one-finger pan
  on empty space
- **Delete** to remove the currently selected element (stroke, note or
  photo)
- **Center** (fit-to-content) and **Reset** (pan 0, zoom 1) as a safety
  net
- **portable export/import** in a single `.zib` file (ZIP + manifest +
  media)
- **Italian and English UI**, chosen inside the app (§15)

The UI is entirely **Jetpack Compose + Material 3**; the data is a pure
serializable model (`kotlinx.serialization`), with no local database
(Room and kapt, declared but never used, were removed after v1.9).

## 2. Features (as of v1.10)

| Area | Detail |
|---|---|
| Bottom toolbar | **Select** · **Pen** ("Pen width" slider, 2–24 dp) · **Eraser** ("Eraser size" slider, 12–64 dp). In Select mode the panel shows contextual hints, different depending on whether nothing is selected, a note is selected, a photo is selected, or a photo is being rotated |
| FABs | **Photo** (SAF `image/*` picker, dropped at the centre of the **board area**) · **Note** (creates "Nuova nota" / "New note" at the centre of the board area, text taken from resources) |
| Top bar | **Clear** (with a confirmation dialog) · **Delete** (enabled only with a selection) · **Center** (enabled once the layout is measured) · **Reset** · **Import** (SAF, `*/*`) · **Export** (SAF, writes `board.zib`) · **Language icon** (menu: System default / Italiano / English, see §15) |
| Selection | Tap on an element = select; tap on empty space = deselect; in SELECT the gesture becomes MOVE, HANDLE or ROTATE depending on the hit |
| Resize | **Four handles**, one per corner (v1.8), 28 dp grab area: dragging one corner keeps the opposite one fixed. On notes it scales the body text (fixed 220 dp width) |
| Photo rotation | **Double tap on a photo** → the handles become four curved arrows; dragging them rotates the photo around its centre, snapping to 0/90/180/270° within 4° |
| Strokes | The eraser erodes the part it touches (leftovers with ≥ 2 points survive); selection shows the bbox; the handle on a stroke does not resize yet (TODO) |
| Note editor | Quick double tap (< 400 ms, `DOUBLE_TAP_MS`) on the note → inline `TextField`, IME "Done"; the rest of the UI never steals the input |
| Portability | `.zib` = ZIP: `manifest.json` (version/name/panX/panY/zoom/elements) + `media/*.jpg` (relative URIs `media/<basename>`) |

## 3. Stack and versions

| Role | Stack |
|---|---|
| Language | Kotlin **1.9.24** |
| UI | Jetpack Compose **1.6.0** (BOM 2024.01.00) + Compose Compiler **1.5.14** + Material3 (BOM) + `com.google.android.material:1.11.0` |
| Build | AGP **8.3.2**, Gradle **8.6** (wrapper), JDK **17** (Temurin 17.0.20), Android platform **34** |
| Data | `kotlinx-serialization-json` 1.6.3, coroutines 1.8.0, lifecycle 2.7.0, activity 1.8.2 — **no annotation processor** (kapt removed after v1.9) |
| Images | Coil **2.5.0** (`AsyncImage`) |
| Target | minSdk **26**, targetSdk **34** |
| App version | **1.10** (versionCode **11**), `applicationId` `it.zibaldone.app` |
| Lint | ktlint **1.2.1** through the `org.jlleitschuh.gradle.ktlint` 12.1.0 plugin (since v1.7) |

## 4. Source architecture (current)

```
zibaldone/
├── app/
│   ├── build.gradle.kts                  # minSdk 26, target 34, version 1.10/11
│   └── src/main/
│       ├── AndroidManifest.xml               # label = @string/app_name
│       ├── java/it/zibaldone/app/
│       │   ├── MainActivity.kt               # entry point, VM provider, locale wrapping
│       │   ├── model/
│       │   │   ├── BoardElement.kt           # serializable sealed class (Drawing/TextNode/ImageNode)
│       │   │   └── BoardId.kt                # atomic id counter (v1.7)
│       │   ├── ui/
│       │   │   ├── BoardScreen.kt            # scaffold, layering, top/bottom bar, FABs, dialog, language menu
│       │   │   ├── BoardGesture.kt           # the single dispatcher for every gesture
│       │   │   ├── HitTesting.kt             # manual hit testing in screen space + HandleCorner
│       │   │   ├── StrokeCanvas.kt           # StrokesCanvas / StrokeNode / EraserPreview / SelectionBox / Handles
│       │   │   ├── TextNodeOverlay.kt        # note: render + on-demand editor
│       │   │   └── ImageNodeOverlay.kt       # photo: rotated render + selection overlay
│       │   ├── util/
│       │   │   ├── CanvasMath.kt             # world<->screen + trimStroke (6px resample) + distances
│       │   │   ├── ExportImportManager.kt    # .zib (zip+manifest), on Dispatchers.IO
│       │   │   └── AppLocale.kt              # UI language: enum + preference + context wrapping (v1.10)
│       │   └── view/
│       │       ├── CameraState.kt            # panX/panY/zoom FloatState + anchored pinch
│       │       └── BoardViewModel.kt         # VM: tools, selection, editing, rotation, strokes, board
│       └── res/
│           ├── values/strings.xml            # English (default / fallback)
│           └── values-it/strings.xml         # Italian
├── build.gradle.kts / settings.gradle.kts / gradle.properties / local.properties
├── .editorconfig                             # the project's ktlint rules
└── gradle/wrapper/
```

### 4.1 Screen layering (draw order)

A white `Box` (fillMaxSize) contains, in this order:
1. **gesture layer**: `Box(Modifier.fillMaxSize().pointerInput { boardDispatch(vm, densityPx) })`
2. **`StrokesCanvas`**: every committed stroke **plus** the one being
   drawn — today each stroke is a **standalone node**, §4.3
3. **node layer**: `for (element in elements)` → `TextNodeOverlay` /
   `ImageNodeOverlay`
4. **`StrokeSelectionBox`**: outline + handle of the selected stroke
5. **`EraserPreview`**: the eraser radius ring, in screen space
6. outside the box: top bar, bottom bar, FABs, confirmation `AlertDialog`

### 4.2 Architectural principle

A **single gesture dispatcher** (`PointerInputScope.boardDispatch`)
decides all input (hit testing in `HitTesting`, modes MOVE/HANDLE/PEN/
ERASE/CAMERA); nodes have no gestures of their own (the editor's
`TextField`, mounted only while editing, uses `pointerInput` for its
selection handles). Selection, moving, resizing, erasing, panning and
zooming all go through the same `vm.*` and compose without conflicts.

### 4.3 Stroke rendering — "a stroke is a node" (v1.5, stable in v1.6)

**Every stroke is a `StrokeNode` composable built like the notes and
photos** (which have always worked on the tablet with any camera):

1. In **body scope** (where Compose snapshot tracking works on this
   device): read `camera.zoom` and apply `camera.worldToScreen` to the
   two corners of the stroke's world bbox (grown by `strokeWidth/2` for
   the round caps).
2. **Layout**: `Modifier.offset { IntOffset(tl) } + .size(widthPx/density dp,
   heightPx/density dp)` → the box is in screen pixels and moves with
   the camera like any other node.
3. **Content**: the stroke's points are already transformed *in body
   scope* into the canvas's local coordinates: `(p − bboxMin) * zoom`,
   and the width is `strokeWidth * zoom`. The draw closure (`drawLine`
   for 2 points, `drawPath` with `Stroke(Round, Round)` otherwise)
   **iterates precomputed data only**: no camera reads inside the
   closure, no `DrawScope` translate/scale, no `graphicsLayer`.

**Why this way** (crucial — see §14 "Device findings" for the story):
on the user's tablet, camera updates **do not propagate** into
transforms applied at draw time (`DrawScope.translate/scale` inside the
closure, v1.2) nor into `graphicsLayer` (block form in v1.1,
property-style in v1.3): the strokes stayed "frozen" or drew "at a
distance". The same state change, applied **through layout
(offset/size) from body-scope reads**, always propagates — and it is
exactly the mechanism `TextNodeOverlay` uses.

Practical consequences: no giant buffer (each canvas is small and
local), no clipping possible, native resolution at every zoom level, no
GPU layer cost, and the input identity
`screen = pan + zoom·(screen−pan)/zoom` is guaranteed by the same single
transformation step.

### 4.4 Perf / why it no longer stutters

- `panX/panY/zoom` are **`mutableFloatStateOf`** — granular observation
  of 3 scalars.
- Every camera-driven recomposition touches only: the layout of
  `StrokeNode`/nodes (offset/size) plus the `EraserPreview`.
- The stroke in progress: `activeStroke` + `liveStrokeVersion` →
  `StrokesCanvas` re-reads only for `updateStroke`.
- The eraser: only the elements it touches enter `eraseAt` (which does
  `clear + addAll` on the list).

## 5. Data model (package structure unchanged)

```kotlin
@Serializable sealed class BoardElement {
    abstract val id: Long
    abstract val kind: String           // JSON discriminator

    data class Drawing(  id, points: List<SerializablePoint>,
                         color: Int = 0xFF000000.toInt(), strokeWidth: Float = 4f )
    data class TextNode( id, text, position: SerializablePoint,
                         fontSize: Float = 20f, color: Int = 0xFF000000.toInt() )
    data class ImageNode(id, imageUri, position: SerializablePoint,
                         scale: Float = 1f, rotation: Float = 0f )
}
```

`.zib` = a ZIP with `manifest.json` (`BoardManifest(version=1, name,
panX, panY, zoom, elements)`) + `media/*.jpg` with relative URIs
`media/<basename>.jpg`. **The format has never changed**: boards
exported in the past are still importable.

## 6. Camera

`view/CameraState.kt` — `panX`, `panY`, `zoom`
(`mutableFloatStateOf`, zoom clamped to 0.1–10):

```
world  = (screen − pan) / zoom
screen = world * zoom + pan
panBy(Δscreen)
pinch(prevCentroid, centroid, prevSize, size)
```

**Pinch anchored on the centroid** (so things don't fly away):
```
k     = newZoom / zoom
pan'  = c − (c − pan) * k   +   (c − c_prev)          [c = current centroid]
```
that is: zoom anchored on the point under the fingers, plus the
centroid's translation (the "pan" movement during the pinch).

## 7. Gestures (single dispatcher)

`ui/BoardGesture.kt` — `suspend fun PointerInputScope.boardDispatch(vm, densityPx)`
(`awaitEachGesture` + an `awaitPointerEvent()` loop).

| Finger | SELECT | PEN | ERASER |
|---|---|---|---|
| 1 on an element | tap = select; ≥ slop = **move** (Δ/zoom, world); quick double tap = **note editor** | — | — |
| 1 on a handle | tap = no-op; ≥ slop = **resize** (note: font; photo: scale) — the stroke has the handle but does not resize yet | — | — |
| 1 on empty space | tap = deselect; drag = **pan** | **draw** | **partial erase** |
| ≥ 2 | **anchored pinch-zoom** + pan | same (stroke in progress is discarded) | same |

Details and rules learned over the iterations:

- `MOVE_SLOP_PX = 12f`: below the slop it is a tap (selection/
  manipulation only); above it is MOVE/HANDLE. This removed the
  micro-drags that in v1.1 made elements "disappear" on a light touch.
- **`lastPointer` must be updated in EVERY branch of the loop** — in
  v1.4 this was the "pan jumps when you lift one finger during a pinch"
  bug: the ≥ 2 finger branch ended with `continue` without updating
  `lastPointer`, so on the first single-finger event the delta was the
  distance accumulated over the whole pinch. Fix: `lastPointer =
  centroid` on every ≥ 2 finger event (fallback `pressed[0]`).
- Double tap: in MOVE with `< slop` and `< 330 ms` since the previous
  tap on the same note → `startEditingText`.
- Pan = `camera.panBy(changed − last)`.

## 8. Overlays (node composables)

### 8.1 `TextNodeOverlay`
- Non-interactive render: `Text` + (when selected) a blue border;
- `isEditing` (double tap): `Text` replaced by a white `TextField`,
  `FocusRequester` on mount, IME action **Done** (closes the editor and
  saves), a cross to cancel/close;
- **layout = `offset { worldToScreen(pos) } + width(220dp·zoom)`**, text
  at `fontSize·zoom`, height `max(28dp, 18dp·lines·zoom)` — all read in
  body scope (the §4.3 pattern that works in every version).

### 8.2 `ImageNodeOverlay`
- `AsyncImage` (Coil) with `ContentScale.Crop` inside
  `size(200dp·scale·zoom)`; blue border when selected; the shared
  handle.

### 8.3 `StrokeCanvas.kt`
- `StrokesCanvas`: body scope reads `elements` + `activeStroke` +
  `liveStrokeVersion` and mounts **one `StrokeNode` per stroke**
  (committed and in progress), §4.3;
- `EraserPreview`: a screen-space ring at the exact finger radius;
- `StrokeSelectionBox` + `SelectionHandle`: the bbox frame of the
  selected stroke + its handle (14 dp blue, 4 dp white centre).

## 9. Build

The machine-specific environment and the delivery protocol moved to
[`docs/MAINTAINING.md`](docs/MAINTAINING.md): they are operations, not
engineering. What stays here is what anyone building the project needs.

### 9.1 Commands
```bash
./gradlew :app:clean :app:assembleDebug --console=plain --offline
```

`JAVA_HOME` must point at a JDK 17 and `ANDROID_HOME` at an SDK with platform 34;
on the maintainer's machine neither is on `PATH`, so both are exported by hand
(the exact paths are in `docs/MAINTAINING.md` §1).

`--offline` is deliberate: the dependencies are expected to be in the local
Gradle cache already. A release build cannot run offline the first time —
`lintVitalRelease` pulls `com.android.tools.lint:lint-gradle`, which is not in
the cache until it has been fetched once.

### 9.2 Release build and signing

```bash
./gradlew :app:assembleRelease --console=plain
# -> app/build/outputs/apk/release/app-release.apk
```

The signing material is **not** in the repository: `keystore.properties` at the
repo root is gitignored and points at a keystore kept in `~/keystores/`. When
that file is missing — a fresh clone, a CI runner — the release build is left
**unsigned** rather than failing, so nobody needs the maintainer's key to build
the project.

| | |
|---|---|
| Keystore | `~/keystores/zibaldone-release.p12` (PKCS12, RSA 4096, valid until 2054) |
| Alias | `zibaldone`, DN `CN=Zibaldone, O=Zibaldone, C=IT` |
| Credentials | `keystore.properties` (gitignored, mode 600) |
| Verified with | `apksigner verify --print-certs` → v2 scheme, 1 signer, SHA-256 `18fcd9b1…72dda8e9` |

`isMinifyEnabled` stays **false** in release: `kotlinx.serialization` resolves
the serializers of the sealed `BoardElement` hierarchy reflectively, and turning
R8 on without keep rules and a full on-device pass would be a silent risk for no
real gain at this size.

**The release key is not the debug key.** An APK signed this way cannot be
installed over a debug-signed Zibaldone (v1.10 and earlier): Android refuses the
update, and the app has to be uninstalled first — which also drops the images
imported into `filesDir`. Export any board you care about as a `.zib` before
switching a device from a debug build to a release one.

### 9.3 Continuous integration

`.github/workflows/ci.yml` runs `:app:ktlintCheck` then `:app:assembleDebug` on
every push to `main` and every pull request, and uploads the debug APK as a
build artifact. No `--offline` there: a runner has to download its dependencies.

## 10. Build lessons (in order, across all sessions)

1. Missing `Theme.Material3.DayNight.NoActionBar` theme →
   `com.google.android.material:material:1.11.0`
2. Compose Compiler 1.5.14 ↔ Kotlin 1.9.24 pairing
3. AGP 8.3.2 requires Gradle ≥ 8.4 → wrapper **8.6**
4. `Color.value` in Compose 1.6 is a `ULong` → keep colours as **ARGB
   Int**
5. `StrokeCap`/`StrokeJoin` live in `androidx.compose.ui.graphics`
6. `graphicsLayer` (named) lives in `androidx.compose.ui.graphics`
7. `awaitEachGesture`/`awaitFirstDown` are in `foundation.gestures`;
   `awaitPointerEvent()` is a member of the gesture scope
8. `PointerInputChange` does not expose `.delta` → do the pinch with
   `calculateCentroid()` + `calculateCentroidSize()`
9. `getDistance` was removed → local `Offset.distance()` helper
   (`ui/HitTesting.kt`)
10. `viewModel()` → `lifecycle-viewmodel-compose:2.7.0`

**Session v1.1 (interaction revamp):**
11. Trailing argument comma at the end of a lambda block: trailing commas
    are legal inside `f(...)`, **not** inside `{ ... }`
12. `awaitEachGesture` has a **`PointerInputScope`** receiver → the
    dispatcher is `suspend fun PointerInputScope.boardDispatch(...)`
13. `Offset.distanceTo()` **does not exist** in `ui.geometry`
14. `ImeAction` lives in **`androidx.compose.ui.text.input`** (not
    `ui.input.ime`)
15. `Modifier.focusRequester` is a separate extension that must be
    imported explicitly (`androidx.compose.ui.focus`)
16. In Compose 1.6 there is no `Modifier.offset(IntOffset)` nor
    `offset(Int, Int)`: use `offset { IntOffset(x, y) }`
17. `val ... by mutableStateOf` does not re-subscribe: use `var` for
    `activeStroke`/`liveStrokeVersion`
18. `fun setEraserPreview(Offset?)` collided with the setter of the
    `var eraserPreview: Offset?` property (same JVM signature) → the
    function was dropped

**Sessions v1.2–v1.6:**
19. `MutableStateList.replaceAll(list)` is not accepted with a `List<T>`
    in this environment (the signature wants a `UnaryOperator`); use
    `clear() + addAll()`
20. The import `androidx.compose.graphics.TransformOrigin` would not
    resolve (broken build at tooling runtime); the working import is
    `androidx.compose.ui.graphics.TransformOrigin` (no longer needed
    after v1.4)
21. `Modifier.size(width, height)` wants **`Dp`**, not `Float`:
    `(px / density).dp`
22. `drawCircle`/`drawLine`/`drawPath` are **members of `DrawScope`**
    (no import needed); `Stroke` and `Path` do need
    `drawscope.Stroke` / `graphics.Path`

## 10.1 Cleanup after v1.9 — Room and kapt removed

Room 2.6.1 (`room-runtime`, `room-ktx`, `room-compiler` through kapt)
had been declared in `app/build.gradle.kts` **since v1.0 and was never
used**: there is not a single `@Entity`, `@Dao` or `@Database` anywhere
in `app/src`. The cost was not size but build time: the `kotlin-kapt`
plugin alone adds `kaptGenerateStubsDebugKotlin` and `kaptDebugKotlin`
to every compilation, even when there is nothing to process.

Measured over consecutive clean builds (`:app:clean` +
`:app:assembleDebug --offline`), same machine, daemon restarted:

| | With Room + kapt | Without | Difference |
|---|---|---|---|
| Clean build time | 16.8 s | 14.7 s | **−2.1 s (−12%)** |
| Tasks executed | 37 | 34 | −3 |
| APK | 19,995,908 bytes | 19,842,038 bytes | **−150 KB** |

Verified after the removal: **0** references to `androidx.room` in the
dex, 301 to `zibaldone` (the app is intact), `ktlintCheck` green.

Local junk was cleared out at the same time: `app/build/` (167 MB) and
`.gradle/` (3.4 MB), both regenerable, plus the `res/drawable/` and
`res/layout/` folders — empty from the start and never referenced,
since the app is entirely Compose.

## 11. Version history and root causes

| v | build | Changes |
|---|---|---|
| v1.0 | 1 | First working build; the pen only worked at pan=0/zoom=1 |
| v1.1 | 2 | Full interaction revamp (toolbar, tools, notes/photos, editor, anchored pinch, eraser, `Center`/`Reset`, `.mboard`), `transformOrigin` fixed |
| v1.2 | 3 | Stable pan (slop), unified screen-space `StrokesCanvas`, **partial** eraser (`trimStroke`), `Center` (fit-to-content) |
| v1.3 | 4 | Attempt at property-style `graphicsLayer` + world canvas buffer — **failed on the tablet** (strokes "frozen", unstable pan) |
| v1.4 | 5 | **`StrokeNode`** (a stroke is a node, via layout) + `lastPointer` fix in the pinch |
| v1.5 | 6 | **Geometry bug fixed**: canvas points are transformed `(p−min)·zoom` in body scope (the Box was screen-space while the content was in world space → it slid at zoom ≠ 1) |
| v1.6 | 7 | **`Delete` button** (removes only the selected element) in the top bar |
| v1.7 | 8 | **Code audit**: 14 bugs fixed (ANR on export/import, imported images in the cache, `Float.MIN_VALUE` bbox, note quota without `densityPx`, `Center` over the whole screen, hit test on vertices only, pinch drift, timestamp ids) + ktlint |
| v1.8 | 9 | **Handles on all 4 corners** (anchored to the opposite corner, 28 dp grab area) + **photo rotation** with curved arrows on double tap (`rotation` finally exposed in the UI) |
| v1.9 | 10 | **Renamed to Zibaldone**: `applicationId`/package `com.moodboard.app` → `it.zibaldone.app`, label, in-app title, `Moodboard*` classes → `Board*`, extension `.mboard` → `.zib` |
| v1.10 | 11 | **Bilingual IT/EN UI**: every string moved to `res/values` (EN, default) + `res/values-it` (IT), language chosen inside the app (globe menu in the top bar) and persisted in `SharedPreferences` |

**v1.2 — symptoms on v1.1 and fixes** (detail):
1. A light touch was a micro-drag applied immediately → **12 px slop**.
2. Content "disappeared" after panning far away: the stroke canvas was a
   screen-sized buffer in world coordinates inside the layer, and it
   permanently clipped strokes outside the window; on top of that, the
   default `transformOrigin` (centre) desynchronised zoom and nodes. →
   a single full-screen `StrokesCanvas` applying the transform inside
   the draw call (no clipping) + a `pan(screen px) + zoom` camera.
3. The eraser deleted the whole stroke → `trimStroke`: resample at a
   6 px step, keeping the points outside the radius and the leftovers
   with ≥ 2 points (new `Drawing`s, `nanoTime` ids). Radius 12–64 dp
   (28 by default), preview ring at the exact radius.
4. Added `Center` (element bbox + 40 px padding, clamp 0.1–10, centred
   on screen) and `Reset` (`pan=(0,0), zoom=1`).

**v1.3 — the attempt at "the pen follows the finger again"**
Symptom on v1.2: after zoom/pan the stroke no longer followed the finger
(everything else was fine). Hypothesis confirmed: a camera transform
applied **inside** the draw closure did not update with the camera state
on that tablet, while the LAYOUT mechanism used by the nodes did. Fix
(v1.3): property-style `graphicsLayer` with `transformOrigin=(0,0)` on
the camera Box (body-scope reads) + a "pure world" canvas content
(buffer computed around the content, 8192 px ceiling).

**v1.3 did not work: symptoms on the tablet**
The strokes stayed **frozen in place** (the layer did not update); pan/
zoom became hypersensitive: **lifting one finger made everything jump**
(the `lastPointer` bug, §7).

**v1.4 — the "a stroke is a node" architecture**
- Fixed `lastPointer = centroid` on every ≥ 2 finger event (§7).
- Every stroke → a `StrokeNode`: `worldToScreen` on the bbox in body
  scope + `offset/size` (layout) + a local
  `Canvas(Modifier.fillMaxSize())`. The same mechanism as notes and
  photos — the only one proven on the device.

**v1.4 still had a bug (fixed in v1.5)**
At zoom ≠ 1 the content was not multiplied by `zoom` (screen-space Box,
world-space points): the stroke "slid" away from the tracked position
exactly when zooming — precisely what was observed.

**v1.5 — the complete transform (stable)**
`(p − bboxMin)·zoom` as a `localPts` list in body scope; width
`strokeWidth·zoom`; the closure iterates `localPts` + `strokeW` only —
no state inside. The same shape as `TextNodeOverlay` (body → transform →
layout), which has worked for the user in every version.

**v1.6 — `Delete`**
Top bar: `TextButton(enabled = selectedId != null) {
viewModel.removeElement(id) }`. The VM already had `removeElement` (it
removes the element, clears the selection, and leaves editing if the
note was being edited). Works for strokes, notes and photos.

**v1.7 — full code audit (14 bugs)**
No symptom was reported by the user: this was a complete re-reading of
the 12 source files looking for latent defects. Ordered by severity.

*Critical — data loss / app freeze*
1. **ANR on export/import/add photo.** `ExportImportManager.saveBoard`
   and `loadBoard` were `suspend` but without
   `withContext(Dispatchers.IO)`, and `viewModelScope` is Main: the
   whole zip (and the copy of the picked photo in `addImageFromUri`) ran
   on the UI thread. → `Dispatchers.IO` in all three places.
2. **Images of imported boards in `cacheDir`.** `loadBoard` extracted
   into `cacheDir/imports/<ts>/` and the nodes pointed there forever:
   Android empties the cache when it needs space → broken photos.
   Worse, on re-export `saveBoard` silently skipped the missing files,
   producing a `.mboard` whose manifest referenced absent images. →
   extraction into `filesDir/imports/<ts>/` (persistent), cleanup of
   previous imports after a successful load, and `SavedBoard.missingMedia`
   reported in the Toast.

*High — visible errors*
3. **`Float.MIN_VALUE` as the seed for a maximum** (it is the smallest
   *positive* float, 1.4E-45, not the most negative one): a stroke
   entirely at negative world coordinates left `maxX/maxY` at ~0. In
   `StrokeSelectionBox` the blue frame stretched all the way to the
   world origin; in `HitTesting.strokeBbox` it inflated the bbox. →
   `NEGATIVE_INFINITY` (as `StrokeNode` already did).
4. **`nodeRect` for notes: `densityPx` missing from the height.** The
   width had it, the line term did not → on a 2–3x screen the rectangle
   ended up well above the drawn note: the lower half was not selectable
   and the **resize handle could not be grabbed where it is painted**
   (drawing uses the Box's real corner, hit testing used the estimate).
   → height aligned with the rendering:
   `(10dp + lines · fontSize · 1.4) · zoom · densityPx`, with `ceil`
   instead of `roundToInt` on the line count.
5. **`Center` with photos.** `fitToContent` treated `position` as the
   centre (`± half`), while rendering and hit testing use it as the
   **top-left corner**. → consistent bbox (`position` → `position +
   side`).
6. **`Center` and "insert at the centre" measured the whole screen**
   (`LocalConfiguration`), not the board area, which is what is left
   after the top and bottom bars (150+ dp): overestimated zoom, wrong
   vertical centring, and new notes/photos below the visible centre. →
   `onSizeChanged` on the board Box and `canvasCenterPx()`.

*Medium*
7. **Hit testing and erasing only on recorded vertices**: with a fast
   finger the points are dozens of units apart and the middle of a
   segment could neither be selected nor erased. →
   `CanvasMath.distanceToSegmentSq` + `polylineContains`, used by
   `strokeContainsPoint` and by `eraseAt`.
8. **Pinch drift.** The formula was `c − (c − pan)·k + (c − c0)`: it
   added the centroid's displacement on top of an anchor that was
   already translating, with an error of `(c − c0)·(1 − k)` per frame
   (exact only at `k = 1`, i.e. pure pan). → the correct law
   `newPan = c − (c0 − pan)·k`.
9. **Eraser: `clear()` + `addAll()` on every move event** invalidated
   every node on the board each frame. → in-place splice walking
   backwards; the first surviving piece **keeps the original id**
   (selection preserved, less churn).
10. **`trimStroke` duplicated points.** The resample started at `k = 0`,
    re-emitting the vertex shared by two segments: points doubled on
    every eraser pass and ended up in the `.mboard`. → start at `k = 1`.
    The discarding of segments whose first point == last point was
    dropped as well.

*Low*
11. **Zip-slip without a separator**:
    `startsWith(packageDir.canonicalPath)` accepted sibling folders with
    a common prefix (`.../imports/123` vs `.../imports/1234evil`). →
    `+ File.separator`.
12. **Timestamp ids** (`currentTimeMillis()` in the defaults,
    `nanoTime()` in the eraser): two elements created in the same
    millisecond shared an id, and select/move/delete acted on both. →
    `BoardId` (an atomic counter seeded from the clock) +
    `BoardId.observe()` on imported ids, so a `.mboard` from another
    device cannot collide.
13. **`offset { IntOffset(x.toInt(), …) }`**: truncation goes towards
    zero, so a node crossing the origin flickered by 1px asymmetrically.
    → `roundToInt()` in all four overlays.
14. **`BoardManifest.version` was never checked** on read. → compared
    against `CURRENT_FORMAT_VERSION`, with an explicit error on a newer
    format.

*Around the edges*
- Added **ktlint** (`org.jlleitschuh.gradle.ktlint` 12.1.0, ktlint
  1.2.1) with an `.editorconfig`: trailing commas disabled (the
  project's style) and `@Composable` exempted from the lowercase-name
  rule. `./gradlew :app:ktlintCheck` / `:app:ktlintFormat`.
- Fixed the header comment of `MoodboardScreen`, which still described
  the camera as `graphicsLayer` (the v1.1–v1.3 architecture, disproven
  on this tablet) while the code has been "a stroke is a node" since
  v1.4.
- The `.mboard` format **did not change**: v1.6 boards open in v1.7.

**v1.8 — four corner handles and photo rotation**
User request: handles that are easier to grab and, on a second tap,
curved arrows to rotate photos.

*Resize handles (`SelectionHandles`)*
- From **1 to 4**, one per corner (`HandleCorner`), drawn **centred on
  the corner** (offset by half a handle outwards) instead of 7dp inside:
  before, the dot did not sit on top of its own grab area. Visually
  20dp, white with a 3dp blue border; grab area `HANDLE_RADIUS_DP` from
  22 → **28dp**. `nearestCorner()` picks the closest corner, so on a
  small node the overlapping areas resolve without ambiguity.
- **Anchored to the opposite corner**: while dragging one corner, the
  diagonally opposite one stays put in world coordinates
  (`resizeImageAnchored` recomputes `position` from the new side).
- **Fixed a resize bug** that was present up to v1.7: the anchor was the
  *grabbed* corner, so `handleStartDist = down.distance(corner)` was ~0
  (clamped to 1px) if you touched the handle at its centre — and the
  first pixel of movement sent the scale to its limit. The anchor is now
  the diagonal, so the initial distance is always meaningful.
- Notes scale only the **body text** (width fixed at 220dp) and stay
  anchored to their own top-left; the incremental `scaleText(factor)`
  became an **absolute** `setTextSize(newSize)`, computed from the size
  at grab time: no drift accumulates during the gesture.

*Photo rotation (`RotationHandles`)*
- **Double tap on a photo** → `rotatingId`: the four resize handles are
  **replaced** by four curved arrows (`Icons.Filled.RotateRight`, 30dp
  on a blue disc), so a corner never means two things at once.
- Double tap on a **note**: unchanged, it opens the text editor.
- Dragging an arrow rotates around the node's centre (`atan2` of the
  centre→finger vector, delta added to the initial rotation).
  **Snapping to 0/90/180/270°** within 4°, to straighten things easily.
- You leave the mode by touching anything else (`select()` clears
  `rotatingId`).
- `ImageNode.rotation` **already existed** since v1.1 and was serialized
  but never exposed (it was listed in §13 among the known limitations):
  the `.mboard` format therefore **does not change**, v1.7 boards open
  in v1.8 and vice versa.

*Rendering — mind the device rule (§14)*
The photo is rotated with `Modifier.rotate()` (which is internally a
`graphicsLayer`), but **the camera does not go through it**: `rotate`
only affects drawing, after `offset`/`size`, so pan/zoom still reach the
node exclusively through layout. The rotation is a static value of the
element, not camera state read inside a closure — the case that failed
in v1.1–v1.3.
The selection frame and the handles are a **sibling, non-rotated Box**
positioned with the same `offset`/`size`: they stay axis-aligned and
therefore consistent with the hit test, which remains a plain rectangle.
*Known limitation*: at rotations that are not multiples of 90° the photo
sticks out of the selection frame and the corners of the touch area do
not follow the rotated image (the centre always works).

**v1.9 — the app becomes Zibaldone**
No functional change: identity only. The user had never really used the
app (tests only), so even the `applicationId` could change — which
otherwise would have meant losing the local storage.

| What | Before | After |
|---|---|---|
| `applicationId` / `namespace` | `com.moodboard.app` | `it.zibaldone.app` |
| Kotlin package | `com.moodboard.app.*` | `it.zibaldone.app.*` |
| Label (launcher) | `"Moodboard App"` hand-written in the manifest | `@string/app_name` → **Zibaldone** |
| In-app title | literal `Text("Moodboard")` | `stringResource(R.string.app_name)` |
| `rootProject.name` | `MoodboardApp` | `Zibaldone` |
| Classes | `MoodboardViewModel`, `MoodboardScreen` | `BoardViewModel`, `BoardScreen` |
| Export extension | `board.mboard` | `board.zib` |
| Delivery APK | `~/Moodboard-v<n>.apk` | `~/Zibaldone-v<n>.apk` |

Notes:
- The label and the title now **both** go through `strings.xml`. Before,
  the manifest had the literal string and `app_name` was used by nobody:
  changing the name meant remembering two places.
- Class names moved to `Board*` rather than `Zibaldone*`: the code's
  domain is already all `BoardElement`/`BoardGesture`/`BoardId`/
  `BoardHit`/`BoardTool`/`BoardManifest`, so the consistent prefix is
  `Board`. That keeps the commercial name of the app confined to
  `strings.xml` and the package.
- **The exported package structure does not change** (a ZIP with
  `manifest.json` + `media/`): only the suggested extension changed on
  export. Old `.mboard` files **still import**, because the import
  picker accepts `*/*` and reads the ZIP by content.
- A different `applicationId` means Android sees **a different app**:
  the old "Moodboard App" stays installed alongside and must be
  uninstalled by hand; its data (`filesDir`) does not migrate.
- Verified with `aapt2 dump badging`: `package: name='it.zibaldone.app'`,
  `application-label:'Zibaldone'`. In the dex: **0** occurrences of
  `moodboard`.
- **The v1.9 tag and GitHub release were deleted** while preparing the
  repository to go public: it was a test build nobody outside this
  machine had used (one download, by the maintainer). The commit
  `f71ad21` and this section stay — the version is still part of the
  history, it just has no downloadable artifact any more. v1.10 is
  therefore the oldest tag.

**v1.10 — the UI speaks two languages**

*Request.* "Is it possible to make an English version of this app and
let whoever installs it choose between Italian and English?"

*Starting point.* There was no English version, and no way to make one:
`strings.xml` held **only** `app_name`, and every visible text was an
Italian string literal inside the Kotlin (top bar, tools, contextual
hints, confirmation dialog, `contentDescription`s, ViewModel Toasts, the
default text of a new note). It was not a bug — it was simply code never
meant to be translated.

*What changed.*
1. Every visible string extracted into resources:
   `res/values/strings.xml` (**English**, default and fallback) and
   `res/values-it/strings.xml` (**Italian**). The count of missing media
   on export became a `<plurals>`, so the singular/plural form is right
   in both languages instead of being nailed to the plural.
2. New `util/AppLocale.kt`: an `enum AppLocale` (`SYSTEM`/`ITALIAN`/
   `ENGLISH`, BCP-47 tags) and an `object LocalePreference` that
   persists the choice and applies it through
   `createConfigurationContext`.
3. `MainActivity.attachBaseContext` applies the locale **before** any
   resource is resolved; the globe menu at the end of the top bar saves
   the choice and calls `recreate()`.

The mechanism and its rationale are detailed in **§15**.

*Decision worth remembering.* No
`AppCompatDelegate.setApplicationLocales` and no
`android:localeConfig`: a single `ComponentActivity` with no AppCompat
theme, `minSdk = 26` (the per-app language API is native only from API
33), and the wish to keep **one single source of truth** for the choice.

*Why the board is not lost when the language changes.* `recreate()` is
the same path as a rotation: the `BoardViewModel` is retained by the
activity's `ViewModelStore`, so elements, selection and camera stay
where they are. Nothing is serialized or reloaded.

*What did NOT change.* The `.zib` format (existing boards import
identically), the rendering and the gestures: not a line was touched in
`BoardGesture`/`StrokeCanvas` beyond one `contentDescription` moved to a
resource, so the device rule of §14 stands untouched.

## 12. Verification

- Build: `:app:clean :app:assembleDebug` → **BUILD SUCCESSFUL** (v1.10).
- Lint: `:app:ktlintCheck` → **BUILD SUCCESSFUL** (v1.10).
- **On the tablet (2026-09-11, v1.10): the checklist below passes.** The
  language switch keeps the board — elements, camera and selection all
  survive the `recreate()` — and the UI, the hints and the toasts follow
  the chosen language.
- Dex (verified over every `classes*.dex`):
  | v | grep marker |
  |---|---|
  | v1.2 | `trimStroke`, `fitToContent`, `eraseAt`, `StrokesCanvas`, `MOVE_SLOP_PX` |
  | v1.5 | `localPts` |
  | v1.6 | `"Elimina"` |
  | v1.7 | `BoardId` (classes4: 3, classes5: 1), `polylineContains` (classes5: 1, classes6: 2), `distanceToSegmentSq` (classes6: 2), `SavedBoard` (classes5: 1, classes6: 3), `canvasCenterPx` (classes5: 2) |
  | v1.8 | all in classes5: `HandleCorner` (8), `RotationHandles` (7), `RotateHandle` (4), `resizeImageAnchored` (3), `setImageRotation` (2), `startRotating` (1) |
  | v1.9 | `zibaldone` in 6 dex (classes2: 3, classes3: 24, classes4: 45, classes5: 16, classes6: 183, classes7: 30), `BoardViewModel` (classes4: 41), `BoardScreen` (classes6: 174); **0** occurrences of `moodboard` |
  | v1.10 | `AppLocale` (classes: 19, classes3: 11, classes6: 2, classes8: 5), `LocalePreference` (classes3: 2, classes5: 1, classes6: 1, classes8: 14), `findActivity` (classes6: 3), `ui_language` (classes3: 1). Also in `resources.arsc`: **both** "Svuotare la board?" and "Clear the board?" are present → the two locales really are packaged |
- **Tablet checklist (v1.10):**
  1. Open the app on a tablet set to Italian → the UI is **in Italian**
     (it follows the system; no choice saved yet).
  2. **Globe icon** at the end of the top bar → a menu with
     *System default · Italiano · English*, with a check mark on the
     active entry.
  3. Pick **English** → the screen is recreated and everything is in
     English (`Clear / Delete / Center / Reset / Import / Export`,
     `Select / Pen / Eraser`, the hints at the bottom, the *Clear*
     dialog).
  4. Put a few elements on the board before switching: after the switch
     they **must still be there**, with the same camera and the same
     selection.
  5. New note from the FAB → the default text is "New note" in English,
     "Nuova nota" in Italian.
  6. Export and re-import a board → the Toasts are in the chosen
     language; a `.zib` saved with v1.9 still imports.
  7. Close and reopen the app → the chosen language **sticks**.
  8. Go back to *System default* → the tablet's language returns.
- **Tablet checklist (v1.8):**
  1. Select a photo → **four** white/blue handles, one per corner,
     centred on the corner.
  2. Drag each of the four corners → the photo scales and **the opposite
     corner stays put**; no jump on the first pixel of movement.
  3. **Double tap on a photo** → the handles become four curved blue
     arrows.
  4. Drag an arrow → the photo rotates around its centre; near
     0/90/180/270° it snaps.
  5. Touch elsewhere → rotation mode exits, the handles return.
  6. **Double tap on a note** → text editor, as before (it does not
     rotate).
  7. Export with a rotated photo, then import → the rotation is kept.
- **Tablet checklist (v1.7):**
  1. Large note: select it and **grab the blue handle exactly where you
     see it** (before, you had to touch higher up). The same goes for
     touching the lower half of the note.
  2. Move the camera to the top left (negative world), draw a stroke and
     select it → the blue frame **wraps the stroke**, it does not
     stretch towards the board origin.
  3. With a few photos on the board press **Center** → everything fits
     on screen, centred in the board area (not under the bottom bar).
  4. Add a note with the FAB → it appears **at the visible centre**.
  5. Draw a long line moving your finger **fast**, then touch halfway
     between two points → it gets selected; with the eraser, cross it
     fast → it gets erased.
  6. Pinch zoom while **moving** the fingers at the same time → no
     drift.
  7. **Export** a board with photos → no UI lag or freeze; **Import**
     into a heavy board → same. If an image were missing, the Toast says
     so ("N images were not found").
  8. Import a board, close the app, reopen it, open that board again →
     the photos are still there (they could vanish with a cache cleanup
     before).
- **Tablet checklist (baseline, since v1.6):**
  1. Draw a few strokes with the pen → they follow the finger.
  2. Pinch in / pinch out → everything grows/shrinks **under the
     fingers**; no jump when a finger is lifted.
  3. One-finger pan on empty space → the world follows the finger;
     notes, photos and strokes stay aligned with each other.
  4. Eraser: cross the middle of a line → only the crossed part
     disappears.
  5. Note: tap = select; double tap = editor; Done = closed; handle =
     resize; **Delete** = gone.
  6. Photo: import, tap, move, handle, **Delete**.
  7. Lost? **Center**. Back to the origin? **Reset**.
  8. **Export** → `.mboard`; restart; **Import** → identical board, same
     framing (pan/zoom saved in the manifest).

## 13. Known limitations / roadmap

- No **undo/redo**: the eraser is destructive (partial, though), and so
  are `Delete` and `Clear`.
- No **colour picker** (strokes and text are fixed to black; the model
  already has the `color` field — a slider would be enough).
- **One board** per session, held in memory in the ViewModel: there is
  no automatic persistence, you only save by exporting a `.zib`.
  ~~Room is declared but unused~~ → **removed after v1.9** together with
  kapt. If persistence is ever needed, consider `DataStore` first, or
  simply serializing the manifest to a file: the model is already
  entirely serializable.
- ~~Photo `rotation` is persisted but not exposed in the UI~~ →
  **done in v1.8** (double tap → curved arrows). What remains is that at
  rotations which are not multiples of 90° the photo sticks out of the
  selection frame and the touch area does not follow the rotated image
  (it stays axis-aligned): a faithful hit test would need the inverse
  transform of the point.
- The **width of a note** is an estimate (220 dp × zoom, height
  `max(28, 18·lines) dp × zoom`) — very long words can escape the
  hitbox.
- The handle on a **selected stroke**: it is there but does not resize
  yet (scaling of the points + strokeWidth is missing in the `vm`).
- Performance on **very** dense boards (hundreds of strokes): every
  stroke is a composable — if this ever becomes a bottleneck, optimise
  with batching or a `drawBehind` in an "already transformed" canvas for
  the static part only.
- **README screenshots**: `docs/screenshot-board.jpg` (a photo in
  rotation mode, a freehand stroke, a note) and
  `docs/screenshot-language-menu.jpg` (the language menu open). Both are
  captures from the tablet with the EXIF stripped before committing —
  screenshots carry a capture timestamp, and the repository is meant to
  go public. Nothing shows the Italian UI yet; a third capture would
  close that, but nothing depends on it.

## 14. Device findings (the user's tablet, Compose 1.6.0) — the lesson

This is the section to preserve. **Two "camera transform" mechanisms
behave differently on this device:**

1. **RELIABLE** (used in v1.4/1.5/1.6):
   **layout** — `Modifier.offset { IntOffset(...) }` + `.size(Dp)` —
   with camera reads in the composable's **body scope**.
   Demonstrated on the device: `TextNodeOverlay`/`ImageNodeOverlay` move
   correctly with pan/zoom, at every zoom level, in **every** version of
   the app; `EraserPreview` (a Canvas that "draws in screen space from
   body-scope reads") does the same; the stroke in progress (v1.0,
   "draw in world + `graphicsLayer`") followed the finger.
2. **NOT** reliable (did not track the camera on this tablet; do NOT use
   for strokes):
   - a `graphicsLayer` **block** inside a composable (v1.1: out of sync)
   - `DrawScope.translate/scale` inside a draw closure (v1.2: the pen
     drew "far away")
   - **property-style** `graphicsLayer` (v1.3: strokes "frozen")

**Working rule** for this device: the camera enters rendering **once,
through layout**, and is read **in the composable's body**; draw
closures iterate **precomputed data**, never state. (This also agrees
with "Perf" §4.4: the GPU layer re-reads nothing when the camera
changes, so it moves without recomposition.)

**Delivery rule** (learned on v1.0): if the user says "it looks the
same", check the **`versionCode`** and that the APK name differs, and
**verify the dex** (unzip + strings over every `classes*.dex`) before
talking about a bug.


## 15. UI language (i18n)

Since **v1.10**: the UI exists in **Italian and English**, and the
language can be chosen inside the app.

**Where the strings live.** Every visible string (top bar, tools,
contextual hints, confirmation dialog, `contentDescription`s,
import/export Toasts, the text of a new note) is in
`res/values/strings.xml` (**English, default/fallback**) and
`res/values-it/strings.xml` (**Italian**). The deliberate exceptions:

- `app_name` stays "Zibaldone" in both;
- `language_italian` / `language_english` are endonyms, so they live in
  the default file only;
- the messages of internal exceptions (`ExportImportManager`,
  `BoardViewModel`) stay in English: they never reach the user;
- the count of missing media on export uses a `<plurals>`, not a string
  with an inline `%d`.

**How the choice is applied.** `util/AppLocale.kt`:

- `enum class AppLocale(val tag: String)` — `SYSTEM` (empty tag),
  `ITALIAN`, `ENGLISH`; the *BCP-47 tag* is what gets persisted;
- `object LocalePreference` — reads/writes the choice in
  `SharedPreferences` (`zibaldone_settings` / `ui_language`) and offers
  `wrap(base)`, which returns a `createConfigurationContext` for the
  chosen locale (or `base` when following the system).

`MainActivity.attachBaseContext` calls `LocalePreference.wrap`: the
context Compose hands to `stringResource` is already configured, so the
whole UI **and** the Toasts raised by the ViewModel (which receive the
same context) follow the choice. The Language menu saves the preference
and calls `Activity.recreate()`; **the board is not lost**, because the
`BoardViewModel` is retained by the activity's ViewModelStore exactly as
it is on a rotation.

**Why not `AppCompatDelegate.setApplicationLocales`.** The app has a
single `ComponentActivity` and no AppCompat theme or activity, and the
per-app language API is native only from API 33 while this app targets
`minSdk = 26`. Wrapping the base context behaves identically on every
supported release, adds no dependency, and keeps **a single source of
truth** (the preference). For the same reason `android:localeConfig`
was not added: it would create two places (system settings and the
in-app menu) deciding the same thing.

**If you add strings.** They go in *both* files; if one is missing from
`values-it`, the Italian UI silently falls back to English.
