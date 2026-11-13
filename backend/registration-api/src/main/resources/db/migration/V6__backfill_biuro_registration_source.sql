-- Legacy manual staff registrations were created as ACCEPTED with the initial
-- status audit timestamp matching their creation/update timestamp.
UPDATE registration r
JOIN registration_status_history h ON h.registration_id = r.id
SET r.source = 'BIURO'
WHERE r.registration_type = 'STAFF'
  AND h.previous_status = 'NEW'
  AND h.new_status = 'ACCEPTED'
  AND h.changed_at >= r.created_at
  AND h.changed_at < DATE_ADD(r.created_at, INTERVAL 1 SECOND)
  AND r.updated_at = r.created_at;
