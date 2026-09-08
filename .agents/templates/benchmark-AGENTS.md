# Benchmark-local agent rules

- Benchmarks measure release-like behavior; do not benchmark debug-only artifacts.
- Each benchmark must state the critical path and setup assumptions.
- Stabilize input/data and avoid measuring unrelated initialization unless startup itself is the target.
- Record device/API/build variant and representative baseline/result.
- Macrobenchmarks cover user journeys/startup/frame timing; microbenchmarks cover isolated algorithms only.
- Baseline profile generators should follow real critical user paths.
- Performance claims outside measurement noise require repeated samples.
