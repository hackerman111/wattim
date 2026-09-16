//! Ядро демона: подписка на события Hyprland + логика перемешивания,
//! плюс управление фоновым процессом (start/stop/status) через pid-файл.

use anyhow::{Context, Result};
use daemonize::Daemonize;
use rand::seq::SliceRandom;
use std::collections::HashMap;

use crate::config::{self, Config};
use crate::events::{self, Event};
use crate::hypr;

/// Состояние демона между событиями.
struct State {
    cfg: Config,
    /// (адрес, класс) окна, которое было активно последний раз.
    active: Option<(String, String)>,
    /// Кэш адрес -> класс, чтобы не дёргать `hyprctl` на каждое переключение фокуса.
    class_cache: HashMap<String, String>,
}

impl State {
    fn new(cfg: Config) -> Self {
        let mut class_cache = HashMap::new();
        if let Ok(clients) = hypr::get_clients() {
            for c in &clients {
                class_cache.insert(hypr::norm_address(&c.address), c.class.clone());
            }
        }
        Self { cfg, active: None, class_cache }
    }

    fn is_guarded(&self, class: &str) -> bool {
        self.cfg.guarded_apps.iter().any(|g| g == class)
    }

    fn on_open(&mut self, address: &str, class: &str) {
        self.class_cache.insert(hypr::norm_address(address), class.to_string());
    }

    fn on_close(&mut self, address: &str) {
        let addr = hypr::norm_address(address);
        self.class_cache.remove(&addr);
        if self.active.as_ref().map(|(a, _)| a.as_str()) == Some(addr.as_str()) {
            self.active = None;
        }
    }

    /// Класс окна по адресу: сначала кэш, при промахе — живой запрос к Hyprland
    /// (например, окно уже существовало на момент старта демона, но `openwindow`
    /// на него, разумеется, не приходил).
    fn class_of(&mut self, address: &str) -> Option<String> {
        if let Some(c) = self.class_cache.get(address) {
            return Some(c.clone());
        }
        let clients = hypr::get_clients().ok()?;
        let found = hypr::find_client_by_address(&clients, address)?.class.clone();
        self.class_cache.insert(address.to_string(), found.clone());
        Some(found)
    }

    fn on_active_changed(&mut self, address: Option<String>) {
        let address = address.map(|a| hypr::norm_address(&a));
        let class = address.as_ref().and_then(|a| self.class_of(a));

        if let Some((prev_addr, prev_class)) = self.active.clone() {
            let left_a_guarded_app = self.is_guarded(&prev_class);
            let switched_to_something_else = address.as_deref() != Some(prev_addr.as_str());
            if left_a_guarded_app && switched_to_something_else {
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

    /// Перемещает окно `address` на случайный рабочий стол из разрешённых,
    /// по возможности не тот, на котором оно уже находится.
    fn shuffle(&self, address: &str) -> Result<()> {
        if self.cfg.allowed_workspaces.is_empty() {
            return Ok(());
        }
        let clients = hypr::get_clients()?;
        let current_ws = hypr::find_client_by_address(&clients, address).map(|c| c.workspace.id);

        let mut candidates: Vec<i32> =
            self.cfg.allowed_workspaces.iter().copied().filter(|w| Some(*w) != current_ws).collect();
        if candidates.is_empty() {
            // единственный разрешённый стол — деваться некуда, но и падать незачем
            candidates = self.cfg.allowed_workspaces.clone();
        }

        let mut rng = rand::thread_rng();
        if let Some(target) = candidates.choose(&mut rng) {
            hypr::move_window_silent(address, *target)?;
            println!("wattim: окно {address} перемешано на рабочий стол {target}");
        }
        Ok(())
    }
}

/// Основной цикл: выполняется в текущем процессе до завершения Hyprland
/// или ошибки чтения из сокета событий.
pub fn run_foreground() -> Result<()> {
    let cfg = config::load()?;
    if cfg.guarded_apps.is_empty() || cfg.allowed_workspaces.is_empty() {
        anyhow::bail!("конфигурация пуста — сначала запустите `wattim` без аргументов для настройки");
    }
    println!(
        "wattim: слежу за [{}] на рабочих столах {:?}",
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
    if is_running()? {
        println!("wattim уже запущен (pid {}).", read_pid()?.unwrap_or_default());
        return Ok(());
    }
    // Проверяем конфиг до форка, чтобы явную ошибку увидел пользователь в терминале,
    // а не только в лог-файле фонового процесса.
    let cfg = config::load()?;
    if cfg.guarded_apps.is_empty() || cfg.allowed_workspaces.is_empty() {
        anyhow::bail!("конфигурация пуста — сначала запустите `wattim` без аргументов для настройки");
    }

    let pid_path = config::pid_path()?;
    let log_path = config::log_path()?;
    std::fs::create_dir_all(config::config_dir()?)?;
    let stdout = std::fs::File::create(&log_path).context("не удалось создать лог-файл")?;
    let stderr = stdout.try_clone()?;

    let daemonize = Daemonize::new().pid_file(&pid_path).stdout(stdout).stderr(stderr);

    match daemonize.start() {
        // Мы уже в форкнутом фоновом процессе — просто запускаем цикл.
        Ok(_) => run_foreground(),
        Err(e) => Err(anyhow::anyhow!("не удалось уйти в фон: {e}")),
    }
}

pub fn stop() -> Result<()> {
    let Some(pid) = read_pid()? else {
        println!("wattim не запущен.");
        return Ok(());
    };
    if !process_alive(pid) {
        println!("wattim не запущен (устаревший pid-файл, удаляю).");
        std::fs::remove_file(config::pid_path()?).ok();
        return Ok(());
    }
    let status = std::process::Command::new("kill").arg("-TERM").arg(pid.to_string()).status()?;
    std::fs::remove_file(config::pid_path()?).ok();
    if status.success() {
        println!("wattim (pid {pid}) остановлен.");
    } else {
        println!("не удалось отправить сигнал процессу {pid}.");
    }
    Ok(())
}

pub fn restart() -> Result<()> {
    stop().ok();
    start_background()
}

pub fn status() -> Result<()> {
    let cfg = config::load()?;
    match read_pid()? {
        Some(pid) if process_alive(pid) => println!("wattim запущен (pid {pid})."),
        _ => println!("wattim не запущен."),
    }
    println!(
        "Охраняемые приложения: {}",
        if cfg.guarded_apps.is_empty() { "—".to_string() } else { cfg.guarded_apps.join(", ") }
    );
    println!("Рабочие столы для перемешивания: {:?}", cfg.allowed_workspaces);
    Ok(())
}

fn read_pid() -> Result<Option<u32>> {
    let path = config::pid_path()?;
    if !path.exists() {
        return Ok(None);
    }
    let raw = std::fs::read_to_string(path)?;
    Ok(raw.trim().parse().ok())
}

fn is_running() -> Result<bool> {
    Ok(match read_pid()? {
        Some(pid) => process_alive(pid),
        None => false,
    })
}

fn process_alive(pid: u32) -> bool {
    std::path::Path::new(&format!("/proc/{pid}")).exists()
}
