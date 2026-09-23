package com.kursi.shared.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.persona.BotDifficulty
import com.kursi.ai.persona.MatchPreset
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.feature.game.DraftPresets
import com.kursi.shared.strings.LocalGaddiStrings
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_advanced_options
import kursi.core.designsystem.generated.resources.a11y_decrease_players
import kursi.core.designsystem.generated.resources.a11y_increase_players
import kursi.core.designsystem.generated.resources.a11y_toggle_state
import kursi.core.designsystem.generated.resources.cta_quick_start_stamp
import kursi.core.designsystem.generated.resources.label_active_badge
import kursi.core.designsystem.generated.resources.label_coins_suffix
import kursi.core.designsystem.generated.resources.label_players_plural
import kursi.core.designsystem.generated.resources.label_selected_check
import kursi.core.designsystem.generated.resources.label_vs
import kursi.core.designsystem.generated.resources.setup_advanced_options_label
import kursi.core.designsystem.generated.resources.setup_advanced_options_sub
import kursi.core.designsystem.generated.resources.setup_anarchy_toggle_off
import kursi.core.designsystem.generated.resources.setup_anarchy_toggle_on
import kursi.core.designsystem.generated.resources.setup_darbar_toggle_off
import kursi.core.designsystem.generated.resources.setup_darbar_toggle_on
import kursi.core.designsystem.generated.resources.setup_draft_classic
import kursi.core.designsystem.generated.resources.setup_vishesh_bail
import kursi.core.designsystem.generated.resources.setup_vishesh_emergency
import kursi.core.designsystem.generated.resources.setup_vishesh_hawala
import kursi.core.designsystem.generated.resources.setup_vishesh_inflation
import kursi.core.designsystem.generated.resources.setup_vishesh_khazana
import kursi.core.designsystem.generated.resources.setup_vishesh_sabotage
import kursi.core.designsystem.generated.resources.setup_vishesh_scarcity
import kursi.core.designsystem.generated.resources.setup_vishesh_section_label
import kursi.core.designsystem.generated.resources.setup_vishesh_section_sub
import org.jetbrains.compose.resources.stringResource
import kotlin.random.Random

// ── Difficulty descriptors ────────────────────────────────────────────────────

private data class DifficultyMeta(
    val tier: Difficulty,
    val nameplate: String,
    val voiceLine: String,
)

// ── Mode descriptors ──────────────────────────────────────────────────────────

private data class ModeMeta(
    val id: String,
    val label: String,
    val sublabel: String,
    val available: Boolean,
)

/** The two live modes a player can actually start. */
private enum class PlayMode { VS_AI, PASS_AND_PLAY }

/**
 * SetupScreen — Requisition Form (S2) from 17_app_plan.md §4.
 *
 * A cream certificate that fills as you choose:
 * - FORM 1-A: mode (vs AI selected; others MUHAR PENDING disabled)
 * - FORM 1-B: player count 2–10 (brass abacus slider)
 * - FORM 1-C: difficulty (4 enamel tabs with in-voice descriptions)
 *
 * onNext(seed) → Lobby(seed)
 */
