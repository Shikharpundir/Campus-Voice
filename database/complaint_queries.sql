-- Campus Voice - Demonstration queries (MySQL 8.x). Run after schema.sql + sample_data.sql.
USE campus_voice;

-- 1. ADD a complaint (parameterized in Java; literals here for demo)
INSERT INTO complaints (user_id, department_id, title, description, location, priority)
VALUES (4, 1, 'Broken bench', 'Bench in the garden is broken.', 'Central Garden', 'LOW');
SET @new_id = LAST_INSERT_ID();
INSERT INTO complaint_history (complaint_id, changed_by, old_status, new_status, remarks)
VALUES (@new_id, 4, NULL, 'PENDING', 'Complaint submitted');

-- 2. All complaints submitted by one student (user_id = 4)
SELECT complaint_id, title, priority, status, created_at
FROM complaints WHERE user_id = 4 ORDER BY created_at DESC;

-- 3. Complaint details with student and department names (INNER JOIN)
--    LEFT JOIN on assignee, because assigned_to may be NULL
SELECT c.complaint_id, c.title, c.priority, c.status,
       u.name  AS student_name,
       d.department_name,
       a.name  AS assigned_staff
FROM complaints c
INNER JOIN users u       ON u.user_id = c.user_id
INNER JOIN departments d ON d.department_id = c.department_id
LEFT  JOIN users a       ON a.user_id = c.assigned_to
ORDER BY c.complaint_id;

-- 4. Urgent/high-priority and unresolved complaints
SELECT complaint_id, title, priority, status, created_at
FROM complaints
WHERE priority IN ('URGENT','HIGH') AND status IN ('PENDING','IN_PROGRESS')
ORDER BY FIELD(priority,'URGENT','HIGH'), created_at;

-- 5. Complaint count by department (LEFT JOIN keeps departments with zero)
SELECT d.department_name, COUNT(c.complaint_id) AS total_complaints
FROM departments d
LEFT JOIN complaints c ON c.department_id = d.department_id
GROUP BY d.department_id, d.department_name
ORDER BY total_complaints DESC;

-- 6. Departments with the highest workload (open complaints), using a subquery:
--    departments whose open-complaint count equals the maximum
SELECT d.department_name, COUNT(*) AS open_complaints
FROM complaints c
INNER JOIN departments d ON d.department_id = c.department_id
WHERE c.status IN ('PENDING','IN_PROGRESS')
GROUP BY d.department_id, d.department_name
HAVING COUNT(*) = (
    SELECT MAX(cnt) FROM (
        SELECT COUNT(*) AS cnt
        FROM complaints
        WHERE status IN ('PENDING','IN_PROGRESS')
        GROUP BY department_id
    ) AS per_dept
);

-- 6b. Subquery: students who have at least one URGENT complaint
SELECT name FROM users
WHERE user_id IN (SELECT user_id FROM complaints WHERE priority = 'URGENT');

-- 7. Status update + history in ONE transaction (see transaction_demo.sql for rollback)
START TRANSACTION;
UPDATE complaints SET status = 'IN_PROGRESS', assigned_to = 2 WHERE complaint_id = 3;
INSERT INTO complaint_history (complaint_id, changed_by, old_status, new_status, remarks)
VALUES (3, 2, 'PENDING', 'IN_PROGRESS', 'Plumber assigned');
COMMIT;

-- 9. History of one complaint (complaint 1)
SELECT h.history_id, h.old_status, h.new_status, h.remarks, u.name AS changed_by, h.changed_at
FROM complaint_history h
INNER JOIN users u ON u.user_id = h.changed_by
WHERE h.complaint_id = 1
ORDER BY h.changed_at, h.history_id;

-- UPDATE and DELETE examples
UPDATE complaints SET priority = 'HIGH' WHERE complaint_id = 4;
DELETE FROM complaints WHERE title = 'Broken bench';   -- history rows removed via ON DELETE CASCADE
