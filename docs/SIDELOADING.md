# iOS sideloading

Gaddi isn't on the App Store. Every tagged release publishes an unsigned `.ipa` that you install
yourself using your own free Apple ID, with one of the tools below. No account or payment to
anyone required, and it works from any country.

## Why unsigned, and why that's fine

iOS refuses to run any app without a valid code signature. Apple's own alternative marketplaces
(AltStore PAL, Onside, the EU/Japan lane of Aptoide and Epic Games Store) could sign one for you,
but every one of them requires the publisher to hold a paid Apple Developer Program membership and
notarize the app through Apple, and they only work on a device whose Apple Account region is set
to the EU or Japan. None of that applies here.

The path that's actually open to everyone: publish the app unsigned, and let a resigning tool
attach a free, personal signature using *your* Apple ID at install time. That's the entire purpose
of AltStore (classic), SideStore, and Sideloadly. They strip whatever signature an `.ipa` carries,
or don't care that it has none, and sign it fresh.

## What this gets you, and what it doesn't

- Installs and runs like any app, no jailbreak.
- Free Apple ID: the resulting signature lasts **7 days**, then needs re-signing (AltStore/SideStore
  can do this automatically over Wi-Fi if the app stays installed and the tool stays running/paired).
- A free Apple ID limits you to **3 sideloaded apps at a time** on the device, Gaddi included.
- Push notifications are unverified. The Apple entitlement they need (`aps-environment`) generally
  isn't issuable to a free-tier signature. Gaddi should still install and run; whether Firebase
  Messaging functions under a resigned build hasn't been tested.

## Install steps

1. Download the `Kursi-<version>-ios-unsigned.ipa` asset from the
   [latest release](https://github.com/darkpandawarrior/Gaddi/releases/latest).
2. Pick one:
   - **SideStore** (no computer needed after first setup): <https://sidestore.io>. First install
     needs a computer once; after that it can resign itself over Wi-Fi.
   - **AltStore + AltServer** (Mac or Windows): <https://altstore.io>. Install AltServer on your
     computer, pair your iPhone, then open the `.ipa` with AltServer's "Install" (or drag it onto
     the AltStore app on the device) using your Apple ID.
   - **Sideloadly** (Mac or Windows, no companion app on the phone):
     <https://sideloadly.io>. Connect over USB, drag in the `.ipa`, enter your Apple ID, install.
3. On the device, trust the certificate: **Settings > General > VPN & Device Management**, then the
   profile under your Apple ID, then Trust.
4. Open Gaddi. If it's been 7 days and the app stopped launching, re-run the tool you used (or let
   SideStore's background refresh handle it).

Every tag build replaces the previous `.ipa` on the release, so re-downloading and re-sideloading
after an update follows the same steps above.
