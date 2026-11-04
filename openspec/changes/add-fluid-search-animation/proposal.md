## Why

The search transition feels abrupt: pressing Search replaces the query screen with a centered spinner
and then snaps to results. There is no visual continuity between typing and receiving results, and no
sense of progress while the metasearch fan-out runs. Adding a fluid-fill animation inside the search
field itself turns the dead time into a polished, branded moment — the user sees the bar filling in
real time, which communicates progress and makes the wait feel shorter. This directly serves the
**customizable** and **always-on** goals: a metasearch app people keep open all day should reward
interaction, not punish it with a blank screen. The animation respects all 13 named themes (it
derives its palette from the active theme's accent/surface/border tokens) and adds no telemetry,
network calls, or new permissions.

GitHub issue: #117 ("Enhancement: Add a loading animation during search").

## What Changes

### 1. Fluid-fill progress indicator in the search field (app)

Replace the centered `CircularProgressIndicator` (`LoadingState()` in `SearchScreen.kt`) with an
in-field layered-wave animation:

- On submit (Enter / search-icon tap), the `OutlinedTextField` stays visible.
- A `Canvas` overlay inside the field draws three layered sine waves at different frequencies and
  amplitudes that fill from left to right, acting as a progress bar (progress is time-based with an
  ease curve, since fan-out completion is not granular enough for true determinate).
- **Style: Layered Waves (Style B).** Three sine layers at different frequencies, slightly offset,
  with semi-transparent blending. Smooth, decorative, and clean.
- **Performance:** the sine math is trivial (three `sin()` calls per x-pixel per frame). The canvas
  draw uses a single `Path` per layer with `drawPath` + gradient fill — no pixel-level operations,
  no allocations in the draw loop. Target: <0.5ms/frame on mid-range hardware. The animation runs
  only during the ~1-3s search window and stops immediately on results (no ongoing draw loop, no
  `LaunchedEffect` leak). Use `withFrameMillis` for timing, not a coroutine delay loop.
- Colors derive from the active theme's `accent` (fluid body), `surface` (gradient top), and a
  computed foam color (white at reduced opacity). Works across all 13 themes and Material You.
- When results arrive, the bar quickly fills any remaining width (fast ease-to-100%), then the
  results page slides up from the bottom with a clear top border that fades out as it reaches its
  final position (Compose `AnimatedContent` with a `slideInVertically` + `fadeIn` transition; the
  top border uses `drawWithContent` to overlay a horizontal rule that animates from full opacity to
  zero over the slide duration).

### 2. Fluid-fill progress indicator on the served page (web UI)

The same layered-wave visual on the Ktor-served search page:

- A `<canvas>` element positioned inside the search `<input>` wrapper.
- JavaScript drives three layered sine waves, reading CSS custom properties (`--sm-accent`,
  `--sm-surface`, etc.) for theme-aware colors.
- **Performance:** uses `requestAnimationFrame` (not `setInterval`), cancels on results, allocates
  nothing in the draw loop. The canvas is sized to the input field (small), so even on low-end
  phones the cost is negligible.
- On form submit, the canvas animates; on results load, it fills and the results container slides up
  with a CSS transition. The results container has a clear top border (`border-top`) that fades from
  full opacity to transparent over the 400ms slide-up transition.
- No external JS dependencies; the simulation is a self-contained ~60-line module inlined in the
  served page.

### 3. Accessibility and reduced motion

- When `prefers-reduced-motion` is active (web) or the Android "Remove animations" accessibility
  setting is on, fall back to a simple left-to-right color wipe (no wave physics) at the same
  progress rate.
- The fluid overlay does not obscure the query text (text `z-index` stays above the canvas; the
  fluid is semi-transparent).
- Screen readers see no change: the existing `search_loading` content description / `aria-busy`
  attribute is retained.

## Capabilities

### New Capabilities
- `fluid-search-animation`: a theme-aware 1D fluid-fill progress animation inside the search field,
  with a slide-up results transition, on both the Compose app and the served web page.

### Modified Capabilities
- `search-loading`: the existing `LoadingState()` composable and its centered spinner are removed in
  favor of the in-field fluid animation; the `SearchUiState.Loading` state is retained but renders
  differently.

## Non-goals

- Full 2D Navier-Stokes, SPH particles, or heightfield physics: unnecessarily heavy for a phone
  search bar. Layered sine waves are visually clean and run in <0.5ms/frame.
- User-configurable animation style or speed: the animation is a fixed part of the brand. A future
  change could add this.
- Animating anything outside the search field (no full-screen fluid, no splash).
- Progress tied to individual engine responses: the fan-out completes too fast and unevenly for
  per-engine ticks to look smooth. The progress curve is time-based with a fast finish on completion.
- Lottie or GIF assets: the simulation is code-driven and theme-aware; a canned asset cannot adapt
  to 13 palettes.

## Impact

- **Modified files (app):**
  - `ui/search/SearchScreen.kt`: replace `LoadingState()` with `FluidSearchBar()` composable
    containing a `Canvas` overlay; add slide-up `AnimatedContent` transition for results.
  - `ui/search/SearchViewModel.kt`: no structural change; `Loading` state still emitted; may add a
    `progressFraction` flow if we want the fill to accelerate on partial engine returns.
  - `ui/search/SearchUiState.kt`: no change (Loading state reused).

- **New files (app):**
  - `ui/search/WaveAnimation.kt`: the layered sine-wave math (pure functions, no Android deps;
    unit-testable). Three wave configs (amplitude, frequency, speed, alpha).
  - `ui/search/FluidSearchBar.kt`: the Compose `Canvas` overlay composable that drives the
    animation. Draws three gradient-filled `Path` objects per frame — no allocations in the draw
    loop (paths are pre-allocated and reset each frame).

- **Modified files (web):**
  - `server/SearchServer.kt`: inline the JS fluid module in the served page `<script>`, wire it to
    the search form's submit event, and add the slide-up CSS transition for the results container.

- **New files (web):** none (JS is inlined in the served HTML template).

- **No new dependencies.** The simulation uses `kotlin.math` (app) and vanilla JS `Math` / Canvas
  2D (web). No Lottie, no animation libraries.
- **No new permissions, no network calls, no telemetry.**
- **Battery:** the Canvas animation runs only during the ~1-3s search window and stops immediately
  on results. No ongoing draw loop.
- **Tests:** unit tests for `FluidSimulation.kt` (deterministic step function), UI test asserting
  the loading tag still appears and that results eventually display.
