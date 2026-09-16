//! Тонкий слой поверх IPC-протокола Hyprland.
//!
//! Hyprland предоставляет два unix-сокета в `$XDG_RUNTIME_DIR/hypr/<sig>/`:
//! `.socket.sock`  — командный: одно соединение = один запрос/ответ;
//! `.socket2.sock` — событийный: постоянный поток строк вида `KIND>>PAYLOAD`.

use anyhow::{anyhow, Context, Result};
use serde::Deserialize;
use std::io::{Read, Write};
use std::os::unix::net::UnixStream;
use std::path::PathBuf;

pub fn is_hyprland_active() -> bool {
    runtime_dir().is_ok()
}

fn runtime_dir() -> Result<PathBuf> {
    let xdg = std::env::var("XDG_RUNTIME_DIR")
        .context("XDG_RUNTIME_DIR не установлена — не могу найти сокеты Hyprland")?;
    let sig = std::env::var("HYPRLAND_INSTANCE_SIGNATURE")
        .context("HYPRLAND_INSTANCE_SIGNATURE не установлена — похоже, Hyprland не запущен в этой сессии")?;
    Ok(PathBuf::from(xdg).join("hypr").join(sig))
}

pub fn command_socket_path() -> Result<PathBuf> {
    Ok(runtime_dir()?.join(".socket.sock"))
}

pub fn event_socket_path() -> Result<PathBuf> {
    Ok(runtime_dir()?.join(".socket2.sock"))
}

/// Отправляет одну команду в командный сокет и возвращает ответ целиком.
/// Каждый вызов открывает новое соединение — так работает протокол Hyprland,
/// повторно использовать соединение между командами нельзя.
pub fn send_command(cmd: &str) -> Result<String> {
    let path = command_socket_path()?;
    let mut stream = UnixStream::connect(&path)
        .with_context(|| format!("не удалось подключиться к командному сокету Hyprland ({path:?})"))?;
    stream.write_all(cmd.as_bytes())?;
    stream.shutdown(std::net::Shutdown::Write).ok();
    let mut buf = String::new();
    stream.read_to_string(&mut buf)?;
    Ok(buf)
}

#[derive(Debug, Deserialize, Clone)]
pub struct Workspace {
    pub id: i32,
    pub name: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct WorkspaceRef {
    pub id: i32,
    #[allow(dead_code)]
    pub name: String,
}

#[derive(Debug, Deserialize, Clone)]
pub struct Client {
    pub address: String,
    pub class: String,
    #[allow(dead_code)]
    pub title: String,
    pub workspace: WorkspaceRef,
}

pub fn get_workspaces() -> Result<Vec<Workspace>> {
    let raw = send_command("j/workspaces")?;
    serde_json::from_str(&raw)
        .with_context(|| format!("не удалось разобрать список рабочих столов Hyprland: {raw}"))
}

pub fn get_clients() -> Result<Vec<Client>> {
    let raw = send_command("j/clients")?;
    serde_json::from_str(&raw)
        .with_context(|| format!("не удалось разобрать список окон Hyprland: {raw}"))
}

/// Убирает необязательный префикс `0x` (в любом регистре) и приводит к нижнему регистру,
/// чтобы адреса окон из разных источников (события, JSON-запросы) сравнивались надёжно.
pub fn norm_address(addr: &str) -> String {
    let lower = addr.trim().to_lowercase();
    lower.strip_prefix("0x").unwrap_or(&lower).to_string()
}

pub fn find_client_by_address<'a>(clients: &'a [Client], address: &str) -> Option<&'a Client> {
    let target = norm_address(address);
    clients.iter().find(|c| norm_address(&c.address) == target)
}

/// Тихо перемещает окно на рабочий стол, не переключая на него фокус пользователя.
/// Поддерживает современный Lua API Hyprland 0.56+ с фоллбэком на классический dispatch.
pub fn move_window_silent(address: &str, workspace_id: i32) -> Result<()> {
    let addr = format!("0x{}", norm_address(address));

    // 1. Пробуем современный Lua API Hyprland (0.50+)
    let lua_cmd = format!(
        r#"repl return hl.dispatch(hl.dsp.window.move({{ window = hl.get_window("{addr}"), workspace = {workspace_id}, silent = true }}))"#
    );
    if let Ok(reply) = send_command(&lua_cmd) {
        if !reply.contains("error:") {
            return Ok(());
        }
    }

    // 2. Фоллбэк на классический командный dispatch для более старых версий
    let legacy_cmd = format!("dispatch movetoworkspacesilent {workspace_id},address:{addr}");
    let legacy_reply = send_command(&legacy_cmd)?;
    if legacy_reply.trim() == "ok" || !legacy_reply.contains("error:") {
        return Ok(());
    }

    Err(anyhow!(
        "Hyprland не смог переместить окно {addr} на стол {workspace_id}: {}",
        legacy_reply.trim()
    ))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn normalizes_addresses_properly() {
        assert_eq!(norm_address("0x5590D2A3B1C0"), "5590d2a3b1c0");
        assert_eq!(norm_address("5590d2a3b1c0"), "5590d2a3b1c0");
        assert_eq!(norm_address("0XABC"), "abc");
    }

    #[test]
    fn finds_client_by_normalized_address() {
        let clients = vec![
            Client {
                address: "0x5590d2a3b1c0".to_string(),
                class: "telegram-desktop".to_string(),
                title: "Telegram".to_string(),
                workspace: WorkspaceRef { id: 2, name: "2".to_string() },
            },
            Client {
                address: "0x1234abcd".to_string(),
                class: "firefox".to_string(),
                title: "Mozilla Firefox".to_string(),
                workspace: WorkspaceRef { id: 4, name: "4".to_string() },
            },
        ];

        let found = find_client_by_address(&clients, "5590D2A3B1C0");
        assert!(found.is_some());
        assert_eq!(found.unwrap().class, "telegram-desktop");

        let not_found = find_client_by_address(&clients, "999999");
        assert!(not_found.is_none());
    }
}
