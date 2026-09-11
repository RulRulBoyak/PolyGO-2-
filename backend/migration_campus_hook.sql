-- Campus Hook features: free class slots on listings + department (major) filter.
-- Apply manually against existing databases (e.g. XAMPP phpMyAdmin -> polygo -> SQL) once.
-- New imports should use backend/polygo.sql which already contains these changes.

ALTER TABLE listings
    ADD COLUMN free_slots VARCHAR(500) NULL DEFAULT NULL AFTER tags,
    ADD COLUMN major_id INT UNSIGNED NULL DEFAULT NULL AFTER free_slots;

CREATE INDEX idx_listings_major ON listings (major_id);

CREATE TABLE IF NOT EXISTS majors (
    id INT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(120) NOT NULL UNIQUE,
    faculty VARCHAR(120) NOT NULL DEFAULT '',
    is_published TINYINT(1) NOT NULL DEFAULT 1
);

INSERT IGNORE INTO majors (name, faculty) VALUES
    ('Computer Science', 'ICT'),
    ('Software Engineering', 'ICT'),
    ('Information Technology', 'ICT'),
    ('Electrical Engineering', 'Engineering'),
    ('Civil Engineering', 'Engineering'),
    ('Mechanical Engineering', 'Engineering'),
    ('Business Studies', 'Commerce'),
    ('Accountancy', 'Commerce'),
    ('Hospitality & Tourism', 'Commerce'),
    ('Graphic Design', 'Applied Sciences');