@Composable
fun SetupScreen(
    onBack: () -> Unit,
    onNext: (
        seed: Long,
        players: Int,
        difficulty: Difficulty,
        humanCount: Int,
        teamCount: Int,
        narrative: Boolean,
        anarchy: Boolean,
        draftCode: String,
        bailEnabled: Boolean,
        sabotageEnabled: Boolean,
        hawalaEnabled: Boolean,
        emergencyEnabled: Boolean,
        khazanaEnabled: Boolean,
        khazanaTarget: Int,
        inflationEnabled: Boolean,
        scarcityEnabled: Boolean,
    ) -> Unit,
    initialPlayers: Int = 4,
    initialDifficulty: Difficulty = Difficulty.Medium,
    /**
     * M5 ONBOARD — start a match immediately from a fully-resolved config, bypassing the form
     * (used by QUICK-MATCH and the named persona-lineup presets). Defaults to the same handler as
     * [onNext] so a fresh seed is generated; the app layer overrides it to route through the Lobby.
     */
    onStartPreset: (seed: Long, players: Int, difficulty: Difficulty) -> Unit =
        {
            seed,
            players,
            difficulty,
            ->
            onNext(seed, players, difficulty, 1, 0, false, false, "", false, false, false, false, false, 25, false, false)
        },
    /**
     * M7 ONLINE — the three online modes (PRIVATE KAMRA / KHULI BOLI / EK HI LAN) now route into the
     * Online Mehfil hub instead of sitting "JALD AANE WAALA". Defaults to a no-op so older call sites
     * (and the static screenshot harness) compile unchanged.
     */
    onOnline: () -> Unit = {},
    /**
     * Seeds the Team Khel (1-D) toggle ON at first composition. Defaults OFF for the live app (the
     * player opts in); the static screenshot harness sets it true so the captured `setup_teams` shot
     * actually demonstrates the team-assignment row in its enabled state. Only takes effect while the
     * row is eligible (vs-AI, >= 3 seats); ignored otherwise, exactly like a user tap would be.
     */
    initialTeamPlay: Boolean = false,
    /**
     * Vertical scroll state for the form body. Injectable so the screenshot harness can render a first
     * pass, scroll the team row into view, and render a second pass (a static [ImageComposeScene] does
     * not pump a scroll animation). Live call sites get a fresh [rememberScrollState].
     */
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    modifier: Modifier = Modifier,
) {
    val s = LocalGaddiStrings.current
    var playerCount by remember { mutableIntStateOf(initialPlayers) }
    var difficulty by remember { mutableStateOf(initialDifficulty) }
    var playMode by remember { mutableStateOf(PlayMode.VS_AI) }
    // Hot-seat human count (pass-and-play only). Always < playerCount so at least one bot can fill in;
    // clamped whenever playerCount shrinks below the chosen human count.
    var humanCount by remember { mutableIntStateOf(2) }
    if (humanCount > playerCount) humanCount = playerCount
    // M6e TEAM KHEL: when on, the table splits into two teams (last-team-standing). Surfaced only in
    // vs-AI mode (teams + hot-seat humans is deferred) and needs >= 3 seats for a meaningful 2v(N-2).
    var teamPlay by remember { mutableStateOf(initialTeamPlay) }
    val teamEligible = playMode == PlayMode.VS_AI && playerCount >= 3
    if (!teamEligible) teamPlay = false

    // Narrative (Darbar / Kissa) toggle — vs-AI only
    var narrativeEnabled by remember { mutableStateOf(false) }
    val narrativeEligible = playMode == PlayMode.VS_AI
    if (!narrativeEligible) narrativeEnabled = false

    // Anarchy (Andher Nagari) toggle — vs-AI only
    var anarchyEnabled by remember { mutableStateOf(false) }
    val anarchyEligible = playMode == PlayMode.VS_AI
    if (!anarchyEligible) anarchyEnabled = false

    // Draft preset picker — vs-AI only; "" = classic (no draft)
    var selectedDraftCode by remember { mutableStateOf("") }
    val draftEligible = playMode == PlayMode.VS_AI

    // Vishesh (Special) variant flags — all vs-AI only; all off by default
    val visheshEligible = playMode == PlayMode.VS_AI
    var bailEnabled by remember { mutableStateOf(false) }
    var sabotageEnabled by remember { mutableStateOf(false) }
    var hawalaEnabled by remember { mutableStateOf(false) }
    var emergencyEnabled by remember { mutableStateOf(false) }
    var khazanaEnabled by remember { mutableStateOf(false) }
    var khazanaTarget by remember { mutableIntStateOf(25) }
    var inflationEnabled by remember { mutableStateOf(false) }
    var scarcityEnabled by remember { mutableStateOf(false) }
    if (!visheshEligible) {
        bailEnabled = false
        sabotageEnabled = false
        hawalaEnabled = false
        emergencyEnabled = false
        khazanaEnabled = false
        inflationEnabled = false
        scarcityEnabled = false
    }
    val anyVishesh =
        visheshEligible &&
            (
                bailEnabled ||
                    sabotageEnabled ||
                    hawalaEnabled ||
                    emergencyEnabled ||
                    khazanaEnabled ||
                    inflationEnabled ||
                    scarcityEnabled
            )

    // Advanced options (team/narrative/anarchy/draft) are behind an expandable
    // section on mobile to keep the critical path short for casual players.
    var advancedExpanded by remember { mutableStateOf(false) }
    val hasAnyAdvanced = teamPlay || narrativeEnabled || anarchyEnabled || selectedDraftCode.isNotEmpty() || anyVishesh
    // Auto-expand if any advanced option is already on (e.g. from a preset)
    LaunchedEffect(hasAnyAdvanced) { if (hasAnyAdvanced) advancedExpanded = true }

    val difficultyMeta =
        listOf(
            DifficultyMeta(Difficulty.Easy, s.diffEasyName, s.diffEasyVoice),
            DifficultyMeta(Difficulty.Medium, s.diffMediumName, s.diffMediumVoice),
            DifficultyMeta(Difficulty.Hard, s.diffHardName, s.diffHardVoice),
            DifficultyMeta(Difficulty.Expert, s.diffExpertName, s.diffExpertVoice),
            DifficultyMeta(Difficulty.Grandmaster, s.diffGrandmasterName, s.diffGrandmasterVoice),
        )
    // M7 — all five modes are now LIVE: vs-AI + pass-and-play run locally; the three online modes
    // (PRIVATE KAMRA / KHULI BOLI / EK HI LAN) route into the Online Mehfil hub (onOnline).
    val modes =
        listOf(
            ModeMeta("vs_ai", s.modeAILabel, s.modeAISub, true),
            ModeMeta("local", s.modeLocalLabel, s.modeLocalSub, true),
            ModeMeta("private", s.modePrivateLabel, s.modePrivateSub, true),
            ModeMeta("open", s.modeOpenLabel, s.modeOpenSub, true),
            ModeMeta("lan", s.modeLanLabel, s.modeLanSub, true),
        )

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .litGround(),
    ) {
        // Header bar
        SetupHeader(onBack = onBack)

        // Scrollable form body — centred certificate column so the form reads as a
        // requisition slip rather than a full-bleed banner on wide desktop windows.
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    // Asymmetric bottom padding so the last FORM 1-C card (NAYA BHARTI →
                    // HEAD CLERK SAAB) clears the sticky AAGE BADHO footer instead of being
                    // clipped behind it.
                    .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Form title
                FormSectionTitle(text = s.setupFormTitle)

                // ── M5 ONBOARD: Quick-match + curated presets ─────────────────────
                // A one-tap "start from my saved defaults" hero, plus three named persona-lineup
                // requisitions that pre-fill the whole form. All route through onStartPreset so the
                // deal stays deterministic and flows through the same Lobby → Game path.
                QuickMatchChit(
                    label = s.setupQuickMatchLabel,
                    sublabel = s.setupQuickMatchSub,
                    onClick = { onStartPreset(Random.nextLong(), initialPlayers, initialDifficulty) },
                )

                FormSection(
                    label = s.setupPresetSectionLabel,
                    sublabel = s.setupPresetSectionSub,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        val presetMeta =
                            listOf(
                                Triple(MatchPreset.CLASSIC_CABINET, s.presetCabinetName, s.presetCabinetSub),
                                Triple(MatchPreset.SNAKE_PIT, s.presetSnakePitName, s.presetSnakePitSub),
                                Triple(MatchPreset.CHAOS_TEN, s.presetChaosName, s.presetChaosSub),
                            )
                        presetMeta.forEach { (preset, name, sub) ->
                            PresetChit(
                                name = name,
                                sublabel = sub,
                                stamp = s.setupPresetRequisition,
                                lineupMonograms = preset.lineup().map { it.monogram },
                                onClick = {
                                    onStartPreset(preset.seed, preset.playerCount, preset.difficulty.toUiDifficulty())
                                },
                            )
                        }
                    }
                }

                // ── 1-A: Mode ─────────────────────────────────────────────────────
                FormSection(
                    label = s.setupModeLabel,
                    sublabel = s.setupModeSublabel,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        modes.forEach { mode ->
                            val selected =
                                when (mode.id) {
                                    "vs_ai" -> playMode == PlayMode.VS_AI
                                    "local" -> playMode == PlayMode.PASS_AND_PLAY
                                    else -> false
                                }
                            val isOnline = mode.id == "private" || mode.id == "open" || mode.id == "lan"
                            ModeChit(
                                mode = mode,
                                selected = selected,
                                comingSoonBadge = s.modeComingSoonBadge,
                                onlineBadge = if (isOnline) s.modeOnlineBadge else null,
                                onSelect = {
                                    when {
                                        isOnline -> onOnline()
                                        mode.id == "local" -> playMode = PlayMode.PASS_AND_PLAY
                                        else -> playMode = PlayMode.VS_AI
                                    }
                                },
                            )
                        }
                        // Hot-seat human-count picker — only meaningful for pass-and-play.
                        if (playMode == PlayMode.PASS_AND_PLAY) {
                            HumanCountPicker(
                                humanCount = humanCount,
                                playerCount = playerCount,
                                label = s.setupHumanCountLabel,
                                sublabel = s.setupHumanCountSublabel(humanCount, playerCount),
                                onChange = { humanCount = it.coerceIn(2, playerCount) },
                            )
                        }
                    }
                }

                // ── 1-B: Player count — large +/- stepper (replaces the barely-visible slider)
                FormSection(
                    label = s.setupPlayerSectionLabel,
                    sublabel = s.setupPlayerSublabel(playerCount),
                ) {
                    PlayerCountStepper(
                        count = playerCount,
                        min = 2,
                        max = 10,
                        onChange = { playerCount = it },
                    )
                }

                // ── 1-C: Difficulty — horizontal pill row (replaces 5 full-height cards)
                FormSection(
                    label = s.setupDifficultyLabel,
                    sublabel = s.setupDifficultySublabel,
                ) {
                    DifficultyPillRow(
                        meta = difficultyMeta,
                        selected = difficulty,
                        onSelect = { difficulty = it },
                    )
                    // Voice line for selected difficulty
                    val selectedMeta = difficultyMeta.first { it.tier == difficulty }
                    Text(
                        text = "\"${selectedMeta.voiceLine}\"",
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = GaddiNeutrals.TextMuted,
                        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                    )
                }

                // ── ADVANCED OPTIONS — collapsed by default; tap to expand ──────────
                AdvancedOptionsSection(
                    expanded = advancedExpanded,
                    onToggle = { advancedExpanded = !advancedExpanded },
                    hasActiveOption = hasAnyAdvanced,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 1-D: Team Khel
                        if (teamEligible) {
                            FormSection(label = s.setupTeamLabel, sublabel = s.setupTeamSublabel) {
                                TeamToggle(
                                    on = teamPlay,
                                    onLabel = s.setupTeamToggleOn,
                                    offLabel = s.setupTeamToggleOff,
                                    teamAName = s.teamNameA,
                                    teamBName = s.teamNameB,
                                    onToggle = { teamPlay = it },
                                )
                            }
                        }
                        // 1-E: Darbar (narrative)
                        if (narrativeEligible) {
                            FormSection(label = s.setupDarbarLabel, sublabel = s.setupDarbarSub) {
                                TeamToggle(
                                    on = narrativeEnabled,
                                    onLabel = stringResource(Res.string.setup_darbar_toggle_on),
                                    offLabel = stringResource(Res.string.setup_darbar_toggle_off),
                                    teamAName = "",
                                    teamBName = "",
                                    onToggle = { narrativeEnabled = it },
                                )
                            }
                        }
                        // 1-F: Anarchy
                        if (anarchyEligible) {
                            FormSection(label = s.setupAnarchyLabel, sublabel = s.setupAnarchySub) {
                                TeamToggle(
                                    on = anarchyEnabled,
                                    onLabel = stringResource(Res.string.setup_anarchy_toggle_on),
                                    offLabel = stringResource(Res.string.setup_anarchy_toggle_off),
                                    teamAName = "",
                                    teamBName = "",
                                    onToggle = { anarchyEnabled = it },
                                )
                            }
                        }
                        // 1-G: Vishesh (Special) variant modes
                        if (visheshEligible) {
                            FormSection(
                                label = stringResource(Res.string.setup_vishesh_section_label),
                                sublabel = stringResource(Res.string.setup_vishesh_section_sub),
                            ) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val (bailLabel, bailSub) = stringResource(Res.string.setup_vishesh_bail).split(" / ", limit = 2)
                                    VisheshToggle(bailLabel, bailSub, bailEnabled) { bailEnabled = it }
                                    val (sabotageLabel, sabotageSub) =
                                        stringResource(
                                            Res.string.setup_vishesh_sabotage,
                                        ).split(" / ", limit = 2)
                                    VisheshToggle(sabotageLabel, sabotageSub, sabotageEnabled) { sabotageEnabled = it }
                                    val (hawalaLabel, hawalaSub) = stringResource(Res.string.setup_vishesh_hawala).split(" / ", limit = 2)
                                    VisheshToggle(hawalaLabel, hawalaSub, hawalaEnabled) { hawalaEnabled = it }
                                    val (emergencyLabel, emergencySub) =
                                        stringResource(Res.string.setup_vishesh_emergency).split(" / ", limit = 2)
                                    VisheshToggle(
                                        emergencyLabel,
                                        emergencySub,
                                        emergencyEnabled,
                                    ) {
                                        emergencyEnabled =
                                            it
                                    }
                                    val (khazanaLabel, khazanaSub) =
                                        stringResource(Res.string.setup_vishesh_khazana, khazanaTarget).split(" / ", limit = 2)
                                    VisheshToggle(khazanaLabel, khazanaSub, khazanaEnabled) {
                                        khazanaEnabled =
                                            it
                                    }
                                    if (khazanaEnabled) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            listOf(25, 50, 100).forEach { target ->
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .clip(RoundedCornerShape(4.dp))
                                                            .background(
                                                                if (khazanaTarget ==
                                                                    target
                                                                ) {
                                                                    BrandTokens.GoldAntique.copy(alpha = 0.2f)
                                                                } else {
                                                                    BrandTokens.TeakDark
                                                                },
                                                            ).border(
                                                                1.dp,
                                                                if (khazanaTarget ==
                                                                    target
                                                                ) {
                                                                    BrandTokens.GoldAntique
                                                                } else {
                                                                    BrandTokens.BrassDark.copy(alpha = 0.4f)
                                                                },
                                                                RoundedCornerShape(4.dp),
                                                            ).clickable { khazanaTarget = target }
                                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                                ) {
                                                    Text(
                                                        "$target",
                                                        style = GaddiType.label.copy(fontSize = 11.sp),
                                                        color =
                                                            if (khazanaTarget ==
                                                                target
                                                            ) {
                                                                BrandTokens.GoldAntique
                                                            } else {
                                                                GaddiNeutrals.TextMuted
                                                            },
                                                    )
                                                }
                                            }
                                            Text(
                                                stringResource(Res.string.label_coins_suffix),
                                                style = GaddiType.caption.copy(fontSize = 10.sp),
                                                color = GaddiNeutrals.TextMuted,
                                            )
                                        }
                                    }
                                    val (inflationLabel, inflationSub) =
                                        stringResource(Res.string.setup_vishesh_inflation).split(" / ", limit = 2)
                                    VisheshToggle(
                                        inflationLabel,
                                        inflationSub,
                                        inflationEnabled,
                                    ) { inflationEnabled = it }
                                    val (scarcityLabel, scarcitySub) =
                                        stringResource(Res.string.setup_vishesh_scarcity).split(" / ", limit = 2)
                                    VisheshToggle(
                                        scarcityLabel,
                                        scarcitySub,
                                        scarcityEnabled,
                                    ) { scarcityEnabled = it }
                                }
                            }
                        }

                        // 1-H: Draft
                        if (draftEligible) {
                            FormSection(label = s.setupDraftLabel, sublabel = s.setupDraftSub) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    val (classicTitle, classicSub) = stringResource(Res.string.setup_draft_classic).split(" / ", limit = 2)
                                    DraftOptionChit(
                                        title = classicTitle,
                                        subtitle = classicSub,
                                        selected = selectedDraftCode.isEmpty(),
                                        onSelect = { selectedDraftCode = "" },
                                    )
                                    DraftPresets.ALL.forEach { preset ->
                                        DraftOptionChit(
                                            title = preset.title,
                                            subtitle = preset.subtitle,
                                            selected = selectedDraftCode == preset.code,
                                            onSelect = { selectedDraftCode = preset.code },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Footer CTA — a hairline rule lifts the stamp off the form above it, not a filled/bordered bar.
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, BrandTokens.BrassDark.copy(alpha = 0.5f), Color.Transparent),
                            ),
                        ),
            )
            Spacer(Modifier.height(10.dp))
            StampChit(
                label = s.setupCta,
                sublabel = s.setupCtaSub,
                isHero = true,
                onClick = {
                    val seed = Random.nextLong()
                    val humans = if (playMode == PlayMode.PASS_AND_PLAY) humanCount.coerceIn(2, playerCount) else 1
                    val teamCount = if (teamPlay && teamEligible) 2 else 0
                    onNext(
                        seed,
                        playerCount,
                        difficulty,
                        humans,
                        teamCount,
                        narrativeEnabled && narrativeEligible,
                        anarchyEnabled && anarchyEligible,
                        if (draftEligible) selectedDraftCode else "",
                        bailEnabled && visheshEligible,
                        sabotageEnabled && visheshEligible,
                        hawalaEnabled && visheshEligible,
                        emergencyEnabled && visheshEligible,
                        khazanaEnabled && visheshEligible,
                        if (khazanaEnabled && visheshEligible) khazanaTarget else 25,
                        inflationEnabled && visheshEligible,
                        scarcityEnabled && visheshEligible,
                    )
                },
                modifier = Modifier.widthIn(max = 760.dp).fillMaxWidth(),
            )
        }
    }
}

