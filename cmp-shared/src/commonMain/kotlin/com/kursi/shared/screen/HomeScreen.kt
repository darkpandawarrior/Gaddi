package com.kursi.shared.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.persona.BotPersona
import com.kursi.ai.persona.PersonaRoster
import com.kursi.core.prefs.AppPrefs
import com.kursi.designsystem.*
import com.kursi.shared.strings.GaddiStrings
import com.kursi.shared.strings.LocalGaddiStrings
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_career_mini_strip
import kursi.core.designsystem.generated.resources.a11y_hero_play_button
import kursi.core.designsystem.generated.resources.a11y_resume_in_progress
import kursi.core.designsystem.generated.resources.a11y_resume_match
import kursi.core.designsystem.generated.resources.brand_wordmark
import kursi.core.designsystem.generated.resources.brass_seal_authority
import kursi.core.designsystem.generated.resources.brass_seal_ruin
import kursi.core.designsystem.generated.resources.cta_enter
import kursi.core.designsystem.generated.resources.home_hero_subtitle
import kursi.core.designsystem.generated.resources.home_hero_title
import kursi.core.designsystem.generated.resources.home_more_modes_header
import kursi.core.designsystem.generated.resources.home_progress_header
import kursi.core.designsystem.generated.resources.label_approved_stamp
import kursi.core.designsystem.generated.resources.label_games_short
import kursi.core.designsystem.generated.resources.label_on_duty
import kursi.core.designsystem.generated.resources.label_unavailable
import kursi.core.designsystem.generated.resources.label_win_rate_short
import kursi.core.designsystem.generated.resources.label_wins_short
import org.jetbrains.compose.resources.stringResource
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where an official-seal rim tick starts, as a fraction of the seal radius. */
private const val SealRayInnerFraction = 0.86f

// ── Ticker headlines ──────────────────────────────────────────────────────────

private val TICKER_LINES =
    listOf(
        "ROZNAMCHA ⌁  Netaji Vachan promises to keep every promise he forgot to make.",
        "ROZNAMCHA ⌁  Babu Filewala locates a file last seen during the previous government.",
        "ROZNAMCHA ⌁  Seth Khokhawala donates generously; receipt unavailable.",
        "ROZNAMCHA ⌁  Inspector Damaad clarifies the raid was purely ceremonial.",
        "ROZNAMCHA ⌁  Madam Sarpanch wins unanimously; turnout disputed by mathematics.",
        "ROZNAMCHA ⌁  Vakil Loophole files a stay against the laws of physics.",
        "ROZNAMCHA ⌁  Jugaadu Chhotu fixes the budget with one zip-tie and confidence.",
        "ROZNAMCHA ⌁  Dalla Tiwari \"knows a guy\" for that too.",
        "ROZNAMCHA ⌁  Maaji Anna fasts in protest; breaks for tea.",
        "ROZNAMCHA ⌁  Bhai Teja offers protection from problems Bhai Teja invented.",
    )

// ── On-duty captions per persona ──────────────────────────────────────────────

private fun onDutyCaption(personaId: String): String =
    when (personaId) {
        "netaji_vachan" -> "On duty. Will see you after the rally."
        "bhai_teja" -> "On duty. Smiling is not permitted at this counter."
        "babu_filewala" -> "On duty. Kindly join the queue (no queue exists)."
        "jugaadu_chhotu" -> "On duty. Will fix everything. Probably."
        "vakil_loophole" -> "On duty. Filing objections. Against everything."
        "inspector_damaad" -> "On duty. Under investigation. Himself."
        "seth_khokhawala" -> "On duty. Cash counter closes when convenient."
        "madam_sarpanch" -> "On duty. All decisions final. Husband concurs."
        "dalla_tiwari" -> "On duty. Available for introductions only."
        "maaji_anna" -> "On duty. Fasting. But available for donations."
        else -> "On duty. File pending."
    }

// ─────────────────────────────────────────────────────────────────────────────

/**
 * HomeScreen — License Raj Deco "waiting room outside the Minister's office."
 *
 * §3 spec from 17_app_plan.md + 17b_start_page.md.
 * Regions: A=ticker, B=brass seal, C=wordmark+tagline, D=persona on duty, E=CTA stack, F=footer.
 */
@Composable
fun HomeScreen(
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onOnlineTap: () -> Unit,
    launchIndex: Int = 0, // rotates duty persona
    modifier: Modifier = Modifier,
    // KISSA / Story mode entry.
    onStory: () -> Unit = {},
    // M5 ONBOARD — Pehli Hazri / interactive tutorial entry.
    onTutorial: () -> Unit = {},
    // M3 — lifetime career ledger surfaced as a compact strip (hidden when no games played).
    ledger: com.kursi.core.prefs.StatsLedger =
        com.kursi.core.prefs
            .StatsLedger(),
    onCareer: () -> Unit = {},
    // M3 — an in-progress match to offer resuming. Null = no resumable game.
    resumeLabel: String? = null,
    onResume: () -> Unit = {},
    // M6d — ranked ELO standing surfaced as a compact strip + the local leaderboard entry.
    ranked: com.kursi.core.prefs.RankedStanding =
        com.kursi.core.prefs
            .RankedStanding(),
    onLeaderboard: () -> Unit = {},
    // M6d — daily challenge (Aaj ki Chunauti) entry + standing.
    daily: com.kursi.core.prefs.DailyStanding =
        com.kursi.core.prefs
            .DailyStanding(),
    todayDailyDone: Boolean = false,
    onDaily: () -> Unit = {},
    // M6e — gauntlet ladder (Tarakki ki Seedhi) entry + progress, and the watch-only Tamasha demo.
    gauntlet: com.kursi.core.prefs.GauntletProgress =
        com.kursi.core.prefs
            .GauntletProgress(),
    gauntletRungCount: Int = 0,
    onGauntlet: () -> Unit = {},
    onSpectate: () -> Unit = {},
    // Render harness: pre-select a mode tile so the right-panel preview is visible on frame 1.
    initialSelectedKey: String? = null,
) {
    val persona =
        remember(launchIndex) {
            PersonaRoster.ALL[launchIndex % PersonaRoster.ALL.size]
        }

    // Read reduced-motion once per composition — stable snapshot from storage.
    val reducedMotion = remember { AppPrefs().reducedMotion }

    // ── Staggered entrance ────────────────────────────────────────────────────
    // visible flips true on first composition; animate* then tween to 1f / 0.dp.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val enterDuration = if (reducedMotion) 0 else 420

    val wordmarkAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 0),
        label = "wordmarkAlpha",
    )
    val wordmarkSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 22f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 0),
        label = "wordmarkSlide",
    )
    val taglineAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 80),
        label = "taglineAlpha",
    )
    val sealAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 120),
        label = "sealAlpha",
    )
    val sealScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 120),
        label = "sealScale",
    )
    val personaAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 180),
        label = "personaAlpha",
    )
    val personaSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 16f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 180),
        label = "personaSlide",
    )
    val ctaAlpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 260),
        label = "ctaAlpha",
    )
    val ctaSlide by animateFloatAsState(
        targetValue = if (visible) 0f else 18f,
        animationSpec = tween(enterDuration, easing = FastOutSlowInEasing, delayMillis = 260),
        label = "ctaSlide",
    )

    // ── Continuous ambient: seal slow rotation ────────────────────────────────
    val infiniteAmbient = rememberInfiniteTransition(label = "ambient")
    val sealRotation by if (reducedMotion) {
        remember { mutableStateOf(0f) }.let { s -> derivedStateOf { s.value } }
    } else {
        infiniteAmbient.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 60_000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
            label = "sealRotation",
        )
    }

    // ── Continuous ambient: persona card gentle bob ───────────────────────────
    val personaBob by if (reducedMotion) {
        remember { mutableStateOf(0f) }.let { s -> derivedStateOf { s.value } }
    } else {
        infiniteAmbient.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(durationMillis = 3200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            label = "personaBob",
        )
    }
    // Bob offset: ±4dp mapped from [0,1] → [-4, +4]
    val personaBobPx = (personaBob * 2f - 1f) * 4f

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .litGround()
                .drawBehind { drawHomeDepth() },
    ) {
        // 840dp threshold: tablets (≥600dp) get the two-column layout, phones (<840dp) get the
        // mobile-first compact layout. The original 1024dp gate was leaving 600–900dp tablets on
        // the cramped single-column path, wasting the extra surface area.
        val isExpanded = maxWidth >= 840.dp

        Column(modifier = Modifier.fillMaxSize()) {
            // ── A: Teleprinter ticker ──────────────────────────────────────────
            TeleprinterTicker(
                lines = TICKER_LINES,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxWidth().height(44.dp),
            )

            // ── Main body ─────────────────────────────────────────────────────
            if (isExpanded) {
                ExpandedHomeLayout(
                    persona = persona,
                    onNewGame = onNewGame,
                    onGazette = onGazette,
                    onSettings = onSettings,
                    onOnlineTap = onOnlineTap,
                    onStory = onStory,
                    onTutorial = onTutorial,
                    wordmarkAlpha = wordmarkAlpha,
                    wordmarkSlide = wordmarkSlide,
                    taglineAlpha = taglineAlpha,
                    sealAlpha = sealAlpha,
                    sealScale = sealScale,
                    sealRotation = sealRotation,
                    personaAlpha = personaAlpha,
                    personaSlide = personaSlide,
                    personaBobPx = personaBobPx,
                    ctaAlpha = ctaAlpha,
                    ctaSlide = ctaSlide,
                    ledger = ledger,
                    onCareer = onCareer,
                    resumeLabel = resumeLabel,
                    onResume = onResume,
                    ranked = ranked,
                    onLeaderboard = onLeaderboard,
                    daily = daily,
                    todayDailyDone = todayDailyDone,
                    onDaily = onDaily,
                    gauntlet = gauntlet,
                    gauntletRungCount = gauntletRungCount,
                    onGauntlet = onGauntlet,
                    onSpectate = onSpectate,
                    initialSelectedKey = initialSelectedKey,
                )
            } else {
                CompactHomeLayout(
                    persona = persona,
                    onNewGame = onNewGame,
                    onGazette = onGazette,
                    onSettings = onSettings,
                    onStory = onStory,
                    onTutorial = onTutorial,
                    wordmarkAlpha = wordmarkAlpha,
                    wordmarkSlide = wordmarkSlide,
                    taglineAlpha = taglineAlpha,
                    sealAlpha = sealAlpha,
                    sealScale = sealScale,
                    sealRotation = sealRotation,
                    personaAlpha = personaAlpha,
                    personaSlide = personaSlide,
                    personaBobPx = personaBobPx,
                    ctaAlpha = ctaAlpha,
                    ctaSlide = ctaSlide,
                    ledger = ledger,
                    onCareer = onCareer,
                    resumeLabel = resumeLabel,
                    onResume = onResume,
                    ranked = ranked,
                    onLeaderboard = onLeaderboard,
                    daily = daily,
                    todayDailyDone = todayDailyDone,
                    onDaily = onDaily,
                    gauntlet = gauntlet,
                    gauntletRungCount = gauntletRungCount,
                    onGauntlet = onGauntlet,
                    onSpectate = onSpectate,
                )
            }
        }
    }
}

