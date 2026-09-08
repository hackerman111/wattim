# UI design-engineering review workflow

1. Identify the primary user task and visual hierarchy.
2. Compare screen spacing, typography, color, shape, and components to the design system and neighboring screens.
3. Check all interaction states and 48dp targets.
4. Test dark theme and large font scale.
5. Test compact width and at least one wider/adaptive layout if supported.
6. Check system bars, cutouts, IME, and edge-to-edge.
7. Verify loading/error/empty/disabled states.
8. Remove ornamental cards/borders/gradients/shadows that do not communicate structure.
9. Inspect Compose state ownership and recomposition only after visual correctness.
10. Add/update previews or screenshot tests for meaningful states.
