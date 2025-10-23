#!/bin/bash

# Kafka Dashboard Testing Script
# This script generates test messages to verify Grafana dashboard metrics

set -e

echo "=========================================="
echo "Kafka Grafana Dashboard Test Script"
echo "=========================================="
echo ""

# Configuration
KAFKA_CONTAINER="kafka"
KAFKA_BROKER="localhost:9092"
TEST_TOPIC="test-metrics"
NUM_MESSAGES=100

echo "1. Creating test topic: $TEST_TOPIC"
echo "------------------------------------------"
docker exec $KAFKA_CONTAINER kafka-topics \
  --create \
  --topic $TEST_TOPIC \
  --bootstrap-server $KAFKA_BROKER \
  --partitions 3 \
  --replication-factor 1 \
  --if-not-exists

echo ""
echo "2. Current Kafka topics:"
echo "------------------------------------------"
docker exec $KAFKA_CONTAINER kafka-topics \
  --list \
  --bootstrap-server $KAFKA_BROKER

echo ""
echo "3. Producing $NUM_MESSAGES test messages..."
echo "------------------------------------------"
for i in $(seq 1 $NUM_MESSAGES); do
  echo "Test message $i - timestamp: $(date +%s)" | docker exec -i $KAFKA_CONTAINER \
    kafka-console-producer \
    --broker-list $KAFKA_BROKER \
    --topic $TEST_TOPIC

  # Show progress every 10 messages
  if [ $((i % 10)) -eq 0 ]; then
    echo "  → Sent $i messages..."
  fi

  # Small delay to simulate real traffic
  sleep 0.05
done

echo ""
echo "4. Verifying metrics in JMX Exporter..."
echo "------------------------------------------"
echo "Total messages:"
curl -s http://localhost:7071/metrics | grep "kafka_messages_in_total " || echo "  Metric not found yet (normal for first run)"

echo ""
echo "Topic-level messages:"
curl -s http://localhost:7071/metrics | grep "kafka_topic_messages_in_total" | head -5 || echo "  Metric not found yet"

echo ""
echo "5. Checking Prometheus scraping..."
echo "------------------------------------------"
PROM_QUERY='sum(rate(kafka_messages_in_total[1m]))'
PROM_RESULT=$(curl -s "http://localhost:9090/api/v1/query?query=$PROM_QUERY" | grep -o '"result":\[.*\]')
echo "Query: $PROM_QUERY"
echo "Result: $PROM_RESULT"

echo ""
echo "6. Starting consumer in background..."
echo "------------------------------------------"
echo "Consumer will process messages from the beginning..."
docker exec -d $KAFKA_CONTAINER kafka-console-consumer \
  --bootstrap-server $KAFKA_BROKER \
  --topic $TEST_TOPIC \
  --group test-consumer-group \
  --from-beginning \
  --max-messages $NUM_MESSAGES > /dev/null 2>&1

echo ""
echo "=========================================="
echo "Test Complete!"
echo "=========================================="
echo ""
echo "Next Steps:"
echo "1. Open Grafana: http://localhost:3000"
echo "2. Import the dashboard from: docs/kafka-comprehensive-dashboard.json"
echo "3. Select topic '$TEST_TOPIC' from the dropdown"
echo "4. You should see:"
echo "   - Messages per second graph showing activity"
echo "   - Consumer lag metrics"
echo "   - Partition count (3 partitions)"
echo ""
echo "To generate continuous traffic, run:"
echo "  while true; do"
echo "    echo \"Message \$(date)\" | docker exec -i $KAFKA_CONTAINER \\"
echo "      kafka-console-producer --broker-list $KAFKA_BROKER --topic $TEST_TOPIC"
echo "    sleep 1"
echo "  done"
echo ""
