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

/// Убирает необязательный префикс `0x` и приводит к нижнему регистру,
/// чтобы адреса окон из разных источников (события, JSON-запросы) сравнивались надёжно.
pub fn norm_address(addr: &str) -> String {
    addr.trim_start_matches("0x").to_lowercase()
}

pub fn find_client_by_address<'a>(clients: &'a [Client], address: &str) -> Option<&'a Client> {
    let target = norm_address(address);
    clients.iter().find(|c| norm_address(&c.address) == target)
}

/// Тихо перемещает окно на рабочий стол, не переключая на него фокус пользователя.
pub fn move_window_silent(address: &str, workspace_id: i32) -> Result<()> {
    let addr = format!("0x{}", norm_address(address));
    let cmd = format!("dispatch movetoworkspacesilent {workspace_id},address:{addr}");
    let reply = send_command(&cmd)?;
    if reply.trim() != "ok" {
        return Err(anyhow!("Hyprland ответил на dispatch: {}", reply.trim()));
    }
    Ok(())
}
