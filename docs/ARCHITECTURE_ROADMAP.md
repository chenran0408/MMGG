# MMGG architecture roadmap

This roadmap is for work that can be completed without a connected device. It does not replace `CURRENT_STATUS.md`; that file remains the source for current facts.

## Four functional areas (2026-11-05 audit)

The project is organized around four functional areas. Package layout already separates gestures code; the `codex` package still mixes area 1, area 4 and shared UI infrastructure, which the rounds below address by ownership rather than mass file moves.

| Area | Scope | Main code (current location) | Size (files/lines) |
|---|---|---|---|
| 1 分词与拖拽 | Press capture, segmentation, drag, share targets, stash, WeChat adaptation, OCR/recognize | `codex` press/drag/bigbang/stash/portal files, `gestures` root input readers | 73 files / ~22.9k lines |
| 2 手势增强 | Corner/side/honeycomb triggers, recents, outside gestures, gesture config | `gestures/xposed` Fan* + Honeycomb* + TaskSwitcher*, `gestures/config` | 60 files / ~14.0k lines |
| 3 小窗增强 | HyperOS freeform bridge, mini windows, shortcut host, media controls | `gestures/xposed` HyperOsFreeformBridge, ShortcutHostRuntime, MiniMediaControls, HyperOsProfile | 16 files / ~5.1k lines |
| 4 其他功能 | Settings UI, activation/diagnostics, touch probe reports, app update, copy direct, licenses | `codex` settings/diagnostics/touchprobe/update pages | 34 files / ~10.4k lines |

Cross-cutting shared UI (`ModernOverlayViews`, `OverlayPanelUi`, `InteractiveOverlayWindow`, `NativeGlassBackdrop`, `Os3BackgroundEffect`) serves areas 1 and 2; it is treated as shared infrastructure, not a fifth area.

Hotspots by size: `DragShareController` 2592, `FanRuntime` 2307, `HyperOsFreeformBridge` 1894, `DragShareSettings` 1378, `ConfigStore` 1377, `SettingsScreen` 1136, `HoneycombOverlayView` 1113, `FanFreeformHook` 1062, `PressCaptureSource` 1001 (71 WeChat references, the heaviest host coupling in area 1).

## Completed offline work

- Fan runtime eligibility, target resolution, configuration loading and freeform geometry are isolated behind small decision boundaries.
- Settings navigation and effect notices are isolated without changing keys, defaults or reload timing.
- Drag session and preview callback ownership use lifecycle tokens and generation checks.
- Freeform queued launches use request generations and explicit `QUEUED`, `REQUESTED`, `OBSERVED` and `UNKNOWN` evidence stages.
- Activation presentation keeps `UNKNOWN` separate from missing permission.
- The latest offline follow-up added retry budget and stale same-task generation tests. No production behavior was changed because the current guards were already complete.

## Next device-independent rounds

### E1 (area 1): WeChat press candidate owner

Extract the WeChat press-candidate state machine (`PendingWeChat`, drag takeover, text delivery, screen-capture rejection) out of `PressCaptureSource` into one owner class. Generation checks, Root authority and Bitmap/URI ownership stay unchanged; existing TwoFingerPress/WeChat tests are the guardrails.

### E2 (area 2): FanRuntime remaining responsibilities

Keep extracting only clearly owned pieces from `FanRuntime` (recents watching, corner/side arbitration state) behind the existing collaborators. No input semantics change.

### E3 (area 3): freeform request value object

If duplication continues to grow, introduce a small internal request record for queued target launches and external intents. It must preserve the existing retry policy, task matching, timeout and private HyperOS calls. Do not merge the two launch paths until tests prove their behavior is equivalent.

### E4 (area 4): diagnostic capability projection

Keep capability collection, evidence interpretation and user-facing presentation separate. The home page should expose only the required action; technical evidence belongs in the diagnostic export. Missing evidence must remain `UNKNOWN`.

### Deferred: controller boundary map (area 1)

Document the remaining `DragShareController` responsibilities and keep only one owner for each callback family. Extract code only when a pure boundary has a behavior test. Keep Root input, `UP/CANCEL`, payload ownership and URI/Bitmap release unchanged.

### R17: test matrix and release traceability

Add a single machine-readable verification record containing commit, version, APK hash, unit-test count, lint result and build tasks. Keep device-only checks explicitly pending. Do not treat a local build as a cloud CI result or a cross-app send confirmation.

## Device-dependent gate

After these offline rounds, use a real HyperOS device for the smallest high-value matrix: single/two-finger qualification, long-press menu interaction, IME plus freeform window, WeChat thumbnail drag, stash text/image drop, AICR OCR, and light/dark/blurred surfaces. Capture the last confirmed stage for every failure; do not infer the receiver's final send from `startActivity`, drop acceptance or a provider descriptor alone.