// ─────────────────────────── Sub-components ───────────────────────────────────

@Composable
private fun SetupHeader(onBack: () -> Unit) {
    val s = LocalGaddiStrings.current
    EngravedNavHeader(
        title = s.setupTitle,
        onBack = onBack,
        backLabel = s.back,
        modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        trailing = {
            Text(s.setupFormBadge, style = GaddiType.caption.copy(fontSize = 9.sp), color = GaddiNeutrals.TextMuted)
        },
    )
}

@Composable
private fun FormSectionTitle(text: String) {
    // The one focal point on the form — a sparing Rozha display line, no filled bar (non-negotiable #3).
    Text(
        text = text,
        style = GaddiType.display.rozha().copy(fontSize = 22.sp),
        color = GaddiNeutrals.TextPrimary,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
}

@Composable
private fun FormSection(
    label: String,
    sublabel: String,
    content: @Composable () -> Unit,
) {
    // AAA polish: sections rest on the shared lit ground — an engraved eyebrow + hairline
    // rule replaces the bordered paper-tint panel (non-negotiable #1).
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        EngravedHeader(eyebrow = label) {
            Text(
                text = sublabel,
                style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        content()
    }
}

@Composable
private fun ModeChit(
    mode: ModeMeta,
    selected: Boolean,
    comingSoonBadge: String,
    onSelect: () -> Unit,
    onlineBadge: String? = null,
) {
    val isAvailable = mode.available
    // AAA polish: a hairline row with a brass-ringed selection dot, not a bordered/filled tile
    // stacked with seven others (non-negotiable #1 + #4 "lists = rows on the ground").
    HairlineRow(
        onClick = if (isAvailable) onSelect else null,
        modifier = Modifier.alpha(if (isAvailable) 1f else 0.5f),
        verticalPadding = 10.dp,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(16.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (selected) {
                                BrandTokens.GoldAntique
                            } else if (isAvailable) {
                                BrandTokens.BrassDark.copy(alpha = 0.5f)
                            } else {
                                BrandTokens.TeakDark
                            },
                        ).border(1.5.dp, BrandTokens.BrassAged, RoundedCornerShape(50)),
            )
            Column {
                Text(
                    text = mode.label,
                    style = GaddiType.name.copy(fontSize = 13.sp),
                    color = if (isAvailable) GaddiNeutrals.TextPrimary else GaddiNeutrals.TextDisabled,
                )
                Text(
                    text = mode.sublabel,
                    style = GaddiType.caption.copy(fontSize = 9.sp),
                    color = GaddiNeutrals.TextMuted,
                )
            }
        }
        if (!isAvailable) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.StampRed.copy(alpha = 0.12f))
                        .border(0.7.dp, BrandTokens.StampRed.copy(alpha = 0.4f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Text(comingSoonBadge, style = GaddiType.caption.copy(fontSize = 9.sp), color = BrandTokens.StampRed.copy(alpha = 0.7f))
            }
        } else if (onlineBadge != null) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.GoldAntique.copy(alpha = 0.15f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    onlineBadge,
                    style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.GoldAntique,
                )
            }
        } else if (selected) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("✓", style = GaddiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold), color = BrandTokens.GoldAntique)
            }
        }
    }
}

