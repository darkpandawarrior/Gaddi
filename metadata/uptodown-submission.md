# Uptodown submission — Gaddi

## Verdict: worth doing

Uptodown runs a genuine free self-serve developer console (sign up, "Add new app", attach a file
— [en.uptodown.com/developers-console](https://en.uptodown.com/developers-console)), no per-app
fee, no country restriction found for India. Its security posture is meaningfully better than the
other mirror sites checked for this channel: every file is scanned across 75+ VirusTotal engines
and Uptodown runs an identity-verification step on registering developers, which independent
trust-scoring puts it at 100/100 with no malware/phishing blacklist hits (source:
[mywot.com/scorecard/uptodown.com](https://www.mywot.com/scorecard/uptodown.com)). That is the
opposite of the reputational risk that ruled out APKPure for this app family — see the channel
verdict summary for the comparison. Gaddi's whole pitch is "genuinely FOSS-clean, nothing hidden,"
and Uptodown doesn't undercut that story the way a moderate-risk mirror site would.

## Not done yet (owner-only, needs a real Uptodown account)

- Actual account signup and identity verification — cannot be scripted or done on your behalf.
- A 1024×500 featured banner graphic. None exists in the repo (`featureGraphic.png` in fastlane is
  a different aspect ratio, used for other stores). Uptodown's spec is "recommended, not mandatory,"
  so this is skippable for a first submission.
- Whether a privacy policy URL is mandatory on Uptodown's form — not confirmed from outside the
  console (see caveats at the bottom). Gaddi is offline-only with no accounts and no server for the
  core game, so there is nothing to disclose either way; have an answer ready in case the form asks.

## Account setup (do this yourself)

1. Go to https://en.uptodown.com/developers-console and register — free.
2. Complete Uptodown's developer verification step before your first submission goes live.

## Submit the app

1. Developers Console → Apps → **Add new app**.
2. Package name: `com.kursi.android` (must match exactly — same package as the F-Droid/GitHub
   Release build, so it can be recognized as the same app if you ever claim/merge listings).
3. Upload the signed APK. Use the exact release asset, not a glob — the release also carries
   `-gms-release-unsigned` and `-noGms-release-unsigned` builds that are NOT signed:
   `https://github.com/darkpandawarrior/Gaddi/releases/download/v2026.08.35.1.234/Kursi-v2026.08.35.1.234.apk`
   (signing cert SHA-256: `e3cd9ed25baaa6db5501621a2a7399edc0878022f9b64b5d95446db0348dd19c` — verify
   with `apksigner verify --print-certs` before uploading, per the app-distribution skill's rule of
   verifying the certificate, not just the presence of one).
4. Icon: Uptodown wants a square PNG, ≥256×256, and rounds the corners itself, so upload a full
   square, not a pre-rounded one. Gaddi ships an adaptive icon (no static PNG in source) — export it
   with Android Studio's Image Asset tool, or unzip the signed APK above and take
   `res/mipmap-xxxhdpi-v4/ic_launcher.png`.
5. Screenshots: vertical/portrait preferred per Uptodown's guidance. Reuse the existing set, already
   built for this exact listing, at
   `/Users/darkpandawarrior/Repos/Android/Kursi/fastlane/metadata/android/en-US/images/phoneScreenshots/1.png`
   through `8.png`.
6. Category: closest fit is Games (matches the F-Droid `Categories: Games` entry) — confirm against
   Uptodown's own category list in the console, it wasn't independently verified here.

## Copy to paste

**Title**
```
Gaddi: Bluffing Card Game
```

**Short description**
```
Gaddi ke liye kuch bhi karega. Lie. Bribe. Survive. Repeat.
```

**Full description** (trim if the form enforces a shorter limit than this — not confirmed)
```
Gaddi ke liye kuch bhi karega.

Bluffing card game set in a satirical India corporate-political underworld. Claim any role on your turn, whether you hold it or not. If someone calls your bluff and they're right, you lose influence. If they're wrong, they do. Last player standing wins the Gaddi.

Five roles, each a deadpan archetype:
• Netaji Vachan: claims FDI, blocks Foreign Aid. Believes his own lies in real time.
• Bhai Teja: collects Supari, blocks assassination. Quiet, slow, unbothered.
• Babu Filewala: levies Tax, blocks Vasooli. Power through delay and the comma.
• Jugaadu Chhotu: Vasooli (steal). A solution for every problem. Mostly illegal.
• Vakil Loophole: blocks Supari. Has read every rule twice, knows three exceptions.

At 10 coins you must Khela (Coup). No exceptions.

10 AI bots, each bluffs differently. Netaji claims his role even when holding Bhai, Bhai Teja goes silent before he strikes, Babu Filewala taxis obsessively, Jugaadu Chhotu does something unpredictable every time, Vakil Loophole challenges on principle. Then there's Didi (vengeance is her love language), Madam Ji (always two moves ahead), and four others.

Modes:
• vs AI: 1 human vs up to 9 bots
• GAUNTLET (Tarakki ki Seedhi): 5-rung escalating ladder from Easy/3p through Grandmaster/6p
• Team Khel: faction play; allies are never legal targets
• DARBAR: bots chat, conspire, form alliances and hold grudges. You can manipulate them.
• TAMASHA: AI plays all seats. Watch it unfold.

DARBAR runs four story arcs during games: GATHBANDHAN (two bots ally, one eventually defects), AFWAAH (a rumour about someone's role spreads, might be true), STING (a bot leaks a real card claim), BADLA (a bot declares a grudge and follows through).

Everything else: interactive tutorial (Pehli Hazri), career stats and bot H2H records, auto-resume via deterministic intent log replay, full TalkBack semantics, CVD-safe role colours. Plays completely offline. No account, no ads, no server needed.

The visual identity is License Raj Deco: a 1950s to 1970s government-issue document aesthetic. Ration cards for liars. Enamel nameplates for crooks. Everything is on theme.

Gaddi is written in Kotlin Multiplatform and Compose Multiplatform, one codebase across Android, iOS, desktop, and web, with a Ktor server backing the online modes. This build is the noGms flavor: no Google Play Services, no Firebase, no proprietary blobs. Source is GPL-3.0-or-later, at https://github.com/darkpandawarrior/Gaddi.

Sab mile hue hain. Trust no claim. Especially your own.

सारे पात्र काल्पनिक हैं।
All characters and events are fictional. Satire only.
```

## What I could not confirm (verify in the console before relying on it)

- Exact character limits for title / short description / full description.
- Whether a privacy policy URL field is mandatory.
- Review/approval turnaround time.
