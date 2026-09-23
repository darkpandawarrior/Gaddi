package com.kursi.designsystem.moment

// ═══════════════════════════════════════════════════════════════════════════════
// MomentSamples.kt — KDoc usage examples for screen wiring.
// For integration reference only. No @Preview (requires no UI dependency).
//
// Design: gaddi-plan/docs/15c_action_moments.md §3
// ═══════════════════════════════════════════════════════════════════════════════

/**
 * Example: how a game screen wires the [ActionMomentOverlay] + fires moments.
 *
 * The overlay is placed as the LAST child of the root Box so it draws above the felt
 * table but below any system-level overlays. The [MomentHost] is the only coupling
 * point: the presentation reducer enqueues moments, the overlay plays them FIFO.
 *
 * ```kotlin
 * @Composable
 * fun GameScreen(viewModel: GameViewModel) {
 *     val host = rememberMomentHost()
 *
 *     // Build TableAnchors from measured layout positions.
 *     // In production this comes from onGloballyPositioned() callbacks on each seat.
 *     val anchors = TableAnchors(
 *         seatCenters = mapOf(
 *             0 to Offset(200f, 400f),   // your seat
 *             1 to Offset(800f, 150f),
 *             2 to Offset(800f, 700f),
 *         ),
 *         treasuryCenter = Offset(500f, 400f),
 *     )
 *
 *     // Wire engine events → moments
 *     val engineEvent by viewModel.lastEvent.collectAsStateWithLifecycle()
 *     LaunchedEffect(engineEvent) {
 *         engineEvent?.let { event ->
 *             val moment = event.toMoment()   // your mapping function
 *             host.play(moment)
 *         }
 *     }
 *
 *     // Example: chain Reveal → InfluenceLoss → Elimination in one call
 *     LaunchedEffect(revealResult) {
 *         host.playAll(
 *             GaddiMoment.Reveal(
 *                 actorSeat = 1,
 *                 claimant = 1,
 *                 claimedRole = "NETA",
 *                 truthful = false,
 *                 roleHue = GaddiRoleHues.Neta,
 *             ),
 *             GaddiMoment.InfluenceLoss(
 *                 actorSeat = 1,
 *                 lostRole = "NETA",
 *                 roleHue = GaddiRoleHues.Neta,
 *             ),
 *             GaddiMoment.Elimination(actorSeat = 1),
 *         )
 *     }
 *
 *     Box(Modifier.fillMaxSize()) {
 *         // ── Game table ──────────────────────────────────────────────────────
 *         GameTable(
 *             viewModel = viewModel,
 *             modifier = Modifier.fillMaxSize(),
 *         )
 *
 *         // ── Moment overlay — ABOVE table, tap-to-skip handled inside ───────
 *         ActionMomentOverlay(
 *             host = host,
 *             anchors = anchors,
 *             reducedMotion = LocalReducedMotion.current,  // your CompositionLocal
 *             onMomentDone = { moment ->
 *                 // Optional: log, analytics, etc.
 *             },
 *             modifier = Modifier.fillMaxSize(),
 *         )
 *
 *         // ── Action dock stays on top, fully interactive ─────────────────────
 *         ActionDock(
 *             viewModel = viewModel,
 *             modifier = Modifier.align(Alignment.BottomCenter),
 *         )
 *     }
 * }
 * ```
 *
 * ### Mapping engine events to moments (sketch)
 *
 * ```kotlin
 * fun EngineEvent.toMoment(): GaddiMoment = when (this) {
 *     is EngineEvent.IncomeResolved       -> GaddiMoment.Income(actorSeat = actorSeatId)
 *     is EngineEvent.ForeignAidResolved   -> GaddiMoment.ForeignAid(actorSeat = actorSeatId)
 *     is EngineEvent.TaxResolved          -> GaddiMoment.Tax(
 *                                               actorSeat = actorSeatId,
 *                                               roleHue = GaddiRoleHues.Neta,
 *                                           )
 *     is EngineEvent.StealResolved        -> GaddiMoment.Steal(
 *                                               actorSeat = actorSeatId,
 *                                               victim = victimSeatId,
 *                                               roleHue = GaddiRoleHues.Babu,
 *                                           )
 *     is EngineEvent.AssassinateResolved  -> GaddiMoment.Assassinate(
 *                                               actorSeat = actorSeatId,
 *                                               target = targetSeatId,
 *                                               roleHue = GaddiRoleHues.Bhai,
 *                                           )
 *     is EngineEvent.ExchangeResolved     -> GaddiMoment.Exchange(
 *                                               actorSeat = actorSeatId,
 *                                               roleHue = GaddiRoleHues.Jugaadu,
 *                                           )
 *     is EngineEvent.CoupResolved         -> GaddiMoment.Coup(
 *                                               actorSeat = actorSeatId,
 *                                               target = targetSeatId,
 *                                           )
 *     is EngineEvent.BlockResolved        -> GaddiMoment.Block(
 *                                               actorSeat = blockerSeatId,
 *                                               blockedSeat = actorSeatId,
 *                                               roleHue = blockerRoleHue,
 *                                           )
 *     is EngineEvent.ChallengeThrown      -> GaddiMoment.Challenge(
 *                                               actorSeat = challengerSeatId,
 *                                               claimant = claimantSeatId,
 *                                           )
 *     is EngineEvent.ChallengeRevealDone  -> GaddiMoment.Reveal(
 *                                               actorSeat = challengerSeatId,
 *                                               claimant = claimantSeatId,
 *                                               claimedRole = claimedRoleName,
 *                                               truthful = wasTruthful,
 *                                               roleHue = roleHue,
 *                                           )
 *     is EngineEvent.InfluenceLost        -> GaddiMoment.InfluenceLoss(
 *                                               actorSeat = losingSeatId,
 *                                               lostRole = lostRoleName,
 *                                               roleHue = lostRoleHue,
 *                                           )
 *     is EngineEvent.PlayerEliminated     -> GaddiMoment.Elimination(actorSeat = eliminatedSeatId)
 *     is EngineEvent.TurnChanged          -> GaddiMoment.TurnHandoff(
 *                                               actorSeat = prevSeatId,
 *                                               nextSeat = nextSeatId,
 *                                           )
 *     is EngineEvent.GameWon              -> GaddiMoment.Win(actorSeat = winnerSeatId)
 * }
 * ```
 *
 * ### Instant-fire examples (copy-paste ready)
 *
 * ```kotlin
 * // Single economic action:
 * host.play(GaddiMoment.Tax(actorSeat = 0, roleHue = GaddiRoleHues.Neta))
 *
 * // The hero:
 * host.play(GaddiMoment.Coup(actorSeat = 0, target = 2))
 *
 * // Chained reaction sequence (FIFO — each plays after the previous):
 * host.playAll(
 *     GaddiMoment.Reveal(
 *         actorSeat = 1, claimant = 1,
 *         claimedRole = "BHAI", truthful = false,
 *         roleHue = GaddiRoleHues.Bhai,
 *     ),
 *     GaddiMoment.InfluenceLoss(
 *         actorSeat = 1, lostRole = "BHAI", roleHue = GaddiRoleHues.Bhai
 *     ),
 * )
 *
 * // Clear on game reset:
 * host.clearQueue()
 * ```
 */
object MomentUsageSample
