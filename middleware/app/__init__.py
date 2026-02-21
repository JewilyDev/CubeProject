from fastapi import FastAPI
from .routes import ws_router, api_router


def create_app() -> FastAPI:
    app = FastAPI(title="ImmersiveCiv Middleware", version="0.1.0")
    app.include_router(ws_router)
    app.include_router(api_router, prefix="/api")
    return app
