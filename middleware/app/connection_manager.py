"""
ConnectionManager — управляет активными WebSocket-соединениями от Minecraft-клиентов.
Один сервер может обслуживать несколько подключённых инстансов (мультисервер).
"""

import asyncio
import json
import logging
from fastapi import WebSocket

logger = logging.getLogger("immersiveciv.ws")


class ConnectionManager:
    def __init__(self) -> None:
        # Словарь id соединения → WebSocket
        self._connections: dict[str, WebSocket] = {}

    async def connect(self, connection_id: str, ws: WebSocket) -> None:
        await ws.accept()
        self._connections[connection_id] = ws
        logger.info("Minecraft подключился: %s  (всего: %d)", connection_id, len(self._connections))

    def disconnect(self, connection_id: str) -> None:
        self._connections.pop(connection_id, None)
        logger.info("Minecraft отключился: %s  (всего: %d)", connection_id, len(self._connections))

    async def send_to(self, connection_id: str, payload: dict) -> bool:
        """Отправить JSON конкретному соединению. Возвращает False если не найдено."""
        ws = self._connections.get(connection_id)
        if ws is None:
            return False
        await ws.send_text(json.dumps(payload))
        return True

    async def broadcast(self, payload: dict) -> None:
        """Отправить JSON всем подключённым клиентам."""
        text = json.dumps(payload)
        dead: list[str] = []
        for cid, ws in list(self._connections.items()):
            try:
                await ws.send_text(text)
            except Exception:
                dead.append(cid)
        for cid in dead:
            self.disconnect(cid)

    @property
    def active_count(self) -> int:
        return len(self._connections)


# Глобальный синглтон
manager = ConnectionManager()
