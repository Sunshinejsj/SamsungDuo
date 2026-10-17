package com.jsj.samsungduo.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.pm.PackageInfoCompat
import com.jsj.samsungduo.SamsungDuoApp
import com.jsj.samsungduo.Prefs
import com.jsj.samsungduo.R
import com.jsj.samsungduo.TrioAppearance
import com.jsj.samsungduo.TrioPreviewView
import com.jsj.samsungduo.TrioSettings
import io.github.libxposed.service.XposedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONObject
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.ColorPalette
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TooltipBox
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.NavDisplayEffects
import top.yukonga.miuix.kmp.nav.core.NavKey
import top.yukonga.miuix.kmp.nav.core.rememberNavController
import top.yukonga.miuix.kmp.nav.transition.NavMotion
import top.yukonga.miuix.kmp.nav.transition.NavSettleSpec
import top.yukonga.miuix.kmp.nav.transition.NavTransition
import top.yukonga.miuix.kmp.nav.transition.navGraphicsTransition
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.shader.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * Inner padding of a preference row inside its Card. Miuix's Card carries no
 * outer margin and its preferences default to a uniform 16.dp box; the MIUI look
 * wants 18.dp against the card edge and a slightly tighter 14.dp vertical rhythm.
 */
/** 列表项的横向内边距；与 [SettingsItemMargin] 同源，卡片里的文字/按钮用它对齐。 */
private val SettingsItemInset = 18.dp
private val SettingsItemMargin = PaddingValues(horizontal = SettingsItemInset, vertical = 14.dp)

/**
 * Blur radius of the top bar, in dp.
 *
 * Well above the library's 20.dp default because the bar is as tall as the
 * status bar plus a large title: at a small radius the rows underneath stay
 * legible and the bar reads as a translucent scrim rather than frosted glass.
 */
private val BarBlurRadius = 40f

/**
 * Group heading. Miuix's [SmallTitle] defaults to a 28.dp vertical margin, which
 * reads as a stray indent once the page itself only insets 12.dp; the reference
 * page narrows it to 18.dp so the heading lines up with the card padding instead
 * of the card edge.
 */
@Composable
private fun SectionTitle(title: String) {
    SmallTitle(
        text = title,
        modifier = Modifier.padding(top = 4.dp),
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
    )
}

/** Which role colour the picker dialog is editing. */
private enum class RoleColor(val labelRes: Int) {
    Low(R.string.color_low_title),
    Charging(R.string.color_charging_title),
    Critical(R.string.color_critical_title),
}

/**
 * The page's top-level sections, in strip order. The label reuses the section
 * heading the group already had, so the strip reads as a table of contents for
 * the page rather than a second vocabulary for the same four things.
 *
 * Each section is also a destination on the nav back stack ([route]), which is
 * what gives a section switch its own animated page change and gives the system
 * back gesture something to pop. The routes' names and the strip's names are the
 * same four words, but they are separate types on purpose: the label is a
 * resource id, which has no business living on a value that gets serialized into
 * the saved-state bundle.
 */
private enum class SettingsTab(val labelRes: Int, val route: Route) {
    General(R.string.tab_general, Route.General),
    Geometry(R.string.group_geometry, Route.Geometry),
    Colors(R.string.group_colors, Route.Colors),
    About(R.string.about_title, Route.About),
}

/**
 * Destinations of the settings back stack, one per section.
 *
 * Serialized data objects rather than a plain enum or class: miuix-nav keeps its
 * stack in rememberSaveable and rebuilds it through a serializer, so every route
 * has to round-trip. It also namespaces each route's saved state by the route's
 * own toString(), and an object's toString() is its class name — stable across a
 * process death, unlike the identity hash a plain class would print.
 *
 * The on-screen state that is not a place — the colour picker, the restart sheet,
 * the reset prompt — deliberately stays a dialog. Those are prompts dismissed
 * over the page, and pushing them would turn the back gesture into "cancel"
 * instead of "go back a section".
 */
@Serializable
private sealed interface Route : NavKey {
    @Serializable
    data object General : Route

    @Serializable
    data object Geometry : Route

    @Serializable
    data object Colors : Route

    @Serializable
    data object About : Route
}

/**
 * The section change: the two sections cross-fade where they stand.
 *
 * The library's default preset moves a page — the entering section slides in
 * full-width from the trailing edge, the outgoing one parallaxes a quarter width
 * the other way, and the effects layer lays a 50% scrim over the lot. That is the
 * right motion for arriving at a new screen and the wrong one here: the four tabs
 * are four views of one page, and the strip above them never moves. Sliding them
 * sideways reads as the page tearing in half.
 *
 * So this transition moves nothing. It only ramps the incoming layer's alpha up
 * from 0 over the outgoing one, which stays fully opaque until it is covered. The
 * two are the same size and sit in the same place, so the alpha alone reads as one
 * section turning into the next.
 *
 * `relativeDepth` is the single driver: 0 at rest on top, -1 fully out above it,
 * +1 fully covered by the layer above. Both directions therefore come out
 * symmetric — a push fades the new section in over the old, a pop fades the
 * revealed section back in the same way — with no per-direction branch.
 *
 * The covered branch still assigns alpha explicitly rather than leaving it alone:
 * the block runs inside a retained `graphicsLayer`, so a property it stops setting
 * keeps the value from the frame before — which would freeze the layer at the alpha
 * of its last entering frame instead of letting it read as opaque.
 */
private val SectionTransition: NavTransition = navGraphicsTransition(
    opaqueDepth = 1f,
    // 220ms rather than the preset's 500ms: a section swap is a glance sideways,
    // not a journey, and the default was tuned for a full page push.
    motion = NavMotion(programmatic = NavSettleSpec.Tween(220, FastOutSlowInEasing)),
) { scope ->
    val d = scope.relativeDepth
    alpha = if (d <= 0f) (1f + d).coerceIn(0f, 1f) else 1f
}

/**
 * The transition above draws no scrim and rounds no corners, because there is
 * nothing to separate: no layer ever sits on top of another here, and the corner
 * clip exists to round the edge of a page sliding across the screen.
 */
private val SectionEffects = NavDisplayEffects(
    enableCornerClip = false,
    dimAmount = 0f,
)

