# Индустриальное Возрождение

Minecraft мод (Fabric 1.20.1) с AI-валидацией зданий через GPT-4o и LLM-управляемыми NPC.

## Структура проекта

```
ClaudeProject/
├── java_mod/          — Fabric мод (Java 17)
│   ├── build.gradle
│   └── src/main/java/com/example/immersiveciv/
│       ├── Immerviseciv.java          — точка входа
│       ├── command/
│       │   ├── ScanCommand.java       — /scan <radius> <label>
│       │   └── CivResultCommand.java  — /civresult (внутренняя)
│       └── network/
│           └── MiddlewareClient.java  — WebSocket-клиент
│
├── kubejs/            — KubeJS скрипты (горячая перезагрузка)
│   └── server_scripts/immersiveciv/
│       ├── validate_trigger.js        — /validatebuilding, /freebuild
│       ├── validate_result_handler.js — обработка ответа от Middleware
│       └── checks/
│           ├── perimeter_check.js     — замкнутость стен
│           ├── required_blocks_check.js — обязательные блоки
│           └── su_check.js            — мощность Create (SU)
│
├── middleware/        — Python FastAPI сервер
│   ├── main.py                — точка входа (порт 8765)
│   ├── requirements.txt
│   ├── .env.example           — шаблон переменных окружения
│   ├── fake_mod.py            — тестирование без Minecraft
│   ├── test_renderer.py       — проверка изометрического рендера
│   └── app/
│       ├── routes.py          — /ws, /api/status, /api/broadcast
│       ├── connection_manager.py
│       ├── message_handler.py — диспетчер пакетов
│       ├── aggregator.py      — объединение KubeJS + VLM результатов
│       └── vlm/
│           ├── client.py      — GPT-4o вызов + кеш
│           ├── prompts.py     — промпты для 22 типов зданий
│           └── renderer.py    — изометрический рендер блок-дампа (Pillow)
│
└── plan.md            — план разработки с прогрессом
```

---

## Быстрый старт

### 1. Middleware (Python)

```bash
cd middleware

# Установить зависимости
pip install -r requirements.txt

# Создать .env с API-ключом
cp .env.example .env
# Отредактировать .env — вписать OPENAI_API_KEY

# Запустить
python main.py
```

Сервер стартует на `ws://localhost:8765`.

---

### 2. Тестирование без Minecraft

**Шаг 1 — проверить рендерер (не нужен API-ключ):**
```bash
cd middleware
python test_renderer.py
```
Генерирует PNG в `middleware/test_renders/` — ферма, башня, кузница, теплица.

**Шаг 2 — проверить полный pipeline (нужен OPENAI_API_KEY в .env):**
```bash
# Терминал 1
python main.py

# Терминал 2
python fake_mod.py farm_ok        # ферма, все проверки OK → VLM оценка
python fake_mod.py forge_tech_fail # кузница, техпроверка провалена
python fake_mod.py free_build      # свободная постройка → XP бонус
python fake_mod.py plain_scan      # обычный /scan
```

---

### 3. Java-мод

```bash
cd java_mod

# Скачать зависимости и запустить dev-сервер
./gradlew runServer
```

KubeJS папку положить в `java_mod/run/kubejs/` (симлинк или копия).

После старта сервера — команды доступны в игре:
```
/scan 12 test_area
/validatebuilding farm 12
/validatebuilding foundry 10
/freebuild 8
```

---

## Поток валидации здания

```
Игрок: /validatebuilding farm 12
  ↓
KubeJS: scan region → 3 техпроверки (перimeter, blocks, SU)
  ↓
Java-мод: /scan с JSON-меткой → WebSocket → Middleware
  ↓
Middleware: aggregator
  ├── tech_passed = false → validate_result (провал) → игрок
  └── tech_passed = true
        ↓
      validate_progress → "Визуальная оценка…" → игрок
        ↓
      VLM: render_blocks() → PNG → GPT-4o → score 0-100
        ↓
      ValidationReport → validate_result → Java-мод
        ↓
      /civresult → KubeJS → title + звук (+ XP при /freebuild)
```

---

## Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `OPENAI_API_KEY` | — | **Обязательно.** Ключ OpenAI |
| `VLM_MODEL` | `gpt-4o` | Модель для визуальной оценки |
| `VLM_PASS_SCORE` | `60` | Минимальный балл для прохождения |
