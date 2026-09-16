# Hyprland Wattim Daemon Design

## Overview
`wattim` — это легковесный анти-думскроллинг демон для оконного менеджера Hyprland.
Принцип работы:
1. Пользователь вводит в консоль команду `wattim`.
2. Появляется интерактивная инлайн-панель (`inquire`), позволяющая выбрать допустимые рабочие столы (автоопределение существующих столов + выбор диапазона) и охраняемые приложения (с автодополнением по открытым окнам и установленным `.desktop`-файлам).
3. После сохранения конфигурации демон автоматически запускается или перезапускается в фоне.
4. При работе в охраняемом приложении (например, Telegram на рабочем столе 2), как только пользователь переключается на другое окно или другой рабочий стол, охраняемое приложение мгновенно и бесшумно («silent move») перемешивается на случайный рабочий стол из списка разрешённых.

## Architecture

```
                       +-------------------------------+
                       |           wattim CLI          |
                       +---------------+---------------+
                                       |
                     +-----------------+-----------------+
                     |                                   |
            (no args: interactive)            (subcommand: start/stop/status)
                     |                                   |
             [ wizard::run() ]                           |
                     |                                   |
         writes config.toml                              |
                     |                                   |
                     +-----------------+-----------------+
                                       |
                          [ daemon::start_background() ]
                                       |
                       +---------------+---------------+
                       |        Background Daemon      |
                       +---------------+---------------+
                                       |
            +--------------------------+--------------------------+
            | (Read events)                                       | (Dispatch commands)
            v                                                     v
  .socket2.sock (IPC event stream)                     .socket.sock (IPC command)
  [activewindowv2, openwindow, closewindow]            [dispatch movetoworkspacesilent]
            |                                                     ^
            v                                                     |
  [ State::on_active_changed() ] -------------(if left guarded)---+
```

## Module Structure

* `hypr_ver/src/main.rs`: Точка входа CLI. Обработка аргументов (`start`, `stop`, `restart`, `status`, `run`, default -> wizard).
* `hypr_ver/src/wizard.rs`: Интерактивный диалог настройки воркспейсов и приложений (`inquire`).
* `hypr_ver/src/desktop_apps.rs`: Поиск и парсинг `.desktop`-файлов для автодополнения незапущенных приложений.
* `hypr_ver/src/autocomplete.rs`: Реализация `inquire::Autocomplete`, объединяющая открытые окна и системные приложения.
* `hypr_ver/src/config.rs`: Сериализация/десериализация конфигурации (`~/.config/wattim/config.toml`), пути к PID и логам.
* `hypr_ver/src/hypr.rs`: Клиент протокола Hyprland IPC (запросы `j/workspaces`, `j/clients`, команда `movetoworkspacesilent`).
* `hypr_ver/src/events.rs`: Парсер и стример событий `.socket2.sock`.
* `hypr_ver/src/daemon.rs`: Стейт-машина демона, логика перемешивания, управление фоновым процессом (`daemonize`).

## Invariants & Shuffle Logic

1. **Фокус на охраняемом приложении**:
   - `State` хранит `active: Option<(address, class)>`.
   - Если окно имеет класс из `cfg.guarded_apps`, оно считается охраняемым.
2. **Уход фокуса**:
   - Событие `activewindowv2>>new_address`.
   - Если предыдущее окно охраняемое, и `new_class != prev_class` (или новое окно пустое):
     - Запускается процедура `shuffle(prev_address)`.
   - Если новое окно имеет тот же класс (внутреннее окно/модальное окно того же приложения), перемешивание НЕ происходит.
3. **Выбор целевого стола**:
   - Исключается текущий рабочий стол охраняемого окна.
   - Выбирается случайный рабочий стол из `cfg.allowed_workspaces`.
   - Отправляется `dispatch movetoworkspacesilent <target>,address:0x<prev_address>`.

## Error Handling & Lifecycle
- Проверка наличия переменных окружения Hyprland.
- Проверка живости процессов через `/proc/<pid>`.
- Автоматическая очистка устаревших PID-файлов.
- Логирование в `~/.config/wattim/wattim.log`.
