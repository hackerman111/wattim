//! Подписка на событийный сокет Hyprland (`.socket2.sock`) и разбор событий.
//!
//! Формат каждой строки: `имя_события>>аргумент1,аргумент2,...`.
//! Полный список событий шире, но демону нужны только эти:
//! `openwindow`, `closewindow` и `activewindowv2`.

use anyhow::{Context, Result};
use std::io::BufRead;
use std::io::BufReader;
use std::os::unix::net::UnixStream;

use crate::hypr::event_socket_path;

#[derive(Debug, Clone)]
pub enum Event {
    /// Новое окно появилось: адрес, id/имя рабочего стола, класс, заголовок.
    OpenWindow {
        address: String,
        #[allow(dead_code)]
        workspace: String,
        class: String,
        #[allow(dead_code)]
        title: String,
    },
    /// Окно закрыто.
    CloseWindow { address: String },
    /// Сменился активный (сфокусированный) клиент. `None`, если фокус ушёл в никуда
    /// (например, все окна на рабочем столе закрыты).
    ActiveWindowV2 { address: Option<String> },
    /// Событие, которое демону не нужно.
    Other,
}

fn parse_line(line: &str) -> Event {
    let Some((kind, payload)) = line.split_once(">>") else {
        return Event::Other;
    };
    match kind {
        "openwindow" => {
            let mut parts = payload.splitn(4, ',');
            let address = parts.next().unwrap_or_default().to_string();
            let workspace = parts.next().unwrap_or_default().to_string();
            let class = parts.next().unwrap_or_default().to_string();
            let title = parts.next().unwrap_or_default().to_string();
            Event::OpenWindow { address, workspace, class, title }
        }
        "closewindow" => Event::CloseWindow { address: payload.trim().to_string() },
        "activewindowv2" => {
            let addr = payload.trim();
            Event::ActiveWindowV2 {
                address: if addr.is_empty() { None } else { Some(addr.to_string()) },
            }
        }
        _ => Event::Other,
    }
}

/// Подключается к событийному сокету и вызывает `handler` для каждой полученной строки.
/// Блокирует вызывающий поток до закрытия соединения (Hyprland завершится/перезапустится)
/// или до ошибки чтения — предполагается, что это основной цикл демона.
pub fn listen<F: FnMut(Event)>(mut handler: F) -> Result<()> {
    let path = event_socket_path()?;
    let stream = UnixStream::connect(&path)
        .with_context(|| format!("не удалось подключиться к сокету событий Hyprland ({path:?})"))?;
    let reader = BufReader::new(stream);
    for line in reader.lines() {
        let line = line.context("ошибка чтения из сокета событий Hyprland")?;
        handler(parse_line(&line));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn parses_openwindow() {
        match parse_line("openwindow>>5590d2a3b1c0,2,telegram-desktop,Telegram") {
            Event::OpenWindow { address, workspace, class, title } => {
                assert_eq!(address, "5590d2a3b1c0");
                assert_eq!(workspace, "2");
                assert_eq!(class, "telegram-desktop");
                assert_eq!(title, "Telegram");
            }
            _ => panic!("ожидался OpenWindow"),
        }
    }

    #[test]
    fn parses_activewindowv2_empty() {
        match parse_line("activewindowv2>>") {
            Event::ActiveWindowV2 { address } => assert_eq!(address, None),
            _ => panic!("ожидался ActiveWindowV2"),
        }
    }

    #[test]
    fn parses_closewindow() {
        match parse_line("closewindow>>5590d2a3b1c0") {
            Event::CloseWindow { address } => assert_eq!(address, "5590d2a3b1c0"),
            _ => panic!("ожидался CloseWindow"),
        }
    }
}
