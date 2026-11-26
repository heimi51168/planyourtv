package app.orchard.tv.ui.policy

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orchard.tv.R
import app.orchard.tv.policy.PolicyModeCoordinator
import app.orchard.tv.policy.PolicyProduct
import app.orchard.tv.policy.web.ParentWebCenter
import app.orchard.tv.ui.theme.Glyph
import app.orchard.tv.ui.theme.GlyphIcon
import app.orchard.tv.ui.theme.Palette
import app.orchard.tv.ui.theme.Type
import com.planyourtv.policy.PolicyMode
import com.planyourtv.policy.PolicyRuntime

private val ParentQrPlateSize = 120.dp
private val ParentQrPlatePadding = 10.dp
private val ParentQrPlaceholder = Color(0xFF6E6E73)

internal enum class ParentQrTarget { HIDDEN, LOCAL_WEB, MINI_PROGRAM }

/**
 * Which phone surface the top-right hint advertises. A binding wins over the mode so the hint
 * matches ParentWebAccess.webAllowed(): the local page is blocked while bound, and the mini
 * program is the remaining management surface. A community build without cloud never advertises
 * a mini program, even if a stale bound flag survived a build change.
 */
internal fun parentQrTarget(
    loaded: Boolean,
    mode: PolicyMode,
    bound: Boolean,
    cloudEnabled: Boolean,
    webEnabled: Boolean,
): ParentQrTarget = when {
    !loaded -> ParentQrTarget.HIDDEN
    cloudEnabled && bound -> ParentQrTarget.MINI_PROGRAM
    webEnabled && mode == PolicyMode.LOCAL -> ParentQrTarget.LOCAL_WEB
    else -> ParentQrTarget.HIDDEN
}

/**
 * A compact parent-center QR beside its label, for the two top-right panels a child can open.
 * The code follows the phone surface that actually manages this TV. A LOCAL, unbound TV shows
 * the local web page (same Wi-Fi); a cloud-bound TV shows the Family Check-in mini program,
 * because the local page is read-only there. Cloud mode without a binding and child mode off
 * have no usable phone surface, so nothing is advertised.
 *
 * The plate has a fixed size, so loading and offline states never move the panel. The block is
 * not focusable: the rows above keep the remote's focus chain, and a child cannot "open" it.
 * [load] keeps a warmed but hidden Control Center copy from starting PhoneServer.
 */
@Composable
internal fun ParentCenterQrHint(
    load: Boolean,
    modifier: Modifier = Modifier,
) {
    val coordinator by PolicyModeCoordinator.state.collectAsState()
    val runtime by PolicyRuntime.state.collectAsState()
    val target = parentQrTarget(
        loaded = coordinator.loaded,
        mode = coordinator.mode,
        bound = runtime.bound,
        cloudEnabled = PolicyProduct.cloudEnabled,
        webEnabled = ParentWebCenter.ENABLED,
    )
    if (target == ParentQrTarget.HIDDEN) return
    val remote = target == ParentQrTarget.MINI_PROGRAM
    // The mini program code is static; only the local page needs PhoneServer and its status.
    val web = rememberParentWebQr(
        scale = 8,
        enabled = load && target == ParentQrTarget.LOCAL_WEB,
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // The white plate is also the QR quiet zone: PhoneServer.qr draws with MARGIN 0, and
        // the mini-program image already carries its own white margin.
        Box(
            modifier = Modifier
                .size(ParentQrPlateSize)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(ParentQrPlatePadding),
            contentAlignment = Alignment.Center,
        ) {
            if (remote) {
                // The same asset the parent center's remote-management pane shows.
                Image(
                    painter = painterResource(R.drawable.policy_mini_program_qr),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                when {
                    web.qr.bitmap != null -> Image(
                        bitmap = web.qr.bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        // The bitmap is generated above the on-screen size, so it is reduced here.
                        filterQuality = FilterQuality.Low,
                        modifier = Modifier.fillMaxSize(),
                    )
                    web.qr.loading -> BasicText(
                        "…",
                        style = Type.Caption.copy(fontSize = 16.sp, color = ParentQrPlaceholder),
                    )
                    else -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        GlyphIcon(Glyph.Offline, ParentQrPlaceholder, 22.dp)
                        BasicText(
                            stringResource(R.string.policy_parent_qr_offline),
                            style = Type.Caption.copy(
                                fontSize = 9.sp,
                                color = ParentQrPlaceholder,
                                textAlign = TextAlign.Center,
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        Column(Modifier.weight(1f)) {
            BasicText(
                stringResource(R.string.cc_parent_control),
                style = Type.Body,
            )
            Spacer(Modifier.height(4.dp))
            BasicText(
                stringResource(
                    if (remote) R.string.policy_parent_qr_remote_hint
                    else R.string.policy_parent_qr_hint,
                ),
                style = Type.Caption.copy(color = Palette.SecondaryLabel),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!remote) {
                Spacer(Modifier.height(4.dp))
                ParentWebStatusCompact(web.status)
            }
        }
    }
}
