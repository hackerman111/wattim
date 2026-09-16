# Hyprland Wattim Daemon Implementation Plan

> **For Antigravity:** REQUIRED SUB-SKILL: Load executing-plans to implement this plan task-by-task.

**Goal:** Build a lightweight Hyprland anti-doomscrolling daemon (`wattim`) with an inline terminal UI (`inquire`), auto-detection of workspaces, application autocompletion, and silent workspace shuffling on app abandonment.

**Architecture:** A native Rust daemon connecting directly to Hyprland's IPC unix sockets (`.socket.sock` and `.socket2.sock`). Tracks focus transitions in-memory; when a user switches away from a guarded app, the app is moved via `movetoworkspacesilent` to a random allowed workspace. Provides an interactive CLI wizard for zero-config setup and subcommands for daemon management.

**Tech Stack:** Rust 2021, Hyprland IPC sockets, `inquire` for inline CLI TUI, `daemonize` for background daemon management, `serde` / `toml` for config.

---

### Task 1: Reorganize project into standard Cargo structure and configure dependencies

**Files:**
- Modify: `hypr_ver/Cargo.toml`
- Move/Create: `hypr_ver/src/` structure

**Step 1:** Verify/adjust `hypr_ver/Cargo.toml` dependencies and setup `hypr_ver/src/` directory.
**Step 2:** Ensure `Cargo.toml` specifies `src/main.rs`.

---

### Task 2: Desktop application scanner for autocompletion

**Files:**
- Create: `hypr_ver/src/desktop_apps.rs`

**Step 1: Write unit tests for .desktop file parsing**
- Test parsing `StartupWMClass`, `Name`, `Exec`.
**Step 2: Run test to verify it fails**
**Step 3: Implement `desktop_apps.rs`**
- Scan `/usr/share/applications` and `~/.local/share/applications`.
- Parse entry keys and return a deduplicated vector of classes/names.
**Step 4: Run test to verify it passes**

---

### Task 3: Autocomplete component

**Files:**
- Create: `hypr_ver/src/autocomplete.rs`

**Step 1: Write test for `AppClassCompleter`**
- Test matching substring case-insensitively and completing suggestions.
**Step 2: Run test to verify it fails**
**Step 3: Implement `autocomplete.rs`**
**Step 4: Run test to verify it passes**

---

### Task 4: Hyprland IPC & Event streaming

**Files:**
- Create: `hypr_ver/src/hypr.rs`
- Create: `hypr_ver/src/events.rs`

**Step 1: Write unit tests for event line parsing and address normalization**
- Test `openwindow`, `closewindow`, `activewindowv2`.
**Step 2: Implement `events.rs` and `hypr.rs`**
**Step 3: Run tests to verify they pass**

---

### Task 5: Daemon state machine, focus tracking & shuffle logic

**Files:**
- Create: `hypr_ver/src/config.rs`
- Create: `hypr_ver/src/daemon.rs`

**Step 1: Write unit tests for `State` transition logic**
- Verify that leaving a guarded app triggers shuffle.
- Verify that switching within the same guarded app does NOT trigger shuffle.
- Verify that switching to an empty desktop (`None`) triggers shuffle.
**Step 2: Implement `daemon.rs`**
- In-memory client cache.
- `on_active_changed` with app protection.
- `shuffle` silent dispatch.
- Background process lifecycle (`start_background`, `stop`, `restart`, `status`, `run_foreground`).
**Step 3: Run tests to verify they pass**

---

### Task 6: Interactive wizard and CLI entrypoint

**Files:**
- Create: `hypr_ver/src/wizard.rs`
- Create: `hypr_ver/src/main.rs`

**Step 1: Implement `wizard.rs`**
- Query active workspaces from Hyprland and combine with standard 1..10 range.
- `MultiSelect` prompt for workspaces.
- Input loop for guarded applications with `AppClassCompleter`.
- Save config and automatically invoke `daemon::restart()`.
**Step 2: Implement `main.rs`**
- Parse CLI arguments (`start`, `stop`, `restart`, `status`, `run`, `--help`).
- Default (no arguments): execute `wizard::run()`.
**Step 3: Build and test full CLI**
- Run `cargo test --manifest-path hypr_ver/Cargo.toml`.
- Run `cargo build --manifest-path hypr_ver/Cargo.toml`.
