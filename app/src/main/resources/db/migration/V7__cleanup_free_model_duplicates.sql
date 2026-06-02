-- V7: Remove base model IDs that have a :free counterpart.
--
-- Cause: PRD-004 startup sync inserted both X (pricing=0) and X:free (:free suffix)
-- as distinct rows. The UI displays both with the same name because display logic
-- strips the :free suffix, making them appear as duplicates.
--
-- Fix: delete the base (non-:free) row when a :free variant exists.
-- Models seeded without :free that have no :free counterpart (e.g. openrouter/owl-alpha,
-- openrouter/free) are untouched because the EXISTS subquery won't match them.

DELETE mc FROM model_config mc
WHERE  mc.model_id NOT LIKE '%:free'
  AND  EXISTS (
    SELECT 1 FROM (SELECT model_id FROM model_config) AS mc2
    WHERE mc2.model_id = CONCAT(mc.model_id, ':free')
  );
