# MMGG architecture roadmap

This roadmap is for work that can be completed without a connected device. It does not replace `CURRENT_STATUS.md`; that file remains the source for current facts.

## Four functional areas (2026-11-05 audit)

The project is organized around four functional areas. Package layout already separates gestures code; the `codex` package still mixes area 1, area 4 and shared UI infrastructure, which the rounds below address by ownership rather than mass file moves.

| Area | Scope | Main code (current location) | Size (files/lines) |
|---|---|---|---|
| 1 分词与拖拽 | Press capture, segmentation, drag, share targets, stash, WeChat adaptation, OCR/recognize | `codex` press/drag/bigbang/stash/portal files, `gestures` root input readers | 73 files / ~22.9k lines |
| 2 手势增强 | Corner/honeycomb triggers, outside gestures, gesture config | `gestures/xposed` Fan* + Honeycomb*, `gestures/config` | 60 files / ~14.0k lines |
| 3 小窗增强 | HyperOS freeform bridge, mini windows, shortcut host, media controls | `gestures/xposed` HyperOsFreeformBridge, ShortcutHostRuntime, MiniMediaControls, HyperOsProfile | 16 files / ~5.1k lines |
| 4 其他功能 | Settings UI, activation/diagnostics, touch probe reports, app update, copy direct, licenses | `codex` settings/diagnostics/touchprobe/update pages | 34 files / ~10.4k lines |

Cross-cutting shared UI (`ModernOverlayViews`, `OverlayPanelUi`, `InteractiveOverlayWindow`, `NativeGlassBackdrop`, `Os3BackgroundEffect`) serves areas 1 and 2; it is treated as shared infrastructure, not a fifth area.

Hotspots by size (2026-11-05, after E1–E6): `DragShareController` 2506, `FanRuntime` 2289, `HyperOsFreeformBridge` 1813, `DragShareSettings` 1378, `ConfigStore` 1377, `SettingsScreen` 1136, `HoneycombOverlayView` 1113, `FanFreeformHook` 979, `PressCaptureSource` 839.

## Completed offline work

- Fan runtime eligibility, target resolution, configuration loading and freeform geometry are isolated behind small decision boundaries.
- Settings navigation and effect notices are isolated without changing keys, defaults or reload timing.
- Drag session and preview callback ownership use lifecycle tokens and generation checks.
- Freeform queued launches use request generations and explicit `QUEUED`, `REQUESTED`, `OBSERVED` and `UNKNOWN` evidence stages.
- Activation presentation keeps `UNKNOWN` separate from missing permission.
- The latest offline follow-up added retry budget and stale same-task generation tests. No production behavior was changed because the current guards were already complete.

## Next device-independent rounds

### E1 (area 1): WeChat press candidate owner — DONE 2026-11-05

The OCR candidate state introduced in commit `0992141` is now generalized as `PressOcrFlow`: text delivery and screen-capture rejection live in a source-neutral owner. Generation checks, Root authority and Bitmap/URI ownership stay in `PressCaptureSource`.

### E2 (area 2): FanRuntime remaining responsibilities — DONE 2026-11-05

The side swipe and task switcher paths were removed from the runtime. `FanRuntime` now owns only corner and honeycomb arbitration; outside gesture handling remains separate.

### E3 (area 3): freeform request value object — DONE 2026-11-05

Done as `QueuedLaunchQueue` (commit `046cecb`): queued target/external launch payload, task id and generation live in one owner with atomic `begin/current/consume/clear/nextRetry` operations. The retry policy, task matching, timeout and private HyperOS calls are unchanged; the two launch paths remain separate.

### E4 (area 4): diagnostic capability projection — VERIFIED 2026-11-05

Re-checked: capability collection (`RootConnectionDiagnostics`, `GestureDiagnostics`, `DragShareDiagnostics`), evidence interpretation and user-facing presentation are already separate classes; no extraction was needed. Missing evidence remains `UNKNOWN` in the status model.

### E5 (area 2/3): freeform hook probe helpers — DONE 2026-11-05

Done as `FreeformHookProbe` (commit `74abeec`): classloader probing and reflection summaries moved out of `FanFreeformHook` with unit tests. Hook installation order, candidate constants and private HyperOS calls are unchanged.

### E6 (area 1): modern overlay prewarm owner — DONE 2026-11-05

Done as `ModernOverlayPrewarm` (commit `7e50e03`): the warm-up overlay window lifecycle (schedule, first-draw finish, teardown, failure cleanup) is a standalone owner. Destroy-time disposal semantics and log messages are preserved; `DragShareExitLifecycleTest` guards the teardown order.

### Deferred: controller boundary map (area 1)

Document the remaining `DragShareController` responsibilities and keep only one owner for each callback family. Extract code only when a pure boundary has a behavior test. Keep Root input, `UP/CANCEL`, payload ownership and URI/Bitmap release unchanged.

Current extraction state after E1–E6: gesture trace recording, press release mode, pending preview gesture, WeChat press flow, modern overlay prewarm, drag actions and operation tracing all have named owners. The controller remains the single owner of gesture state transitions, Root UP/CANCEL semantics and window/menu lifecycle.

### R17: test matrix and release traceability — DONE 2026-11-05

Done as `docs/VERIFICATION.json`: one machine-readable record holding commit, version, APK hash, unit-test counts, lint results and executed build tasks. Device-only checks stay explicitly `PENDING`; a local build is never recorded as a cloud CI result or a cross-app send confirmation.

Add a single machine-readable verification record containing commit, version, APK hash, unit-test count, lint result and build tasks. Keep device-only checks explicitly pending. Do not treat a local build as a cloud CI result or a cross-app send confirmation.

## Device-dependent gate

After these offline rounds, use a real HyperOS device for the smallest high-value matrix: single/two-finger qualification, long-press menu interaction, IME plus freeform window, WeChat thumbnail drag, stash text/image drop, AICR OCR, and light/dark/blurred surfaces. Capture the last confirmed stage for every failure; do not infer the receiver's final send from `startActivity`, drop acceptance or a provider descriptor alone.
