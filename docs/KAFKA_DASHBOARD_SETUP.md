# Kafka Grafana Dashboard Setup Guide

This guide will help you set up and configure the comprehensive Kafka monitoring dashboard in Grafana.

## Overview

The new dashboard provides complete monitoring for:
- **Messages per second** (total and by topic)
- **Throughput** (bytes in/out, average message size)
- **Consumer lag** (by consumer group and topic)
- **Partition health** (partition count, under-replicated partitions)
- **Network metrics** (requests per second by type)

## Prerequisites

Ensure your monitoring stack is running:

```bash
cd /Users/mengruwang/Github/finpay
docker-compose up -d kafka prometheus grafana kafka-jmx-exporter
```

Verify services are healthy:
```bash
docker-compose ps
```

Expected services:
- Kafka on port 9092 (JMX on 9999)
- Prometheus on port 9090
- Grafana on port 3000
- Kafka JMX Exporter on port 7071

## Installation Steps

### Option 1: Import via Grafana UI

1. **Access Grafana**
   ```
   http://localhost:3000
   ```
   Login: `admin` / `admin`

2. **Import Dashboard**
   - Click on the "+" icon in the left sidebar
   - Select "Import dashboard"
   - Click "Upload JSON file"
   - Select: `/Users/mengruwang/Github/finpay/docs/kafka-comprehensive-dashboard.json`
   - Or copy/paste the JSON content directly

3. **Configure Data Source**
   - Select your Prometheus data source from the dropdown
   - Click "Import"

4. **Verify Dashboard**
   - The dashboard should load with all panels
   - Use the "Topic" dropdown to filter by specific topics
   - Set the time range (default: Last 1 hour)
   - Set refresh interval (default: 10 seconds)

### Option 2: Using Grafana Provisioning

1. **Copy the dashboard file**
   ```bash
   cp /Users/mengruwang/Github/finpay/docs/kafka-comprehensive-dashboard.json /path/to/grafana/provisioning/dashboards/
   ```

2. **Restart Grafana**
   ```bash
   docker-compose restart grafana
   ```

## Dashboard Panels Explained

### Message Throughput Section

1. **Total Messages In Per Second**
   - Query: `sum(rate(kafka_messages_in_total[1m]))`
   - Shows overall message production rate
   - Color-coded thresholds (green < 100, yellow < 1000, red >= 1000)

2. **Messages In Per Second by Topic**
   - Query: `rate(kafka_topic_messages_in_total{topic=~"$topic"}[1m])`
   - Time series chart showing each topic's message rate
   - Supports topic filtering via dashboard variable

3. **Top 5 Topics by Message Rate**
   - Query: `topk(5, rate(kafka_topic_messages_in_total[5m]))`
   - Bar gauge showing busiest topics
   - Useful for identifying high-volume topics

### Throughput (Bytes) Section

4. **Bytes In/Out Per Second**
   - Queries:
     - `rate(kafka_BytesInPerSec_total[1m])` (Bytes In)
     - `rate(kafka_BytesOutPerSec_total[1m])` (Bytes Out)
   - Monitors network bandwidth usage
   - Blue line = incoming, Orange line = outgoing

5. **Average Message Size**
   - Query: `rate(kafka_BytesInPerSec_total[1m]) / rate(kafka_messages_in_total[1m])`
   - Calculated metric showing bytes per message
   - Helps identify message size trends

### Consumer Lag & Performance Section

6. **Consumer Lag**
   - Query: `kafka_consumergroup_lag{topic=~"$topic"}`
   - Shows how far behind consumers are
   - Thresholds: green (0-100), yellow (100-1000), red (>1000)
   - **Critical**: High lag indicates consumer processing issues

7. **Consumer Offset vs Lag**
   - Queries:
     - `kafka_consumergroup_current_offset{topic=~"$topic"}` (Current offset)
     - `kafka_consumergroup_lag_offset{topic=~"$topic"}` (Lag offset)
   - Visualizes consumer progress vs available messages

### Partition & Broker Health Section

8. **Partitions per Topic**
   - Query: `sum by(topic) (kafka_topic_partitions{topic=~"$topic"})`
   - Bar gauge showing partition distribution
   - Helps with load balancing analysis

9. **Under-Replicated Partitions**
   - Query: `kafka_under_replicated_partitions`
   - **Critical metric**: Should always be 0
   - Red background if > 0 indicates replication issues

10. **Total Partitions**
    - Query: `kafka_partitions_count`
    - Overall partition count across all topics
    - Thresholds: green (<50), yellow (<100), red (>=100)

### Network & Request Metrics Section

11. **Network Requests Per Second by Type**
    - Query: `rate(kafka_network_requests_total[1m])`
    - Shows request types: Produce, Fetch, Metadata, etc.
    - Helps diagnose client connection issues

## Dashboard Variables

The dashboard includes dynamic variables for filtering:

### Datasource Variable
- **Name**: `$datasource`
- **Type**: Datasource selector
- **Default**: Prometheus
- Allows switching between different Prometheus instances

### Topic Variable
- **Name**: `$topic`
- **Type**: Query variable
- **Query**: `label_values(kafka_topic_messages_in_total, topic)`
- **Multi-select**: Yes
- **Include All**: Yes
- Filters all panels by selected topics

## Verifying Metrics Are Available

Before using the dashboard, verify metrics are being collected:

### Check Prometheus Targets

1. Visit http://localhost:9090/targets
2. Verify these targets are UP:
   - kafka-jmx-exporter (localhost:7071)
   - Spring Boot apps (localhost:8083, 8085)

