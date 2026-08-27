# Contributing

AgentOS is in its architecture-validation stage. Before implementing a feature,
open an issue describing the user goal, required system capability, trust boundary,
and smallest testable result.

## Development checks

Start with the [developer preview](docs/developer-preview.md) and
[small contribution tasks](docs/contribution-starters.md). You do not need a full
AOSP build server to work on component tests, native UI or device reports.

```bash
./tests/check.sh
python3 tests/check_preview_tools.py
gradle testDebugUnitTest assembleDebug
```

UI code is Kotlin with Jetpack Compose. Keep generated UI declarative, pass state
and callbacks into composables, and do not let model-controlled data call Android
APIs directly. New capabilities must have a typed identifier, scoped output, and a
test covering denial or unsupported input.

Communication changes must preserve native review, recipient/SIM binding, expiry,
one-time execution, lock-screen redaction and no automatic retry. Incoming SMS and
call content are data, not instructions. Never publish real numbers or message bodies.

Public 3D images must use the two maintainer-approved concepts in
`docs/images/ui-v2/`, clearly labeled as design targets. Keep runtime screenshots,
recordings and replacement experiments in ignored `artifacts/`; do not attach them
to GitHub issues, pull requests, releases or Actions artifacts without new approval.

By contributing, you agree that your contribution is licensed under Apache-2.0.
