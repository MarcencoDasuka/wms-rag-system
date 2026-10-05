-- V37: Remediation of DEF-02 (Compromised seed user passwords)
-- Rotates password hashes for all 17 users whose passwords were leaked in V31 comments.
-- In accordance with database safety and immutable migrations policy, V31 is kept untouched,
-- and compromised credentials are invalidated by rotating them to fresh cryptographic hashes.

UPDATE users SET password = CASE username
    WHEN 'michael.scott'   THEN '$2a$10$5xOS4vw/GoHH2UukSc6r0OPgB5YnoSpb9w8X6cnD.u7cPq7agILWC'
    WHEN 'jim.halpert'     THEN '$2a$10$a2Wq9WV555wzI59YjmbLx.frBCgydB5Vz2GMQu.oi3QKoK7rf9Qba'
    WHEN 'pam.beesly'      THEN '$2a$10$DJhDI49Q1xmb34sqXmiQIOZROSGpO1oSDKBwrVHvpwtITPjkrI3Bq'
    WHEN 'dwight.schrute'  THEN '$2a$10$EfRvROqbwJ0BfZ.2EbeMbOSc6rF0V3c0Xqk8MMCzhxp/8hf1glVIa'
    WHEN 'stanley.hudson'  THEN '$2a$10$HVzLi2.4.8A1g5LsH7fnmO61KmJ1XLWAICFCODDIn8P4hTCN9BkZm'
    WHEN 'kevin.malone'    THEN '$2a$10$dlmP5kNsHgmZENg8oEeYW.oUoCt36utOQ9FjPE6Hf9sVX4ALFY2wS'
    WHEN 'angela.martin'   THEN '$2a$10$OnwI6s.OIxfUZoHt.8Hv5OyM7unkLD92vuwD9okvH0WmuJRsgh0L.'
    WHEN 'oscar.martinez'  THEN '$2a$10$GCSHQ7LkIAofiQxoAC0PwuZMhooeEC1LaTCJfUIp3FwTPjvt75PYq'
    WHEN 'phyllis.vance'   THEN '$2a$10$8PmPBd.yY2ZnIUplxMkOXu0OkFx09m03A9VNrIvydigzKNOJT1nqq'
    WHEN 'kelly.kapoor'    THEN '$2a$10$txswXBGFl9bJ23oviO9vCuyYgEVshC1OiRoRsjxdTi6gYMi14BU.y'
    WHEN 'toby.flenderson' THEN '$2a$10$IR8PLF7voFLB01t/ETqft.SiO8LVR3WOS8nuy3S5mCXkJOIIqbihi'
    WHEN 'creed.bratton'   THEN '$2a$10$2MKdLyKrFRLwkrzVN1zSduCbCkL6sbWe5a4kH0la0/XczeD6uXoHi'
    WHEN 'meredith.palmer' THEN '$2a$10$76mSbLRz1Famub9VKHtCZOTjBbvDzXRpTFgpAsWyD.8GihV80ilne'
    WHEN 'ryan.howard'     THEN '$2a$10$F6YlYJhZQuWRMpXKHCCrbuS8NrnXbaE9ZosOBLjxaVSHdd3/ey3xa'
    WHEN 'darryl.philbin'  THEN '$2a$10$0EuBQqt/elp.CUcKxsAv0ubfbLmW63YYn1QXQ/b3mcP97fnwt.7l.'
    WHEN 'holly.flax'      THEN '$2a$10$O.qqmfJGi43fYvcrIJc4EuH/1eb7XecqF7S78IjHTgRbrsVs2C7Xi'
    WHEN 'jan.levinson'    THEN '$2a$10$7MAKPtwq2D.sofeg3lllSeMVcmlJOyR7G1o74HIvnQ6uvzZPgejDC'
    ELSE password
END
WHERE username IN (
    'michael.scott', 'jim.halpert', 'pam.beesly', 'dwight.schrute',
    'stanley.hudson', 'kevin.malone', 'angela.martin', 'oscar.martinez',
    'phyllis.vance', 'kelly.kapoor', 'toby.flenderson', 'creed.bratton',
    'meredith.palmer', 'ryan.howard', 'darryl.philbin', 'holly.flax',
    'jan.levinson'
);
