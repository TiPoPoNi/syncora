import os
from pydantic import BaseModel
from dotenv import load_dotenv

load_dotenv()

class Settings(BaseModel):
    service_name: str = "SYNCORA AI Intelligence Service"
    version: str = "1.0.0"
    port: int = int(os.getenv("AI_SERVICE_PORT", "8000"))
    host: str = os.getenv("AI_SERVICE_HOST", "127.0.0.1")
    cors_allowed_origins: str = os.getenv("CORS_ALLOWED_ORIGINS", "http://localhost:5173,http://localhost:8080")
    
    # Provider: "mock", "openai", "gemini", "anthropic"
    llm_provider: str = os.getenv("LLM_PROVIDER", "mock").lower()
    
    # API Keys (read from environment only, never hard-coded)
    openai_api_key: str = os.getenv("OPENAI_API_KEY", "")
    gemini_api_key: str = os.getenv("GEMINI_API_KEY", "")
    anthropic_api_key: str = os.getenv("ANTHROPIC_API_KEY", "")
    
    # Timeouts & Limits
    request_timeout_seconds: float = float(os.getenv("REQUEST_TIMEOUT_SECONDS", "30.0"))
    max_context_length_chars: int = int(os.getenv("MAX_CONTEXT_LENGTH_CHARS", "20000"))

settings = Settings()
