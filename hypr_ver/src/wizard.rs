//! Инлайн-панель настройки: `wattim` без аргументов запускает именно это.
//! Рабочие столы демон определяет сам (запрашивает у Hyprland), приложения
//! вводятся с автодополнением по реально открытым сейчас окнам.

use anyhow::Result;
use inquire::{Confirm, MultiSelect, Text};
use std::collections::BTreeSet;

use crate::autocomplete::AppClassCompleter;
use crate::config::{self, Config};
use crate::daemon;
use crate::hypr;

pub fn run() -> Result<Config> {
    println!("== wattim: настройка анти-думскроллинга ==");

    let existing = config::load().unwrap_or_default();

    // --- рабочие столы: список приходит от Hyprland, руками их не вводят ---
    let workspaces = hypr::get_workspaces()?;
    if workspaces.is_empty() {
        anyhow::bail!("Hyprland не сообщил ни одного рабочего стола");
    }
    let mut sorted = workspaces;
    sorted.sort_by_key(|w| w.id);
    let ws_labels: Vec<String> = sorted.iter().map(|w| format!("{} — {}", w.id, w.name)).collect();

    let default_indices: Vec<usize> = ws_labels
        .iter()
        .enumerate()
        .filter(|(_, label)| {
            let id: i32 = label.split(" — ").next().unwrap_or("").parse().unwrap_or(i32::MIN);
            existing.allowed_workspaces.contains(&id)
        })
        .map(|(i, _)| i)
        .collect();

    let chosen = MultiSelect::new(
        "Между какими рабочими столами можно перемешивать охраняемые окна?",
        ws_labels,
    )
    .with_default(&default_indices)
    .with_help_message("space — отметить, enter — подтвердить")
    .prompt()?;

    let allowed_workspaces: Vec<i32> = chosen
        .iter()
        .filter_map(|l| l.split(" — ").next()?.parse().ok())
        .collect();

    if allowed_workspaces.len() < 2 {
        println!("⚠ выбран только один рабочий стол — перемешивать будет фактически некуда.");
    }

    // --- приложения: автодополнение по реально открытым сейчас окнам ---
    let clients = hypr::get_clients().unwrap_or_default();
    let known_classes: Vec<String> =
        clients.iter().map(|c| c.class.clone()).collect::<BTreeSet<_>>().into_iter().collect();

    if known_classes.is_empty() {
        println!("Сейчас не вижу открытых окон — можно ввести класс приложения вручную.");
    }

    let mut guarded_apps = existing.guarded_apps.clone();
    loop {
        let prompt = format!(
            "Добавить приложение для охраны (Tab — автодополнение, пусто + Enter — закончить). Уже выбрано: [{}]",
            guarded_apps.join(", ")
        );
        let input = Text::new(&prompt)
            .with_autocomplete(AppClassCompleter::new(known_classes.clone()))
            .with_help_message("класс окна (WM_CLASS), например: telegram-desktop, firefox, discord")
            .prompt_skippable()?;

        match input {
            None => break,
            Some(s) if s.trim().is_empty() => break,
            Some(s) => {
                let s = s.trim().to_string();
                if !guarded_apps.contains(&s) {
                    guarded_apps.push(s);
                }
            }
        }
    }

    let cfg = Config { guarded_apps, allowed_workspaces };
    config::save(&cfg)?;

    println!(
        "Сохранено: {} приложений под охраной, {} рабочих стола(ов) для перемешивания.",
        cfg.guarded_apps.len(),
        cfg.allowed_workspaces.len()
    );

    Ok(cfg)
}

pub fn maybe_restart_daemon(cfg: &Config) -> Result<()> {
    if cfg.guarded_apps.is_empty() || cfg.allowed_workspaces.is_empty() {
        println!("Демон не запущен: нужно указать хотя бы одно приложение и один рабочий стол.");
        return Ok(());
    }
    let restart = Confirm::new("Перезапустить демон wattim с новыми настройками сейчас?")
        .with_default(true)
        .prompt()?;
    if restart {
        daemon::restart()?;
    }
    Ok(())
}
