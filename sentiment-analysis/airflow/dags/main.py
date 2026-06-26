from datetime import datetime, timedelta

from dependencyinjector.container import container
from langchain_text_splitters import RecursiveCharacterTextSplitter
import logging
import polars
import json
from polars.dataframe import DataFrame
import psycopg
import duckdb
from airflow.sdk import task, dag
from playwright.sync_api import sync_playwright
from playwright_stealth import Stealth
from trafilatura import extract
from transformers import BertTokenizer, BertForSequenceClassification, Pipeline, pipeline
from gliner import GLiNER
import os

NEED_SCRAPED_CSV = "NEED_SCRAPED.csv"
SUCCESSFULLY_SCRAPED = "SUCCESSFULLY_SCRAPED.json"
FAILED_SCRAPED = "FAILED_SCRAPED.txt"

conn_info = container.conn_info()
postgres_url = container.psql_url()
classification_labels = container.classification_labels()
# Configure logging to output INFO messages and higher to the console
logger = logging.getLogger(__name__)

def get_temp_file_path(**context) -> str:
    temp_file_path = r"temp/{}/".format(context['run_id'])
    return temp_file_path

def generate_path(file_name,**context) -> str:
    return get_temp_file_path(**context) + file_name

@dag(dag_id="SentimentAnalysis",
     description="Financial News Sentiment Analysis",
     start_date=datetime(2026,6,24),
     schedule=timedelta(minutes=10))
