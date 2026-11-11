# Campus Voice – Viva Guide

## 1. 30-second summary
Campus Voice stores complaints in MySQL (normalized, with constraints, image attachments and a status-history audit trail). A Java Swing app talks to MySQL via JDBC and launches a C program via ProcessBuilder. The C program simulates OS concepts: CPU scheduling (FCFS, Priority), mutex synchronization, and deadlock detection.

## 2. DBMS questions

**Why separate entity tables? Is it normalized?**
Yes, 3NF. Users, departments, complaints, attachments and history are separate entities. A department name is stored once (not repeated in every complaint) – this removes update/delete anomalies. Every non-key column depends on the key, the whole key, and nothing but the key. Assignee and student are both rows of `users`, referenced by foreign keys.

**1NF / 2NF / 3NF in one line each?** 1NF: atomic values, no repeating groups. 2NF: no partial dependency on part of a composite key (all our keys are single-column). 3NF: no transitive dependency (e.g. we do not store `department_name` in `complaints`).

**What are the relationships?** `departments 1—N complaints`, `users 1—N complaints` (submitter), `users 1—N complaints` (assignee, nullable), `complaints 1—N complaint_history`, `complaints 1—N complaint_attachments`.

**What do the foreign keys give you?** Referential integrity – you cannot file a complaint for a user/department that doesn't exist. `ON DELETE RESTRICT` blocks deleting a user who has complaints; `ON DELETE CASCADE` on history removes the audit rows with their complaint; `ON DELETE SET NULL` on `assigned_to` keeps the complaint if a staff account is removed.

**Which constraints and why?** `NOT NULL` (mandatory data), `UNIQUE` (email, department name), `DEFAULT` (status PENDING, priority MEDIUM, timestamps), `CHECK` (allowed priority/status/role values; `resolved_at >= created_at`). CHECK is enforced from MySQL 8.0.16.

**Why a separate `complaint_history` table?** The complaint row only holds the *current* status; history keeps *every* change (who, when, old → new). Putting history columns in `complaints` would violate 1NF.

**What is a transaction? ACID?** A group of statements that succeed or fail together. Atomicity (all or nothing), Consistency (constraints hold before/after), Isolation (concurrent transactions don't see half-done work), Durability (committed data survives crashes).

**Where do you use one?** Submitting a complaint (complaint + first history row) and updating status (UPDATE + history INSERT). In `transaction_demo.sql` the history insert fails (user 999 doesn't exist) so `ROLLBACK` undoes the status change. In Java: `setAutoCommit(false)`, `commit()`, and `rollback()` in the catch block.

**Why `SELECT … FOR UPDATE`?** It locks the row so two staff members can't update the same complaint at once and write a wrong `old_status`.

**INNER JOIN vs LEFT JOIN?** INNER keeps only matching rows. LEFT keeps all rows from the left table – e.g. the "Transport" department with zero complaints still appears in the count query, and unassigned complaints still show.

**Why PreparedStatement?** Parameters are sent separately from the SQL text, so input like `'; DROP TABLE users;--` is treated as data, not code (prevents SQL injection). Also lets the DB reuse the plan.

**Why indexes?** Speed up filters/joins: `(status, priority)` for the urgent-and-unresolved query, FK columns for joins, `(complaint_id, changed_at)` for history lookups. Trade-off: slightly slower writes and more storage.

**Why is the password column `password_hash`?** Never store plain passwords. Sample values are dummies; real hashing (bcrypt) comes with the login module.

## 3. OS questions

**What is a process? Which states do you model?** A program in execution. NEW → READY → RUNNING → TERMINATED (the 5-state model's WAITING isn't needed because jobs run without I/O).

**FCFS?** Run in order of arrival. Simple and fair but suffers the *convoy effect*: P3 (burst 8) delays P4 (burst 2) – P4 waited 13 units in our demo.

**Non-preemptive priority?** When the CPU is free, pick the ready job with the best (smallest) effective priority number; it runs to completion even if a better job arrives. Aging reduces the numeric rank once every three waiting days, down to 1 (URGENT), so long-waiting low-priority complaints do not starve.

**How do you handle arrival times?** Only jobs with `arrival <= clock` are READY. If none is ready the CPU idles to the next arrival (shown as IDLE in the Gantt chart).

**Tie-breaking?** FCFS: arrival, then pid. Priority: effective priority, then arrival, then pid.

**Formulas?** Turnaround = Completion − Arrival. Waiting = Turnaround − Burst. Demo results: FCFS avg WT 7.00 / TAT 11.40; aging-aware Priority avg WT 5.00 / TAT 9.40.

**Race condition?** The outcome depends on thread timing. `counter++` is read–modify–write; two threads can read the same value and both write value+1, losing an update. Our no-mutex run showed 129,336 instead of 200,000 – but it is *possible, not guaranteed*, on any one run.

**Critical section? Mutual exclusion?** The critical section is the code touching the shared counter. Mutual exclusion means only one thread executes it at a time – achieved by `lock` before and `unlock` after. Four requirements: mutual exclusion, progress, bounded waiting, no assumptions about speed.

**Mutex vs semaphore?** A mutex is a lock owned by the thread that locked it (binary, ownership). A semaphore is a counter used for signalling or for limiting access to N identical resources.

**Why Win32 code in `synchronization.c`?** pthreads are not available in every Windows compiler, so the Windows branch uses `CreateThread` and `CreateMutex`; Linux/WSL uses pthreads.

**Deadlock? Four necessary conditions?** Processes wait forever for resources held by each other. Mutual exclusion, hold and wait, no preemption, circular wait – all four must hold.

**How does your algorithm work?** Work = Available. Repeatedly find an unfinished process whose Request ≤ Work, pretend it finishes and add its Allocation to Work. Whoever can never finish is deadlocked. Test 2: P2 asks for 1 unit of C but none is free, so P1–P4 are stuck.

**Detection vs prevention vs avoidance?** Prevention: make one of the four conditions impossible (e.g. global lock ordering). Avoidance: grant a request only if the state stays safe (Banker's – needs max demands in advance). Detection: allow deadlock, find it periodically, then recover (kill/preempt).

**Does your scheduler prevent deadlock?** **No.** Scheduling decides CPU order; deadlock is about resource holding. The modules are independent.

## 4. Integration questions
**How do Java and C talk?** `ProcessBuilder` starts the executable with arguments; Java writes job lines (`pid complaint arrival burst priority aging_days`) to its stdin and reads the report from stdout; exit code ≠ 0 or timeout = error.
**Is the Java data real?** Yes: open complaints are read from MySQL. *Assumption:* burst time is estimated from priority because the DB has no CPU-time column.
**What isn't done yet?** Lost & Found, notifications, authentication, analytics, Round Robin, Windows build verification, and DB/JDBC testing on a live MySQL.
