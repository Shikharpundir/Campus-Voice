-- Campus Voice - Transaction and rollback demonstrations
USE campus_voice;

-- ===== Demo 1: successful transaction (COMMIT) =====
START TRANSACTION;
UPDATE complaints SET status = 'RESOLVED', resolved_at = NOW() WHERE complaint_id = 8;
INSERT INTO complaint_history (complaint_id, changed_by, old_status, new_status, remarks)
VALUES (8, 3, 'IN_PROGRESS', 'RESOLVED', 'PCs reimaged');
COMMIT;
SELECT complaint_id, status, resolved_at FROM complaints WHERE complaint_id = 8;

-- ===== Demo 2: ROLLBACK after a failed step =====
-- The UPDATE succeeds, then the history INSERT violates a foreign key (changed_by = 999
-- does not exist). We then ROLLBACK, so the UPDATE is undone as well.
-- (Run these statements one at a time in MySQL Workbench or the mysql client;
--  the INSERT is expected to fail with error 1452.)
START TRANSACTION;
UPDATE complaints SET status = 'RESOLVED', resolved_at = NOW() WHERE complaint_id = 7;
SELECT complaint_id, status FROM complaints WHERE complaint_id = 7;   -- shows RESOLVED (inside txn)
INSERT INTO complaint_history (complaint_id, changed_by, old_status, new_status, remarks)
VALUES (7, 999, 'PENDING', 'RESOLVED', 'This fails: user 999 does not exist');
ROLLBACK;
SELECT complaint_id, status, resolved_at FROM complaints WHERE complaint_id = 7;  -- back to PENDING / NULL

-- ===== Demo 3: rollback by choice =====
START TRANSACTION;
UPDATE complaints SET priority = 'LOW' WHERE complaint_id = 5;
ROLLBACK;
SELECT complaint_id, priority FROM complaints WHERE complaint_id = 5;  -- still URGENT

-- ===== Demo 4: CHECK constraint violation (error 3819) =====
-- UPDATE complaints SET status = 'DONE' WHERE complaint_id = 1;   -- uncomment to see the error
