-- Campus Voice - Schema (MySQL 8.0.16+ required for enforced CHECK constraints)
DROP DATABASE IF EXISTS campus_voice;
CREATE DATABASE campus_voice CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE campus_voice;

-- Departments (1) -> (many) complaints
CREATE TABLE departments (
    department_id   INT AUTO_INCREMENT PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

-- Users: students, staff, admins
CREATE TABLE users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    name          VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NULL,               -- NULL only until a staff member completes first-time setup
    role          VARCHAR(10)  NOT NULL DEFAULT 'STUDENT',
    department_id INT          NULL,               -- required for staff; admins are global
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_department FOREIGN KEY (department_id)
        REFERENCES departments(department_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_users_role CHECK (role IN ('STUDENT','STAFF','ADMIN')),
    CONSTRAINT chk_user_department_scope CHECK (
        (role IN ('STUDENT','ADMIN') AND department_id IS NULL AND password_hash IS NOT NULL)
        OR (role = 'STAFF' AND (
            (department_id IS NULL AND password_hash IS NULL)
            OR (department_id IS NOT NULL AND password_hash IS NOT NULL)
        ))
    )
) ENGINE=InnoDB;

-- Complaints
CREATE TABLE complaints (
    complaint_id  INT AUTO_INCREMENT PRIMARY KEY,
    user_id       INT          NOT NULL,           -- who submitted
    department_id INT          NOT NULL,           -- who is responsible
    title         VARCHAR(150) NOT NULL,
    description   TEXT         NOT NULL,
    location      VARCHAR(150) NOT NULL,
    priority      VARCHAR(10)  NOT NULL DEFAULT 'MEDIUM',
    status        VARCHAR(15)  NOT NULL DEFAULT 'PENDING',
    assigned_to   INT          NULL,               -- staff member handling it
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at   TIMESTAMP    NULL,
    CONSTRAINT fk_complaint_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_complaint_dept FOREIGN KEY (department_id)
        REFERENCES departments(department_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_complaint_assignee FOREIGN KEY (assigned_to)
        REFERENCES users(user_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_complaint_priority CHECK (priority IN ('LOW','MEDIUM','HIGH','URGENT')),
    CONSTRAINT chk_complaint_status   CHECK (status IN ('PENDING','IN_PROGRESS','RESOLVED','REJECTED')),
    CONSTRAINT chk_resolved_time      CHECK (resolved_at IS NULL OR resolved_at >= created_at)
) ENGINE=InnoDB;

-- A student can support an open complaint once. Five or more votes promote it to URGENT.
CREATE TABLE complaint_votes (
    complaint_id INT NOT NULL,
    user_id      INT NOT NULL,
    voted_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (complaint_id, user_id),
    CONSTRAINT fk_vote_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(complaint_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_vote_user FOREIGN KEY (user_id)
        REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- A student may submit one editable rating/comment for each resolved complaint.
CREATE TABLE complaint_feedback (
    complaint_id     INT PRIMARY KEY,
    rating           TINYINT UNSIGNED NOT NULL,
    feedback_comment VARCHAR(1000) NULL,
    submitted_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(complaint_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT chk_feedback_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB;

-- Complaint images are stored separately from complaint text.
CREATE TABLE complaint_attachments (
    attachment_id INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id  INT          NOT NULL,
    file_name     VARCHAR(255) NOT NULL,
    mime_type     VARCHAR(100) NOT NULL,
    file_data     LONGBLOB     NOT NULL,
    uploaded_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attachment_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(complaint_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- Staff reports are visible to their student reporter and the global administrator only.
CREATE TABLE staff_reports (
    report_id       INT AUTO_INCREMENT PRIMARY KEY,
    student_id      INT          NOT NULL,
    staff_id        INT          NOT NULL,
    complaint_id    INT          NULL,
    reason          VARCHAR(30)  NOT NULL,
    description     TEXT         NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTED',
    admin_response  VARCHAR(2000) NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_staff_report_student FOREIGN KEY (student_id)
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_staff_report_staff FOREIGN KEY (staff_id)
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_staff_report_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(complaint_id) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT chk_staff_report_reason CHECK (
        reason IN ('IGNORED_COMPLAINT','UNNECESSARY_DELAY','FAKE_RESOLUTION','MISCONDUCT')),
    CONSTRAINT chk_staff_report_status CHECK (
        status IN ('SUBMITTED','UNDER_REVIEW','ACTION_TAKEN','CLOSED'))
) ENGINE=InnoDB;

CREATE TABLE staff_report_evidence (
    report_id  INT          PRIMARY KEY,
    file_name  VARCHAR(255) NOT NULL,
    mime_type  VARCHAR(100) NOT NULL,
    file_data  LONGBLOB     NOT NULL,
    uploaded_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_staff_report_evidence FOREIGN KEY (report_id)
        REFERENCES staff_reports(report_id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB;

-- History: one complaint (1) -> many status changes
CREATE TABLE complaint_history (
    history_id   INT AUTO_INCREMENT PRIMARY KEY,
    complaint_id INT          NOT NULL,
    changed_by   INT          NOT NULL,
    old_status   VARCHAR(15)  NULL,                -- NULL for the first entry
    new_status   VARCHAR(15)  NOT NULL,
    remarks      VARCHAR(255) NULL,
    changed_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_hist_complaint FOREIGN KEY (complaint_id)
        REFERENCES complaints(complaint_id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_hist_user FOREIGN KEY (changed_by)
        REFERENCES users(user_id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT chk_hist_new CHECK (new_status IN ('PENDING','IN_PROGRESS','RESOLVED','REJECTED')),
    CONSTRAINT chk_hist_old CHECK (old_status IS NULL OR old_status IN ('PENDING','IN_PROGRESS','RESOLVED','REJECTED'))
) ENGINE=InnoDB;

-- Indexes (foreign keys already get indexes in InnoDB; these support common filters)
CREATE INDEX idx_complaints_status_priority ON complaints(status, priority);
CREATE INDEX idx_complaints_user            ON complaints(user_id);
CREATE INDEX idx_complaints_dept            ON complaints(department_id);
CREATE INDEX idx_history_complaint          ON complaint_history(complaint_id, changed_at);
CREATE INDEX idx_staff_reports_student      ON staff_reports(student_id, created_at);
CREATE INDEX idx_staff_reports_status       ON staff_reports(status, created_at);