### Query Metrics Directly

In Prometheus (http://localhost:9090), test these queries:

```promql
# Check if Kafka metrics exist
{__name__=~"kafka.*"}

# Check message metrics specifically
kafka_messages_in_total
kafka_topic_messages_in_total

# Check consumer lag metrics
kafka_consumergroup_lag
```

If no results appear, check your JMX exporter configuration.

## Troubleshooting

### Issue: No data in panels

**Solution 1: Check JMX Exporter**
```bash
# Check if JMX exporter is running
curl http://localhost:7071/metrics | grep kafka

# Restart JMX exporter if needed
docker-compose restart kafka-jmx-exporter
```

**Solution 2: Verify Kafka JMX Port**
```bash
# Check if Kafka JMX is accessible
docker exec -it finpay-kafka-1 bash
echo "stats" | nc localhost 9999
```

**Solution 3: Update Datasource UID**
1. Go to Grafana → Configuration → Data Sources
2. Click on your Prometheus data source
3. Copy the UID from the URL
4. Edit dashboard JSON and replace `"uid": "${datasource}"` with actual UID

### Issue: Consumer lag metrics missing

**Possible causes:**
- No active consumer groups
- JMX exporter not configured to export consumer group metrics
- Consumer groups using internal topics only

**Solution:**
Add consumer group metrics to kafka-jmx.yml:
```yaml
- pattern: "kafka.consumer<type=consumer-fetch-manager-metrics, client-id=(.+), topic=(.+), partition=(.+)><>records-lag"
  name: kafka_consumergroup_lag
  labels:
    client_id: "$1"
    topic: "$2"
    partition: "$3"
  type: GAUGE
```

### Issue: Metric names don't match

If your metrics have different names (e.g., `kafka_server_brokertopicmetrics_messagesin_total` instead of `kafka_topic_messages_in_total`):

1. Check your actual metric names:
   ```bash
   curl http://localhost:7071/metrics | grep -i message
   ```

2. Update the queries in the dashboard to match your metric names

3. Common naming patterns from different exporters:
   - JMX Exporter: `kafka_server_*`, `kafka_topic_*`
   - Kafka Exporter: `kafka_brokers_*`, `kafka_topic_partition_*`
   - Our config: `kafka_messages_in_total`, `kafka_topic_messages_in_total`

## Testing the Dashboard

### Generate Test Traffic

1. **Produce test messages:**
   ```bash
   # Create a test topic
   docker exec -it kafka kafka-topics \
     --create \
     --topic test-metrics \
     --bootstrap-server localhost:9092 \
     --partitions 3 \
     --replication-factor 1

   # Produce messages in a loop
   for i in {1..1000}; do
     echo "Test message $i" | docker exec -i kafka \
       kafka-console-producer \
       --broker-list localhost:9092 \
       --topic test-metrics
     sleep 0.1
   done
   ```

2. **Consume messages:**
   ```bash
   docker exec -it kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic test-metrics \
     --group test-consumer-group \
     --from-beginning
   ```

3. **Watch the dashboard:**
   - Messages In Per Second should show activity
   - Consumer Lag should appear when consumer is slower than producer
   - Network Requests should show Produce and Fetch operations

### Expected Results

After generating traffic, you should see:
- Message rate increasing in "Messages In Per Second by Topic"
- "test-metrics" appearing in the topic filter dropdown
- Bytes In/Out showing network activity
- Consumer lag appearing for "test-consumer-group"
- Network requests showing Produce and Fetch types

## Customization

### Adding Alert Rules

Add alert rules directly in Grafana panels:

1. Edit any panel
2. Click "Alert" tab
3. Create alert rule, example:
   ```
   Condition: WHEN last() OF query(A, 5m, now) IS ABOVE 1000
   Alert name: High Consumer Lag
   Message: Consumer lag is above 1000 messages
   ```

### Adding More Panels

Useful additional queries:

**Broker CPU/Memory (if monitoring Kafka container):**
```promql
rate(container_cpu_usage_seconds_total{name="kafka"}[1m])
container_memory_usage_bytes{name="kafka"}
```

**Log size growth:**
```promql
kafka_log_size_bytes
```

**Leader election rate:**
```promql
rate(kafka_controller_ControllerStats_LeaderElectionRateAndTimeMs_count[1m])
```

## Dashboard Maintenance

### Regular Checks
- Monitor "Under-Replicated Partitions" - should always be 0
- Check consumer lag trends - increasing lag indicates issues
- Review message rate patterns to understand traffic patterns
- Monitor partition distribution for load balancing

### Performance Tips
- Adjust time ranges to match your needs (shorter = faster queries)
- Use topic filtering to reduce query load
- Set appropriate refresh intervals (10s default, increase for slower systems)
- Archive old dashboards when creating new versions

## Next Steps

1. **Set up alerting** in Grafana for critical metrics
2. **Configure notification channels** (Slack, email, PagerDuty)
3. **Create custom panels** for application-specific metrics
4. **Export and version control** your dashboard configurations
5. **Set up dashboard snapshots** for historical analysis

## Support

If you encounter issues:
1. Check Prometheus targets: http://localhost:9090/targets
2. Verify JMX exporter metrics: http://localhost:7071/metrics
3. Review Kafka logs: `docker-compose logs kafka`
4. Check Grafana logs: `docker-compose logs grafana`

For Grafana dashboard questions, see: https://grafana.com/docs/
For Kafka JMX metrics reference, see: https://kafka.apache.org/documentation/#monitoring
