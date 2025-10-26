# Quick Start: Kafka Grafana Dashboard

## What Was Fixed

Your original dashboard had **incorrect metric names**. The queries have been corrected to match your JMX exporter configuration.

### Before (Wrong):
```promql
sum(rate(kafka_brokertopicmetrics_messagesin_total[1m]))
```

### After (Correct):
```promql
rate(kafka_topic_messages_in_total{topic=~"$topic"}[1m])  # By topic
sum(rate(kafka_messages_in_total[1m]))                     # Total
```

---

## Import Dashboard (2 Steps)

1. **Open Grafana**: http://localhost:3000 (login: `admin` / `admin`)

2. **Import Dashboard**:
   - Click "+" → "Import dashboard"
   - Upload file: `docs/kafka-comprehensive-dashboard.json`
   - Select Prometheus datasource
   - Click "Import"

✅ Done! Your dashboard is ready.

---

## Test the Dashboard

Run this command to generate test traffic:

```bash
cd /Users/mengruwang/Github/finpay

# Run the test script (sends 100 messages)
./test-kafka-metrics.sh
```

Or manually produce messages:

```bash
# Produce messages to an existing topic
for i in {1..50}; do
  echo "Test message $i at $(date)" | docker exec -i kafka \
    kafka-console-producer \
    --broker-list localhost:9092 \
    --topic transactions-topic
  sleep 0.5
done
```

---

## What You'll See in the Dashboard

### Messages Per Second Section
- **Total Messages/sec**: Single stat showing overall rate
- **Messages by Topic**: Time series graph (filter by topic dropdown)
- **Top 5 Topics**: Bar chart of busiest topics

### Throughput Section
- **Bytes In/Out**: Network bandwidth usage
- **Avg Message Size**: Calculated from bytes/messages

### Consumer Lag Section
- **Consumer Lag**: How far behind consumers are (should be low)
- **Offset vs Lag**: Visual comparison of consumer progress

### Health Section
- **Partitions per Topic**: Distribution across topics
- **Under-Replicated Partitions**: Should always be 0 (red if not)
- **Total Partitions**: Overall count

### Network Section
- **Requests by Type**: Produce, Fetch, Metadata, etc.

---

## Continuous Test Traffic

Keep generating messages in the background:

```bash
# Terminal 1: Producer (generates traffic)
while true; do
  echo "Continuous test at $(date +%s)" | docker exec -i kafka \
    kafka-console-producer \
    --broker-list localhost:9092 \
    --topic test-metrics
  sleep 1
done

# Terminal 2: Consumer (creates lag when stopped)
docker exec -it kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic test-metrics \
  --group test-consumer-group \
  --from-beginning
```

---

## Troubleshooting

### No Data in Dashboard?

1. **Check Prometheus targets**: http://localhost:9090/targets
   - JMX exporter should be UP at `localhost:7071`

2. **Verify metrics exist**:
   ```bash
   curl http://localhost:7071/metrics | grep kafka_messages_in_total
   ```

3. **Check containers are running**:
   ```bash
   docker ps | grep -E "kafka|prometheus|grafana"
   ```

### Wrong Datasource UID?

If you get datasource errors:
1. Go to Grafana → Configuration → Data Sources
2. Click your Prometheus datasource
3. Copy the UID from the URL
4. In the dashboard JSON, the datasource is set to `${datasource}` variable (should work automatically)

---

## Files Created

- `docs/kafka-comprehensive-dashboard.json` - Complete dashboard with 11 panels
- `docs/KAFKA_DASHBOARD_SETUP.md` - Detailed setup and troubleshooting guide
- `test-kafka-metrics.sh` - Test script to generate traffic

---

## Your Current Topics

You already have these topics running:
- `transactions-topic` - Your main transaction topic
- `fraud-check` - Fraud detection topic
- `test-metrics` - Created by the test script

Use the dashboard's **Topic dropdown** to filter and see metrics for specific topics.

---

## Next Steps

1. ✅ Import the dashboard (2 minutes)
2. ✅ Run test script to verify metrics
3. ✅ Add alerting rules for critical metrics
4. ✅ Configure notification channels (Slack, email)
5. ✅ Customize panels for your specific needs

For detailed information, see `docs/KAFKA_DASHBOARD_SETUP.md`
