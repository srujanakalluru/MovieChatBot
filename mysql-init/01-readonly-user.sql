-- Runs automatically on FIRST MySQL volume initialization only (docker-entrypoint-initdb.d).
-- Creates the SELECT-only user used for executing model-generated queries.
--
-- Existing installations (volume already initialized) must run this once by hand:
--   docker exec -i movie-mysql mysql -uroot -prootpass < mysql-init/01-readonly-user.sql
-- then set QUERY_DB_USER=moviebot_ro and QUERY_DB_PASSWORD=moviebot_ro in .env.

CREATE USER IF NOT EXISTS 'moviebot_ro'@'%' IDENTIFIED BY 'moviebot_ro';
GRANT SELECT ON repo.* TO 'moviebot_ro'@'%';
FLUSH PRIVILEGES;
