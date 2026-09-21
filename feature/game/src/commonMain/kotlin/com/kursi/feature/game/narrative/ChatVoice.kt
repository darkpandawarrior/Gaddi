package com.kursi.feature.game.narrative

import com.kursi.ai.social.CharacterFlaw
import com.kursi.feature.game.Language

/**
 * The DARBAR's voice — persona-flavoured Hinglish (and English) chat lines for the narrative layer.
 *
 * Deterministic + offline + free: every line is a template selected from this corpus, mirroring the
 * existing [com.kursi.feature.game.KursiVoice] pattern. The [SocialDirector] picks a line for a beat,
 * an event, a conspiracy pile-on, or a flaw-blunder; an optional online [ChatEmbellisher] may restyle
 * the chosen line later without ever changing the decision behind it.
 *
 * Persona ids are the 10 [com.kursi.ai.persona.PersonaRoster] ids; unknown ids fall back to
 * archetype-neutral lines so the corpus degrades gracefully.
 */
class ChatVoice(
    private val language: Language = Language.HINGLISH,
) {
    private val hi: Boolean get() = language == Language.HINGLISH

    // ── Opening: a bot greets the table when a narrative game begins ─────────────
    fun greet(personaId: String): String =
        when (personaId) {
            "netaji_vachan" -> if (hi) "Aa gaye? Achha hua. Vote… matlab support, ready rakho." else "You came? Good. Keep your support ready."
            "bhai_teja" -> if (hi) "Table pe baith. Zyada haath-paer mat chalana." else "Sit at the table. Don't move too much."
            "babu_filewala" -> if (hi) "Form bhar do pehle. Khel baad mein." else "Fill the form first. Game later."
            "jugaadu_chhotu" -> if (hi) "Aaja aaja, setting kar dete hain saath mein." else "Come, let's fix a deal together."
            "vakil_loophole" -> if (hi) "Har chaal note ho rahi hai. Soch ke." else "Every move is on record. Think."
            "madam_sarpanch" -> if (hi) "Mere gaon mein khel? Niyam mere." else "A game in my village? My rules."
            "dalla_tiwari" -> if (hi) "Dono taraf se commission. Welcome." else "Commission from both sides. Welcome."
            "maaji_anna" -> if (hi) "Imaandaari se khelo. Main dekh raha hoon." else "Play honestly. I'm watching."
            else -> if (hi) "Shuru karein? Kursi ek hi hai." else "Shall we begin? There's only one chair."
        }

    // ── Conspiracy: a bot openly joins the gang-up on [targetName] ────────────────
    fun pileOn(
        personaId: String,
        targetName: String,
    ): String =
        when (personaId) {
            "bhai_teja" -> if (hi) "$targetName ko main dekh leta hoon. Tum log piche raho." else "I'll handle $targetName. You stay back."
            "vakil_loophole" -> if (hi) "$targetName ke khilaaf case strong hai. Saath do." else "The case against $targetName is strong. Back me."
            "madam_sarpanch" -> if (hi) "Sab milke $targetName ko hatao. Hukum hai." else "Everyone, remove $targetName. That's an order."
            else -> if (hi) "$targetName pehle. Baaki baad mein." else "$targetName first. The rest later."
        }

    // ── Threatened: a bot frets when IT is the table's conspiracy target ──────────
    fun threatened(personaId: String): String =
        when (personaId) {
            "netaji_vachan" -> if (hi) "Sab mere peeche? Yeh saazish hai, media ko bulao." else "Everyone's after me? This is a conspiracy."
            "babu_filewala" -> if (hi) "Mere against kyun? Maine toh sirf file rok rakhi thi." else "Why against me? I only held a file."
            "jugaadu_chhotu" -> if (hi) "Arre yaar, mujhe kyun? Main toh neutral hoon!" else "Hey, why me? I'm neutral!"
            else -> if (hi) "Mujhe gher rahe ho? Galti karoge." else "Cornering me? You'll regret it."
        }

    // ── Taunt: a bot crows after hitting [targetName] ─────────────────────────────
    fun taunt(
        personaId: String,
        targetName: String,
    ): String =
        when (personaId) {
            "bhai_teja" -> if (hi) "$targetName, samjha diya na? Number delete." else "$targetName, message received? Delete the number."
            "jugaadu_chhotu" -> if (hi) "$targetName ka setting ho gaya. Next?" else "$targetName is sorted. Next?"
            "dalla_tiwari" -> if (hi) "$targetName se cut le liya. Standard hai." else "Took my cut off $targetName. Standard."
            else -> if (hi) "$targetName, baith jao. Aaram se." else "$targetName, take a seat. Relax."
        }

    // ── Gloat: a bot after surviving a challenge / a big play ─────────────────────
    fun gloat(personaId: String): String =
        when (personaId) {
            "netaji_vachan" -> if (hi) "Bola tha na — main hi Neta hoon." else "Told you — I'm the Neta."
            "vakil_loophole" -> if (hi) "Verdict mere haq mein. Hamesha ki tarah." else "Verdict in my favour. As always."
            else -> if (hi) "Dekha? Bluff nahi tha." else "See? Not a bluff."
        }

    // ── Flaw blurt: a bot's tell as it gets baited into a blunder ─────────────────
    fun flawBlurt(flaw: CharacterFlaw): String =
        when (flaw) {
            CharacterFlaw.EGO -> if (hi) "Main sabse strong hoon. Abhi dikhata hoon." else "I'm the strongest. Watch me prove it."
            CharacterFlaw.GREED -> if (hi) "Itna maal? Chhodne ka sawaal hi nahi." else "That much loot? No way I'm passing."
            CharacterFlaw.PARANOIA -> if (hi) "Yeh sab mere against hai. Pehle main maarta hoon." else "This is all against me. I strike first."
            CharacterFlaw.VENGEANCE -> if (hi) "Hisaab baaki hai. Aaj chukta karta hoon." else "A score is pending. I settle it today."
            CharacterFlaw.ZEAL -> if (hi) "Yeh anyaay hai! Main rok ke rahunga." else "This is injustice! I will stop it."
            CharacterFlaw.IMPULSE -> if (hi) "Soch ke kya karna, chalo kar dete hain." else "Why overthink — let's just do it."
        }

    // ── Ally coordination ─────────────────────────────────────────────────────────
    fun allyWith(allyName: String): String = if (hi) "$allyName, apna gathbandhan zinda hai. Saath chalte hain." else "$allyName, our pact holds. We move together."

    // ── Arc beats: player + narrator + generic bot fallback ───────────────────────
    fun arcBeat(
        beatKey: String,
        speakerName: String,
        targetName: String?,
    ): String {
        val t = targetName ?: "unhe"
        return when (beatKey) {
            // GATHBANDHAN
            "gathbandhan.offer" ->
                if (hi) {
                    "$t, sun. Tu-main milke khelein? Tu mujhe, main tujhe — abhi nahi."
                } else {
                    "$t, listen. Shall we team up? You and me — not yet against each other."
                }
            "gathbandhan.knife" -> if (hi) "$t, gathbandhan khatam. Sorry, politics hai." else "$t, the pact is over. Sorry, it's politics."
            // AFWAAH
            "afwaah.plant" -> if (hi) "Suna? $t ke paas Patrakaar chhupa hai. Sambhal ke." else "Heard? $t is hiding a Patrakaar. Be careful."
            "afwaah.spreads" -> if (hi) "Darbar mein khusur-phusur shuru… $t ke khilaaf." else "Whispers ripple through the Darbar… against $t."
            "afwaah.fuel" -> if (hi) "Aur ek baat — $t ne pichli baar bhi jhooth bola tha." else "One more thing — $t lied last time too."
            // STING
            "sting.flatter" -> if (hi) "$t, tu hi asli khiladi hai. Inhe dikha de tera dum." else "$t, you're the real player. Show them your strength."
            "sting.dare" -> if (hi) "$t, baatein toh sab karte hain. Karke dikha." else "$t, anyone can talk. Prove it."
            // BADLA
            "badla.approach" -> if (hi) "$t, gussa toh tujhe bhi hai. Main jaanta hoon kispe." else "$t, you're angry too. I know at whom."
            "badla.point" -> if (hi) "$t, asli dushman woh hai — $targetName. Usse nipta." else "$t, the real enemy is $targetName. Deal with them."
            else -> if (hi) "$speakerName: …" else "$speakerName: …"
        }
    }

    /** Persona-flavoured bot arc reply; falls back to a tone-neutral generic for unscripted ids. */
    fun botArcBeat(
        beatKey: String,
        personaId: String,
        targetName: String?,
    ): String {
        val t = targetName ?: ""
        return when (beatKey) {
            "gathbandhan.accept" ->
                when (personaId) {
                    "dalla_tiwari" -> if (hi) "Deal. Commission baad mein decide karenge." else "Deal. We'll fix the commission later."
                    "jugaadu_chhotu" -> if (hi) "Done bhai. Setting pakki." else "Done, bro. Pact's locked."
                    else -> if (hi) "Theek hai. Filhaal saath." else "Fine. Together — for now."
                }
            "gathbandhan.decline" ->
                when (personaId) {
                    "babu_filewala" -> if (hi) "Application reject. Akela theek hoon." else "Application rejected. I'm fine alone."
                    else -> if (hi) "Nahi. Main kisi ke saath nahi." else "No. I side with no one."
                }
            "gathbandhan.scorned" -> if (hi) "Gaddaar! Yaad rakhunga." else "Traitor! I'll remember this."
            "afwaah.bite" -> if (hi) "Toh yeh baat hai! Main pehle hi maarunga." else "So that's it! I'll strike first."
            "sting.swallow" ->
                when (personaId) {
                    "netaji_vachan" -> if (hi) "Bilkul sahi pehchana. Dekhte raho." else "You read me right. Keep watching."
                    else -> if (hi) "Haan, main hi sabse aage hoon. Dekh." else "Yes, I'm ahead of all. Watch."
                }
            "sting.boast" -> if (hi) "Abhi dikhata hoon kaun bada khiladi hai." else "I'll show you who the big player is."
            "badla.listen" -> if (hi) "Bol. Kaun?" else "Speak. Who?"
            "badla.accept" -> if (hi) "$t. Theek hai. Ab woh nishaane pe hai." else "$t. Fine. They're the target now."
            else -> arcBeat(beatKey, "", targetName)
        }
    }
}
