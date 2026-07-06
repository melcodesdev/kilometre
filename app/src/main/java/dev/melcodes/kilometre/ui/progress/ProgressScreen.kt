package dev.melcodes.kilometre.ui.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import dev.melcodes.kilometre.domain.AacMilestones
import kotlinx.coroutines.launch

// Fallback goal used only before the driver row has loaded (the first
// frame). The real goal is the driver's stored kmGoal, set at onboarding.
// In v0.1 both are 3000 — France's AAC minimum before the exam — but
// reading the stored value is what lets a future scheme picker change it.
private const val DEFAULT_GOAL_KM = 3000

// Progress tab. Shows total accompanied distance against the driver's
// km goal as a big circular ring with the percentage in the middle.
// Observes SessionRepository.totalDistanceMeters(driverId = 1L), so the
// total climbs live as sessions complete.
//
// When AAC mode is on and the total reaches the current goal, an RDV card
// appears above the ring (the reliable acknowledgement path — the milestone
// notification is just the alert). "J'ai compris" advances the goal to the
// next milestone, or marks the AAC journey complete on the final one.
@Composable
fun ProgressScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val scope = rememberCoroutineScope()
    val totalMeters by container.sessionRepository.totalDistanceMeters(1L)
        .collectAsStateWithLifecycle(initialValue = 0.0)
    val driver by container.database.driverDao().observeDriver()
        .collectAsStateWithLifecycle(initialValue = null)
    val aacModeEnabled by container.aacModeEnabled.collectAsStateWithLifecycle(initialValue = false)
    val aacComplete by container.aacComplete.collectAsStateWithLifecycle(initialValue = false)

    val goalKm = driver?.kmGoal ?: DEFAULT_GOAL_KM
    val totalKm = totalMeters / 1000.0
    // coerceIn keeps the bar within 0..1 even if the driver overshoots
    // the goal; toFloat() because CircularProgressIndicator wants a Float.
    val fraction = (totalKm / goalKm).coerceIn(0.0, 1.0).toFloat()
    val remainingKm = (goalKm - totalKm).coerceAtLeast(0.0)

    // The RDV reminder is "pending" once the total reaches the current goal,
    // while AAC mode is on and the journey isn't already marked complete.
    val rdvPending = aacModeEnabled && !aacComplete && totalKm >= goalKm

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (rdvPending) {
            RdvCard(
                isFinal = AacMilestones.isFinalMilestone(goalKm),
                goalKm = goalKm,
                onAcknowledge = { scope.launch { container.acknowledgeRdvMilestone() } },
            )
        }

        // The ring fills the rest of the screen and stays centred whether or
        // not the card is present.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Box stacks the ring and the centred text on top of each other.
                // The km total is the headline — it's the number the driver
                // actually cares about during the year. The % is secondary
                // context, not the hero, so it moves below the ring.
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.size(220.dp),
                        strokeWidth = 14.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.progress_goal_km, totalKm),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(R.string.today_km_unit),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                androidx.compose.foundation.layout.Spacer(Modifier.size(24.dp))

                Text(
                    text = stringResource(R.string.progress_goal, totalKm, goalKm),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                androidx.compose.foundation.layout.Spacer(Modifier.size(4.dp))
                Text(
                    text = stringResource(
                        R.string.progress_percent_remaining,
                        (fraction * 100).toInt(),
                        remainingKm,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

// The rendez-vous-pédagogique prompt shown on the Progress tab when a milestone
// is reached. Wording differs for the final (3000 km) milestone, which closes
// the AAC journey rather than opening the next leg. "J'ai compris" advances the
// goal (or completes the journey) via AppContainer.acknowledgeRdvMilestone.
@Composable
private fun RdvCard(isFinal: Boolean, goalKm: Int, onAcknowledge: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.progress_rdv_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
            Text(
                text = if (isFinal) {
                    stringResource(R.string.progress_rdv_body_final)
                } else {
                    stringResource(R.string.progress_rdv_body_first, goalKm)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            androidx.compose.foundation.layout.Spacer(Modifier.size(16.dp))
            Button(
                onClick = onAcknowledge,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.progress_rdv_ack))
            }
        }
    }
}
