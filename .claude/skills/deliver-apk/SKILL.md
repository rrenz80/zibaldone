---
name: deliver-apk
description: Build and deliver a new APK version of Zibaldone (the moodboard app) following the project's strict delivery protocol (PROGETTO.md §9.3). Use whenever the user asks to build, deliver, ship, or release a new version/APK of the app — not for a plain dev/debug build with no delivery intent.
---

Follow every step below, in order, for any request to build/deliver/ship a version of this app. Do not skip steps or substitute a plain `assembleDebug` — the debug signature is identical across builds, so skipping the version bump means the app on the tablet silently fails to update even though the file looks new.

Steps 1–5 produce the release; step 6 records it in git. Every delivered version gets exactly one commit and one tag, so `versionCode`, the PROGETTO.md changelog and the git history never drift apart.

1. **Bump the version.** In `app/build.gradle.kts`, increment `versionCode` by 1 and give `versionName` a new label. Confirm what changed in this release before picking the label.

2. **Build.**
   ```bash
   cd ~/zibaldone
   export JAVA_HOME=$HOME/tools/jdk-17.0.20.1+1   # or $HOME/java/jdk-17.0.20.1+1
   export ANDROID_HOME=$HOME/android-sdk
   ./gradlew :app:clean :app:assembleDebug --console=plain --offline
   ```

3. **Install the APK and enforce the single-APK rule.** Copy the built `app-debug.apk` to `~/Zibaldone-v<name>.apk` (using the new `versionName`), then delete the previous APK under `~/`. Exactly one installable APK must exist under `~/` at a time.

4. **Verify the dex.** Unzip the new APK and run `strings | grep -c <new-symbol>` against **every** `classes*.dex` file inside it (D8 splits code across multiple dex buckets, so the new symbol may not land in `classes.dex` alone). Use a symbol name that's actually new in this change (a new method/class name introduced by the fix). Report the count found per dex file — don't just claim success without showing this.

5. **Update PROGETTO.md.** Append a new section documenting this version: symptoms (what was wrong / what changed), root cause, and fix. Match the style and Italian language of existing entries. Also update the version table (§11), the dex markers (§12), and the "file di consegna" line (§15).

6. **Commit and tag.** The repo is `rrenz80/zibaldone` (private, remote `origin`, branch `main`). Do this only after the build succeeded and the dex check passed — a tag must always point at a version that actually built.

   ```bash
   cd ~/zibaldone
   git status --short          # look before staging
   git add -A
   git status --short          # confirm nothing unwanted crept in
   ```

   Check that no build output, APK or `local.properties` is staged — `.gitignore`
   covers them, but confirm rather than assume. If unrelated work-in-progress is
   also modified, ask before sweeping it into the release commit.

   Commit message in **Italian** (matching the existing history and PROGETTO.md),
   subject line `v<versionName>: <cosa cambia>`, then a body explaining what changed
   and why — the same substance as the PROGETTO.md entry, condensed. End it with the
   attribution footer this session was given.

   Then an **annotated** tag on that commit and a push of both:

   ```bash
   git tag -a v<versionName> -m "v<versionName> (build <versionCode>): <riassunto>"
   git push origin main --follow-tags
   ```

   If the tag already exists, the version was never bumped — go back to step 1 rather
   than moving or force-pushing the tag. Never rewrite a tag that has been pushed.

7. **Deliver in Italian.** Reply to the user with a delivery message in Italian describing the manual test procedure for verifying the fix/feature on-device. Mention the tag you pushed.