@Composable
fun SettingsScreen(repository: SettingsRepository) {
    // Kept as explicit state objects, not just `by` delegates, because the nav
    // entries below are built once and remembered: an entry that closed over the
    // *value* of settings would keep rendering the snapshot it was built with.
    // Handing the entries these objects makes every read a real snapshot read,
    // taken while the destination composes.
    val settingsState = remember { mutableStateOf(repository.read()) }
    var settings by settingsState
    val serviceState = remember { mutableStateOf(SamsungDuoApp.xposedService) }
    var service by serviceState
    var editing by remember { mutableStateOf<RoleColor?>(null) }
    var resettingColors by remember { mutableStateOf(false) }
    var restarting by remember { mutableStateOf(false) }

    // The section the page is on *is* the top of the back stack, rather than a
    // second piece of state kept in step with it: the strip and the system back
    // gesture then read and write the same list, so they cannot disagree about
    // where the user is.
    //
    // The supertype is spelled out because the reified parameter is otherwise
    // inferred from the argument, and a stack declared as the first concrete
    // route cannot hold the other three once the process is recreated.
    val nav = rememberNavController<Route>(Route.General)

    // The framework service can bind or die at any moment; both the status row
    // and every remote write have to follow it.
    DisposableEffect(repository) {
        val listener: (XposedService?) -> Unit = { service = it }
        SamsungDuoApp.addServiceListener(listener)
        onDispose { SamsungDuoApp.removeServiceListener(listener) }
    }

    val context = LocalContext.current

    // 模块是否真的注入到 System UI。`xposedService != null` 只代表 LSPosed 管理器在线，
    // 不代表本模块被勾选并作用于 com.android.systemui；判定见 rememberModuleActive()。
    val activated = rememberModuleActive()

    // 顶部预览卡 + 四个 tab 在列表里的底边（根坐标）。锁定拦截层从这条线往下盖，
    // 保证 tab 与预览卡永远不被拦，且滚动时跟着走。
    val headerBottomPx = remember { mutableStateOf(0f) }

    // Same reasoning as the preview above: the dialog can be dismissed mid-kill, and a
    // root prompt that outlives the dialog still has to land somewhere.
    val restarter = remember { RestartController() }
    DisposableEffect(restarter) { onDispose { restarter.dispose() } }

    // ★ v85：更新控制器提到屏幕级，不放进关于页的 item 里 —— LazyColumn 会把划出
    //   屏幕的 item 销毁，而下载不该因为用户切了个 tab 就中断。
    val updater = remember(context) { UpdateController(context) }
    DisposableEffect(updater) { onDispose { updater.dispose() } }

    // Single funnel: apply the write, then re-read so the preview never shows a
    // value the repository did not accept (clamping happens on read).
    fun update(block: (SettingsRepository) -> Unit) {
        block(repository)
        settings = repository.read()
    }

    // Switching section: tapping the one already open is a no-op rather than a
    // second copy of it on the stack — the library rejects a repeated route, and
    // the strip accepts taps faster than a transition runs. Going back to a
    // section further down walks back to it instead of pushing it again, so the
    // stack stays a path through the page rather than a log of every tap.
    fun selectTab(next: SettingsTab) {
        val backStack = nav.backStack
        if (backStack.lastOrNull() == next.route) return
        if (backStack.contains(next.route)) {
            nav.popUntil { it == next.route }
        } else {
            nav.push(next.route)
        }
    }

    // The preview and the strip are the head of every section rather than a band
    // pinned above the display. They then scroll away with the rows they belong
    // to, which is how this page behaved before it had a back stack, and the
    // display has no reason to reshape the page to run a transition over it.
    //
    // Being the same two rows in all four sections, they also cost the transition
    // nothing: two copies fading through one another land on identical pixels.
    fun LazyListScope.sectionHeader() {
        item { PreviewCard(settingsState.value) }
        item {
            // Read here rather than taken from the enclosing scope: the entries
            // are built once and remembered, so a value captured above would keep
            // highlighting whichever section was open when the screen appeared.
            val current = SettingsTab.entries.firstOrNull { it.route == nav.backStack.lastOrNull() }
                ?: SettingsTab.General
            val labels = SettingsTab.entries.map { stringResource(it.labelRes) }
            TabRow(
                tabs = labels,
                selectedTabIndex = current.ordinal,
                onTabSelected = { index -> selectTab(SettingsTab.entries[index]) },
                modifier = Modifier
                    .fillMaxWidth()
                    // 记录 tab 行的底边，供锁定拦截层定位（见 SectionList）。
                    .onGloballyPositioned { headerBottomPx.value = it.boundsInRoot().bottom },
            )
        }
    }

    val scrollBehavior = MiuixScrollBehavior(rememberTopAppBarState())
    // Resolved here rather than inside the click lambda: a string resource needs
    // a composable context, and the dialog is opened from a lambda.
    val restartTitle = stringResource(R.string.restart_title)
    val restartDone = stringResource(R.string.restart_done)

    // Miuix's own host rather than a system Toast: the snackbar is drawn on the
    // same surface as the page that raised it, so it follows the theme and dies
    // with the screen instead of hovering over whatever comes next.
    //
    // Short (4s) rather than Long (10s): these messages are one short
    // sentence, and ten seconds is a long time to look at one. The user can
    // swipe the bar away if even four is too long.
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // Remembered so the row callbacks below capture one stable value: a lambda
    // rebuilt every recomposition would make every row that takes it unstable.
    val showMessage: (String) -> Unit = remember(scope, snackbarHostState) {
        { message ->
            scope.launch {
                snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
            }
            Unit
        }
    }

    // ★ v104：进 App 自动查一次更新。查询本身是静默的 —— 只有确实查到新版本才会
    // 冒出来（见 UpdateController.autoCheck），查不到/网络不通都当没问过。
    LaunchedEffect(updater) { updater.autoCheck() }

    // 只提示一次：state 之后还会走到 Downloading / Ready，不记一笔就会反复弹同一条。
    var updateAnnounced by remember { mutableStateOf(false) }
    LaunchedEffect(updater.state) {
        if (updateAnnounced) return@LaunchedEffect
        val found = updater.state as? UpdateState.Available ?: return@LaunchedEffect
        updateAnnounced = true
        showMessage(context.getString(R.string.update_found, found.info.version, found.current))
    }

    // The kill lands on System UI, not on this process, so the confirmation
    // outlives the dialog and has to be shown from here.
    LaunchedEffect(restarter.state) {
        if (restarter.state is RestartState.Done) {
            showMessage(restartDone)
            restarting = false
        }
    }

    // Hoisted out of the draw lambda below, which is not a composable scope and
    // so cannot read the theme itself.
    val surfaceColor = MiuixTheme.colorScheme.surface

    // Blur needs a runtime shader (API 33). The library skips the effect itself
    // on older devices, but that is not enough here: the blur is what replaces
    // the bar's background, and the bar applies the modifier before that
    // background, so an un-gated bar would end up with no background at all and
    // the list would scroll straight through the title.
    val barBlurSupported = isRuntimeShaderSupported()

    // Remembered unconditionally: a conditional remember would break the slot
    // table, and the layer costs nothing while nothing samples it.
    //
    // Keyed on the colour alone; rememberLayerBackdrop reads the draw lambda
    // through rememberUpdatedState, so a fresh lambda per frame does not rebuild
    // the backdrop and reset its coordinates.
    val barBackdrop = rememberLayerBackdrop {
        // The captured layer has to be opaque first. The page leaves its margins
        // and the gaps between cards transparent, and blurring those pixels
        // smears their neighbours' colour across the whole bar.
        drawRect(surfaceColor)
        drawContent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = stringResource(R.string.app_name),
                // The blur is drawn by the modifier, which the bar applies before
                // its own background, so the background has to get out of the
                // way for the effect to be visible at all.
                modifier = if (barBlurSupported) {
                    Modifier.textureBlur(
                        backdrop = barBackdrop,
                        // The bar is flush with the screen edges, so there is no
                        // corner for a rounded shape to round off.
                        shape = RectangleShape,
                        blurRadius = BarBlurRadius,
                    )
                } else {
                    Modifier
                },
                color = if (barBlurSupported) Color.Transparent else surfaceColor,
                largeTitle = stringResource(R.string.app_name),
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        // 未激活 → 只提示去 LSPosed 勾选；已激活但无 ROOT → 提示授权；
                        // 两者都满足才真正进入重启流程。
                        onClick = {
                            when {
                                !activated -> showMessage(context.getString(R.string.lsposed_hint))
                                !isRootAvailable() ->
                                    showMessage(context.getString(R.string.root_required))
                                else -> {
                                    restarter.reset()
                                    restarting = true
                                }
                            }
                        },
                    ) {
                        Icon(imageVector = MiuixIcons.Refresh, contentDescription = restartTitle)
                    }
                },
            )
        },
        contentWindowInsets = WindowInsets.statusBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val sectionPadding = PaddingValues(
            start = 12.dp,
            // The bar's inset belongs to the list now that the header is its first
            // row: the preview used to be laid out outside the display, where the
            // Scaffold's padding reached it.
            top = padding.calculateTopPadding() + 12.dp,
            end = 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp,
        )

        // The body of the page: one destination per section, each with its own
        // list. Nothing is pinned above it, so the page keeps the shape it had
        // before there was any navigation at all — the preview and the strip are
        // simply the first two rows of whichever section is open.
        //
        // The default transition and its effects are replaced wholesale: they
        // are built for arriving at a page, and these four entries are four
        // views of one page. See [SectionTransition].
        NavDisplay(
            backStack = nav.backStack,
            modifier = Modifier
                .fillMaxSize()
                // Both modifiers reach down into the display rather than
                // sitting on a wrapper: the blur samples all of it, and the
                // scroll listener has to be above the list — which lives one
                // level down inside a destination — to hear the scroll that
                // collapses the bar.
                .layerBackdrop(barBackdrop)
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            onBack = { nav.pop() },
            transition = SectionTransition,
            effects = SectionEffects,
        ) {
            // Every entry reads settingsState/serviceState rather than a value
            // captured here: the provider is remembered, so an entry built with a
            // snapshot of the settings would go on showing that snapshot.
            entry<Route.General> {
                SectionList(
                    contentPadding = sectionPadding,
                    isTop = nav.backStack.lastOrNull() == Route.General,
                    locked = !activated,
                    headerBottom = headerBottomPx,
                    onLockedTap = { showMessage(context.getString(R.string.lsposed_hint)) },
                ) {
                    sectionHeader()
                    val s = settingsState.value
                    generalTab(s) { block -> update(block) }
                }
            }

            entry<Route.Geometry> {
                SectionList(
                    contentPadding = sectionPadding,
                    isTop = nav.backStack.lastOrNull() == Route.Geometry,
                    locked = !activated,
                    headerBottom = headerBottomPx,
                    onLockedTap = { showMessage(context.getString(R.string.lsposed_hint)) },
                ) {
                    sectionHeader()
                    val s = settingsState.value
                    geometryTab(s) { block -> update(block) }
                }
            }

            entry<Route.Colors> {
                SectionList(
                    contentPadding = sectionPadding,
                    isTop = nav.backStack.lastOrNull() == Route.Colors,
                    locked = !activated,
                    headerBottom = headerBottomPx,
                    onLockedTap = { showMessage(context.getString(R.string.lsposed_hint)) },
                ) {
                    sectionHeader()
                    val s = settingsState.value
                    colorsTab(
                        settings = s,
                        update = { block -> update(block) },
                        onEdit = { role -> editing = role },
                        onReset = { resettingColors = true },
                    )
                }
            }

            entry<Route.About> {
                // 关于页永不限制：locked 恒为 false。
                SectionList(
                    contentPadding = sectionPadding,
                    isTop = nav.backStack.lastOrNull() == Route.About,
                    locked = false,
                    headerBottom = headerBottomPx,
                    onLockedTap = {},
                ) {
                    sectionHeader()
                    val s = settingsState.value
                    aboutTab(s, serviceState.value, updater) { block ->
                        update(block)
                    }
                }
            }
        }

        // Must stay inside the Scaffold: MiuixPopupHost reads LocalRootDialogStates,
        // which Scaffold provides. Composed outside this lambda the dialog would
        // register itself on a list nothing ever renders, and silently never appear.
        val current = editing
        if (current != null) {
            ColorDialog(
                role = current,
                settings = settings,
                onPick = { onDark, argb ->
                    update { repo ->
                        when (current) {
                            RoleColor.Critical ->
                                if (onDark) repo.setColorCriticalOnDark(argb)
                                else repo.setColorCriticalOnLight(argb)

                            RoleColor.Low ->
                                if (onDark) repo.setColorLowOnDark(argb)
                                else repo.setColorLowOnLight(argb)

                            RoleColor.Charging ->
                                if (onDark) repo.setColorChargingOnDark(argb)
                                else repo.setColorChargingOnLight(argb)
                        }
                    }
                },
                onDismiss = { editing = null },
            )
        }

        RestartDialog(
            controller = restarter,
            show = restarting,
            onDismiss = { restarting = false },
        )

        ResetColorsDialog(
            show = resettingColors,
            onDismiss = { resettingColors = false },
            onConfirm = {
                update { it.resetRoleColors() }
                resettingColors = false
            },
        )
    }
}

/**
 * The scrolling body of one destination: the shared header and then the rows of
 * whichever section this is.
 *
 * Each destination carries its own copy of the header — the preview and the strip
 * are the same two rows everywhere — because they have to scroll with the list
 * they belong to. A band pinned above the display would not: it would have to sit
 * outside every destination, which is a different page shape from the one the rows
 * were written for.
 *
 * The rows arrive as a [LazyListScope] builder rather than a composable so each
 * section keeps contributing plain `item {}`s: the list owns their identity and
 * therefore their state, and the shape of the four `*Tab` functions is unchanged.
 *
 * [isTop] is whether this destination is the section currently on screen — the top
 * of the back stack. It gates the reset to the first row, so a section opens at the
 * top every time the strip brings it forward rather than resuming where it was left.
 */
@Composable
private fun SectionList(
    contentPadding: PaddingValues,
    isTop: Boolean,
    locked: Boolean,
    headerBottom: androidx.compose.runtime.State<Float>,
    onLockedTap: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    // A section is a place the reader arrives at, not a document they come back to:
    // opening one starts at its first row. The offset therefore belongs to the visit
    // and is not carried across — the entry stays composed while it is covered, so a
    // list that kept its position would drop the reader into the middle of a section
    // they had just chosen from the strip.
    //
    // Keyed on [isTop] rather than run once, because the same composed list is
    // covered and uncovered as sections change; the reset has to happen on each
    // arrival, not on the composition that happened to create it.
    val listState = rememberLazyListState()
    LaunchedEffect(isTop) {
        if (isTop) listState.scrollToItem(0)
    }

    // ★ LSPosed 未激活时：不盖任何覆盖层，只在 LazyColumn 自己的 modifier 上挂一个手势闸门。
    //   闸门工作在 Initial 阶段（父节点先于子节点收到事件），因此能拦住控件的「抬起」，
    //   又完全不碰「按下 / 移动」，滚动与惯性全归列表自己处理 —— 锁定时照样能上下浏览。
    //   四个 tab 与预览卡在 headerBottom 之上，闸门直接放过那一块，永远可点。
    //   locked = false 时这个 modifier 不挂，行为与 v108 完全一致。
    var boxTop by remember { mutableStateOf(0f) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { boxTop = it.boundsInRoot().top },
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                // Opaque, because a destination here is a whole page. The section being
                // left stays composed and keeps being drawn underneath for the length of
                // the transition — the visible window is `-1 < d <= 1`, deliberately wide
                // enough to cross-fade from. Nothing else hides it: both lists are the same
                // size and sit in the same place, so the covered one is covered only by the
                // pixels the top one actually paints. A transparent list therefore lets the
                // previous section show through the gaps between cards and below the end of
                // a shorter section, which reads as the old tab lying under the new one.
                //
                // The same colour the Scaffold paints, which was doing this job alone while
                // there was only ever one list to paint.
                .background(MiuixTheme.colorScheme.surface)
                .lockGate(
                    locked = locked,
                    headerBottom = { (headerBottom.value - boxTop).coerceAtLeast(0f) },
                    onTap = onLockedTap,
                ),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            content()
        }
    }
}

/**
 * 锁定态的手势闸门：只拦「按」，不拦「滚」。
 *
 * <p>挂在 LazyColumn 自己的 modifier 上，工作在 [PointerEventPass.Initial]（父节点先于子节点
 * 拿到事件），因此：
 * <ul>
 *   <li>按下、移动一概不消费 —— 滚动、惯性、列表的手势全归列表自己；</li>
 *   <li>点按抬起时消费 —— 子控件的 clickable 收不到 up，开关/按钮不会触发，同时弹提示；</li>
 *   <li>横向拖动消费 —— 滑块拖不动；纵向一旦超过 slop 就退出，交给滚动。</li>
 * </ul>
 * 预览卡与四个 tab 位于 [headerBottom] 之上，直接放过，永远可点。
 */
