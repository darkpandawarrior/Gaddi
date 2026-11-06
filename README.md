<img src="docs/assets/banner.gif" alt="Gaddi" width="900" />

# Gaddi

Gaddi is a bluffing card game for 2 to 10 players, set in a made-up corporate-political India. Everyone holds hidden role cards, everyone lies about them, and the last player with a card wins the chair (the *gaddi*). The tagline is "Gaddi ke liye kuch bhi karega", he will do anything for the chair.

It is a Kotlin Multiplatform project with Compose Multiplatform screens: one codebase for Android, iOS, desktop and a Wasm browser build, plus a Ktor server for online rooms. The rules are those of *Coup* (Rikki Tahta, Indie Boards and Cards, 2012) with a new skin, a sixth role, seven optional variants and a bot social layer on top. Mechanics are not copyrightable and everything else here is original; see `NOTICE`.

I built it to see how far one engine written as pure Kotlin could carry: the same reducer runs inside the phone app, on the server, in a headless terminal client and in the bot search.

![A mid-game table with a claim on the stack](docs/screenshots/4p_mid_claim.png)

## Run it

The build composes two sibling repos as git submodules under `external/` (`kmp-toolkit` and `kmp-build-logic`). A plain `git clone` leaves them empty and Gradle stops at the first `includeBuild`, so clone with them:

```bash
git clone --recurse-submodules https://github.com/darkpandawarrior/Gaddi.git
cd Gaddi
./gradlew :cmp-desktop:run
```

That is the quickest way to play. The code targets JDK 25 (`jvmToolchain(25)` in `cli/build.gradle.kts`). The Gradle wrapper is `9.9.0-rc-1`. I wrote this page from the code and did not build anything for it, so the commands are copied from the build files, not re-run.

Other entry points, all taken from the build files and `Makefile`:

```bash
./gradlew :cmp-android:assembleGmsDebug          # Android, Firebase and Play Core included
./gradlew :cmp-android:assembleNoGmsRelease -Pfdroid   # FOSS build for F-Droid
./gradlew :cmp-web:wasmJsBrowserDistribution     # browser build
./gradlew :cmp-ios:linkReleaseFrameworkIosArm64  # KursiKit.framework for the Xcode project in iosApp/
./gradlew :server:installDist                    # online game server (server/Dockerfile packages this)
./gradlew :cmp-desktop:renderScreens             # render screen fixtures to PNG, no device
./gradlew jvmTest                                # all JVM tests
make all                                         # desktop, android, web and server into outputs/
```

`cp keystore.properties.template keystore.properties` and fill it in before a signed release build. `scripts/bump_version.sh --patch|--minor|--major` bumps `VERSION`.

There is also a terminal client that runs the engine with no UI at all, useful for checking seat balance under random play:

```bash
./gradlew :cli:run --args="4 1000 1"    # seats, games, seed
```

## What is in the game

**Roles.** Five roles in every game (Neta, Bhai, Babu, Jugaadu, Vakil), mapped onto Coup's Duke, Assassin, Captain, Ambassador and Contessa. A sixth, Patrakaar, who examines another player's card, joins at larger tables. `Role` in `engine/.../Model.kt` has all six.

**Bots.** Five difficulty levels, Easy to Grandmaster (`Difficulty` in `feature/game/.../GameAction.kt`). Expert and Grandmaster run ISMCTS, a search that samples the hidden cards each opponent might hold and plays out the game from each guess (`ai/.../IsmctsSearch.kt`, `Determinizer.kt`, `BeliefModel.kt`). Ten named personas (`PersonaRoster.kt`) each play a different style: Vakil Loophole challenges everything, Didi holds grudges, Sharmaji's Son never adapts.

**DARBAR.** The bots talk, gossip and form pacts at the table. Four story arcs (`DarbarArc`): Gathbandhan, Afwaah, Sting and Badla. The KISSA mode strings them into a campaign.

**Three densities.** The same game state can be shown at FOCUS (a headline, your hand, your legal moves), GUIDED (one suggestion at a time) or ANALYST (suspicion pips, odds, dossiers, event log, chat). `DensityLayer.kt` decides, and a player graduates upward from their own stats ledger.