// ─────────────────────────── Expanded layout (≥1024dp) ───────────────────────

@Composable
private fun ColumnScope.ExpandedHomeLayout(
    persona: BotPersona,
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onOnlineTap: () -> Unit,
    onStory: () -> Unit,
    onTutorial: () -> Unit,
    wordmarkAlpha: Float,
    wordmarkSlide: Float,
    taglineAlpha: Float,
    sealAlpha: Float,
    sealScale: Float,
    sealRotation: Float,
    personaAlpha: Float,
    personaSlide: Float,
    personaBobPx: Float,
    ctaAlpha: Float,
    ctaSlide: Float,
    ledger: com.kursi.core.prefs.StatsLedger,
    onCareer: () -> Unit,
    resumeLabel: String?,
    onResume: () -> Unit,
    ranked: com.kursi.core.prefs.RankedStanding,
    onLeaderboard: () -> Unit,
    daily: com.kursi.core.prefs.DailyStanding,
    todayDailyDone: Boolean,
    onDaily: () -> Unit,
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    gauntletRungCount: Int,
    onGauntlet: () -> Unit,
    onSpectate: () -> Unit,
    initialSelectedKey: String? = null,
) {
    val s = LocalGaddiStrings.current
    var selectedKey by remember(initialSelectedKey) { mutableStateOf(initialSelectedKey) }
    val modes =
        remember(s) {
            listOf(
                HomeModeData(
                    key = "new_game",
                    icon = "♛",
                    accentArgb = 0xFFC9873AL,
                    label = s.homeCtaNewGame,
                    sublabel = s.homeCtaNewGameSub,
                    description =
                        "Challenge the Cabinet in a single-player match. Outmanoeuvre AI opponents, seize the Gaddi, and survive the " +
                            "vote of no " +
                            "confidence.",
                    details = listOf("Players" to "2 – 5", "Opponent" to "AI Cabinet", "Duration" to "~20 min"),
                    isHero = true,
                    onClick = onNewGame,
                ),
                HomeModeData(
                    key = "story",
                    icon = "◉",
                    accentArgb = 0xFF2D1F5EL,
                    label = s.homeCtaStory,
                    sublabel = s.homeCtaStorySub,
                    description =
                        "Enter the Darbar. Bots talk, conspire, and betray each other in real time. You observe the room and pull the " +
                            "strings from " +
                            "the shadows.",
                    details = listOf("Format" to "Narrative", "Control" to "Indirect", "Bots" to "Fully reactive"),
                    onClick = onStory,
                ),
                HomeModeData(
                    key = "gauntlet",
                    icon = "▲",
                    accentArgb = 0xFF1B4A2EL,
                    label = s.homeCtaGauntlet,
                    sublabel = s.homeCtaGauntletSub,
                    description =
                        "Climb from Peon to Prime Minister. Each rung is a harder table — beat it to advance. Lose and the rung resets. " +
                            "Progress " +
                            "persists between sessions.",
                    details = listOf("Format" to "Gauntlet", "Tiers" to "Progressive", "Progress" to "Persistent"),
                    onClick = onGauntlet,
                ),
                HomeModeData(
                    key = "spectate",
                    icon = "◎",
                    accentArgb = 0xFF1A2C4AL,
                    label = s.homeCtaSpectate,
                    sublabel = s.homeCtaSpectateSub,
                    description =
                        "Watch a full game with no input required. Today's Tamasha seed is deterministic — the exact same table plays " +
                            "out for every " +
                            "player.",
                    details = listOf("Input" to "None", "Seed" to "Today's date", "Players" to "4 bots"),
                    onClick = onSpectate,
                ),
                HomeModeData(
                    key = "tutorial",
                    icon = "✎",
                    accentArgb = 0xFF1A3A3AL,
                    label = s.homeCtaTutorial,
                    sublabel = s.homeCtaTutorialSub,
                    description =
                        "Your first day in the daftar. The game walks you through every rule interactively, one move at a " +
                            "time. No reading required.",
                    details = listOf("Format" to "Interactive", "Pace" to "Self-guided", "For" to "New players"),
                    onClick = onTutorial,
                ),
                HomeModeData(
                    key = "rules",
                    icon = "§",
                    accentArgb = 0xFF4A1A1AL,
                    label = s.homeCtaRules,
                    sublabel = s.homeCtaRulesSub,
                    description =
                        "The complete NIYAM Gazette. Every card, every action class, who beats whom, and the full laws of succession — " +
                            "in one " +
                            "reference.",
                    details = listOf("Contents" to "Full rules", "Beat chart" to "Included", "Cards" to "All 15"),
                    onClick = onGazette,
                ),
                HomeModeData(
                    key = "settings",
                    icon = "⚙",
                    accentArgb = 0xFF1A1E2EL,
                    label = s.homeCtaSettings,
                    sublabel = s.homeCtaSettingsSub,
                    description =
                        "Adjust sound, motion reduction, language, and AI difficulty defaults. All preferences carry across " +
                            "every game session.",
                    details = listOf("Sound" to "On / Off", "Language" to "EN / HI", "Motion" to "Full / Reduced"),
                    onClick = onSettings,
                ),
                HomeModeData(
                    key = "multiplayer",
                    icon = "⊕",
                    accentArgb = 0xFF2A1A1AL,
                    label = s.homeCtaMultiplayer,
                    sublabel = s.homeCtaMultiplayerSub,
                    description =
                        "Open the Mehfil to outsiders. Create a private room and share the code, or drop into a quick match. Works " +
                            "online and on " +
                            "local network — no account required.",
                    details = listOf("Players" to "2 – 5", "Network" to "Online + LAN", "Rooms" to "Private / Quick"),
                    onClick = onOnlineTap,
                ),
            )
        }
    val selectedMode = modes.find { it.key == selectedKey }

    // Two even columns that together fill the whole body — the left "file spine"
    // (wordmark + CTAs) and the right "stamped sidebar" (seal + persona on duty)
    // each take real width and full height, so neither the canvas nor the right
    // half reads as dead space. Generous symmetric padding keeps it gallery-framed.
    Row(
        modifier =
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 72.dp, vertical = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left 52%: the file spine — scrollable so all 8 mode tiles are reachable.
        Box(modifier = Modifier.weight(0.52f).fillMaxHeight()) {
            val scrollState = rememberScrollState()
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState),
                verticalArrangement = Arrangement.Top,
            ) {
                GaddiWordmark(
                    displaySize = 84,
                    wordmarkAlpha = wordmarkAlpha,
                    wordmarkSlide = wordmarkSlide,
                    taglineAlpha = taglineAlpha,
                )
                Spacer(Modifier.height(14.dp))
                HomeContinuityStrips(
                    ledger = ledger,
                    onCareer = onCareer,
                    resumeLabel = resumeLabel,
                    onResume = onResume,
                    ranked = ranked,
                    onLeaderboard = onLeaderboard,
                    daily = daily,
                    todayDailyDone = todayDailyDone,
                    onDaily = onDaily,
                    gauntlet = gauntlet,
                    gauntletRungCount = gauntletRungCount,
                    onGauntlet = onGauntlet,
                )
                Spacer(Modifier.height(8.dp))
                ModeGrid(
                    modes = modes,
                    selectedKey = selectedKey,
                    onSelect = { selectedKey = it },
                    ctaAlpha = ctaAlpha,
                    ctaSlide = ctaSlide,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(32.dp))
            }
            // Fade at the bottom to signal more content below
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, BrandTokens.TeakInk.copy(alpha = 0.92f)),
                            ),
                        ),
            )
        }

        // Right 48%: mode preview when a tile is selected, persona panel otherwise.
        // the right field. Large seal + a brass divider + the persona-on-duty card,
        // all centred in a bordered panel that fills the column height. This is what
        // fills the former void and balances the left spine.
        Crossfade(
            targetState = selectedMode,
            modifier = Modifier.weight(0.48f).fillMaxHeight(),
            label = "rightPanel",
        ) { mode ->
            if (mode != null) {
                ModePreviewPanel(mode = mode, personaAlpha = personaAlpha, personaSlide = personaSlide)
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier =
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth()
                                // Raised lit surface — real cast shadow, no thin brass border framing
                                // an otherwise-flat panel (non-negotiable #1).
                                .shadow(
                                    14.dp,
                                    RoundedCornerShape(20.dp),
                                    clip = false,
                                    ambientColor = Color.Black,
                                    spotColor = BrandTokens.TeakInk,
                                ).clip(RoundedCornerShape(20.dp))
                                .background(Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark)))
                                .padding(horizontal = 40.dp, vertical = 48.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = s.homeRosterHeader,
                            style =
                                GaddiType.caption.copy(
                                    fontSize = 11.sp,
                                    letterSpacing = 3.sp,
                                    fontWeight = FontWeight.Bold,
                                ),
                            color = BrandTokens.BrassAged,
                            modifier = Modifier.graphicsLayer { alpha = sealAlpha },
                        )
                        Spacer(Modifier.height(28.dp))
                        BrassSeal(
                            modifier =
                                Modifier
                                    .size(168.dp)
                                    .graphicsLayer {
                                        alpha = sealAlpha
                                        scaleX = sealScale
                                        scaleY = sealScale
                                        rotationZ = sealRotation
                                    },
                        )
                        Spacer(Modifier.height(32.dp))
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(1.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                Color.Transparent,
                                                BrandTokens.BrassAged.copy(alpha = 0.6f),
                                                BrandTokens.GoldAntique.copy(alpha = 0.6f),
                                                BrandTokens.BrassAged.copy(alpha = 0.6f),
                                                Color.Transparent,
                                            ),
                                        ),
                                    ),
                        )
                        Spacer(Modifier.height(32.dp))
                        PersonaOnDutyCard(
                            persona = persona,
                            enlarged = true,
                            personaAlpha = personaAlpha,
                            personaSlide = personaSlide,
                            bobOffsetPx = personaBobPx,
                        )
                    }
                }
            }
        }
    }

    // ── F: Footer ──────────────────────────────────────────────────────────────
    HomeFooter()
}

