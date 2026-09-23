package com.kursi.ai.persona

/**
 * The canonical 10-persona roster, spec §15b.
 *
 * Colors are LOCKED Okabe-Ito role hues or the reserved Inspector slate:
 *   Neta blue       #0072B2 → 0xFF0072B2L
 *   Bhai vermillion #D55E00 → 0xFFD55E00L
 *   Babu green      #009E73 → 0xFF009E73L
 *   Jugaadu orange  #E69F00 → 0xFFE69F00L
 *   Vakil purple    #CC79A7 → 0xFFCC79A7L
 *   Inspector slate #56638A → 0xFF56638AL  (reserved off-hue)
 */
object PersonaRoster {
    val ALL: List<BotPersona> =
        listOf(
            // ── 1. Netaji Vachan ──────────────────────────────────────────────────
            BotPersona(
                id = "netaji_vachan",
                name = "Netaji Vachan",
                title = "The Eternal Candidate",
                archetype = "Schemer/Deceiver",
                seatColorArgb = 0xFF0072B2L,
                monogram = "NV",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.75f,
                        challengeAggression = 0.35f,
                        economicAggression = 0.40f,
                        targetingBias = TargetingBias.LEADER,
                        risk = 0.65f,
                        vindictiveness = 0.15f,
                        predictability = 0.45f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Janta ka paisa, janta ke liye... mere through."),
                            BarkEvent.BLUFF_DECLARE to listOf("Main toh sirf samaj sevak hoon. Trust me."),
                            BarkEvent.CHALLENGED to listOf("Mujh par ungli? Defamation case ready rakho."),
                            BarkEvent.WIN_GAME to listOf("Yeh gaddi nahi, janta ka pyaar hai."),
                            BarkEvent.ELIMINATED to listOf("Main wapas aaunga. Election ke baad."),
                            BarkEvent.TAUNT to listOf("Vote… I mean, coins… is khaate mein daalo."),
                        ),
                    ),
            ),
            // ── 2. Bhai Teja ─────────────────────────────────────────────────────
            BotPersona(
                id = "bhai_teja",
                name = "Bhai Teja",
                title = "Owns the Silence",
                archetype = "Aggressor/Enforcer",
                seatColorArgb = 0xFFD55E00L,
                monogram = "BT",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.20f,
                        challengeAggression = 0.55f,
                        economicAggression = 0.85f,
                        targetingBias = TargetingBias.LEADER,
                        risk = 0.55f,
                        vindictiveness = 0.70f,
                        predictability = 0.75f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Kaam ho gaya. Number delete kar do."),
                            BarkEvent.BLUFF_DECLARE to listOf("Main businessman hoon. Yeh sab galatfehmi hai."),
                            BarkEvent.CHALLENGED to listOf("Tune mujhe challenge kiya? Bahadur hai tu."),
                            BarkEvent.WIN_GAME to listOf("Bola tha na — sab setting hai."),
                            BarkEvent.ELIMINATED to listOf("Galti ek hi baar hoti hai. Aaj meri thi."),
                            BarkEvent.TAUNT to listOf("Baith ja. Abhi time hai."),
                        ),
                    ),
            ),
            // ── 3. Babu Filewala ──────────────────────────────────────────────────
            BotPersona(
                id = "babu_filewala",
                name = "Babu Filewala",
                title = "Approver of Nothing",
                archetype = "Cautious Banker",
                seatColorArgb = 0xFF009E73L,
                monogram = "BF",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.15f,
                        challengeAggression = 0.30f,
                        economicAggression = 0.20f,
                        targetingBias = TargetingBias.WEAKEST,
                        risk = 0.20f,
                        vindictiveness = 0.10f,
                        predictability = 0.70f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Processing fee laga hai. Receipt nahi milega."),
                            BarkEvent.BLUFF_DECLARE to listOf("Rule book mere paas hai. Aapke paas?"),
                            BarkEvent.BLOCK to listOf("File abhi pending hai."),
                            BarkEvent.CHALLENGED to listOf("Aapne galat counter pe complaint ki hai."),
                            BarkEvent.WIN_GAME to listOf("Saara kaam approved. Apne aap ko chhod ke."),
                            BarkEvent.ELIMINATED to listOf("Transfer ho gaya. Dehradun. Theek hai."),
                            BarkEvent.TAUNT to listOf("Kal aana. Parso ka token le lo."),
                        ),
                    ),
            ),
            // ── 4. Jugaadu Chhotu ─────────────────────────────────────────────────
            BotPersona(
                id = "jugaadu_chhotu",
                name = "Jugaadu Chhotu",
                title = "Sab Fix",
                archetype = "Chaos/Wildcard",
                seatColorArgb = 0xFFE69F00L,
                monogram = "JC",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.60f,
                        challengeAggression = 0.45f,
                        economicAggression = 0.50f,
                        targetingBias = TargetingBias.RANDOM,
                        risk = 0.70f,
                        vindictiveness = 0.20f,
                        predictability = 0.20f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Setting ho gaya bhai. Tension nahi lene ka."),
                            BarkEvent.BLUFF_DECLARE to listOf("Card? Kaunsa card? Mere paas toh kuch nahi."),
                            BarkEvent.BLOCK to listOf("Arre, isko main dekh leta hoon."),
                            BarkEvent.CHALLENGED to listOf("Arre ruko ruko, ek minute, sun toh lo—"),
                            BarkEvent.WIN_GAME to listOf("Bina paise ke gaddi. Asli jugaad yeh hai."),
                            BarkEvent.ELIMINATED to listOf("Koi baat nahi. Naya scene dhoondte hain."),
                            BarkEvent.TAUNT to listOf("Tera kaam bhi ho jayega… price hai."),
                        ),
                    ),
            ),
            // ── 5. Vakil Loophole ─────────────────────────────────────────────────
            BotPersona(
                id = "vakil_loophole",
                name = "Vakil Loophole",
                title = "Finds the Clause",
                archetype = "Vindictive Defender",
                seatColorArgb = 0xFFCC79A7L,
                monogram = "VL",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.40f,
                        challengeAggression = 0.70f,
                        economicAggression = 0.30f,
                        targetingBias = TargetingBias.VINDICTIVE,
                        risk = 0.45f,
                        vindictiveness = 0.85f,
                        predictability = 0.50f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Stay order. Tareekh pe tareekh, milord."),
                            BarkEvent.BLUFF_DECLARE to listOf("Constitution ki dhara 420... uh, padhi hai maine."),
                            BarkEvent.BLOCK to listOf("Note it down."),
                            BarkEvent.CHALLENGED to listOf("Yeh court ki avmanna hai. Note kijiye."),
                            BarkEvent.WIN_GAME to listOf("Verdict mere haq mein. Jaise hamesha."),
                            BarkEvent.ELIMINATED to listOf("Appeal karunga. Upper court tak jaayenge."),
                            BarkEvent.TAUNT to listOf("Costs follow the event, my friend."),
                        ),
                    ),
            ),
            // ── 6. Inspector Damaad ───────────────────────────────────────────────
            BotPersona(
                id = "inspector_damaad",
                name = "Inspector Damaad",
                title = "The Agency",
                archetype = "Honest Plodder",
                seatColorArgb = 0xFF56638AL, // reserved slate
                monogram = "ID",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.10f,
                        challengeAggression = 0.65f,
                        economicAggression = 0.35f,
                        targetingBias = TargetingBias.LEADER,
                        risk = 0.25f,
                        vindictiveness = 0.30f,
                        predictability = 0.80f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Yeh case mere under hai. Aur tu bhi."),
                            BarkEvent.BLUFF_DECLARE to listOf("Wardi pe ungli mat utha. Galat hoga."),
                            BarkEvent.CHALLENGED to listOf("FIR likhna hai? Mere thane mein? Likh."),
                            BarkEvent.WIN_GAME to listOf("Law and order. Mera law, mera order."),
                            BarkEvent.ELIMINATED to listOf("Suspend? Sasur ko phone lagao, abhi."),
                        ),
                    ),
            ),
            // ── 7. Seth Khokhawala ────────────────────────────────────────────────
            BotPersona(
                id = "seth_khokhawala",
                name = "Seth Khokhawala",
                title = "The Financier",
                archetype = "Opportunist",
                seatColorArgb = 0xFF009E73L, // Babu green — distinct monogram/name from Filewala
                monogram = "SK",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.30f,
                        challengeAggression = 0.40f,
                        economicAggression = 0.55f,
                        targetingBias = TargetingBias.WEAKEST,
                        risk = 0.40f,
                        vindictiveness = 0.15f,
                        predictability = 0.55f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Loan liya, default kiya, foreign chala gaya — paisa."),
                            BarkEvent.BLUFF_DECLARE to listOf("Mera CA sambhal lega. Tum file dekho mat."),
                            BarkEvent.CHALLENGED to listOf("Audit? Woh company toh kal band ho gayi."),
                            BarkEvent.WIN_GAME to listOf("Profit booked. Offshore. Tata bye-bye."),
                            BarkEvent.ELIMINATED to listOf("Liquidation. Personal guarantee mat dilao."),
                        ),
                    ),
            ),
            // ── 8. Madam Sarpanch ─────────────────────────────────────────────────
            BotPersona(
                id = "madam_sarpanch",
                name = "Madam Sarpanch",
                title = "Hukum Chalta Hai",
                archetype = "Aggressor",
                seatColorArgb = 0xFF0072B2L, // Neta blue — distinct monogram from Vachan
                monogram = "MS",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.45f,
                        challengeAggression = 0.50f,
                        economicAggression = 0.80f,
                        targetingBias = TargetingBias.LEADER,
                        risk = 0.60f,
                        vindictiveness = 0.40f,
                        predictability = 0.55f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Gaon ka vikaas... mere aangan se shuru."),
                            BarkEvent.BLUFF_DECLARE to listOf("Main toh sirf naam ki sarpanch hoon, beta."),
                            BarkEvent.CHALLENGED to listOf("Panchayat mein laao baat. Wahin nipta denge."),
                            BarkEvent.WIN_GAME to listOf("Gaddi mahila ke haath. Pati ke through."),
                            BarkEvent.ELIMINATED to listOf("Agla election? Devar ladega. Seat apni hi hai."),
                        ),
                    ),
            ),
            // ── 9. Dalla Tiwari ───────────────────────────────────────────────────
            BotPersona(
                id = "dalla_tiwari",
                name = "Dalla Tiwari",
                title = "The Broker",
                archetype = "Schemer/Deceiver",
                seatColorArgb = 0xFFE69F00L, // Jugaadu orange — distinct monogram from Chhotu
                monogram = "DT",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.70f,
                        challengeAggression = 0.35f,
                        economicAggression = 0.45f,
                        targetingBias = TargetingBias.RANDOM,
                        risk = 0.65f,
                        vindictiveness = 0.10f,
                        predictability = 0.30f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Commission fix hai. Dono taraf se. Standard."),
                            BarkEvent.BLUFF_DECLARE to listOf("Main toh bas introduce karaata hoon, bhai."),
                            BarkEvent.CHALLENGED to listOf("Mera naam mat lo deal mein. Main beech mein nahi."),
                            BarkEvent.WIN_GAME to listOf("Sauda ho gaya. Cut sabka, gaddi meri."),
                            BarkEvent.ELIMINATED to listOf("Network rehta hai. Aadmi aate jaate hain."),
                        ),
                    ),
            ),
            // ── 10. Maaji Anna ────────────────────────────────────────────────────
            BotPersona(
                id = "maaji_anna",
                name = "Maaji Anna",
                title = "The Conscience",
                archetype = "Honest Plodder + Vindictive",
                seatColorArgb = 0xFFCC79A7L, // Vakil purple — distinct monogram from Loophole
                monogram = "MA",
                personality =
                    PersonalityProfile(
                        bluffRate = 0.15f,
                        challengeAggression = 0.60f,
                        economicAggression = 0.25f,
                        targetingBias = TargetingBias.VINDICTIVE,
                        risk = 0.30f,
                        vindictiveness = 0.80f,
                        predictability = 0.75f,
                    ),
                barks =
                    BarkSet(
                        mapOf(
                            BarkEvent.ACT to listOf("Yeh andolan hai, beta. Chhutta nahi maangta — donation."),
                            BarkEvent.BLUFF_DECLARE to listOf("Main toh nishkaam sevak hoon. Aaroop galat hai."),
                            BarkEvent.CHALLENGED to listOf("Mere upar shaq? Aaj se anshan shuru."),
                            BarkEvent.WIN_GAME to listOf("Satya ki jeet hui. Sponsor ka dhanyavaad."),
                            BarkEvent.ELIMINATED to listOf("Andolan zinda rahega. Main thoda thak gaya."),
                            BarkEvent.TAUNT to listOf("Yeh sab ganda khel hai."),
                        ),
                    ),
            ),
        )
}