**Coach and narrator.** The decision coach grades your claims and challenges against the search. The Munshi narrates the match in character; it picks an on-device model if one exists, then a cloud model with your own key (Anthropic, OpenAI or Gemini), then falls back to templated lines that always work (`ai/.../MunshiNarrator.kt`).

**Modes.** Versus bots, GAUNTLET (a promotion ladder), team play, TAMASHA (watch bots play), pass and play on one device, a daily challenge, a career dossier with Elo, and a replay scrubber that rebuilds a match from its seed.

**Seven variants**, all off by default, set in `GameConfig`: bail (pay 9 coins to flip a lost card back), sabotage (sacrifice a card for 3 coins), hawala (gift coins), emergency, khazana (race to a coin total), inflation (costs rise every few turns) and scarcity (capped coin supply).

**Online.** Private rooms by 4-letter code, quick match, and LAN discovery. The Ktor/Netty server holds the true state and sends each client only its redacted view. Deploying it to Fly.io is wired (`fly.toml`, `server-deploy.yml`) and does nothing until `FLY_API_TOKEN` is set, so no public server runs from this repo.

More screens: [home](docs/gifs/home.gif), [a turn](docs/gifs/turn.gif), [DARBAR](docs/gifs/darbar.gif), [coach](docs/gifs/coach.gif), [online](docs/gifs/online.gif), [career](docs/gifs/career.gif), and 61 stills in [docs/screenshots](docs/screenshots).

## How it is laid out

```
engine/          pure reducer, secrecy boundary (redact), seeded RNG. Depends on nothing.
ai/              bot policies, ISMCTS, personas, DARBAR arcs, Munshi
shared-protocol/ serializable views and client/server messages
core/            designsystem, network (WebSocket client), prefs
feature/game/    game session, MVI view model, the table screens
cmp-shared/      GaddiApp(), the root composable all four shells host
cmp-android  cmp-ios  cmp-desktop  cmp-web    thin application shells
server/          Ktor/Netty server, one actor per match
game-services/   Game Center and room-code helpers
cli/             headless terminal client
```

That is 15 Gradle modules in `settings.gradle.kts`. Part of the toolkit comes from [kmp-toolkit](https://github.com/darkpandawarrior/kmp-toolkit) and the convention plugins from [kmp-build-logic](https://github.com/darkpandawarrior/kmp-build-logic), both pulled in through `includeBuild`. The code still uses the original name in packages (`com.kursi`); the product is Gaddi. Design rules are in [DESIGN.md](DESIGN.md), the visual identity in [docs/brand/BRAND.md](docs/brand/BRAND.md), and the printable rules in [docs/Gaddi_Game_Rules-v2.pdf](docs/Gaddi_Game_Rules-v2.pdf).

## What is proven and what is not

Counted today with `grep -rho '@Test' --include='*.kt'` outside `external/`: **412 `@Test` methods in 68 files**. By module: engine 69, ai 106, feature/game 114, core/designsystem 12, server 17. The ktlint, detekt and `jvmTest` run lives in `quality.yml`.

- The Munshi's on-device tier (Gemini Nano on Android, Apple Foundation Models on iOS) has only run against test fakes. No device with that hardware has run it.
- Android release signing, store uploads (Play, F-Droid, Amazon, Huawei, Samsung, Indus, Aptoide) and the Fly.io deploy all have workflows that stay idle until their secrets exist.
- `VERSION` is `1.0.0`, `BUILD_NUMBER` is `0`, and no GitHub Release has been cut.
- `AiBotDecisionEngine`, the voiced-persona bot tier, is built and tested but nothing constructs it in a live match yet.

## Licence

Code is GPL-3.0-or-later (`LICENSE`), with an App Store exception so forks can reach iOS. Shipped assets (art, fonts, audio, character names, Hinglish voice lines) are CC BY-SA 4.0 (`ASSETS-LICENSE`). The unshipped story bible stays all rights reserved. All characters and events are fictional and the setting is satire.

Related: [Doori](https://github.com/darkpandawarrior/Doori), [PaymentsLab-KMP](https://github.com/darkpandawarrior/PaymentsLab-KMP), and [the portfolio](https://cv-siddharth.vercel.app/).