/** Hot-seat human-count picker (pass-and-play). Brass +/- stepper over a labelled rail. */
@Composable
private fun HumanCountPicker(
    humanCount: Int,
    playerCount: Int,
    label: String,
    sublabel: String,
    onChange: (Int) -> Unit,
) {
    // AAA polish: rests on the ground, no bordered gold-tint panel — the brass stepper +
    // GaddiType.label eyebrow already carry the section's identity.
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = GaddiType.label_sm.dmMono().copy(letterSpacing = 1.5.sp),
            color = BrandTokens.GoldAntique,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepperButton(symbol = "−", enabled = humanCount > 2) { onChange(humanCount - 1) }
            Text(
                text = "$humanCount",
                style = GaddiType.title.copy(fontSize = 22.sp),
                color = BrandTokens.GoldAntique,
            )
            StepperButton(symbol = "+", enabled = humanCount < playerCount) { onChange(humanCount + 1) }
            Spacer(Modifier.weight(1f))
            Text(
                text = sublabel,
                style = GaddiType.caption.copy(fontSize = 9.sp, fontStyle = FontStyle.Italic),
                color = GaddiNeutrals.TextMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(2f),
            )
        }
    }
}

/** M6e TEAM KHEL — an enamel toggle that splits the table into two factions (last-team-standing). */
@Composable
private fun TeamToggle(
    on: Boolean,
    onLabel: String,
    offLabel: String,
    teamAName: String,
    teamBName: String,
    onToggle: (Boolean) -> Unit,
) {
    // AAA polish: no filled/bordered toggle panel — the row sits on the ground; the pill switch
    // and gold text already carry the on/off state (non-negotiable #1).
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!on) }
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Switch
                    contentDescription = if (on) onLabel else offLabel
                }.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (on) onLabel else offLabel,
                style = GaddiType.name.copy(fontSize = 13.sp),
                color = if (on) BrandTokens.GoldAntique else GaddiNeutrals.TextSecondary,
                modifier = Modifier.weight(1f),
            )
            // Brass pill switch
            Box(
                modifier =
                    Modifier
                        .size(width = 44.dp, height = 24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (on) BrandTokens.GoldAntique.copy(alpha = 0.4f) else BrandTokens.TeakDark)
                        .border(
                            1.dp,
                            if (on) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.5f),
                            RoundedCornerShape(50),
                        ),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    modifier =
                        Modifier
                            .padding(2.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(if (on) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.7f))
                            .border(1.dp, BrandTokens.BrassAged, CircleShape),
                )
            }
        }
        if (on) {
            // Show the two faction nameplates so the player knows what they're signing up for.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamPill(name = teamAName, teamId = 0)
                Text(
                    stringResource(Res.string.label_vs),
                    style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextMuted,
                )
                TeamPill(name = teamBName, teamId = 1)
            }
        }
    }
}

