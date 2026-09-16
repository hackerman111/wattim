//! Инлайн-панель настройки: `wattim` без аргументов запускает именно это.
//! Позволяет выбирать и отменять выбор рабочих столов и приложений,
//! а также отменять всю операцию в любой момент (Esc/Ctrl+C или подтверждение).

use anyhow::Result;
use inquire::{Confirm, InquireError, MultiSelect, Text};
use std::collections::{BTreeMap, BTreeSet};

use crate::autocomplete::AppClassCompleter;
use crate::config::{self, Config};
use crate::daemon;
use crate::desktop_apps;
use crate::hypr;

pub fn run() -> Result<Config> {
    println!("== wattim: настройка анти-думскроллинга ==");
    println!("Подсказка: в любой момент нажмите Esc для отмены без сохранения изменений.\n");

    let existing = config::load().unwrap_or_default();

    // --- 1. Определение и выбор рабочих столов ---
    let mut ws_map: BTreeMap<i32, Option<String>> = BTreeMap::new();
    for id in 1..=10 {
        ws_map.insert(id, None);
    }
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

    let chosen_ws = match MultiSelect::new(
        "Выберите рабочие столы для перемешивания окон:",
        ws_labels,
    )
    .with_default(&default_indices)
    .with_help_message("Пробел — отметить/отменить выбор, Enter — подтвердить, Esc — отмена")
    .prompt()
    {
        Ok(v) => v,
        Err(InquireError::OperationCanceled | InquireError::OperationInterrupted) => {
            println!("\n[!] Настройка отменена. Изменения не сохранены.");
            return Ok(existing);
        }
        Err(e) => return Err(e.into()),
    };

    let allowed_workspaces: Vec<i32> = chosen_ws
        .iter()
        .filter_map(|l| l.split_whitespace().next()?.parse().ok())
        .collect();

    if allowed_workspaces.len() < 2 {
        println!("⚠ Выбран только один рабочий стол: перемешивать будет фактически некуда.");
    }

    // --- 2. Приложения: управление и отмена выбора ---
    let mut guarded_apps = existing.guarded_apps.clone();

    // Если уже были сохранённые приложения — даём возможность отменить их выбор через MultiSelect
    if !guarded_apps.is_empty() {
        let all_indices: Vec<usize> = (0..guarded_apps.len()).collect();
        match MultiSelect::new(
            "Текущие охраняемые приложения (снимите отметку пробелом для отмены выбора):",
            guarded_apps.clone(),
        )
        .with_default(&all_indices)
        .with_help_message("Пробел — включить/отменить выбор, Enter — подтвердить список")
        .prompt()
        {
            Ok(selected) => {
                guarded_apps = selected;
            }
            Err(InquireError::OperationCanceled | InquireError::OperationInterrupted) => {
                println!("\n[!] Настройка отменена. Изменения не сохранены.");
                return Ok(existing);
            }
            Err(e) => return Err(e.into()),
        }
    }

    // Собираем кандидатов для автодополнения
    let mut known_classes = BTreeSet::new();
    if let Ok(clients) = hypr::get_clients() {
        for c in clients {
            if !c.class.is_empty() {
                known_classes.insert(c.class);
            }
        }
    }
    for class in desktop_apps::discover_desktop_classes() {
        if !class.is_empty() {
            known_classes.insert(class);
        }
    }
    let completer = AppClassCompleter::new(known_classes.into_iter().collect());

    println!("\nДобавление / удаление приложений.");
    println!("Подсказки: Tab — автодополнение, '-имя' — удалить/отменить выбор, пусто + Enter — готово.\n");

    loop {
        let current_list = if guarded_apps.is_empty() {
            "пока пусто".to_string()
        } else {
            guarded_apps.join(", ")
        };

        let prompt = format!("Добавить приложение [выбрано: {current_list}]:");
        let input = match Text::new(&prompt)
            .with_autocomplete(completer.clone())
            .with_help_message("Tab — дополнить, '-имя' — отменить выбор, пусто + Enter — закончить, Esc — отмена")
            .prompt_skippable()
        {
            Ok(v) => v,
            Err(InquireError::OperationCanceled | InquireError::OperationInterrupted) => {
                println!("\n[!] Настройка отменена. Изменения не сохранены.");
                return Ok(existing);
            }
            Err(e) => return Err(e.into()),
        };

        match input {
            None => break,
            Some(s) if s.trim().is_empty() => break,
            Some(s) => {
                let trimmed = s.trim();

                // Проверка на команду отмены/удаления с префиксом '-'
                if let Some(to_remove) = trimmed.strip_prefix('-') {
                    let to_remove = to_remove.trim();
                    let before_len = guarded_apps.len();
                    guarded_apps.retain(|a| !a.eq_ignore_ascii_case(to_remove));
                    if guarded_apps.len() < before_len {
                        println!("  - выбор отменён: {to_remove}");
                    } else {
                        println!("  (приложение '{to_remove}' не найдено в выбранных)");
                    }
                    continue;
                }

                // Если уже есть в списке — предлагаем отменить выбор
                if let Some(pos) = guarded_apps.iter().position(|a| a.eq_ignore_ascii_case(trimmed)) {
                    let confirm_remove = match Confirm::new(&format!(
                        "Приложение '{}' уже выбрано. Отменить его выбор (удалить из списка)?",
                        guarded_apps[pos]
                    ))
                    .with_default(false)
                    .prompt()
                    {
                        Ok(ans) => ans,
                        Err(InquireError::OperationCanceled | InquireError::OperationInterrupted) => {
                            println!("\n[!] Настройка отменена. Изменения не сохранены.");
                            return Ok(existing);
                        }
                        Err(e) => return Err(e.into()),
                    };

                    if confirm_remove {
                        let removed = guarded_apps.remove(pos);
                        println!("  - выбор отменён: {removed}");
                    }
                } else {
                    println!("  + добавлено: {trimmed}");
                    guarded_apps.push(trimmed.to_string());
                }
            }
        }
    }

    let cfg = Config {
        guarded_apps,
        allowed_workspaces,
    };

    // --- 3. Финальное подтверждение сохранения настроек ---
    println!("\nИтоговая конфигурация:");
    println!("  Рабочие столы: {:?}", cfg.allowed_workspaces);
    println!(
        "  Охраняемые приложения: [{}]",
        if cfg.guarded_apps.is_empty() {
            "нет".to_string()
        } else {
            cfg.guarded_apps.join(", ")
        }
    );

    let confirm_save = match Confirm::new("Сохранить настройки и применить к демону?")
        .with_default(true)
        .with_help_message("Enter — сохранить, 'n' или Esc — отменить")
        .prompt()
    {
        Ok(v) => v,
        Err(InquireError::OperationCanceled | InquireError::OperationInterrupted) => false,
        Err(e) => return Err(e.into()),
    };

    if !confirm_save {
        println!("[!] Настройка отменена пользователем. Прежняя конфигурация сохранена без изменений.");
        return Ok(existing);
    }

    config::save(&cfg)?;
    println!("\n[✓] Настройки сохранены в ~/.config/wattim/config.toml");

    // Автоматический перезапуск фонового демона
    if !cfg.guarded_apps.is_empty() && !cfg.allowed_workspaces.is_empty() {
        println!("Перезапускаю фоновый демон...");
        if let Err(e) = daemon::restart() {
            eprintln!("⚠ Не удалось запустить демон в фоне: {e:#}");
            eprintln!("Вы можете запустить его вручную командой `wattim run`.");
        }
    } else {
        println!("Демон остановлен, так как список приложений или столов пуст.");
        daemon::stop().ok();
    }

    Ok(cfg)
}
