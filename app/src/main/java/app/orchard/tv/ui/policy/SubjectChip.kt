package app.orchard.tv.ui.policy

import android.graphics.BitmapFactory
import android.util.Log
import android.view.KeyEvent as NativeKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.policy.FamilyAvatarStore
import app.orchard.tv.policy.FamilyHub
import app.orchard.tv.policy.MemberSwitchAuth
import app.orchard.tv.policy.PolicyAvatarStore
import app.orchard.tv.policy.PolicyCloud
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.SubjectSwitcher
import app.orchard.tv.ui.components.Pressable
import app.orchard.tv.ui.components.consumeTaps
import app.orchard.tv.ui.components.liquidGlass
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Motion
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.CloudMember
import com.planyourtv.policy.FamilyMember
import com.planyourtv.policy.MemberPin
import com.planyourtv.policy.MemberSource
import com.planyourtv.policy.PolicyRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime

private const val TAG = "SubjectChip"

/**
 * Ring around a family member's avatar. The desktop chip and both member pickers draw it from
 * here, so a colour or weight change reaches every place the current member is shown.
 */
private val SubjectAvatarRing = Color.White.copy(alpha = 0.35f)

/** The same ring on the pickers' near-white focus plate, where white would disappear. */
private val SubjectAvatarRingOnFocus = Palette.OnFocus.copy(alpha = 0.22f)

/**
 * The current child's face beside the lock desktop's clock: right of a left clock, left of a
 * right clock. It is drawn even for a single default member so an upgraded TV reads as “this TV
 * has a user”, not as the old anonymous single-child screen.
 *
 * The large clock gives the face the room it lacked beside the status pill; the small clock
 * keeps the old 34 dp chip. [diameter] is the visible circle, and [focusedScale] shrinks slightly
 * for the large face so its ring stays clear of the clock above and the estimate band below.
 *
 * Local members draw the four preset animals with Canvas, or the locally uploaded JPEG; this
 * never reuses the cloud grant's `userName/userIconUrl`. [onUp], [onRight] and [onDown] keep the
 * focus path explicit (pill at the top right, dock below) instead of relying on Compose's
 * search.
 */
@Composable
internal fun SubjectChip(
    member: FamilyMember,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onUp: () -> Boolean,
    onRight: () -> Boolean,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 34.dp,
    focusedScale: Float = 1.1f,
    /** False while a modal sheet is up: the face stays composed for focus restoration. */
    enabled: Boolean = true,
    /** Lets the lock desktop return its grid to the shelf while the face is focused. */
    onFocused: ((Boolean) -> Unit)? = null,
) {
    SubjectChipFrame(
        label = stringResource(R.string.policy_subject_chip, member.displayName),
        focusRequester = focusRequester,
        onClick = onClick,
        onUp = onUp,
        onRight = onRight,
        onDown = onDown,
        modifier = modifier,
        diameter = diameter,
        focusedScale = focusedScale,
        enabled = enabled,
        onFocused = onFocused,
    ) {
        MemberAvatar(member, Modifier.fillMaxSize())
    }
}

/**
 * The garden family member the TV is currently running as (2026-11-21). The cloud-bound
 * desktop shows the member the backend resolved from `device_list.active_member_id` — not
 * the TV's local roster — so the face here matches what the parent sees in the garden app.
 * Styling is shared with [SubjectChip]; only the avatar source differs (an OSS URL instead
 * of the local preset animals).
 */
@Composable
internal fun CloudSubjectChip(
    name: String,
    avatarUrl: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onUp: () -> Boolean,
    onRight: () -> Boolean,
    onDown: () -> Boolean,
    modifier: Modifier = Modifier,
    diameter: Dp = 34.dp,
    focusedScale: Float = 1.1f,
    /** False while a modal sheet is up: the face stays composed for focus restoration. */
    enabled: Boolean = true,
    onFocused: ((Boolean) -> Unit)? = null,
) {
    SubjectChipFrame(
        label = stringResource(R.string.policy_subject_chip, name),
        focusRequester = focusRequester,
        onClick = onClick,
        onUp = onUp,
        onRight = onRight,
        onDown = onDown,
        modifier = modifier,
        diameter = diameter,
        focusedScale = focusedScale,
        enabled = enabled,
        onFocused = onFocused,
    ) {
        CloudMemberAvatar(name, avatarUrl, Modifier.fillMaxSize())
    }
}

