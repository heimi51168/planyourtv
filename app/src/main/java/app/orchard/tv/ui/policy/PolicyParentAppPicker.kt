package app.orchard.tv.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.policy.PolicyInstalledApp
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.ALL_VALID_APP
import com.planyourtv.policy.LocalRules

internal const val APP_PICKER_COLUMNS = 5

internal sealed interface AppPickerEntry {
    data object AllApps : AppPickerEntry

    data class App(
        val packageName: String,
        val label: String,
        val icon: ImageBitmap?,
        val installed: Boolean,
    ) : AppPickerEntry
}

/**
 * Installed apps first, then packages that are still in the rule but no longer installed. Keeping
 * the latter visible means a reinstall cannot silently widen access, and the parent can remove a
 * stale package without it hiding in the stored JSON.
 *
 * [study] edits the study scope instead. That scope has no all-apps tile and may be empty, so a
 * parent can turn study mode off by clearing every tile.
 */
internal fun buildAppPickerEntries(
    installed: List<PolicyInstalledApp>,
    rules: LocalRules,
    study: Boolean = false,
): List<AppPickerEntry> = buildList {
    if (!study) add(AppPickerEntry.AllApps)
    installed.forEach { app ->
        add(AppPickerEntry.App(app.packageName, app.label, app.icon, installed = true))
    }
    val selected = when {
        study -> rules.effectiveStudyPackages
        rules.usesAppSelection -> rules.effectiveAllowedPackages
        else -> emptySet()
    }
    if (selected.isNotEmpty()) {
        val known = installed.mapTo(HashSet()) { it.packageName }
        selected
            .asSequence()
            .filter { it != ALL_VALID_APP && it !in known }
            .sorted()
            .forEach { packageName ->
                add(AppPickerEntry.App(packageName, packageName, icon = null, installed = false))
            }
    }
}

internal fun appPickerInitialIndex(
    entries: List<AppPickerEntry>,
    rules: LocalRules,
    study: Boolean = false,
): Int {
    val selected = when {
        study -> rules.effectiveStudyPackages
        rules.usesAppSelection -> rules.effectiveAllowedPackages
        else -> return 0
    }
    return entries.indexOfFirst {
        it is AppPickerEntry.App && it.packageName in selected
    }.coerceAtLeast(0)
}

/**
 * Logical grid movement. Edges never wrap and a vertical move stays in the same column, so the
 * focused tile cannot jump to a different position when a row is only partly filled.
 */
internal fun moveAppPickerIndex(index: Int, count: Int, dx: Int, dy: Int): Int {
    if (count <= 0) return 0
    val column = index % APP_PICKER_COLUMNS
    val next = when {
        dx < 0 && column > 0 -> index - 1
        dx > 0 && column < APP_PICKER_COLUMNS - 1 && index + 1 < count -> index + 1
        dy < 0 -> index - APP_PICKER_COLUMNS
        dy > 0 -> index + APP_PICKER_COLUMNS
        else -> index
    }
    return if (next in 0 until count) next else index
}

/**
 * Full-screen app scope picker. Focus stays at the root key handler; the grid only renders the
 * logical [focusedIndex], so a d-pad press can never move focus geometrically into another tile.
 */
