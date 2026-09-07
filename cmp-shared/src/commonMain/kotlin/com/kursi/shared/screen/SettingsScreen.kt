package com.kursi.shared.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kursi.ai.provider.createSecureKeyStore
import com.kursi.core.prefs.AppPrefs
import com.kursi.core.prefs.TurnSpeed
import com.kursi.designsystem.*
import com.kursi.feature.game.Difficulty
import com.kursi.shared.strings.LocalKursiStrings
import com.siddharth.kmp.ai.NoModelManager
import com.siddharth.kmp.ai.UnavailableOnDeviceLlm
import com.siddharth.kmp.designsystem.ai.AiConsentStore
import com.siddharth.kmp.designsystem.ai.AiSettingsSection
import com.siddharth.kmp.designsystem.ai.AiSettingsState
import com.siddharth.kmp.llmchat.ProviderId
import kursi.core.designsystem.generated.resources.Res
import kursi.core.designsystem.generated.resources.a11y_player_count_radio
import kursi.core.designsystem.generated.resources.settings_edit_profile_label
import kursi.core.designsystem.generated.resources.settings_title
import org.jetbrains.compose.resources.stringResource

/**
 * SettingsScreen — Daftari (S6) from 17_app_plan.md §4.
 *
 * Sarkari Noir rebuild: engraved nav header, every section an [EngravedHeader] resting on the
 * lit ground (no bordered SettingsSection cards), toggle/link rows as [HairlineRow]s, and
 * segmented pickers as raised [StampButton] chips / [BrassToken] steppers instead of
 * outline-only pills — design-language.md non-negotiables #1, #3, #4.
 *
 * - AAWAZ: sound toggle
 * - DIKHAWA: reduced motion toggle
 * - KHEL: default difficulty, default player count
 * - SEEKH: replay primer link
 * - BAARE MEIN: about / disclaimer
 */
