use std::collections::BTreeSet;
use std::fs;
use std::path::{Path, PathBuf};

/// Извлекает класс окна или имя приложения из содержимого .desktop файла.
/// Приоритет:
/// 1. `StartupWMClass` — точный WM_CLASS/app_id в Wayland/Hyprland.
/// 2. Имя бинарника из `Exec` (без аргументов и параметров вроде %U).
pub fn parse_desktop_entry(content: &str) -> Option<String> {
    let mut is_application = false;
    let mut no_display = false;
    let mut startup_wm_class = None;
    let mut exec = None;

    for line in content.lines() {
        let line = line.trim();
        if line.starts_with('#') || line.is_empty() {
            continue;
        }
        if let Some((key, val)) = line.split_once('=') {
            let key = key.trim();
            let val = val.trim();
            match key {
                "Type" if val.eq_ignore_ascii_case("Application") => is_application = true,
                "NoDisplay" if val.eq_ignore_ascii_case("true") => no_display = true,
                "StartupWMClass" if !val.is_empty() => startup_wm_class = Some(val.to_string()),
                "Exec" if !val.is_empty() => {
                    // Извлекаем первое слово из строки Exec (например: "telegram-desktop %u" -> "telegram-desktop")
                    let first = val.split_whitespace().next().unwrap_or_default();
                    let bin_name = Path::new(first)
                        .file_name()
                        .and_then(|f| f.to_str())
                        .unwrap_or(first);
                    if !bin_name.is_empty() {
                        exec = Some(bin_name.to_string());
                    }
                }
                _ => {}
            }
        }
    }

    if no_display {
        return None;
    }
    // Если Type явно не указан, но есть Exec или StartupWMClass, всё равно считаем кандидатом
    if !is_application && startup_wm_class.is_none() && exec.is_none() {
        return None;
    }

    startup_wm_class.or(exec)
}

fn candidate_dirs() -> Vec<PathBuf> {
    let mut dirs = vec![
        PathBuf::from("/usr/share/applications"),
        PathBuf::from("/usr/local/share/applications"),
        PathBuf::from("/var/lib/flatpak/exports/share/applications"),
    ];
    if let Some(user_data) = dirs::data_dir() {
        dirs.push(user_data.join("applications"));
        dirs.push(user_data.join("flatpak/exports/share/applications"));
    }
    dirs
}

/// Сканирует системные и пользовательские каталоги .desktop файлов
/// и возвращает отсортированный список уникальных идентификаторов/классов приложений.
pub fn discover_desktop_classes() -> Vec<String> {
    let mut classes = BTreeSet::new();

    for dir in candidate_dirs() {
        if !dir.exists() {
            continue;
        }
        let entries = match fs::read_dir(&dir) {
            Ok(entries) => entries,
            Err(_) => continue,
        };

        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|s| s.to_str()) == Some("desktop") {
                if let Ok(content) = fs::read_to_string(&path) {
                    if let Some(class) = parse_desktop_entry(&content) {
                        classes.insert(class);
                    }
                }
            }
        }
    }

    classes.into_iter().collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_startup_wm_class_preferred() {
        let content = r#"
[Desktop Entry]
Type=Application
Name=Telegram Desktop
Exec=telegram-desktop -- %u
Icon=telegram
StartupWMClass=telegram-desktop
Categories=Chat;Network;
"#;
        assert_eq!(parse_desktop_entry(content), Some("telegram-desktop".to_string()));
    }

    #[test]
    fn falls_back_to_exec_name() {
        let content = r#"
[Desktop Entry]
Type=Application
Name=Firefox
Exec=/usr/lib/firefox/firefox %u
Icon=firefox
"#;
        assert_eq!(parse_desktop_entry(content), Some("firefox".to_string()));
    }

    #[test]
    fn ignores_no_display() {
        let content = r#"
[Desktop Entry]
Type=Application
Name=Hidden Internal App
Exec=hidden-helper
NoDisplay=true
"#;
        assert_eq!(parse_desktop_entry(content), None);
    }
}
