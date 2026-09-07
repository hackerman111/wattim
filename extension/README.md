# wattim Browser Extension 🌿

<div align="center">

### Осознанный цифровой детокс и защита от думскроллинга в браузере

[![Platform](https://img.shields.io/badge/Platform-Chrome%20%7C%20Brave%20%7C%20Edge%20%7C%20Firefox-blue?style=for-the-badge&logo=googlechrome&logoColor=white)](https://developer.chrome.com/docs/extensions/mv3/)
[![Standard](https://img.shields.io/badge/Standard-Manifest%20V3-brightgreen?style=for-the-badge)](https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions)
[![Build](https://img.shields.io/badge/Build-Zero%20Dependencies%20(Vanilla)-success?style=for-the-badge)](package.json)
[![License](https://img.shields.io/badge/License-MIT-blue?style=for-the-badge)](../LICENSE)

*Помогает разорвать петлю импульсивного открытия отвлекающих сайтов (YouTube, Reddit, Instagram, X, VK и др.) с помощью дыхательных пауз, экспоненциального сдерживания и осознанности.*

</div>

---

## 🎯 О расширении

**wattim Browser Extension** переносит функционал осознанного детокса из Android-приложения *wattim* прямо в ваш веб-браузер.

Каждый раз, когда вы по привычке переходите на отвлекающий ресурс:
1. Загрузка сайта немедленно прерывается. Сайт не тратит трафик и не начинает воспроизведение видео со звуком.
2. Включается полноэкранный терминальный экран осознанности с 60 FPS дыхательной анимацией (`Вдох -> Задержка -> Выдох -> Покой`).
3. Кнопка **«ВЫЙТИ»** доступна мгновенно — при нажатии закрывает вкладку и увеличивает счетчик сбереженного времени.
4. Кнопка **«ПРОДОЛЖИТЬ НА САЙТ»** появляется только после полного завершения дыхания и выдает временный допуск на 15 минут.

---

## ✨ Ключевые возможности

- 🧘 **3 стиля Canvas-анимации 60 FPS**: `PULSE` (пульсирующая сфера), `FILL` (плавная волна), `ZEN ORBIT` (орбитальная частица).
- 📈 **Экспоненциальный рост задержки (Exponential Backoff)**: при частых попытках открыть сайт пауза прогрессивно растет:
  $$T = T_{\text{базовая}} \times \left(1 + \frac{\text{рост}}{100}\right)^N$$
  *(10с $\to$ 12с $\to$ 14с $\to$ 17с $\to$ 21с...)*.
- 🕒 **Сторож залипания (Re-intervention Sentinel)**: если вы провели на сайте больше отведенного лимита (по умолчанию 15 минут), расширение активирует повторную интервенцию.
- 📅 **Расписания (Schedules)**: поддержка режимов **Hard Block** (полная блокировка без возможности входа в рабочие часы) и персональных задержек дыхания.
- ⏸️ **Глобальная быстрая пауза**: приостановка защиты на 15 мин, 30 мин, 1 час или 1 день из всплывающего меню (Popup).
- 🎨 **6 авторских цветовых тем**: Nord, Catppuccin Mocha, Dracula, Gruvbox Dark, Tokyo Night, Cyber Terminal.
- 📊 **Статистика сбереженной жизни**: подсчет сохраненных импульсов и часов реальной жизни.
- 🔒 **100% Offline & Конфиденциальность**: никаких внешних запросов, трекеров, аналитики или серверов. Все данные хранятся в `chrome.storage.local`.

---

## 🚀 Установка расширения

### Google Chrome / Brave / Edge / Яндекс.Браузер (Chromium)

1. Откройте в браузере страницу расширений:
   - В Chrome: `chrome://extensions`
   - В Brave: `brave://extensions`
   - В Edge: `edge://extensions`
2. В правом верхнем углу включите тумблер **«Режим разработчика»** (Developer mode).
3. Нажмите появившуюся кнопку **«Загрузить распакованное расширение»** (Load unpacked).
4. Выберите директорию `extension/` из этого репозитория:
   `/home/papayka/Rust_project/one sec clone/extension`
5. Расширение установлено и готово к работе!

### Mozilla Firefox

1. Откройте страницу отладки дополнений: `about:debugging#/runtime/this-firefox`
2. Нажмите **«Загрузить временное дополнение...»** (Load Temporary Add-on).
3. Выберите файл `manifest.json` в папке `extension/`.
4. Расширение активно в Firefox!

---

## 🧪 Запуск тестов

В репозитории подготовлен набор unit-тестов логики и валидации манифеста:

```bash
node tests/run-all.mjs
```

---

## 📁 Структура кодовой базы

```
extension/
├── manifest.json              # Универсальный Manifest V3
├── icons/                     # Иконки (16, 32, 48, 128 px)
├── common/
│   ├── theme.css              # 6 цветовых палитр (CSS переменные)
│   ├── themes.js              # Экспорт конфигураций тем
│   └── storage.js             # Асинхронный слой работы с chrome.storage.local
├── background/
│   ├── background.js          # Service Worker: перехват webNavigation, сессии, alarms
│   └── rule-engine.js         # Матчер доменов, расчет бэкоффа, расписания
├── intervention/
│   ├── intervention.html      # Полноэкранный экран паузы ($ wattim --breathe)
│   ├── intervention.css       # Стили терминала, кнопок и карточек
│   ├── intervention.js        # Контроллер дыхательного таймера и кнопок
│   └── animations.js          # 60 FPS Canvas-движок (FILL, PULSE, ZEN_ORBIT)
├── popup/
│   ├── popup.html             # Быстрое меню в панели браузера
│   ├── popup.css              # Компактный терминальный стиль
│   └── popup.js               # Быстрая пауза, статус, добавление текущего сайта
├── options/
│   ├── options.html           # Полноценная панель управления (Dashboard)
│   ├── options.css            # Терминальный интерфейс настроек с вкладками
│   └── options.js             # Управление сайтами, бэкоффом, расписаниями и темами
└── sentinel/
    └── sentinel.js            # Легковесный контент-скрипт (re-intervention)
```
