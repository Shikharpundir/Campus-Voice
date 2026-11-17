-- Run once against an existing campus_voice database to enable report voting.
USE campus_voice;

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
