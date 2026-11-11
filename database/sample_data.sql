-- Campus Voice - Sample data (run after schema.sql)
-- NOTE: password_hash values are dummy placeholders, not real hashes.
-- This script is for initial setup, not for rerunning after a partial execution.
USE campus_voice;

INSERT INTO departments (department_name) VALUES
 ('Maintenance'),          -- 1
 ('IT Services'),          -- 2
 ('Hostel Administration'),-- 3
 ('Housekeeping'),         -- 4
 ('Library'),              -- 5
 ('Transport');            -- 6  (no complaints: demonstrates LEFT JOIN)

INSERT INTO users (name, email, password_hash, role, department_id) VALUES
 ('Dr. Anita Rao',   'admin@campus.edu',   'dummyhash_admin',  'ADMIN',   NULL),-- 1, global admin
 ('Ravi Kumar',      'ravi.staff@campus.edu',  'dummyhash_s1', 'STAFF',   1),   -- 2, Maintenance
 ('Meena Joshi',     'meena.staff@campus.edu', 'dummyhash_s2', 'STAFF',   2),   -- 3, IT Services
 ('Parikshit Negi',  'parikshit@campus.edu',   'dummyhash_u1', 'STUDENT', NULL),-- 4
 ('Shikhar Pundir',  'shikhar@campus.edu',     'dummyhash_u2', 'STUDENT', NULL),-- 5
 ('Om Krishali',     'om@campus.edu',          'dummyhash_u3', 'STUDENT', NULL),-- 6
 ('Sameer Singh',    'sameer@campus.edu',      'dummyhash_u4', 'STUDENT', NULL);-- 7

INSERT INTO complaints
 (user_id, department_id, title, description, location, priority, status, assigned_to, created_at, resolved_at)
VALUES
 (4, 1, 'Broken ceiling fan',        'Fan in room makes noise and stops.',        'Hostel A-204',   'MEDIUM', 'RESOLVED',    2, '2026-09-01 09:00:00', '2026-09-03 15:30:00'), -- 1
 (4, 2, 'Wi-Fi not working',         'No internet in the study block.',           'Library Block',  'HIGH',   'IN_PROGRESS', 3, '2026-09-10 10:15:00', NULL),                  -- 2
 (5, 3, 'No hot water',              'Geyser not working for 3 days.',            'Hostel B-110',   'URGENT', 'PENDING',     NULL,'2026-09-12 07:45:00', NULL),                  -- 3
 (5, 4, 'Dirty washrooms',           'Washroom on 2nd floor not cleaned.',        'Academic Block 2','LOW',   'PENDING',     NULL,'2026-09-14 11:00:00', NULL),                  -- 4
 (6, 1, 'Water leakage',             'Roof leaking into classroom during rain.',  'Room C-12',      'URGENT', 'IN_PROGRESS', 2, '2026-09-15 08:30:00', NULL),                  -- 5
 (6, 5, 'AC not cooling',            'Reading hall AC is not cooling.',           'Library Hall',   'MEDIUM', 'RESOLVED',    NULL, '2026-09-05 14:00:00', '2026-09-07 12:00:00'), -- 6
 (7, 2, 'Projector faulty',          'Projector flickers during lectures.',       'Room D-03',      'HIGH',   'PENDING',     NULL,'2026-09-16 09:20:00', NULL),                  -- 7
 (7, 2, 'Lab PC not booting',        'Three PCs fail to boot.',                   'Computer Lab 1', 'URGENT', 'IN_PROGRESS', 3, '2026-09-17 13:10:00', NULL),                  -- 8
 (4, 3, 'Mess hygiene issue',        'Utensils not washed properly.',              'Hostel Mess',    'HIGH',   'REJECTED',    NULL, '2026-09-08 19:00:00', NULL),                  -- 9
 (5, 1, 'Broken door lock',          'Room door lock jammed.',                    'Hostel B-110',   'MEDIUM', 'PENDING',     NULL,'2026-09-18 16:40:00', NULL);                  -- 10

