# Pending Tasks · Ink & Gold Design System Implementation

> This checklist is the English version written by WorkBuddy as the "first pass" baseline.
> Purpose: implement the already-confirmed design system (in `docs/design-system/`) into the two front-end codebases.
> Prerequisite: the design direction, tokens, icon library, component specs, and prototype are all confirmed and archived (no re-confirmation needed).

---

## 1. Background & Goal

- **Done:** The "Ink & Gold" design system is confirmed. Deliverables live in `docs/design-system/`:
  - `DesignSystemManifest.md` — unified semantic tokens (ink-blue + warm-gold, light/dark dual-theme mapping)
  - `ComponentSpec.md` — component inventory and full states
  - `QAReport.md` — QA (passed, 3 P2 suggestions)
  - `DeveloperGuide.md` — Web/Mini implementation steps and old→new icon name mapping
  - `code/icons.js` — 36 original SVG icons (single source of truth)
  - `code/AppIcon.web.vue` / `code/AppIcon.mini.vue` — dual-end icon components
  - `prototype/design-showcase.html` — interactive preview
- **To do:** the above design has NOT yet touched `loan-web` / `loan-mini` source. This task implements it.
- **Goal:** eliminate the two core problems — inconsistent icon sizing/stroke/coloring, and disconnected style tokens — so that theming only changes in one place.

## 2. Scope & Boundaries

| Item | In scope | Out of scope |
|---|---|---|
| Ends | loan-web (admin), loan-mini (mini-program) | loan-service (Java backend, not front-end) |
| Changes | tokenization, icon unification, fonts/theme | business logic, APIs, route structure |
| Risky moves | replace hard-coded colors, replace inline SVG | delete business components, change API fields |

## 3. Pending Task Checklist

### A. loan-web (Vue3 + Element Plus + Vite)
- [ ] **A1 Token intake:** In `src/styles/index.css` (~1157 lines), replace hard-coded hex (e.g. `#16203A`/`#D9A441` magic numbers) with the semantic tokens from `DesignSystemManifest.md`; keep `--loan-*` aliases for backward-compat or do a unified rename.
- [ ] **A2 Icon consolidation:** Adopt `code/AppIcon.web.vue`; replace the hand-written inline `<svg>` scattered across **23 .vue files** (messy sizes 12/14/16/18/20, strokes 1.7/1.8/2/2.4); unify on `AppIcon`.
- [ ] **A3 Element Plus built-in icons:** `el-alert show-icon`, `el-button icon`, etc. — progressively replace with original `AppIcon` without breaking functionality, for visual consistency.
- [ ] **A4 Dark theme:** Apply the dark承载层/cards/tables/trend-chart styles from the workbench prototype (design-showcase screen 4); ensure `var()` reacts to theme.
- [ ] **A5 Font swap:** Global fonts to `Space Grotesk` (Latin) + `Noto Sans SC` (CJK); remove Inter/Roboto traces.
- [ ] **A6 Self-test:** icon size/stroke uniform, theme color linkage, no console errors.

### B. loan-mini (uni-app Vue3 mini-program)
- [ ] **B1 Token alignment:** Align `--brand-*` to unified semantic tokens; add the missing spacing/radius scales on Mini.
- [ ] **B2 Icon coloring fix (critical):** Adopt `code/AppIcon.mini.vue`, injecting **real THEME color values at runtime** instead of the hard-coded `#2443C2` / TabBar `rgba(26,35,54,.55)`; resolves the historical bug where mini-program SVG doesn't parse `var()` (icons turning into solid circles / disappearing).
- [ ] **B3 Font/spacing/radius tokenization:** Unify page-level spacing and radius, remove scattered magic numbers.
- [ ] **B4 TabBar linkage:** Bottom TabBar icon & text active state switches to warm-gold on theme change.
- [ ] **B5 Real-device self-test:** WeChat DevTools + real device preview; verify icon rendering and theme switching.

### C. Dual-end Acceptance
- [ ] **C1 QA recheck:** Re-run the 5 checks from `QAReport.md`; focus on "design-token compliance" and "decoration sanity".
- [ ] **C2 Visual walkthrough:** Unified icons, consistent palette, correct light/dark themes on both ends.
- [ ] **C3 Follow-ups:** Address the 3 P2 suggestions (if any); output recheck conclusion.

## 4. Acceptance Criteria

1. **Single token source:** changing the theme edits only one token definition; both ends (and icons) sync.
2. **Unified icons:** all business icons come from the single `code/icons.js` source, uniform 24×24, stroke 1.75, rounded caps, `currentColor` coloring; Web links via `var()`, Mini links via THEME injection.
3. **No hard-coded colors:** no游离 hex in `loan-web/src/styles/index.css` or `loan-mini` global styles (rare token-uncovered exceptions must be commented).
4. **Zero regression:** existing features, routes, APIs unaffected; build passes, no console errors.

## 5. Key Risks & Notes

- **index.css is large:** 1157 lines with heavy duplication; do incremental replacement + global search for游离 hex to avoid regressions from one big edit.
- **Mini var() trap:** mini-program SVG `stroke` doesn't parse `var()`; must inject real color values (see `AppIcon.mini.vue` THEME approach); do NOT reuse Web's `var()` directly.
- **Icon name mapping:** `DeveloperGuide.md` provides old→new `AppIcon` name mapping; cross-reference during migration to avoid misses.
- **Staged PRs:** recommend separate PRs for A/B/C for easier review and rollback.

## 6. Reference Inputs (archived, do not modify)

Under `docs/design-system/`: DesignSystemManifest.md, ComponentSpec.md, QAReport.md, DeveloperGuide.md, code/, prototype/design-showcase.html
