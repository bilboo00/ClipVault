package com.clipvault.manager.ui.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TrendingDown
import androidx.compose.material.icons.automirrored.outlined.TrendingFlat
import androidx.compose.material.icons.automirrored.outlined.TrendingUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DonutSmall
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.clipvault.manager.data.local.entity.ClipType
import com.clipvault.manager.ui.components.label
import com.clipvault.manager.ui.home.FilterPill
import com.clipvault.manager.ui.theme.Spacing
import kotlin.math.roundToInt

/**
 * "Clipboard Insights" — chip-selected time horizon, a hero capture card with
 * a typical-vs-best gauge, a seven-day activity chart, the content-type split
 * and a storage & privacy pod with a manual prune action.
 */
@Composable
fun StatsScreen(
    viewModel: StatsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prune by viewModel.pruneState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = Spacing.Md,
                    end = Spacing.Md,
                    top = Spacing.Sm,
                    bottom = 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md)
            ) {
                item(key = "header", contentType = "header") { StatsHeader() }
                item(key = "periods", contentType = "chips") {
                    PeriodChips(selected = state.period, onSelect = viewModel::setPeriod)
                }
                item(key = "hero", contentType = "hero") { HeroCard(state) }
                item(key = "daily", contentType = "chart") { DailyActivityCard(state) }
                if (state.typeBreakdown.isNotEmpty()) {
                    item(key = "types", contentType = "types") {
                        ContentTypesCard(state.typeBreakdown)
                    }
                }
                item(key = "storage", contentType = "storage") {
                    StorageCard(state = state, prune = prune, onPrune = viewModel::pruneOld)
                }
            }
        }
    }
}

// ── Header & horizon chips ─────────────────────────────────────────

@Composable
private fun StatsHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(Spacing.Md))
        Column {
            Text(
                text = "Clipboard Insights",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Capture health & productivity metrics",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PeriodChips(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
        FilterPill(
            label = "7 Days",
            icon = Icons.Outlined.DateRange,
            selected = selected == StatsPeriod.SEVEN_DAYS,
            onClick = { onSelect(StatsPeriod.SEVEN_DAYS) }
        )
        FilterPill(
            label = "30 Days",
            icon = Icons.Outlined.CalendarMonth,
            selected = selected == StatsPeriod.THIRTY_DAYS,
            onClick = { onSelect(StatsPeriod.THIRTY_DAYS) }
        )
        FilterPill(
            label = "All Time",
            icon = Icons.Outlined.History,
            selected = selected == StatsPeriod.ALL_TIME,
            onClick = { onSelect(StatsPeriod.ALL_TIME) }
        )
    }
}

// ── Hero card ──────────────────────────────────────────────────────