// ─────────────────────────── Compact layout (<840dp) ─────────────────────────
//
// Mobile-first hierarchy:
//   1. Wordmark (compact header — brand anchor)
//   2. Resume strip — FIRST if a match is in progress (most time-sensitive action)
//   3. Hero "NAYA KHEL" button — primary CTA, large and unmissable
//   4. Mode rail — horizontal scroll of secondary modes (small, non-blocking)
//   5. Continuity dashboard — career/daily/ranked in a single compact strip
//   6. Persona teaser — subtle "who's on duty" chip
//   7. Footer

@Composable
private fun ColumnScope.CompactHomeLayout(
    persona: BotPersona,
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onStory: () -> Unit,
    onTutorial: () -> Unit,
    wordmarkAlpha: Float,
    wordmarkSlide: Float,
    taglineAlpha: Float,
    sealAlpha: Float,
    sealScale: Float,
    sealRotation: Float,
    personaAlpha: Float,
    personaSlide: Float,
    personaBobPx: Float,
    ctaAlpha: Float,
    ctaSlide: Float,
    ledger: com.kursi.core.prefs.StatsLedger,
    onCareer: () -> Unit,
    resumeLabel: String?,
    onResume: () -> Unit,
    ranked: com.kursi.core.prefs.RankedStanding,
    onLeaderboard: () -> Unit,
    daily: com.kursi.core.prefs.DailyStanding,
    todayDailyDone: Boolean,
    onDaily: () -> Unit,
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    gauntletRungCount: Int,
    onGauntlet: () -> Unit,
    onSpectate: () -> Unit,
) {
    val s = LocalGaddiStrings.current

    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
        // Background watermark seal — decorative, does not occupy content space
        BrassSeal(
            modifier =
                Modifier
                    .size(260.dp)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = sealAlpha * 0.06f
                        scaleX = sealScale
                        scaleY = sealScale
                        rotationZ = sealRotation
                    },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ① Wordmark — compact header, brass gradient
            GaddiWordmark(
                displaySize = 44,
                wordmarkAlpha = wordmarkAlpha,
                wordmarkSlide = wordmarkSlide,
                taglineAlpha = taglineAlpha,
            )

            // ② Resume strip — always first when a match is in-progress
            if (resumeLabel != null) {
                ResumeStripProminent(label = resumeLabel, onResume = onResume)
            }

            // ③ Hero CTA — primary action, large and physically-stamped
            HeroPlayButton(
                persona = persona,
                personaAlpha = personaAlpha,
                personaBobPx = personaBobPx,
                ctaAlpha = ctaAlpha,
                ctaSlide = ctaSlide,
                onClick = onNewGame,
            )

            // ④ Secondary mode rail — horizontal scroll, non-blocking
            ModeRail(
                s = s,
                onStory = onStory,
                onGauntlet = onGauntlet,
                onTutorial = onTutorial,
                onSpectate = onSpectate,
                onGazette = onGazette,
                onSettings = onSettings,
                ctaAlpha = ctaAlpha,
                ctaSlide = ctaSlide,
            )

            // ⑤ Continuity dashboard — all active progress, condensed
            CompactContinuityDashboard(
                ledger = ledger,
                onCareer = onCareer,
                ranked = ranked,
                onLeaderboard = onLeaderboard,
                daily = daily,
                todayDailyDone = todayDailyDone,
                onDaily = onDaily,
                gauntlet = gauntlet,
                gauntletRungCount = gauntletRungCount,
                onGauntlet = onGauntlet,
            )

            // ⑥ Persona teaser — subtle, below all actions
            PersonaTeaserChip(
                persona = persona,
                alpha = personaAlpha,
                translationY = personaSlide + personaBobPx,
            )
        }
    }

    HomeFooter()
}

