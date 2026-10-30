# MMGG architecture roadmap

This roadmap is for work that can be completed without a connected device. It does not replace `CURRENT_STATUS.md`; that file remains the source for current facts.

## Completed offline work

- Fan runtime eligibility, target resolution, configuration loading and freeform geometry are isolated behind small decision boundaries.
- Settings navigation and effect notices are isolated without changing keys, defaults or reload timing.
- Drag session and preview callback ownership use lifecycle tokens and generation checks.
- Freeform queued launches use request generations and explicit `QUEUED`, `REQUESTED`, `OBSERVED` and `UNKNOWN` evidence stages.
- Activation presentation keeps `UNKNOWN` separate from missing permission.
- The latest offline follow-up added retry budget and stale same-task generation tests. No production behavior was changed because the current guards were already complete.

## Next device-independent rounds

### R14: controller boundary map

Document the remaining `DragShareController` responsibilities and keep only one owner for each callback family. Extract code only when a pure boundary has a behavior test. Keep Root input, `UP/CANCEL`, payload ownership and URI/Bitmap release unchanged.

### R15: freeform request value object

If duplication continues to grow, introduce a small internal request record for queued target launches and external intents. It must preserve the existing retry policy, task matching, timeout and private HyperOS calls. Do not merge the two launch paths until tests prove their behavior is equivalent.

### R16: diagnostic capability projection

Keep capability collection, evidence interpretation and user-facing presentation separate. The home page should expose only the required action; technical evidence belongs in the diagnostic export. Missing evidence must remain `UNKNOWN`.

### R17: test matrix and release traceability

Add a single machine-readable verification record containing commit, version, APK hash, unit-test count, lint result and build tasks. Keep device-only checks explicitly pending. Do not treat a local build as a cloud CI result or a cross-app send confirmation.

## Device-dependent gate

After these offline rounds, use a real HyperOS device for the smallest high-value matrix: single/two-finger qualification, long-press menu interaction, IME plus freeform window, WeChat thumbnail drag, stash text/image drop, AICR OCR, and light/dark/blurred surfaces. Capture the last confirmed stage for every failure; do not infer the receiver's final send from `startActivity`, drop acceptance or a provider descriptor alone.
