# Roadmap

Where Kilomètre is and where it's going. The current public release is **0.3.1**
(the public-beta line opened at 0.3.0), a beta — the app records and reviews drives but cannot yet sign them. `1.0.0` is
reserved for the point where the livret is genuinely trustworthy: signing,
integrity, and export all in place. It is a milestone, not a number to drift into.

## Done

**Phase 1 — Core session engine.** Room persistence, a foreground GPS service
sampling at 1 Hz, the session lifecycle (start, auto-pause, manual pause/resume,
auto-stop), cold-boot recovery, and first-launch onboarding.

**Phase 2 — Map and viewing (current — public-beta line, 0.3.0 → 0.3.1).** Session detail screen with a
MapLibre route map (direction-of-travel gradient), an elevation/speed chart,
drive replay with a moving dot and camera follow, a sessions list with route
thumbnails, a progress tracker, GPX export, and a full Settings hub. Recent
subjects: AAC milestone reminders (rendez-vous pédagogique at 1000 and 3000 km),
and an in-app update check / "what's new" / source-code link.

## Next

**Phase 3 — Signature and integrity.** Capture the accompagnateur's signature on
the device at the end of a session, and chain a tamper-evident hash across
signed sessions so the record is verifiably intact. This is the work that earns
the 1.0 milestone.

**Phase 4 — Enrichment.** After a session is signed, fetch weather (Open-Meteo)
and classify road types (Overpass) in the background, and compute day/night from
sun position. All keyless and optional; enrichment is informational and sits
outside the hash chain.

**Phase 5 — Dashboard polish.** Aggregate stats, night/road-type breakdowns, and
progress visualisations across the whole accompanied period.

**Phase 6 — Backup, export, and distribution.** Encrypted backups, a PDF livret
export, and an F-Droid submission.

## Out of scope

A cloud account, a server, telemetry, or any background upload. Data stays on the
device; the only network calls are the optional, user-initiated update check and
the post-signing enrichment lookups above.
