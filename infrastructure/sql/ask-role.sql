-- Run as the postgres superuser: psql -d dbuff -v ask_password=... -f ask-role.sql
DO $$
BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'dbuff_ask') THEN
    CREATE ROLE dbuff_ask LOGIN;
  END IF;
END
$$;

ALTER ROLE dbuff_ask PASSWORD :'ask_password';
ALTER ROLE dbuff_ask SET default_transaction_read_only = on;
GRANT CONNECT ON DATABASE dbuff TO dbuff_ask;
