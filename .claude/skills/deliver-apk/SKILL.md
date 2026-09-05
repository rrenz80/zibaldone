---
name: deliver-apk
description: Build and deliver a new APK version of Zibaldone (the moodboard app) following the project's strict delivery protocol (PROGETTO.md §9.3). Use whenever the user asks to build, deliver, ship, or release a new version/APK of the app — not for a plain dev/debug build with no delivery intent.
---

Follow every step below, in order, for any request to build/deliver/ship a version of this app. Do not skip steps or substitute a plain `assembleDebug` — the debug signature is identical across builds, so skipping the version bump means the app on the tablet silently fails to update even though the file looks new.

Steps 1–5 produce the build; steps 6–7 record it in git and publish it. Every delivered version gets exactly one commit, one tag and one GitHub release carrying the APK, so `versionCode`, the PROGETTO.md changelog, the git history and the downloadable installable never drift apart.

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

6. **Commit and tag — after asking.** Stop and ask the user before running any of this; their approval of the release itself is not approval to publish it. The repo is `rrenz80/zibaldone` (private, remote `origin`, branch `main`). Do this only after the build succeeded and the dex check passed — a tag must always point at a version that actually built.

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

7. **Publish the GitHub release with the APK attached — after asking.** The tag alone
   carries the source; the release is what makes the installable downloadable. Ask
   before publishing: a release is visible work, not a local step.

   ```bash
   gh release create v<versionName> ~/Zibaldone-v<versionName>.apk \
     -R rrenz80/zibaldone \
     -t "v<versionName> — <titolo breve>" \
     -F <file-con-le-note>
   ```

   Release notes in **Italian**, written for someone installing on the tablet, not for
   a developer: what changed, and what to check. Reuse the manual test procedure from
   step 8 rather than writing it twice. Pass them via `-F` from a file (a scratchpad
   file is fine) — heredocs through `-n` mangle multi-line text.

   Then verify the asset actually uploaded, rather than trusting the command:

   ```bash
   gh release view v<versionName> -R rrenz80/zibaldone --json assets \
     --jq '.assets[] | "\(.name) — \(.size) byte"'
   ```

   The repo is **private**, so the download link asks for a GitHub login. That is fine
   on a device already signed in; if the user needs a link that just works, serve the
   APK over Tailscale instead (bind to the tailnet IP only, never `0.0.0.0`).

8. **Deliver in Italian.** Reply to the user with a delivery message in Italian describing the manual test procedure for verifying the fix/feature on-device. Give the release link and the tag.