def sentiment_analysis() -> None:
    @task(retries=3, task_id="SentimentAnalysis.get_news_articles")
    def query_news_articles(**context) -> list[str]:
        news_articles = list()
        schema = list()
        with duckdb.connect() as conn:
            conn.execute("INSTALL POSTGRES")
            conn.execute("LOAD POSTGRES")
            conn.execute(f"ATTACH '{conn_info}' AS postgres_db (TYPE POSTGRES)")
            conn.execute("SELECT * FROM postgres_db.raw_api_news_v1 WHERE transformed_at is NULL")
            schema = [col[0] for col in conn.description]
            news_articles.extend(conn.fetchall())
        df = DataFrame(data=news_articles,schema=schema, orient='row')
        file_path = generate_path(NEED_SCRAPED_CSV,**context)
        df.write_csv(file_path)

    @task(task_id="SentimentAnalysis.setup")
    def setup(**context):
        temp_file_path = get_temp_file_path(**context)
        os.makedirs(temp_file_path,exist_ok=True)

        

    @task(retries=3, task_id="SentimentAnalysis.scrape_news_urls")
    def scrape_news_urls(**context) -> dict[str, str]:
        file_path = generate_path(NEED_SCRAPED_CSV,**context)
        df = polars.scan_csv(file_path)
        df = df.collect()
        succesfully_scraped = dict()
        failed = list()
        with Stealth().use_sync(sync_playwright()) as pw:
            browser = pw.chromium.launch(executable_path='/usr/bin/chromium-browser',
                                         args=['--disable-blink-features=AutomationControlled',
                                               '--disable-dev-shm-usage',
                                               '--no-sandbox',
                                               '--disable-setuid-sandbox',
                                               '--disable-infobars',
                                               '--window-size=1920,1080',
                                               ])
            pg_context = browser.new_context(ignore_https_errors=True,
                                             user_agent='Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
                                             viewport={'width': 1920, 'height': 1080},
                                             locale='en-US',
                                             timezone_id='America/New_York',
                                             extra_http_headers={
                                                 'Accept-Language': 'en-US,en;q=0.9',
                                                 'Accept': 'text/html,application/xhtml+xml,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
                                             },
                                             java_script_enabled=False
                                             )
            page = pg_context.new_page()
            for url in df['url']:
                try:
                    page.goto(url)
                    downloaded = page.content()
                    result = extract(downloaded)
                    succesfully_scraped[url] = result
                except Exception as e:
                    logger.warning(f"Failed to scrape {url}: {e}")
                    failed.append(url)
                    continue
        logger.info(f"SentimentAnalysis scraped content: {succesfully_scraped}")
        logger.info(f"failed to scrape urls {failed}")
        successfull_path = generate_path(SUCCESSFULLY_SCRAPED,**context)
        failed_path = generate_path(FAILED_SCRAPED,**context)
        try:
            with open(successfull_path,'x') as file:
                json.dump(succesfully_scraped,successfull_path)
            with open(failed_path, 'x') as file:
                file.writelines(failed)
        except Exception as e:
            logger.error(e)

        

    @task(task_id="SentimentAnalysis.update_content")
    def update_content(**context):
        content_map: dict = dict()
        try:
            with open(generate_path(SUCCESSFULLY_SCRAPED,**context)) as file:
                content_map = json.load(file)
        except Exception as e:
            logger.error(e)
        updates = [(url, val) for url, val in content_map.items()]
        successfully_updated = list()
        attempts = 0
        while attempts < 3:
            attempts += 1
            try:
                with psycopg.connect(conn_info) as conn:
                    with conn.cursor() as cursor:
                        cursor.execute("""
                                            UPDATE raw_api_news_v1 AS t
                                            SET content = v.content
                                            FROM unnest(%s::text[], %s::text[]) AS v(url, content)
                                            WHERE t.url = v.url
                                            RETURNING t.*
                                        """,( [url for url, _ in updates],
                                             [content for _, content in updates]
                                        ))
                        conn.commit()
                        successfully_updated.extend(cursor.fetchall())
                        successfull_urls = [article[0] for article in successfully_updated]
                        content_map = {url: val for (url, val) in content_map.items() if url not in successfull_urls}
                        break
            except Exception as e:
                logger.error(f"failed to update on attempt {attempts}", exc_info=True)
        logger.info(f"succesfully updated {len(successfully_updated)} articles")
        context['ti'].xcom_push(key="successfully_updated", value=successfully_updated)
        context['ti'].xcom_push(key="failed_updates", value=content_map)

    @task(task_id="SentimentAnalysis.sentiment",retries=3)
    def run_sentiment_model(**context):
        content_dict = dict()
        try:
            with open(generate_path(SUCCESSFULLY_SCRAPED,**context),'r+') as file:
                content_dict = json.load(file)
        except Exception as e:
            logger.error(e)
        text = [value for _,value in content_dict.items()]
        sentiment_tokenizer = BertTokenizer.from_pretrained("ProsusAI/finbert")
        sentiment_model = BertForSequenceClassification.from_pretrained("ProsusAI/finbert")
        sentiment_analysis = pipeline(task='sentiment-analysis',
                                      tokenizer=sentiment_tokenizer,
                                      model=sentiment_model,
                                      device=-1)
        sentiment = analyze_articles(text, sentiment_analysis)
        logger.info(f"sentiment{sentiment[0:10]}")

    @task(task_id="SentimentAnalysis.classification",retries=3)
    def run_classification_model(**context):
        content_dict = dict()
        try:
            with open(generate_path(SUCCESSFULLY_SCRAPED,**context),'r+') as file:
                content_dict = json.load(file)
        except Exception as e:
            logger.error(e)
        text = [value for _,value in content_dict.items()]
        classification_model = GLiNER.from_pretrained("gliner-community/gliner_medium-v2.5",load_tokenizer=True)
        for key,value in classification_labels:
            labels = value
            classified = classification_model.inference(text,labels,False,multi_label=True,batch_size=16)
            num = 1
            for batch in classified:
                for classification in batch:
                    logger.info(f"batch {num}:{classification['text']} => {classification["label"]}")
                num += 1


    setup_dag = setup()
    get_news_articles = query_news_articles()
    scraped_content = scrape_news_urls()
    sentiment = run_sentiment_model()
    classification = run_classification_model()
    setup_dag >> get_news_articles >> scraped_content
    scraped_content >> sentiment
    scraped_content >> classification


def analyze_articles(articles: list[str], nlp: Pipeline, chunk_size=450) -> list[str]:
    all_chunks = []
    article_chunk_counts = []
    splitter = RecursiveCharacterTextSplitter(chunk_size=1100)
    for text in articles:
        chunks = splitter.split_text(text) if text and text.strip() else []
        logger.info(f"chunks size: {len(chunks)}")
        all_chunks.extend(chunks)
        article_chunk_counts.append(len(chunks))
    if not all_chunks:
        return []

    all_results = nlp(all_chunks, batch_size=16)

    sentiments = []
    idx = 0
    for count in article_chunk_counts:
        if count == 0:
            sentiments.append('neutral')
            continue
        article_results = all_results[idx:idx + count]
        scores = {'positive': 0, 'negative': 0, 'neutral': 0}
        for r in article_results:
            scores[r['label'].lower()] += r['score']
        avg = {k: v / count for k, v in scores.items()}
        sentiments.append(max(avg, key=avg.get))
        idx += count
    logger.info(f"articles count: {len(articles)}")
    logger.info(f"all_chunks count: {len(all_chunks)}")
    logger.info(f"all_results count: {len(all_results)}")
    logger.info(f"article_chunk_counts: {article_chunk_counts}")

    return sentiments



sentiment_analysis()