@Composable
private fun SubjectChipFrame(
    label: String,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
    onUp: () -> Boolean,
    onRight: () -> Boolean,
    onDown: () -> Boolean,
    modifier: Modifier,
    diameter: Dp,
    focusedScale: Float,
    enabled: Boolean,
    onFocused: ((Boolean) -> Unit)?,
    avatar: @Composable () -> Unit,
) {
    Pressable(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        focusedScale = focusedScale,
        onFocused = onFocused,
        onKey = { event ->
            when (event.key) {
                androidx.compose.ui.input.key.Key.DirectionUp -> {
                    if (event.type == KeyEventType.KeyDown) onUp() else true
                }
                androidx.compose.ui.input.key.Key.DirectionRight -> {
                    if (event.type == KeyEventType.KeyDown) onRight()
                    true
                }
                androidx.compose.ui.input.key.Key.DirectionDown -> {
                    if (event.type == KeyEventType.KeyDown) onDown() else true
                }
                else -> false
            }
        },
        modifier = modifier.semantics { contentDescription = label },
    ) { state ->
        val bg by animateColorAsState(
            if (state.focused) Palette.Focus else Color.White.copy(alpha = 0.14f),
            tween(160, easing = Motion.EaseOut),
            label = "subject-chip-bg",
        )
        Box(
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(bg)
                .border(
                    width = if (diameter >= 60.dp) 2.dp else 1.dp,
                    color = SubjectAvatarRing,
                    shape = CircleShape,
                )
                .padding(diameter * 0.07f),
            contentAlignment = Alignment.Center,
        ) {
            avatar()
        }
    }
}

/**
 * A single member's profile. With one member there is nothing to switch to, so the page only
 * explains where to edit the name and photo; the switching list arrives with subject-aware
 * runtime/quiz. Back and Close both dismiss it.
 */
@Composable
internal fun SubjectProfileOverlay(
    member: FamilyMember,
    showSingleHint: Boolean,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeFocus.requestFocus() }
    }
    // Drawn inline, not in a Dialog: a second window took over a second to appear on the
    // projector, which read as a frozen remote. The caller disables the desktop's focus while
    // this is up, so the card is still the only focusable surface.
    BackHandler(onBack = onDismiss)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Palette.OnFocus.copy(alpha = 0.72f))
            .consumeTaps(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .liquidGlass(26.dp)
                .padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MemberAvatar(member, Modifier.size(92.dp))
            BasicText(
                member.displayName,
                style = Type.LargeTitle.copy(fontSize = 24.sp),
            )
            BasicText(
                stringResource(
                    if (member.source == MemberSource.CLOUD) {
                        R.string.policy_subject_cloud
                    } else {
                        R.string.policy_subject_local
                    }
                ),
                style = Type.Caption.copy(fontSize = 13.sp, color = Palette.SecondaryLabel),
            )
            Spacer(Modifier.height(2.dp))
            BasicText(
                stringResource(R.string.policy_subject_web_hint),
                style = Type.Body.copy(fontSize = 15.sp, color = Palette.Label),
            )
            if (showSingleHint) {
                BasicText(
                    stringResource(R.string.policy_subject_single_hint),
                    style = Type.Caption.copy(fontSize = 13.sp, color = Palette.SecondaryLabel),
                )
            }
            Spacer(Modifier.height(8.dp))
            LockButton(
                label = stringResource(R.string.policy_parent_close),
                onClick = onDismiss,
                width = 200.dp,
                focusRequester = closeFocus,
                primary = true,
            )
        }
    }
}

private enum class SubjectSwitchPhase { LIST, PIN }

/**
 * The child-facing member switch. When the device setting is on, picking a name asks for that
 * member's 6-digit password; when it is off the switch is direct. Either way [SubjectSwitcher]
 * refuses the move while a session, quiz or study round belongs to the current child, and it is
 * never offered on a cloud-bound TV (that build is single-member until the backend carries
 * per-member policy).
 *
 * The profile editor stays on the phone: this sheet only picks the child. The TV draws the same
 * avatars as the Web so a member looks identical on both terminals.
 */
