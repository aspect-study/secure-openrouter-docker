-- V6: Disable free models that have been removed from OpenRouter's free tier.
-- Verified 2026-06-01: these models return 404 "No endpoints found".
-- Using enabled = b'0' (BIT column — Hibernate 6 / MySQL8Dialect maps Java boolean → BIT).

UPDATE model_config
SET    enabled = b'0'
WHERE  model_id IN (
    'deepseek/deepseek-v4-flash:free'
);
