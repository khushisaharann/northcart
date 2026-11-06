-- Reference data required by the application in every environment.
-- The first ADMIN user is NOT seeded here (that would put a password hash in Git);
-- it is created at startup from environment variables in Phase 4.
INSERT INTO roles (name) VALUES ('CUSTOMER'), ('ADMIN');
