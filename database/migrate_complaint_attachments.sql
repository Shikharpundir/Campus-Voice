-- Run once against an existing campus_voice database to enable image attachments.
USE campus_voice;

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