@Composable
private fun HeroCard(state: StatsUiState) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box {
            // Soft corner bloom — radial gradient instead of blur so it
            // renders identically below API 31.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = 32.dp, y = 32.dp)
                    .size(144.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                scheme.primary.copy(alpha = 0.35f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
            Column(
                modifier = Modifier.padding(Spacing.Lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.Md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.peakCount > 0) {
                        HeroPill(
                            icon = Icons.Outlined.AutoAwesome,
                            text = "Peak ${state.peakCount}"
                        )
                    } else {
                        Spacer(Modifier.width(Spacing.Xs))
                    }
                    val average = state.averagePerDay
                    if (average > 0f) {
                        val delta = ((state.todayCount - average) / average * 100f).roundToInt()
                        HeroPill(
                            icon = when {
                                delta > 0 -> Icons.AutoMirrored.Outlined.TrendingUp
                                delta < 0 -> Icons.AutoMirrored.Outlined.TrendingDown
                                else -> Icons.AutoMirrored.Outlined.TrendingFlat
                            },
                            text = "${if (delta > 0) "+" else ""}$delta%"
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = "%,d".format(state.periodCount),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = scheme.onPrimary
                            )
                            Spacer(Modifier.width(Spacing.Xs))
                            Text(
                                text = "clips",
                                style = MaterialTheme.typography.bodyMedium,
                                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
                                modifier = Modifier.padding(bottom = Spacing.Xs)
                            )
                        }
                        Text(
                            text = periodSubtitle(state.period),
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                    Gauge(
                        value = state.averagePerDay,
                        fraction = if (state.peakCount > 0) {
                            state.averagePerDay / state.peakCount
                        } else 0f
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(scheme.onPrimaryContainer.copy(alpha = 0.15f))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = null,
                            tint = scheme.onPrimaryContainer.copy(alpha = 0.9f),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(Modifier.width(Spacing.Xs))
                        Text(
                            text = "${state.todayCount} today",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onPrimaryContainer.copy(alpha = 0.9f)
                        )
                    }
                    Text(
                        text = "This week: ${state.weekCount}",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun HeroPill(icon: ImageVector, text: String) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(50),
        color = scheme.onPrimaryContainer.copy(alpha = 0.18f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = scheme.onPrimaryContainer
            )
        }
    }
}

/** 64dp ring: center shows the typical day, the arc the typical-vs-best ratio. */
@Composable
private fun Gauge(value: Float, fraction: Float) {
    val scheme = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 4.5.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawArc(
                color = scheme.onPrimaryContainer.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = scheme.onPrimary,
                startAngle = -90f,
                sweepAngle = 360f * fraction.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "%.1f".format(value),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary
            )
            Text(
                text = "PER DAY",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                letterSpacing = 0.6.sp,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

private fun periodSubtitle(period: StatsPeriod): String = when (period) {
    StatsPeriod.SEVEN_DAYS -> "clips captured in the last 7 days"
    StatsPeriod.THIRTY_DAYS -> "clips captured in the last 30 days"
    StatsPeriod.ALL_TIME -> "clips captured since you started"
}

// ── Daily activity chart ───────────────────────────────────────────

@Composable
private fun DailyActivityCard(state: StatsUiState) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = scheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.Sm))
                    Text(
                        text = "Daily Activity",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface
                    )
                }
                Text(
                    text = "Avg: ${"%.1f".format(state.averagePerDay)} clips/day",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(164.dp),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)
            ) {
                state.dailyBars.forEach { bar ->
                    DayBarColumn(bar = bar, peak = state.peakCount)
                }
            }
        }
    }
}

