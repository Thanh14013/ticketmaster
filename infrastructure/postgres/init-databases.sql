-- ================================================================
--  TICKETMASTER – PostgreSQL Initialization Script
--
--  Chạy tự động bởi Docker khi postgres container khởi động lần đầu.
--  (Mount vào /docker-entrypoint-initdb.d/)
--
--  Mục đích:
--    Tạo database riêng cho từng microservice (Database-per-Service pattern).
--    Mỗi service chỉ có quyền trên database của mình → loose coupling,
--    tránh data corruption lan rộng nếu một service gặp sự cố.
--
--  QUAN TRỌNG:
--    Script này chỉ chạy khi volume postgres_data trống (lần đầu tiên).
--    Nếu muốn chạy lại: docker compose down -v → xóa volume → up lại.
-- ================================================================

-- ── Tạo databases ─────────────────────────────────────────────

CREATE DATABASE user_db
    WITH
    OWNER     = ticketmaster
    ENCODING  = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE   = 'en_US.utf8'
    TEMPLATE  = template0;

COMMENT ON DATABASE user_db IS 'User Service – Identity & Access Management';

-- ────────────────────────────────────────────────────────────────

CREATE DATABASE event_db
    WITH
    OWNER     = ticketmaster
    ENCODING  = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE   = 'en_US.utf8'
    TEMPLATE  = template0;

COMMENT ON DATABASE event_db IS 'Event Service – Events, Venues, Seats';

-- ────────────────────────────────────────────────────────────────

CREATE DATABASE booking_db
    WITH
    OWNER     = ticketmaster
    ENCODING  = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE   = 'en_US.utf8'
    TEMPLATE  = template0;

COMMENT ON DATABASE booking_db IS 'Booking Service – Bookings, Quartz Jobs';

-- ────────────────────────────────────────────────────────────────

CREATE DATABASE payment_db
    WITH
    OWNER     = ticketmaster
    ENCODING  = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE   = 'en_US.utf8'
    TEMPLATE  = template0;

COMMENT ON DATABASE payment_db IS 'Payment Service – Transactions';

-- ────────────────────────────────────────────────────────────────

CREATE DATABASE notification_db
    WITH
    OWNER     = ticketmaster
    ENCODING  = 'UTF8'
    LC_COLLATE = 'en_US.utf8'
    LC_CTYPE   = 'en_US.utf8'
    TEMPLATE  = template0;

COMMENT ON DATABASE notification_db IS 'Notification Service – Email & SSE Notifications';

-- ── Grant privileges ───────────────────────────────────────────
-- (ticketmaster user đã là owner, nhưng explicit grant để rõ ràng)

GRANT ALL PRIVILEGES ON DATABASE user_db         TO ticketmaster;
GRANT ALL PRIVILEGES ON DATABASE event_db        TO ticketmaster;
GRANT ALL PRIVILEGES ON DATABASE booking_db      TO ticketmaster;
GRANT ALL PRIVILEGES ON DATABASE payment_db      TO ticketmaster;
GRANT ALL PRIVILEGES ON DATABASE notification_db TO ticketmaster;

-- ── Extensions (cài vào mỗi DB) ───────────────────────────────
-- uuid-ossp: generate UUID v4 cho primary keys
-- pgcrypto : hash passwords tại DB level (backup cho bcrypt ở app)

\connect user_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\connect event_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect booking_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

\connect payment_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

\connect notification_db
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ── Done ─────────────────────────────────────────────────────
\connect postgres
SELECT
    datname          AS database_name,
    pg_catalog.pg_get_userbyid(datdba) AS owner,
    pg_encoding_to_char(encoding) AS encoding
FROM pg_database
WHERE datname IN ('user_db','event_db','booking_db','payment_db','notification_db')
ORDER BY datname;