/** A compact on/off row toggle for Vishesh (Special) variant flags. */
@Composable
private fun VisheshToggle(
    label: String,
    subtitle: String,
    on: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    // AAA polish: bare row on the ground — the pill switch + gold label already read on/off.
    val toggleStateDesc = stringResource(Res.string.a11y_toggle_state, label, if (on) "on" else "off")
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!on) }
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Switch
                    contentDescription = toggleStateDesc
                }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = GaddiType.label.copy(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
                color = if (on) BrandTokens.GoldAntique else GaddiNeutrals.TextSecondary,
            )
            Text(subtitle, style = GaddiType.caption.copy(fontSize = 9.sp), color = GaddiNeutrals.TextMuted, maxLines = 2)
        }
        Box(
            modifier =
                Modifier
                    .size(width = 36.dp, height = 20.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (on) BrandTokens.GoldAntique.copy(alpha = 0.4f) else BrandTokens.TeakDark)
                    .border(0.8.dp, if (on) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.5f), RoundedCornerShape(50)),
            contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier
                        .padding(
                            3.dp,
                        ).size(14.dp)
                        .clip(CircleShape)
                        .background(if (on) BrandTokens.GoldAntique else BrandTokens.BrassAged.copy(alpha = 0.5f)),
            )
        }
    }
}

