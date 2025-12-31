-- V4__Seed_default_roles.sql
-- Insert default roles using MERGE for H2 compatibility
-- MERGE works in both H2 and PostgreSQL
MERGE INTO roles (name) KEY(name) VALUES ('ROLE_USER');
MERGE INTO roles (name) KEY(name) VALUES ('ROLE_ADMIN');
