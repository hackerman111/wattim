# Jetpack Compose engineering

## Layering

Split screen entry from reusable UI:

```kotlin
@Composable
fun FooRoute(viewModel: FooViewModel, ...) { ... }

@Composable
fun FooScreen(
    state: FooUiState,
    onAction: (FooAction) -> Unit,
) { ... }
```

`FooScreen` should be previewable/testable without DI, navigation controller, repository, or Android service objects.

## UI state

- Prefer one immutable screen state model over many unrelated `StateFlow`s exposed to the screen.
- Model mutually exclusive states explicitly.
- UI actions should be typed when a screen has nontrivial behavior.
- Do not place navigation/resource/context objects inside immutable UI state.

## State hoisting

Keep state at the lowest common owner that must coordinate it.

Local ephemeral UI state may remain in Compose. Durable/business state belongs in a state holder.

Do not push every animation expansion flag into a ViewModel.

## Side effects

Use Compose side-effect APIs according to lifecycle semantics:

- `LaunchedEffect` for key-scoped suspending effects;
- `DisposableEffect` for acquire/release;
- `rememberUpdatedState` when a long-lived effect needs the latest callback/value;
- avoid firing business mutations directly during composition.

## Lists

- stable unique keys;
- content type when heterogeneous lists benefit;
- avoid rebuilding expensive mapped/sorted lists inside item lambdas;
- keep item state identity stable.

## Stability

Prefer immutable UI models.

Do not mechanically annotate `@Stable`/`@Immutable`. Use compiler reports when stability causes measured recomposition problems.

## High-frequency state

Animation/scroll state can update faster than semantic UI.

- derive coarse semantic values with `derivedStateOf` when appropriate;
- defer high-frequency reads to layout/draw;
- update text only at the precision users can perceive/need;
- avoid triggering entire-screen recomposition for a canvas progress value.

## Resources

Do not hardcode user-visible strings. Use resources/localization.

Use design-system tokens for semantic colors, typography, shapes, dimensions, and motion when available.

## Preview coverage

For reusable visual components/screens include representative previews where practical:

- normal;
- dark theme;
- large font;
- loading/empty/error;
- compact and wider layout if adaptive behavior exists.

## Semantics

Set content descriptions only when they add information not already conveyed by accessible text. Use headings, roles, state descriptions, and merge semantics deliberately.