/** A small faction nameplate pill used in the Team Khel toggle + lobby. */
@Composable
private fun TeamPill(
    name: String,
    teamId: Int,
) {
    val hue = if (teamId == 0) BrandTokens.GoldAntique else BrandTokens.StampRed
    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(hue.copy(alpha = 0.14f))
                .border(0.8.dp, hue.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(name, style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.5.sp, fontWeight = FontWeight.Bold), color = hue)
    }
}

@Composable
private fun StepperButton(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(50))
                .background(if (enabled) BrandTokens.BrassAged.copy(alpha = 0.25f) else BrandTokens.TeakDark.copy(alpha = 0.4f))
                .border(
                    1.dp,
                    if (enabled) BrandTokens.GoldAntique.copy(alpha = 0.7f) else BrandTokens.BrassDark.copy(alpha = 0.3f),
                    RoundedCornerShape(50),
                ).clickable(enabled = enabled, onClick = onClick)
                .alpha(if (enabled) 1f else 0.5f),
        contentAlignment = Alignment.Center,
    ) {
        Text(symbol, style = GaddiType.title.copy(fontSize = 18.sp), color = BrandTokens.GoldAntique)
    }
}

// ─────────────────────────── Player Count Stepper ─────────────────────────────
// Replaces the barely-visible Slider + 14dp bead rail with large +/- buttons
// and a clear numeric display. 56dp buttons meet the 48dp minimum touch target.

@Composable
private fun PlayerCountStepper(
    count: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    val decreaseDesc = stringResource(Res.string.a11y_decrease_players)
    val increaseDesc = stringResource(Res.string.a11y_increase_players)
    val playersLabel = stringResource(Res.string.label_players_plural)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Minus button — large tap target
        Box(
            modifier =
                Modifier
                    .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                    .shadow(
                        if (count >
                            min
                        ) {
                            5.dp
                        } else {
                            0.dp
                        },
                        RoundedCornerShape(10.dp),
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = BrandTokens.TeakInk,
                    ).clip(RoundedCornerShape(10.dp))
                    .background(
                        if (count > min) {
                            Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark))
                        } else {
                            Brush.verticalGradient(listOf(BrandTokens.TeakDark, BrandTokens.TeakDark))
                        },
                    ).border(
                        1.5.dp,
                        if (count > min) BrandTokens.BrassAged.copy(alpha = 0.7f) else BrandTokens.BrassDark.copy(alpha = 0.25f),
                        RoundedCornerShape(10.dp),
                    ).semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = decreaseDesc
                    }.clickable(enabled = count > min) { onChange(count - 1) }
                    .alpha(if (count > min) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) {
            Text("−", style = GaddiType.display.copy(fontSize = 26.sp), color = BrandTokens.GoldAntique)
        }

        // Count display
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$count",
                style = GaddiType.display.copy(fontSize = 48.sp),
                color = BrandTokens.GoldAntique,
            )
            Text(
                text = playersLabel,
                style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 2.sp),
                color = GaddiNeutrals.TextMuted,
            )
            // Compact bead visual (larger beads, easier to read)
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(top = 8.dp),
            ) {
                repeat(max) { i ->
                    Box(
                        modifier =
                            Modifier
                                .size(if (i < count) 10.dp else 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i < count) {
                                        BrandTokens.GoldAntique
                                    } else {
                                        BrandTokens.BrassDark.copy(alpha = 0.3f)
                                    },
                                ),
                    )
                }
            }
        }

        // Plus button — large tap target
        Box(
            modifier =
                Modifier
                    .defaultMinSize(minWidth = 56.dp, minHeight = 56.dp)
                    .shadow(
                        if (count <
                            max
                        ) {
                            5.dp
                        } else {
                            0.dp
                        },
                        RoundedCornerShape(10.dp),
                        clip = false,
                        ambientColor = Color.Black,
                        spotColor = BrandTokens.TeakInk,
                    ).clip(RoundedCornerShape(10.dp))
                    .background(
                        if (count < max) {
                            Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark))
                        } else {
                            Brush.verticalGradient(listOf(BrandTokens.TeakDark, BrandTokens.TeakDark))
                        },
                    ).border(
                        1.5.dp,
                        if (count < max) BrandTokens.GoldAntique.copy(alpha = 0.8f) else BrandTokens.BrassDark.copy(alpha = 0.25f),
                        RoundedCornerShape(10.dp),
                    ).semantics {
                        role = androidx.compose.ui.semantics.Role.Button
                        contentDescription = increaseDesc
                    }.clickable(enabled = count < max) { onChange(count + 1) }
                    .alpha(if (count < max) 1f else 0.4f),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = GaddiType.display.copy(fontSize = 26.sp), color = BrandTokens.GoldAntique)
        }
    }
}

