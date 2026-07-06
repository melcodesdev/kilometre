package dev.melcodes.kilometre.domain

// The AAC rendez-vous-pédagogique milestones, as pure logic.
//
// French conduite accompagnée has two km-relevant rendez-vous pédagogiques:
// the first around 1000 km (4–6 months into the accompanied phase) and the
// second at the 3000 km minimum, at the end. There is deliberately NO 2000 km
// milestone — no real RDV happens there (verified against the AAC rules).
//
// This object is pure Kotlin with no Android imports so it can be unit-tested
// on the JVM, like SessionLifecycle. It owns only the "where am I in the
// ladder" arithmetic; posting notifications and persisting state live in the
// Android layers (LocationService, AppContainer).
object AacMilestones {

    // The km marks that each carry a real rendez-vous pédagogique, ascending.
    val LADDER: List<Int> = listOf(1000, 3000)

    // The target a fresh AAC driver starts on.
    val FIRST: Int = LADDER.first()

    // The lowest milestone the driver hasn't yet driven past — i.e. the goal
    // to set when AAC mode is (re)enabled. Once total is at or beyond the last
    // milestone, the last one stays the target (its final RDV is then due).
    // Uses a strict `>` so being exactly at a milestone counts it as reached.
    fun firstUnreachedMilestone(totalKm: Double): Int =
        LADDER.firstOrNull { it > totalKm } ?: LADDER.last()

    // The next target after the driver acknowledges the RDV at `goal`, or null
    // when `goal` is the final milestone (the AAC journey is complete). A goal
    // not on the ladder (e.g. a custom simple-mode goal) also returns null.
    fun nextGoalAfter(goal: Int): Int? {
        val i = LADDER.indexOf(goal)
        return if (i == -1 || i == LADDER.lastIndex) null else LADDER[i + 1]
    }

    // True when reaching `totalKm` means the RDV reminder for `goal` is due and
    // hasn't been posted yet. `notifiedKm` is the milestone we last notified for
    // (0 = none); `complete` is set once the final RDV has been acknowledged.
    fun isRdvDue(totalKm: Double, goal: Int, notifiedKm: Int, complete: Boolean): Boolean =
        !complete && totalKm >= goal && notifiedKm != goal

    // Whether `goal` is the last milestone — used to pick the "final RDV"
    // wording over the "first RDV" wording in the notification and the card.
    fun isFinalMilestone(goal: Int): Boolean = goal == LADDER.last()
}
