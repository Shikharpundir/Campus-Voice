-- Run once against an existing campus_voice database to enable Report Staff.
USE campus_voice;

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

CREATE INDEX idx_staff_reports_student ON staff_reports(student_id, created_at);
CREATE INDEX idx_staff_reports_status ON staff_reports(status, created_at);
