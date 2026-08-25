# Contributing

AgentOS is in its architecture-validation stage. Before implementing a feature,
open an issue describing the user goal, required system capability, trust boundary,
and smallest testable result.

## Development checks

```bash
./tests/check.sh
gradle testDebugUnitTest assembleDebug
```

UI code is Kotlin with Jetpack Compose. Keep generated UI declarative, pass state
and callbacks into composables, and do not let model-controlled data call Android
APIs directly. New capabilities must have a typed identifier, scoped output, and a
test covering denial or unsupported input.

By contributing, you agree that your contribution is licensed under Apache-2.0.

