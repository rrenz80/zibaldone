# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

**Zibaldone** — a single-module Android moodboard app (Kotlin + Jetpack Compose, Material3), package `it.zibaldone.app`. No other modules, no backend. The app was renamed from "Moodboard" in v1.9; since v1.10 the UI ships in **Italian and English**, picked in-app. The authoritative project log — architecture decisions, version history, known limitations — lives in `PROJECT.md` at the repo root. Everything written into this repo — that log, the README, code comments/KDoc, commit messages, release notes — is in **English**, so the project reads as one piece for an international audience. (The git history before v1.10 and the release notes up to v1.9 are in Italian; they are left as they are.)

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

When asked to build/deliver/ship a version of the app, use the `deliver-apk` skill — it encodes the full release protocol from PROJECT.md §9.3 (version bump, single-APK rule, dex verification, changelog entry, release commit + annotated tag, test notes). Don't do a plain `assembleDebug` and call it delivered.

## Git

**Ask before every commit, push, tag or release — every time.** Approval for one git
operation never carries over to the next, not even later in the same session and not
even when the code change itself was approved. Finish the edit, show what changed, and
offer the commit as the next step instead of running it. Writing a git step into a
skill or protocol is not permission to execute it.

Repo `rrenz80/zibaldone` (private), remote `origin`, branch `main`. Commit messages are in English from v1.10 on (earlier history is Italian — don't rewrite it). Each delivered version is one commit plus one annotated tag `v<versionName>`, created by the `deliver-apk` skill — which still asks first. Never move or force-push a tag that has already been pushed.

## UI strings (i18n)

Every user-facing string lives in resources, never as a literal in Kotlin: `res/values/strings.xml` is **English** (default and fallback) and `res/values-it/strings.xml` is **Italian**. A new string goes in **both** files — a missing Italian entry silently falls back to English. Messages of internal exceptions stay as English literals: they never reach the user.

The language choice (System default / Italiano / English) is persisted by `util/AppLocale.kt` and applied in `MainActivity.attachBaseContext`; the picker is the globe menu in the top bar. Don't reach for `AppCompatDelegate.setApplicationLocales` or `android:localeConfig` — PROJECT.md §16 explains why this app can't use them.

## Critical rendering constraint

Camera state (pan/zoom, see `view/CameraState.kt`) must be read in **composable body scope** and applied via **layout** (`Modifier.offset {}` / `.size()`). Never read it inside a `DrawScope` draw closure or apply it via `graphicsLayer`. Both alternatives were tried in v1.1–v1.3 and failed to track camera updates on the target tablet (PROJECT.md §4.3, §14). This applies to any code touching `ui/BoardGesture.kt`, `ui/StrokeCanvas.kt`, `ui/ImageNodeOverlay.kt`, or `ui/TextNodeOverlay.kt`.

All pointer/gesture input is centralized in `ui/BoardGesture.kt` — there are no per-node gesture handlers. New interactive behavior belongs there, not scattered across overlay composables.

## Tests

There are no test sources in this repo (`app/src/test`, `app/src/androidTest` don't exist), despite JUnit/Espresso/Compose-UI-test being declared as dependencies. Don't assume a test suite exists or try to run one.

## Code style

- Standard Kotlin official style (4-space indent, no semicolons), but with heavier KDoc than typical: most public classes/functions carry a `/** ... */` block explaining *why*, not just *what* — especially for device-specific or historical decisions. Preserve this pattern in new code.
- No trailing commas in multi-line parameter/argument lists.
- Sealed classes + `@Serializable`/`@SerialName` for polymorphic models (see `model/BoardElement.kt`); pure utility functions go in a singleton `object` (see `util/CanvasMath.kt`), not top-level functions.

## Gotchas

- There is **no local database** and no annotation processor. Room and `kotlin-kapt` were declared but never used, and were removed in v1.9 — don't reintroduce kapt without need: it adds two stub-generation tasks to every compile.
- `.zib` export format (ZIP: `manifest.json` + `media/*.jpg`) has intentionally never changed across versions for backward compatibility — don't alter it without a strong reason, and document any change in PROJECT.md. Only the suggested extension changed in v1.9 (was `.mboard`); the archive layout is identical and old files still import, since the picker accepts `*/*`.
