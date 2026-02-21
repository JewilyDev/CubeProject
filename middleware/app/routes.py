"""
Роутеры FastAPI:
  - ws_router  : WebSocket /ws  — постоянное соединение с Minecraft
  - api_router : REST /api/...  — разовые запросы (Middleware → Middleware, тесты)
"""

import logging
import uuid
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from .connection_manager import manager
from .message_handler import dispatch

logger = logging.getLogger("immersiveciv.routes")

# ── WebSocket ─────────────────────────────────────────────────────────────────

ws_router = APIRouter()


@ws_router.websocket("/ws")
async def websocket_endpoint(ws: WebSocket):
    connection_id = str(uuid.uuid4())[:8]
    await manager.connect(connection_id, ws)
    try:
        while True:
            raw = await ws.receive_text()
            logger.debug("[%s] → %s", connection_id, raw[:300])
            await dispatch(connection_id, raw)
    except WebSocketDisconnect:
        manager.disconnect(connection_id)
    except Exception as e:
        logger.error("[%s] Неожиданная ошибка: %s", connection_id, e, exc_info=True)
        manager.disconnect(connection_id)


# ── REST API ──────────────────────────────────────────────────────────────────

api_router = APIRouter()


@api_router.get("/status")
async def status():
    """Возвращает состояние Middleware — удобно для healthcheck."""
    return {
        "status": "ok",
        "connected_clients": manager.active_count,
    }


@api_router.post("/broadcast")
async def broadcast(payload: dict):
    """Отправить произвольный JSON всем подключённым Minecraft-клиентам."""
    await manager.broadcast(payload)
    return {"sent_to": manager.active_count}
