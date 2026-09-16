use anyhow::{Context, Result};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;

#[derive(Debug, Serialize, Deserialize, Clone, Default)]
pub struct Config {
    /// Классы окон (WM_CLASS), которые демон "охраняет" от залипания на одном столе.
    pub guarded_apps: Vec<String>,
    /// ID рабочих столов, между которыми можно перемешивать охраняемые окна.
    pub allowed_workspaces: Vec<i32>,
}

pub fn config_dir() -> Result<PathBuf> {
    let base = dirs::config_dir().context("не удалось определить каталог конфигурации ($XDG_CONFIG_HOME)")?;
    Ok(base.join("wattim"))
}

pub fn config_path() -> Result<PathBuf> {
    Ok(config_dir()?.join("config.toml"))
}

pub fn pid_path() -> Result<PathBuf> {
    Ok(config_dir()?.join("wattim.pid"))
}

pub fn log_path() -> Result<PathBuf> {
    Ok(config_dir()?.join("wattim.log"))
}

pub fn load() -> Result<Config> {
    let path = config_path()?;
    if !path.exists() {
        return Ok(Config::default());
    }
    let raw = std::fs::read_to_string(&path)
        .with_context(|| format!("не удалось прочитать {path:?}"))?;
    toml::from_str(&raw).with_context(|| format!("не удалось разобрать {path:?}"))
}

pub fn save(cfg: &Config) -> Result<()> {
    let dir = config_dir()?;
    std::fs::create_dir_all(&dir).with_context(|| format!("не удалось создать {dir:?}"))?;
    let raw = toml::to_string_pretty(cfg)?;
    std::fs::write(config_path()?, raw)?;
    Ok(())
}