private fun Modifier.lockGate(
    locked: Boolean,
    headerBottom: () -> Float,
    onTap: () -> Unit,
): Modifier = if (!locked) this else pointerInput(onTap) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        if (down.position.y < headerBottom()) return@awaitEachGesture
        var tap = true
        var horizontal: Boolean? = null
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                if (tap) {
                    change.consume()
                    onTap()
                }
                break
            }
            val dx = kotlin.math.abs(change.position.x - down.position.x)
            val dy = kotlin.math.abs(change.position.y - down.position.y)
            if (tap && (dx > slop || dy > slop)) {
                tap = false
                horizontal = dx > dy
            }
            when (horizontal) {
                true -> change.consume()
                false -> break
                null -> Unit
            }
        }
    }
}

/**
 * General: the master and appearance switches, then the advanced rows.
 * Advanced has no tab of its own, so it folds in here.
 */
private fun LazyListScope.generalTab(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    item { SectionTitle(stringResource(R.string.group_appearance)) }
    item {
        Card {
            // The same appearance the renderer builds from these settings, so a
            // row here and the glyph on screen can never disagree about what is
            // switched on - which is what used to let the two drift apart.
            val a = TrioAppearance.of(settings)
            SwitchPreference(
                checked = settings.enabled,
                onCheckedChange = { value -> update { it.setEnabled(value) } },
                title = stringResource(R.string.master_title),
                summary = stringResource(R.string.master_summary),
                insideMargin = SettingsItemMargin,
            )
            // 0 = the ring arrangement, 1 = the rectangular one; the option list
            // is ordered so its index is exactly the stored value.
            //
            // Sits directly under the master switch rather than with the sliders:
            // it decides which arrangement every other row below applies to, and
            // it is the one setting here that is a choice of layout rather than
            // of content. Gated on the master switch only - the arrangement is
            // still worth picking while the icon itself is off, and nothing here
            // depends on Wi-Fi or the percentage being shown.
            val styles = listOf(
                stringResource(R.string.trio_style_ring),
                stringResource(R.string.trio_style_rect),
            )
            OverlayDropdownPreference(
                items = styles,
                selectedIndex = a.style,
                title = stringResource(R.string.trio_style_title),
                summary = stringResource(R.string.trio_style_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
                onSelectedIndexChange = { index -> update { repo -> repo.setTrioStyle(index) } },
            )
            // Where the glyph is drawn, not what it draws. The status bar window
            // is only about 43dp tall and clips the rest, so this is the switch
            // between "whole glyph, in a window of its own" and "inside the bar,
            // cut down to the bar's height".
            SwitchPreference(
                checked = settings.overlayGlyph,
                onCheckedChange = { value -> update { it.setOverlay(value) } },
                title = stringResource(R.string.overlay_title),
                summary = stringResource(R.string.overlay_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            SwitchPreference(
                checked = a.wifi,
                onCheckedChange = { value -> update { it.setShowWifi(value) } },
                title = stringResource(R.string.show_wifi_title),
                summary = stringResource(R.string.show_wifi_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            SwitchPreference(
                checked = a.mobile,
                onCheckedChange = { value -> update { it.setShowMobile(value) } },
                title = stringResource(R.string.show_mobile_title),
                summary = stringResource(R.string.show_mobile_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            // Sits directly under the mobile meter because it only ever reshapes
            // that meter: one row per SIM instead of one row for the data SIM.
            // Gated on showMobile for the same reason the bolt row is gated on
            // showValue - with no meter there is nothing for this to reshape.
            val mobileGate = gateHint(
                a.glyph to R.string.master_title,
                a.mobile to R.string.show_mobile_title,
            )
            TooltipBox(text = mobileGate.orEmpty(), enabled = mobileGate != null) {
                SwitchPreference(
                    checked = a.dualSim,
                    onCheckedChange = { value -> update { it.setDualSim(value) } },
                    title = stringResource(R.string.dual_sim_title),
                    summary = stringResource(R.string.dual_sim_summary),
                    insideMargin = SettingsItemMargin,
                    enabled = a.glyph && a.mobile,
                )
            }
            SwitchPreference(
                checked = a.value,
                onCheckedChange = { value -> update { it.setShowValue(value) } },
                title = stringResource(R.string.show_value_title),
                summary = stringResource(R.string.show_value_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            // The three rows below are the out-of-ring reading. The first is a
            // position rather than a switch, and the other two are switches that
            // only mean anything once the reading has actually moved out of the
            // ring - which is why both of them name that position as their gate.
            //
            // Stored values are 0 = inside the ring, 1 = outside it, so the
            // option list is ordered so its index is exactly the stored value.
            val signalModes = listOf(
                stringResource(R.string.signal_mode_inside),
                stringResource(R.string.signal_mode_outside),
            )
            OverlayDropdownPreference(
                items = signalModes,
                selectedIndex = a.signalMode,
                title = stringResource(R.string.signal_mode_title),
                summary = stringResource(R.string.signal_mode_summary),
                insideMargin = SettingsItemMargin,
                // Gated on the master switch only: the position is still worth
                // picking while the icon is off, and it is the reading's own
                // switch, not the dots', so the mobile row above must not hold
                // it shut.
                enabled = a.glyph,
                onSelectedIndexChange = { index -> update { repo -> repo.setSignalMode(index) } },
            )
            // Every gate here is another row, so the hint can always name the
            // switch that is holding it shut. Self-gating would be unrecoverable:
            // the rule would go false when the switch is turned off, and the row
            // would disable before it could be turned back on.
            val stackedGate = gateHint(
                a.glyph to R.string.master_title,
                a.signalOutOfRing to R.string.signal_mode_title,
            )
            TooltipBox(text = stackedGate.orEmpty(), enabled = stackedGate != null) {
                SwitchPreference(
                    checked = a.stackedSignal,
                    onCheckedChange = { value -> update { it.setStackedSignal(value) } },
                    title = stringResource(R.string.stacked_signal_title),
                    summary = stringResource(R.string.stacked_signal_summary),
                    insideMargin = SettingsItemMargin,
                    enabled = a.glyph && a.signalOutOfRing,
                )
            }
            // Needs the reading to be in the out-of-ring form before there is a
            // second reading to hide: turned off, the native icon shows both SIMs
            // on its own and this row could not deliver what it promises. The
            // stacked switch is named rather than the rule's own outcome
            // (stackedOut), so the hint points at the row the user can act on.
            val dataSimGate = gateHint(
                a.glyph to R.string.master_title,
                a.signalOutOfRing to R.string.signal_mode_title,
                a.stackedSignal to R.string.stacked_signal_title,
            )
            TooltipBox(text = dataSimGate.orEmpty(), enabled = dataSimGate != null) {
                SwitchPreference(
                    checked = a.dataSimOnly,
                    onCheckedChange = { value -> update { it.setDataSimOnly(value) } },
                    title = stringResource(R.string.data_sim_only_title),
                    summary = stringResource(R.string.data_sim_only_summary),
                    insideMargin = SettingsItemMargin,
                    enabled = a.glyph && a.signalOutOfRing && a.stackedSignal,
                )
            }
            // The rows below inherit the master gate, but the ones with a second
            // dependency explain the specific switch that is holding them shut:
            // "off because the master is off" is already visible on screen.
            //
            // The gate is the percentage switch, and it has to be that switch
            // rather than {@link TrioAppearance#drawsBolt}. drawsBolt() folds in
            // this very row's own value, so gating on it makes the switch lock
            // itself: turn the bolt off and "the rule would draw a bolt" goes
            // false, the row disables, and it can never be turned back on.
            // A row may be gated by other switches but never by its own.
            // ★ v100：闪电开关独立化 —— 只受总开关(glyph)门控，不再依附"电量数字"。
            //   此前 enabled = a.glyph && a.value 且 gateHint 里含 a.value：
            //   关掉"电量数字"后这个开关被禁用，用户"关电量后闪电按钮就废了"。
            //   现已解耦（TrioAppearance.drawsBolt() 只认 boltWanted）：关掉电量数字，
            //   闪电仍可单独画在 12 点充电缺口（渲染层 r.bolt && !chargePair 分支）。
            val boltGate = gateHint(
                a.glyph to R.string.master_title,
            )
            TooltipBox(text = boltGate.orEmpty(), enabled = boltGate != null) {
                SwitchPreference(
                    checked = a.boltWanted,
                    onCheckedChange = { value -> update { it.setShowBolt(value) } },
                    title = stringResource(R.string.show_bolt_title),
                    summary = stringResource(R.string.show_bolt_summary),
                    insideMargin = SettingsItemMargin,
                    enabled = a.glyph,
                )
            }
            // 0 = hidden, 1 = inside the ring, 2 = drawn outside the ring; the
            // option list is ordered so its index is exactly the stored value.
            //
            // Gated on the master switch only, same as the bolt row above since
            // v100 (both dropped their showValue dependency): the renderer picks
            // the in-ring type centre slot without consulting showValue
            // (TrioRenderer typeInCentre), and the out-of-ring label is a separate
            // view entirely. Requiring the percentage would leave this row
            // unreachable for no reason.
            val typeModes = listOf(
                stringResource(R.string.mobile_type_off),
                stringResource(R.string.mobile_type_inside),
                stringResource(R.string.mobile_type_outside),
            )
            OverlayDropdownPreference(
                items = typeModes,
                selectedIndex = a.typeMode,
                title = stringResource(R.string.show_mobile_type_title),
                summary = stringResource(R.string.show_mobile_type_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
                onSelectedIndexChange = { index -> update { repo -> repo.setMobileTypeMode(index) } },
            )
            // The centred-percentage row has three dependencies, and the style is
            // one of them: the rectangle has no notch, so
            // {@code TrioAppearance.centreValue} is false there whatever the
            // stored switch says. Gating on that rule - rather than on
            // {@code settings.valueCentred && !a.rect} - keeps the reason in one
            // place. It is also the one intended behaviour change of this
            // refactor: under the rectangle the row is now plainly unavailable
            // instead of silently doing nothing.
            val centreHint = gateHint(
                a.glyph to R.string.master_title,
                !a.rect to R.string.trio_style_ring,
                a.wifi to R.string.show_wifi_title,
                a.value to R.string.show_value_title,
            )
            TooltipBox(text = centreHint.orEmpty(), enabled = centreHint != null) {
                SwitchPreference(
                    checked = settings.valueCentred,
                    onCheckedChange = { value -> update { it.setValueCentred(value) } },
                    title = stringResource(R.string.value_centre_title),
                    summary = stringResource(R.string.value_centre_summary),
                    insideMargin = SettingsItemMargin,
                    // The arcs have to be drawn for "move them into the notch" to
                    // mean anything, and the percentage has to be drawn for it to
                    // be centred at all.
                    enabled = a.glyph && !a.rect && a.wifi && a.value,
                )
            }
        }
    }
}

/** Geometry: the stroke, text, network-type and track sliders. */
private fun LazyListScope.geometryTab(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    // Same rule the renderer builds, so the two style-dependent labels below -
    // the bar thickness, and the arc's vertical placement in the top slot -
    // follow the arrangement actually on screen instead of a second copy of it.
    val a = TrioAppearance.of(settings)

    // ★ v103：「整体尺寸」的上限跟着「解除尺寸限制」走（用户定的规则，一个开关两档）——
    //   关着 = 60–100%，开着 = 60–130%。三处都挂在状态栏行里参与排布（树模式），
    //   不再有独立悬浮窗口（v102 试过：脱离树就不抢位子，会遮挡原生图标，已彻底放弃）。
    //   这里 coerceIn 兜住"刚关掉开关、存储里还留着更大值"的那一瞬。
    val glyphScaleCap = if (settings.overlayGlyph) {
        Prefs.MAX_GLYPH_SCALE
    } else {
        Prefs.MAX_GLYPH_SCALE_TREE
    }
    // The overall size gets its own group rather than a row inside the stroke
    // card: it scales every stroke, arc, digit and dot at once, so it is not one
    // more width among the others. 100% is the shipped look, and the ceiling is
    // the box the system reserves - there is nothing above it to grow into.
    item { SectionTitle(stringResource(R.string.group_geometry)) }
    item {
        Card {
            IntSlider(
                value = settings.glyphScale.coerceIn(
                    Prefs.MIN_GLYPH_SCALE, glyphScaleCap),
                min = Prefs.MIN_GLYPH_SCALE,
                max = glyphScaleCap,
                title = stringResource(R.string.glyph_scale_title),
                // ★ v80：上限跟着「独立窗口绘制」走，说明文字也跟着换 ——
                //   关着时还写着"可调 60–130%"就是在骗人。
                summary = stringResource(
                    if (settings.overlayGlyph) {
                        R.string.glyph_scale_summary
                    } else {
                        R.string.glyph_scale_summary_capped
                    }),
                valueTextSuffix = "%",
                enabled = a.glyph,
                onValueChange = { v -> update { it.setGlyphScale(v) } },
            )
        }
    }
    item { SectionTitle(stringResource(R.string.group_stroke)) }
    item {
        Card {
            IntSlider(
                value = settings.ringStroke,
                min = Prefs.MIN_RING_STROKE,
                max = Prefs.MAX_RING_STROKE,
                // One stroke setting, two names: the rectangle spends it on the
                // bar's thickness, the ring on the outline's width.
                title = stringResource(
                    if (a.rect) R.string.stroke_title_rect else R.string.stroke_title,
                ),
                summary = stringResource(
                    if (a.rect) R.string.stroke_summary_rect else R.string.stroke_summary,
                ),
                enabled = a.glyph,
                onValueChange = { v -> update { it.setRingStroke(v) } },
            )
            IntSlider(
                value = settings.arcStroke,
                min = Prefs.MIN_ARC_STROKE,
                max = Prefs.MAX_ARC_STROKE,
                title = stringResource(R.string.arc_stroke_title),
                // In the rectangle the arc group hangs off the top slot by its
                // own stroke, so this slider moves the arcs as well as thickening
                // them; the ring's summary has nothing to add there.
                summary = stringResource(
                    if (a.rect) {
                        R.string.arc_stroke_summary_rect
                    } else {
                        R.string.arc_stroke_summary
                    },
                ),
                enabled = a.glyph,
                onValueChange = { v -> update { it.setArcStroke(v) } },
            )
        }
    }
    item { SectionTitle(stringResource(R.string.group_text)) }
    item {
        Card {
            IntSlider(
                value = settings.valueSize,
                min = Prefs.MIN_VALUE_SIZE,
                max = Prefs.MAX_VALUE_SIZE,
                title = stringResource(R.string.value_size_title),
                summary = stringResource(R.string.value_size_summary),
                enabled = a.glyph,
                onValueChange = { v -> update { it.setValueSize(v) } },
            )
            val weightHint = gateHint(
                a.glyph to R.string.master_title,
                a.value to R.string.show_value_title,
            )
            TooltipBox(text = weightHint.orEmpty(), enabled = weightHint != null) {
                IntSlider(
                    value = settings.valueWeight,
                    min = Prefs.MIN_VALUE_WEIGHT,
                    max = Prefs.MAX_VALUE_WEIGHT,
                    title = stringResource(R.string.value_weight_title),
                    summary = stringResource(R.string.value_weight_summary),
                    // A 100..900 range would give the slider 799 steps, so
                    // it snaps in hundreds: the nine weights the platform
                    // actually ships distinct faces for.
                    step = WEIGHT_STEP,
                    enabled = a.glyph && a.value,
                    onValueChange = { v -> update { it.setValueWeight(v) } },
                )
            }
        }
    }
    item { SectionTitle(stringResource(R.string.group_type)) }
    item {
        Card {
            // These two sizes drive the same glyph in two different spaces.
            // The in-ring size is authored against the ring's 120x120 design
            // space, so it tops out at 44; the out-of-ring label is laid out
            // in the status bar's real pixel space and needs the wider 16..64
            // range it carries on its own slider below. They are deliberately
            // separate settings: one number cannot serve both spaces.
            val typeSizeHint = gateHint(
                a.glyph to R.string.master_title,
                a.typeInRing to R.string.show_mobile_type_title,
            )
            TooltipBox(text = typeSizeHint.orEmpty(), enabled = typeSizeHint != null) {
                IntSlider(
                    value = settings.typeSize,
                    min = Prefs.MIN_TYPE_SIZE,
                    max = Prefs.MAX_TYPE_SIZE,
                    title = stringResource(R.string.type_size_title),
                    summary = stringResource(R.string.type_size_summary),
                    enabled = a.glyph && a.typeInRing,
                    onValueChange = { v -> update { it.setTypeSize(v) } },
                )
            }
            // The out-of-ring label has its own size, live only when the label
            // actually sits out of the ring.
            val outTypeSizeHint = gateHint(
                a.glyph to R.string.master_title,
                a.typeOutOfRing to R.string.show_mobile_type_title,
            )
            TooltipBox(
                text = outTypeSizeHint.orEmpty(),
                enabled = outTypeSizeHint != null,
            ) {
                IntSlider(
                    value = settings.outTypeSize,
                    min = Prefs.MIN_OUT_TYPE_SIZE_DP,
                    max = Prefs.MAX_OUT_TYPE_SIZE_DP,
                    title = stringResource(R.string.out_type_size_title),
                    summary = stringResource(R.string.out_type_size_summary),
                    valueTextSuffix = "dp",
                    enabled = a.glyph && a.typeOutOfRing,
                    onValueChange = { v -> update { it.setOutTypeSize(v) } },
                )
            }
            // The trailing "A" of "5GA", shrunk against the main label. It is a
            // property of the label itself rather than of either position, so
            // its gate is "a network type is drawn somewhere" - and it stays
            // live while the type is out of ring, where the label is a real
            // TextView and both draws have to agree.
            val suffixScaleHint = gateHint(
                a.glyph to R.string.master_title,
                a.typeAnywhere() to R.string.show_mobile_type_title,
            )
            TooltipBox(text = suffixScaleHint.orEmpty(), enabled = suffixScaleHint != null) {
                IntSlider(
                    value = settings.typeSuffixScale,
                    min = Prefs.MIN_TYPE_SUFFIX_SCALE,
                    max = Prefs.MAX_TYPE_SUFFIX_SCALE,
                    title = stringResource(R.string.type_suffix_scale_title),
                    summary = stringResource(R.string.type_suffix_scale_summary),
                    enabled = a.glyph && a.typeAnywhere(),
                    onValueChange = { v -> update { it.setTypeSuffixScale(v) } },
                )
            }
            // The label's two margins. Same gate as its size: they only mean
            // anything while the label is the status bar's own child, and the
            // row names each margin by its physical side. Under RTL the pair
            // swaps those sides, which the summary says rather than the row
            // pretending the labels are reading-order starts.
            val marginHint = gateHint(
                a.glyph to R.string.master_title,
                a.typeOutOfRing to R.string.show_mobile_type_title,
            )
            TooltipBox(text = marginHint.orEmpty(), enabled = marginHint != null) {
                IntSlider(
                    value = settings.outTypeMarginLeft,
                    min = Prefs.MIN_OUT_TYPE_MARGIN,
                    max = Prefs.MAX_OUT_TYPE_MARGIN,
                    title = stringResource(R.string.out_type_margin_left_title),
                    summary = stringResource(R.string.out_type_margin_left_summary),
                    valueTextSuffix = "dp",
                    enabled = a.glyph && a.typeOutOfRing,
                    onValueChange = { v -> update { it.setOutTypeMarginLeft(v) } },
                )
            }
            TooltipBox(text = marginHint.orEmpty(), enabled = marginHint != null) {
                IntSlider(
                    value = settings.outTypeMarginRight,
                    min = Prefs.MIN_OUT_TYPE_MARGIN,
                    max = Prefs.MAX_OUT_TYPE_MARGIN,
                    title = stringResource(R.string.out_type_margin_right_title),
                    summary = stringResource(R.string.out_type_margin_right_summary),
                    valueTextSuffix = "dp",
                    enabled = a.glyph && a.typeOutOfRing,
                    onValueChange = { v -> update { it.setOutTypeMarginRight(v) } },
                )
            }
            // The weight still applies in both positions.
            val typeWeightHint = gateHint(
                a.glyph to R.string.master_title,
                a.typeAnywhere() to R.string.show_mobile_type_title,
            )
            TooltipBox(text = typeWeightHint.orEmpty(), enabled = typeWeightHint != null) {
                IntSlider(
                    value = settings.typeWeight,
                    min = Prefs.MIN_TYPE_WEIGHT,
                    max = Prefs.MAX_TYPE_WEIGHT,
                    title = stringResource(R.string.type_weight_title),
                    summary = stringResource(R.string.type_weight_summary),
                    step = WEIGHT_STEP,
                    enabled = a.glyph && a.typeAnywhere(),
                    onValueChange = { v -> update { it.setTypeWeight(v) } },
                )
            }
        }
    }
    // The out-of-ring reading gets its own card: it is a different element from
    // the type label (which is why its two sliders were buried in the type card
    // until now), it has a different gate - the stacked reading has to actually
    // be drawn - and mixing the two groups made the user dig for the spacing
    // row between type rows that do not affect it.
    //
    // The ring's own offset sits in a group ahead of it because it belongs to a
    // different element again: it spaces the ring from the native row, whatever
    // the out-of-ring reading is doing.
    item { SectionTitle(stringResource(R.string.group_spacing)) }
    item {
        Card {
            // Negative values are the whole point of this control: at 0 the ring
            // already keeps no layout margin from the icon on its left, and the
            // distance still visible is transparent padding inside the native
            // icons, which no margin can touch. Only a negative offset can eat
            // into it, and how much is safe varies by icon set - hence a slider
            // rather than a constant. Read every frame, so it moves as it is
            // dragged.
            IntSlider(
                value = settings.glyphLeftOffset,
                min = Prefs.MIN_GLYPH_LEFT_OFFSET,
                max = Prefs.MAX_GLYPH_LEFT_OFFSET,
                title = stringResource(R.string.glyph_left_offset_title),
                summary = stringResource(R.string.glyph_left_offset_summary),
                valueTextSuffix = "dp",
                enabled = a.glyph,
                onValueChange = { v -> update { it.setGlyphLeftOffset(v) } },
                // Stored in half-dp; show the dp the summary talks about.
                valueFormatter = { v ->
                    String.format(java.util.Locale.US, "%.1f", v / 2f)
                },
            )
            // ★ v82：整组左右平移。与上面那条分工明确 —— 上面只改"与左侧原生
            //   图标的间距"，这条动的是整组位置：margin（把原生图标推走/放回）
            //   与 translationX（平移整组）两条路共用同一个量，组内相对距离不变。
            //   实机几何：我们这一组是状态栏那一行最后一组子 View，行的右缘钉死在
            //   屏幕右缘（geometry dump 里我们的 View 的 r 恒等于行的宽）——
            //   所以只改 margin 动的会是原生图标、只改 translation 会压到它们上面。
            IntSlider(
                value = settings.glyphShift,
                min = Prefs.MIN_GLYPH_SHIFT,
                max = Prefs.MAX_GLYPH_SHIFT,
                title = stringResource(R.string.glyph_shift_title),
                summary = stringResource(R.string.glyph_shift_summary),
                valueTextSuffix = "dp",
                enabled = a.glyph,
                onValueChange = { v -> update { it.setGlyphShift(v) } },
            )
        }
    }
    // Burn-in protection gets its own group: it is not a spacing control (it
    // moves the drawing, never the layout) and it is not an appearance control
    // either, so putting it under either heading would misdescribe what it does.
    item { SectionTitle(stringResource(R.string.group_burn_in)) }
    item {
        Card {
            // On by default: screen burn-in is permanent, and the default
            // amplitude is small enough that nothing visibly moves.
            SwitchPreference(
                checked = settings.burnInProtect,
                onCheckedChange = { value -> update { it.setBurnInProtect(value) } },
                title = stringResource(R.string.burn_in_protect_title),
                summary = stringResource(R.string.burn_in_protect_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            // One gate for both knobs: while the switch is off they do nothing,
            // so they share a single hint instead of each repeating the reason.
            val burnInHint = gateHint(
                a.glyph to R.string.master_title,
                settings.burnInProtect to R.string.burn_in_protect_title,
            )
            TooltipBox(text = burnInHint.orEmpty(), enabled = burnInHint != null) {
                // Stored in quarter-dp; show the dp the summary talks about.
                IntSlider(
                    value = settings.burnInAmplitude,
                    min = Prefs.MIN_BURN_IN_AMPLITUDE,
                    max = Prefs.MAX_BURN_IN_AMPLITUDE,
                    title = stringResource(R.string.burn_in_amplitude_title),
                    summary = stringResource(R.string.burn_in_amplitude_summary),
                    valueTextSuffix = "dp",
                    enabled = a.glyph && settings.burnInProtect,
                    onValueChange = { v -> update { it.setBurnInAmplitude(v) } },
                    valueFormatter = { v ->
                        String.format(java.util.Locale.US, "%.2f", v / 4f)
                    },
                )
            }
            TooltipBox(text = burnInHint.orEmpty(), enabled = burnInHint != null) {
                IntSlider(
                    value = settings.burnInPeriodSec,
                    min = Prefs.MIN_BURN_IN_PERIOD,
                    max = Prefs.MAX_BURN_IN_PERIOD,
                    step = BURN_IN_PERIOD_STEP,
                    title = stringResource(R.string.burn_in_period_title),
                    summary = stringResource(R.string.burn_in_period_summary),
                    valueTextSuffix = "s",
                    enabled = a.glyph && settings.burnInProtect,
                    onValueChange = { v -> update { it.setBurnInPeriod(v) } },
                )
            }
        }
    }
    item { SectionTitle(stringResource(R.string.group_out_signal)) }
    item {
        Card {
            // The size of the out-of-ring reading itself. It is authored against
            // neither design space and against no view either: the setting is a
            // plain dp, resolved against the display's density, so it is the same
            // reading whatever the status bar row happens to be doing - MIUI
            // lays that row out 88px tall at rest and 134px with the control
            // centre pulled down, and a share of either would drift with it.
            //
            // Gated on the switches the reading needs, named individually so the
            // hint points at a row the user can act on. The knob itself is
            // {@code a.stackedOut()} - the rule that decides there is a reading to
            // size - rather than a second copy of the same three conditions.
            val outSignalSizeHint = gateHint(
                a.glyph to R.string.master_title,
                a.mobile to R.string.show_mobile_title,
                a.signalOutOfRing to R.string.signal_mode_title,
                a.stackedSignal to R.string.stacked_signal_title,
            )
            TooltipBox(
                text = outSignalSizeHint.orEmpty(),
                enabled = outSignalSizeHint != null,
            ) {
                IntSlider(
                    value = settings.outSignalSize,
                    min = Prefs.MIN_OUT_SIGNAL_SIZE,
                    max = Prefs.MAX_OUT_SIGNAL_SIZE,
                    title = stringResource(R.string.out_signal_size_title),
                    summary = stringResource(R.string.out_signal_size_summary),
                    valueTextSuffix = "dp",
                    enabled = a.stackedOut(),
                    onValueChange = { v -> update { it.setOutSignalSize(v) } },
                )
            }
            // How far the reading stands from the battery. A reserved margin, so
            // it moves the native icons as well as the reading - which is why it
            // is a gap and not a position: a free position slid the reading over
            // the icons it should have displaced. Same gate as the size, since
            // there is only a reading to space while one is drawn.
            TooltipBox(
                text = outSignalSizeHint.orEmpty(),
                enabled = outSignalSizeHint != null,
            ) {
                IntSlider(
                    value = settings.outSignalMargin,
                    min = Prefs.MIN_OUT_SIGNAL_MARGIN,
                    max = Prefs.MAX_OUT_SIGNAL_MARGIN,
                    title = stringResource(R.string.out_signal_margin_title),
                    summary = stringResource(R.string.out_signal_margin_summary),
                    valueTextSuffix = "dp",
                    enabled = a.stackedOut(),
                    onValueChange = { v -> update { it.setOutSignalMargin(v) } },
                )
            }
        }
    }
    // Battery readout: temperature only. Charging power was removed in v75 -
    // on this device the platform never reports CURRENT_NOW, and flipping the
    // switch on made the status bar flicker. Default off, so an install that
    // touches nothing looks exactly as it did before the feature existed.
    item { SectionTitle(stringResource(R.string.group_perf)) }
    item {
        Card {
            SwitchPreference(
                checked = settings.perfTemp,
                onCheckedChange = { value -> update { it.setPerfTemp(value) } },
                title = stringResource(R.string.perf_temp_title),
                summary = stringResource(R.string.perf_temp_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            // The knobs only move something while the switch is up, so they
            // share one gate and one hint.
            val perfHint = gateHint(
                a.glyph to R.string.master_title,
                a.perfTemp to R.string.perf_temp_title,
            )
            TooltipBox(text = perfHint.orEmpty(), enabled = perfHint != null) {
                IntSlider(
                    value = settings.perfOuterMargin,
                    min = Prefs.MIN_PERF_OUTER_MARGIN,
                    max = Prefs.MAX_PERF_OUTER_MARGIN,
                    title = stringResource(R.string.perf_outer_margin_title),
                    summary = stringResource(R.string.perf_outer_margin_summary),
                    valueTextSuffix = "dp",
                    enabled = a.glyph && a.perfTemp,
                    onValueChange = { v -> update { it.setPerfOuterMargin(v) } },
                )
            }
            TooltipBox(text = perfHint.orEmpty(), enabled = perfHint != null) {
                IntSlider(
                    value = settings.perfOuterMarginRight,
                    min = Prefs.MIN_PERF_OUTER_MARGIN_RIGHT,
                    max = Prefs.MAX_PERF_OUTER_MARGIN_RIGHT,
                    title = stringResource(R.string.perf_outer_margin_right_title),
                    summary = stringResource(R.string.perf_outer_margin_right_summary),
                    valueTextSuffix = "dp",
                    enabled = a.glyph && a.perfTemp,
                    onValueChange = { v -> update { it.setPerfOuterMarginRight(v) } },
                )
            }
            TooltipBox(text = perfHint.orEmpty(), enabled = perfHint != null) {
                IntSlider(
                    value = settings.perfScale,
                    min = Prefs.MIN_PERF_SCALE,
                    max = Prefs.MAX_PERF_SCALE,
                    title = stringResource(R.string.perf_scale_title),
                    summary = stringResource(R.string.perf_scale_summary),
                    valueTextSuffix = "%",
                    enabled = a.glyph && a.perfAnywhere(),
                    onValueChange = { v -> update { it.setPerfScale(v) } },
                )
            }
            TooltipBox(text = perfHint.orEmpty(), enabled = perfHint != null) {
                IntSlider(
                    value = settings.perfWeight,
                    min = Prefs.MIN_PERF_WEIGHT,
                    max = Prefs.MAX_PERF_WEIGHT,
                    step = WEIGHT_STEP,
                    title = stringResource(R.string.perf_weight_title),
                    summary = stringResource(R.string.perf_weight_summary),
                    enabled = a.glyph && a.perfAnywhere(),
                    onValueChange = { v -> update { it.setPerfWeight(v) } },
                )
            }
        }
    }
    item { SectionTitle(stringResource(R.string.group_track)) }
    item {
        Card {
            IntSlider(
                value = settings.trackAlpha,
                min = Prefs.MIN_TRACK_ALPHA,
                max = Prefs.MAX_TRACK_ALPHA,
                title = stringResource(R.string.track_alpha_title),
                summary = stringResource(R.string.track_alpha_summary),
                enabled = a.glyph,
                onValueChange = { v -> update { it.setTrackAlpha(v) } },
            )
        }
    }
}

/**
 * Colors: the role-colour switch, the low-battery threshold and the three
 * per-role colour rows. The threshold stays here rather than under Geometry
 * because it only ever chooses between two colours.
 */
private fun LazyListScope.colorsTab(
    settings: TrioSettings,
    update: ((SettingsRepository) -> Unit) -> Unit,
    onEdit: (RoleColor) -> Unit,
    onReset: () -> Unit,
) {
    item {
        Card {
            // Built here for the same reason as the other two tabs: the rows
            // follow the rule rather than restating the settings.
            val a = TrioAppearance.of(settings)
            SwitchPreference(
                checked = a.roleColors,
                onCheckedChange = { value -> update { it.setRoleColors(value) } },
                title = stringResource(R.string.color_role_title),
                summary = stringResource(R.string.color_role_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            // One gate for the whole card: everything under the switch is
            // meaningless while per-role colours are off, so they share the hint.
            val roleGate = gateHint(
                a.glyph to R.string.master_title,
                a.roleColors to R.string.color_role_title,
            )
            TooltipBox(text = roleGate.orEmpty(), enabled = roleGate != null) {
                IntSlider(
                    value = settings.lowThreshold,
                    min = Prefs.MIN_LOW_THRESHOLD,
                    max = Prefs.MAX_LOW_THRESHOLD,
                    title = stringResource(R.string.low_threshold_title),
                    summary = stringResource(R.string.low_threshold_summary),
                    enabled = a.glyph && a.roleColors,
                    onValueChange = { v -> update { it.setLowThreshold(v) } },
                )
            }
            // ★ v77：省电模式是否把电量染黄（默认开 = MIUI 语义；三星原生不染）。
            TooltipBox(text = roleGate.orEmpty(), enabled = roleGate != null) {
                SwitchPreference(
                    checked = settings.powerSaveColor,
                    onCheckedChange = { value -> update { it.setPowerSaveColor(value) } },
                    title = stringResource(R.string.power_save_color_title),
                    summary = stringResource(R.string.power_save_color_summary),
                    insideMargin = SettingsItemMargin,
                    enabled = a.glyph && a.roleColors,
                )
            }
            // ★ v82：充电时「闪电 + 电量」同显（默认关）。放在颜色这一组，
            //   与省电染色并列 —— 它跟"颜色怎么定"无关、跟"充电时长什么样"有关，
            //   但用户明确要求放这儿，就放这儿。
            SwitchPreference(
                checked = settings.chargeWithValue,
                onCheckedChange = { value -> update { it.setChargeWithValue(value) } },
                title = stringResource(R.string.charge_with_value_title),
                summary = stringResource(R.string.charge_with_value_summary),
                insideMargin = SettingsItemMargin,
                enabled = a.glyph,
            )
            IntSlider(
                value = settings.chargeValueSize,
                min = Prefs.MIN_CHARGE_VALUE_SIZE,
                max = Prefs.MAX_CHARGE_VALUE_SIZE,
                title = stringResource(R.string.charge_value_size_title),
                summary = stringResource(R.string.charge_value_size_summary),
                enabled = a.glyph && settings.chargeWithValue,
                onValueChange = { v -> update { it.setChargeValueSize(v) } },
            )
            RoleColor.entries.forEach { role ->
                TooltipBox(text = roleGate.orEmpty(), enabled = roleGate != null) {
                    ArrowPreference(
                        title = stringResource(role.labelRes),
                        summary = stringResource(role.colorFieldLabelRes),
                        insideMargin = SettingsItemMargin,
                        startAction = {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Swatch(colorFor(settings, role, onDark = true))
                                Swatch(colorFor(settings, role, onDark = false))
                            }
                        },
                        enabled = a.glyph && a.roleColors,
                        onClick = { onEdit(role) },
                    )
                }
            }
            TooltipBox(text = roleGate.orEmpty(), enabled = roleGate != null) {
                ArrowPreference(
                    title = stringResource(R.string.color_reset),
                    summary = stringResource(R.string.color_reset_summary),
                    insideMargin = SettingsItemMargin,
                    enabled = a.glyph && a.roleColors,
                    // Asks first: the reset throws away every custom colour at
                    // once and there is no undo behind it.
                    onClick = onReset,
                )
            }
        }
    }
}

/** About: activation state, module info, the update card, both repo links and the log switch. */
private fun LazyListScope.aboutTab(
    settings: TrioSettings,
    service: XposedService?,
    updater: UpdateController,
    update: ((SettingsRepository) -> Unit) -> Unit,
) {
    item { ActivatedRow(settings = settings, service = service) }
    item { InfoCard(service = service) }
    // ★ v87：作者与检查更新对调（用户要求）—— 只读的作者信息在前，带动作的检查更新
    //   紧随其后；导出/导入同样是对配置做动作，放在检查更新下面。
    item { AuthorCard() }
    item { UpdateCard(controller = updater) }
    item { ExportConfigCard(settings = settings) }
    item { ImportConfigCard(update = update) }
    // ★ v85：本项目自己的仓库在上游参考项目上面 —— 用户先看到的是"这个模块本身"，
    //   致谢对象退到第二位。
    item { RepoHomeCard() }
    item { RepoCard() }
    item {
        Card {
            SwitchPreference(
                checked = settings.debugLog,
                onCheckedChange = { value -> update { it.setDebugLog(value) } },
                title = stringResource(R.string.debug_title),
                summary = stringResource(R.string.debug_summary),
                insideMargin = SettingsItemMargin,
            )
        }
    }
}

/**
 * The module's author.
 *
 * <p>A card of its own, sitting exactly where the updater card used to be. It is
 * read-only information like the two cards around it, but it belongs to neither:
 * [InfoCard] is the module's own version and scope, [RepoCard] is where the
 * upstream project lives. Kept separate so each of the three answers one
 * question instead of the author being buried as a line inside another.
 */
@Composable
private fun AuthorCard() {
    // fillMaxWidth on both the card and its column, for the same reason InfoCard
    // needs it: Miuix's Card wraps to its content and a single short row would
    // otherwise render visibly inset against its siblings.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InfoRow(
                label = stringResource(R.string.info_author),
                value = stringResource(R.string.info_author_name),
                bottomPadding = 0.dp,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 配置的导出 / 导入
//
// 文件格式是一个 JSON 对象：键名与 Prefs.KEY_* 完全一致（直接复用 TrioSettings
// .toBundle() 的键，不另建一份映射），外加一个 config_version 标记，将来格式变了
// 可以识别并拒绝。走系统文件选择器（SAF）而不是自己往公共 Download 目录写文件：
// Android 10+ 限制直接写公共目录，而且让用户自己选位置才知道文件去了哪。
// ---------------------------------------------------------------------------

private const val CONFIG_VERSION_KEY = "config_version"
private const val CONFIG_VERSION = 1
private const val CONFIG_FILE_NAME = "SamsungDuo-config.json"

/**
 * Writes the whole configuration to a file the user names and places.
 *
 * <p>Only the four types the preferences actually use are written; anything
 * else would come back from JSON as a shape [TrioSettings.fromBundle] has no
 * way to read, and the point of the file is that it round-trips.
 */
@Composable
private fun ExportConfigCard(settings: TrioSettings) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            val json = JSONObject()
            json.put(CONFIG_VERSION_KEY, CONFIG_VERSION)
            val bundle = settings.toBundle()
            for (key in bundle.keySet()) {
                val value = bundle[key]
                if (value is Boolean || value is Int || value is Float || value is String) {
                    json.put(key, value)
                }
            }
            val bytes = json.toString(2).toByteArray(Charsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) }
                ?: error("cannot open $uri for writing")
        }.isSuccess
        Toast.makeText(
            context,
            context.getString(
                if (ok) R.string.config_export_done else R.string.config_export_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
    Card {
        ArrowPreference(
            title = stringResource(R.string.config_export_title),
            summary = stringResource(R.string.config_export_summary),
            insideMargin = SettingsItemMargin,
            // A device with no save dialogue must not take the screen down with it.
            onClick = { runCatching { launcher.launch(CONFIG_FILE_NAME) } },
        )
    }
}

/**
 * Reads a configuration back from a file the user picks.
 *
 * <p>Every value goes through [TrioSettings.fromBundle] — the same path the
 * hooked process uses for broadcast extras: each missing key falls back on its
 * own default and every number is clamped there. A partial or hand-edited file
 * therefore cannot put the status bar into a state the UI would not let you
 * dial in by hand.
 */
@Composable
private fun ImportConfigCard(update: ((SettingsRepository) -> Unit) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val imported: TrioSettings? = runCatching {
            val text = context.contentResolver.openInputStream(uri)
                ?.use { it.readBytes().toString(Charsets.UTF_8) }
                ?: error("cannot read $uri")
            val json = JSONObject(text)
            if (json.optInt(CONFIG_VERSION_KEY, CONFIG_VERSION) > CONFIG_VERSION) {
                error("config written by a newer version")
            }
            val bundle = Bundle()
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                when (val value = json.get(key)) {
                    is Boolean -> bundle.putBoolean(key, value)
                    is Int -> bundle.putInt(key, value)
                    is Double -> bundle.putFloat(key, value.toFloat())
                    is String -> bundle.putString(key, value)
                }
            }
            TrioSettings.fromBundle(bundle)
        }.getOrNull()

        if (imported == null) {
            Toast.makeText(
                context, context.getString(R.string.config_import_failed), Toast.LENGTH_SHORT,
            ).show()
        } else {
            update { it.replaceAll(imported) }
            Toast.makeText(
                context, context.getString(R.string.config_import_done), Toast.LENGTH_SHORT,
            ).show()
        }
    }
    Card {
        ArrowPreference(
            title = stringResource(R.string.config_import_title),
            summary = stringResource(R.string.config_import_summary),
            insideMargin = SettingsItemMargin,
            onClick = { runCatching { launcher.launch(arrayOf("application/json", "*/*")) } },
        )
    }
}

/**
 * ★ v85：在线更新卡片 —— 检查 / 下载 / 交给系统安装器，全走 GitHub Releases。
 *
 * <p>控制器由屏幕级传入并 remember，不在这里 new：LazyColumn 会销毁划出屏幕的 item，
 * 而一次下载不该因为用户切到别的 tab 就中断（UpdateController 的头注释同理）。
 *
 * <p>每种拒绝都给一句话说明而不是静默失败：缺"安装未知应用"权限和系统里没有安装器
 * 是两回事，前者用户能去设置里给，后者给不了。
 */
@Composable
private fun UpdateCard(controller: UpdateController) {
    val context = LocalContext.current
    val state = controller.state
    var note by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            when (state) {
                is UpdateState.Idle -> ArrowPreference(
                    title = stringResource(R.string.update_title),
                    summary = stringResource(R.string.update_summary_idle),
                    insideMargin = SettingsItemMargin,
                    onClick = { note = null; controller.check() },
                )

                is UpdateState.Checking -> ArrowPreference(
                    title = stringResource(R.string.update_checking),
                    summary = "",
                    insideMargin = SettingsItemMargin,
                    enabled = false,
                    startAction = { InfiniteProgressIndicator() },
                )

                is UpdateState.UpToDate -> ArrowPreference(
                    title = stringResource(R.string.update_uptodate, state.current),
                    summary = "",
                    insideMargin = SettingsItemMargin,
                    onClick = { note = null; controller.check() },
                )

                is UpdateState.NoRelease -> ArrowPreference(
                    title = stringResource(R.string.update_no_release),
                    summary = "",
                    insideMargin = SettingsItemMargin,
                    onClick = { note = null; controller.check() },
                )

                is UpdateState.Available -> {
                    val size = formatSize(state.info.apkSize)
                    ArrowPreference(
                        title = stringResource(R.string.update_available, state.info.version),
                        summary = stringResource(
                            R.string.update_available_summary,
                            state.current,
                            if (size.isEmpty()) state.info.tag else size,
                        ),
                        insideMargin = SettingsItemMargin,
                        onClick = { controller.openInBrowser(state.info.pageUrl) },
                    )
                    UpdateButtons {
                        TextButton(
                            text = stringResource(R.string.update_download),
                            onClick = { note = null; controller.download(state.info) },
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            text = stringResource(R.string.update_release_page),
                            onClick = { controller.openInBrowser(state.info.pageUrl) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is UpdateState.Downloading -> {
                    ArrowPreference(
                        title = stringResource(
                            R.string.update_downloading,
                            Math.round(state.progress * 100f),
                        ),
                        summary = "",
                        insideMargin = SettingsItemMargin,
                        enabled = false,
                    )
                    LinearProgressIndicator(
                        progress = state.progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = SettingsItemInset)
                            .padding(bottom = 12.dp),
                    )
                }

                is UpdateState.Ready -> {
                    ArrowPreference(
                        title = stringResource(R.string.update_ready),
                        summary = stringResource(R.string.update_ready_summary),
                        insideMargin = SettingsItemMargin,
                    )
                    UpdateButtons {
                        TextButton(
                            text = stringResource(R.string.update_install),
                            onClick = {
                                when (controller.install(state.file)) {
                                    InstallResult.Started -> note = null
                                    InstallResult.NeedsPermission -> {
                                        note = context.getString(
                                            R.string.update_permission_needed)
                                        controller.openInstallPermissionScreen()
                                    }
                                    InstallResult.NoInstaller ->
                                        note = context.getString(
                                            R.string.update_install_failed)
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                is UpdateState.Failed -> ArrowPreference(
                    title = stringResource(R.string.update_failed),
                    summary = state.detail ?: "",
                    insideMargin = SettingsItemMargin,
                    onClick = { note = null; controller.check() },
                )
            }

            val text = note
            if (text != null) {
                Text(
                    text = text,
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier
                        .padding(horizontal = SettingsItemInset)
                        .padding(bottom = 12.dp),
                )
            }
        }
    }
}

/** 更新卡片底部那一行按钮：与卡片左右留出和列表项一致的内边距。 */
@Composable
private fun UpdateButtons(content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsItemInset)
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/**
 * 本项目自己的仓库（源码、发布记录、问题反馈）。
 *
 * <p>★ v85 新增：更新功能的数据源换成本项目仓库之后，"检查更新"卡片里的
 * "打开发布页面"已经能到仓库，但关于页缺一个常驻入口，而且原来的
 * [RepoCard] 指的是上游参考项目，名不副实 —— 所以拆成两张卡，本项目在上。
 */
@Composable
private fun RepoHomeCard() {
    val context = LocalContext.current
    Card {
        ArrowPreference(
            title = stringResource(R.string.repo_home_title),
            summary = stringResource(R.string.repo_home_summary),
            insideMargin = SettingsItemMargin,
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(REPO_HOME))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}

/**
 * The About page's link to the open-source project this module was ported from.
 *
 * <p>It used to be the last row of the updater card — the one that stayed put
 * when every action above it could not be offered. The updater is back above,
 * so this is the acknowledgement row: it always leads somewhere, and what it
 * leads to is no longer this project's own repository (see [RepoHomeCard]).
 */
@Composable
private fun RepoCard() {
    val context = LocalContext.current
    Card {
        ArrowPreference(
            title = stringResource(R.string.update_repo),
            summary = stringResource(R.string.update_repo_summary),
            insideMargin = SettingsItemMargin,
            onClick = {
                // A device with no browser must not take the settings screen down
                // with it, so the launch is allowed to fail quietly.
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(UPSTREAM_HOME))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
        )
    }
}

/**
 * Previews the glyph in seven states, drawn by the very renderer the status bar
 * uses, so the two can never disagree about the geometry.
 *
 * <p>The no-Wi-Fi state is the one worth watching: with no Wi-Fi ink the value
 * moves down into the ring centre, which is where the percentage lives whenever
 * Wi-Fi is off.
 *
 * <p>The last two cells are the odd ones out: neither is the glyph but one of
 * the two things the status bar lays out beside the icon when the reading is
 * moved out of the ring. They get their own cells because neither has a size or
 * a shape that shows up anywhere else on this screen.
 */
@Composable
private fun PreviewCard(settings: TrioSettings) {
    Card {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.preview_title),
                style = MiuixTheme.textStyles.subtitle,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                PreviewCell(settings, 88, charging = true, caption = stringResource(R.string.preview_charging))
                PreviewCell(
                    settings, 88,
                    charging = true,
                    quickCharging = true,
                    caption = stringResource(R.string.preview_quick_charge),
                )
                PreviewCell(settings, 64, caption = stringResource(R.string.preview_normal))
                PreviewCell(
                    settings, 79,
                    wifiLevel = NO_WIFI_LEVEL,
                    slotLevels = DUAL_SIM_PREVIEW,
                    mobileType = MOBILE_TYPE_PREVIEW,
                    caption = stringResource(R.string.preview_no_wifi),
                )
                PreviewCell(settings, 24, powerSave = true, low = true, caption = stringResource(R.string.preview_low))
                PreviewCell(settings, 12, low = true, caption = stringResource(R.string.preview_critical))
                PreviewCell(
                    settings, 79,
                    mobileType = MOBILE_TYPE_PREVIEW,
                    outTypeOnly = true,
                    caption = stringResource(R.string.preview_out_type),
                )
                PreviewCell(
                    settings, 79,
                    slotLevels = DUAL_SIM_PREVIEW,
                    outSignalOnly = true,
                    caption = stringResource(R.string.preview_out_signal),
                )
            }
        }
    }
}

@Composable
private fun RowScope.PreviewCell(
    settings: TrioSettings,
    level: Int,
    charging: Boolean = false,
    quickCharging: Boolean = false,
    powerSave: Boolean = false,
    low: Boolean = false,
    wifiLevel: Int = WIFI_LEVEL,
    slotLevels: IntArray? = null,
    mobileType: String = "",
    /** Draws the out-of-ring type label instead of the glyph in this cell. */
    outTypeOnly: Boolean = false,
    /** Draws the out-of-ring signal reading instead of the glyph in this cell. */
    outSignalOnly: Boolean = false,
    caption: String,
) {
    // The status bar sits on the wallpaper, so the preview follows the app's own
    // light/dark mode rather than trying to guess the wallpaper.
    val dark = isDarkTheme()

    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AndroidView(
            modifier = Modifier.size(PREVIEW_SIZE),
            factory = { context -> TrioPreviewView(context) },
            update = { view ->
                view.setSettings(settings)
                view.setOutTypeOnly(outTypeOnly)
                view.setOutSignalOnly(outSignalOnly)
                view.setPreviewBackground(if (dark) 0xFF1C1B1F.toInt() else 0xFFF2F2F7.toInt())
                view.setForeground(if (dark) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
                view.setState(level, charging, quickCharging, powerSave, low, wifiLevel,
                    MOBILE_LEVEL, slotLevels, mobileType)
            },
        )
        Text(
            text = caption,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * One label/value pair of the about card: a heavy dark label over a lighter grey
 * value, stacked so long values (the framework string, the scope list) stay free
 * to wrap without a second column squeezing them.
 */
@Composable
private fun InfoRow(label: String, value: String, bottomPadding: Dp = 24.dp) {
    Text(
        text = label,
        style = MiuixTheme.textStyles.headline1,
        fontWeight = FontWeight.Medium,
        color = MiuixTheme.colorScheme.onSurface,
    )
    Text(
        text = value,
        style = MiuixTheme.textStyles.body2,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(top = 2.dp, bottom = bottomPadding),
    )
}

/**
 * The top block of the about page: a tall "activated" card on the left and two
 * short statistic cards stacked on the right.
 *
 * <p>The size is driven by aspectRatio(1f) rather than a fixed height, matching
 * the reference: the side length follows the available width on any device, so
 * the block keeps its proportions instead of looking squat on a tablet.
 */
@Composable
private fun ActivatedRow(settings: TrioSettings, service: XposedService?) {
    val context = LocalContext.current
    val module = remember(context) { readModuleInfo(context) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActivatedCard(
            active = service != null,
            version = module.moduleVersion,
            modifier = Modifier.weight(1f).aspectRatio(1f),
        )
        Column(
            modifier = Modifier.weight(1f).aspectRatio(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label = stringResource(R.string.about_scope_count),
                value = scopeCount(service).toString(),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label = stringResource(R.string.about_enabled_count),
                value = enabledCount(settings).toString(),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * The green activation card: state as a heavy title, module version underneath,
 * and the ring-and-check badge bleeding off the bottom-right corner.
 */
@Composable
private fun ActivatedCard(active: Boolean, version: String, modifier: Modifier = Modifier) {
    val dark = isDarkTheme()
    val content = if (dark) MiuixTheme.colorScheme.onSurface else ActivatedContent

    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.defaultColors(
            color = if (dark) ActivatedContainerDark else ActivatedContainer,
            contentColor = content,
        ),
        // These cards are read-outs, not buttons: they have nowhere to navigate
        // to, so they stay non-clickable. Miuix wires the press feedback on
        // independently of onClick, which gives the MIUI "give" under the finger
        // without pretending there is an action behind it. Tilt is the variant
        // the reference page uses on its equivalent cards.
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        // clipToBounds rather than clip(RoundedCornerShape): the card has already
        // drawn its squircle outline, and clipping to a plain rounded rectangle
        // here would square off the corners the user is looking at.
        Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
            ) {
                Text(
                    text = stringResource(
                        if (active) R.string.about_activated else R.string.about_inactive,
                    ),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (active) {
                        stringResource(R.string.about_module_version, version)
                    } else {
                        stringResource(R.string.about_inactive_hint)
                    },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = content,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // The badge is not aligned to the corner: it is nudged 27x31 further
            // out so a different slice of the ring survives the clip. The vector
            // is the reference's own asset rather than a redrawing of it — the
            // ring-to-viewport ratio is what the eye compares, and it is easy to
            // get wrong by hand.
            Box(
                modifier = Modifier.fillMaxSize().offset(27.dp, 31.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_check_circle_outline),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(if (dark) ActivatedBadgeDark else ActivatedBadge),
                    modifier = Modifier.size(110.dp),
                )
            }
        }
    }
}

/** One of the two short right-hand cards: a quiet label over a large figure. */
@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxHeight(),
        // Same read-out-not-a-button treatment as [ActivatedCard]; the two cards
        // sit side by side, so they must not disagree about how they respond.
        pressFeedbackType = PressFeedbackType.Tilt,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Text(
                text = value,
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The reference's four identity rows first, then the two the reference has no
 * equivalent for — the scope list and the Android level are what a user actually
 * needs to report a bug, so they stay on rather than being dropped for symmetry.
 */
@Composable
private fun InfoCard(service: XposedService?) {
    val context = LocalContext.current

    // Every framework getter is a binder call into the daemon: bind them to the
    // service identity so they run when it changes rather than on every
    // recomposition.
    val unknown = stringResource(R.string.info_unknown)
    val module = remember(context) { readModuleInfo(context) }
    val framework = remember(service, unknown) { readFrameworkInfo(service, unknown) }
    val scope = remember(service, unknown) { readScope(service, unknown) }

    // fillMaxWidth is required on both the card and its content column: Miuix's Card
    // wraps to its content, and this card's text is narrower than the sibling cards,
    // so without it the About card renders visibly inset on the right.
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            InfoRow(stringResource(R.string.info_system_version), module.systemVersion)
            InfoRow(stringResource(R.string.info_module_version), module.moduleVersion)
            InfoRow(stringResource(R.string.info_framework), framework)
            InfoRow(stringResource(R.string.info_device_model), module.deviceModel)
            InfoRow(stringResource(R.string.info_scope), scope)
            InfoRow(
                label = stringResource(R.string.info_android_version),
                value = module.androidVersion,
                bottomPadding = 12.dp,
            )

            Text(
                text = stringResource(R.string.about_summary),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The confirmation for restarting System UI.
 *
 * <p>[OverlayDialog] rather than a bottom sheet: this is the dialog the colour
 * picker already uses, so the confirmation reads as part of the module instead
 * of borrowing a second popup style. Rendering through the Scaffold's popup host
 * is also why this has to stay inside the Scaffold's content lambda.
 *
 * <p>Dismissal is refused while the kill is in flight — the user may already be
 * looking at a root prompt, and closing the dialog there would leave the outcome
 * with nowhere to be reported.
 */
@Composable
private fun RestartDialog(
    controller: RestartController,
    show: Boolean,
    onDismiss: () -> Unit,
) {
    val state = controller.state
    val running = state is RestartState.Running

    OverlayDialog(
        show = show,
        title = stringResource(R.string.restart_title),
        summary = stringResource(R.string.restart_summary),
        onDismissRequest = { if (!running) onDismiss() },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The raw shell text rides along with the localised headline, the
            // same way a failed update pairs its diagnostic detail.
            if (state is RestartState.Failed) {
                Text(
                    text = listOfNotNull(
                        stringResource(R.string.restart_failed),
                        state.detail?.takeIf { it.isNotBlank() },
                    ).joinToString(" · "),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(
                    text = stringResource(R.string.cancel),
                    onClick = onDismiss,
                    enabled = !running,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { controller.restart() },
                    enabled = !running,
                    colors = ButtonDefaults.buttonColorsPrimary(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = stringResource(
                            if (running) R.string.restart_running else R.string.restart_confirm,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * The confirmation for resetting the three role colours.
 *
 * <p>Same [OverlayDialog] shell as the restart confirmation, so it stays inside
 * the Scaffold's content lambda for the same reason.
 */
@Composable
private fun ResetColorsDialog(
    show: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.color_reset),
        summary = stringResource(R.string.color_reset_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                text = stringResource(R.string.cancel),
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColorsPrimary(),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.color_reset_confirm))
            }
        }
    }
}

@Composable
private fun ColorDialog(
    role: RoleColor,
    settings: TrioSettings,
    onPick: (onDark: Boolean, argb: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var onDark by remember { mutableStateOf(true) }

    OverlayDialog(
        show = true,
        title = stringResource(role.labelRes),
        summary = stringResource(R.string.color_dialog_summary),
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundChip(
                    label = stringResource(R.string.color_on_dark),
                    selected = onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = true }
                BackgroundChip(
                    label = stringResource(R.string.color_on_light),
                    selected = !onDark,
                    modifier = Modifier.weight(1f),
                ) { onDark = false }
            }
            ColorPalette(
                color = Color(colorFor(settings, role, onDark)),
                onColorChanged = { picked -> onPick(onDark, picked.toArgb()) },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BackgroundChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = if (selected) {
        ButtonDefaults.buttonColors()
    } else {
        ButtonDefaults.buttonColors(
            color = MiuixTheme.colorScheme.surfaceContainerHigh,
            contentColor = MiuixTheme.colorScheme.onSurfaceContainerHigh,
        )
    }
    Button(onClick = onClick, modifier = modifier, colors = colors) {
        Text(text = label, style = MiuixTheme.textStyles.button)
    }
}

@Composable
private fun Swatch(color: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(Color(color)),
    )
}

/**
 * Integer slider over a closed range, showing the current value at the end.
 *
 * @param step increment between selectable values; the range must divide by it.
 * @param valueTextSuffix appended to the displayed number - "dp" on the sliders
 *   whose setting is a length, so the value text says the unit the summary
 *   talks about instead of a bare figure.
 */
@Composable
private fun IntSlider(
    value: Int,
    min: Int,
    max: Int,
    title: String,
    summary: String,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    step: Int = 1,
    valueTextSuffix: String? = null,
    // Turns the stored number into the text shown at the end of the row. The
    // default prints the stored value; a setting whose stored unit is not the
    // unit its summary talks about - half-dp, say - supplies its own, so the
    // row reads in the unit the user is being asked to think in.
    valueFormatter: (Int) -> String = { it.toString() },
) {
    SliderPreference(
        value = value.toFloat(),
        // Rounded here rather than stored rounded: the slider reports floats,
        // and every preset in this screen is a whole number. Snapping through
        // [step] keeps a wide range (100..900) from producing values no font
        // actually ships.
        onValueChange = { raw ->
            val snapped = Math.round(raw / step) * step
            onValueChange(snapped.coerceIn(min, max))
        },
        title = title,
        summary = summary,
        valueText = valueFormatter(value) + (valueTextSuffix ?: ""),
        enabled = enabled,
        valueRange = min.toFloat()..max.toFloat(),
        steps = ((max - min) / step - 1).coerceAtLeast(0),
        insideMargin = SettingsItemMargin,
    )
}

/**
 * The reason a gated row is greyed out, or null when it is not gated at all.
 *
 * <p>Each gate is a dependency paired with the title of the row that owns it.
 * The first dependency that is switched off wins, so a row that needs two of
 * them names the one the user is likeliest to fix first — reading order, top
 * to bottom. Returning null rather than a generic sentence is what lets the
 * caller pass the result straight to `TooltipBox(enabled = ...)`: a row that is
 * live has nothing to explain, and a tooltip that says nothing would still eat
 * the long-press.
 */
@Composable
private fun gateHint(vararg gates: Pair<Boolean, Int>): String? {
    val missing = gates.firstOrNull { !it.first } ?: return null
    return stringResource(R.string.gate_hint, stringResource(missing.second))
}

private val RoleColor.colorFieldLabelRes: Int
    get() = R.string.color_field_summary

private fun colorFor(settings: TrioSettings, role: RoleColor, onDark: Boolean): Int =
    when (role) {
        RoleColor.Critical -> if (onDark) settings.criticalOnDark else settings.criticalOnLight
        RoleColor.Low -> if (onDark) settings.lowOnDark else settings.lowOnLight
        RoleColor.Charging -> if (onDark) settings.chargingOnDark else settings.chargingOnLight
    }

/** Signal levels the preview shows; the middle of each range. */
private const val WIFI_LEVEL = 3
private const val MOBILE_LEVEL = 4

/** Font weights snap in hundreds: 100, 200, ... 900. */
private const val WEIGHT_STEP = 100

/** Burn-in period snaps in tens of seconds: 10, 20, ... 300. */
private const val BURN_IN_PERIOD_STEP = 10

/** What the renderer treats as "no Wi-Fi ink": the value drops to the centre. */
private const val NO_WIFI_LEVEL = -1

/**
 * Preview glyph edge. Sized so the eight cells still fit one row inside a 368.dp
 * card: eight of these plus seven 4.dp gutters come to 340.dp, clear of the
 * 344.dp content width, and the cell that is not a glyph has no fixed aspect of
 * its own to preserve.
 */
private val PREVIEW_SIZE = 39.dp

/**
 * Stand-in label for the type preview cells; the real one comes from SystemUI.
 *
 * <p>Deliberately the "5GA" form rather than "5G": the trailing A is what the
 * suffix-scale row changes, so this is the only cell where that setting can be
 * seen at all.
 */
private const val MOBILE_TYPE_PREVIEW = "5GA"

/**
 * Stand-in per-SIM levels for the preview: SIM 1 one step down from SIM 2, so a
 * preview that has quietly collapsed to a single row still looks wrong rather
 * than looking like a plausible single-SIM reading.
 */
private val DUAL_SIM_PREVIEW = intArrayOf(2, 4)

/** Palette for the activation card, sampled off the reference screenshot. */
private val ActivatedContainer = Color(0xFFE4F9E5)
private val ActivatedContent = Color(0xFF101010)
private val ActivatedBadge = Color(0xFF7FD78A)

/** Dark-mode counterparts; the reference only shows the light theme. */
private val ActivatedContainerDark = Color(0xFF1D2A1F)
private val ActivatedBadgeDark = Color(0xFF6FC77C)

/**
 * True when the app is rendering its dark palette, following the system setting
 * too. Miuix exposes the mode rather than a resolved boolean, so the system case
 * has to be resolved here.
 */
@Composable
private fun isDarkTheme(): Boolean = MiuixTheme.colorSchemeMode == ColorSchemeMode.Dark ||
    (MiuixTheme.colorSchemeMode == ColorSchemeMode.System && isSystemInDarkTheme())

/** Everything the about card renders about this installation. */
private data class ModuleInfo(
    val moduleVersion: String,
    val systemVersion: String,
    val androidVersion: String,
    val deviceModel: String,
)

/**
 * Reads the values that never change while the process lives.
 *
 * [Build.VERSION.INCREMENTAL] and [Build.DISPLAY] are both shown when they differ:
 * on MIUI the former carries the ROM build (OS4.0.0.27.XNCCNXM) and the latter the
 * underlying AOSP build id, and either one alone leaves the user guessing.
 */
private fun readModuleInfo(context: Context): ModuleInfo {
    val packageInfo = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }.getOrNull()
    val versionName = packageInfo?.versionName ?: "?"
    val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0L
    val systemVersion = listOf(Build.VERSION.INCREMENTAL, Build.DISPLAY)
        .filter { !it.isNullOrBlank() }
        .distinct()
        .joinToString(" · ")
    return ModuleInfo(
        moduleVersion = "$versionName ($versionCode)",
        systemVersion = systemVersion.ifBlank { "?" },
        androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        deviceModel = readDeviceName(context),
    )
}

/**
 * The name the user sees in Settings, not the one printed on the box.
 *
 * <p>[Build.MODEL] is the SKU (`23127PN0CC`); the marketing name (`Xiaomi 14`)
 * only exists as [Settings.Global.DEVICE_NAME], and `Build.MARKET_NAME` is not
 * present in this SDK. A device that has never had the setting written falls
 * back to the model rather than showing an empty row.
 */
private fun readDeviceName(context: Context): String {
    val name = runCatching {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
    }.getOrNull()
    return name?.takeIf { it.isNotBlank() } ?: Build.MODEL.orEmpty()
}

/**
 * How many packages the framework will load this module into; the reference page
 * shows an equivalent count, and it is the fastest way to spot a mis-scoped
 * install.
 */
private fun scopeCount(service: XposedService?): Int {
    if (service == null) return 0
    return runCatching { service.scope.size }.getOrDefault(0)
}

/**
 * How many of the appearance switches are on, as a single at-a-glance figure.
 *
 * <p>Derived from [TrioAppearance] rather than read off [TrioSettings] directly.
 * The switches gate one another, and the count used to ignore both the master
 * switch and those gates, so it could report a healthy total while the glyph
 * drew nothing at all.
 */
private fun enabledCount(settings: TrioSettings): Int {
    val a = TrioAppearance.of(settings)
    if (!a.glyph) return 0
    return listOf(
        a.wifi,
        // The reading counts in either position: in the ring it is the dot row,
        // outside it is the bars the out-of-ring view draws.
        a.mobile,
        // The second row only exists when the mobile level is drawn as dots, so
        // out of ring - where nothing is drawn along the bottom at all - it is
        // not a second thing on screen however the switch is set.
        a.dualSim && a.signalDots(),
        a.value,
        a.drawsBolt(),
        // The network type is a three-way mode now; anything but "off" counts as on.
        a.typeAnywhere(),
    ).count { it }
}

/**
 * Every getter here is a binder call into the framework daemon, so all of them are
 * wrapped: a service that died between binding and this call must degrade to
 * [unknown] rather than take the settings screen down with it.
 */
private fun readFrameworkInfo(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    return runCatching {
        val name = service.frameworkName
        val version = service.frameworkVersion
        val code = service.frameworkVersionCode
        "$name $version ($code) API ${service.apiVersion}"
    }.getOrDefault(unknown)
}

private fun readScope(service: XposedService?, unknown: String): String {
    if (service == null) return unknown
    val packages = runCatching { service.scope }.getOrNull()
    if (packages.isNullOrEmpty()) return unknown
    return packages.joinToString("\n")
}

/**
 * 模块是否真的注入到了 System UI。
 *
 * <p>`xposedService != null` 只说明 LSPosed 管理器在线；模块仍可能没被勾选、或没勾作用域，
 * 那样它根本不会被加载进 System UI。框架通过 [XposedService.getRunningTargets] 报告它已注入
 * 的进程，System UI 在这个列表里才算「已激活」。
 *
 * <p>查询本身抛异常时一律按「未激活」处理：误放行比短暂锁住更糟，3 秒轮询会自行恢复。
 */
private const val SYSTEM_UI_TARGET = "com.android.systemui"

private suspend fun computeModuleActive(): Boolean = withContext(Dispatchers.IO) {
    val svc = SamsungDuoApp.xposedService ?: return@withContext false
    runCatching {
        svc.getRunningTargets().any { it.processName == SYSTEM_UI_TARGET }
    }.getOrElse { false }
}

/**
 * 屏幕级激活状态：绑定/解绑立即重查，另每 3 秒轮询一次 ——
 * 用户可能在本页开着时去 LSPosed 勾选模块，那条路径不会触发 binder 变化。
 */
@Composable
private fun rememberModuleActive(): Boolean {
    var active by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val listener: (XposedService?) -> Unit = {
            scope.launch { active = computeModuleActive() }
        }
        SamsungDuoApp.addServiceListener(listener)
        listener(SamsungDuoApp.xposedService)
        onDispose { SamsungDuoApp.removeServiceListener(listener) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            active = computeModuleActive()
        }
    }
    return active
}
