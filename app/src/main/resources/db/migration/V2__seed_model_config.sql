-- ============================================================
-- V2__seed_model_config.sql
-- Seeds enabled free models into model_config.
-- INSERT IGNORE: idempotent — re-running is safe.
--
-- To update models: add/remove rows here AND update
-- OpenRouterClient.java FREE_MODELS set. Then create a new
-- migration (V5, V6, ...) — never edit this file retroactively.
-- ============================================================

INSERT IGNORE INTO model_config (model_id, enabled) VALUES
    ('nvidia/nemotron-nano-9b-v2:free',                              TRUE),
    ('nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free',           TRUE),
    ('nvidia/nemotron-3-super-120b-a12b:free',                       TRUE),
    ('nvidia/nemotron-3-nano-30b-a3b:free',                          TRUE),
    ('nvidia/nemotron-nano-12b-v2-vl:free',                          TRUE),
    ('meta-llama/llama-3.3-70b-instruct:free',                       TRUE),
    ('meta-llama/llama-3.2-3b-instruct:free',                        TRUE),
    ('deepseek/deepseek-v4-flash:free',                              TRUE),
    ('qwen/qwen3-coder:free',                                        TRUE),
    ('qwen/qwen3-next-80b-a3b-instruct:free',                        TRUE),
    ('google/gemma-4-31b-it:free',                                   TRUE),
    ('google/gemma-4-26b-a4b-it:free',                               TRUE),
    ('openai/gpt-oss-120b:free',                                     TRUE),
    ('openai/gpt-oss-20b:free',                                      TRUE),
    ('poolside/laguna-xs.2:free',                                    TRUE),
    ('poolside/laguna-m.1:free',                                     TRUE),
    ('liquid/lfm-2.5-1.2b-thinking:free',                            TRUE),
    ('liquid/lfm-2.5-1.2b-instruct:free',                            TRUE),
    ('moonshotai/kimi-k2.6:free',                                    TRUE),
    ('z-ai/glm-4.5-air:free',                                        TRUE),
    ('cognitivecomputations/dolphin-mistral-24b-venice-edition:free', TRUE),
    ('nousresearch/hermes-3-llama-3.1-405b:free',                    TRUE),
    ('openrouter/owl-alpha',                                          TRUE),
    ('openrouter/free',                                               TRUE);