@Composable
fun SettingsScreen(
    prefs: AppPrefs,
    onBack: () -> Unit,
    onReplayPrimer: () -> Unit,
    onGazette: () -> Unit,
    onEditProfile: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val s = LocalKursiStrings.current
    val scrollState = rememberScrollState()

    // Read prefs into local state
    var soundEnabled by remember { mutableStateOf(prefs.soundEnabled) }
    var reducedMotion by remember { mutableStateOf(prefs.reducedMotion) }
    var defaultDifficulty by remember { mutableStateOf(prefs.defaultDifficulty) }
    var defaultPlayers by remember { mutableIntStateOf(prefs.defaultPlayerCount) }
    var language by remember { mutableStateOf(prefs.language) }
    var coachEnabled by remember { mutableStateOf(prefs.coachEnabled) }
    var turnSpeed by remember { mutableStateOf(prefs.turnSpeed) }
    var autoPass by remember { mutableStateOf(prefs.autoPass) }
    var autoForced by remember { mutableStateOf(prefs.autoPlayForced) }

    // ── MUNSHI (AI narration BYOK) ──────────────────────────────────────────
    val aiScope = rememberCoroutineScope()
    val secureKeyStore = remember { createSecureKeyStore() }
    val aiSettingsState =
        remember {
            AiSettingsState(
                // ponytail: on-device model download management (a real per-platform ModelManager +
                // manifest) is a separate feature this lane doesn't add — on-device narration already
                // works automatically without this screen (OnDeviceAiProvider, picked up with zero
                // setup). This card stays BYOK-focused: NoModelManager/UnavailableOnDeviceLlm report
                // "nothing downloadable / not available" everywhere honestly, rather than faking a
                // real capability check. Upgrade path: a com.kursi.ai.provider on-device accessor +
                // a Gemma manifest entry, once model-download UI is actually scoped.
                modelManager = NoModelManager,
                manifest = emptyList(),
                onDeviceLlm = UnavailableOnDeviceLlm,
                getKey = secureKeyStore::getKey,
                setKey = secureKeyStore::setKey,
                scope = aiScope,
                consentStore =
                    object : AiConsentStore {
                        override fun consentGiven(): Boolean = prefs.aiConsentGiven

                        override fun setConsent(consent: Boolean) {
                            prefs.aiConsentGiven = consent
                        }
                    },
            ).also { state ->
                ProviderId.entries.firstOrNull { it.name == prefs.aiSelectedProviderName }?.let(state::selectProvider)
            }
        }
    val aiUiState by aiSettingsState.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize().litGround()) {
        EngravedNavHeader(
            title = s.settingsTitle,
            onBack = onBack,
            backLabel = s.back,
            modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
        )

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(26.dp),
            ) {
                // ── PROFILE ───────────────────────────────────────────────────────
                if (onEditProfile != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        EngravedHeader(eyebrow = stringResource(Res.string.settings_title))
                        HairlineRow(
                            onClick = onEditProfile,
                            showDivider = false,
                        ) {
                            BrassToken(
                                monogram = prefs.displayName,
                                fill = if (prefs.playerColorArgb != 0L) Color(prefs.playerColorArgb) else BrandTokens.StampRed,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = prefs.displayName,
                                    style = KursiType.name.copy(fontSize = 15.sp),
                                    color = KursiNeutrals.TextPrimary,
                                )
                                Text(
                                    text = stringResource(Res.string.settings_edit_profile_label),
                                    style = KursiType.label_micro.copy(letterSpacing = 0.8.sp, fontSize = 10.sp),
                                    color = BrandTokens.BrassAged.copy(alpha = 0.7f),
                                )
                            }
                            Text("›", style = KursiType.title.copy(fontSize = 18.sp), color = BrandTokens.BrassAged)
                        }
                    }
                }

                // ── BHASHA (Language) ──────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EngravedHeader(eyebrow = s.settingsLanguageSection)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf(s.settingsLangHinglish to "HINGLISH", s.settingsLangEnglish to "ENGLISH").forEach { (label, code) ->
                            StampButton(
                                label = label,
                                onClick = {
                                    language = code
                                    prefs.language = code
                                },
                                style = if (language == code) StampStyle.Primary else StampStyle.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                // ── AAWAZ (Sound) ──────────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EngravedHeader(eyebrow = s.settingsSoundSection)
                    SettingsToggleRow(
                        label = s.settingsSoundLabel,
                        sublabel = s.settingsSoundSub,
                        checked = soundEnabled,
                        showDivider = false,
                        onCheckedChange = { v ->
                            soundEnabled = v
                            prefs.soundEnabled = v
                        },
                    )
                }

                // ── MASHWARA (Decision Coach) ──────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EngravedHeader(eyebrow = s.settingsCoachSection)
                    SettingsToggleRow(
                        label = s.settingsCoachLabel,
                        sublabel = s.settingsCoachSub,
                        checked = coachEnabled,
                        showDivider = false,
                        onCheckedChange = { v ->
                            coachEnabled = v
                            prefs.coachEnabled = v
                        },
                    )
                }

                // ── AUTO-MODE (M5 turn-speed + assistant) ──────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EngravedHeader(eyebrow = s.settingsAutoSection)
                    Text(
                        s.settingsTurnSpeedLabel,
                        style = KursiType.body.copy(fontSize = 12.sp),
                        color = KursiNeutrals.TextSecondary,
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            TurnSpeed.SLOW to s.settingsSpeedSlow,
                            TurnSpeed.NORMAL to s.settingsSpeedNormal,
                            TurnSpeed.FAST to s.settingsSpeedFast,
                        ).forEach { (speed, label) ->
                            StampButton(
                                label = label,
                                onClick = {
                                    turnSpeed = speed
                                    prefs.turnSpeed = speed
                                },
                                style = if (turnSpeed == speed) StampStyle.Primary else StampStyle.Secondary,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        SettingsToggleRow(
                            label = s.settingsAutoPassLabel,
                            sublabel = s.settingsAutoPassSub,
                            checked = autoPass,
                            onCheckedChange = { v ->
                                autoPass = v
                                prefs.autoPass = v
                            },
                        )
                        SettingsToggleRow(
                            label = s.settingsAutoForcedLabel,
                            sublabel = s.settingsAutoForcedSub,
                            checked = autoForced,
                            showDivider = false,
                            onCheckedChange = { v ->
                                autoForced = v
                                prefs.autoPlayForced = v
                            },
                        )
                    }
                }

                // ── DIKHAWA (Motion & display) ─────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EngravedHeader(eyebrow = s.settingsMotionSection)
                    SettingsToggleRow(
                        label = s.settingsMotionLabel,
                        sublabel = s.settingsMotionSub,
                        checked = reducedMotion,
                        showDivider = false,
                        onCheckedChange = { v ->
                            reducedMotion = v
                            prefs.reducedMotion = v
                        },
                    )
                }

                // ── KHEL (Gameplay defaults) ───────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EngravedHeader(eyebrow = s.settingsDefaultsSection)
                    Text(
                        s.settingsDefaultDiffLabel,
                        style = KursiType.body.copy(fontSize = 12.sp),
                        color = KursiNeutrals.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Difficulty.entries.forEach { d ->
                            StampButton(
                                label = d.name.uppercase(),
                                onClick = {
                                    defaultDifficulty = d.name
                                    prefs.defaultDifficulty = d.name
                                },
                                style = if (d.name == defaultDifficulty) StampStyle.Primary else StampStyle.Secondary,
                                modifier = Modifier.width(104.dp),
                            )
                        }
                    }

                    Text(
                        s.settingsDefaultPlayersLabel(defaultPlayers),
                        style = KursiType.body.copy(fontSize = 12.sp),
                        color = KursiNeutrals.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        (2..10).forEach { n ->
                            val isSelected = n == defaultPlayers
                            val playerCountDesc = stringResource(Res.string.a11y_player_count_radio, n)
                            BrassToken(
                                monogram = "$n",
                                fill = if (isSelected) BrandTokens.GoldAntique else BrandTokens.TeakMid,
                                size = 38.dp,
                                modifier =
                                    Modifier
                                        .clickable {
                                            defaultPlayers = n
                                            prefs.defaultPlayerCount = n
                                        }.semantics(mergeDescendants = true) {
                                            role = Role.RadioButton
                                            contentDescription = playerCountDesc
                                            selected = isSelected
                                        },
                            )
                        }
                    }
                }

                // ── SEEKH (Learning) ───────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    EngravedHeader(eyebrow = s.settingsLearningSection)
                    SettingsLinkRow(label = s.settingsReplayPrimerLabel, sublabel = s.settingsReplayPrimerSub, onClick = onReplayPrimer)
                    SettingsLinkRow(label = s.settingsRulesLabel, sublabel = s.settingsRulesSub, onClick = onGazette, showDivider = false)
                }

                // ── MUNSHI (AI narration BYOK) ──────────────────────────────────────
                // ponytail: hardcoded English label, no LocalKursiStrings entry — the toolkit
                // AiSettingsSection card below is itself all hardcoded English (no bilingual support
                // yet), so a localized eyebrow here would be a language mismatch with its own body.
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EngravedHeader(eyebrow = "AI NARRATION")
                    AiSettingsSection(
                        uiState = aiUiState,
                        onConsentChange = aiSettingsState::setAiConsent,
                        onStartDownload = aiSettingsState::startDownload,
                        onPauseDownload = aiSettingsState::pauseDownload,
                        onDeleteModel = aiSettingsState::deleteModel,
                        onSelectProvider = { id ->
                            aiSettingsState.selectProvider(id)
                            prefs.aiSelectedProviderName = id.name
                        },
                        onProviderKeyChange = aiSettingsState::setProviderKey,
                        onClearProviderKey = aiSettingsState::clearProviderKey,
                        onTestProviderKey = aiSettingsState::testKey,
                    )
                }

                // ── BAARE MEIN (About) ─────────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    EngravedHeader(eyebrow = s.settingsAboutSection)
                    Text(
                        s.settingsAboutTitle,
                        style = KursiType.body.copy(fontSize = 13.sp),
                        color = KursiNeutrals.TextPrimary,
                    )
                    Text(
                        s.settingsAboutDisclaimer,
                        style = KursiType.caption.copy(fontSize = 10.sp, fontStyle = FontStyle.Italic),
                        color = KursiNeutrals.TextMuted,
                    )
                    Text(
                        s.settingsAboutFooter,
                        style = KursiType.caption.copy(fontSize = 9.sp),
                        color = KursiNeutrals.TextDisabled,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

// ─────────────────────────── Sub-components ───────────────────────────────────

@Composable
private fun SettingsToggleRow(
    label: String,
    sublabel: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    showDivider: Boolean = true,
) {
    HairlineRow(showDivider = showDivider) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = KursiType.name.copy(fontSize = 13.sp),
                color = KursiNeutrals.TextPrimary,
            )
            Text(
                sublabel,
                style = KursiType.caption.copy(fontSize = 10.sp),
                color = KursiNeutrals.TextMuted,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = BrandTokens.GoldAntique,
                    checkedTrackColor = BrandTokens.BrassAged.copy(alpha = 0.5f),
                    uncheckedThumbColor = BrandTokens.BrassDark,
                    uncheckedTrackColor = BrandTokens.TeakDark,
                ),
        )
    }
}

@Composable
private fun SettingsLinkRow(
    label: String,
    sublabel: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    HairlineRow(onClick = onClick, showDivider = showDivider) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = KursiType.name.copy(fontSize = 13.sp),
                color = KursiNeutrals.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                sublabel,
                style = KursiType.caption.copy(fontSize = 10.sp),
                color = KursiNeutrals.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = KursiType.title.copy(fontSize = 18.sp), color = BrandTokens.BrassAged)
    }
}
