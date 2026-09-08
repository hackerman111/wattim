# UI design-engineering standards

The goal is a coherent product interface, not merely valid Compose code.

## Visual hierarchy

Every screen must have a clear hierarchy:

1. primary task/content;
2. primary action if one exists;
3. secondary controls;
4. metadata/supporting text.

Do not give every element equal visual weight.

## Layout rhythm

Use a consistent spacing scale, normally based on 4dp increments with common 8/12/16/24/32dp steps.

Do not scatter arbitrary values such as 13dp, 19dp, 27dp without a design reason.

Use alignment and whitespace before adding borders/cards/dividers.

## Density

Avoid both extremes:

- dense terminal-like walls with insufficient touch/reading space;
- oversized card stacks that hide little information behind excessive padding.

For productivity/utility apps, prioritize scanability and information density while preserving 48dp interactive targets and readable line height.

## Typography

Define semantic typography roles. Avoid arbitrary font-size changes per screen.

Maintain:

- readable body size/line height;
- restrained number of weights;
- clear title/body/label distinction;
- tabular figures where rapidly changing numeric values benefit;
- ellipsis/wrapping rules for constrained widths.

Test font scaling. Do not solve clipping by globally disabling scaling.

## Color

Use semantic tokens such as:

- background/surface;
- primary/accent;
- on-surface;
- muted/secondary text;
- success/warning/error;
- outline/divider.

Do not encode meaning by color alone.

Support dark mode with independently checked contrast; do not simply invert colors.

## Components

Create/reuse design-system components for recurring semantic concepts such as primary button, destructive action, setting row, status chip, top bar, dialog, card, metric row.

Do not wrap every `Row`, `Text`, or `Spacer` in a custom component.

Component APIs expose semantic intent rather than dozens of styling parameters.

## Shape and elevation

Use a small, consistent corner-radius vocabulary.

Do not make every container a rounded card. Group through spacing and typography first.

Use elevation/shadow only to communicate layering or interaction, not decoration.

## Icons

Use a consistent icon family and optical size.

Do not use emoji as functional icons.

Icon-only actions require an accessible label and sufficient target area.

## Interaction states

Every actionable control must define applicable states:

- default;
- pressed;
- focused;
- selected;
- disabled;
- loading.

Avoid controls that visually disappear or jump when entering loading state unless layout change is intentional.

## Feedback

Immediate local action should produce immediate local feedback.

Do not show a global snackbar for every small state change. Reserve transient messages for confirmation/errors that are not already visible in state.

Destructive/irreversible actions require proportionate confirmation, undo, or clear consequence text.

## Dialogs and sheets

Use dialogs/sheets only when interruption/context retention justifies them. Do not turn ordinary navigation into nested modal flows.

Keep action ordering and destructive emphasis consistent across the app.

## Adaptive layout

Design for available window size, not device labels.

Consider compact/medium/expanded layouts and foldables/tablets when the product supports them.

Avoid fixed widths that look correct only on one phone.

## System integration

Handle:

- edge-to-edge;
- status/navigation bars;
- display cutouts;
- IME resize/insets;
- gesture navigation;
- orientation/window resize where supported.

## Accessibility

- minimum 48dp interactive targets;
- useful semantics and focus order;
- no information conveyed solely by color;
- sufficient contrast;
- large-font resilience;
- TalkBack traversal for complex screens;
- respect system animation scale/reduced-motion expectations where applicable.

## Motion

Motion explains state change or hierarchy. Keep it short and purposeful.

Avoid simultaneous decorative animations competing for attention.

High-frequency animation must not cause broad recomposition.

## Error/empty/loading quality

Do not ship placeholder-level states.

Each state should answer:

- what happened;
- what the user can do;
- whether retry is possible;
- whether existing data remains usable.

## UI review questions

Before finishing a screen ask:

- Can the primary task be identified in 2 seconds?
- Are spacing/typography/components consistent with neighboring screens?
- Does it work at large font scale and dark theme?
- Are touch targets reliable?
- Is any card/border/gradient purely ornamental?
- Are loading/error/empty states complete?
- Does animation communicate something useful?
