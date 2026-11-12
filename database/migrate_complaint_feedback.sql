-- Run once against an existing campus_voice database to enable resolution feedback.
USE campus_voice;

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
