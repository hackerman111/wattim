//! Инлайн-панель настройки: `wattim` без аргументов запускает именно это.
//! Рабочие столы демон определяет сам (запрашивает у Hyprland + предлагает 1..10),
//! а приложения вводятся с автодополнением по открытым окнам и установленным .desktop-файлам.

use anyhow::Result;
use inquire::{MultiSelect, Text};
use std::collections::{BTreeMap, BTreeSet};

use crate::autocomplete::AppClassCompleter;
use crate::config::{self, Config};
use crate::daemon;
use crate::desktop_apps;
use crate::hypr;

pub fn run() -> Result<Config> {
    println!("== wattim: настройка анти-думскроллинга ==");

    let existing = config::load().unwrap_or_default();

    // --- 1. Определение рабочих столов ---
    // Опрашиваем Hyprland на активные столы и объединяем со стандартным диапазоном 1..10
    let mut ws_map: BTreeMap<i32, Option<String>> = BTreeMap::new();

    // Добавляем стандартные воркспейсы 1..=10
    for id in 1..=10 {
        ws_map.insert(id, None);
    }

    // Дополняем информацией из живого Hyprland
    if let Ok(active_workspaces) = hypr::get_workspaces() {
        for ws in active_workspaces {
            ws_map.insert(ws.id, Some(ws.name));
        }
    }

    let ws_labels: Vec<String> = ws_map
        .into_iter()
        .map(|(id, name)| match name {
            Some(n) if n != id.to_string() => format!("{id} — активен ({n})"),
            Some(_) => format!("{id} — активен"),
            None => format!("{id}"),
        })
        .collect();

    // Предустановка выбранных значений (из сохраненного конфига или первых активных)
    let default_indices: Vec<usize> = ws_labels
        .iter()
        .enumerate()
        .filter(|(_, label)| {
            let id: i32 = label
                .split_whitespace()
                .next()
                .unwrap_or("")
                .parse()
                .unwrap_or(i32::MIN);
            if existing.allowed_workspaces.is_empty() {
                label.contains("активен") || (1..=5).contains(&id)
            } else {
                existing.allowed_workspaces.contains(&id)
            }
        })
        .map(|(i, _)| i)
        .collect();

    let chosen_ws = MultiSelect::new(
        "Выберите рабочие столы для перемешивания окон:",
        ws_labels,
    )
    .with_default(&default_indices)
    .with_help_message("Пробел — отметить/снять, Enter — подтвердить выбор")
    .prompt()?;

    let allowed_workspaces: Vec<i32> = chosen_ws
        .iter()
        .filter_map(|l| l.split_whitespace().next()?.parse().ok())
        .collect();

    if allowed_workspaces.len() < 2 {
        println!("⚠ Выбран только один рабочий стол: перемешивать будет фактически некуда.");
    }

    // --- 2. Приложения: автодополнение по открытым окнам + установленным .desktop ---
    let mut known_classes = BTreeSet::new();

    // 1) Открытые прямо сейчас окна
    if let Ok(clients) = hypr::get_clients() {
        for c in clients {
            if !c.class.is_empty() {
                known_classes.insert(c.class);
            }
        }
    }

    // 2) Установленные в системе программы (.desktop)
    for class in desktop_apps::discover_desktop_classes() {
        if !class.is_empty() {
            known_classes.insert(class);
        }
    }

    let completer = AppClassCompleter::new(known_classes.into_iter().collect());

    let mut guarded_apps = existing.guarded_apps.clone();
    println!("\nВведите приложения для охраны от думскроллинга.");
    println!("Используйте Tab для автодополнения (например: telegram-desktop, firefox, discord).");
    println!("Для завершения ввода оставьте строку пустой и нажмите Enter.\n");

    loop {
        let current_list = if guarded_apps.is_empty() {
            "пока пусто".to_string()
        } else {
            guarded_apps.join(", ")
        };

        let prompt = format!("Добавить приложение [выбрано: {current_list}]:");
        let input = Text::new(&prompt)
            .with_autocomplete(completer.clone())
            .with_help_message("Tab — дополнить имя, пусто + Enter — готово")
            .prompt_skippable()?;

        match input {
            None => break,
            Some(s) if s.trim().is_empty() => break,
            Some(s) => {
                let trimmed = s.trim().to_string();
                if !guarded_apps.iter().any(|a| a.eq_ignore_ascii_case(&trimmed)) {
                    println!("  + добавлено: {trimmed}");
                    guarded_apps.push(trimmed);
                } else {
                    println!("  (уже в списке)");
                }
            }
        }
    }

    if guarded_apps.is_empty() {
        println!("⚠ Список охраняемых приложений пуст. Демон не будет ничего перемешивать.");
    }

    let cfg = Config {
        guarded_apps,
        allowed_workspaces,
    };
    config::save(&cfg)?;

    println!(
        "\n[✓] Настройки сохранены: {} приложений под охраной, {} рабочих стола(ов).",
        cfg.guarded_apps.len(),
        cfg.allowed_workspaces.len()
    );

    // Автоматический запуск / перезапуск демона
    if !cfg.guarded_apps.is_empty() && !cfg.allowed_workspaces.is_empty() {
        println!("Запускаю фоновый демон...");
        match daemon::restart() {
            Ok(_) => {
                if let Ok(Some(pid)) = daemon::read_pid() {
                    println!("[✓] Демон wattim успешно запущен в фоне (PID: {pid}).");
                } else {
                    println!("[✓] Демон wattim запущен.");
                }
            }
            Err(e) => {
                eprintln!("⚠ Не удалось запустить демон в фоне: {e:#}");
                eprintln!("Вы можете запустить его вручную командой `wattim run`.");
            }
        }
    }

    Ok(cfg)
}
