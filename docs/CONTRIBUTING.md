# Contributing

**Status:** Until v1.0 ships, Kilomètre is a solo project. Contributions are welcome as ideas, bug reports, and discussion, but pull requests are not being merged. The reason is that the architecture is still being shaped and arbitrary changes would create churn.

After v1.0 ships, this file becomes the active contribution guide.

## Before contributing

1. Read `README.md` and `docs/ARCHITECTURE.md` as well as `docs/FEATURES.md`. The project has strong opinions; understand them before suggesting changes.
2. Search existing issues. Your idea may already be discussed.

## Kinds of contribution

### Bug reports

Always welcome. Open an issue with:
- Android version + OEM (e.g. Pixel 8, Galaxy S23).
- App version.
- Steps to reproduce.
- Expected vs actual behavior.
- Logs from the in-app crash log share, if relevant.

### Feature requests

Open an issue. Before opening, check whether the request fits the project's principles in `README.md` ("What it is not") as well as checking if it doesn't already exist in `docs/FEATURES.md`. Features that compromise local-only data, add tracking, or require account systems are out of scope by design and will not be added.

### Translations

Translations into additional languages are very welcome. To contribute:
1. Fork the repo.
2. Create `app/src/main/res/values-<locale>/strings.xml`.
3. Translate the strings. Keep the same key names; only change the values.
4. Open a pull request titled `[i18n] Add <language> translation`.

Native speakers preferred for accuracy. Machine translation is OK as a starting draft but should be reviewed by a fluent speaker before submission.

### Code changes

For non-trivial changes, open an issue first to discuss the approach. This avoids spending hours on a PR that conflicts with planned architecture.

For trivial changes (typos, dependency version bumps, lint fixes), a direct PR is fine.

## Pull request process

1. **Fork** the repo.
2. **Branch** from `main`: `git checkout -b feat/<short-description>`.
3. **Implement** the change. Match existing style.
4. **Test** locally:
   - `./gradlew test` (unit tests).
   - `./gradlew connectedAndroidTest` (instrumented, requires emulator or device).
   - Manual smoke test if the change touches UI or services.
5. **Commit** with `[scope] subject` format. Multiple commits OK; squash if they're work-in-progress noise.
6. **Push** to your fork.
7. **Open a PR** targeting `main`. Reference any related issue.
8. **CI** runs (post-v1.0; not yet wired up). Pass before review.
9. **Review.** The maintainer (melcodes) will read it within roughly a week. Patient is appreciated.
10. **Iterate** if changes are requested.
11. **Merge.** The maintainer merges. Squash-merge is the default to keep history clean.

## Code style

### Kotlin

- Standard Kotlin style guide (`https://kotlinlang.org/docs/coding-conventions.html`).
- `ktlint` enforced (Gradle task). Run `./gradlew ktlintFormat` before pushing.
- Prefer expression bodies for one-line functions: `fun double(x: Int) = x * 2`.
- Prefer immutability: `val` over `var`, `List` over `MutableList` in APIs.
- Use trailing commas in multi-line lists.
- Use string templates: `"Hello, $name"` not `"Hello, " + name`.

### Compose

- Composable function names are PascalCase: `TodayScreen`, not `todayScreen`.
- Composables that emit UI take a `Modifier` parameter, defaulting to `Modifier`.
- State is hoisted: composables receive state and callbacks, not their own ViewModels (with rare exceptions for screen-level composables).
- Avoid putting business logic inside composables. Put it in ViewModels or domain code.

### Comments and documentation

- KDoc on every public class and function.
- Inline comments for non-obvious decisions (especially Android quirks).
- No comments that just restate the code: `// increment counter` above `counter++` is noise.

### Commit messages

```
[scope] short subject line, 50 chars max

Optional longer body explaining why. Wrapped at 72 chars.
Reference issues with #N.
```

Scopes: `session`, `db`, `ui`, `signature`, `integrity`, `map`, `export`, `i18n`, `build`, `docs`, `meta`.

## License agreement

By contributing, you agree that your contribution is licensed under AGPL-3.0 — the same license as the rest of the project. You retain copyright on your contribution.

We don't require a CLA (Contributor License Agreement). Just inherit the project license.

## Code of conduct

Be respectful. Disagreement is welcome; personal attacks are not.

Specific things that aren't welcome:
- Pressure to make the project something it isn't (e.g., "you should add cloud sync"). The local-only, no-account, no-tracking principles are not up for debate.
- Demanding free support for personal use cases.
- Off-topic political or ideological arguments.
- Discriminatory language.

The maintainer reserves the right to close issues, lock discussions, or block users for cause.

## Recognition

Contributors are listed in a `CONTRIBUTORS.md` file at the repo root (post-v1.0). To opt out, say so in your PR.

## Maintainer availability

This is a hobby project maintained in spare time, not a service. Response times
vary from a few days to a couple of weeks, and may be longer during busy
periods. Setting realistic expectations protects both sides.

## Things you can't contribute

- Cloud sync, account systems, telemetry, advertising, analytics. Out of scope by principle.
- Migration to a different license. AGPL-3.0 is final.
- Migration to closed-source dependencies. None will be accepted.
- Migration to a different ecosystem (KMP, Flutter, React Native). Out of scope at least until v2.0.
- Changes to the package ID. It is set in stone for F-Droid compatibility.

## Recovery if the maintainer goes inactive

If melcodes is unresponsive for more than 6 months and the project clearly needs a maintainer:

1. Open an issue titled "Project status check".
2. If no response within a further 30 days, anyone may fork under a different name and continue the project under AGPL-3.0.
3. The original repo's README should be updated to point to the fork, if the maintainer ever returns.

This is the FOSS social contract. The license allows anyone to continue the work if the original author can't.
