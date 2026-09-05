# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Zibaldone** — a single-module Android moodboard app (Kotlin + Jetpack Compose, Material3), package `it.zibaldone.app`. No other modules, no backend. The app was renamed from "Moodboard" in v1.9; the UI is in Italian. The authoritative project log — architecture decisions, version history, known limitations — lives in `PROGETTO.md` at the repo root and is written in Italian; source comments/KDoc are in English.

## Build

This machine's JDK is not on a system PATH — set it explicitly:

```bash
export JAVA_HOME=$HOME/tools/jdk-17.0.20.1+1   # or $HOME/java/jdk-17.0.20.1+1
export ANDROID_HOME=$HOME/android-sdk
./gradlew :app:clean :app:assembleDebug --console=plain --offline
```

The `--offline` flag is intentional — dependencies are expected to already be cached locally.

## Linting

ktlint is configured via the `org.jlleitschuh.gradle.ktlint` Gradle plugin. Run `./gradlew :app:ktlintCheck` before considering Kotlin changes done, and `./gradlew :app:ktlintFormat` to auto-fix what's correctable. Project deviations from ktlint defaults live in `.editorconfig`: no trailing commas (matches existing code), and `@Composable` functions are exempt from the lowercase-function-name rule (PascalCase is the Compose convention).

## Delivering a build

When asked to build/deliver/ship a version of the app, use the `deliver-apk` skill — it encodes the full release protocol from PROGETTO.md §9.3 (version bump, single-APK rule, dex verification, changelog entry, release commit + annotated tag, Italian test notes). Don't do a plain `assembleDebug` and call it delivered.

## Git

**Ask before every commit, push, tag or release — every time.** Approval for one git
operation never carries over to the next, not even later in the same session and not
even when the code change itself was approved. Finish the edit, show what changed, and
offer the commit as the next step instead of running it. Writing a git step into a
skill or protocol is not permission to execute it.

Repo `rrenz80/zibaldone` (private), remote `origin`, branch `main`. Commit messages are in Italian, matching PROGETTO.md and the existing history; code comments stay English. Each delivered version is one commit plus one annotated tag `v<versionName>`, created by the `deliver-apk` skill — which still asks first. Never move or force-push a tag that has already been pushed.

## Critical rendering constraint

Camera state (pan/zoom, see `view/CameraState.kt`) must be read in **composable body scope** and applied via **layout** (`Modifier.offset {}` / `.size()`). Never read it inside a `DrawScope` draw closure or apply it via `graphicsLayer`. Both alternatives were tried in v1.1–v1.3 and failed to track camera updates on the target tablet (PROGETTO.md §4.3, §14). This applies to any code touching `ui/BoardGesture.kt`, `ui/StrokeCanvas.kt`, `ui/ImageNodeOverlay.kt`, or `ui/TextNodeOverlay.kt`.

All pointer/gesture input is centralized in `ui/BoardGesture.kt` — there are no per-node gesture handlers. New interactive behavior belongs there, not scattered across overlay composables.

## Tests

There are no test sources in this repo (`app/src/test`, `app/src/androidTest` don't exist), despite JUnit/Espresso/Compose-UI-test being declared as dependencies. Don't assume a test suite exists or try to run one.

## Code style

- Standard Kotlin official style (4-space indent, no semicolons), but with heavier KDoc than typical: most public classes/functions carry a `/** ... */` block explaining *why*, not just *what* — especially for device-specific or historical decisions. Preserve this pattern in new code.
- No trailing commas in multi-line parameter/argument lists.
- Sealed classes + `@Serializable`/`@SerialName` for polymorphic models (see `model/BoardElement.kt`); pure utility functions go in a singleton `object` (see `util/CanvasMath.kt`), not top-level functions.

## Gotchas

- There is **no local database** and no annotation processor. Room and `kotlin-kapt` were declared but never used, and were removed in v1.9 — don't reintroduce kapt without need: it adds two stub-generation tasks to every compile.
- `.zib` export format (ZIP: `manifest.json` + `media/*.jpg`) has intentionally never changed across versions for backward compatibility — don't alter it without a strong reason, and document any change in PROGETTO.md. Only the suggested extension changed in v1.9 (was `.mboard`); the archive layout is identical and old files still import, since the picker accepts `*/*`.