-- Resolve complaint and actor IDs from stable values, not assumed AUTO_INCREMENT IDs.
-- This also lets this section complete after a partial seed-data run.
INSERT INTO complaint_history
 (complaint_id, changed_by, old_status, new_status, remarks, changed_at)
SELECT c.complaint_id,
       CASE WHEN h.actor_email IS NULL THEN c.user_id ELSE actor.user_id END,
       h.old_status, h.new_status,
       h.remarks, h.changed_at
FROM (
    SELECT 'Broken ceiling fan' AS title, '2026-09-01 09:00:00' AS complaint_created_at,
           NULL AS actor_email, NULL AS old_status,
           'PENDING' AS new_status, 'Complaint submitted' AS remarks,
           '2026-09-01 09:00:00' AS changed_at
    UNION ALL SELECT 'Broken ceiling fan', '2026-09-01 09:00:00', 'ravi.staff@campus.edu', 'PENDING',
           'IN_PROGRESS', 'Technician assigned', '2026-09-02 10:00:00'
    UNION ALL SELECT 'Broken ceiling fan', '2026-09-01 09:00:00', 'ravi.staff@campus.edu', 'IN_PROGRESS',
           'RESOLVED', 'Fan replaced', '2026-09-03 15:30:00'
    UNION ALL SELECT 'Wi-Fi not working', '2026-09-10 10:15:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-10 10:15:00'
    UNION ALL SELECT 'Wi-Fi not working', '2026-09-10 10:15:00', 'meena.staff@campus.edu', 'PENDING',
           'IN_PROGRESS', 'Network team investigating', '2026-09-11 09:00:00'
    UNION ALL SELECT 'No hot water', '2026-09-12 07:45:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-12 07:45:00'
    UNION ALL SELECT 'Dirty washrooms', '2026-09-14 11:00:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-14 11:00:00'
    UNION ALL SELECT 'Water leakage', '2026-09-15 08:30:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-15 08:30:00'
    UNION ALL SELECT 'Water leakage', '2026-09-15 08:30:00', 'ravi.staff@campus.edu', 'PENDING',
           'IN_PROGRESS', 'Roof inspection started', '2026-09-15 12:00:00'
    UNION ALL SELECT 'AC not cooling', '2026-09-05 14:00:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-05 14:00:00'
    UNION ALL SELECT 'AC not cooling', '2026-09-05 14:00:00', 'ravi.staff@campus.edu', 'PENDING',
           'RESOLVED', 'Filter cleaned, gas refilled', '2026-09-07 12:00:00'
    UNION ALL SELECT 'Projector faulty', '2026-09-16 09:20:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-16 09:20:00'
    UNION ALL SELECT 'Lab PC not booting', '2026-09-17 13:10:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-17 13:10:00'
    UNION ALL SELECT 'Lab PC not booting', '2026-09-17 13:10:00', 'meena.staff@campus.edu', 'PENDING',
           'IN_PROGRESS', 'Technician on the way', '2026-09-17 15:00:00'
    UNION ALL SELECT 'Mess hygiene issue', '2026-09-08 19:00:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-08 19:00:00'
    UNION ALL SELECT 'Mess hygiene issue', '2026-09-08 19:00:00', 'admin@campus.edu', 'PENDING',
           'REJECTED', 'Duplicate of existing audit', '2026-09-09 10:00:00'
    UNION ALL SELECT 'Broken door lock', '2026-09-18 16:40:00', NULL, NULL, 'PENDING',
           'Complaint submitted', '2026-09-18 16:40:00'
) AS h
JOIN complaints c ON c.title = h.title AND c.created_at = h.complaint_created_at
LEFT JOIN users actor ON actor.email = h.actor_email
WHERE NOT EXISTS (
    SELECT 1
    FROM complaint_history existing
    WHERE existing.complaint_id = c.complaint_id
      AND existing.new_status = h.new_status
      AND existing.changed_at = h.changed_at
);
