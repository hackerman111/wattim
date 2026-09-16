//! Ядро демона: подписка на события Hyprland + логика перемешивания,
//! плюс управление фоновым процессом (start/stop/status) через pid-файл.

use anyhow::{Context, Result};
use daemonize::Daemonize;
use rand::seq::SliceRandom;
use std::collections::HashMap;

use crate::config::{self, Config};
use crate::events::{self, Event};
use crate::hypr;

/// Определяет, нужно ли перемешивать предыдущее окно при переключении фокуса.
pub fn should_shuffle(
    is_guarded: bool,
    prev_addr: &str,
    prev_class: &str,
    new_addr: Option<&str>,
    new_class: Option<&str>,
) -> bool {
    if !is_guarded {
        return false;
    }

    match (new_addr, new_class) {
        // Фокус ушёл в пустоту (на пустой рабочий стол или все окна свернуты)
        (None, _) => true,
        // Фокус на том же самом окне — ничего не делаем
        (Some(addr), _) if addr == prev_addr => false,
        // Окно сменилось, но класс приложения тот же (диалог, просмотр фото/настройки внутри того же приложения)
        (Some(_), Some(class)) if class == prev_class => false,
        // Переключились на другое приложение
        (Some(_), _) => true,
    }
}

/// Выбирает случайный целевой рабочий стол из списка разрешённых,
/// по возможности исключая текущий стол, на котором находится окно.
pub fn pick_target_workspace(allowed: &[i32], current_ws: Option<i32>) -> Option<i32> {
    if allowed.is_empty() {
        return None;
    }
    let candidates: Vec<i32> = allowed
        .iter()
        .copied()
        .filter(|w| Some(*w) != current_ws)
        .collect();

    let pool = if candidates.is_empty() {
        allowed
    } else {
        &candidates
    };

    let mut rng = rand::thread_rng();
    pool.choose(&mut rng).copied()
}

/// Состояние демона между событиями.
pub struct State {
    pub cfg: Config,
    /// (адрес, класс) окна, которое было активно последний раз.
    pub active: Option<(String, String)>,
    /// Кэш адрес -> класс, чтобы не дёргать `hyprctl` на каждое переключение фокуса.
    pub class_cache: HashMap<String, String>,
}

impl State {
    pub fn new(cfg: Config) -> Self {
        let mut class_cache = HashMap::new();
        if let Ok(clients) = hypr::get_clients() {
            for c in &clients {
                class_cache.insert(hypr::norm_address(&c.address), c.class.clone());
            }
        }
        Self {
            cfg,
            active: None,
            class_cache,
        }
    }

    pub fn is_guarded(&self, class: &str) -> bool {
        self.cfg.guarded_apps.iter().any(|g| g.eq_ignore_ascii_case(class))
    }

    pub fn on_open(&mut self, address: &str, class: &str) {
        self.class_cache.insert(hypr::norm_address(address), class.to_string());
    }

    pub fn on_close(&mut self, address: &str) {
        let addr = hypr::norm_address(address);
        self.class_cache.remove(&addr);
        if self.active.as_ref().map(|(a, _)| a.as_str()) == Some(addr.as_str()) {
            self.active = None;
        }
    }

    /// Класс окна по адресу: сначала кэш, при промахе — запрос к Hyprland.
    pub fn class_of(&mut self, address: &str) -> Option<String> {
        let norm = hypr::norm_address(address);
        if let Some(c) = self.class_cache.get(&norm) {
            return Some(c.clone());
        }
        let clients = hypr::get_clients().ok()?;
        let found = hypr::find_client_by_address(&clients, &norm)?.class.clone();
        self.class_cache.insert(norm, found.clone());
        Some(found)
    }

    pub fn on_active_changed(&mut self, address: Option<String>) {
        let address = address.map(|a| hypr::norm_address(&a));
        let class = address.as_ref().and_then(|a| self.class_of(a));

        if let Some((prev_addr, prev_class)) = self.active.clone() {
            let is_guarded = self.is_guarded(&prev_class);
            let needs_shuffle = should_shuffle(
                is_guarded,
                &prev_addr,
                &prev_class,
                address.as_deref(),
                class.as_deref(),
            );

            if needs_shuffle {
                if let Err(e) = self.shuffle(&prev_addr) {
                    eprintln!("wattim: не удалось перемешать {prev_class} ({prev_addr}): {e:#}");
                }
            }
        }

        self.active = match (address, class) {
            (Some(a), Some(c)) => Some((a, c)),
            _ => None,
        };
    }

    /// Перемещает окно `address` на случайный рабочий стол из разрешённых.
    pub fn shuffle(&self, address: &str) -> Result<()> {
        let current_ws = hypr::get_clients()
            .ok()
            .and_then(|clients| hypr::find_client_by_address(&clients, address).map(|c| c.workspace.id));

        if let Some(target) = pick_target_workspace(&self.cfg.allowed_workspaces, current_ws) {
            hypr::move_window_silent(address, target)?;
            println!("wattim: окно {address} перемешано на рабочий стол {target}");
        }
        Ok(())
    }
}

/// Основной цикл: выполняется в текущем процессе до завершения Hyprland
/// или ошибки чтения из сокета событий.
pub fn run_foreground() -> Result<()> {
    if !hypr::is_hyprland_active() {
        anyhow::bail!("Hyprland не запущен или отсутствуют переменные сессии (HYPRLAND_INSTANCE_SIGNATURE)");
    }

    let cfg = config::load()?;
    if cfg.guarded_apps.is_empty() || cfg.allowed_workspaces.is_empty() {
        anyhow::bail!("Конфигурация пуста — запустите `wattim` без аргументов для первоначальной настройки");
    }
    println!(
        "wattim: демон запущен в foreground. Охраняемые приложения: [{}] на рабочих столах {:?}",
        cfg.guarded_apps.join(", "),
        cfg.allowed_workspaces
    );

    let mut state = State::new(cfg);

    events::listen(move |ev| match ev {
        Event::OpenWindow { address, class, .. } => state.on_open(&address, &class),
        Event::ActiveWindowV2 { address } => state.on_active_changed(address),
        Event::CloseWindow { address } => state.on_close(&address),
        Event::Other => {}
    })
}

