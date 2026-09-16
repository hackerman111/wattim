pub mod autocomplete;
pub mod config;
pub mod daemon;
pub mod desktop_apps;
pub mod events;
pub mod hypr;
pub mod wizard;

use anyhow::Result;

fn print_help() {
    println!(
        r#"wattim — анти-думскроллинг демон для Hyprland

ИСПОЛЬЗОВАНИЕ:
    wattim                Запустить инлайн-панель настройки и демон
    wattim start          Запустить демон в фоне (для hyprland.conf: exec-once = wattim start)
    wattim stop           Остановить фоновый демон
    wattim restart        Перезапустить фоновый демон
    wattim status         Показать статус демона и текущую конфигурацию
    wattim run            Запустить демон в foreground (для отладки/просмотра логов)
    wattim help, -h       Показать эту справку

КАК ЭТО РАБОТАЕТ:
    Когда вы переключаетесь с охраняемого приложения (например, Telegram) на другое
    окно или пустой рабочий стол, брошенное приложение бесшумно перемещается
    на случайный рабочий стол из разрешённых вами.
"#
    );
}

fn main() -> Result<()> {
    let args: Vec<String> = std::env::args().collect();
    let command = args.get(1).map(|s| s.as_str());

    match command {
        None => {
            wizard::run()?;
        }
        Some("start") => {
            daemon::start_background()?;
        }
        Some("stop") => {
            daemon::stop()?;
        }
        Some("restart") => {
            daemon::restart()?;
        }
        Some("status") => {
            daemon::status()?;
        }
        Some("run") => {
            daemon::run_foreground()?;
        }
        Some("-h" | "--help" | "help") => {
            print_help();
        }
        Some(unknown) => {
            eprintln!("Неизвестная команда: {unknown}\n");
            print_help();
            std::process::exit(1);
        }
    }

    Ok(())
}
