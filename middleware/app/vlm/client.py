"""
VLM-клиент для оценки зданий через GPT-4o.

Поток (новый — рендер на клиенте Minecraft):
  1. Java-клиент рендерит 3 PNG вида → отправляет на сервер → Middleware получает base64
  2. base64-строки + промпт → GPT-4o vision (3 image_url в одном запросе)
  3. Ответ GPT-4o → парсинг JSON (structured output)
  4. Результат кешируется по SHA-256 хешу изображений

Конфигурация через переменные окружения:
  OPENAI_API_KEY  — обязательно
  VLM_MODEL       — опционально, по умолчанию "gpt-4o"
  VLM_PASS_SCORE  — минимальный балл для прохождения (по умолчанию 60)
"""

import hashlib
import json
import logging
import os
from dataclasses import dataclass
from typing import Optional

import httpx

from .prompts import get_prompt

logger = logging.getLogger("immersiveciv.vlm")

OPENAI_API_URL = os.getenv("OPENAI_API_URL", "https://openrouter.ai/api/v1") + "/chat/completions"
VLM_MODEL      = os.getenv("VLM_MODEL", "gpt-4o")
VLM_PASS_SCORE = int(os.getenv("VLM_PASS_SCORE", "60"))


@dataclass
class VLMResult:
    score: int
    passed: bool
    style_rating: str          # "poor" | "fair" | "good" | "excellent"
    comments: str
    improvements: list[str]
    cached: bool = False
    error: Optional[str] = None


# ── Кеш ──────────────────────────────────────────────────────────────────────
# Простой in-memory кеш: hash → VLMResult
# В продакшне заменить на SQLite/Redis (Фаза 4)
_cache: dict[str, VLMResult] = {}


def _images_hash(images: dict[str, str]) -> str:
    """SHA-256 по конкатенации трёх base64-строк."""
    combined = (
        images.get("top_down", "")
        + images.get("iso_ne", "")
        + images.get("iso_sw", "")
    )
    return hashlib.sha256(combined.encode()).hexdigest()


def _cache_key(building_type: str, images: dict[str, str]) -> str:
    return f"{building_type}:{_images_hash(images)}"


# ── Парсинг ответа ────────────────────────────────────────────────────────────

def _parse_response(raw: str) -> VLMResult:
    """
    Парсит JSON из ответа GPT-4o.
    GPT иногда оборачивает JSON в ```json ... ``` — очищаем.
    """
    cleaned = raw.strip()
    if cleaned.startswith("```"):
        lines = cleaned.split("\n")
        cleaned = "\n".join(lines[1:-1])

    data = json.loads(cleaned)

    score         = max(0, min(100, int(data.get("score", 0))))
    passed        = bool(data.get("passed", score >= VLM_PASS_SCORE))
    style_rating  = data.get("style_rating", "fair")
    comments      = data.get("comments", "")
    improvements  = data.get("improvements", [])

    # Форсируем порог независимо от того, что ответил GPT
    if score < VLM_PASS_SCORE:
        passed = False

    return VLMResult(
        score=score,
        passed=passed,
        style_rating=style_rating,
        comments=comments,
        improvements=improvements,
    )


# ── Основной вызов ────────────────────────────────────────────────────────────

async def evaluate_building(
    building_type: str,
    images: dict[str, str],
) -> VLMResult:
    """
    Основная точка входа.

    Args:
        building_type: тип здания (например, "farm", "watchtower")
        images: словарь с тремя base64 PNG:
                  "top_down" — вид сверху
                  "iso_ne"   — изометрия с северо-востока
                  "iso_sw"   — изометрия с юго-запада

    Returns:
        VLMResult с оценкой и комментариями.
    """
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        logger.error("OPENAI_API_KEY не задан — VLM недоступен.")
        return VLMResult(
            score=0, passed=False,
            style_rating="poor", comments="VLM недоступен: API ключ не настроен.",
            improvements=[], error="no_api_key",
        )

    # Проверяем наличие изображений
    missing = [k for k in ("top_down", "iso_ne", "iso_sw") if not images.get(k)]
    if missing:
        logger.error("VLM: отсутствуют изображения: %s", missing)
        return VLMResult(
            score=0, passed=False, style_rating="poor",
            comments=f"Не хватает изображений: {missing}.",
            improvements=[], error="missing_images",
        )

    key = _cache_key(building_type, images)

    # Проверяем кеш
    if key in _cache:
        cached = _cache[key]
        logger.info("VLM cache hit: %s (score=%d)", key[:32], cached.score)
        return VLMResult(
            score=cached.score, passed=cached.passed,
            style_rating=cached.style_rating, comments=cached.comments,
            improvements=cached.improvements, cached=True,
        )

    # Строим image_content из готовых base64 — три вида от Java-клиента
    logger.info("VLM: отправляем 3 вида постройки «%s» в %s…", building_type, VLM_MODEL)
    view_labels = [
        ("top_down", "top-down plan view"),
        ("iso_ne",   "isometric north-east view"),
        ("iso_sw",   "isometric south-west view"),
    ]
    image_content = [
        {
            "type": "image_url",
            "image_url": {
                "url": f"data:image/png;base64,{images[key_name]}",
                "detail": "high",
            },
        }
        for key_name, _ in view_labels
    ]

    system_prompt, user_prompt = get_prompt(building_type)
    view_note = (
        "\n\nThree views are provided (rendered directly by Minecraft client): "
        "(1) top-down plan, (2) isometric north-east, (3) isometric south-west."
    )

    payload = {
        "model": VLM_MODEL,
        "max_tokens": 512,
        "messages": [
            {"role": "system", "content": system_prompt},
            {
                "role": "user",
                "content": image_content + [{"type": "text", "text": user_prompt + view_note}],
            },
        ],
    }

    try:
        async with httpx.AsyncClient(timeout=60.0) as client:
            resp = await client.post(
                OPENAI_API_URL,
                json=payload,
                headers={"Authorization": f"Bearer {api_key}"},
            )
        resp.raise_for_status()
        raw_content = resp.json()["choices"][0]["message"]["content"]
        result = _parse_response(raw_content)

    except httpx.HTTPStatusError as e:
        logger.error("VLM HTTP ошибка %d: %s", e.response.status_code, e.response.text[:300])
        return VLMResult(
            score=0, passed=False, style_rating="poor",
            comments=f"Ошибка VLM-сервера (HTTP {e.response.status_code}).",
            improvements=[], error=f"http_{e.response.status_code}",
        )
    except json.JSONDecodeError as e:
        logger.error("VLM: не удалось распарсить JSON: %s", e)
        return VLMResult(
            score=0, passed=False, style_rating="poor",
            comments="VLM вернул некорректный ответ. Попробуй позже.",
            improvements=[], error="parse_error",
        )
    except Exception as e:
        logger.error("VLM неожиданная ошибка: %s", e, exc_info=True)
        return VLMResult(
            score=0, passed=False, style_rating="poor",
            comments="Внутренняя ошибка VLM.",
            improvements=[], error="unknown",
        )

    # Сохраняем в кеш только успешные ответы
    _cache[key] = result
    logger.info("VLM результат: score=%d, passed=%s, style=%s",
                result.score, result.passed, result.style_rating)
    return result