@Composable
internal fun V6AppPickerOverlay(
    rules: LocalRules,
    entries: List<AppPickerEntry>,
    loading: Boolean,
    loadFailed: Boolean,
    focusedIndex: Int,
    message: Int?,
    study: Boolean = false,
    onActivate: (Int) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val allApps = !study && !rules.usesAppSelection
    val selectedPackages = if (study) {
        rules.effectiveStudyPackages
    } else {
        rules.effectiveAllowedPackages
    }
    val selectedCount = selectedPackages.size
    val installedCount = entries.count { it is AppPickerEntry.App && it.installed }

    LaunchedEffect(focusedIndex, entries.size) {
        if (focusedIndex !in entries.indices) return@LaunchedEffect
        val visible = gridState.layoutInfo.visibleItemsInfo.any { it.index == focusedIndex }
        if (!visible) gridState.scrollToItem(focusedIndex)
    }

    val status = when {
        message != null -> message
        loading && installedCount == 0 -> R.string.policy_parent_apps_loading
        loadFailed && installedCount == 0 -> R.string.policy_parent_apps_load_failed
        installedCount == 0 -> R.string.policy_parent_apps_empty
        study -> R.string.policy_v6_study_hint
        else -> R.string.policy_parent_apps_hint
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        PolicyParentBackdrop()
        // A sub-page of the parent center, on the same glass sheet so it reads as one surface.
        Column(
            modifier = Modifier
                .width(ParentSheetWidth)
                .height(ParentSheetHeight)
                .liquidGlass(ParentSheetCorner)
                .padding(horizontal = ParentSheetPaddingH, vertical = ParentSheetPaddingV),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BasicText(
                    stringResource(
                        if (study) R.string.policy_v6_study_apps else R.string.policy_parent_apps,
                    ),
                    style = Type.Title.copy(fontSize = 22.sp),
                    modifier = Modifier.weight(1f),
                )
                PolicyStatusChip(
                    text = if (allApps) {
                        stringResource(R.string.policy_parent_apps_all)
                    } else {
                        stringResource(R.string.policy_parent_apps_count, selectedCount)
                    },
                    color = if (allApps) Palette.SecondaryLabel else Palette.Green,
                )
            }
            Spacer(Modifier.height(5.dp))
            BasicText(
                stringResource(status),
                style = Type.Body.copy(
                    fontSize = 13.sp,
                    color = if (message != null) Palette.Orange else Palette.SecondaryLabel,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(14.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(APP_PICKER_COLUMNS),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(
                    items = entries,
                    key = { _, entry ->
                        when (entry) {
                            AppPickerEntry.AllApps -> "all"
                            is AppPickerEntry.App -> entry.packageName
                        }
                    },
                ) { index, entry ->
                    AppPickerTile(
                        entry = entry,
                        selected = when (entry) {
                            AppPickerEntry.AllApps -> allApps
                            is AppPickerEntry.App -> entry.packageName in selectedPackages
                        },
                        highlighted = index == focusedIndex,
                        onClick = { onActivate(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AppPickerTile(
    entry: AppPickerEntry,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val background = when {
        highlighted -> Palette.Focus
        selected -> Palette.Green.copy(alpha = 0.18f)
        else -> Palette.Fill.copy(alpha = 0.45f)
    }
    val border = if (selected && !highlighted) Palette.Green.copy(alpha = 0.72f) else Color.Transparent
    val labelColor = if (highlighted) Palette.OnFocus else Palette.Label
    val secondaryColor = if (highlighted) {
        Palette.OnFocus.copy(alpha = 0.62f)
    } else {
        Palette.SecondaryLabel
    }

    Pressable(
        onClick = onClick,
        focusRequester = null,
        focusedScale = 1f,
        modifier = Modifier.height(112.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(background)
                .border(2.dp, border, shape)
                .padding(horizontal = 8.dp, vertical = 9.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    when (entry) {
                        AppPickerEntry.AllApps -> AllAppsGlyph(labelColor)
                        is AppPickerEntry.App -> {
                            val icon = entry.icon
                            if (icon != null) {
                                Image(
                                    bitmap = icon,
                                    contentDescription = null,
                                    modifier = Modifier.size(42.dp),
                                    contentScale = ContentScale.Fit,
                                )
                            } else {
                                GlyphIcon(Glyph.Bag, secondaryColor, 26.dp)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(5.dp))
                BasicText(
                    text = when (entry) {
                        AppPickerEntry.AllApps -> stringResource(R.string.policy_parent_apps_all)
                        is AppPickerEntry.App -> entry.label
                    },
                    style = Type.Headline.copy(
                        fontSize = 12.sp,
                        color = labelColor,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (entry is AppPickerEntry.App && !entry.installed) {
                    Spacer(Modifier.height(2.dp))
                    BasicText(
                        stringResource(R.string.policy_parent_apps_not_installed),
                        style = Type.Caption.copy(fontSize = 10.sp, color = secondaryColor),
                        maxLines = 1,
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (highlighted) Palette.OnFocus else Palette.Green),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        Glyph.Check,
                        if (highlighted) Palette.Focus else Color.White,
                        11.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllAppsGlyph(color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color),
                    )
                }
            }
        }
    }
}
