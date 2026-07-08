# Integrity

> **Design document.** Describes the intended design and may run ahead of the current build (0.3.0). See `ROADMAP.md` for what has actually shipped.

Status: NOT yet built. No session is signed yet, no hash chain is computed, and the `EditLog` table plus the `Session` hash columns exist but are empty. This is the spec for when signing lands.

What "tamper-evident" means in Kilomètre, what it protects against, and what it explicitly does not.

## What we're protecting

The integrity of the driving record. Specifically:

- A reader of an exported backup (the user, a future auto-école, a parent reviewing their kid's drives) should be able to verify that the data is internally consistent.
- A signature should be cryptographically bound to the session it signs, so altering the session invalidates the signature.
- Sessions should form a chain so that inserting, deleting, or reordering sessions becomes detectable.

## What we're NOT protecting against

These are explicit non-goals. Pretending otherwise would be dishonest design.

- **The user editing their own data on their own device.** The user controls the device, the source code (AGPL), and the database file. They can modify anything. Our job is to make modifications visible, not to prevent them.
- **A determined attacker with root access.** Same as above plus they can patch the binary. We are not a forensic tool.
- **Spoofed GPS data.** Mock location apps exist. We could try to detect them, but a sophisticated user can defeat detection. We don't try.
- **Replaying a real drive's GPS data for a fake session.** Same — possible, detection is unreliable.
- **Forging a signature.** A drawn signature is not biometric. Someone can imitate it. The cryptographic binding only proves the signature pixels haven't been changed *since signing*; it doesn't prove who drew them.

## The mental model

Think of it as a paper ledger with sealed pages:

- Each session is a page.
- The page has the session details, the signature at the bottom, and a small number at the top that's computed from everything on the previous page.
- If anyone changes anything on a past page, the number on the next page won't match anymore, and the inconsistency is obvious to anyone reading the ledger.
- You can still physically erase a page, or tear one out — but the missing or modified page is visible.

That's the goal. Not unforgeable, but auditable.

## The chain

Each SIGNED session has three hashes:

```
prevHash      = previous SIGNED session's contentHash (or "GENESIS")
contentHash   = SHA-256(prevHash + "\n" + canonicalPayload(session))
signatureHash = SHA-256(contentHash + "\n" + signaturePngBytes + "\n" + signedAtIso)
```

The chain is verified by walking sessions in order of `signedAt`:

```
for each SIGNED session S, in signedAt order:
    expectedPrev = previous session's contentHash (or "GENESIS")
    if S.prevHash != expectedPrev:
        FAIL: chain order broken
    if SHA-256(S.prevHash + "\n" + canonicalPayload(S)) != S.contentHash:
        FAIL: session content modified
    if SHA-256(S.contentHash + "\n" + signaturePng(S) + "\n" + S.signedAt) != S.signatureHash:
        FAIL: signature or content modified
```

Any failure produces a warning banner in the UI and a notice in the export. The user is not blocked from using the app.

## What's in the canonical payload

The canonical payload is a deterministic string representation of the session. It must include every field that, if modified, should invalidate the hash.

```
v1|<id>|<driverId>|<accompagnateurId>|<startedAt>|<endedAt>|<pausedSeconds>
|<distanceMeters_e6>|<durationSeconds>|<startLat_e7>|<startLng_e7>
|<endLat_e7>|<endLng_e7>|<state>|<notes_b64>|<manualEntry>|<gpsPointsHash>|<metadataCore>
```

Where:
- `_e6` and `_e7` denote integer micro-units to avoid floating-point variation.
- `notes_b64` is base64-encoded UTF-8 (to avoid newline ambiguity in notes).
- `gpsPointsHash` is a separate hash of all GPS points for this session.
- `metadataCore` is the JSON metadata object, with only "core" fields (excluding fields added by background jobs after signing).

### Why GPS points aren't in `contentHash` directly

GPS points can number in the thousands per session. Hashing them all into the main payload makes the hash chain expensive to verify and bloats the export. Instead, all GPS points for a session are themselves hashed into a single `gpsPointsHash`, which IS in the canonical payload. Verifying the chain implies verifying every GPS point.

```
gpsPointsHash = SHA-256(
    p1_canonical + p2_canonical + ... + pn_canonical
)
```

Each point's canonical form:
```
<timestamp_ms>|<lat_e7>|<lng_e7>|<accuracyMm>|<speedMmps>
```

Adding, removing, or reordering points changes the hash.

### Why metadata is split

Some metadata is fetched AFTER signing (weather from Open-Meteo, road classification from Overpass). If those were in the hash, every background fetch would invalidate the chain.

The split:
- `metadataCore` — fields written at session creation or signing time. In the hash.
- `metadataEnrichment` — fields written by post-signing background jobs. NOT in the hash. Free to update.

The split is enforced in code: `Session.metadataCore` and `Session.metadataEnrichment` are two distinct fields in v0.x; the database stores both in a single `metadata` JSON column with a known top-level structure.

```json
{
  "core": {
    "tags": ["night_driving"],
    "userNotes": "..."
  },
  "enrichment": {
    "weather": {...},
    "roadTypes": {...},
    "dayNight": {...}
  }
}
```

## What happens when verification fails

The user is informed, not punished:

- App-level banner: "Session #42 appears modified outside the app."
- Session detail shows the specific failure: "Field 'distanceMeters' was modified after signing on 2026-07-15."
- Exports include a `verification.json` file with the failure details so any reader sees them.
- The user can choose to acknowledge the failure ("yes, I edited this on purpose"), which adds an entry to `EditLog` but does NOT recompute the hash. The original signature remains broken in the chain.

There is no "fix" button that recomputes hashes. That would defeat the point.

## Re-signing

If a user legitimately needs to edit a SIGNED session (e.g., they realized they forgot to record a 2 km segment), they:

1. Edit the session. State changes to DRAFT. Original `contentHash`, `signatureHash`, `prevHash` are moved to `metadata.previousHashes` (for audit).
2. Accompagnateur signs again (or the signature is reused with a fresh `signedAt`, depending on user preference).
3. New `contentHash`, `signatureHash`, `prevHash` are computed and stored.

The old hashes in metadata mean the export shows the editing history transparently.

## Why not just store everything in a git repo?

It would technically work — git is a Merkle tree, integrity is built in. But:
- Adding a git library to an Android app adds ~5 MB and complexity.
- The user interaction model is wrong (commits, branches).
- Recovery from a corrupted git store is uglier than from a corrupted SQLite DB.

A custom hash chain over SQLite gets the same integrity property at a fraction of the complexity.

## Why not a blockchain?

Because we don't need one. A blockchain solves the "trust without a central authority" problem. Here, the user IS the authority. They just want a record they can verify hasn't been tampered with. A simple hash chain in a SQLite database does this perfectly.

## Comparison to a paper livret

| Property | Paper livret | Kilomètre |
|---|---|---|
| Legal force | Yes (with auto-école stamp) | No |
| Tamper-evident | No (easily edited with whiteout) | Yes (hashes break on edit) |
| Cryptographic signature binding | No | Yes |
| Auditable export | No (it's the original) | Yes (machine-readable) |
| Required for permit exam | Yes | No |
| Survives loss | No (one paper copy) | Yes (backups) |

Kilomètre is strictly additive. The paper livret remains primary. Kilomètre offers backup and verifiability.

## Threat model summary

| Threat | Detection | Prevention |
|---|---|---|
| User edits a past session in the DB | Yes (broken hash) | No |
| User deletes a past session | Yes (broken chain) | No |
| User adds a fake session | Partial (no signature = DRAFT, doesn't count) | No |
| Phone storage corrupts a file | Yes (broken hash) | Backups |
| Backup file is modified in transit | Yes (broken hash) | Encryption |
| Encrypted backup passphrase is cracked | No | Strong passphrase + PBKDF2 |
| Mock GPS provider | No | None — out of scope |
| Cloud account compromise | N/A — no cloud | N/A |