@Composable
private fun RowScope.DayBarColumn(bar: DayBar, peak: Int) {
    val scheme = MaterialTheme.colorScheme
    val isPeak = peak > 0 && bar.count == peak
    val fraction = if (peak > 0) bar.count.toFloat() / peak else 0f
    val barColor = when {
        isPeak -> scheme.primary
        bar.count == 0 -> scheme.surfaceContainerHighest
        else -> scheme.secondaryContainer
    }
    // Fixed bar area so the day label below can never be pushed out by a
    // full-height bar, and every column measures identically.
    val barArea = 120.dp
    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Box(modifier = Modifier.fillMaxWidth().height(barArea)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(0.55f)
                        .height(if (fraction > 0f) barArea * fraction else 4.dp)
                        .clip(RoundedCornerShape(topStart = 13.dp, topEnd = 13.dp))
                        .background(barColor)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = bar.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (bar.isToday) scheme.primary else scheme.onSurfaceVariant
            )
        }
        if (isPeak) {
            Surface(
                modifier = Modifier.align(Alignment.TopCenter),
                shape = RoundedCornerShape(6.dp),
                color = scheme.primary
            ) {
                Text(
                    text = "${bar.count}",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = scheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

// ── Content types ──────────────────────────────────────────────────

/** Categorical segment palette. Both the stacked bar and the legend dots pull
 *  from this list, and the legend cells sit on `surfaceContainerLow` — so every
 *  entry has to stay legible against a *dark* chip in dark mode. The "on
 *  container" and dark container roles fail that test (a dark clay dot on a
 *  dark cell disappears), so the dark ordering swaps in roles that are light
 *  there instead. Order keeps the mock's lead pairing of the two brand accents. */
@Composable
private fun segmentColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    val isDark = scheme.surface.luminance() < 0.5f
    return if (isDark) {
        listOf(
            scheme.primary,
            scheme.tertiary,
            scheme.secondary,
            scheme.onSurfaceVariant,
            scheme.outline
        )
    } else {
        listOf(
            scheme.primary,
            scheme.secondary,
            scheme.secondaryContainer,
            scheme.tertiary,
            scheme.onSurfaceVariant
        )
    }
}

private const val MAX_SEGMENTS = 4

@Composable
private fun ContentTypesCard(breakdown: List<Pair<ClipType, Int>>) {
    val scheme = MaterialTheme.colorScheme
    val colors = segmentColors()
    val total = breakdown.sumOf { it.second }.coerceAtLeast(1)

    // Top four types stay individual; anything beyond folds into "Other".
    val top = breakdown.take(MAX_SEGMENTS)
    val otherCount = breakdown.drop(MAX_SEGMENTS).sumOf { it.second }
    val segments: List<Pair<String, Int>> = buildList {
        top.forEach { (type, count) -> add(type.label() to count) }
        if (otherCount > 0) add("Other" to otherCount)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.DonutSmall,
                        contentDescription = null,
                        tint = scheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.Sm))
                    Text(
                        text = "Content Types",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface
                    )
                }
                Text(
                    text = "${breakdown.size} types",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant
                )
            }

            // Stacked multi-bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(scheme.surfaceContainerHigh)
            ) {
                segments.forEachIndexed { index, (_, count) ->
                    Box(
                        modifier = Modifier
                            .weight(count.toFloat().coerceAtLeast(0.01f))
                            .fillMaxHeight()
                            .background(colors[index % colors.size])
                    )
                }
            }

            // 2-column legend
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                segments.chunked(2).forEachIndexed { rowIndex, pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                        pair.forEachIndexed { indexInRow, (name, count) ->
                            LegendCell(
                                color = colors[(rowIndex * 2 + indexInRow) % colors.size],
                                name = name,
                                count = count,
                                total = total,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendCell(
    color: Color,
    name: String,
    count: Int,
    total: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(Spacing.Sm))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${(count * 100.0 / total).roundToInt()}% ($count)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ── Storage & privacy ──────────────────────────────────────────────

@Composable
private fun StorageCard(
    state: StatsUiState,
    prune: PruneUiState,
    onPrune: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Security,
                        contentDescription = null,
                        tint = scheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(Spacing.Sm))
                    Text(
                        text = "Storage & Privacy",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurface
                    )
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = scheme.secondaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.Sm, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VerifiedUser,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Clean",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = scheme.primary
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Sm)) {
                StoragePod(
                    label = "Storage used",
                    value = formatBytes(state.totalBytes),
                    caption = "Local SQLite cache",
                    captionColor = scheme.secondary,
                    modifier = Modifier.weight(1f)
                )
                StoragePod(
                    label = "Favorites",
                    value = "${state.favoriteCount}",
                    caption = "Pinned & liked safe",
                    captionColor = scheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Md)
            ) {
                Text(
                    text = if (state.retentionDays > 0) {
                        "Clips older than ${state.retentionDays} days can be pruned safely."
                    } else {
                        "Auto-delete is off — your history is kept."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (state.retentionDays > 0) {
                    Surface(
                        onClick = onPrune,
                        enabled = !prune.running && !prune.done,
                        shape = RoundedCornerShape(50),
                        color = scheme.secondaryContainer,
                        contentColor = scheme.onSecondaryContainer
                    ) {
                        Row(
                            modifier = Modifier
                                .height(40.dp)
                                .padding(horizontal = Spacing.Lg),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Xs)
                        ) {
                            when {
                                prune.running -> CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = scheme.onSecondaryContainer
                                )
                                prune.done -> Icon(
                                    imageVector = Icons.Outlined.VerifiedUser,
                                    contentDescription = null,
                                    tint = scheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                else -> Icon(
                                    imageVector = Icons.Outlined.CleaningServices,
                                    contentDescription = null,
                                    tint = scheme.onSecondaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Text(
                                text = when {
                                    prune.running -> "Pruning…"
                                    prune.done -> "Pruned!"
                                    else -> "Prune old clips"
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StoragePod(
    label: String,
    value: String,
    caption: String,
    captionColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.padding(Spacing.Md)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = captionColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
}
