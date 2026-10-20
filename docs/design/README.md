# Free TV — FigCraft design kit

Three self-contained vector files, made for FigCraft 0.7 (SVG import). Every screen is a named group
(`screen-01` … `screen-12`, `design-01` …); every poster and app tile is embedded, so the files travel
on their own. Font stack is Inter / Helvetica Neue / Arial.

| File | What is inside |
|---|---|
| `free-tv-ui-kit.svg` | 12 screens and states: home, rail focus, app focus, drawer, all apps, settings, home choice, sound picker, screensaver, markets, offline, restore confirmation. Plus palette, type scale, focus and interaction rules. |
| `free-tv-home-directions.svg` | Five new homepages: Command Bar (shipped), Editorial, Two-Tier, Sidebar, Minimal. Each has its own top toolbar and app rail. |
| `free-tv-app-rails.svg` | Five takes on the app row: Glass Shelf, Cinema Strip, Icon Row, Podium, Text Rail. |

Contact sheets: `ui-kit-contact.png`, `home-directions-contact.png`, `app-rails-contact.png`.

## Import

```bash
open -g -a FigCraft docs/design/free-tv-ui-kit.svg
open -g -a FigCraft docs/design/free-tv-home-directions.svg
open -g -a FigCraft docs/design/free-tv-app-rails.svg
```

FigCraft imports SVG as an editable document. Text remains text; posters and app tiles are embedded
images; groups are named for the layer panel. The boards are 1920×1080 artboards laid out with labels
and UX notes, not just flat screenshots.

## Interaction contract

- UP from the shelf focuses the Netflix rail; DOWN returns to the shelf.
- LEFT / RIGHT moves; OK opens; BACK returns.
- The top bar is the only global chrome: clock, search, weather, Nifty, grid, settings.
- Focus = 1.07 scale + 2 px white ring + coral glow; 160 ms ease-out.
- Sounds = 01 Tick Soft (focus), 04 Glass Ping (open).

Gold and silver are spot USD/oz converted at USD/INR, not MCX. Netflix posters are fetched at runtime;
this folder is design material.
