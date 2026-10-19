# Fork notes — Free TV

**Upstream:** https://github.com/simbasjungles/free-my-tv (MIT) — all credit for the app, the design and
the original TCL support belongs there. This fork exists to run it on one specific TV and to send the
generic fixes back.

**This fork:** https://github.com/Ghost-9/free-my-tv · branch `acer-r10g` · PR #1
**Product name:** Free TV (the upstream code is credited in NOTICE/CREDITS and LICENSE)
**Target:** Acer R10G (`MOKA`, Realtek `rtd6748`, Android 14 / API 34, 1.81 GiB RAM, 4× ARMv7,
1920×1080 display override, Google TV `launcherx`).

## Weather city

`Delhi, India` — `28.6519, 77.2315`, `Asia/Kolkata`, `en`, `IN`, `USD` for the Binance ticker
(Binance has no INR pair; `INR` returns HTTP 400). These live in `config.properties`, which is
git-ignored. The coordinates were cross-checked against the network's public geolocation and are
approximate; edit `config.properties` and rebuild if the TV is somewhere else.

## What this branch changes

- `HomeMode.DEBLOAT` / `PROTECTED`: Acer R10G / Realtek lists (M Sign, Meeting Mode, ACR, Esticker,
  legacy input helpers; Realtek and Acer system packages protected).
- `HomeApps.SHELF` / `LIVE_TV_APPS` / `HIDDEN`: the eight apps actually installed on the TV,
  `com.realtek.tv` as Live TV, and the Acer system entries hidden from All Apps.
- `HomeView`: without a recommendation key, say so instead of drawing three empty cards.
- `app/assets/logos/youtube.png`, `youtube-dark.png`, `smarttube.png`: transparent marks; the banner
  extraction was cropping YouTube and shrinking SmartTube.
- The synthesized click was removed entirely. Ten replacement sounds live in `sound-lab/` and
  `app/assets/sounds/`; one will be wired behind an off-by-default Sound setting.
- Keyless **Popular on Netflix** rail via JustWatch (`NetflixRail.java`), with direct
  `netflix.com/title/<id>` links and a weekly cache.
- Settings retention fix: Settings runs in its own `:settings` process, `Scene.release()` on exit and
  the process is ended. Measured 101 → 191 MB graphics over 10 opens before; 77 MB fresh, 64 MB after
  10 opens with the fix (only `:home` remains). The generic upstream variant is PR #1 on the original repo.
- Build with **build-tools 36.0.0**. Build-tools 34's `d8` crashes with a `NullPointerException` on
  the nameless `MethodParameters` entry that javac 21 emits for an anonymous class's outer instance.

## Not upstreamed yet

The generic fixes (brand profile files, sound toggle, Scene lifecycle leak, `Adb` `::1` fallback,
missing `tools/logos.py`) are candidates for upstream PRs. This branch is **not** a request to change
the upstream project's scope.

## Safety

The app uses the TV's own ADB on `127.0.0.1:5555` and runs fixed commands only. It becomes the home
screen and screensaver and uses `pm disable-user` (never uninstall). *Original* restores the factory
home screen, screensaver and apps. Verify *Original* after every firmware update.