// ─────────────────────────── Hero Play Button ─────────────────────────────────

@Composable
private fun HeroPlayButton(
    persona: BotPersona,
    personaAlpha: Float,
    personaBobPx: Float,
    ctaAlpha: Float,
    ctaSlide: Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1f,
        animationSpec = tween(80, easing = FastOutSlowInEasing),
        label = "heroPress",
    )

    val brassGradient =
        Brush.linearGradient(
            listOf(BrandTokens.BrassDark, BrandTokens.BrassAged, BrandTokens.GoldAntique, BrandTokens.BrassAged),
        )
    val heroPlayDesc = stringResource(Res.string.a11y_hero_play_button)

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = ctaAlpha
                    translationY = ctaSlide
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(RoundedCornerShape(14.dp))
                .background(brassGradient)
                .border(
                    2.dp,
                    Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark, BrandTokens.GoldAntique)),
                    RoundedCornerShape(14.dp),
                ).semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = heroPlayDesc
                }.clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 22.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(Res.string.home_hero_title),
                    style =
                        MaterialTheme.typography.displayLarge.copy(
                            fontSize = 32.sp,
                            letterSpacing = 1.sp,
                        ),
                    color = BrandTokens.TeakDark,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(Res.string.home_hero_subtitle),
                    style = GaddiType.body.copy(fontSize = 12.sp, fontStyle = FontStyle.Italic),
                    color = BrandTokens.BrassDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Stamp impression column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                // Persona monogram as opponent teaser
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BrandTokens.TeakDark.copy(alpha = 0.35f))
                            .border(1.5.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), CircleShape)
                            .graphicsLayer {
                                alpha = personaAlpha
                                translationY = personaBobPx
                            },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = persona.monogram,
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.GoldAntique,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(BrandTokens.StampRed.copy(alpha = 0.92f))
                            .border(1.dp, BrandTokens.Oxblood, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.label_approved_stamp),
                        style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.PaperCream,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Prominent Resume Strip ──────────────────────────

@Composable
private fun ResumeStripProminent(
    label: String,
    onResume: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = tween(80),
        label = "resumePress",
    )
    val resumeDesc = stringResource(Res.string.a11y_resume_match, label)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(BrandTokens.GoldAntique.copy(alpha = 0.22f), BrandTokens.BrassAged.copy(alpha = 0.14f)),
                    ),
                ).border(2.dp, BrandTokens.GoldAntique.copy(alpha = 0.75f), RoundedCornerShape(10.dp))
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = resumeDesc
                }.clickable(interactionSource = interactionSource, indication = null, onClick = onResume)
                .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("⟳", style = GaddiType.title.copy(fontSize = 22.sp), color = BrandTokens.GoldAntique)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    LocalGaddiStrings.current.homeResumeLabel,
                    style = GaddiType.title.copy(fontSize = 15.sp),
                    color = BrandTokens.GoldAntique,
                )
                Text(
                    label,
                    style = GaddiType.caption.copy(fontSize = 11.sp),
                    color = GaddiNeutrals.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text("›", style = GaddiType.title.copy(fontSize = 20.sp), color = BrandTokens.GoldAntique)
        }
    }
}

// ─────────────────────────── Mode Rail (horizontal scroll) ────────────────────

private data class ModeRailItem(
    val key: String,
    val icon: String,
    val label: String,
    val accentArgb: Long,
    val onClick: () -> Unit,
)

@Composable
private fun ModeRail(
    s: GaddiStrings,
    onStory: () -> Unit,
    onGauntlet: () -> Unit,
    onTutorial: () -> Unit,
    onSpectate: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    ctaAlpha: Float,
    ctaSlide: Float,
) {
    val modes =
        remember(s) {
            listOf(
                ModeRailItem("story", "◉", s.homeCtaStory, 0xFF2D1F5EL, onStory),
                ModeRailItem("gauntlet", "▲", s.homeCtaGauntlet, 0xFF1B4A2EL, onGauntlet),
                ModeRailItem("tutorial", "✎", s.homeCtaTutorial, 0xFF1A3A3AL, onTutorial),
                ModeRailItem("watch", "◎", s.homeCtaSpectate, 0xFF1A2C4AL, onSpectate),
                ModeRailItem("rules", "§", s.homeCtaRules, 0xFF4A1A1AL, onGazette),
                ModeRailItem("settings", "⚙", s.homeCtaSettings, 0xFF1A1E2EL, onSettings),
            )
        }

    Column(
        modifier =
            Modifier.graphicsLayer {
                alpha = ctaAlpha
                translationY = ctaSlide
            },
    ) {
        Text(
            text = stringResource(Res.string.home_more_modes_header),
            style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 2.sp),
            color = GaddiNeutrals.TextMuted,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 16.dp),
        ) {
            items(modes) { mode ->
                ModeRailTile(mode = mode)
            }
        }
    }
}

@Composable
private fun ModeRailTile(mode: ModeRailItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.93f else 1f,
        animationSpec = tween(70, easing = FastOutSlowInEasing),
        label = "railTile_${mode.key}",
    )
    val accent = Color(mode.accentArgb)

    Column(
        modifier =
            Modifier
                .width(108.dp)
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }.clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.55f))
                .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = mode.label
                }.clickable(interactionSource = interactionSource, indication = null, onClick = mode.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(accent.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = mode.icon,
                style = GaddiType.title.copy(fontSize = 24.sp),
                color = Color.White.copy(alpha = 0.85f),
            )
        }
        Text(
            text = mode.label,
            style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.sp),
            color = GaddiNeutrals.TextPrimary,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
        )
    }
}

// ─────────────────────────── Compact Continuity Dashboard ────────────────────
// Condenses all progress signals (career, daily, ranked, gauntlet) into a
// single slim section below the primary action so new players don't drown.

@Composable
private fun CompactContinuityDashboard(
    ledger: com.kursi.core.prefs.StatsLedger,
    onCareer: () -> Unit,
    ranked: com.kursi.core.prefs.RankedStanding,
    onLeaderboard: () -> Unit,
    daily: com.kursi.core.prefs.DailyStanding,
    todayDailyDone: Boolean,
    onDaily: () -> Unit,
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    gauntletRungCount: Int,
    onGauntlet: () -> Unit,
) {
    val hasAnyProgress = ledger.games > 0 || !gauntlet.isFresh || daily.hasPlayed
    if (!hasAnyProgress) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.home_progress_header),
            style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 2.sp),
            color = GaddiNeutrals.TextMuted,
        )
        // Career stats row — always show if any games played
        if (ledger.games > 0) {
            CareerMiniStrip(ledger = ledger, onOpen = onCareer)
        }
        // Daily challenge pill — show when there's a daily to do (or done today)
        if (daily.hasPlayed || !todayDailyDone) {
            DailyChallengeStrip(
                daily = daily,
                todayDone = todayDailyDone,
                onStart = onDaily,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Gauntlet progress pill
        if (!gauntlet.isFresh && gauntletRungCount > 0) {
            GauntletStrip(
                gauntlet = gauntlet,
                total = gauntletRungCount,
                onOpen = onGauntlet,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        // Ranked strip
        RankedStrip(ranked = ranked, onOpen = onLeaderboard, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun CareerMiniStrip(
    ledger: com.kursi.core.prefs.StatsLedger,
    onOpen: () -> Unit,
) {
    val s = LocalGaddiStrings.current
    val careerMiniDesc = stringResource(Res.string.a11y_career_mini_strip, ledger.games, ledger.wins)
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(BrandTokens.BrassAged.copy(alpha = 0.08f))
                .border(1.dp, BrandTokens.BrassDark.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = careerMiniDesc
                }.clickable(onClick = onOpen)
                .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CareerStat(value = "${ledger.games}", label = stringResource(Res.string.label_games_short))
                CareerStat(value = "${ledger.wins}", label = stringResource(Res.string.label_wins_short))
                if (ledger.games > 0) {
                    val pct = (ledger.wins * 100 / ledger.games)
                    CareerStat(value = "$pct%", label = stringResource(Res.string.label_win_rate_short))
                }
            }
            Text(
                text = "›",
                style = GaddiType.title.copy(fontSize = 16.sp),
                color = BrandTokens.BrassAged,
            )
        }
    }
}

@Composable
private fun CareerStat(
    value: String,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = GaddiType.numeric.copy(fontSize = 16.sp),
            color = BrandTokens.GoldAntique,
        )
        Text(
            text = label,
            style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
            color = GaddiNeutrals.TextMuted,
        )
    }
}

