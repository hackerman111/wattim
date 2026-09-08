# Recommended installation for Wattim

Copy the following to repository root:

```text
AGENTS.md
.agents/
```

For Wattim work, agents modifying these areas should additionally read `.agents/profiles/accessibility-protection.md`:

- `AppMonitorService` / AccessibilityService logic;
- intervention coordinator/state machine;
- overlay/window code;
- audio focus/media suppression;
- grants/emergency access;
- re-intervention/schedules/timers;
- foreground service;
- runtime policy state.

Recommended nested files after the architecture refactor:

```text
app/src/main/.../protection/AGENTS.md
app/src/main/.../overlay/AGENTS.md
app/src/main/.../data/AGENTS.md
app/src/main/.../ui/AGENTS.md
```

Base them on templates under `.agents/templates/` and add only Wattim-specific invariants. Do not copy the whole root guide into every subtree.

Suggested Wattim-specific invariants for `protection/AGENTS.md`:

- coordinator is the only writer of active protection session state;
- every active target session has `sessionId`;
- timers emit events and never show overlay directly;
- no Room/PackageManager/statistics I/O in foreground-decision hot path;
- no `currentForegroundPackage` string as a state machine;
- session permits are RAM-only; timed permits are persistent;
- schedule priority and overnight behavior are explicit and tested;
- adaptive accessibility subscription must remain event-driven and battery-aware.
