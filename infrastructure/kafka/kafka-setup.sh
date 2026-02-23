#!/bin/bash
# ================================================================
#  TICKETMASTER – Kafka Topic Setup Script
#
#  Chạy bởi: kafka-init container trong docker-compose.yml
#  Mục đích: Tạo tất cả Kafka topics với đúng cấu hình
#             trước khi các microservice khởi động.
#
#  Topics theo domain:
#    - booking.*      : Booking lifecycle events
#    - payment.*      : Payment processing events
#    - seat.*         : Seat availability events (high-traffic)
#    - notification.* : Notification dispatch events
#    - dlq.*          : Dead Letter Queues (failed messages)
#
#  Cách chạy thủ công (khi đang dev):
#    docker exec -it tm-kafka bash /kafka-setup.sh
# ================================================================

set -e  # Dừng ngay nếu có lỗi

# ── Config ────────────────────────────────────────────────────
KAFKA_BROKER="${KAFKA_BROKER:-kafka:9092}"
REPLICATION_FACTOR=1          # Tăng lên 3 ở production (cần 3 brokers)
DEFAULT_RETENTION_MS=604800000 # 7 ngày
DLQ_RETENTION_MS=2592000000   # 30 ngày

# ── Colors ────────────────────────────────────────────────────
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo ""
echo "================================================================"
echo "  TICKETMASTER – Kafka Topic Initialization"
echo "  Broker: ${KAFKA_BROKER}"
echo "================================================================"
echo ""

# ── Wait for Kafka ────────────────────────────────────────────
echo -e "${YELLOW}⏳ Waiting for Kafka broker to be ready...${NC}"
MAX_RETRIES=30
RETRY_COUNT=0

until kafka-topics.sh --bootstrap-server "$KAFKA_BROKER" --list > /dev/null 2>&1; do
    RETRY_COUNT=$((RETRY_COUNT + 1))
    if [ $RETRY_COUNT -ge $MAX_RETRIES ]; then
        echo -e "${RED}❌ Kafka did not become ready after ${MAX_RETRIES} attempts. Exiting.${NC}"
        exit 1
    fi
    echo "   Attempt ${RETRY_COUNT}/${MAX_RETRIES} – retrying in 3s..."
    sleep 3
done

echo -e "${GREEN}✅ Kafka broker is ready!${NC}"
echo ""

# ── Helper Function ───────────────────────────────────────────
create_topic() {
    local topic=$1
    local partitions=${2:-3}
    local retention_ms=${3:-$DEFAULT_RETENTION_MS}
    local cleanup_policy=${4:-delete}

    if kafka-topics.sh --bootstrap-server "$KAFKA_BROKER" --list 2>/dev/null | grep -q "^${topic}$"; then
        echo -e "   ${YELLOW}⚠️  Topic already exists – skipping: ${topic}${NC}"
    else
        kafka-topics.sh \
            --bootstrap-server "$KAFKA_BROKER" \
            --create \
            --topic "$topic" \
            --partitions "$partitions" \
            --replication-factor "$REPLICATION_FACTOR" \
            --config "retention.ms=${retention_ms}" \
            --config "cleanup.policy=${cleanup_policy}" \
            --config "compression.type=lz4" \
            > /dev/null 2>&1

        echo -e "   ${GREEN}✔ Created: ${topic}${NC} (partitions=${partitions}, retention=${retention_ms}ms)"
    fi
}

# ================================================================
# BOOKING DOMAIN TOPICS
# ================================================================
echo "📦 [1/5] Creating Booking topics..."

# booking.created   → consumed by: payment-service, notification-service
create_topic "booking.created"    3 $DEFAULT_RETENTION_MS

# booking.confirmed → consumed by: notification-service, event-service
create_topic "booking.confirmed"  3 $DEFAULT_RETENTION_MS

# booking.cancelled → consumed by: notification-service, event-service
create_topic "booking.cancelled"  3 $DEFAULT_RETENTION_MS

# booking.expired   → consumed by: notification-service, event-service (seat release)
create_topic "booking.expired"    3 $DEFAULT_RETENTION_MS

echo ""

# ================================================================
# PAYMENT DOMAIN TOPICS
# ================================================================
echo "💳 [2/5] Creating Payment topics..."

# payment.processed → consumed by: booking-service, notification-service
create_topic "payment.processed"  3 $DEFAULT_RETENTION_MS

# payment.failed    → consumed by: booking-service, notification-service
create_topic "payment.failed"     3 $DEFAULT_RETENTION_MS

# payment.refunded  → consumed by: notification-service
create_topic "payment.refunded"   3 $DEFAULT_RETENTION_MS

echo ""

# ================================================================
# SEAT / EVENT DOMAIN TOPICS
# ================================================================
echo "🪑 [3/5] Creating Seat topics..."

# seat.status.changed → consumed by: event-service (update cache & DB)
# High traffic → 6 partitions để parallel consume
create_topic "seat.status.changed" 6 $DEFAULT_RETENTION_MS

echo ""

# ================================================================
# NOTIFICATION DOMAIN TOPICS
# ================================================================
echo "🔔 [4/5] Creating Notification topics..."

# notification.email → consumed by: notification-service
create_topic "notification.email"  3 $DEFAULT_RETENTION_MS

# notification.push  → consumed by: notification-service (SSE)
create_topic "notification.push"   3 $DEFAULT_RETENTION_MS

echo ""

# ================================================================
# DEAD LETTER QUEUES (DLQ)
# Messages không xử lý được sau N retries sẽ vào đây
# ================================================================
echo "🚨 [5/5] Creating Dead Letter Queue topics..."

create_topic "dlq.booking"      1 $DLQ_RETENTION_MS
create_topic "dlq.payment"      1 $DLQ_RETENTION_MS
create_topic "dlq.notification" 1 $DLQ_RETENTION_MS

echo ""

# ── Summary ───────────────────────────────────────────────────
echo "================================================================"
echo -e "${GREEN}✅ All Kafka topics initialized successfully!${NC}"
echo ""
echo "📋 Complete topic list:"
kafka-topics.sh --bootstrap-server "$KAFKA_BROKER" --list | sort | sed 's/^/   /'
echo "================================================================"
echo ""