-- ============================================================
-- V2__seed_model_config.sql
-- Seeds enabled free models into model_config.
-- INSERT IGNORE: idempotent — re-running is safe.
--
-- created_at is intentionally omitted: the column DEFAULT
-- CURRENT_TIMESTAMP(6) (defined in V1) supplies the value
-- automatically for raw SQL inserts.
--
-- To update models: add/remove rows here AND update
-- OpenRouterClient.java FREE_MODELS set. Then create a new
-- migration (V5, V6, ...) — never edit this file retroactively.
-- ============================================================

INSERT IGNORE INTO model_config (model_id, enabled) VALUES
    ('nvidia/nemotron-nano-9b-v2:free',                              b'1'),
    ('nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',           b'1'),
    ('nvidia/nemotron-3-super-120b-a12b:free',                       b'1'),
    ('nvidia/nemotron-3-nano-30b-a3b:free',                          b'1'),
    ('nvidia/nemotron-nano-12b-v2-vl:free',                          b'1'),
    ('meta-llama/llama-3.3-70b-instruct:free',                       b'1'),
    ('meta-llama/llama-3.2-3b-instruct:free',                        b'1'),
    ('deepseek/deepseek-v4-flash:free',                              b'1'),
    ('qwen/qwen3-coder:free',                                        b'1'),
    ('qwen/qwen3-next-80b-a3b-instruct:free',                        b'1'),
    ('google/gemma-4-31b-it:free',                                   b'1'),
    ('google/gemma-4-26b-a4b-it:free',                               b'1'),
    ('openai/gpt-oss-120b:free',                                     b'1'),
    ('openai/gpt-oss-20b:free',                                      b'1'),
    ('poolside/laguna-xs.2:free',                                    b'1'),
    ('poolside/laguna-m.1:free',                                     b'1'),
    ('liquid/lfm-2.5-1.2b-thinking:free',                            b'1'),
    ('liquid/lfm-2.5-1.2b-instruct:free',                            b'1'),
    ('moonshotai/kimi-k2.6:free',                                    b'1'),
    ('z-ai/glm-4.5-air:free',                                        b'1'),
    ('cognitivecomputations/dolphin-mistral-24b-venice-edition:free', b'1'),
    ('nousresearch/hermes-3-llama-3.1-405b:free',                    b'1'),
    ('openrouter/owl-alpha',                                          b'1'),
    ('openrouter/free',                                               b'1');
