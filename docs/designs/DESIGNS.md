# Five Free TV homepage directions

All five use the same clock, weather, shelf and real Netflix India posters. Only the rail is different.

| # | Direction | What it does | Tradeoff |
|---|---|---|---|
| 1 | **Big Rail** (`1.webp`) — **shipped** | Five 270×378 posters, rank badges, focus glow, See all, shelf. | Best balance. Larger posters cost ~? graphics; measure in P4. |
| 2 | **Hero Grid** (`2.webp`) | One focused title on the left, a 2×2 grid of the next four on the right. | Great for one strong pick; less shelf/rail rhythm. |
| 3 | **Editorial** (`3.webp`) | Three large posters with oversized outlined ranks and a short side list. | Most magazine-like; only three titles above the fold. |
| 4 | **Cover Flow** (`4.webp`) | A real carousel with depth and rotations; the focused poster is #1. | Most dynamic; highest GPU cost and the fiddliest focus code. |
| 5 | **Stage** (`5.webp`) | Large focused poster, synopsis and Play; Up Next in a 2×2 grid. | Most informative; closest to a detail page, less “browse at a glance”. |

The current build ships **1**. The other four are production-ready designs, not placeholders; each can be
wired into `NetflixRail`/`HomeView` without changing the data layer.

Credit: posters belong to Netflix and are fetched at runtime through JustWatch; this folder is design
material, not a redistribution of their artwork.