@Composable
internal fun SubjectSwitchOverlay(
    family: FamilyHub.UiState,
    onDismiss: () -> Unit,
) {
    val members = family.activeMembers
    val currentId = family.activeUserId
    var phase by remember { mutableStateOf(SubjectSwitchPhase.LIST) }
    var target by remember { mutableStateOf<FamilyMember?>(null) }
    var entered by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val switchable = SubjectSwitcher.canSwitch()
    val unavailable = when {
        PolicyModeCoordinator.isCloud() -> R.string.policy_subject_switch_cloud
        !switchable -> R.string.policy_subject_switch_busy
        else -> null
    }
    val firstSwitchTarget = members.indexOfFirst { it.userId != currentId }
    // The waiver label must disappear when its window ends while the sheet stays open. A
    // half-minute tick is enough for a minute-resolution rule and stays idle otherwise.
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            delay(30_000)
            value = LocalDateTime.now()
        }
    }

    // The keypad leaves the composition while a code is checked, so a wrong code would come
    // back with no focused key and the remote's number keys would go nowhere. Ask for focus
    // whenever the sheet settles. The error is part of the key because a failed check can
    // flip busy back before a frame renders it, leaving busy unchanged by the time the
    // keypad returns; the lock screen's PIN pad relies on the same error-keyed request.
    LaunchedEffect(phase, busy, error) {
        if (busy) return@LaunchedEffect
        withFrameNanos { }
        runCatching { firstFocus.requestFocus() }
    }

    /**
     * [promptShown] is the decision made when the row was picked, not a fresh clock reading.
     * Re-evaluating at submit time would make a window ending during PIN entry reject an empty
     * code, and would let a window starting during a direct switch change the flow mid-tap.
     * When no prompt was shown, only a concurrent change that turns protection on aborts.
     */
    fun submit(
        chosen: FamilyMember,
        candidate: String?,
        promptShown: Boolean,
        protectedAtPick: Boolean,
    ) {
        busy = true
        error = null
        scope.launch {
            val protectedNow = FamilyHub.member(chosen.userId)
                ?.switchPinRequired
                ?: chosen.switchPinRequired
            // Protection turned on remotely between the tap and this switch: do not use an
            // exemption decision made against the old, open member.
            if (!promptShown && !protectedAtPick && protectedNow) {
                busy = false
                entered = ""
                phase = SubjectSwitchPhase.LIST
                error = R.string.policy_subject_switch_failed
                return@launch
            }
            if (promptShown) {
                val checked = withContext(Dispatchers.Default) {
                    MemberSwitchAuth.verify(chosen.userId, candidate.orEmpty())
                }
                if (checked != MemberSwitchAuth.VerifyResult.OK) {
                    busy = false
                    entered = ""
                    error = if (checked == MemberSwitchAuth.VerifyResult.LOCKED_OUT) {
                        R.string.policy_locked_out
                    } else {
                        R.string.policy_wrong_pin
                    }
                    return@launch
                }
            }
            val result = withContext(Dispatchers.Default) {
                SubjectSwitcher.switchTo(chosen.userId)
            }
            busy = false
            if (result == SubjectSwitcher.Result.OK) {
                onDismiss()
            } else {
                entered = ""
                phase = SubjectSwitchPhase.LIST
                error = when (result) {
                    SubjectSwitcher.Result.CLOUD_MODE -> R.string.policy_subject_switch_cloud
                    SubjectSwitcher.Result.BUSY,
                    SubjectSwitcher.Result.NOT_LOCKED -> R.string.policy_subject_switch_busy
                    // STATE_CHANGED cannot happen here: the TV always switches from the state
                    // it just rendered, and the gate re-checks it under the switch lock.
                    else -> R.string.policy_subject_switch_failed
                }
            }
        }
    }

    fun start(chosen: FamilyMember) {
        target = chosen
        entered = ""
        error = null
        val latest = FamilyHub.member(chosen.userId) ?: chosen
        val prompt = MemberSwitchAuth.requiresPin(latest)
        if (prompt) {
            phase = SubjectSwitchPhase.PIN
        } else {
            submit(chosen, null, promptShown = false, protectedAtPick = latest.switchPinRequired)
        }
    }

    fun type(digit: Char) {
        if (busy || phase != SubjectSwitchPhase.PIN ||
            entered.length >= MemberPin.LENGTH
        ) {
            return
        }
        entered += digit
        error = null
        if (entered.length == MemberPin.LENGTH) {
            target?.let {
                submit(it, entered, promptShown = true, protectedAtPick = true)
            }
        }
    }

    fun erase() {
        if (!busy && entered.isNotEmpty()) {
            entered = entered.dropLast(1)
            error = null
        }
    }

    // Inline instead of a Dialog: the second window cost over a second on the projector, so
    // tapping the face looked ignored. The lock desktop disables its own focus while this is
    // composed; the sheet is the only focusable surface left.
    BackHandler {
        if (!busy) {
            if (phase == SubjectSwitchPhase.PIN) {
                phase = SubjectSwitchPhase.LIST
                entered = ""
                error = null
            } else {
                onDismiss()
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.OnFocus.copy(alpha = 0.72f))
                .consumeTaps()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val native = event.nativeKeyEvent
                    when {
                        busy -> native.keyCode == NativeKeyEvent.KEYCODE_BACK
                        phase == SubjectSwitchPhase.PIN &&
                            native.keyCode == NativeKeyEvent.KEYCODE_BACK -> {
                            phase = SubjectSwitchPhase.LIST
                            entered = ""
                            error = null
                            true
                        }
                        phase == SubjectSwitchPhase.PIN &&
                            native.keyCode in NativeKeyEvent.KEYCODE_0..NativeKeyEvent.KEYCODE_9 -> {
                            type('0' + (native.keyCode - NativeKeyEvent.KEYCODE_0))
                            true
                        }
                        phase == SubjectSwitchPhase.PIN &&
                            native.keyCode == NativeKeyEvent.KEYCODE_DEL -> {
                            erase()
                            true
                        }
                        else -> false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .liquidGlass(26.dp)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (phase == SubjectSwitchPhase.LIST) {
                    BasicText(
                        stringResource(R.string.policy_subject_switch_title),
                        style = Type.LargeTitle.copy(fontSize = 22.sp),
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 250.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        members.forEachIndexed { index, member ->
                            MemberRow(
                                member = member,
                                current = member.userId == currentId,
                                now = now,
                                // Every row stays focusable, the active member included;
                                // choosing the current member just closes the sheet.
                                enabled = unavailable == null && !busy,
                                focusRequester = if (index == firstSwitchTarget) firstFocus else null,
                                onClick = {
                                    if (member.userId == currentId) onDismiss() else start(member)
                                },
                            )
                        }
                    }
                    when {
                        busy -> BasicText(
                            stringResource(R.string.policy_subject_switching),
                            style = Type.Caption.copy(
                                fontSize = 13.sp,
                                color = Palette.SecondaryLabel,
                                textAlign = TextAlign.Center,
                            ),
                        )
                        unavailable != null -> BasicText(
                            stringResource(unavailable),
                            style = Type.Caption.copy(
                                fontSize = 13.sp,
                                color = Palette.SecondaryLabel,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                    error?.let { message ->
                        BasicText(
                            stringResource(message),
                            style = Type.Caption.copy(
                                fontSize = 13.sp,
                                color = Palette.Red,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                    LockButton(
                        label = stringResource(R.string.policy_parent_close),
                        onClick = onDismiss,
                        width = 200.dp,
                        focusRequester = if (unavailable != null || firstSwitchTarget < 0) {
                            firstFocus
                        } else {
                            null
                        },
                        primary = unavailable != null,
                        enabled = !busy,
                    )
                } else {
                    target?.let { MemberAvatar(it, Modifier.size(64.dp)) }
                    BasicText(
                        target?.let {
                            stringResource(
                                R.string.policy_subject_switch_pin_title,
                                it.displayName,
                            )
                        } ?: stringResource(R.string.policy_subject_switch_title),
                        style = Type.LargeTitle.copy(
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp)) {
                        repeat(MemberPin.LENGTH) { index ->
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (index < entered.length) {
                                            Palette.Label
                                        } else {
                                            Color.Transparent
                                        },
                                    )
                                    .border(1.5.dp, Palette.Label, CircleShape),
                            )
                        }
                    }
                    BasicText(
                        error?.let { stringResource(it) }
                            ?: if (busy) stringResource(R.string.policy_subject_switching) else " ",
                        style = Type.Caption.copy(
                            fontSize = 13.sp,
                            color = if (busy) Palette.SecondaryLabel else Palette.Red,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    if (!busy) {
                        PinKeypad(
                            onDigit = ::type,
                            onErase = ::erase,
                            firstKeyFocus = firstFocus,
                            leftKeyLabel = stringResource(R.string.policy_back),
                            onLeftKey = {
                                phase = SubjectSwitchPhase.LIST
                                entered = ""
                                error = null
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cloud profile page for a TV whose allowed list has a single garden member: there is nothing
 * to switch to, so it only explains where the profile is edited. Mirrors [SubjectProfileOverlay]
 * so a bound TV and a local TV read the same.
 */
@Composable
internal fun CloudSubjectProfileOverlay(
    member: CloudMember,
    onDismiss: () -> Unit,
) {
    val closeFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { closeFocus.requestFocus() }
    }
    // Inline, like [SubjectProfileOverlay], so opening it never waits for a second window.
    BackHandler(onBack = onDismiss)
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.OnFocus.copy(alpha = 0.72f))
                .consumeTaps(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .liquidGlass(26.dp)
                    .padding(horizontal = 36.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CloudMemberAvatar(
                    name = member.name,
                    avatarUrl = member.avatarUrl,
                    modifier = Modifier.size(92.dp),
                )
                BasicText(
                    member.name,
                    style = Type.LargeTitle.copy(fontSize = 24.sp),
                )
                BasicText(
                    stringResource(R.string.policy_subject_cloud),
                    style = Type.Caption.copy(fontSize = 13.sp, color = Palette.SecondaryLabel),
                )
                Spacer(Modifier.height(2.dp))
                BasicText(
                    stringResource(R.string.policy_subject_garden_hint),
                    style = Type.Body.copy(fontSize = 15.sp, color = Palette.Label),
                )
                Spacer(Modifier.height(8.dp))
                LockButton(
                    label = stringResource(R.string.policy_parent_close),
                    onClick = onDismiss,
                    width = 200.dp,
                    focusRequester = closeFocus,
                    primary = true,
                )
            }
        }
    }
}

/**
 * The garden family switch for a cloud-bound TV (2026-11-21). The list is the family roster the
 * backend ships with the config — already filtered by this device's allowed members — and the
 * pick is written back with `PUT /devices/current/active-member`, so the phone and the TV agree
 * on who is using the TV. The backend re-projects that member's apps onto the next config poll;
 * [PolicyRuntime.requestSync] pulls it immediately on success.
 *
 * There is no local PIN here: the identity is a garden account, and a refused switch is shown
 * with the backend's human-readable reason instead of a generic error.
 */
@Composable
internal fun CloudSubjectSwitchOverlay(
    deviceNo: String,
    members: List<CloudMember>,
    activeMemberId: Long?,
    onDismiss: () -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    var busyId by remember { mutableStateOf<Long?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firstFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }
    val failureText = stringResource(R.string.policy_subject_switch_failed)
    val firstSwitchTarget = members.indexOfFirst { it.memberId != activeMemberId }

    LaunchedEffect(busy, error) {
        if (busy) return@LaunchedEffect
        withFrameNanos { }
        runCatching {
            if (firstSwitchTarget >= 0) firstFocus.requestFocus() else closeFocus.requestFocus()
        }
    }

    fun switchTo(member: CloudMember) {
        if (busy || member.memberId == activeMemberId) return
        busy = true
        busyId = member.memberId
        error = null
        scope.launch {
            val result = PolicyCloud.switchActiveMember(deviceNo, member.memberId)
            busy = false
            busyId = null
            result.fold(
                onSuccess = {
                    PolicyRuntime.requestSync()
                    onDismiss()
                },
                onFailure = { failure ->
                    Log.w(TAG, "switch active member failed: ${member.memberId}", failure)
                    error = PolicyCloud.businessMessage(failure) ?: failureText
                },
            )
        }
    }

    // Inline, like the local sheet: a Dialog window takes too long to appear on TV hardware.
    BackHandler { if (!busy) onDismiss() }
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Palette.OnFocus.copy(alpha = 0.72f))
                .consumeTaps(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .liquidGlass(26.dp)
                    .padding(horizontal = 32.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BasicText(
                    stringResource(R.string.policy_subject_switch_title),
                    style = Type.LargeTitle.copy(fontSize = 22.sp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 250.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    members.forEachIndexed { index, member ->
                        CloudMemberRow(
                            member = member,
                            current = member.memberId == activeMemberId,
                            // Every row stays focusable, the active member included;
                            // choosing the current member just closes the sheet.
                            enabled = !busy,
                            focusRequester = if (index == firstSwitchTarget) firstFocus else null,
                            onClick = {
                                if (member.memberId == activeMemberId) {
                                    onDismiss()
                                } else {
                                    switchTo(member)
                                }
                            },
                        )
                    }
                }
                when {
                    busy -> BasicText(
                        stringResource(R.string.policy_subject_switching),
                        style = Type.Caption.copy(
                            fontSize = 13.sp,
                            color = Palette.SecondaryLabel,
                            textAlign = TextAlign.Center,
                        ),
                    )
                    else -> Unit
                }
                error?.let { message ->
                    BasicText(
                        message,
                        style = Type.Caption.copy(
                            fontSize = 13.sp,
                            color = Palette.Red,
                            textAlign = TextAlign.Center,
                        ),
                    )
                }
                LockButton(
                    label = stringResource(R.string.policy_parent_close),
                    onClick = onDismiss,
                    width = 200.dp,
                    focusRequester = if (firstSwitchTarget < 0) closeFocus else null,
                    primary = firstSwitchTarget < 0,
                    enabled = !busy,
                )
            }
        }
    }
}

/** Roster row for [CloudSubjectSwitchOverlay]; the busy member shows "Switching…". */
@Composable
private fun CloudMemberRow(
    member: CloudMember,
    current: Boolean,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Pressable(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        focusedScale = 1.02f,
        modifier = Modifier.fillMaxWidth(),
    ) { state ->
        val bg by animateColorAsState(
            if (state.focused) Palette.Focus else Color.White.copy(alpha = 0.10f),
            tween(160, easing = Motion.EaseOut),
            label = "cloud-subject-row-bg",
        )
        val fg by animateColorAsState(
            if (state.focused) Palette.OnFocus else Palette.Label,
            tween(160, easing = Motion.EaseOut),
            label = "cloud-subject-row-fg",
        )
        val avatarRing = when {
            current -> if (state.focused) SubjectAvatarRingOnFocus else SubjectAvatarRing
            state.focused -> Palette.OnFocus.copy(alpha = 0.16f)
            else -> Color.Transparent
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CloudMemberAvatar(
                name = member.name,
                avatarUrl = member.avatarUrl,
                modifier = Modifier
                    .size(36.dp)
                    .border(1.dp, avatarRing, CircleShape),
            )
            BasicText(
                member.name,
                modifier = Modifier.weight(1f),
                style = Type.Body.copy(fontSize = 16.sp, color = fg),
            )
            if (current) CurrentMemberBadge(focused = state.focused)
        }
    }
}

/**
 * A garden member's photo. The backend ships a public icon-library URL; it is loaded straight
 * from OSS like the grant icon. A blank URL draws the member's initial, and a URL that fails to
 * load is logged and then shows the same initial — the circle never goes blank silently.
 */
@Composable
private fun CloudMemberAvatar(
    name: String,
    avatarUrl: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, avatarUrl) {
        value = if (avatarUrl.isBlank()) {
            null
        } else {
            val loaded = withContext(Dispatchers.IO) {
                PolicyAvatarStore.load(context, avatarUrl)
            }
            if (loaded == null) {
                Log.w(TAG, "cloud member avatar failed to load: $avatarUrl")
            }
            loaded
        }
    }
    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        BoxWithConstraints(
            modifier = modifier
                .clip(CircleShape)
                .background(cloudAvatarColor(name)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(
                name.take(1).ifBlank { "?" },
                style = Type.LargeTitle.copy(
                    fontSize = (maxWidth.value * 0.44f).sp,
                    color = Color.White,
                ),
            )
        }
    }
}

private fun cloudAvatarColor(name: String): Color {
    val index = name.fold(0) { acc, ch -> acc * 31 + ch.code } and Int.MAX_VALUE
    return CloudAvatarColors[index % CloudAvatarColors.size]
}

private val CloudAvatarColors = listOf(
    Color(0xFF6C8AE4),
    Color(0xFF4FA98C),
    Color(0xFFE0A458),
    Color(0xFFD2768A),
)

@Composable
private fun MemberRow(
    member: FamilyMember,
    current: Boolean,
    now: LocalDateTime,
    enabled: Boolean,
    focusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    Pressable(
        onClick = onClick,
        enabled = enabled,
        focusRequester = focusRequester,
        focusedScale = 1.02f,
        modifier = Modifier.fillMaxWidth(),
    ) { state ->
        val bg by animateColorAsState(
            if (state.focused) Palette.Focus else Color.White.copy(alpha = 0.10f),
            tween(160, easing = Motion.EaseOut),
            label = "subject-row-bg",
        )
        // The focused plate is near-white, so the label must flip to the dark content colour
        // the rest of the tvOS controls use; white-on-white made the focused member vanish.
        val fg by animateColorAsState(
            if (state.focused) Palette.OnFocus else Palette.Label,
            tween(160, easing = Motion.EaseOut),
            label = "subject-row-fg",
        )
        val avatarRing = when {
            current -> if (state.focused) SubjectAvatarRingOnFocus else SubjectAvatarRing
            state.focused -> Palette.OnFocus.copy(alpha = 0.16f)
            else -> Color.Transparent
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MemberAvatar(
                member,
                Modifier
                    .size(36.dp)
                    .border(1.dp, avatarRing, CircleShape),
            )
            BasicText(
                member.displayName,
                modifier = Modifier.weight(1f),
                style = Type.Body.copy(fontSize = 16.sp, color = fg),
            )
            if (!current && MemberSwitchAuth.isExemptNow(member, now)) {
                ExemptBadge(member, focused = state.focused)
            }
            if (current) CurrentMemberBadge(focused = state.focused)
        }
    }
}

/** The interval currently waiving this member's switch password, shown on its row. */
@Composable
private fun ExemptBadge(member: FamilyMember, focused: Boolean) {
    BasicText(
        stringResource(
            R.string.policy_subject_switch_exempt,
            formatMinute(member.switchPinExemptStartMinute),
            formatMinute(member.switchPinExemptEndMinute),
        ),
        style = Type.Caption.copy(
            fontSize = 12.sp,
            color = if (focused) Palette.OnFocus.copy(alpha = 0.62f) else Palette.Green,
        ),
    )
}

/** Marks the member the TV is actually running; the row focus plate is a separate state. */
@Composable
private fun CurrentMemberBadge(focused: Boolean) {
    val fg = if (focused) Palette.OnFocus.copy(alpha = 0.62f) else Palette.Green
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GlyphIcon(Glyph.Check, fg, 12.dp)
        BasicText(
            stringResource(R.string.policy_subject_current),
            style = Type.Caption.copy(fontSize = 12.sp, color = fg),
        )
    }
}

@Composable
private fun MemberAvatar(member: FamilyMember, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(
        initialValue = null,
        member.avatarImage,
        member.avatarRevision,
    ) {
        value = if (member.avatarImage == null) {
            null
        } else {
            withContext(Dispatchers.IO) {
                FamilyAvatarStore.read(context, member)?.let { bytes ->
                    runCatching {
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }.getOrNull()
                }
            }
        }
    }
    val preset = FamilyAvatarStore.canonicalPreset(member.avatarPreset)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(paletteFor(preset).bg),
    ) {
        val bitmap = image
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Image(
                painter = painterResource(FamilyAvatarStore.presetDrawable(preset)),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private class AvatarPalette(
    val bg: Color,
    val face: Color,
    val ear: Color,
    val inner: Color,
    val eye: Color,
    val cheek: Color,
)

private val RabbitPalette = AvatarPalette(
    bg = Color(0xFFF3DDC9),
    face = Color(0xFFF7E7D6),
    ear = Color(0xFFEFC3A8),
    inner = Color(0xFFFFF8EF),
    eye = Color(0xFF3D3029),
    cheek = Color(0xFFE87365).copy(alpha = 0.28f),
)

private val FoxPalette = AvatarPalette(
    bg = Color(0xFFF8DFCA),
    face = Color(0xFFEEB170),
    ear = Color(0xFFD9824A),
    inner = Color(0xFFF9D3AE),
    eye = Color(0xFF4B3021),
    cheek = Color(0xFFE26D4D).copy(alpha = 0.25f),
)

private val BearPalette = AvatarPalette(
    bg = Color(0xFFE9E1D4),
    face = Color(0xFFC99769),
    ear = Color(0xFFA36D45),
    inner = Color(0xFFE4BB8F),
    eye = Color(0xFF3D2C23),
    cheek = Color(0xFFCE665A).copy(alpha = 0.22f),
)

private val PandaPalette = AvatarPalette(
    bg = Color(0xFFE3E8E1),
    face = Color(0xFFFBF8F0),
    ear = Color(0xFF303630),
    inner = Color(0xFFD7DDD5),
    eye = Color(0xFF2F342E),
    cheek = Color(0xFFE88F8F).copy(alpha = 0.22f),
)

private fun paletteFor(preset: String): AvatarPalette = when (preset) {
    "fox" -> FoxPalette
    "bear" -> BearPalette
    "panda" -> PandaPalette
    else -> RabbitPalette
}

/**
 * The same four faces the Web preview approved, on a 64×64 grid. Coordinates mirror the SVG
 * in parent.js so a member looks identical on the TV and the phone.
 */
private fun DrawScope.drawPresetAvatar(preset: String) {
    val p = paletteFor(preset)
    val u = size.minDimension / 64f
    fun point(x: Float, y: Float) = Offset(x * u, y * u)
    fun circle(color: Color, cx: Float, cy: Float, radius: Float) {
        drawCircle(color, radius * u, point(cx, cy))
    }
    fun mouth(yTop: Float, yBottom: Float, width: Float) {
        val path = Path().apply {
            moveTo(29f * u, yTop * u)
            quadraticBezierTo(32f * u, yBottom * u, 35f * u, yTop * u)
        }
        drawPath(path, p.eye, style = Stroke(width = width * u, cap = StrokeCap.Round))
    }

    drawRect(p.bg)
    when (preset) {
        "fox" -> {
            val leftEar = Path().apply {
                moveTo(14f * u, 24f * u)
                lineTo(18f * u, 8f * u)
                lineTo(26f * u, 20f * u)
                close()
            }
            val rightEar = Path().apply {
                moveTo(50f * u, 24f * u)
                lineTo(46f * u, 8f * u)
                lineTo(38f * u, 20f * u)
                close()
            }
            drawPath(leftEar, p.ear)
            drawPath(rightEar, p.ear)
            drawLine(
                p.inner,
                point(18f, 14f),
                point(21f, 22f),
                strokeWidth = 3f * u,
                cap = StrokeCap.Round,
            )
            circle(p.face, 32f, 35f, 18f)
            val brow = Path().apply {
                moveTo(20f * u, 33f * u)
                quadraticBezierTo(32f * u, 23f * u, 44f * u, 33f * u)
            }
            drawPath(brow, p.ear, style = Stroke(width = 3f * u, cap = StrokeCap.Round))
            circle(p.eye, 25f, 35f, 2.1f)
            circle(p.eye, 39f, 35f, 2.1f)
            mouth(41f, 44f, 1.7f)
            circle(p.cheek, 19f, 41f, 3f)
            circle(p.cheek, 45f, 41f, 3f)
        }
        "panda" -> {
            circle(p.ear, 20f, 19f, 10f)
            circle(p.ear, 44f, 19f, 10f)
            circle(p.face, 32f, 35f, 18f)
            rotate(28f, pivot = point(24f, 34f)) {
                drawOval(p.ear, topLeft = point(18f, 27f), size = Size(12f * u, 14f * u))
            }
            rotate(-28f, pivot = point(40f, 34f)) {
                drawOval(p.ear, topLeft = point(34f, 27f), size = Size(12f * u, 14f * u))
            }
            circle(p.inner, 24f, 34f, 2f)
            circle(p.inner, 40f, 34f, 2f)
            drawOval(
                p.eye,
                topLeft = point(29f, 39.8f),
                size = Size(6f * u, 4.4f * u),
            )
            mouth(46f, 48f, 1.4f)
        }
        "bear" -> {
            circle(p.ear, 20f, 19f, 8f)
            circle(p.ear, 44f, 19f, 8f)
            circle(p.face, 32f, 35f, 18f)
            circle(p.ear, 26f, 30f, 2f)
            circle(p.ear, 38f, 30f, 2f)
            circle(p.eye, 25f, 35f, 2.1f)
            circle(p.eye, 39f, 35f, 2.1f)
            mouth(41f, 44f, 1.7f)
            circle(p.cheek, 19f, 41f, 3f)
            circle(p.cheek, 45f, 41f, 3f)
        }
        else -> {
            rotate(-17f, pivot = point(23f, 17f)) {
                drawOval(p.ear, topLeft = point(17f, 5f), size = Size(12f * u, 24f * u))
            }
            rotate(17f, pivot = point(41f, 17f)) {
                drawOval(p.ear, topLeft = point(35f, 5f), size = Size(12f * u, 24f * u))
            }
            rotate(-17f, pivot = point(23f, 18f)) {
                drawOval(p.inner, topLeft = point(20.4f, 11f), size = Size(5.2f * u, 14f * u))
            }
            rotate(17f, pivot = point(41f, 18f)) {
                drawOval(p.inner, topLeft = point(38.4f, 11f), size = Size(5.2f * u, 14f * u))
            }
            circle(p.face, 32f, 35f, 18f)
            circle(p.eye, 25f, 35f, 2.1f)
            circle(p.eye, 39f, 35f, 2.1f)
            mouth(41f, 44f, 1.7f)
            circle(p.cheek, 19f, 41f, 3f)
            circle(p.cheek, 45f, 41f, 3f)
        }
    }
}