// ─────────────────────────── Difficulty Pill Row ──────────────────────────────
// Replaces 5 full-height cards with a horizontal LazyRow of compact pills —
// dramatically reduces vertical scroll height on mobile.

@Composable
private fun DifficultyPillRow(
    meta: List<DifficultyMeta>,
    selected: Difficulty,
    onSelect: (Difficulty) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        items(meta, key = { it.tier.name }) { m ->
            val isSelected = m.tier == selected
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val pressScale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isPressed) 0.93f else 1f,
                animationSpec =
                    androidx.compose.animation.core
                        .tween(70),
                label = "diffPress_${m.tier.name}",
            )
            // Raised stamp chip — selected = gold-fill w/ dark ink, unselected = dark raised
            // + brass hairline (non-negotiable #4), matching the reference board's action chips.
            Column(
                modifier =
                    Modifier
                        .width(80.dp)
                        .graphicsLayer {
                            scaleX = pressScale
                            scaleY = pressScale
                        }.shadow(
                            if (isSelected) 6.dp else 3.dp,
                            Squircle(GaddiRadii.md),
                            clip = false,
                            ambientColor = Color.Black,
                            spotColor = BrandTokens.TeakInk,
                        ).clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) {
                                Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged))
                            } else {
                                Brush.verticalGradient(listOf(BrandTokens.TeakMid, BrandTokens.TeakDark))
                            },
                        ).border(
                            if (isSelected) 1.5.dp else GaddiDimens.stroke_ring_idle,
                            if (isSelected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.6f),
                            RoundedCornerShape(10.dp),
                        ).semantics(mergeDescendants = true) {
                            role = androidx.compose.ui.semantics.Role.RadioButton
                            contentDescription = m.nameplate
                        }.clickable(interactionSource = interactionSource, indication = null) { onSelect(m.tier) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Difficulty level indicator: filled dots
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    val level = meta.indexOf(m) + 1
                    repeat(5) { i ->
                        Box(
                            modifier =
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (i < level) {
                                            if (isSelected) BrandTokens.TeakInk else BrandTokens.BrassAged
                                        } else {
                                            BrandTokens.BrassDark.copy(alpha = 0.3f)
                                        },
                                    ),
                        )
                    }
                }
                Text(
                    text = m.nameplate,
                    style = GaddiType.caption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp),
                    color = if (isSelected) BrandTokens.TeakInk else GaddiNeutrals.TextSecondary,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isSelected) {
                    Text(
                        stringResource(Res.string.label_selected_check),
                        style = GaddiType.caption.copy(fontSize = 7.sp, letterSpacing = 0.3.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.TeakInk.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────── Advanced Options Section ─────────────────────────
// Collapsible wrapper so casual players see a short form; power players expand.

@Composable
private fun AdvancedOptionsSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    hasActiveOption: Boolean,
    content: @Composable () -> Unit,
) {
    val advancedOptionsDesc =
        stringResource(Res.string.a11y_advanced_options, if (expanded) "Tap to collapse." else "Tap to expand.")
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Header row — tap to expand/collapse. A hairline row on the ground, not a bordered tile.
        HairlineRow(
            onClick = onToggle,
            showDivider = !expanded,
            verticalPadding = 12.dp,
            modifier =
                Modifier.semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = advancedOptionsDesc
                },
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(Res.string.setup_advanced_options_label),
                        style = GaddiType.label_sm.dmMono().copy(letterSpacing = 1.5.sp),
                        color = if (hasActiveOption) BrandTokens.GoldAntique else BrandTokens.BrassAged,
                    )
                    if (hasActiveOption) {
                        Box(
                            modifier =
                                Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(BrandTokens.GoldAntique.copy(alpha = 0.2f))
                                    .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                stringResource(Res.string.label_active_badge),
                                style = GaddiType.caption.copy(fontSize = 8.sp, letterSpacing = 0.5.sp),
                                color = BrandTokens.GoldAntique,
                            )
                        }
                    }
                }
                Text(
                    text = stringResource(Res.string.setup_advanced_options_sub),
                    style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextMuted,
                )
            }
            Text(
                text = if (expanded) "▲" else "▼",
                style = GaddiType.body.copy(fontSize = 14.sp),
                color = BrandTokens.BrassAged,
            )
        }

        // Animated content
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
        ) {
            content()
        }
    }
}

/** A selectable draft-preset option row for the Nilaami picker. */
@Composable
private fun DraftOptionChit(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    HairlineRow(
        onClick = onSelect,
        verticalPadding = 8.dp,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.RadioButton
                contentDescription = "$title. $subtitle"
            },
    ) {
        Box(
            modifier =
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (selected) {
                            BrandTokens.GoldAntique
                        } else {
                            BrandTokens.BrassDark.copy(alpha = 0.4f)
                        },
                    ).border(1.dp, BrandTokens.BrassAged, RoundedCornerShape(50)),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = GaddiType.name.copy(fontSize = 13.sp),
                color = if (selected) BrandTokens.GoldAntique else GaddiNeutrals.TextPrimary,
            )
            Text(
                text = subtitle,
                style = GaddiType.caption.copy(fontSize = 9.sp),
                color = GaddiNeutrals.TextMuted,
            )
        }
        if (selected) {
            Box(
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                        .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "✓",
                    style = GaddiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                    color = BrandTokens.GoldAntique,
                )
            }
        }
    }
}

