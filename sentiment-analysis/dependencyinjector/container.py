import json
import os
from pathlib import Path

from dependency_injector import containers, providers
from dotenv import load_dotenv
from gliner import GLiNER
from transformers import (
    AutoModel,
    AutoTokenizer,
    BertForSequenceClassification,
    BertTokenizer,
    Pipeline,
    pipeline,
)

BASE_DIR = Path(__file__).parent.parent
CLASSIFICATIONS_PATH = BASE_DIR / "configs" / "classifications.json"
REDIS_HOST = "REDIS_HOST"
REDIS_PORT = "REDIS_PORT"
PSQL_HOST = "PSQL_HOST"
PSQL_PORT = "PSQL_PORT"
PSQL_URL = "PSQL_URL"
PSQL_USER = "PSQL_USER"
PSQL_PASSWORD = "PSQL_PASSWORD"
PSQL_DB = "PSQL_DB"


class Container(containers.DynamicContainer):
    classification_config = providers.Configuration()
    classification_config.from_json(CLASSIFICATIONS_PATH, True)
    # load_dotenv()
    redis_host = providers.Object(os.getenv(REDIS_HOST, "localhost"))
    redis_port = providers.Object(os.getenv(REDIS_PORT, 6379))
    psql_host = providers.Object(os.getenv(PSQL_HOST, "localhost"))
    psql_port = providers.Object(os.getenv(PSQL_PORT, 5432))
    psql_url = providers.Object(os.getenv(PSQL_URL, "localhost"))
    psql_username = providers.Object(os.getenv(PSQL_USER, "user"))
    psql_password = providers.Object(os.getenv(PSQL_PASSWORD, "password"))
    psql_db = providers.Object(os.getenv(PSQL_DB, "postgres"))
    classification_labels = providers.Object(classification_config)
    sentiment_tokenizer = BertTokenizer.from_pretrained("ProsusAI/finbert")
    sentiment_model = BertForSequenceClassification.from_pretrained("ProsusAI/finbert")
    sentiment_analysis = pipeline(
        task="sentiment-analysis",
        tokenizer=sentiment_tokenizer,
        model=sentiment_model,
        device=-1,
    )
    sentiment_analysis_pipeline = providers.Object(sentiment_analysis)
    classification_model = providers.Object(
        GLiNER.from_pretrained(
            "gliner-community/gliner_medium-v2.5", load_tokenizer=True
        )
    )
    summarization_model = pipeline("summarization", model="facebook/bart-large-cnn")
    summarization_model = providers.Object(summarization_model)
    embedding_tokenizer = providers.Object(
        AutoTokenizer.from_pretrained("BAAI/bge-large-en-v1.5")
    )
    embedding_model = providers.Object(
        AutoModel.from_pretrained("BAAI/bge-large-en-v1.5")
    )
    conn_info = providers.Singleton(
        lambda host, port, user, db, password: (
            f"host={host} port={port} user={user} dbname={db} password={password}"
        ),
        host=redis_host(),
        port=psql_port(),
        user=psql_username(),
        db=psql_db(),
        password=psql_password(),
    )
    labels: list = list()
    with open(CLASSIFICATIONS_PATH) as file:
        labels = json.load(file)
    classification_labels = providers.Object(labels)


container = Container()
