# Differentiators

What Kilomètre offers that existing AAC tracking apps don't, based on a hands-on review of Kopilote and Coach AAC.

## Positioning after the review

The two apps a French AAC learner actually encounters are:

- **Kopilote (ENPC-EDISER)** — the regulator-mandated digital livret since 1 January 2024. Gated behind the auto-école. Uploads identity, GPS, livret entries, and driving stats to ENPC servers, accessible to the school. Privacy charter is a generic e-commerce document that does not mention GPS or driving data. Play Store data-safety declares "no data collected" despite uploading GPS traces. Zero minor-specific handling. No in-app data export.
- **Coach AAC (COVEA)** — insurance-group-published, freely usable without an auto-école account. Requests `ACCESS_BACKGROUND_LOCATION` for opaque "automatic detection." CGU permits contractual reuse of processed data by COVEA's insurance partners. Zero minor-specific handling. No in-app data export. Mandatory phone number at signup with no stated purpose. Genuine privacy positives on the technical surface: zero Exodus trackers, raw GPS traces deleted after processing per the CGU.

The Mon APP'AAC and Conduire par MAIF references that appeared in earlier drafts of this document are dropped: Mon APP'AAC appears delisted or rebranded and no longer surfaces in Play Store searches; Conduire par MAIF was not reviewed in G1.

## Validated differentiators

### 1. Truly local data

Both Kopilote and Coach AAC upload driving data to remote servers. Kopilote uploads to ENPC-EDISER and exposes the data to the auto-école by design. Coach AAC uploads to COVEA Solutions Prevention with contractual reuse permitted by insurance partners. Neither discloses hosting location or processor names.

**Kilomètre stores nothing on a remote server. Not by default, not optionally. There is no server.**

### 2. Cryptographic integrity

Kopilote and Coach AAC produce GPS-derived logs. Neither exposes hashes, integrity verification, or tamper-evidence to the user. If the auto-école or a future skeptical reader wants to know whether the data has been edited, the answer is "trust the publisher's database."

**Kilomètre exports include the full hash chain. Any modification is visible to any reader. The user can prove integrity to themselves and to others.**

### 3. Open data formats and user-owned export

Kopilote offers no real export — its "copy" function duplicates a trajet inside the app. Coach AAC has no in-app data export at all. Both apps' RGPD portability rights are exercised by writing to a postal address or emailing a privacy mailbox, with a one-month response window.

**Kilomètre exports GPX, CSV, and JSON in-app, at any time, without asking anyone for permission. The schema is documented in `docs/DATA_MODEL.md`. Anyone can write tools that consume Kilomètre's exports. Users can migrate elsewhere whenever they want.**

### 4. Foreground-only location

Coach AAC requests `ACCESS_BACKGROUND_LOCATION` on first use, framed as enabling "automatic trajet detection" — a euphemism for persistent always-on GPS sampling, given the permission set the Exodus report shows. The mechanism is not disclosed.

**Kilomètre never requests `ACCESS_BACKGROUND_LOCATION`. Location is sampled only when the user has pressed START and a foreground service is active. Auto-detection is explicitly deferred to post-v1.0, and only with a clear in-app explanation of mechanism if it is ever added.**

### 5. Treats minors as minors

AAC is legally restricted to learners aged 15-17. Both Kopilote's privacy charter and Coach AAC's CGU contain zero specific provisions for minors — no parental consent flow, no age gate, no minor-specific data handling. The two mandated/default options for French teenage AAC students treat their data exactly like adult e-commerce customers' data.

**Kilomètre operates without an account, without identity collection, without a phone number, without uploading anything. The question of how to protect a minor's data does not arise because the app does not collect data from anyone.**

### 6. Honest road-type classification

Kopilote and Coach AAC surface trajet metadata at varying levels of detail — Kopilote labels trajets only by city name, Coach AAC's surface area was not fully observable in the review. Neither documents how road type is classified.

**Kilomètre will classify via the OpenStreetMap road network (Overpass API). The classification method is documented; the source data is open; the user can verify any session's classification themselves using the same public data.**

### 7. No subscription, no upsell, no insurance

Coach AAC is published by an insurance group whose CGU contractually permits reuse of driving data by insurance partners. Kopilote is bundled by auto-écoles as part of a paid driving-education ecosystem. Both products' business models depend on driving data flowing to commercial recipients.

**Kilomètre has no business model. It is AGPL-3.0 software. It costs nothing, ever, in any form.**

### 8. Bilingual at launch

French is primary; English is included. This makes the app accessible to bilingual families and non-French students in French driving schemes.

