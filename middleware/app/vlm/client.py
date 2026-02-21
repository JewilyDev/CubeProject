"""
VLM-клиент для оценки зданий через GPT-4o.

Поток:
  1. blocks → renderer.render_blocks() → PNG base64
  2. PNG + промпт → GPT-4o vision
  3. Ответ GPT-4o → парсинг JSON (structured output)
  4. Результат кешируется по SHA-256 хешу блок-дампа

Конфигурация через переменные окружения:
  OPENAI_API_KEY  — обязательно
  VLM_MODEL       — опционально, по умолчанию "gpt-4o"
  VLM_PASS_SCORE  — минимальный балл для прохождения (по умолчанию 60)
"""

import json
import logging
import os
from dataclasses import dataclass
from typing import Optional

import httpx

from .prompts import get_prompt
from .renderer import blocks_to_hash, png_to_base64, render_all_views

logger = logging.getLogger("immersiveciv.vlm")

OPENAI_API_URL = "https://openrouter.ai/api/v1"
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


def _cache_key(building_type: str, block_hash: str) -> str:
    return f"{building_type}:{block_hash}"


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
    blocks: list[dict],
) -> VLMResult:
    """
    Основная точка входа.
    Принимает тип здания и список блоков, возвращает VLMResult.
    """
    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        logger.error("OPENAI_API_KEY не задан — VLM недоступен.")
        return VLMResult(
            score=0, passed=False,
            style_rating="poor", comments="VLM недоступен: API ключ не настроен.",
            improvements=[], error="no_api_key",
        )

    block_hash = blocks_to_hash(blocks)
    key = _cache_key(building_type, block_hash)

    # Проверяем кеш
    if key in _cache:
        cached = _cache[key]
        logger.info("VLM cache hit: %s (score=%d)", key, cached.score)
        return VLMResult(
            score=cached.score, passed=cached.passed,
            style_rating=cached.style_rating, comments=cached.comments,
            improvements=cached.improvements, cached=True,
        )

    # Рендерим три вида: top-down, iso NE, iso SW
    logger.info("VLM: рендеринг %d блоков для «%s»…", len(blocks), building_type)
    png_views = render_all_views(blocks)

    image_content = [
        {
            "type": "image_url",
            "image_url": {
                "url": f"data:image/png;base64,{png_to_base64(png)}",
                "detail": "high",
            },
        }
        for png in png_views
    ]

    system_prompt, user_prompt = get_prompt(building_type)
    view_note = (
        "\n\nThree views are provided: "
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

    logger.info("VLM: отправляем запрос к %s…", VLM_MODEL)
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
