import logging
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from .api.routes import router
from .api.ml_routes import router as ml_router
from .config import settings

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logger = logging.getLogger("prs-ai-service")

app = FastAPI(
    title=settings.service_name,
    version=settings.version,
    description="Grounded AI Intelligence Layer for SYNCORA (Project Resource Allocation & Scheduling System)"
)

cors_origins = [o.strip() for o in settings.cors_allowed_origins.split(",") if o.strip()]

app.add_middleware(
    CORSMiddleware,
    allow_origins=cors_origins if cors_origins else ["http://localhost:5173", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["*"],
)

app.include_router(router)
app.include_router(ml_router)

@app.on_event("startup")
async def startup_event():
    logger.info(f"Starting {settings.service_name} on port {settings.port} (Provider: {settings.llm_provider})")

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host=settings.host, port=settings.port, reload=True)
