"""
Агрегатор валидации — объединяет результаты KubeJS (бинарные) и VLM (оценка 0-100).

Логика двухфазной валидации:
  Фаза 1 (мгновенно): KubeJS — бинарные проверки блоков, периметра, SU
  Фаза 2 (async):    VLM    — визуальная оценка 0–100

Итоговое решение (overall_passed):
  - Если KubeJS провален → провал (VLM не запускается)
  - Если KubeJS ОК, VLM < порога → провал
  - Если KubeJS ОК, VLM >= порога → успех
  - Если VLM недоступен (ошибка API) → техпроверки достаточны (graceful degradation)

Свободные постройки (building_type == "free"):
  - KubeJS проверки не запускаются
  - VLM оценивает субъективно без чеклиста
  - При VLM >= 40 → малый бонус-атрибуция
"""

from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Optional

from .vlm.client import VLMResult

logger = logging.getLogger("immersiveciv.aggregator")

VLM_PASS_SCORE   = 60   # минимальный балл VLM для прохождения
FREE_BONUS_SCORE = 40   # минимальный балл для малого бонуса у свободных построек


# ── Структуры данных ──────────────────────────────────────────────────────────

@dataclass
class TechCheck:
    name: str
    ok: bool
    message: str


@dataclass
class ValidationReport:
    building_type:   str
    player:          str
    overall_passed:  bool

    # Фаза 1 — KubeJS
    tech_passed:     bool
    tech_checks:     list[TechCheck]

    # Фаза 2 — VLM
    vlm_score:       Optional[int]
    vlm_style:       Optional[str]
    vlm_comment:     Optional[str]
    vlm_improvements: list[str]
    vlm_cached:      bool
    vlm_error:       Optional[str]

    # Свободная постройка
    is_free_build:   bool = False
    free_bonus:      bool = False   # малый бонус при score >= 40

    # Итоговый feedback для игрока
    summary_lines:   list[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "type":            "validate_result",
            "building_type":   self.building_type,
            "player":          self.player,
            "overall_passed":  self.overall_passed,
            "tech_passed":     self.tech_passed,
            "tech_checks":     [{"name": c.name, "ok": c.ok, "message": c.message}
                                 for c in self.tech_checks],
            "vlm_score":       self.vlm_score,
            "vlm_style":       self.vlm_style,
            "vlm_comment":     self.vlm_comment,
            "vlm_improvements": self.vlm_improvements,
            "vlm_cached":      self.vlm_cached,
            "vlm_error":       self.vlm_error,
            "is_free_build":   self.is_free_build,
            "free_bonus":      self.free_bonus,
            "summary_lines":   self.summary_lines,
        }


# ── Агрегатор ─────────────────────────────────────────────────────────────────

