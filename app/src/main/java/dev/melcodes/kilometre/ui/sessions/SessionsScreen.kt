package dev.melcodes.kilometre.ui.sessions

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.melcodes.kilometre.DEFAULT_GRADIENT_END_HEX
import dev.melcodes.kilometre.DEFAULT_GRADIENT_START_HEX
import dev.melcodes.kilometre.KilometreApp
import dev.melcodes.kilometre.R
import dev.melcodes.kilometre.domain.RouteSnapshot
import dev.melcodes.kilometre.domain.models.Session
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

// Sessions tab. Lists every past session newest-first: date, time range,
// duration, distance. Reads SessionRepository.allSessions as a Flow and
// turns it into Compose state, so the list updates live when a recording
// stops or a new row lands. Tapping a row opens the detail screen via the
// onSessionClick callback, which the NavHost turns into a navigation.
@Composable
fun SessionsScreen(
    onSessionClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Same container-lookup idiom as TodayScreen: the Application holds
    // the manual-DI container; we reach it through the context.
    val container = remember(context) {
        (context.applicationContext as KilometreApp).container
    }
    val sessions by container.sessionRepository.allSessions
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val startHex by container.gradientStartHex
        .collectAsStateWithLifecycle(initialValue = DEFAULT_GRADIENT_START_HEX)
    val endHex by container.gradientEndHex
        .collectAsStateWithLifecycle(initialValue = DEFAULT_GRADIENT_END_HEX)

    if (sessions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.sessions_placeholder),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // key = id so Compose can track rows across reorders/insertions
        // instead of recomposing the whole list.
        items(sessions, key = { it.id }) { session ->
            SessionCard(
                session = session,
                container = container,
                startHex = startHex,
                endHex = endHex,
                onClick = { onSessionClick(session.id) },
            )
        }
    }
}

// One card in the list: route thumbnail on the left (loaded async from the
// cached WebP), date + time range + duration in the middle, distance on the
// right. The thumbnail turns the text-only list into a visual archive of
// drives — you can spot the shape of a familiar route without tapping in.
// startHex/endHex are passed down from the screen so the thumbnail regenerates
// whenever the user changes the route gradient in Settings.
// container is hoisted from the screen so we don't redo the application-cast
// + remember on every card; matters when scrolling a long list.
@Composable
private fun SessionCard(
    session: Session,
    container: dev.melcodes.kilometre.AppContainer,
    startHex: String,
    endHex: String,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    // Three-tier load:
    //   1. process-wide ImageBitmap cache on the container — instant, survives
    //      leaving the Sessions tab so the cards don't flash blank on return.
    //   2. on-disk WebP — fast, populated by the recording pipeline.
    //   3. regenerate from raw GPS points — slow, only when neither cache hit.
    // The cache (memory + disk) is invalidated by setGradientXxx in the
    // container, so re-rendering only happens when the user actually changes
    // the gradient. Re-key on (startHex, endHex) too so a gradient change
    // forces the effect to re-run after invalidation.
    var thumbnail by remember(session.id, startHex, endHex) {
        mutableStateOf(container.routeThumbnailCache[session.id])
    }
    LaunchedEffect(session.id, startHex, endHex) {
        if (thumbnail != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            RouteSnapshot.load(context.cacheDir, session.id)?.asImageBitmap()
                ?: run {
                    val points = container.sessionRepository.points(session.id).first()
                    if (points.size < 2) return@run null
                    val bmp = RouteSnapshot.render(points, startHex, endHex)
                        ?: return@run null
                    RouteSnapshot.save(context.cacheDir, session.id, bmp)
                    bmp.asImageBitmap()
                }
        }
        if (loaded != null) {
            container.routeThumbnailCache[session.id] = loaded
            thumbnail = loaded
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reserve the thumbnail slot even while the bitmap is still
            // loading so the rest of the row doesn't reflow when it arrives.
            // While loading we show a muted box as a placeholder; once the
            // snapshot is ready the tile goes transparent so the route line
            // (with its pale casing) sits directly on the card surface.
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (thumbnail == null) MaterialTheme.colorScheme.surface
                        else Color.Transparent,
                    ),
            ) {
                val snap = thumbnail
                if (snap != null) {
                    Image(
                        bitmap = snap,
                        contentDescription = null,
                        modifier = Modifier.size(72.dp),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            // Left column: date headline + time range.
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatDate(session.startedAt),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatTimeRange(session.startedAt, session.endedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Right column: km (hero) stacked above duration (secondary).
            // This two-line right-side layout lets each piece of information
            // breathe without either wrapping or crowding the left column.
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = stringResource(R.string.session_distance, session.distanceMeters / 1000.0),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = formatDuration(session.durationSeconds),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
