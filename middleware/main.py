"""
ImmersiveCiv Middleware
Точка входа — запускает FastAPI + WebSocket-сервер для общения с Minecraft-модом.

Запуск:
    pip install -r requirements.txt
    python main.py
"""

import uvicorn
from app import create_app

if __name__ == "__main__":
    app = create_app()
    uvicorn.run(
        app,
        host="0.0.0.0",
        port=8765,
        log_level="info",
    )
