import json
import os
import sys
import time
import logging
import psycopg2
import redis

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from training.trainer import Trainer, TemporalKGDataset
from mining.pattern_miner import FrequentPatternMiner, CausalDiscovery
from evaluation.evaluator import Evaluator

logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

REDIS_HOST = os.environ.get('REDIS_HOST', 'localhost')
REDIS_PORT = int(os.environ.get('REDIS_PORT', 6379))
DB_HOST = os.environ.get('DB_HOST', 'localhost')
DB_PORT = os.environ.get('DB_PORT', '5432')
DB_NAME = os.environ.get('DB_NAME', 'temporal_kg')
DB_USER = os.environ.get('DB_USER', 'tkg')
DB_PASSWORD = os.environ.get('DB_PASSWORD', 'tkg_secret_2024')
TASK_QUEUE = os.environ.get('TASK_QUEUE', 'tkg:ml:tasks')
RESULT_QUEUE = os.environ.get('RESULT_QUEUE', 'tkg:ml:results')
MODEL_SAVE_DIR = os.environ.get('MODEL_SAVE_DIR', '/app/models')

DB_CONFIG = {
    'host': DB_HOST,
    'port': DB_PORT,
    'database': DB_NAME,
    'user': DB_USER,
    'password': DB_PASSWORD
}


def update_job_status(redis_client, job_id, status, result=None, error=None):
    payload = {
        'jobId': job_id,
        'status': status,
        'timestamp': time.time()
    }
    if result:
        payload['result'] = result
    if error:
        payload['error'] = error
    redis_client.publish(RESULT_QUEUE, json.dumps(payload))


def handle_train_task(redis_client, task: dict):
    model_type = task.get('modelType', 'ttranse')
    job_id = task.get('jobId')
    params = task.get('params', {})

    if isinstance(params, str):
        try:
            params = json.loads(params)
        except json.JSONDecodeError:
            params = {}

    try:
        dataset = TemporalKGDataset(DB_CONFIG)
        dataset.load_from_db()

        trainer = Trainer(dataset, redis_client, RESULT_QUEUE, MODEL_SAVE_DIR)

        if model_type == 'ttranse':
            result = trainer.train_ttranse(params)
        elif model_type == 'gru':
            result = trainer.train_gru(params)
        else:
            raise ValueError(f"Unknown model type: {model_type}")

        update_job_status(redis_client, job_id, 'COMPLETED', result=json.dumps(result))
        logger.info(f"Training job {job_id} completed: {result}")

    except Exception as e:
        logger.error(f"Training job {job_id} failed: {e}", exc_info=True)
        update_job_status(redis_client, job_id, 'FAILED', error=str(e))


def handle_evaluate_task(redis_client, task: dict):
    model_type = task.get('modelType', 'ttranse')
    job_id = task.get('jobId')
    params = task.get('params', {})

    try:
        dataset = TemporalKGDataset(DB_CONFIG)
        dataset.load_from_db()

        evaluator = Evaluator(dataset, MODEL_SAVE_DIR)
        result = evaluator.evaluate_link_prediction(model_type)

        update_job_status(redis_client, job_id, 'COMPLETED', result=json.dumps(result))
        logger.info(f"Evaluation job {job_id} completed")

    except Exception as e:
        logger.error(f"Evaluation job {job_id} failed: {e}", exc_info=True)
        update_job_status(redis_client, job_id, 'FAILED', error=str(e))