/** Map the AI tier enum back to the UI-layer difficulty used by the Setup/Lobby flow. */
private fun BotDifficulty.toUiDifficulty(): Difficulty =
    when (this) {
        BotDifficulty.EASY -> Difficulty.Easy
        BotDifficulty.MEDIUM -> Difficulty.Medium
        BotDifficulty.HARD -> Difficulty.Hard
        BotDifficulty.EXPERT -> Difficulty.Expert
        BotDifficulty.GRANDMASTER -> Difficulty.Grandmaster
    }

/** One-tap QUICK MATCH — a hero requisition that stamps straight to the table from saved defaults. */
@Composable
private fun QuickMatchChit(
    label: String,
    sublabel: String,
    onClick: () -> Unit,
) {
    // A raised gold stamp — the one true hero CTA of the presets list, so it earns real depth
    // (non-negotiable #4), unlike the hairline rows underneath it.
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, Squircle(GaddiRadii.md), clip = false, ambientColor = Color.Black, spotColor = BrandTokens.TeakInk)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.verticalGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassAged)))
                .border(1.5.dp, BrandTokens.GoldAntique, RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .semantics(mergeDescendants = true) {
                    role = androidx.compose.ui.semantics.Role.Button
                    contentDescription = "$label. $sublabel"
                }.padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label, style = GaddiType.title.copy(fontSize = 17.sp, fontWeight = FontWeight.Bold), color = BrandTokens.TeakDark)
                Text(
                    sublabel,
                    style = GaddiType.caption.copy(fontSize = 10.sp),
                    color = BrandTokens.BrassDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // APPROVED-style instant stamp
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
                    stringResource(Res.string.cta_quick_start_stamp),
                    style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 1.sp, fontWeight = FontWeight.Bold),
                    color = GaddiNeutrals.Cream,
                )
            }
        }
    }
}

/** A curated persona-lineup preset row — name, sublabel, a brass monogram rail of the cast. */
@Composable
private fun PresetChit(
    name: String,
    sublabel: String,
    stamp: String,
    lineupMonograms: List<String>,
    onClick: () -> Unit,
) {
    // AAA polish: a hairline row on the ground, matching every other selectable list in the
    // form — the monogram rail + gold name already carry the "curated cast" identity.
    HairlineRow(
        onClick = onClick,
        verticalPadding = 10.dp,
        modifier =
            Modifier.semantics(mergeDescendants = true) {
                role = androidx.compose.ui.semantics.Role.Button
                contentDescription = "$name. $sublabel"
            },
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(name, style = GaddiType.name.copy(fontSize = 14.sp, letterSpacing = 0.5.sp), color = BrandTokens.GoldAntique)
                    Text(
                        sublabel,
                        style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = GaddiNeutrals.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                            .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        stamp,
                        style = GaddiType.caption.copy(fontSize = 9.sp, letterSpacing = 0.6.sp),
                        color = BrandTokens.GoldAntique.copy(alpha = 0.85f),
                    )
                }
            }
            // Brass monogram rail — the curated cast, capped so 10p stays legible.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                val shown = lineupMonograms.take(6)
                shown.forEach { mono ->
                    Box(
                        modifier =
                            Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(Brush.radialGradient(listOf(BrandTokens.BrassAged, BrandTokens.BrassDark)))
                                .border(0.8.dp, BrandTokens.GoldAntique.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            mono.take(2),
                            style = GaddiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                            color = BrandTokens.TeakDark,
                        )
                    }
                }
                if (lineupMonograms.size > shown.size) {
                    Text(
                        "+${lineupMonograms.size - shown.size}",
                        style = GaddiType.caption.copy(fontSize = 9.sp),
                        color = GaddiNeutrals.TextMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun BrassAbacusRail(
    count: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) { i ->
            val isActive = i < count
            Box(
                modifier =
                    Modifier
                        .size(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isActive) {
                                Brush.radialGradient(listOf(BrandTokens.GoldAntique, BrandTokens.BrassDark))
                            } else {
                                Brush.radialGradient(
                                    listOf(BrandTokens.BrassDark.copy(alpha = 0.3f), BrandTokens.TeakDark.copy(alpha = 0.5f)),
                                )
                            },
                        ).border(
                            1.dp,
                            if (isActive) BrandTokens.BrassAged else BrandTokens.BrassDark.copy(alpha = 0.3f),
                            RoundedCornerShape(50),
                        ),
                contentAlignment = Alignment.Center,
            ) {
                if (i == 0) {
                    Text("A", style = GaddiType.caption.copy(fontSize = 6.sp), color = BrandTokens.TeakDark, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
private fun DifficultyTab(
    meta: DifficultyMeta,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (selected) BrandTokens.BrassAged.copy(alpha = 0.2f) else Color.Transparent,
                ).border(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.4f),
                    RoundedCornerShape(8.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(
                        if (selected) BrandTokens.GoldAntique else BrandTokens.BrassDark.copy(alpha = 0.5f),
                    ),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = meta.nameplate,
                    style = GaddiType.name.copy(fontSize = 13.sp, letterSpacing = 0.5.sp),
                    color = if (selected) BrandTokens.GoldAntique else GaddiNeutrals.TextSecondary,
                )
                Text(
                    text = "\"${meta.voiceLine}\"",
                    style = GaddiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                    color = GaddiNeutrals.TextMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected) {
                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(BrandTokens.BrassAged.copy(alpha = 0.2f))
                            .border(0.7.dp, BrandTokens.GoldAntique.copy(alpha = 0.7f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        "✓",
                        style = GaddiType.caption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = BrandTokens.GoldAntique,
                    )
                }
            }
        }
    }
}