// ─────────────────────────── Persona Teaser Chip ─────────────────────────────
// Tiny "who's on duty" teaser — replaces the large PersonaOnDutyCard in the
// compact layout; decorative, placed at the bottom of the scroll.

@Composable
private fun PersonaTeaserChip(
    persona: BotPersona,
    alpha: Float,
    translationY: Float,
) {
    val seatColor = Color(persona.seatColorArgb)
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    this.alpha = alpha
                    this.translationY = translationY
                }.clip(RoundedCornerShape(8.dp))
                .background(BrandTokens.TeakDark.copy(alpha = 0.6f))
                .border(1.dp, seatColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(seatColor.copy(alpha = 0.8f), seatColor.copy(alpha = 0.3f))))
                    .border(1.dp, BrandTokens.BrassAged.copy(alpha = 0.5f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                persona.monogram,
                style = GaddiType.caption.copy(fontSize = 9.sp),
                color = GaddiNeutrals.Cream,
                textAlign = TextAlign.Center,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${persona.name} · ${persona.title}",
                style = GaddiType.caption.copy(fontSize = 10.sp),
                color = GaddiNeutrals.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "\"${onDutyCaption(persona.id)}\"",
                style = GaddiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = stringResource(Res.string.label_on_duty),
            style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
            color = seatColor.copy(alpha = 0.7f),
        )
    }
}

/**
 * Resume + career strips shown between the wordmark/persona and the CTA stack. Each renders only
 * when there's something to show — a resumable in-progress match, and/or a non-empty lifetime
 * ledger — so a brand-new player's hub stays clean.
 */
@Composable
private fun HomeContinuityStrips(
    ledger: com.kursi.core.prefs.StatsLedger,
    onCareer: () -> Unit,
    resumeLabel: String?,
    onResume: () -> Unit,
    ranked: com.kursi.core.prefs.RankedStanding,
    onLeaderboard: () -> Unit,
    daily: com.kursi.core.prefs.DailyStanding,
    todayDailyDone: Boolean,
    onDaily: () -> Unit,
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    gauntletRungCount: Int,
    onGauntlet: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (resumeLabel != null) {
            ResumeStrip(label = resumeLabel, onResume = onResume)
        }
        // M6d — daily challenge + ranked rank/rating strips. The ranked strip always renders (even
        // provisional) so the player sees their starting rank; the daily strip drives Aaj ki Chunauti.
        DailyChallengeStrip(daily = daily, todayDone = todayDailyDone, onStart = onDaily, modifier = Modifier.fillMaxWidth())
        RankedStrip(ranked = ranked, onOpen = onLeaderboard, modifier = Modifier.fillMaxWidth())
        // M6e — gauntlet ladder progress strip (only once a run has started so a fresh hub stays clean).
        if (!gauntlet.isFresh && gauntletRungCount > 0) {
            GauntletStrip(gauntlet = gauntlet, total = gauntletRungCount, onOpen = onGauntlet, modifier = Modifier.fillMaxWidth())
        }
        CareerStrip(ledger = ledger, onOpen = onCareer, modifier = Modifier.fillMaxWidth())
    }
}

/** A "Tarakki ki Seedhi" progress strip — shows rungs cleared, links to the gauntlet ladder. */
@Composable
private fun GauntletStrip(
    gauntlet: com.kursi.core.prefs.GauntletProgress,
    total: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    val label = s.gauntletStripLabel(gauntlet.clearedCount.coerceIn(0, total), total)
    HairlineRow(
        onClick = onOpen,
        modifier =
            modifier.semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                contentDescription = "${s.homeCtaGauntlet}. $label"
            },
        verticalPadding = 11.dp,
    ) {
        Text("▲", style = GaddiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                s.homeCtaGauntlet,
                style = GaddiType.title.copy(fontSize = 14.sp),
                color = GaddiNeutrals.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                label,
                style = GaddiType.caption.copy(fontSize = 10.sp),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = GaddiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique)
    }
}

/** A single "Khel jaari rakho" (resume in-progress match) strip, styled like a pending file. */
@Composable
private fun ResumeStrip(
    label: String,
    onResume: () -> Unit,
) {
    val s = LocalGaddiStrings.current
    val resumeInProgressDesc = stringResource(Res.string.a11y_resume_in_progress, label)
    HairlineRow(
        onClick = onResume,
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = resumeInProgressDesc },
        verticalPadding = 11.dp,
    ) {
        Text("⟳", style = GaddiType.title.copy(fontSize = 18.sp), color = BrandTokens.GoldAntique)
        Column(modifier = Modifier.weight(1f)) {
            Text(s.homeResumeLabel, style = GaddiType.title.copy(fontSize = 14.sp), color = GaddiNeutrals.TextPrimary)
            Text(
                label,
                style = GaddiType.caption.copy(fontSize = 10.sp),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = GaddiType.title.copy(fontSize = 16.sp), color = BrandTokens.GoldAntique)
    }
}

// ─────────────────────────── Teleprinter ticker ───────────────────────────────

/**
 * TeleprinterTicker — continuous right-to-left marquee of ROZNAMCHA headlines.
 *
 * Bug fix: the old code animated an `offsetX` float but never applied it to
 * any modifier, so the text sat static. This implementation:
 *  1. Measures the natural pixel width of one copy of the joined text via
 *     `onGloballyPositioned` on an invisible reference render.
 *  2. Drives `translationX` in [0, -contentWidth] via `infiniteTransition`.
 *  3. Renders TWO copies of the content side-by-side so the seam is invisible
 *     when the loop restarts at 0.
 *  4. Clips the outer Box so nothing bleeds outside the ticker strip.
 *  5. Respects reducedMotion — shows static (non-scrolling) text if true.
 */
@Composable
private fun TeleprinterTicker(
    lines: List<String>,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    // One full sweep of content. Appending a copy below gives seamless looping.
    val text = remember { lines.joinToString("     ◆     ") + "     ◆     " }

    // Natural pixel width of one content copy, measured on first layout.
    var contentWidthPx by remember { mutableStateOf(0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "ticker")

    // Only animate once we know the content width (avoids an ugly instant-jump
    // on first frame). When reducedMotion is on, targetValue stays at 0f so
    // the transition never moves and the text is static.
    val scrollDurationMs = 45_000 // ~45 s for a full loop — readable, not rushed
    val translateX by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = if (reducedMotion || contentWidthPx == 0f) 0f else -contentWidthPx,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = if (reducedMotion || contentWidthPx == 0f) 1 else scrollDurationMs,
                        easing = LinearEasing,
                    ),
                repeatMode = RepeatMode.Restart,
            ),
        label = "tickerTranslateX",
    )

    Box(
        modifier =
            modifier
                .background(BrandTokens.PaperCream)
                .border(1.dp, BrandTokens.BrassAged, RoundedCornerShape(0.dp))
                .clip(RoundedCornerShape(0.dp)),
        // clip scrolling content to ticker strip
        contentAlignment = Alignment.CenterStart,
    ) {
        // Two copies of the content in a Row, translated left.
        // When translateX reaches -contentWidthPx the second copy fills
        // exactly the space the first vacated, so the loop is seamless.
        Row(
            modifier =
                Modifier
                    .graphicsLayer { translationX = translateX }
                    .wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Reference measure — invisible Text sized to natural content width.
            // Its onGloballyPositioned captures the pixel width we need.
            Text(
                text = text,
                style =
                    GaddiType.caption.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                color = Color.Transparent, // invisible; measurement only
                maxLines = 1,
                softWrap = false,
                modifier =
                    Modifier
                        .onGloballyPositioned { coords ->
                            if (coords.size.width > 0) {
                                contentWidthPx = coords.size.width.toFloat()
                            }
                        }.then(Modifier),
                // kept in layout pass only
            )
        }

        // The actual scrolling content — two copies so the wrap is seamless.
        Row(
            modifier =
                Modifier
                    .graphicsLayer { translationX = translateX }
                    .wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Copy 1
            TickerTextBlock(text = text)
            // Copy 2 — identical, placed immediately after copy 1 so that
            // when the first copy has fully exited left, the second fills in
            // and the translateX resets to 0 without a visible gap.
            TickerTextBlock(text = text)
        }

        // Right-edge fade vignette — gives the illusion of text scrolling off into a machine.
        Box(
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(40.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, BrandTokens.PaperCream.copy(alpha = 0.95f)),
                        ),
                    ),
        )
    }
}

