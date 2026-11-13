-- Recover legacy manual staff registrations even if their status or payload
-- was edited after creation. V1 stores created_at at second precision, while
-- the initial creation audit keeps microseconds, so compare within that second.
UPDATE registration r
JOIN registration_status_history h ON h.registration_id = r.id
SET r.source = 'BIURO'
WHERE r.source = 'FORMULARZ'
  AND r.registration_type = 'STAFF'
  AND h.previous_status = 'NEW'
  AND h.new_status = 'ACCEPTED'
  AND h.changed_at >= r.created_at
  AND h.changed_at < DATE_ADD(r.created_at, INTERVAL 1 SECOND)
  -- Only an acceptance that is the first recorded transition is evidence of
  -- acceptance during creation. Later acceptance after another decision is
  -- not sufficient to infer that the registration originated in Biuro.
  AND NOT EXISTS (
      SELECT 1
      FROM registration_status_history earlier
      WHERE earlier.registration_id = r.id
        AND (earlier.changed_at < h.changed_at
          OR (earlier.changed_at = h.changed_at AND earlier.id < h.id))
  );
