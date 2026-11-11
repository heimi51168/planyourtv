package app.orchard.tv.ui.policy

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.orchard.tv.R
import app.orchard.tv.quiz.QuizRuntime
import app.orchard.tv.ui.theme.LocalReducedMotion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.quiz.QuizAnswerScope
import com.planyourtv.policy.quiz.QuizCurriculum
import com.planyourtv.policy.quiz.QuizUnitCatalog
import java.util.Locale

/**
 * The scope preview shown on the round-start film. It repeats the parent's
 * answer range and the format in the child's words, so a stale scope cannot
 * start a round silently after the phone setting changed.
 */
@Composable
internal fun QuizScopeCard(scope: QuizAnswerScope?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Fill)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                stringResource(R.string.policy_quiz_scope_label),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                stringResource(R.string.policy_quiz_format_label),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.width(6.dp))
            BasicText(
                stringResource(R.string.policy_quiz_format_single),
                style = Type.Caption.copy(
                    color = Palette.Label,
                    fontWeight = FontWeight.W600,
                ),
            )
        }
        Spacer(Modifier.height(3.dp))
        BasicText(
            scopeSummary(scope),
            style = Type.Callout.copy(color = Palette.Label),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The level wheel on the local challenge start film. The parent designs the
 * ordered list on the phone; the child picks one like a game level with the
 * remote's up/down keys. The selected level's scope and stock are repeated
 * below, so a short or changed level is visible before the round starts.
 */
@Composable
internal fun QuizLevelWheel(
    levels: List<QuizRuntime.LevelState>,
    selectedIndex: Int,
) {
    val reducedMotion = LocalReducedMotion.current
    val selected = levels.getOrNull(selectedIndex)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Fill)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                stringResource(R.string.policy_quiz_scope_label),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.weight(1f))
            BasicText(
                stringResource(R.string.policy_quiz_level_hint),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
            )
        }
        Spacer(Modifier.height(4.dp))
        if (levels.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QuizLevelWheelHeight),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    stringResource(R.string.policy_quiz_reason_no_levels),
                    style = Type.Caption.copy(color = Palette.Orange),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(QuizLevelWheelHeight),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(QuizLevelPillHeight)
                        .clip(RoundedCornerShape(QuizLevelPillHeight / 2))
                        .background(Palette.Focus),
                )
                AnimatedContent(
                    targetState = selectedIndex,
                    transitionSpec = {
                        val durationMillis = if (reducedMotion) 0 else 160
                        if (targetState > initialState) {
                            (slideInVertically(tween(durationMillis)) { it / 6 } +
                                fadeIn(tween(durationMillis))) togetherWith
                                (slideOutVertically(tween(durationMillis)) { -it / 6 } +
                                    fadeOut(tween(durationMillis)))
                        } else {
                            (slideInVertically(tween(durationMillis)) { -it / 6 } +
                                fadeIn(tween(durationMillis))) togetherWith
                                (slideOutVertically(tween(durationMillis)) { it / 6 } +
                                    fadeOut(tween(durationMillis)))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(QuizLevelWheelHeight)
                        .clipToBounds(),
                    contentAlignment = Alignment.Center,
                    label = "quiz-level",
                ) { value ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        for (offset in -1..1) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(QuizLevelRowHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                val index = value + offset
                                val level = levels.getOrNull(index)
                                if (level != null) {
                                    BasicText(
                                        text = "${index + 1} · " + levelWheelLabel(level),
                                        style = when (offset) {
                                            0 -> Type.Headline.copy(color = Palette.OnFocus)
                                            else -> Type.Caption.copy(
                                                color = Palette.Label.copy(alpha = 0.55f),
                                            )
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            BasicText(
                levelSummary(selected),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (selected != null) {
                Spacer(Modifier.height(2.dp))
                BasicText(
                    stringResource(R.string.policy_quiz_level_available, selected.available),
                    style = Type.Caption.copy(
                        color = if (selected.available > 0) {
                            Palette.SecondaryLabel
                        } else {
                            Palette.Orange
                        },
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val QuizLevelRowHeight = 30.dp
private val QuizLevelWheelHeight = QuizLevelRowHeight * 3
private val QuizLevelPillHeight = 30.dp

@Composable
private fun levelWheelLabel(level: QuizRuntime.LevelState): String =
    level.name.ifBlank { levelSummary(level) }

/** A custom level names its tag; only course levels have a curriculum scope. */
@Composable
private fun levelSummary(level: QuizRuntime.LevelState?): String =
    if (level != null && level.isCustom) {
        stringResource(R.string.policy_quiz_scope_custom_tag, level.tagName)
    } else {
        scopeSummary(level?.scope)
    }

@Composable
internal fun scopeSummary(scope: QuizAnswerScope?): String {
    if (scope == null) return stringResource(R.string.policy_quiz_scope_any)
    val curriculum = QuizCurriculum.current()
    val language = Locale.getDefault().language
    // The bundled curriculum owns these labels so the TV and phone page always
    // name a subject or textbook the same way.
    fun label(labels: Map<String, String>?, fallback: String): String =
        labels?.let { it[language] ?: it["en"] ?: it["zh"] } ?: fallback

    val grade = label(curriculum?.grade(scope.grade)?.labels, scope.grade.toString())
    val subject = label(curriculum?.subject(scope.subject)?.labels, scope.subject)
    val textbook = label(
        curriculum?.textbooks?.firstOrNull { it.id == scope.textbook }?.labels,
        scope.textbook,
    )
    val semester = stringResource(
        if (scope.semester == 2) {
            R.string.policy_quiz_semester_second
        } else {
            R.string.policy_quiz_semester_first
        },
    )
    val topic = when (scope.scopeType.uppercase(Locale.ROOT)) {
        "UNIT" -> QuizUnitCatalog.current()?.unitTitle(
            scope.grade,
            scope.semester,
            scope.subject,
            scope.textbook,
            scope.unitNo ?: 1,
        ) ?: stringResource(R.string.policy_quiz_scope_unit, scope.unitNo ?: 1)
        "FINAL" -> stringResource(R.string.policy_quiz_scope_final)
        "REVIEW" -> stringResource(R.string.policy_quiz_scope_review)
        else -> stringResource(R.string.policy_quiz_scope_midterm)
    }
    val difficulty = when (scope.difficulty?.uppercase(Locale.ROOT)) {
        "EASY" -> stringResource(R.string.policy_quiz_difficulty_easy)
        "MEDIUM" -> stringResource(R.string.policy_quiz_difficulty_medium)
        "HARD" -> stringResource(R.string.policy_quiz_difficulty_hard)
        else -> null
    }
    return listOf(grade, subject, semester, textbook, topic, difficulty)
        .filterNotNull()
        .joinToString(" · ")
}