def aggregate(
    *,
    building_type:  str,
    player:         str,
    tech_passed:    bool,
    tech_checks_raw: list[dict],   # список {"name","ok","message"} от KubeJS
    vlm:            Optional[VLMResult],
) -> ValidationReport:
    """
    Принимает результаты обеих фаз, возвращает ValidationReport.
    vlm=None означает что VLM не запускался (tech_passed=False) или ошибка.
    """
    is_free = building_type == "free"
    tech_checks = [TechCheck(**c) for c in tech_checks_raw]

    # ── Случай: свободная постройка ───────────────────────────────────────────
    if is_free:
        if vlm is None or vlm.error:
            return ValidationReport(
                building_type=building_type, player=player,
                overall_passed=False, tech_passed=True, tech_checks=[],
                vlm_score=None, vlm_style=None, vlm_comment=None,
                vlm_improvements=[], vlm_cached=False,
                vlm_error=vlm.error if vlm else "vlm_not_run",
                is_free_build=True, free_bonus=False,
                summary_lines=["§cVLM недоступен, бонус не начислен."],
            )

        free_bonus = vlm.score >= FREE_BONUS_SCORE
        lines = _build_free_summary(vlm, free_bonus)
        return ValidationReport(
            building_type=building_type, player=player,
            overall_passed=free_bonus,
            tech_passed=True, tech_checks=[],
            vlm_score=vlm.score, vlm_style=vlm.style_rating,
            vlm_comment=vlm.comments, vlm_improvements=vlm.improvements,
            vlm_cached=vlm.cached, vlm_error=None,
            is_free_build=True, free_bonus=free_bonus,
            summary_lines=lines,
        )

    # ── Случай: KubeJS провален → немедленный провал ─────────────────────────
    if not tech_passed:
        failed_names = [c.name for c in tech_checks if not c.ok]
        lines = _build_tech_fail_summary(tech_checks)
        return ValidationReport(
            building_type=building_type, player=player,
            overall_passed=False, tech_passed=False, tech_checks=tech_checks,
            vlm_score=None, vlm_style=None, vlm_comment=None,
            vlm_improvements=[], vlm_cached=False, vlm_error=None,
            summary_lines=lines,
        )

    # ── Случай: KubeJS ОК, VLM вернул ошибку → graceful degradation ──────────
    if vlm is None or vlm.error:
        logger.warning("VLM недоступен для «%s», принимаем на основе техпроверок.", building_type)
        lines = [
            "§e[ImmersiveCiv] VLM временно недоступен.",
            "§aТехнические проверки пройдены — здание принято (без визуальной оценки).",
        ]
        return ValidationReport(
            building_type=building_type, player=player,
            overall_passed=True, tech_passed=True, tech_checks=tech_checks,
            vlm_score=None, vlm_style=None, vlm_comment=None,
            vlm_improvements=[], vlm_cached=False,
            vlm_error=vlm.error if vlm else "vlm_not_run",
            summary_lines=lines,
        )

    # ── Случай: обе фазы пройдены ─────────────────────────────────────────────
    overall = vlm.score >= VLM_PASS_SCORE
    lines = _build_full_summary(tech_checks, vlm, overall)

    return ValidationReport(
        building_type=building_type, player=player,
        overall_passed=overall, tech_passed=True, tech_checks=tech_checks,
        vlm_score=vlm.score, vlm_style=vlm.style_rating,
        vlm_comment=vlm.comments, vlm_improvements=vlm.improvements,
        vlm_cached=vlm.cached, vlm_error=None,
        summary_lines=lines,
    )


# ── Построение текста feedback ─────────────────────────────────────────────────

def _build_tech_fail_summary(checks: list[TechCheck]) -> list[str]:
    lines = ["§c[ImmersiveCiv] ✘ Техническая проверка провалена. Здание не принято."]
    for c in checks:
        icon = "§a✔" if c.ok else "§c✘"
        lines.append(f"  {icon} §7{c.name.replace('_', ' ')}§r: {c.message}")
    lines.append("§7Исправь проблемы и проверь снова командой /validatebuilding.")
    return lines


def _build_full_summary(
    checks: list[TechCheck],
    vlm: VLMResult,
    overall: bool,
) -> list[str]:
    lines = []

    if overall:
        lines.append(f"§a[ImmersiveCiv] ✔ Здание принято! Оценка VLM: {vlm.score}/100 ({vlm.style_rating})")
    else:
        lines.append(f"§c[ImmersiveCiv] ✘ Визуальная оценка недостаточна: {vlm.score}/100 (нужно ≥{VLM_PASS_SCORE})")

    # Прогресс-бар VLM
    lines.append(_score_bar(vlm.score))

    # Комментарий VLM
    if vlm.comments:
        lines.append(f"§7{vlm.comments}")

    # Улучшения
    if not overall and vlm.improvements:
        lines.append("§eЧто улучшить:")
        for tip in vlm.improvements:
            lines.append(f"  §e• {tip}")

    # Пометка о кеше
    if vlm.cached:
        lines.append("§8(результат из кеша — постройка не изменилась)")

    return lines


def _build_free_summary(vlm: VLMResult, bonus: bool) -> list[str]:
    lines = []
    if bonus:
        lines.append(f"§a[ImmersiveCiv] Свободная постройка оценена: {vlm.score}/100 — малый бонус начислен!")
    else:
        lines.append(f"§e[ImmersiveCiv] Свободная постройка оценена: {vlm.score}/100 (нужно ≥{FREE_BONUS_SCORE} для бонуса)")
    lines.append(_score_bar(vlm.score))
    if vlm.comments:
        lines.append(f"§7{vlm.comments}")
    if not bonus and vlm.improvements:
        lines.append("§eЧто улучшить:")
        for tip in vlm.improvements:
            lines.append(f"  §e• {tip}")
    return lines


def _score_bar(score: int) -> str:
    """Текстовый прогресс-бар: §a████§c░░ 72/100"""
    filled = round(score / 10)
    empty  = 10 - filled
    color  = "§a" if score >= VLM_PASS_SCORE else ("§e" if score >= 40 else "§c")
    bar    = color + "█" * filled + "§8" + "░" * empty
    return f"  [{bar}§r] {score}/100"