### 9. F-Droid first

Kopilote and Coach AAC distribute via Google Play. Kilomètre's primary distribution channel is F-Droid, with GitHub APK releases for testers.

## What competitors do well that's worth copying

The review surfaced concrete UX wins that Kilomètre should learn from rather than reject reflexively:

- **Surface the OS settings that kill foreground services.** Coach AAC tells the user at trajet start which Samsung battery / background restrictions to disable. This is the single most important UX touch for any Android GPS-tracking app. Kilomètre needs this for the G4 Samsung verification gate and should copy the framing directly.
- **Short post-drive conditions check.** Both Kopilote and Coach AAC prompt a brief reflective questionnaire after a session (road type, conditions, difficulty). The fact that two completely different products independently arrived at this UX moment is a signal it's right.
- **Default the date and times sensibly on manual entry.** Kopilote getting this wrong is a recurring 5-second friction every single use. Kilomètre defaults: start time = now, end time = open until session ends, date = today.
- **A dashboard view of accumulated km is table-stakes.** Already in scope (the "Progress" tab).

## What competitors do that Kilomètre must do better (or do at all)

- **Manual entry with usable defaults.** Kopilote has manual entry but it's friction-heavy and the date/time defaults are wrong. Coach AAC has no manual entry at all, so a single forgotten launch creates a permanent gap. Kilomètre must support manual entry with sensible defaults.
- **Inline edit of a recorded session.** Kopilote requires walking through the entire creation flow to edit any field. Kilomètre's session detail screen should support direct field edits.
- **Real data export.** Both apps fail this completely. This is the single sharpest functional differentiator Kilomètre has.

## What competitors probably do better

Honest assessment, validated by the review:

- **Polish.** Coach AAC's onboarding and explanations are cleaner than what a solo unpaid teenager's first Android app will produce. Kilomètre will look more utilitarian.
- **Integration with auto-écoles.** Kopilote is mandated and pre-integrated with every French auto-école's workflow. Kilomètre is intentionally standalone. For users whose auto-école requires Kopilote (which is all of them since January 2024), Kilomètre is a parallel personal record, not a replacement.
- **Marketing visibility.** Kopilote and Coach AAC are visible in app stores, sponsored content, partnerships. Kilomètre is discovered via F-Droid or word of mouth.

## What "differentiated" looks like in practice

A French AAC student in 2026 actually faces this choice:

- **They must use Kopilote** — the auto-école enrolled them; the digital livret is regulator-mandated since January 2024. There is no opt-out at the legal-record level.
- **They may additionally use Coach AAC** — if they want a free standalone tracker with a cleaner UX than Kopilote's and don't mind background location going to an insurance group.
- **They may additionally use Kilomètre** — if they want a personal record on their phone that nobody else can read, with a verifiable export, and they don't mind a rougher UX.

Kilomètre is a parallel personal record, not a Kopilote replacement. That framing is what makes the project honest: it does not claim to solve the regulatory data-flow problem (it can't), it solves the personal-record problem for users who care about owning their own driving data.

The target user is the third bullet. That user does exist. They are not the majority of AAC students. They are enough.

## Killer features to commit to building

Based on the validated positioning, the four features that justify Kilomètre's existence are:

1. **Local-only data** — already in the architecture. No additional work needed; just don't add cloud later.
2. **Tamper-evident hash chain with auditable export** — significant engineering work (`docs/INTEGRITY.md`). Differentiator only if it actually works and is visible in the UI.
3. **Open data formats with documented schema and in-app export** — engineering work for the exporters, documentation work for the schema. Differentiator only if the documentation actually helps third parties consume the data.
4. **Manual entry as a first-class flow, with sensible defaults and inline edit.** Promoted from "feature" to "killer feature" by the review. Coach AAC's absence of this and Kopilote's hostile implementation of it are the most visible day-one frustrations for real users.

These four together are the value proposition. Everything else is a feature.

## Anti-features (things to deliberately not build)

To stay differentiated, we deliberately reject:

- Any optional cloud sync, even with end-to-end encryption.
- Any account system, even free.
- Any phone-number collection.
- Any `ACCESS_BACKGROUND_LOCATION` usage. Auto-detection is post-v1.0 and only with a clear explanation of mechanism.
- Any partnership with an insurance company.
- Any partnership with an auto-école software vendor.
- Any monetization scheme.
- Any "AI-powered" driving analysis. (The user doesn't need a robot judging their driving.)
- Any social feature. (No leaderboards, no sharing.)
- Any push notification beyond optional milestone notifications.
- Telemetry of any kind.