// ---- управление фоновым процессом ----

pub fn start_background() -> Result<()> {
    if !hypr::is_hyprland_active() {
        anyhow::bail!("Hyprland не запущен или отсутствуют переменные сессии (HYPRLAND_INSTANCE_SIGNATURE)");
    }

    if is_running()? {
        println!("wattim уже запущен (pid {}).", read_pid()?.unwrap_or_default());
        return Ok(());
    }

    let cfg = config::load()?;
    if cfg.guarded_apps.is_empty() || cfg.allowed_workspaces.is_empty() {
        anyhow::bail!("Конфигурация пуста — сначала запустите `wattim` без аргументов для настройки");
    }

    let pid_path = config::pid_path()?;
    let log_path = config::log_path()?;
    std::fs::create_dir_all(config::config_dir()?)?;
    let stdout = std::fs::File::create(&log_path).context("не удалось создать лог-файл")?;
    let stderr = stdout.try_clone()?;

    let daemonize = Daemonize::new().pid_file(&pid_path).stdout(stdout).stderr(stderr);

    match daemonize.start() {
        Ok(_) => run_foreground(),
        Err(e) => Err(anyhow::anyhow!("не удалось запустить демон в фоне: {e}")),
    }
}

pub fn stop() -> Result<()> {
    let Some(pid) = read_pid()? else {
        println!("wattim не запущен.");
        return Ok(());
    };
    if !process_alive(pid) {
        println!("wattim не запущен (найден устаревший pid-файл, очищаю).");
        std::fs::remove_file(config::pid_path()?).ok();
        return Ok(());
    }
    let status = std::process::Command::new("kill").arg("-TERM").arg(pid.to_string()).status()?;
    std::fs::remove_file(config::pid_path()?).ok();
    if status.success() {
        println!("wattim (pid {pid}) успешно остановлен.");
    } else {
        println!("не удалось отправить сигнал завершения процессу {pid}.");
    }
    Ok(())
}

pub fn restart() -> Result<()> {
    stop().ok();
    start_background()
}

pub fn status() -> Result<()> {
    let cfg = config::load().unwrap_or_default();
    match read_pid()? {
        Some(pid) if process_alive(pid) => println!("● wattim запущен (pid {pid})."),
        _ => println!("○ wattim не запущен."),
    }
    println!(
        "Охраняемые приложения: {}",
        if cfg.guarded_apps.is_empty() { "—".to_string() } else { cfg.guarded_apps.join(", ") }
    );
    println!("Рабочие столы для перемешивания: {:?}", cfg.allowed_workspaces);
    Ok(())
}

pub fn read_pid() -> Result<Option<u32>> {
    let path = config::pid_path()?;
    if !path.exists() {
        return Ok(None);
    }
    let raw = std::fs::read_to_string(path)?;
    Ok(raw.trim().parse().ok())
}

pub fn is_running() -> Result<bool> {
    Ok(match read_pid()? {
        Some(pid) => process_alive(pid),
        None => false,
    })
}

fn process_alive(pid: u32) -> bool {
    std::path::Path::new(&format!("/proc/{pid}")).exists()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn should_shuffle_when_leaving_guarded_to_different_app() {
        let is_guarded = true;
        let prev_addr = "addr1";
        let prev_class = "telegram-desktop";
        let new_addr = Some("addr2");
        let new_class = Some("firefox");

        assert!(should_shuffle(is_guarded, prev_addr, prev_class, new_addr, new_class));
    }

    #[test]
    fn should_shuffle_when_focus_goes_to_empty_workspace() {
        let is_guarded = true;
        let prev_addr = "addr1";
        let prev_class = "telegram-desktop";

        assert!(should_shuffle(is_guarded, prev_addr, prev_class, None, None));
    }

    #[test]
    fn should_not_shuffle_when_staying_in_same_window() {
        let is_guarded = true;
        let prev_addr = "addr1";
        let prev_class = "telegram-desktop";

        assert!(!should_shuffle(is_guarded, prev_addr, prev_class, Some("addr1"), Some("telegram-desktop")));
    }

    #[test]
    fn should_not_shuffle_when_switching_to_same_app_window() {
        let is_guarded = true;
        let prev_addr = "addr1";
        let prev_class = "telegram-desktop";
        let new_addr = Some("addr3_dialog");
        let new_class = Some("telegram-desktop");

        assert!(!should_shuffle(is_guarded, prev_addr, prev_class, new_addr, new_class));
    }

    #[test]
    fn should_not_shuffle_if_app_was_not_guarded() {
        let is_guarded = false;
        let prev_addr = "addr1";
        let prev_class = "firefox";
        let new_addr = Some("addr2");
        let new_class = Some("telegram-desktop");

        assert!(!should_shuffle(is_guarded, prev_addr, prev_class, new_addr, new_class));
    }

    #[test]
    fn picks_different_workspace_if_possible() {
        let allowed = vec![1, 2, 3];
        for _ in 0..20 {
            let target = pick_target_workspace(&allowed, Some(2));
            assert!(target == Some(1) || target == Some(3));
        }
    }

    #[test]
    fn falls_back_to_same_if_only_one_allowed() {
        let allowed = vec![2];
        let target = pick_target_workspace(&allowed, Some(2));
        assert_eq!(target, Some(2));
    }
}
