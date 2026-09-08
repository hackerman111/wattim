# Design-system-local agent rules

- This subtree defines reusable semantic UI primitives and tokens.
- It must not depend on feature or data modules.
- Components expose semantic variants rather than arbitrary per-call styling knobs.
- Maintain a small consistent vocabulary for typography, spacing, shapes, color roles, elevation, and motion.
- Every interactive component supports disabled/pressed/focused/selected/loading states as applicable and minimum 48dp target size.
- Support light/dark themes, large fonts, accessibility semantics, and adaptive constraints.
- Add catalog/previews for component states and screenshot tests when infrastructure exists.
- Do not add a component for a single one-off layout with no stable semantic meaning.
