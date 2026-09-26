# Professional Emulator UI (AetherSX2 Style Translucent Overlay)

Transition from the retro Game Boy handheld plastic shell to a sleek, modern, professional emulator layout modeled directly after top-tier mobile emulators (AetherSX2 / PPSSPP). The app will feature a full-bleed cinematic game display with ergonomic translucent glass touch controls, live FPS telemetry, top pause HUD, and bottom-center system pills.

## User Review & Critical Decisions

> [!IMPORTANT]
> The user confirmed the following design choices based on the reference screenshot:
> - **Orientation & Layout**: Landscape fullscreen layout with edge controls directly overlaid on top of the gameplay, eliminating plastic bezel borders.
> - **Button Aesthetic**: Clean, modern translucent glass outlines with subtle white/gray stroke and minimal dark fill, matching the uploaded AetherSX2 screenshot.
> - **HUD & Telemetry**: Top-right live performance metrics (FPS, speed percentage) and a top-right pause/menu button, alongside a top-left status pill.

- **Confirmed Decision 1**: Modernize `GameBoyScreen` and `GameControllerOverlay` to adopt the borderless, professional emulator layout shown in the reference image.
- **Confirmed Decision 2**: Provide dynamic orientation support (landscape-first cinematic experience with auto-switch or dedicated landscape lock option) and top HUD telemetry (FPS: ~59.7 FPS, Speed: ~100%, and quick pause overlay).

---

## 1. Overview & Core Concept

- **What It Does**: Replaces the bulky retro plastic Game Boy casing with an ultra-clean, professional touchscreen gaming HUD. The game screen scales to fill the display edge-to-edge, while translucent touch controls float over the screen margins for maximum thumb comfort and zero visual clutter.
- **Target Audience**: Mobile gamers and retro gaming enthusiasts who want modern, responsive, distraction-free emulation controls similar to PPSSPP, AetherSX2, and RetroArch.
- **Key Value**: Delivers a premium, immersive gaming interface with precise 8-way directional input, responsive circular action buttons, live performance metrics, and zero bezel obstruction.

---

## 2. User Experience & Visual Design

### Key User Flows

1. **Cinematic Gameplay Zero-State**:
   - The game screen renders edge-to-edge in high contrast.
   - On the left margin: Sleek cross D-Pad with subtle directional indicator arrows and rounded corners.
   - On the right margin: Generously spaced translucent circular `A` and `B` buttons with clean geometric typography.
   - At the bottom-center: Sleek pill buttons for `SELECT` and `START`.
   - At the top-right: Live telemetry badge displaying red/green retro monospace metrics (e.g., `G: 59.73 [P] | V: 59.82`) and a quick pause button (`||`).
   - At the top-left: Compact status badge displaying active core state / ROM information.

2. **Touch & Ergonomic Interaction**:
   - Tapping or sliding the left thumb over the D-Pad activates 8-way directions with smooth visual glow feedback and subtle haptic response.
   - Right thumb taps or rocks across `A` and `B` with tactile spring depression and multi-touch recognition.
   - Tapping the top-right pause button (`||`) freezes the frame loop and reveals a translucent in-game quick drawer (Save State, Load State, Fast Forward Turbo, Settings, and ROM Library).

### Visual Identity & Theme

- **Aesthetic Direction**: High-end mobile emulator HUD (AetherSX2 / PPSSPP inspired), minimalist dark translucent glassmorphism.
- **Color Palette**:
  - Screen Background: `#000000` (deep pitch black for edge-to-edge OLED immersion).
  - Overlay Stroke: `Color.White.copy(alpha = 0.35f)` with `0.55f` highlight on active press.
  - Overlay Fill: `Color(0xFF1E2024).copy(alpha = 0.20f)` (subtle dark glass).
  - Telemetry Accent: `#EF4444` (red metrics label) and `#22C55E` (green stable FPS indicator) in clean monospace styling.
  - Active Press Glow: Dynamic luminance boost and slight physical scale animation (`0.92f`).
- **Typography & Proportions**:
  - Telemetry: Monospace 12sp bold.
  - Button Labels: Bold Sans-Serif 22sp for `A` and `B`, 11sp bold for `SELECT` and `START`.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Fullscreen Overlay vs Handheld Bezel Shell**:
  - *Chosen Approach*: Replace the DMG/plastic handheld frame layout with the full-screen transparent HUD overlay as the default experience, while preserving classic theme preferences in Settings for users who still want nostalgia.
  - *Why*: Direct match to user's uploaded reference image and standard practice for modern Android emulators.
- **Decision 2: Live FPS & Telemetry Calculation**:
  - *Chosen Approach*: Implement a lightweight rolling frame time calculator in `EmulatorViewModel` measuring real elapsed milliseconds between frames to calculate authentic PPU/FPS rates.
  - *Why*: Provides the exact visual styling seen in the top right of the user's reference image without consuming CPU overhead.
- **Decision 3: In-Game Pause & Quick Menu**:
  - *Chosen Approach*: Place a pause button (`||`) at the top right (as shown in the reference image) that toggles emulator pause and displays a floating glass action bar for ROM switching and quick saving.
  - *Why*: Eliminates the need for a permanent chunky top toolbar that takes away valuable screen real estate.

---

## 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│                 Professional Emulator HUD Screen                │
│                                                                 │
│  ┌──────────────────┐               ┌────────────────────────┐  │
│  │ Status Badge     │               │ FPS: 59.7 [P]   [ || ] │  │
│  │ "Game Boy Core"  │               │ Performance Telemetry  │  │
│  └──────────────────┘               └────────────────────────┘  │
│                                                                 │
│                  ┌───────────────────────────┐                  │
│                  │                           │                  │
│                  │    Game Display Canvas    │                  │
│                  │        (160 x 144)        │                  │
│                  │       Maximized Fit       │                  │
│                  │                           │                  │
│                  └───────────────────────────┘                  │
│                                                                 │
│    ┌─────────┐                                 ┌─────────┐      │
│    │  D-Pad  │                                 │    A    │      │
│    │  (8-Way │          ┌───────┐ ┌───────┐    │         │      │
│    │  Glass) │          │SELECT │ │ START │    │    B    │      │
│    └─────────┘          └───────┘ └───────┘    └─────────┘      │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
                                ▲
                                │
              ┌─────────────────┴─────────────────┐
              │     EmulatorViewModel & State     │
              │  - screenImage: StateFlow         │
              │  - joypadDir & joypadAct          │
              │  - fpsCounter: StateFlow          │
              │  - isPaused: StateFlow            │
              └─────────────────┬─────────────────┘
                                │
              ┌─────────────────┴─────────────────┐
              │           GameBoy Core            │
              │   CPU ⇄ MMU ⇄ PPU ⇄ APU ⇄ Joypad  │
              └───────────────────────────────────┘
```

### Components to Update
1. **`GameControllerOverlay.kt`**:
   - Re-style `OverlayDPad` to match the rounded-cross glass outline with subtle internal arrow markings from the screenshot.
   - Re-style `OverlayActionButtons` to match the large circular `A` and `B` glass rings with crisp centered typography.
   - Position `SELECT` and `START` pill buttons at bottom-center with modern rounded borders.
2. **`GameBoyScreen.kt`**:
   - Maximize screen real estate: edge-to-edge game canvas.
   - Add top-right live performance HUD (FPS, speed %, pause toggle).
   - Add top-left subtle notification pill (e.g. core status / loaded ROM).
   - Floating translucent pause menu drawer when paused.
3. **`EmulatorViewModel.kt`**:
   - Add FPS and frame time calculation to provide real live telemetry metrics to the UI.