def handle_mine_patterns_task(redis_client, task: dict):
    job_id = task.get('jobId')
    params = task.get('params', {})

    if isinstance(params, str):
        try:
            params = json.loads(params)
        except json.JSONDecodeError:
            params = {}

    try:
        dataset = TemporalKGDataset(DB_CONFIG)
        dataset.load_from_db()

        miner = FrequentPatternMiner(
            min_support=params.get('min_support', 0.01),
            min_confidence=params.get('min_confidence', 0.5),
            max_time_gap_hours=params.get('max_time_gap_hours', 720)
        )

        patterns = miner.mine_frequent_chains(
            dataset.quadruples,
            dataset.id2timestamp,
            dataset.id2relation,
            max_chain_length=params.get('max_chain_length', 3)
        )

        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        for p in patterns:
            cur.execute(
                """INSERT INTO temporal_patterns
                   (antecedent, consequent, support, confidence, avg_time_interval_hours, pattern_type)
                   VALUES (%s, %s, %s, %s, %s, %s)""",
                (p['antecedent'], p['consequent'], p['support'], p['confidence'],
                 p['avg_time_interval_hours'], p['pattern_type'])
            )
        conn.commit()
        cur.close()
        conn.close()

        causal_discovery = CausalDiscovery(
            significance_level=params.get('significance_level', 0.05),
            max_lag=params.get('max_lag', 10)
        )

        time_series = causal_discovery.build_relation_time_series(
            dataset.quadruples, dataset.num_timestamps
        )
        causal_links = causal_discovery.discover_causal_links(time_series, dataset.id2relation)

        conn = psycopg2.connect(**DB_CONFIG)
        cur = conn.cursor()
        for link in causal_links:
            cause_rel_name = link['cause_relation']
            effect_rel_name = link['effect_relation']
            cur.execute("SELECT id FROM relations WHERE name = %s", (cause_rel_name,))
            cause_row = cur.fetchone()
            cur.execute("SELECT id FROM relations WHERE name = %s", (effect_rel_name,))
            effect_row = cur.fetchone()

            if cause_row and effect_row:
                cur.execute(
                    """INSERT INTO causal_links
                       (cause_entity_id, cause_relation_id, effect_entity_id, effect_relation_id,
                        granger_f_statistic, granger_p_value, lag_hours)
                       VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                    (cause_row[0], cause_row[0], effect_row[0], effect_row[0],
                     link['granger_f_statistic'], link['granger_p_value'],
                     float(link.get('lag', 0)))
                )
        conn.commit()
        cur.close()
        conn.close()

        result = {
            'patterns_found': len(patterns),
            'causal_links_found': len(causal_links)
        }

        update_job_status(redis_client, job_id, 'COMPLETED', result=json.dumps(result))
        logger.info(f"Mining job {job_id} completed: {result}")

    except Exception as e:
        logger.error(f"Mining job {job_id} failed: {e}", exc_info=True)
        update_job_status(redis_client, job_id, 'FAILED', error=str(e))


def handle_predict_task(redis_client, task: dict):
    model_type = task.get('modelType', 'ttranse')
    correlation_id = task.get('correlationId')
    subject = task.get('subject')
    relation = task.get('relation')
    obj = task.get('object')
    timestamp = task.get('timestamp')
    top_k = task.get('topK', 10)
    params = task.get('params', {})

    try:
        import torch
        model_path = f"{MODEL_SAVE_DIR}/{model_type}_best.pt"
        checkpoint = torch.load(model_path, map_location='cpu', weights_only=False)
        saved_params = checkpoint.get('params', {})

        entity2id = checkpoint.get('entity2id', {})
        relation2id = checkpoint.get('relation2id', {})
        id2entity = {v: k for k, v in entity2id.items()}

        if model_type == 'ttranse':
            from models.ttranse import TTransE
            timestamp2id = checkpoint.get('timestamp2id', {})
            model = TTransE(
                num_entities=len(entity2id),
                num_relations=len(relation2id),
                num_timestamps=max(len(timestamp2id), 1),
                embedding_dim=saved_params.get('embedding_dim', 100),
                margin=saved_params.get('margin', 1.0),
            )
            model.load_state_dict(checkpoint['model_state_dict'])
            model.eval()

            with torch.no_grad():
                if obj is None:
                    s_id = entity2id.get(subject, 0)
                    r_id = relation2id.get(relation, 0)
                    t_id = timestamp2id.get(timestamp, 0)

                    s_t = torch.tensor([s_id])
                    r_t = torch.tensor([r_id])
                    t_t = torch.tensor([t_id])
                    all_entities = torch.arange(len(entity2id))

                    top_indices, top_scores = model.predict_object(s_t, r_t, t_t, all_entities, top_k=top_k)
                    predictions = []
                    for idx, (ent_idx, score) in enumerate(zip(top_indices.tolist(), top_scores.tolist())):
                        predictions.append({
                            'entity': id2entity.get(ent_idx, str(ent_idx)),
                            'score': score,
                            'rank': idx + 1
                        })
                else:
                    predictions = []

        elif model_type == 'gru':
            from models.gru_model import GRUTemporalModel
            model = GRUTemporalModel(
                num_entities=len(entity2id),
                num_relations=len(relation2id),
                embedding_dim=saved_params.get('embedding_dim', 100),
                hidden_dim=saved_params.get('hidden_dim', 200),
            )
            model.load_state_dict(checkpoint['model_state_dict'])
            model.eval()

            with torch.no_grad():
                if obj is None:
                    s_id = entity2id.get(subject, 0)
                    r_id = relation2id.get(relation, 0)
                    s_t = torch.tensor([s_id])
                    r_t = torch.tensor([r_id])
                    all_entities = torch.arange(len(entity2id))

                    top_indices, top_scores = model.predict_object(s_t, r_t, all_entities, top_k=top_k)
                    predictions = []
                    for idx, (ent_idx, score) in enumerate(zip(top_indices.tolist(), top_scores.tolist())):
                        predictions.append({
                            'entity': id2entity.get(ent_idx, str(ent_idx)),
                            'score': score,
                            'rank': idx + 1
                        })
                else:
                    predictions = []
        else:
            predictions = []

        result = {
            'predictions': predictions,
            'modelType': model_type,
            'metadata': {}
        }

        result_key = f"tkg:prediction:{correlation_id}"
        redis_client.setex(result_key, 300, json.dumps(result))

        logger.info(f"Prediction completed for correlation {correlation_id}")

    except FileNotFoundError:
        logger.error(f"Model not found for prediction: {model_type}")
        result = {
            'predictions': [],
            'modelType': model_type,
            'metadata': {'error': 'Model not trained yet'}
        }
        result_key = f"tkg:prediction:{correlation_id}"
        redis_client.setex(result_key, 300, json.dumps(result))

    except Exception as e:
        logger.error(f"Prediction failed: {e}", exc_info=True)
        result = {
            'predictions': [],
            'modelType': model_type,
            'metadata': {'error': str(e)}
        }
        result_key = f"tkg:prediction:{correlation_id}"
        redis_client.setex(result_key, 300, json.dumps(result))


def handle_incremental_train_task(redis_client, task: dict):
    params = task.get('params', {})
    if isinstance(params, str):
        try:
            params = json.loads(params)
        except json.JSONDecodeError:
            params = {}

    model_types = ['ttranse', 'gru']
    for model_type in model_types:
        try:
            dataset = TemporalKGDataset(DB_CONFIG)
            dataset.load_from_db()
            trainer = Trainer(dataset, redis_client, RESULT_QUEUE, MODEL_SAVE_DIR)
            result = trainer.incremental_train(model_type, params)
            logger.info(f"Incremental training for {model_type}: {result}")
        except Exception as e:
            logger.error(f"Incremental training failed for {model_type}: {e}", exc_info=True)


def main():
    logger.info("ML Worker starting...")
    redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)

    pubsub = redis_client.pubsub()
    pubsub.subscribe(TASK_QUEUE)
    logger.info(f"Subscribed to task queue: {TASK_QUEUE}")

    while True:
        try:
            message = pubsub.get_message(timeout=1)
            if message and message['type'] == 'message':
                task_str = message['data']
                task = json.loads(task_str)
                task_type = task.get('type', '')

                logger.info(f"Received task: {task_type}")

                if task_type == 'TRAIN':
                    handle_train_task(redis_client, task)
                elif task_type == 'EVALUATE':
                    handle_evaluate_task(redis_client, task)
                elif task_type == 'MINE_PATTERNS':
                    handle_mine_patterns_task(redis_client, task)
                elif task_type == 'PREDICT':
                    handle_predict_task(redis_client, task)
                elif task_type == 'INCREMENTAL_TRAIN':
                    handle_incremental_train_task(redis_client, task)
                else:
                    logger.warning(f"Unknown task type: {task_type}")

        except redis.ConnectionError:
            logger.error("Redis connection lost, retrying...")
            time.sleep(5)
            try:
                redis_client = redis.Redis(host=REDIS_HOST, port=REDIS_PORT, decode_responses=True)
                pubsub = redis_client.pubsub()
                pubsub.subscribe(TASK_QUEUE)
            except Exception:
                time.sleep(5)

        except Exception as e:
            logger.error(f"Error in main loop: {e}", exc_info=True)
            time.sleep(1)


if __name__ == '__main__':
    main()