@Composable
private fun TickerTextBlock(text: String) {
    Text(
        text = text,
        style =
            GaddiType.caption.copy(
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                fontSize = 11.sp,
            ),
        color = BrandTokens.CreamInk,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier.padding(horizontal = 8.dp),
    )
}

// ─────────────────────────── Gaddi Wordmark ───────────────────────────────────

@Composable
private fun GaddiWordmark(
    displaySize: Int,
    wordmarkAlpha: Float,
    wordmarkSlide: Float,
    taglineAlpha: Float,
) {
    val brassGradient =
        Brush.horizontalGradient(
            listOf(
                BrandTokens.GoldAntique,
                BrandTokens.BrassAged,
                BrandTokens.GoldAntique,
            ),
        )

    Column {
        // Chair glyph + wordmark on same line
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier.graphicsLayer {
                    alpha = wordmarkAlpha
                    translationY = wordmarkSlide
                },
        ) {
            // Chair glyph — mini brass roundel
            Box(
                modifier =
                    Modifier
                        .size((displaySize * 0.65f).dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(BrandTokens.BrassAged, BrandTokens.BrassDark),
                            ),
                        ).drawBehind {
                            // Simple chair silhouette via lines
                            val cx = size.width / 2
                            val cy = size.height / 2
                            val r = size.width * 0.35f
                            val col = BrandTokens.TeakDark.copy(alpha = 0.9f)
                            // Seat back arc
                            drawCircle(col, r * 0.6f, Offset(cx, cy * 0.75f), style = Stroke(width = size.width * 0.1f))
                            // Seat legs
                            drawLine(col, Offset(cx - r * 0.4f, cy), Offset(cx - r * 0.4f, cy + r * 0.8f), strokeWidth = size.width * 0.09f)
                            drawLine(col, Offset(cx + r * 0.4f, cy), Offset(cx + r * 0.4f, cy + r * 0.8f), strokeWidth = size.width * 0.09f)
                            drawLine(
                                col,
                                Offset(cx - r * 0.55f, cy + r * 0.5f),
                                Offset(cx + r * 0.55f, cy + r * 0.5f),
                                strokeWidth =
                                    size.width * 0.08f,
                            )
                        },
                contentAlignment = Alignment.Center,
            ) {}

            // GADDI wordmark in Rozha One with brass gradient
            Text(
                text = stringResource(Res.string.brand_wordmark),
                style =
                    MaterialTheme.typography.displayLarge.copy(
                        fontSize = displaySize.sp,
                        brush = brassGradient,
                        letterSpacing = 2.sp,
                    ),
                maxLines = 1,
            )
        }

        // Brass rule
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                Color.Transparent,
                                BrandTokens.BrassAged,
                                BrandTokens.GoldAntique,
                                BrandTokens.BrassAged,
                                Color.Transparent,
                            ),
                        ),
                    ).graphicsLayer { alpha = wordmarkAlpha },
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = LocalGaddiStrings.current.homeTagline,
            style = GaddiType.body.copy(fontStyle = FontStyle.Italic, fontSize = (displaySize * 0.18f).sp),
            color = GaddiNeutrals.TextSecondary,
            modifier = Modifier.graphicsLayer { alpha = taglineAlpha },
        )
    }
}

// ─────────────────────────── Brass Seal ──────────────────────────────────────

@Composable
fun BrassSeal(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(BrandTokens.GoldAntique.copy(alpha = 0.3f), BrandTokens.BrassDark.copy(alpha = 0.5f)),
                    ),
                ).border(
                    2.dp,
                    Brush.sweepGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark, BrandTokens.GoldAntique)),
                    CircleShape,
                ).drawBehind {
                    // Engraved ring text approximation — 3 concentric rings
                    val cx = size.width / 2
                    val cy = size.height / 2
                    val r = size.width * 0.45f
                    val col = BrandTokens.BrassAged.copy(alpha = 0.6f)
                    drawCircle(col, r, Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                    drawCircle(col, r * 0.75f, Offset(cx, cy), style = Stroke(0.7.dp.toPx()))
                    // Radiating lines
                    val rays = 12
                    for (i in 0 until rays) {
                        val angle = (i * 2 * PI / rays).toFloat()
                        drawLine(
                            col.copy(alpha = 0.35f),
                            Offset(cx + r * 0.78f * cos(angle), cy + r * 0.78f * sin(angle)),
                            Offset(cx + r * 0.98f * cos(angle), cy + r * 0.98f * sin(angle)),
                            strokeWidth = 0.8.dp.toPx(),
                        )
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "GADDI",
                style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                color = BrandTokens.GoldAntique,
            )
            Text(
                text = "·",
                style = GaddiType.caption.copy(fontSize = 9.sp),
                color = BrandTokens.BrassAged,
            )
            Text(
                text = stringResource(Res.string.brass_seal_authority),
                style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                color = BrandTokens.BrassAged,
            )
            Text(
                text = "·",
                style = GaddiType.caption.copy(fontSize = 9.sp),
                color = BrandTokens.BrassAged,
            )
            Text(
                text = stringResource(Res.string.brass_seal_ruin),
                style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp),
                color = BrandTokens.BrassAged,
            )
        }
    }
}

// ─────────────────────────── Persona on Duty ──────────────────────────────────

