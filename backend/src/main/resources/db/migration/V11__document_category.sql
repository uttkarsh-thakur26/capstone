-- Categories come from a fixed list instead of free text (DOC-1, ADR-040). Each existing value is
-- mapped to the list by its name or label, ignoring case, spaces and underscores; anything else
-- becomes OTHER, so no document loses its category or its row.
UPDATE document
SET category = CASE regexp_replace(lower(category), '[\s_]+', '', 'g')
                 WHEN 'policy' THEN 'POLICY'
                 WHEN 'syllabus' THEN 'SYLLABUS'
                 WHEN 'courseplan' THEN 'COURSE_PLAN'
                 WHEN 'examination' THEN 'EXAMINATION'
                 WHEN 'notice' THEN 'NOTICE'
                 WHEN 'schedule' THEN 'SCHEDULE'
                 WHEN 'handbook' THEN 'HANDBOOK'
                 ELSE 'OTHER'
               END
WHERE category IS NOT NULL;

ALTER TABLE document
    ADD CONSTRAINT document_category_check CHECK (category IN
        ('POLICY', 'SYLLABUS', 'COURSE_PLAN', 'EXAMINATION', 'NOTICE', 'SCHEDULE', 'HANDBOOK',
         'OTHER'));
