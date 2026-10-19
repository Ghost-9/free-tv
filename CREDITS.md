# Credits

**Free TV** would not exist without these people and projects.

## Upstream

- **[Free My TV](https://github.com/simbasjungles/free-my-tv)** by Simba — the original home screen,
  the settings/screen-switching model, the watchdog, the visual language and the build script. MIT.
  The upstream copyright is retained in [LICENSE](LICENSE); the relationship is described in
  [NOTICE](NOTICE) and [FORK.md](FORK.md).

## Free TV changes

- Acer R10G / Realtek `rtd6748` profile: `HomeMode.DEBLOAT`, `HomeMode.PROTECTED`, `HomeApps.SHELF`,
  `LIVE_TV_APPS`, `HIDDEN`.
- Keyless **Popular on Netflix** rail: `NetflixRail.java`, using JustWatch's public popularity graph
  and the provider's `netflix.com/title/<id>` links.
- Settings/`Scene` retention fix: `:settings` process, explicit `Scene.release()`, process ended on exit; measured 101 → 191 MB
  over ten opens before, 77 MB fresh / 64 MB after ten opens with the fix.
- Transparent YouTube and SmartTube marks; Free TV branding, README, licence and sound lab.

## Data and assets

| Material | Source | Licence |
|---|---|---|
| Inter | Rasmus Andersson | SIL OFL 1.1 |
| Material Symbols | Google | Apache 2.0 |
| cryptocurrency-icons | spothq | CC0 |
| Popularity | JustWatch | fetched at runtime, not redistributed |
| Netflix links/posters | Netflix / JustWatch offers | fetched at runtime, not redistributed |
| Weather | Open-Meteo | CC BY 4.0 |
| Prices | Binance public API | fetched at runtime |
| UI sounds | generated for Free TV | CC0 |

## Disclaimer

Not affiliated with, endorsed by, or sponsored by Netflix, Google, Acer, Realtek or JustWatch.
