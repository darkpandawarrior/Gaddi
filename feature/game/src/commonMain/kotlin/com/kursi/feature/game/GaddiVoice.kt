package com.kursi.feature.game

import androidx.compose.runtime.staticCompositionLocalOf
import com.kursi.engine.Action
import com.kursi.engine.GameEvent
import com.kursi.engine.Role

/**
 * GaddiVoice — all satirical copy for the Gaddi game.
 * Supports HINGLISH (deadpan sarkari/filmi) and ENGLISH.
 * Source: gaddi-plan/docs/16b_voice_copy.md.
 */
class GaddiVoice(
    private val language: Language = Language.HINGLISH,
) {
    // ── Action bark ────────────────────────────────────────────────────────
    fun actionBark(action: Action): String =
        when (language) {
            Language.HINGLISH ->
                when (action) {
                    Action.Income -> "Dehaadi. Imaandaari ka ek rupaiya. Pyaara hai."
                    Action.ForeignAid -> "Videshi maal, desi jeb. Dono khush."
                    Action.Tax -> "Budget khud se ghotala nahi karega na."
                    Action.Exchange -> "Patte phir se baant. Apne hi favour mein."
                    is Action.Steal -> "Chori nahi, processing fee hai. Receipt nahi milega."
                    is Action.Investigate -> "Sting operation. Patta dekhenge, exposé chhapega."
                    is Action.Assassinate -> "Ek file band. Hamesha ke liye. Number delete."
                    is Action.Coup -> "Cabinet reshuffle. Permanent waala."
                    Action.BailPe -> "Bail mil gayi. Paisa laga, parda utha, wapas chhupa."
                    Action.Sabotage -> "Bali Khel. Apna card phenko, khazaana lo. Sarkari jugaad."
                    is Action.Hawala -> "Hawala. Sikke haath-se-haath. Koi receipt nahi."
                    Action.Emergency -> "ADHYADESH! Rashtrapati raj. Sab ek saath giraao."
                }
            Language.ENGLISH ->
                when (action) {
                    Action.Income -> "Honest day's wage. Adorable."
                    Action.ForeignAid -> "Foreign money, local pockets. Win-win."
                    Action.Tax -> "The budget won't embezzle itself."
                    Action.Exchange -> "Reshuffle the deck. Yours, obviously."
                    is Action.Steal -> "It's not theft if there's a receipt."
                    is Action.Investigate -> "A little sting operation. Peek the card, print the exposé."
                    is Action.Assassinate -> "One file closed. Permanently."
                    is Action.Coup -> "Cabinet reshuffle, permanent edition."
                    Action.BailPe -> "Bail granted. Pay up. The card's back in hiding."
                    Action.Sabotage -> "Sacrifice play. Lose a card, pocket the coins. Very dignified."
                    is Action.Hawala -> "Under-the-table transfer. Coins change hands, no paperwork."
                    Action.Emergency -> "Emergency powers. Everyone loses a card. Very constitutional."
                }
        }

    // ── Action mechanic tag ────────────────────────────────────────────────
    fun actionMechanic(action: Action): String =
        when (action) {
            Action.Income -> "Income · +1 · unblockable"
            Action.ForeignAid -> "Foreign Aid · +2 · NETA blocks"
            Action.Tax -> "Tax · +3 · claims NETA"
            Action.Exchange -> "Exchange · claims JUGAADU"
            is Action.Steal -> "Steal +2 · claims BABU · BABU/JUGAADU block"
            is Action.Investigate -> "Investigate · claims PATRAKAAR · peek + force redraw · unblockable"
            is Action.Assassinate -> "Assassinate · pay 3 · claims BHAI · VAKIL blocks"
            is Action.Coup -> "Coup · pay 7 · unblockable/unstoppable"
            Action.BailPe -> "Bail Pe Bahar · pay 9 · restore one face-up influence · unblockable"
            Action.Sabotage -> "Bali Khel · sacrifice face-down influence · gain 3 coins"
            is Action.Hawala -> "Hawala · gift up to 5 coins direct · unblockable"
            Action.Emergency -> "Adhyadesh · spend all coins · force-lose on all opponents · unblockable"
        }

    // ── Role tagline ───────────────────────────────────────────────────────
    fun roleTagline(role: Role): String =
        when (language) {
            Language.HINGLISH ->
                when (role) {
                    Role.NETA -> "Public servant. Privately served."
                    Role.BHAI -> "Sab setting hai. Tu tension mat le."
                    Role.BABU -> "Aapka kaam ho jaayega. File aage badhi hai."
                    Role.JUGAADU -> "System nahi badalta. Hum system ko ghumate hain."
                    Role.VAKIL -> "Objection, milord. Sab kuch."
                    Role.PATRAKAAR -> "Sources ke mutaabik... sab kuch chhapega."
                }
            Language.ENGLISH ->
                when (role) {
                    Role.NETA -> "Public servant. Privately served."
                    Role.BHAI -> "All is arranged. Don't stress."
                    Role.BABU -> "Your work will be done. The file has moved forward."
                    Role.JUGAADU -> "The system doesn't change. We work the system."
                    Role.VAKIL -> "Objection, milord. About everything."
                    Role.PATRAKAAR -> "According to sources... everything goes to print."
                }
        }

    // ── Role blurb ────────────────────────────────────────────────────────
    fun roleBlurb(role: Role): String =
        when (role) {
            Role.NETA ->
                "Runs Ghotala (+3) — the budget is a buffet and he brought a big plate. Also blocks FDI, because foreign aid is " +
                    "his to misplace."
            Role.BHAI -> "Runs Supari (pay 3) — sends a rival's card to permanent retirement. No paperwork. No witnesses. No problem."
            Role.BABU ->
                "Runs Vasooli (steal 2) — extracts a processing fee from anyone, anytime. Also blocks Vasooli, because only he " +
                    "knows the right desk."
            Role.JUGAADU ->
                "Runs Setting (exchange) — swaps his cards with the deck till the hand looks good. " +
                    "Also blocks Vasooli, because you can't steal from a man with no fixed address."
            Role.VAKIL ->
                "Blocks Supari — files a stay order on your assassination. Doesn't earn coins; earns time, which is more " +
                    "expensive."
            Role.PATRAKAAR ->
                "Runs Jaanch — peeks at a rival's hidden card, then can spike it back into the deck for a fresh draw. " +
                    "No coins, no kills; just an exposé and a headache. Can't be blocked — you can't gag the press, only buy it."
        }

    // ── Persona bio ───────────────────────────────────────────────────────
    fun personaBio(personaId: String): String =
        when (language) {
            Language.HINGLISH ->
                when (personaId) {
                    "netaji_vachan" -> "Bees saal se gaddi pe. Aaj bhi 'system se lad raha hai.'"
                    "bhai_teja" -> "Awaaz kabhi nahi uthata. Uthane ki zaroorat hi nahi padti."
                    "babu_filewala" -> "Aapki file chal rahi hai. Dheere. Jaan-boojh ke."
                    "jugaadu_chhotu" -> "Na paisa, na degree, na problem. Bas jugaad."
                    "vakil_loophole" -> "Aaj tak koi case haara nahi. Jeeta bhi nahi."
                    "inspector_damaad" -> "Sahi khaandaan mein shaadi ki. Galat logon ki policing karta hai."
                    "seth_khokhawala" -> "Chalees companies ka maalik. Ek bhi exist nahi karti."
                    "madam_sarpanch" -> "Gaddi uske paas. Cheque pati sign karta hai."
                    "dalla_tiwari" -> "Ek aadmi ko jaanta hai. Woh aadmi ek aur aadmi ko."
                    "maaji_anna" -> "Bhrashtachaar se ladta hai. Usi ke paise pe."
                    else -> ""
                }
            Language.ENGLISH ->
                when (personaId) {
                    "netaji_vachan" -> "Twenty years in power, still 'fighting the system' — the one he runs."
                    "bhai_teja" -> "Never raises his voice. He has never once needed to."
                    "babu_filewala" -> "Your file is moving. Slowly. On purpose. For a fee."
                    "jugaadu_chhotu" -> "No money, no degree, no problem — just an endless supply of workarounds."
                    "vakil_loophole" -> "Hasn't lost a case in years. Hasn't won one either."
                    "inspector_damaad" -> "Married into the right family. Polices all the wrong people."
                    "seth_khokhawala" -> "Owns forty companies. Not one of them actually exists."
                    "madam_sarpanch" -> "She holds the seat; her husband holds the pen."
                    "dalla_tiwari" -> "Knows a guy. The guy knows a guy. The chain never ends."
                    "maaji_anna" -> "Fights corruption full-time. Funded by it, entirely."
                    else -> ""
                }
        }

    // ── Persona barks ─────────────────────────────────────────────────────
    fun personaBark(
        personaId: String,
        event: String,
    ): String =
        when (language) {
            Language.HINGLISH -> hinglishPersonaBark(personaId, event)
            Language.ENGLISH -> englishPersonaBark(personaId, event)
        }

    private fun hinglishPersonaBark(
        personaId: String,
        event: String,
    ): String =
        when (personaId) {
            "netaji_vachan" ->
                when (event) {
                    "act" -> "Janta ka paisa, janta ke liye... mere through."
                    "bluff" -> "Main toh sirf samaj sevak hoon. Trust me."
                    "challenged" -> "Mujh par ungli? Defamation case ready rakho."
                    "win" -> "Yeh gaddi nahi, janta ka pyaar hai."
                    "lose" -> "Main wapas aaunga. Election ke baad."
                    else -> "Yeh toh protocol hai."
                }
            "bhai_teja" ->
                when (event) {
                    "act" -> "Kaam ho gaya. Number delete kar do."
                    "bluff" -> "Main businessman hoon. Yeh sab galatfehmi hai."
                    "challenged" -> "Tune mujhe challenge kiya? Bahadur hai tu."
                    "win" -> "Bola tha na — sab setting hai."
                    "lose" -> "Galti ek hi baar hoti hai. Aaj meri thi."
                    else -> "Sab dekh raha hoon."
                }
            "babu_filewala" ->
                when (event) {
                    "act" -> "Processing fee laga hai. Receipt nahi milega."
                    "bluff" -> "Rule book mere paas hai. Aapke paas?"
                    "challenged" -> "Aapne galat counter pe complaint ki hai."
                    "win" -> "Saara kaam approved. Apne aap ko chhod ke."
                    "lose" -> "Transfer ho gaya. Dehradun. Theek hai."
                    else -> "File aage badh rahi hai."
                }
            "jugaadu_chhotu" ->
                when (event) {
                    "act" -> "Setting ho gaya bhai. Tension nahi lene ka."
                    "bluff" -> "Card? Kaunsa card? Mere paas toh kuch nahi."
                    "challenged" -> "Arre ruko ruko, ek minute, sun toh lo—"
                    "win" -> "Bina paise ke gaddi. Asli jugaad yeh hai."
                    "lose" -> "Koi baat nahi. Naya scene dhoondte hain."
                    else -> "Jugaad nikalte hain."
                }
            "vakil_loophole" ->
                when (event) {
                    "act" -> "Stay order. Tareekh pe tareekh, milord."
                    "bluff" -> "Constitution ki dhara 420... uh, padhi hai maine."
                    "challenged" -> "Yeh court ki avmanna hai. Note kijiye."
                    "win" -> "Verdict mere haq mein. Jaise hamesha."
                    "lose" -> "Appeal karunga. Upper court tak jaayenge."
                    else -> "Objection."
                }
            "inspector_damaad" ->
                when (event) {
                    "act" -> "Yeh case mere under hai. Aur tu bhi."
                    "bluff" -> "Wardi pe ungli mat utha. Galat hoga."
                    "challenged" -> "FIR likhna hai? Mere thane mein? Likh."
                    "win" -> "Law and order. Mera law, mera order."
                    "lose" -> "Suspend? Sasur ko phone lagao, abhi."
                    else -> "Investigation chalu hai."
                }
            "seth_khokhawala" ->
                when (event) {
                    "act" -> "Loan liya, default kiya, foreign chala gaya — paisa."
                    "bluff" -> "Mera CA sambhal lega. Tum file dekho mat."
                    "challenged" -> "Audit? Woh company toh kal band ho gayi."
                    "win" -> "Profit booked. Offshore. Tata bye-bye."
                    "lose" -> "Liquidation. Personal guarantee mat dilao."
                    else -> "Business hai, personal mat lo."
                }
            "madam_sarpanch" ->
                when (event) {
                    "act" -> "Gaon ka vikaas... mere aangan se shuru."
                    "bluff" -> "Main toh sirf naam ki sarpanch hoon, beta."
                    "challenged" -> "Panchayat mein laao baat. Wahin nipta denge."
                    "win" -> "Gaddi mahila ke haath. Pati ke through."
                    "lose" -> "Agla election? Devar ladega. Seat apni hi hai."
                    else -> "Mere area mein mera niyam."
                }
            "dalla_tiwari" ->
                when (event) {
                    "act" -> "Commission fix hai. Dono taraf se. Standard."
                    "bluff" -> "Main toh bas introduce karaata hoon, bhai."
                    "challenged" -> "Mera naam mat lo deal mein. Main beech mein nahi."
                    "win" -> "Sauda ho gaya. Cut sabka, gaddi meri."
                    "lose" -> "Network rehta hai. Aadmi aate jaate hain."
                    else -> "Deal pakki? Deal pakki."
                }
            "maaji_anna" ->
                when (event) {
                    "act" -> "Yeh andolan hai, beta. Chhutta nahi maangta — donation."
                    "bluff" -> "Main toh nishkaam sevak hoon. Aaroop galat hai."
                    "challenged" -> "Mere upar shaq? Aaj se anshan shuru."
                    "win" -> "Satya ki jeet hui. Sponsor ka dhanyavaad."
                    "lose" -> "Andolan zinda rahega. Main thoda thak gaya."
                    else -> "Sach ki jeet hoti hai."
                }
            else -> ""
        }

    private fun englishPersonaBark(
        personaId: String,
        event: String,
    ): String =
        when (personaId) {
            "netaji_vachan" ->
                when (event) {
                    "act" -> "The people's money, for the people... through me."
                    "bluff" -> "I am merely a public servant. Trust me."
                    "challenged" -> "Pointing fingers at me? Have a defamation case ready."
                    "win" -> "This chair is not power — it's the people's love."
                    "lose" -> "I'll be back. After the election."
                    else -> "This is just protocol."
                }
            "bhai_teja" ->
                when (event) {
                    "act" -> "Done. Delete the number."
                    "bluff" -> "I'm a businessman. This is all a misunderstanding."
                    "challenged" -> "You challenged me? Brave of you."
                    "win" -> "Told you — it's all arranged."
                    "lose" -> "A mistake only happens once. Today was mine."
                    else -> "I'm watching everything."
                }
            "babu_filewala" ->
                when (event) {
                    "act" -> "Processing fee applied. No receipt."
                    "bluff" -> "I have the rulebook. Do you?"
                    "challenged" -> "You've filed your complaint at the wrong counter."
                    "win" -> "Everything approved. Except yourself."
                    "lose" -> "Transferred. To the back office. That's fine."
                    else -> "The file is moving forward."
                }
            "jugaadu_chhotu" ->
                when (event) {
                    "act" -> "All sorted, relax."
                    "bluff" -> "Cards? What cards? I have nothing."
                    "challenged" -> "Wait wait wait, just a second, hear me out—"
                    "win" -> "The chair with no money. That's the real hack."
                    "lose" -> "No worries. On to the next scene."
                    else -> "We'll find a workaround."
                }
            "vakil_loophole" ->
                when (event) {
                    "act" -> "Stay order. Date after date, milord."
                    "bluff" -> "Article 420 of the constitution... I've, uh, read it."
                    "challenged" -> "This is contempt of court. Note it down."
                    "win" -> "Verdict in my favour. As always."
                    "lose" -> "I'll appeal. All the way to the top."
                    else -> "Objection."
                }
            "inspector_damaad" ->
                when (event) {
                    "act" -> "This case is under me. So are you."
                    "bluff" -> "Don't point at the uniform. That would be a mistake."
                    "challenged" -> "Want to file an FIR? In my station? Go ahead."
                    "win" -> "Law and order. My law, my order."
                    "lose" -> "Suspended? Call my father-in-law. Now."
                    else -> "Investigation is ongoing."
                }
            "seth_khokhawala" ->
                when (event) {
                    "act" -> "Took the loan, defaulted, went abroad — classic."
                    "bluff" -> "My CA will handle it. Don't look at the files."
                    "challenged" -> "Audit? That company shut down yesterday."
                    "win" -> "Profit booked. Offshore. Goodbye."
                    "lose" -> "Liquidation. Don't ask me for a personal guarantee."
                    else -> "It's business. Don't take it personally."
                }
            "madam_sarpanch" ->
                when (event) {
                    "act" -> "Village development... starting from my backyard."
                    "bluff" -> "I'm only the sarpanch in name, dear."
                    "challenged" -> "Bring this to the panchayat. We'll settle it there."
                    "win" -> "The seat goes to a woman. Via her husband."
                    "lose" -> "Next election? My brother-in-law will run. Same seat."
                    else -> "My area, my rules."
                }
            "dalla_tiwari" ->
                when (event) {
                    "act" -> "Commission fixed. Both sides. Standard."
                    "bluff" -> "I only make introductions. That's all."
                    "challenged" -> "Leave my name out of the deal. I'm not in the middle."
                    "win" -> "Deal done. Everyone gets a cut. Chair's mine."
                    "lose" -> "The network stays. People come and go."
                    else -> "Deal confirmed? Deal confirmed."
                }
            "maaji_anna" ->
                when (event) {
                    "act" -> "This is a movement, child. I don't ask for leave — I ask for donations."
                    "bluff" -> "I am a selfless servant. The accusation is false."
                    "challenged" -> "Doubt me? Fast starts today."
                    "win" -> "Truth prevailed. Thanks to our sponsors."
                    "lose" -> "The movement lives on. I'm just a little tired."
                    else -> "Truth always wins."
                }
            else -> ""
        }

    /** How the human player is named at the table — "Aap" / "You". Never a raw "P{seat}". */
    val selfName: String get() =
        when (language) {
            Language.HINGLISH -> "Aap"
            Language.ENGLISH -> "You"
        }

    // ── M6e TEAM KHEL / SPECTATOR ─────────────────────────────────────────────

    /** Short faction badge for [teamId] (0/1) shown on the in-game plates. */
    fun teamBadge(teamId: Int): String =
        when (language) {
            Language.HINGLISH -> if (teamId == 0) "SAT" else "VIP"
            Language.ENGLISH -> if (teamId == 0) "PTY" else "OPP"
        }

    /** In-game banner for a watch-only TAMASHA / demo game. */
    val spectatorBanner: String get() =
        when (language) {
            Language.HINGLISH -> "TAMASHA — sirf dekhne ke liye"
            Language.ENGLISH -> "DEMO — watch only"
        }

    // ── Status spine ──────────────────────────────────────────────────────
    val yourTurn: String get() =
        when (language) {
            Language.HINGLISH -> "Aapki baari, sarkar."
            Language.ENGLISH -> "Your turn, sir."
        }
    val yourTurnAlt1: String get() =
        when (language) {
            Language.HINGLISH -> "Chaal aapki hai."
            Language.ENGLISH -> "Make your move."
        }
    val yourTurnAlt2: String get() =
        when (language) {
            Language.HINGLISH -> "Karein? Sab dekh rahe hain."
            Language.ENGLISH -> "What'll it be? Everyone's watching."
        }
    val waiting: String get() =
        when (language) {
            Language.HINGLISH -> "File process ho rahi hai..."
            Language.ENGLISH -> "Processing..."
        }
    val waitingAlt: String get() =
        when (language) {
            Language.HINGLISH -> "Stamp lag raha hai..."
            Language.ENGLISH -> "Stamping..."
        }
    val forcedCoup: String get() =
        when (language) {
            Language.HINGLISH -> "Itne paise? Ab toh gaddi giraani padegi."
            Language.ENGLISH -> "That much coin? Time to topple the chair."
        }
    val forcedCoupSub: String get() =
        when (language) {
            Language.HINGLISH -> "10+ coins. Sirf Khela allowed."
            Language.ENGLISH -> "10+ coins. Only Coup allowed."
        }
    val bluffCaught: String get() =
        when (language) {
            Language.HINGLISH -> "Bluff pakda gaya. Card gaya."
            Language.ENGLISH -> "Bluff exposed. Card lost."
        }
    val bluffCaughtAlt: String get() = "Clean bowled. Middle stump."
    val bluffSurvived: String get() =
        when (language) {
            Language.HINGLISH -> "Sach nikla. Challenger ka card gaya."
            Language.ENGLISH -> "Truth revealed. Challenger loses a card."
        }
    val bluffSurvivedAlt: String get() =
        when (language) {
            Language.HINGLISH -> "Galat panga. Bhugto ab."
            Language.ENGLISH -> "Wrong move. Pay up."
        }
    val blockOk: String get() =
        when (language) {
            Language.HINGLISH -> "Block lag gaya. Kaam nahi hua."
            Language.ENGLISH -> "Block successful. Action failed."
        }
    val blockFailed: String get() =
        when (language) {
            Language.HINGLISH -> "Jhootha block. Khul gaya."
            Language.ENGLISH -> "Block exposed. False claim."
        }

    // ── Assistant / auto-mode (M5) ─────────────────────────────────────────
    val playBestMove: String get() =
        when (language) {
            Language.HINGLISH -> "BEST CHAAL"
            Language.ENGLISH -> "BEST MOVE"
        }

    // ── Pass-and-play handoff guard (M5) ──────────────────────────────────
    val handoffTitle: String get() =
        when (language) {
            Language.HINGLISH -> "PHONE AAGE BADHAO"
            Language.ENGLISH -> "PASS THE DEVICE"
        }

    /** "Hand the device to <seat>". The caller supplies the next seat's display name. */
    fun handoffPrompt(nextName: String): String =
        when (language) {
            Language.HINGLISH -> "$nextName ko phone do. Baaki sab nazrein hata lein."
            Language.ENGLISH -> "Hand the device to $nextName. Everyone else, look away."
        }

    val handoffReveal: String get() =
        when (language) {
            Language.HINGLISH -> "Taiyaar? Tap karke apne patte dekho."
            Language.ENGLISH -> "Ready? Tap to see your cards."
        }
    val handoffSecrecy: String get() =
        when (language) {
            Language.HINGLISH -> "Sirf aapke patte. Kisi aur ko mat dikhao."
            Language.ENGLISH -> "Your cards only. Don't let others peek."
        }

    // ── Beat gate (spec §5) — FOCUS/GUIDED tap-to-continue affordance ──────
    val continuePrompt: String get() =
        when (language) {
            Language.HINGLISH -> "Aage badhne ke liye tap karein"
            Language.ENGLISH -> "Tap to continue"
        }

    val youWin: String get() =
        when (language) {
            Language.HINGLISH -> "Gaddi aapki. Taj pehno, sarkar."
            Language.ENGLISH -> "The chair is yours. Wear the crown."
        }
    val gameEndSub: String get() =
        when (language) {
            Language.HINGLISH -> "Khel khatam. Paisa hazam."
            Language.ENGLISH -> "Game over. Coins gone."
        }
    val loading: String get() =
        when (language) {
            Language.HINGLISH -> "Gaddi garam ki ja rahi hai..."
            Language.ENGLISH -> "Warming up the chair..."
        }
    val logEmpty: String get() =
        when (language) {
            Language.HINGLISH -> "Abhi tak sab shareef hain. Wait karo."
            Language.ENGLISH -> "All peaceful so far. Wait."
        }
    val loseInfluence: String get() =
        when (language) {
            Language.HINGLISH -> "Ek card reveal karo. Nange pair mat bano."
            Language.ENGLISH -> "Reveal a card. Face the music."
        }
    val exchange: String get() =
        when (language) {
            Language.HINGLISH -> "Naya haath chuno. Teri marzi."
            Language.ENGLISH -> "Choose a new hand. Your call."
        }

    fun opponentActing(personaName: String): String =
        when (language) {
            Language.HINGLISH -> "$personaName kuch chala raha hai..."
            Language.ENGLISH -> "$personaName is making a move..."
        }

    fun opponentActingAlt(personaName: String): String =
        when (language) {
            Language.HINGLISH -> "$personaName soch raha hai. Achha sign nahi."
            Language.ENGLISH -> "$personaName is thinking. Not a good sign."
        }

    fun opponentWins(personaName: String): String =
        when (language) {
            Language.HINGLISH -> "$personaName ne gaddi pakad li. Baaki sab dhobi ka kutta."
            Language.ENGLISH -> "$personaName has taken the chair. The rest are irrelevant."
        }

    fun cardLost(personaName: String): String =
        when (language) {
            Language.HINGLISH -> "$personaName ne ek card khoya. Ab nange pair."
            Language.ENGLISH -> "$personaName lost a card. Now more vulnerable."
        }

    fun playerOut(personaName: String): String =
        when (language) {
            Language.HINGLISH -> "$personaName out. Gaddi se door, hamesha ke liye."
            Language.ENGLISH -> "$personaName is out. Gone for good."
        }

    fun playerOutAlt(personaName: String): String =
        when (language) {
            Language.HINGLISH -> "$personaName ka wicket gir gaya."
            Language.ENGLISH -> "$personaName has fallen."
        }

    // ── Log entry verbs ────────────────────────────────────────────────────
    fun logActorVerb(
        actorName: String,
        action: Action,
    ): String =
        when (language) {
            Language.HINGLISH ->
                when (action) {
                    Action.Income -> "$actorName ne Dehaadi li. +1."
                    Action.ForeignAid -> "$actorName ne FDI maanga. +2."
                    Action.Tax -> "$actorName ne Ghotala kiya. +3."
                    Action.Exchange -> "$actorName ne Setting ki."
                    is Action.Steal -> "$actorName ne Vasooli ki."
                    is Action.Investigate -> "$actorName ne Jaanch ki."
                    is Action.Assassinate -> "$actorName ne Supari di."
                    is Action.Coup -> "$actorName ne Khela kiya."
                    Action.BailPe -> "$actorName ne Bail Pe Bahar kheela."
                    Action.Sabotage -> "$actorName ne Bali Khel kheela."
                    is Action.Hawala -> "$actorName ne Hawala bheja."
                    Action.Emergency -> "$actorName ne ADHYADESH laga diya."
                }
            Language.ENGLISH ->
                when (action) {
                    Action.Income -> "$actorName took Income. +1."
                    Action.ForeignAid -> "$actorName took Foreign Aid. +2."
                    Action.Tax -> "$actorName ran Tax. +3."
                    Action.Exchange -> "$actorName ran Exchange."
                    is Action.Steal -> "$actorName ran Steal."
                    is Action.Investigate -> "$actorName ran Investigate."
                    is Action.Assassinate -> "$actorName ran Assassinate."
                    is Action.Coup -> "$actorName ran Coup."
                    Action.BailPe -> "$actorName played Bail Pe Bahar."
                    Action.Sabotage -> "$actorName played Bali Khel."
                    is Action.Hawala -> "$actorName sent Hawala."
                    Action.Emergency -> "$actorName declared Emergency."
                }
        }

    fun logActorVerbWithTarget(
        actorName: String,
        action: Action,
        targetName: String,
    ): String =
        when (language) {
            Language.HINGLISH ->
                when (action) {
                    is Action.Steal -> "$actorName ne Vasooli ki. $targetName pe."
                    is Action.Investigate -> "$actorName ne $targetName ki Jaanch ki."
                    is Action.Assassinate -> "$actorName ne Supari di. $targetName ko."
                    is Action.Coup -> "$actorName ne Khela kiya. $targetName ko."
                    else -> logActorVerb(actorName, action)
                }
            Language.ENGLISH ->
                when (action) {
                    is Action.Steal -> "$actorName stole from $targetName."
                    is Action.Investigate -> "$actorName investigated $targetName."
                    is Action.Assassinate -> "$actorName assassinated $targetName."
                    is Action.Coup -> "$actorName couped $targetName."
                    else -> logActorVerb(actorName, action)
                }
        }

    fun logBluffCaught(actorName: String): String =
        when (language) {
            Language.HINGLISH -> "$actorName ka bluff pakda gaya. Card gaya."
            Language.ENGLISH -> "$actorName's bluff was caught. Card lost."
        }

    fun logBluffTrue(actorName: String): String =
        when (language) {
            Language.HINGLISH -> "$actorName sach tha. Challenger ka card gaya."
            Language.ENGLISH -> "$actorName was truthful. Challenger loses a card."
        }

    fun logBlocked(
        blockerName: String,
        roleName: String,
    ): String =
        when (language) {
            Language.HINGLISH -> "$blockerName ne block kiya as $roleName."
            Language.ENGLISH -> "$blockerName blocked as $roleName."
        }

    fun logInfluenceLost(
        playerName: String,
        roleName: String,
    ): String =
        when (language) {
            Language.HINGLISH -> "$playerName ne $roleName khoya."
            Language.ENGLISH -> "$playerName lost $roleName."
        }

    fun logEliminated(playerName: String): String =
        when (language) {
            Language.HINGLISH -> "$playerName OUT. Hamesha ke liye."
            Language.ENGLISH -> "$playerName OUT."
        }

    fun logRoundDivider(
        turn: Int,
        actorName: String,
    ): String = "Turn $turn · $actorName"

    fun logRoundSummary(n: Int): String =
        when (language) {
            Language.HINGLISH -> "Round $n khatam."
            Language.ENGLISH -> "Round $n complete."
        }

    // ── Reaction dock ─────────────────────────────────────────────────────
    val challengePrompt: String get() =
        when (language) {
            Language.HINGLISH -> "Bluff lagta hai?"
            Language.ENGLISH -> "Smells like a bluff?"
        }
    val challengeBtn: String get() = "Challenge"
    val passChallenge: String get() =
        when (language) {
            Language.HINGLISH -> "Rehne do"
            Language.ENGLISH -> "Let it go"
        }
    val blockPrompt: String get() =
        when (language) {
            Language.HINGLISH -> "Block karein? Ya jaane dein?"
            Language.ENGLISH -> "Block? Or let it pass?"
        }
    val passBlock: String get() =
        when (language) {
            Language.HINGLISH -> "Jaane do"
            Language.ENGLISH -> "Let it go"
        }

    fun blockAsRole(roleName: String): String = "Block ($roleName)"

    // ── Tooltip copy ──────────────────────────────────────────────────────
    val cantAffordSupari: String get() =
        when (language) {
            Language.HINGLISH -> "Supari sasti nahi hoti."
            Language.ENGLISH -> "Assassination isn't cheap."
        }
    val cantAffordKhela: String get() =
        when (language) {
            Language.HINGLISH -> "Gaddi giraane ke paise toh laao."
            Language.ENGLISH -> "Bring the coin to topple the chair."
        }
    val khelaForced: String get() =
        when (language) {
            Language.HINGLISH -> "Khela (Majboori)"
            Language.ENGLISH -> "Coup (Forced)"
        }

    // ── Reaction dock hint lines ──────────────────────────────────────────
    val reactionHintChallenge: String get() =
        when (language) {
            Language.HINGLISH -> "Bluff pakda gaya? Card gaya."
            Language.ENGLISH -> "Catch the bluff? They lose a card."
        }
    val reactionHintBlock: String get() =
        when (language) {
            Language.HINGLISH -> "Block kiya toh action ruka. Challenge kiya toh card khatre mein."
            Language.ENGLISH -> "Block stops the action. Challenge puts a card at risk."
        }
    val reactionHintChallengeBlock: String get() =
        when (language) {
            Language.HINGLISH -> "Galat block challenge kiya? Unka card gaya."
            Language.ENGLISH -> "Wrong block challenged? They lose a card."
        }

    // ── Log line: challenge ───────────────────────────────────────────────
    fun logChallenged(
        challengerName: String,
        targetName: String,
    ): String =
        when (language) {
            Language.HINGLISH -> "$challengerName ne $targetName ko challenge kiya."
            Language.ENGLISH -> "$challengerName challenged $targetName."
        }

    // ── Log panel header ──────────────────────────────────────────────────
    val logPanelHeader: String get() =
        when (language) {
            Language.HINGLISH -> "ROZNAMCHA"
            Language.ENGLISH -> "GAME LOG"
        }

    /** Teleprinter sub-line under the masthead — the sarkari office stamp. */
    val logTeleprinterTag: String get() =
        when (language) {
            Language.HINGLISH -> "SARKARI DAFTAR · LIVE FEED"
            Language.ENGLISH -> "OFFICE OF RECORD · LIVE FEED"
        }

    /** Sticky current-turn header label. */
    fun logCurrentTurn(
        turn: Int,
        actorName: String,
    ): String =
        when (language) {
            Language.HINGLISH -> "CHAALU PARCHI · Turn $turn · $actorName"
            Language.ENGLISH -> "ACTIVE FILE · Turn $turn · $actorName"
        }

    /** Jump-to-latest control label. */
    val logJumpToLatest: String get() =
        when (language) {
            Language.HINGLISH -> "TAAZA TAK ↓"
            Language.ENGLISH -> "JUMP TO LATEST ↓"
        }

    /** Per-turn group header in the body (grouped-by-turn dividers). */
    fun logTurnGroup(
        turn: Int,
        actorName: String,
    ): String =
        when (language) {
            Language.HINGLISH -> "PARCHI #$turn · $actorName"
            Language.ENGLISH -> "FILE #$turn · $actorName"
        }

    // ── Persona rivalry / reputation (second dossier line) ─────────────────
    // A deadpan one-liner about who this persona feuds with or what their
    // standing reputation is — drips satire without naming live seats.
    fun personaRivalry(personaId: String): String =
        when (language) {
            Language.HINGLISH ->
                when (personaId) {
                    "netaji_vachan" -> "Maaji Anna se public mein ladta hai, private mein chai peeta hai."
                    "bhai_teja" -> "Inspector Damaad ka 'dost'. Dono ek doosre ko phone nahi karte — sab cash mein."
                    "babu_filewala" -> "Har file pe uska stamp. Har deri pe uska haath."
                    "jugaadu_chhotu" -> "Seth Khokhawala ke liye kaam karta hai. Seth ko pata bhi nahi."
                    "vakil_loophole" -> "Sabka case ladta hai. Kisi ka jeet ki guarantee nahi."
                    "inspector_damaad" -> "Bhai Teja ko pakad nahi sakta — sasur ne mana kiya hai."
                    "seth_khokhawala" -> "Vakil Loophole uska personal lawyer. Bill kabhi nahi chukta."
                    "madam_sarpanch" -> "Gaddi uski, dastkhat pati ke. Gaon dono se darta hai."
                    "dalla_tiwari" -> "Sabko sabse milata hai. Cut har taraf se leta hai."
                    "maaji_anna" -> "Netaji ka purana saathi. Ab uska sabse bada 'aalochak'."
                    else -> ""
                }
            Language.ENGLISH ->
                when (personaId) {
                    "netaji_vachan" -> "Feuds with Maaji Anna in public, shares chai with him in private."
                    "bhai_teja" -> "Inspector Damaad's 'friend'. They never call each other — it's all cash."
                    "babu_filewala" -> "His stamp on every file. His hand behind every delay."
                    "jugaadu_chhotu" -> "Runs errands for Seth Khokhawala. Seth doesn't even know it."
                    "vakil_loophole" -> "Argues everyone's case. Guarantees no one a win."
                    "inspector_damaad" -> "Can't touch Bhai Teja — his father-in-law forbade it."
                    "seth_khokhawala" -> "Vakil Loophole is on retainer. The bill is never settled."
                    "madam_sarpanch" -> "She holds the seat, her husband signs. The village fears both."
                    "dalla_tiwari" -> "Introduces everyone to everyone. Takes a cut from every side."
                    "maaji_anna" -> "Netaji's old comrade. Now his loudest 'critic'."
                    else -> ""
                }
        }

    // ── Centre claim / table-heart inspect copy ────────────────────────────
    fun claimDetailTitle(
        actorName: String,
        roleName: String,
    ): String =
        when (language) {
            Language.HINGLISH -> "$actorName ka daava: $roleName"
            Language.ENGLISH -> "$actorName claims $roleName"
        }

    fun claimDetailWhisper(role: Role): String =
        when (language) {
            Language.HINGLISH ->
                when (role) {
                    Role.NETA -> "Saboot? Saboot toh symbol hai, sahab."
                    Role.BHAI -> "Daava bada hai. Dum hai ya nahi, dekhte hain."
                    Role.BABU -> "Stamp laga diya. Ab challenge karo to dekhein."
                    Role.JUGAADU -> "Card hai bhi ya jugaad hai — koi nahi jaanta."
                    Role.VAKIL -> "Objection ka daava. Court mein saabit karo."
                    Role.PATRAKAAR -> "Press card dikha raha hai. Asli hai ya photostat — challenge karo."
                }
            Language.ENGLISH ->
                when (role) {
                    Role.NETA -> "Proof? Proof is just a symbol, sir."
                    Role.BHAI -> "Big claim. Whether there's muscle behind it — we'll see."
                    Role.BABU -> "Stamp's down. Challenge it if you dare."
                    Role.JUGAADU -> "Is the card real or just a workaround — nobody knows."
                    Role.VAKIL -> "Claims an objection. Prove it in court."
                    Role.PATRAKAAR -> "Flashing a press card. Genuine or a photocopy — challenge it."
                }
        }

    val tableHeartTitle: String get() =
        when (language) {
            Language.HINGLISH -> "Mez ka Hisaab"
            Language.ENGLISH -> "The Table, So Far"
        }
    val tableHeartWhisper: String get() =
        when (language) {
            Language.HINGLISH -> "Deck mein paap, treasury mein paisa. Baaki sab daava."
            Language.ENGLISH -> "Sins in the deck, coins in the treasury. The rest is all claims."
        }

    // ── Roznamcha event narration (deadpan one-liner per event) ────────────
    // Shown when a log row is long-pressed: a richer, satirical retelling of the
    // event than the terse log line. Drips License-Raj cynicism.
    fun logNarration(
        event: GameEvent,
        actorName: String,
        secondary: String? = null,
    ): String =
        when (language) {
            Language.HINGLISH -> hinglishNarration(event, actorName, secondary)
            Language.ENGLISH -> englishNarration(event, actorName, secondary)
        }

    private fun hinglishNarration(
        event: GameEvent,
        actor: String,
        other: String?,
    ): String =
        when (event) {
            is GameEvent.ActionDeclared -> "$actor ne ${actionDesi(
                event.action,
            )} ka elaan kiya. Table chup ho gaya — koi maanega, koi taadega."
            is GameEvent.ActionResolved -> "$actor ka kaam ho gaya. File band, paisa hazam, sab khush (ya majboor)."
            is GameEvent.ActionNegated -> "$actor ka move table ne kha liya. Stamp laga, par approval nahi mili."
            is GameEvent.Challenged -> "${other ?: "Kisi"} ne $actor ko ghoor ke kaha — 'Saboot dikhao.' Ab patte khulenge."
            is GameEvent.ChallengeRevealed ->
                if (event.hadRole) {
                    "$actor ne patta palta — sach nikla. Jisne challenge kiya, woh ab ek card kam."
                } else {
                    "$actor ka bluff rangey haath pakda gaya. Daava jhootha, card gaya."
                }
            is GameEvent.Blocked -> "$actor beech mein kood gaya — '$other? Yeh nahi chalega.' Block laga diya."
            is GameEvent.InfluenceLost -> "$actor ko ek pehchaan kurbaan karni padi. Raaz ab sabke saamne."
            is GameEvent.PlayerEliminated -> "$actor ka aakhri card bhi gaya. Gaddi se hamesha ke liye door. RIP."
            is GameEvent.CoinsTransferred -> "$actor ki jeb se ${other ?: "kisi"} ki jeb mein — 'processing fee' kehte hain ise."
            is GameEvent.Exchanged -> "$actor ne deck se setting ki. Naye patte, wahi purana khel."
            is GameEvent.Investigated ->
                "$actor ne ${other ?: "kisi"} ka ek patta chupke se dekh liya — sting operation. Note kar liya " +
                    "gaya."
            is GameEvent.InvestigateRedraw ->
                "Exposé chhapa — ${other ?: "us"} ka patta deck mein wapas, naya uthana pada. Reputation " +
                    "reset."
            is GameEvent.GameEnded -> "$actor ne gaddi pakad li. Baaki sab tamashbeen, opinion ke saath."
            else -> "$actor ne kuch kiya. Roznamcha mein darj, samajh se pare."
        }

    private fun englishNarration(
        event: GameEvent,
        actor: String,
        other: String?,
    ): String =
        when (event) {
            is GameEvent.ActionDeclared -> "$actor announced ${actionDesi(
                event.action,
            )}. The table went quiet — some will believe it, some are already counting cards."
            is GameEvent.ActionResolved -> "$actor got it done. File closed, coins digested, everyone pleased (or resigned)."
            is GameEvent.ActionNegated -> "The table swallowed $actor's move. Stamped, yes — approved, no."
            is GameEvent.Challenged -> "${other ?: "Someone"} stared $actor down and said: 'Show your proof.' The cards are about to turn."
            is GameEvent.ChallengeRevealed ->
                if (event.hadRole) {
                    "$actor flipped the card — it was true. Whoever challenged is now one card lighter."
                } else {
                    "$actor's bluff was caught red-handed. The claim was a lie, and the card is gone."
                }
            is GameEvent.Blocked -> "$actor jumped in — '$other? That won't fly here.' The block lands."
            is GameEvent.InfluenceLost -> "$actor had to sacrifice an identity. The secret is on the table now."
            is GameEvent.PlayerEliminated -> "$actor's last card is gone. Banished from the chair forever. RIP."
            is GameEvent.CoinsTransferred -> "Out of $actor's pocket, into ${other ?: "someone"}'s — they call it a 'processing fee'."
            is GameEvent.Exchanged -> "$actor cut a deal with the deck. New cards, same old game."
            is GameEvent.Investigated -> "$actor quietly peeked at one of ${other ?: "someone"}'s cards — a sting operation. Duly noted."
            is GameEvent.InvestigateRedraw ->
                "The exposé ran — ${other ?: "their"} card went back into the deck and they had to draw " +
                    "afresh. Reputation reset."
            is GameEvent.GameEnded -> "$actor seized the chair. Everyone else is now an audience with opinions."
            else -> "$actor did something. Logged in the Roznamcha, beyond comprehension."
        }

    private fun actionDesi(action: Action): String =
        when (action) {
            Action.Income -> "Dehaadi"
            Action.ForeignAid -> "FDI"
            Action.Tax -> "Ghotala"
            Action.Exchange -> "Setting"
            is Action.Steal -> "Vasooli"
            is Action.Investigate -> "Jaanch"
            is Action.Assassinate -> "Supari"
            is Action.Coup -> "Khela"
            Action.BailPe -> "Bail Pe Bahar"
            Action.Sabotage -> "Bali Khel"
            is Action.Hawala -> "Hawala"
            Action.Emergency -> "Adhyadesh"
        }

    // ── Phase hint / subtitle band ─────────────────────────────────────────
    // The HINT band beneath the in-game banner. Routed through the voice so it
    // switches with language (Tenet 6 — true Hinglish barks), instead of being
    // an English duplicate baked into the presentation layer.
    fun phaseHint(hint: PhaseHint): String =
        when (language) {
            Language.HINGLISH ->
                when (hint) {
                    PhaseHint.CoinCapKhela -> "10+ sikke. Ab sirf KHELA chalega."
                    PhaseHint.PickAction -> "Aapki baari, sarkar. Kisi bhi action ko der tak dabaaye — catch padh lijiye."
                    PhaseHint.PickTarget -> "Jagmagaate hue vipakshi ko chunne ke liye tap kijiye."
                    PhaseHint.Confirm -> "Elaan kijiye ya radd. Ek baar elaan hua, toh table par hai."
                    is PhaseHint.ChallengeAction -> "${hint.actor} ne ${hint.action} ka daava kiya. Challenge karein ya jaane dein?"
                    PhaseHint.Block -> "Kisi ne aap par chaal chali. Block karein ya jaane dein?"
                    is PhaseHint.ChallengeBlock -> "${hint.actor} block kar raha hai. Block ko challenge karein?"
                    is PhaseHint.LoseInfluence -> "${hint.cause} Ek card kurbaan kijiye."
                    PhaseHint.Exchange -> "Kaunse card rakhne hain chunein. Deck mein aapke liye do extra hain."
                    PhaseHint.InvestigatePeek -> "Patta dekh liya. Deck mein wapas bhejna hai (naya patta) ya rehne dena hai?"
                    is PhaseHint.Thinking -> "${hint.actor ?: "Koi"} soch raha hai..."
                    PhaseHint.GameOver -> "Khel khatam. Jo gaddi le gaya, uski jai ho."
                }
            Language.ENGLISH ->
                when (hint) {
                    PhaseHint.CoinCapKhela -> "10+ coins. Only KHELA allowed."
                    PhaseHint.PickAction -> "Your move, boss. Long-press any action to read the catch."
                    PhaseHint.PickTarget -> "Tap a glowing opponent to target them."
                    PhaseHint.Confirm -> "Declare or cancel. Once declared, it's on the table."
                    is PhaseHint.ChallengeAction -> "${hint.actor} claims ${hint.action}. Challenge or let it pass?"
                    PhaseHint.Block -> "Someone moved against you. Block or pass?"
                    is PhaseHint.ChallengeBlock -> "${hint.actor} is blocking. Challenge the block?"
                    is PhaseHint.LoseInfluence -> "${hint.cause} Choose a card to give up."
                    PhaseHint.Exchange -> "Pick which cards to keep. The deck has two extras for you."
                    PhaseHint.InvestigatePeek -> "You've seen the card. Spike it back into the deck (forces a redraw) or leave it?"
                    is PhaseHint.Thinking -> "${hint.actor ?: "Someone"} is thinking..."
                    PhaseHint.GameOver -> "Game over. Long live whoever grabbed the Gaddi."
                }
        }

    // ── Centre-screen action prompt ────────────────────────────────────────────
    // The concrete "tap-here-to-do-X" instruction rendered in the centre of the
    // board during an interactive phase (pick a target, give up a card, exchange).
    // Distinct from [phaseHint] (the subtitle band): these are imperative UI
    // directions, not flavour. Routed through the voice so they switch with
    // language (Tenet 6 — true Hinglish), instead of being baked-in English.
    fun centerPrompt(prompt: CenterPrompt): String =
        when (language) {
            Language.HINGLISH ->
                when (prompt) {
                    CenterPrompt.PickTarget -> "Chamakte hue vipakshi chip ko tap karke apna nishaana chunein."
                    CenterPrompt.LoseInfluence -> "Apne ek patte ko tap karke reveal kijiye."
                    CenterPrompt.Exchange -> "Kaunse patte rakhne hain — tap karke chunein:"
                }
            Language.ENGLISH ->
                when (prompt) {
                    CenterPrompt.PickTarget -> "Tap a glowing opponent chip to select your target"
                    CenterPrompt.LoseInfluence -> "Tap one of your cards to reveal it."
                    CenterPrompt.Exchange -> "Choose which cards to keep:"
                }
        }

    // ═══════════════════════════════════════════════════════════════════════════
    // CLARITY SYSTEM (Tenet 1) — always shown, NOT gated under the coach. This is
    // pure COMPREHENSION: "what does this option do, and at what cost?" and
    // "what just happened?" — never advice/odds.
    // ═══════════════════════════════════════════════════════════════════════════

    // ── WHAT-NOW: per-action consequence + cost (one plain line) ───────────────
    // Describes, in plain language, what the action DOES and what it COSTS/RISKS —
    // so even a cold player reads the move before committing. No odds, no opinion.
    fun actionConsequence(action: Action): String =
        when (language) {
            Language.HINGLISH ->
                when (action) {
                    Action.Income -> "Khazaane se +1 sikka. Koi rok nahi sakta."
                    Action.ForeignAid -> "+2 sikke. NETA block kar sakta hai."
                    Action.Tax -> "+3 sikke, NETA ka daava. Challenge ho sakta hai."
                    Action.Exchange -> "Patte badlo, JUGAADU ka daava. Challenge ho sakta hai."
                    is Action.Steal -> "Target se 2 sikke kheencho, BABU ka daava. Block/challenge ho sakta hai."
                    is Action.Investigate ->
                        "Target ka ek chhupa patta dekho, phir chaaho toh deck mein wapas — naya patta. " +
                            "PATRAKAAR ka daava. Block nahi, challenge ho sakta hai."
                    is Action.Assassinate -> "3 sikke do, target ek card khoyega. VAKIL block kar sakta hai."
                    is Action.Coup -> "7 sikke do, target ek card khoyega. Na block, na challenge."
                    Action.BailPe -> "9 sikke do, ek face-up card wapas chhup jaata hai. Block/challenge nahi."
                    Action.Sabotage -> "Ek apna chhupa card phenko, 3 sikke khazaane se lo. Bali Khel."
                    is Action.Hawala -> "Target ko seedha sikke do (max 5). Na block, na challenge."
                    Action.Emergency -> "Saare sikke de do, baaki sab ek card kho dete hain. ADHYADESH."
                }
            Language.ENGLISH ->
                when (action) {
                    Action.Income -> "+1 coin from the treasury. Nobody can stop it."
                    Action.ForeignAid -> "+2 coins. NETA can block it."
                    Action.Tax -> "+3 coins, claims NETA. Can be challenged."
                    Action.Exchange -> "Swap your cards, claims JUGAADU. Can be challenged."
                    is Action.Steal -> "Pull 2 coins off the target, claims BABU. Can be blocked or challenged."
                    is Action.Investigate ->
                        "Peek one of the target's hidden cards, then optionally force it back into the deck for a redraw. " +
                            "Claims PATRAKAAR. Can't be blocked, can be challenged."
                    is Action.Assassinate -> "Pay 3, target loses a card. VAKIL can block it."
                    is Action.Coup -> "Pay 7, target loses a card. No block, no challenge."
                    Action.BailPe -> "Pay 9, restore one revealed card to face-down. Unblockable."
                    Action.Sabotage -> "Sacrifice one face-down card, gain 3 coins from the treasury."
                    is Action.Hawala -> "Gift up to 5 coins directly to a target. Unblockable."
                    Action.Emergency -> "Pay all your coins, force every opponent to lose one card. Unblockable."
                }
        }

    // ── WHAT-NOW: per-reaction consequence + risk (one plain line each) ────────
    fun reactionBlockConsequence(role: Role): String =
        when (language) {
            Language.HINGLISH -> "${roleLabelOf(role)} bankar action roko. Jhootha block challenge hua toh aapka card jaayega."
            Language.ENGLISH -> "Block as ${roleLabelOf(role)} to stop the action. If the block is challenged and false, you lose a card."
        }

    val reactionChallengeConsequence: String get() =
        when (language) {
            Language.HINGLISH -> "Daava jhootha saabit hua toh unka card gaya. Sach nikla toh aapka card jaayega."
            Language.ENGLISH -> "Catch the bluff and they lose a card. If the claim is real, you lose a card."
        }
    val reactionPassConsequence: String get() =
        when (language) {
            Language.HINGLISH -> "Kuch mat karo. Action waise hi chalega."
            Language.ENGLISH -> "Do nothing. The action resolves as declared."
        }

    private fun roleLabelOf(role: Role): String =
        when (role) {
            Role.NETA -> "NETA"
            Role.BHAI -> "BHAI"
            Role.BABU -> "BABU"
            Role.JUGAADU -> "JUGAADU"
            Role.VAKIL -> "VAKIL"
            Role.PATRAKAAR -> "PATRAKAAR"
        }

    // ── WHAT-JUST-HAPPENED: compact plain-language recap of the most recent beat ─
    // [actor]/[other] are already-resolved display names from the presentation layer.
    fun recap(
        event: GameEvent,
        actor: String,
        other: String? = null,
    ): String? =
        when (language) {
            Language.HINGLISH ->
                when (event) {
                    is GameEvent.ActionDeclared -> "$actor ne ${actionDesi(event.action)} ka elaan kiya."
                    is GameEvent.Blocked -> "$actor ne ${roleLabelOf(event.role)} bankar block kiya."
                    is GameEvent.Challenged -> "$actor ne ${other ?: "kisi"} ko challenge kiya."
                    is GameEvent.ChallengeRevealed ->
                        if (event.hadRole) {
                            "$actor sach nikla. Challenger ka card gaya."
                        } else {
                            "$actor ka bluff pakda gaya. Card gaya."
                        }
                    is GameEvent.InfluenceLost -> "$actor ne ${roleLabelOf(event.role)} card khoya."
                    is GameEvent.PlayerEliminated -> "$actor out. Gaddi se door."
                    is GameEvent.CoinsTransferred -> "$actor → ${other ?: "kisi"}: ${event.amount} sikke gaye."
                    is GameEvent.Exchanged -> "$actor ne patte badle."
                    is GameEvent.Investigated -> "$actor ne ${other ?: "kisi"} ka patta dekha (Jaanch)."
                    is GameEvent.InvestigateRedraw -> "${other ?: "Us"} ka patta deck mein wapas — naya patta."
                    is GameEvent.GameEnded -> "$actor ne gaddi jeet li."
                    else -> null
                }
            Language.ENGLISH ->
                when (event) {
                    is GameEvent.ActionDeclared -> "$actor declared ${actionDesi(event.action)}."
                    is GameEvent.Blocked -> "$actor blocked as ${roleLabelOf(event.role)}."
                    is GameEvent.Challenged -> "$actor challenged ${other ?: "someone"}."
                    is GameEvent.ChallengeRevealed ->
                        if (event.hadRole) {
                            "$actor was truthful. The challenger loses a card."
                        } else {
                            "$actor's bluff was caught. Card lost."
                        }
                    is GameEvent.InfluenceLost -> "$actor lost their ${roleLabelOf(event.role)} card."
                    is GameEvent.PlayerEliminated -> "$actor is out of the game."
                    is GameEvent.CoinsTransferred -> "$actor → ${other ?: "someone"}: ${event.amount} coins moved."
                    is GameEvent.Exchanged -> "$actor swapped cards."
                    is GameEvent.Investigated -> "$actor peeked at ${other ?: "someone"}'s card (Investigate)."
                    is GameEvent.InvestigateRedraw -> "${other ?: "Their"} card went back to the deck — fresh draw."
                    is GameEvent.GameEnded -> "$actor took the chair."
                    else -> null
                }
        }

    /** A leading label for the recap strip — "Just now" / "Abhi-abhi". */
    val recapLabel: String get() =
        when (language) {
            Language.HINGLISH -> "ABHI-ABHI"
            Language.ENGLISH -> "JUST NOW"
        }
}

/** Phase-hint variants for [GaddiVoice.phaseHint] — the subtitle band beneath the banner. */
sealed interface PhaseHint {
    data object CoinCapKhela : PhaseHint

    data object PickAction : PhaseHint

    data object PickTarget : PhaseHint

    data object Confirm : PhaseHint

    data class ChallengeAction(
        val actor: String,
        val action: String,
    ) : PhaseHint

    data object Block : PhaseHint

    data class ChallengeBlock(
        val actor: String,
    ) : PhaseHint

    /** [cause] is a localized clause that already ends with a period, e.g. "Lost to Teja's Supari." */
    data class LoseInfluence(
        val cause: String,
    ) : PhaseHint

    data object Exchange : PhaseHint

    data object InvestigatePeek : PhaseHint

    data class Thinking(
        val actor: String?,
    ) : PhaseHint

    data object GameOver : PhaseHint
}

/** Centre-screen imperative prompt variants for [GaddiVoice.centerPrompt]. */
sealed interface CenterPrompt {
    data object PickTarget : CenterPrompt

    data object LoseInfluence : CenterPrompt

    data object Exchange : CenterPrompt
}

val LocalGaddiVoice = staticCompositionLocalOf { GaddiVoice(Language.HINGLISH) }