@Composable
private fun PersonaOnDutyCard(
    persona: BotPersona,
    enlarged: Boolean,
    personaAlpha: Float = 1f,
    personaSlide: Float = 0f,
    bobOffsetPx: Float = 0f,
) {
    val seatColor = Color(persona.seatColorArgb)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            Modifier.graphicsLayer {
                alpha = personaAlpha
                translationY = personaSlide + bobOffsetPx
            },
    ) {
        // Enamel nameplate "card" — brass double-rim is the sanctioned card idiom (non-negotiable
        // #4); the real cast shadow is what was missing to make it read as raised, not flat-boxed.
        Box(
            modifier =
                Modifier
                    .then(if (enlarged) Modifier.fillMaxWidth().height(104.dp) else Modifier.fillMaxWidth().height(64.dp))
                    .shadow(8.dp, RoundedCornerShape(16.dp), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF1C1710), Color(0xFF14110D))))
                    .border(
                        1.5.dp,
                        Brush.horizontalGradient(listOf(BrandTokens.GoldAntique, seatColor, BrandTokens.BrassAged)),
                        RoundedCornerShape(16.dp),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Monogram roundel
                Box(
                    modifier =
                        Modifier
                            .size(if (enlarged) 36.dp else 28.dp)
                            .clip(CircleShape)
                            .background(Brush.radialGradient(listOf(seatColor, seatColor.copy(alpha = 0.5f))))
                            .border(1.dp, BrandTokens.BrassAged, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = persona.monogram,
                        style = GaddiType.caption.copy(fontSize = if (enlarged) 11.sp else 9.sp),
                        color = GaddiNeutrals.Cream,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = persona.name,
                    style = GaddiType.name.copy(fontSize = if (enlarged) 14.sp else 11.sp),
                    color = GaddiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = persona.title,
                    style = GaddiType.caption.copy(fontSize = if (enlarged) 10.sp else 9.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // On-duty caption
        Text(
            text = "\"${onDutyCaption(persona.id)}\"",
            style = GaddiType.body.copy(fontSize = 11.sp, fontStyle = FontStyle.Italic),
            color = GaddiNeutrals.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

// ─────────────────────────── Mode Grid (expanded home) ───────────────────────

private data class HomeModeData(
    val key: String,
    val icon: String,
    val accentArgb: Long,
    val label: String,
    val sublabel: String,
    val description: String = "",
    val details: List<Pair<String, String>> = emptyList(),
    val isHero: Boolean = false,
    val isDisabled: Boolean = false,
    val disabledStamp: String? = null,
    val onClick: () -> Unit,
)

// AAA polish: the 3×3 grid of colour-header bordered tiles was the screen's biggest concentration
// of "bordered boxes" (non-negotiable #1) — eight little cards stacked in a grid. Rebuilt as a
// single-column list of hairline rows (matching Setup's mode/preset lists): a brass-rimmed accent
// token carries the mode's colour identity, gold ink + a right chevron carry "selected" instead of
// a full-tile border.
@Composable
private fun ModeGrid(
    modes: List<HomeModeData>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    ctaAlpha: Float = 1f,
    ctaSlide: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier.graphicsLayer {
                alpha = ctaAlpha
                translationY = ctaSlide
            },
    ) {
        modes.forEach { mode ->
            ModeGridTile(
                mode = mode,
                isSelected = selectedKey == mode.key,
                onTap = { onSelect(if (selectedKey == mode.key) null else mode.key) },
            )
        }
    }
}

@Composable
private fun ModeGridTile(
    mode: HomeModeData,
    isSelected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accentColor = Color(mode.accentArgb)
    val contentAlpha = if (mode.isDisabled) 0.45f else 1f

    HairlineRow(
        onClick = onTap,
        modifier = modifier.alpha(contentAlpha),
        verticalPadding = 11.dp,
    ) {
        // Accent-tinted brass token carries the mode's colour identity — no header-strip box.
        Box(
            modifier =
                Modifier
                    .size(38.dp)
                    .shadow(4.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.9f), accentColor.copy(alpha = 0.55f))))
                    .border(1.5.dp, if (isSelected) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = mode.icon, style = GaddiType.title.copy(fontSize = 16.sp), color = Color.White.copy(alpha = 0.92f))
        }
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = mode.label,
                    style = GaddiType.name.copy(fontSize = 14.sp),
                    color = if (isSelected) BrandTokens.GoldAntique else GaddiNeutrals.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val badge =
                    when {
                        mode.isHero -> "APPROVED"
                        mode.disabledStamp != null -> mode.disabledStamp
                        else -> null
                    }
                if (badge != null) {
                    Box(
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    if (mode.isHero) BrandTokens.StampRed.copy(alpha = 0.9f) else BrandTokens.StampRed.copy(alpha = 0.18f),
                                ).border(
                                    1.dp,
                                    BrandTokens.StampRed.copy(alpha = if (mode.isHero) 0f else 0.5f),
                                    RoundedCornerShape(3.dp),
                                ).padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            text = badge,
                            style = GaddiType.caption.copy(fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
                            color = if (mode.isHero) GaddiNeutrals.Cream else BrandTokens.StampRed.copy(alpha = 0.8f),
                        )
                    }
                }
            }
            Text(
                text = mode.sublabel,
                style = GaddiType.caption.copy(fontSize = 10.sp),
                color = GaddiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "›",
            style = GaddiType.title.copy(fontSize = 16.sp),
            color = if (isSelected) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun ModePreviewPanel(
    mode: HomeModeData,
    personaAlpha: Float,
    personaSlide: Float,
) {
    val accentColor = Color(mode.accentArgb)
    // AAA polish: a raised lit surface (real cast shadow) instead of a thin-bordered flat panel
    // (non-negotiable #1) — the accent-colour header rectangle dissolves into a brass-rimmed
    // accent medallion + a sparing Rozha title, the one focal point of the panel.
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .graphicsLayer {
                    alpha = personaAlpha
                    translationY = personaSlide
                }.shadow(14.dp, RoundedCornerShape(20.dp), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark)))
                .padding(horizontal = 28.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .shadow(10.dp, CircleShape, clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(accentColor.copy(alpha = 0.95f), accentColor.copy(alpha = 0.6f))))
                    .border(2.dp, BrandTokens.GoldAntique.copy(alpha = if (mode.isDisabled) 0.3f else 0.8f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = mode.icon, style = GaddiType.title.copy(fontSize = 30.sp), color = Color.White.copy(alpha = 0.9f))
        }
        Spacer(Modifier.height(18.dp))

        // Info + CTA
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Title row
                if (mode.isHero) {
                    Text(
                        "APPROVED",
                        style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.StampRed,
                    )
                    Spacer(Modifier.height(4.dp))
                }
                Text(
                    text = mode.label,
                    style = GaddiType.display.rozha().copy(fontSize = 22.sp),
                    color = GaddiNeutrals.TextPrimary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = mode.sublabel,
                    style = GaddiType.caption.copy(fontSize = 10.sp),
                    color = GaddiNeutrals.TextMuted,
                    textAlign = TextAlign.Center,
                )

                // Description
                if (mode.description.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BrandTokens.BrassDark.copy(alpha = 0.4f)))
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = mode.description,
                        style = GaddiType.body.copy(fontSize = 11.sp),
                        color = GaddiNeutrals.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                }

                // Detail rows — hairline fields, matching the rest of the app's list idiom.
                if (mode.details.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BrandTokens.BrassDark.copy(alpha = 0.4f)))
                    Spacer(Modifier.height(4.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        mode.details.forEach { (fieldLabel, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = fieldLabel.uppercase(),
                                    style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.2.sp),
                                    color = BrandTokens.BrassAged.copy(alpha = 0.7f),
                                )
                                Text(
                                    text = value,
                                    style = GaddiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                    color = GaddiNeutrals.TextPrimary,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ENTER / disabled stamp button
            if (!mode.isDisabled) {
                StampButton(
                    label = stringResource(Res.string.cta_enter),
                    style = StampStyle.Primary,
                    onClick = mode.onClick,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                StampButton(
                    label = mode.disabledStamp ?: stringResource(Res.string.label_unavailable),
                    style = StampStyle.Ghost,
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ─────────────────────────── CTA Stack ───────────────────────────────────────

@Composable
private fun CtaStack(
    onNewGame: () -> Unit,
    onGazette: () -> Unit,
    onSettings: () -> Unit,
    onOnlineTap: () -> Unit,
    onStory: () -> Unit = {},
    onTutorial: () -> Unit = {},
    onGauntlet: () -> Unit = {},
    onSpectate: () -> Unit = {},
    fullWidth: Boolean = false,
    ctaAlpha: Float = 1f,
    ctaSlide: Float = 0f,
) {
    val s = LocalGaddiStrings.current
    Column(
        modifier =
            (if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = 360.dp))
                .graphicsLayer {
                    alpha = ctaAlpha
                    translationY = ctaSlide
                },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ① HERO — red APPROVED stamp CTA
        StampChit(
            label = s.homeCtaNewGame,
            sublabel = s.homeCtaNewGameSub,
            isHero = true,
            onClick = onNewGame,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ② KISSA — Story / narrative Darbar mode
        StampChit(
            label = s.homeCtaStory,
            sublabel = s.homeCtaStorySub,
            isHero = false,
            onClick = onStory,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ④ Tarakki ki Seedhi — the escalating gauntlet ladder
        StampChit(
            label = s.homeCtaGauntlet,
            sublabel = s.homeCtaGauntletSub,
            isHero = false,
            onClick = onGauntlet,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑤ Tamasha — watch-only demo game
        StampChit(
            label = s.homeCtaSpectate,
            sublabel = s.homeCtaSpectateSub,
            isHero = false,
            onClick = onSpectate,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑥ Pehli Hazri — interactive tutorial
        StampChit(
            label = s.homeCtaTutorial,
            sublabel = s.homeCtaTutorialSub,
            isHero = false,
            onClick = onTutorial,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑦ Rules
        StampChit(
            label = s.homeCtaRules,
            sublabel = s.homeCtaRulesSub,
            isHero = false,
            onClick = onGazette,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑧ Settings
        StampChit(
            label = s.homeCtaSettings,
            sublabel = s.homeCtaSettingsSub,
            isHero = false,
            onClick = onSettings,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )

        // ⑨ Multiplayer — disabled PENDING SANCTION
        StampChit(
            label = s.homeCtaMultiplayer,
            sublabel = s.homeCtaMultiplayerSub,
            isHero = false,
            isDisabled = true,
            disabledStamp = s.homeCtaMultiplayerBadge,
            onClick = onOnlineTap,
            modifier = if (fullWidth) Modifier.fillMaxWidth() else Modifier,
        )
    }
}

// ─────────────────────────── StampChit (CTA primitive) ───────────────────────

@Composable
fun StampChit(
    label: String,
    sublabel: String? = null,
    isHero: Boolean = false,
    isDisabled: Boolean = false,
    disabledStamp: String? = null,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val bgColor =
        when {
            isDisabled -> BrandTokens.TeakDark
            isHero -> BrandTokens.BrassAged
            else -> Color(0xFF1E1610)
        }
    val borderColor =
        when {
            isDisabled -> BrandTokens.BrassDark.copy(alpha = 0.4f)
            isHero -> BrandTokens.GoldAntique
            else -> BrandTokens.BrassAged.copy(alpha = 0.7f)
        }
    val contentAlpha = if (isDisabled) 0.45f else 1f

    // Press scale feedback — scale dips to 0.96 on press, restores on release.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && !isDisabled) 0.96f else 1f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "pressScale",
    )

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = pressScale
                    scaleY = pressScale
                }
                // A raised stamp needs a real cast shadow, not just a filled fill + hairline —
                // non-negotiable #4 ("buttons = raised stamps"). Disabled chits stay flat.
                .then(
                    if (isDisabled) {
                        Modifier
                    } else {
                        Modifier.shadow(
                            if (isHero) 8.dp else 4.dp,
                            RoundedCornerShape(8.dp),
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = BrandTokens.TeakInk,
                        )
                    },
                ).clip(RoundedCornerShape(8.dp))
                .background(bgColor)
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                // A11y: each CTA is a button; merge the label/sublabel/stamp into one spoken node and
                // mark disabled CTAs (e.g. multiplayer "pending sanction") so they're announced as such.
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    if (isDisabled) disabled()
                    contentDescription = if (sublabel != null) "$label. $sublabel" else label
                }.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = !isDisabled,
                    onClick = onClick,
                ).padding(horizontal = 20.dp, vertical = if (isHero) 18.dp else 12.dp)
                .alpha(contentAlpha),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style =
                        if (isHero) {
                            MaterialTheme.typography.displaySmall.copy(fontSize = 18.sp)
                        } else {
                            GaddiType.title.copy(fontSize = 15.sp)
                        },
                    color =
                        when {
                            isDisabled -> GaddiNeutrals.TextDisabled
                            isHero -> BrandTokens.TeakDark
                            else -> GaddiNeutrals.TextPrimary
                        },
                    fontWeight = if (isHero) FontWeight.Bold else FontWeight.Normal,
                )
                if (sublabel != null) {
                    Text(
                        text = sublabel,
                        style = GaddiType.caption.copy(fontSize = 11.sp),
                        color = if (isHero) BrandTokens.BrassDark else GaddiNeutrals.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Stamp impression
            if (isHero && !isDisabled) {
                Box(
                    modifier =
                        Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrandTokens.StampRed.copy(alpha = 0.9f))
                            .border(1.dp, BrandTokens.Oxblood, RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "APPROVED",
                        style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                        color = GaddiNeutrals.Cream,
                    )
                }
            }
            if (isDisabled && disabledStamp != null) {
                Box(
                    modifier =
                        Modifier
                            .padding(start = 8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(BrandTokens.StampRed.copy(alpha = 0.15f))
                            .border(1.dp, BrandTokens.StampRed.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = disabledStamp,
                        style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.8.sp),
                        color = BrandTokens.StampRed.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Home Footer ──────────────────────────────────────

@Composable
private fun HomeFooter() {
    // A hairline rule lifts the footer off the body — not a filled/bordered bar (non-negotiable #1).
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Transparent, BrandTokens.BrassDark.copy(alpha = 0.4f), Color.Transparent),
                        ),
                    ),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val sf = LocalGaddiStrings.current
            Text(
                text = sf.homeFooterLeft,
                style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
            )
            Text(
                text = sf.homeFooterRight,
                style = GaddiType.caption.copy(fontSize = 9.sp),
                color = GaddiNeutrals.TextDisabled,
            )
        }
    }
}

// ─────────────────────────── Background depth ─────────────────────────────────

/**
 * Very-low-contrast certificate furniture behind the Home content:
 *  - a thin double inner-border (the engraved frame of a government certificate),
 *  - an oversized guilloche seal watermark anchored in the wide right field,
 *    so the bare teak expanse reads as embossed stationery rather than empty space.
 *
 * All strokes sit at ≤ 6% alpha brass: present on a calm read, never competing with
 * the wordmark, CTAs, or the live brass seal.
 */
private fun DrawScope.drawHomeDepth() {
    val brass = BrandTokens.BrassAged
    val gold = BrandTokens.GoldAntique

    // ── Thin certificate inner-border (double rule, inset from the edges) ──────
    val inset = 18f
    val gap = 6f
    drawRect(
        color = brass.copy(alpha = 0.10f),
        topLeft = Offset(inset, inset),
        size =
            androidx.compose.ui.geometry
                .Size(size.width - inset * 2, size.height - inset * 2),
        style = Stroke(width = 1f),
    )
    drawRect(
        color = brass.copy(alpha = 0.05f),
        topLeft = Offset(inset + gap, inset + gap),
        size =
            androidx.compose.ui.geometry
                .Size(size.width - (inset + gap) * 2, size.height - (inset + gap) * 2),
        style = Stroke(width = 1f),
    )

    // ── Oversized guilloche seal watermark — a faint emboss centred across the
    // whole sheet so the teak field reads as engraved stationery edge to edge,
    // not a spotlight under one corner. ──────────────────────────────────────
    val cx = size.width * 0.50f
    val cy = size.height * 0.52f
    val baseR = minOf(size.width, size.height) * 0.58f

    // Concentric engraved rings.
    val ringAlphas = listOf(0.06f, 0.045f, 0.035f, 0.025f)
    ringAlphas.forEachIndexed { i, a ->
        drawCircle(
            color = brass.copy(alpha = a),
            radius = baseR * (1f - i * 0.16f),
            center = Offset(cx, cy),
            style = Stroke(width = 1f),
        )
    }

    // Guilloche rosette — interlocking petal arcs traced as a ring of small circles,
    // the classic spirograph lacework of banknote/seal engraving.
    val petals = 36
    val petalR = baseR * 0.62f
    val orbit = baseR * 0.40f
    for (i in 0 until petals) {
        val angle = (i * 2.0 * PI / petals).toFloat()
        val px = cx + orbit * cos(angle)
        val py = cy + orbit * sin(angle)
        drawCircle(
            color = gold.copy(alpha = 0.04f),
            radius = petalR,
            center = Offset(px, py),
            style = Stroke(width = 0.8f),
        )
    }

    // Faint radiating ticks at the rim — the "official seal" sunburst.
    val rays = 48
    for (i in 0 until rays) {
        val angle = (i * 2.0 * PI / rays).toFloat()
        drawLine(
            color = brass.copy(alpha = 0.05f),
            start = Offset(cx + baseR * SealRayInnerFraction * cos(angle), cy + baseR * SealRayInnerFraction * sin(angle)),
            end = Offset(cx + baseR * 1.0f * cos(angle), cy + baseR * 1.0f * sin(angle)),
            strokeWidth = 0.8f,
        )
    }
}
