# F-Droid Submission Checklist

> **Design document.** Describes the intended design and may run ahead of the current build (0.3.1). See `ROADMAP.md` for what has actually shipped.


Everything required for Kilomètre to be accepted into F-Droid's main repository. Based on F-Droid's Inclusion Policy and Inclusion How-To, current as of May 2026.

## Pre-submission checklist

### Source code

- [ ] Source code in a public git repository (GitHub primary, Codeberg mirror).
- [ ] Repository tracked in this project's `git remote`.
- [ ] Source kept up-to-date (no large gaps between repo state and released APK).
- [ ] All commits signed (`git commit -S`) — recommended, not required.

### Licensing

- [ ] AGPL-3.0 declared in `LICENSE` file at repo root.
- [ ] AGPL-3.0 header in source files (top-level Kotlin files at minimum).
- [ ] Every dependency's license verified compatible with AGPL-3.0.
- [ ] `LICENSE` file contains full AGPL-3.0 text (not just a pointer).

### Dependencies

F-Droid rejects:
- Proprietary libraries.
- Closed-source advertising SDKs.
- Anything pulled from a non-FOSS-friendly Maven repository.
- Some Google Play Services components.

Audit:
- [ ] All Maven dependencies enumerated in `gradle/libs.versions.toml`.
- [ ] Every dependency's source repository linked and verified open-source.
- [ ] `play-services-location` is the only Play Services dependency. Expect possible `NonFreeDep` anti-feature.
- [ ] No Firebase. No Crashlytics. No Analytics.
- [ ] No Google Maps. No Mapbox SDK.
- [ ] No JitPack-only dependencies (F-Droid does not consider JitPack builds reproducible).

### App structure

- [ ] Unique Android package ID: `dev.melcodes.kilometre`. Not derived from any existing app's ID.
- [ ] Minimum SDK declared in `build.gradle.kts`.
- [ ] Target SDK declared in `build.gradle.kts`.
- [ ] App does NOT download additional executable binaries at runtime.
- [ ] App does NOT auto-update from a non-F-Droid source.

### Anti-features

Audit and disclose:
- [ ] `NonFreeNet` — does the app rely on a non-free network service? Open-Meteo and Overpass are open. SAFE.
- [ ] `NonFreeDep` — does the app depend on `play-services-location`? Probably YES. Disclose.
- [ ] `NonFreeAdd` — does the app advertise non-free add-ons? NO.
- [ ] `Tracking` — does the app track users? NO.
- [ ] `Ads` — does the app contain advertising? NO.
- [ ] `UpstreamNonFree` — is the upstream version different from F-Droid's build? NO (we ship the same).
- [ ] `NoSourceSince` — is current released source code missing? Should never be true if practice is followed.
- [ ] `KnownVuln` — is the app known to have an exploitable vulnerability? NO.
- [ ] `DisabledAlgorithm` — does the app use deprecated crypto? NO (SHA-256, AES-256, PBKDF2 all current).
- [ ] `NonFreeAssets` — does the app contain non-free media assets? NO (all icons / images created for this project under AGPL).

### Metadata (`fastlane/metadata/android/`)

Required structure:
```
fastlane/
└── metadata/
    └── android/
        ├── en-US/
        │   ├── title.txt
        │   ├── short_description.txt
        │   ├── full_description.txt
        │   └── images/
        │       ├── icon.png
        │       ├── featureGraphic.png
        │       └── phoneScreenshots/
        │           ├── 1.png
        │           ├── 2.png
        │           └── ...
        └── fr-FR/
            ├── title.txt
            ├── short_description.txt
            ├── full_description.txt
            └── images/
                └── ...
```

- [ ] English title, short description (80 char max), full description.
- [ ] French equivalents.
- [ ] App icon at 512x512 PNG.
- [ ] Feature graphic at 1024x500 PNG.
- [ ] At least 2 phone screenshots, in both languages.
- [ ] Changelog file per release in `changelogs/{versionCode}.txt`.

### Build configuration

- [ ] Builds with `./gradlew assembleRelease` cleanly on a fresh machine.
- [ ] No required environment variables, secret keys, or external files beyond what's in the repo.
- [ ] Versions of Gradle, AGP, Kotlin all reproducible (committed to repo via wrapper + `libs.versions.toml`).
- [ ] APK signed with a stable key (NOT the debug key).
- [ ] ProGuard / R8 rules committed if used.

### Reproducible builds (strongly recommended, not required)

- [ ] APK built from source matches the published APK byte-for-byte.
- [ ] Build environment documented (JDK version, Gradle version, OS).
- [ ] No timestamps embedded in resources (configure `androidResources.noCompress` accordingly).
- [ ] Tested by building twice on different machines and comparing.

Reproducible builds are a one-shot decision: once an app is on F-Droid with a non-reproducible signing key, switching to reproducible builds requires users to reinstall. Set this up as early as possible.

### Tagging and versions

- [ ] Each release is a git tag of the form `v0.1.0`, `v0.2.0`, etc.
- [ ] `versionCode` and `versionName` in `build.gradle.kts` match the tag.
- [ ] CHANGELOG.md at repo root documents user-visible changes per version.

### Documentation

- [ ] README explains what the app does and what it doesn't.
- [ ] PRIVACY.md describes data handling.
- [ ] CONTRIBUTING.md exists (can be sparse early on).
- [ ] All user-visible strings translated to French in `values-fr/strings.xml`.

## Submission process

Once the checklist passes:

1. Fork `https://gitlab.com/fdroid/fdroiddata`.
2. Add a metadata YAML file at `metadata/dev.melcodes.kilometre.yml` describing the app and how to build it.
3. Open a merge request against `fdroiddata`.
4. Address review feedback. Common requests:
   - Clarify anti-feature flags.
   - Pin dependency versions more strictly.
   - Add missing license headers.
5. Once merged, F-Droid's build server builds the app from source.
6. If the build succeeds, the app appears in F-Droid client within hours to days.

Reference: `https://f-droid.org/en/docs/Inclusion_How-To/`.

## Common rejection reasons (forewarned)

Based on F-Droid's policy documentation and observed practice:

- **A new dependency was added without source verification.** Solution: audit every dependency.
- **Build fails on F-Droid's build server but works locally.** Solution: use the exact JDK and Gradle versions F-Droid uses, documented in their build environment specs.
- **App requires a network service that isn't open-source.** Solution: Open-Meteo and Overpass are both open. Safe.
- **Anti-features not disclosed.** Solution: this checklist.
- **License header missing or wrong.** Solution: pre-commit hook checks for AGPL headers.

## Notes on "Keep Android Open" context

In August 2025, Google announced a Developer Verification program requiring all Android app developers (even those distributing outside Play) to register with Google by September 2026. This affects sideloading and could impact F-Droid distribution.

F-Droid's "Keep Android Open" campaign opposes this. The project should:

- Follow the campaign's status.
- Prepare for potential need to register as a developer if the policy takes effect.
- Be ready to use F-Droid's alternative distribution mechanisms.

This is a moving target. Re-check status closer to v1.0 release.
