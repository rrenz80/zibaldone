# Maintaining Zibaldone

How this app gets built and shipped from the maintainer's machine to the
tablet it is written for. **None of this is needed to read the code or to
build the project** — [`README.md`](../README.md) covers building, and
[`PROJECT.md`](../PROJECT.md) is the engineering log: architecture, the
version history with the root cause of every bug, and the device findings
that shaped the rendering.

What is here is operational and specific: paths under a particular `~/`,
a delivery protocol with a rule about `versionCode` that was learned the
hard way, and the ways an APK reaches a tablet. It lives in the
repository because the delivery has to be repeatable and the
`deliver-apk` skill relies on it — not because a visitor needs it.

---

## 1. Environment (everything under `~/`, no sudo)
| Component | Path | Version |
|---|---|---|
| JDK | `~/tools/jdk-17.0.20.1+1` (also `~/java/jdk-17.0.20.1+1`) | Temurin 17.0.20 |
| Android SDK | `~/android-sdk` | platform-34, build-tools 34.0.0 (licences accepted) |
| Gradle | wrapper **8.6** in the project | 8.6 |

## 2. Delivery protocol (applied to every version)
1. `app/build.gradle.kts`: **`versionCode` +1 and `versionName` → a new
   label** (mandatory: a file with the same name on the tablet does not
   pick up the new dex — in v1.0 the app "looked identical" for exactly
   this reason).
2. Build (PROJECT.md §9.1).
3. `cp app-debug.apk ~/Zibaldone-v<name>.apk` and **remove the previous
   APK**: exactly **one** installable must exist under `~/`.
4. **Dex verification**: `unzip` every `classes*.dex`, then
   `strings | grep -c <new-symbol>` on each one (D8 spreads code over
   N buckets).
5. Append a section to PROJECT.md §11 with symptoms/root cause/fix.
6. **Commit + tag** (since v1.9, when the project moved to GitHub —
   `rrenz80/zibaldone`): `git add -A`, check with
   `git status --short` that no build output or `local.properties`
   sneaks in, commit with the subject `v<versionName>: <what changes>`,
   then an **annotated** tag `git tag -a v<versionName>` and
   `git push origin main --follow-tags`. The tag is created **only
   after** the build and the dex check have passed: it must point at a
   version that actually compiles. If the tag already exists the version
   was not bumped → go back to step 1; never move or force-push a tag
   that has already been published.
7. **GitHub release** with the APK attached: `gh release create
   v<versionName> ~/Zibaldone-v<versionName>.apk -R rrenz80/zibaldone
   -t "..." -F <notes>`, notes written for whoever installs it. Verify
   the asset really uploaded (`gh release view --json assets`).
8. Delivery message with the test procedure.

> Language note: through v1.10 commit messages, release notes and the
> delivery message were written in Italian (the maintainer's language,
> matching the git history). From v1.10 on, everything written *into the
> repository* — this file, the README, commit messages, release notes —
> is in English, so the project reads as one piece for an international
> audience.

## 3. Delivery files

**Where a delivered APK lives.** In the **GitHub release** for its tag —
that is the durable copy, and since v1.10 the only one kept. The
`~/Zibaldone-v<name>.apk` that step 3 of the protocol produces is a
staging artifact for the upload: once `gh release view --json assets`
confirms it landed, it can be deleted. The single-APK rule under `~/`
still holds while it is there, so two versions can never be confused for
one another.

Verify before deleting a local copy, not after: download the asset with
`gh release download v<name>` and `cmp` it against the local file. Done
for v1.10 — the release asset is byte-for-byte the built APK, SHA-256
`82c18bed…a33d4eaa`.

**Getting a build onto the tablet without GitHub** (a release link asks
for a login while the repository is private, and a tablet is a poor place
to sign in):

```bash
mkdir -p ~/apk-serve                       # index.html + a hard link to the APK
python3 -m http.server 8299 --bind <tailnet-ip> --directory ~/apk-serve
```

Bound to the Tailscale address **only**, never `0.0.0.0`, so it is not
exposed to the LAN. Port 8299 is the one used for this in the past. It
is a temporary arrangement: close it when the download is done
(`kill` the process; there is no `tailscale serve` config to unwind) and
remove `~/apk-serve/`, or a stale APK stays reachable on the tailnet.

**Installing**: copy the APK to the tablet, tap it, `Accept` the
"install unknown source" prompt. Debug builds all share the debug
signature, so a newer one overwrites the older install — but a
release-signed APK does not (PROJECT.md §9.2).
