from pathlib import Path

import redis
from dependency_injector import containers, providers
import json

ENVIRONMENT_CONFIG_PATH = Path(r"/home/winig/projects/MacroSentiment/sentiment-analysis/configs/environment.json")
CLASSIFICATIONS_PATH = Path(r"/home/winig/projects/MacroSentiment/sentiment-analysis/configs/classifications.json")


class Container(containers.DynamicContainer):
    environment_config = providers.Configuration()
    environment_config.from_json(ENVIRONMENT_CONFIG_PATH, True)
    redis_host: str = providers.Object(environment_config.REDIS_HOST())
    redis_port: int = providers.Object(environment_config.REDIS_PORT())
    psql_host: str = providers.Object(environment_config.PSQL_HOST())
    psql_port: int = providers.Object(environment_config.PSQL_PORT())
    psql_url: str = providers.Object(environment_config.PSQL_URL())
    psql_username: str = providers.Object(environment_config.PSQL_USERNAME())
    psql_password: str = providers.Object(environment_config.PSQL_PASSWORD())
    psql_db: str = providers.Object(environment_config.PSQL_DATABASE())
    conn_info = providers.Singleton(
        lambda host, port, user, db, password: f"host={host} port={port} user={user} dbname={db} password={password}",
        host=environment_config.PSQL_HOST,
        port=environment_config.PSQL_PORT,
        user=environment_config.PSQL_USERNAME,
        db=environment_config.PSQL_DATABASE,
        password=environment_config.PSQL_PASSWORD
    )
    labels: list = list()
    with open(CLASSIFICATIONS_PATH) as file:
        labels = json.load(file)
    classification_labels = providers.Object(labels)
    
    


    


container = Container()
