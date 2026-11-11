-- Campus Voice - database self-checks.
-- STATUS: written against the sample data but NOT executed (no MySQL server was available
-- where this project was generated). Run it yourself and treat any FAIL as a bug report.
--
-- HOW TO RUN: on a FRESH database  ->  schema.sql, sample_data.sql, then this file.
-- (complaint_queries.sql and transaction_demo.sql modify data, so rerun schema + sample first.)
USE campus_voice;

SELECT 'T1 total complaints = 10' AS test,
       IF((SELECT COUNT(*) FROM complaints) = 10, 'PASS', 'FAIL') AS result;

SELECT 'T2 student 4 has 3 complaints' AS test,
       IF((SELECT COUNT(*) FROM complaints WHERE user_id = 4) = 3, 'PASS', 'FAIL') AS result;

SELECT 'T3 urgent/high unresolved = 5' AS test,
       IF((SELECT COUNT(*) FROM complaints
           WHERE priority IN ('URGENT','HIGH') AND status IN ('PENDING','IN_PROGRESS')) = 5,
          'PASS', 'FAIL') AS result;

SELECT 'T4 IT Services has 3 complaints' AS test,
       IF((SELECT COUNT(c.complaint_id) FROM departments d
           LEFT JOIN complaints c ON c.department_id = d.department_id
           WHERE d.department_name = 'IT Services') = 3, 'PASS', 'FAIL') AS result;

SELECT 'T5 Transport has 0 complaints (LEFT JOIN keeps it)' AS test,
       IF((SELECT COUNT(c.complaint_id) FROM departments d
           LEFT JOIN complaints c ON c.department_id = d.department_id
           WHERE d.department_name = 'Transport') = 0, 'PASS', 'FAIL') AS result;

SELECT 'T6 highest open workload = IT Services (3)' AS test,
       IF((SELECT COUNT(*) FROM complaints c JOIN departments d ON d.department_id = c.department_id
           WHERE d.department_name = 'IT Services' AND c.status IN ('PENDING','IN_PROGRESS')) = 3
          AND (SELECT MAX(cnt) FROM (SELECT COUNT(*) AS cnt FROM complaints
               WHERE status IN ('PENDING','IN_PROGRESS') GROUP BY department_id) x) = 3,
          'PASS', 'FAIL') AS result;

SELECT 'T7 complaint 1 has 3 history rows' AS test,
       IF((SELECT COUNT(*) FROM complaint_history WHERE complaint_id = 1) = 3, 'PASS', 'FAIL') AS result;

SELECT 'T8 every resolved complaint has resolved_at' AS test,
       IF((SELECT COUNT(*) FROM complaints WHERE status = 'RESOLVED' AND resolved_at IS NULL) = 0,
          'PASS', 'FAIL') AS result;

SELECT 'T9 history latest status matches complaints.status' AS test,
       IF((SELECT COUNT(*) FROM complaints c
           WHERE c.status <> (SELECT h.new_status FROM complaint_history h
                              WHERE h.complaint_id = c.complaint_id
                              ORDER BY h.changed_at DESC, h.history_id DESC LIMIT 1)) = 0,
          'PASS', 'FAIL') AS result;

-- Rollback test (single statement batch; should leave complaint 10 unchanged)
START TRANSACTION;
UPDATE complaints SET status = 'REJECTED' WHERE complaint_id = 10;
ROLLBACK;
SELECT 'T10 rollback restores complaint 10 to PENDING' AS test,
       IF((SELECT status FROM complaints WHERE complaint_id = 10) = 'PENDING', 'PASS', 'FAIL') AS result;

-- ===== Constraint tests: each statement below is EXPECTED TO FAIL. Run them one at a time. =====
-- INSERT INTO complaints (user_id, department_id, title, description, location, priority)
--   VALUES (4, 1, 'x', 'x', 'x', 'CRITICAL');                       -- error 3819: priority CHECK
-- INSERT INTO complaints (user_id, department_id, title, description, location)
--   VALUES (999, 1, 'x', 'x', 'x');                                 -- error 1452: user FK
-- INSERT INTO users (name, email, password_hash) VALUES ('Dup','parikshit@campus.edu','h'); -- error 1062: UNIQUE email
-- DELETE FROM users WHERE user_id = 4;                              -- error 1451: RESTRICT (has complaints)
-- UPDATE complaints SET status = 'DONE' WHERE complaint_id = 1;     -- error 3819: status CHECK
