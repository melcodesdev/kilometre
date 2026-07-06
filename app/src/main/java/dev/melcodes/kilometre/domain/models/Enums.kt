package dev.melcodes.kilometre.domain.models

// Driving schemes recognised in v0.1. Only AAC is reachable from the
// onboarding UI (AAC is fixed for now). The other variants
// stay in the enum so a future onboarding revision can light them up
// without a schema migration.
enum class DrivingScheme {
    AAC,
    SUPERVISEE,
    GENERIC,
}

// The lifecycle state of a driving session. Phase 1 only writes ACTIVE
// and DRAFT — SIGNED arrives in Phase 3 with the signature canvas, and
// DISCARDED arrives whenever the user gains a way to mark a session
// as not counting (Phase 5+).
enum class SessionState {
    ACTIVE,
    DRAFT,
    SIGNED,
    DISCARDED,
}
