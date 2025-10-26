# FinPay 项目 Elasticsearch 实现详解

## 目录
- [概述](#概述)
- [架构设计](#架构设计)
- [Elasticsearch 配置](#elasticsearch-配置)
- [Logstash 管道配置](#logstash-管道配置)
- [应用层集成](#应用层集成)
- [数据流与调用时序](#数据流与调用时序)
- [索引结构与数据模型](#索引结构与数据模型)
- [查询与使用](#查询与使用)
- [运维与监控](#运维与监控)

---

## 概述

### 使用场景
FinPay 项目使用 **Elasticsearch 作为 ELK（Elasticsearch + Logstash + Kibana）日志聚合栈**的核心组件，用于：
- 集中式日志收集与存储
- 分布式链路追踪日志关联
- 实时日志搜索与分析
- 系统运维监控与告警

### 关键特性
- **非业务数据索引**：Elasticsearch 仅用于日志聚合，不索引业务实体（如账户、交易等）
- **标准化 ELK 栈**：使用官方 Elastic 产品，版本统一为 8.10.2
- **JSON 结构化日志**：通过 Logstash Encoder 格式化应用日志
- **按日索引分片**：采用 `transaction-service-logs-YYYY.MM.DD` 索引策略

---

## 架构设计

### 系统架构图

```
┌──────────────────────────────────────────────────────────────┐
│                   微服务集群 (6 个服务)                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐              │
│  │ Auth       │  │ Account    │  │Transaction │              │
│  │ Service    │  │ Service    │  │ Service    │              │
│  │ :8081      │  │ :8082      │  │ :8083      │              │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘              │
│        │               │               │                      │
│  ┌─────┴──────┐  ┌─────┴──────┐  ┌─────┴──────┐              │
│  │ Fraud      │  │Notification│  │ API        │              │
│  │ Service    │  │ Service    │  │ Gateway    │              │
│  │ :8085      │  │ :8086      │  │ :8080      │              │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘              │
│        │               │               │                      │
│        └───────────────┼───────────────┘                      │
│                        │                                      │
│                  (Logback TCP)                                │
│                  JSON Lines Format                            │
└────────────────────────┼─────────────────────────────────────┘
                         │
                         ▼
           ┌─────────────────────────┐
           │   Logstash :5001        │
           │  ┌──────────────────┐   │
           │  │ TCP Input        │   │
           │  │ Port: 5000       │   │
           │  │ Codec: json_lines│   │
           │  └────────┬─────────┘   │
           │           │             │
           │  ┌────────▼─────────┐   │
           │  │ Output Pipeline  │   │
           │  │ No Filters       │   │
           │  └────────┬─────────┘   │
           └───────────┼─────────────┘
                       │
                       ▼
         ┌─────────────────────────────────┐
         │   Elasticsearch :9200           │
         │  ┌──────────────────────────┐   │
         │  │ Index Management         │   │
         │  │ Pattern:                 │   │
         │  │ transaction-service-logs-│   │
         │  │ YYYY.MM.DD               │   │
         │  └──────────┬───────────────┘   │
         │             │                   │
         │  ┌──────────▼───────────────┐   │
         │  │ Storage Engine           │   │
         │  │ Path: ./elasticsearch-   │   │
         │  │       data/               │   │
         │  └──────────────────────────┘   │
         └─────────────┬───────────────────┘
                       │
                       ▼
            ┌──────────────────────┐
            │   Kibana :5601       │
            │  ┌────────────────┐  │
            │  │ Discover       │  │
            │  │ Dashboard      │  │
            │  │ Dev Tools      │  │
            │  │ Alerts         │  │
            │  └────────────────┘  │
            └──────────────────────┘
```

### 技术栈版本

| 组件 | 版本 | 作用 |
|------|------|------|
| Elasticsearch | 8.10.2 | 日志存储与搜索引擎 |
| Logstash | 8.10.2 | 日志收集与转换 |
| Kibana | 8.10.2 | 日志可视化与查询界面 |
| Logstash Logback Encoder | 7.4 | Java 日志 JSON 格式化 |

---

## Elasticsearch 配置

### Docker Compose 配置

**文件位置**: [docker-compose.yml](docker-compose.yml#L93-L107)

```yaml
elasticsearch:
  image: docker.elastic.co/elasticsearch/elasticsearch:8.10.2
  container_name: elasticsearch
  environment:
    - discovery.type=single-node          # 单节点模式
    - xpack.security.enabled=false        # 禁用安全认证（开发环境）
    - ES_JAVA_OPTS=-Xms512m -Xmx512m     # JVM 堆内存 512MB
  ports:
    - "9200:9200"   # HTTP REST API
    - "9300:9300"   # 节点通信端口
  volumes:
    - ./elasticsearch-data:/usr/share/elasticsearch/data  # 数据持久化
  networks:
    - finpay-network
```

### 配置参数详解

| 参数 | 值 | 说明 |
|------|-----|------|
| `discovery.type` | `single-node` | 单节点集群，不进行节点发现 |
| `xpack.security.enabled` | `false` | 禁用 X-Pack 安全认证，方便开发调试 |
| `ES_JAVA_OPTS` | `-Xms512m -Xmx512m` | 最小/最大堆内存，适合开发环境 |
| `http.port` | `9200` | REST API 端口 |
| `transport.port` | `9300` | TCP 节点间通信端口 |

### 数据存储

```bash
# 宿主机路径
./elasticsearch-data/

# 容器内路径
/usr/share/elasticsearch/data/

# 索引数据文件结构
elasticsearch-data/
└── nodes/
    └── 0/
        ├── indices/
        │   └── [index-uuid]/
        │       ├── 0/  # 分片 0
        │       │   ├── index/
        │       │   └── translog/
        │       └── _state/
        └── _state/
```

---

## Logstash 管道配置

### 配置文件

**文件位置**: [logstash.conf](logstash.conf)

```conf
input {
  tcp {
    port => 5000                # 容器内端口（映射到宿主机 5001）
    codec => json_lines         # 解析 JSON 行格式
  }
}

# 无 filter 配置，直接透传日志

output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]              # ES 集群地址
    index => "transaction-service-logs-%{+YYYY.MM.dd}"  # 按日动态索引
  }
}
```

### 配置原理

1. **Input 阶段**
   - 监听 TCP 端口 5000
   - 使用 `json_lines` 编码器解析每行 JSON
   - 自动添加 `@timestamp` 字段

2. **Filter 阶段**
   - 当前无自定义过滤器
   - 可扩展添加 grok、mutate、date 等插件

3. **Output 阶段**
   - 写入 Elasticsearch
   - 索引名称动态生成：`transaction-service-logs-2025.10.25`
   - 自动创建映射（Mapping）

### 索引命名策略

```
模式: transaction-service-logs-%{+YYYY.MM.dd}

示例:
- 2025-10-25 的日志 → transaction-service-logs-2025.10.25
- 2025-10-26 的日志 → transaction-service-logs-2025.10.26

优点:
✓ 按日分片，便于数据管理
✓ 支持 ILM（Index Lifecycle Management）策略
✓ 方便批量删除过期数据
✓ Kibana 可用通配符查询: transaction-service-logs-*
```

---

## 应用层集成

### Maven 依赖

**文件位置**: [transaction-service/pom.xml](transaction-service/pom.xml#L122-L126)

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>7.4</version>
</dependency>
```

**作用**：
- 提供 `LogstashTcpSocketAppender` 用于 TCP 日志传输
- 提供 `LogstashEncoder` 将日志格式化为 JSON

### Logback 配置

**文件位置**: [transaction-service/src/main/resources/logback-spring.xml](transaction-service/src/main/resources/logback-spring.xml)

```xml
<configuration>

    <!-- 控制台输出 -->
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss} %-5level [%thread] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Logstash TCP 输出 -->
    <appender name="LOGSTASH" class="net.logstash.logback.appender.LogstashTcpSocketAppender">
        <destination>localhost:5001</destination>  <!-- Logstash 地址 -->
        <encoder class="net.logstash.logback.encoder.LogstashEncoder" />
    </appender>

    <!-- 根日志记录器 -->
    <root level="INFO">
        <appender-ref ref="CONSOLE"/>   <!-- 本地控制台 -->
        <appender-ref ref="LOGSTASH"/>  <!-- 远程 Logstash -->
    </root>

</configuration>
```

### MDC（Mapped Diagnostic Context）配置

**文件位置**: [transaction-service/src/main/resources/application.yml](transaction-service/src/main/resources/application.yml#L44-L46)

```yaml
logging:
  pattern:
    level: "%5p [traceId=%X{traceId}, spanId=%X{spanId}, user=%X{userId}]"
```

**作用**：
- 在日志中注入分布式追踪 ID（traceId、spanId）
- 关联用户上下文（userId）
- 支持跨服务调用链路追踪

### 服务集成状态

| 服务 | 端口 | Logback 配置 | Logstash 依赖 | 状态 |
|------|------|--------------|---------------|------|
| Transaction Service | 8083 | ✅ 已配置 | ✅ v7.4 | 已启用 |
| Account Service | 8082 | ❌ 未配置 | ❌ | 未启用 |
| Auth Service | 8081 | ❌ 未配置 | ❌ | 未启用 |
| Fraud Service | 8085 | ❌ 未配置 | ❌ | 未启用 |
| Notification Service | 8086 | ❌ 未配置 | ❌ | 未启用 |
| API Gateway | 8080 | ❌ 未配置 | ❌ | 未启用 |

---

## 数据流与调用时序

### 完整调用链路

```
时序图（Mermaid 格式）:

sequenceDiagram
    participant App as Transaction Service
    participant LB as Logback
    participant LS as Logstash
    participant ES as Elasticsearch
    participant KB as Kibana

    Note over App: 1. 业务代码执行
    App->>App: logger.info("Processing transfer | key={} | amount={}", key, amount)

    Note over LB: 2. Logback 拦截日志
    App->>LB: Log Event (Level, Message, MDC)
    LB->>LB: 添加 Timestamp, Thread, Logger Name
    LB->>LB: 从 MDC 提取 traceId, spanId, userId

    Note over LB: 3. 双输出
    LB->>App: CONSOLE Appender (控制台输出)
    LB->>LS: LOGSTASH Appender (TCP JSON)

    Note over LS: 4. Logstash 接收
    LS->>LS: TCP Input Plugin (Port 5000)
    LS->>LS: JSON Lines Codec 解析
    LS->>LS: 添加 @timestamp, host 字段

    Note over ES: 5. 写入 Elasticsearch
    LS->>ES: HTTP POST /_bulk
    ES->>ES: 动态创建索引 transaction-service-logs-2025.10.25
    ES->>ES: 自动映射 JSON 字段类型
    ES->>ES: 写入 Lucene 索引文件
    ES-->>LS: 200 OK

    Note over KB: 6. Kibana 查询
    KB->>ES: GET /transaction-service-logs-*/_search
    ES->>ES: 查询 Lucene 索引
    ES-->>KB: 返回匹配文档
    KB->>KB: 渲染 Discover 界面
```

### 时序分解说明

#### 阶段 1: 应用层日志生成
```java
// 示例代码片段
@Slf4j
@Service
public class TransactionService {
    public void processTransfer(String key, BigDecimal amount) {
        MDC.put("traceId", generateTraceId());
        MDC.put("userId", getCurrentUser());

        log.info("Processing transfer | key={} | amount={}", key, amount);
        // 业务逻辑...
    }
}
```

#### 阶段 2: Logback 日志处理
```
LogEvent {
    timestamp: 2025-10-25T10:30:45.123Z
    level: INFO
    logger: com.finpay.transactions.services.TransactionService
    thread: http-nio-8083-exec-1
    message: "Processing transfer | key=abc123 | amount=200.00"
    mdc: {
        traceId: "b7ad6b144ac7d0fb"
        spanId: "b7ad6b144ac7d0fb"
        userId: "alice@example.com"
    }
}
```

#### 阶段 3: TCP 传输到 Logstash
```json
{
  "@timestamp": "2025-10-25T10:30:45.123Z",
  "@version": "1",
  "message": "Processing transfer | key=abc123 | amount=200.00",
  "logger_name": "com.finpay.transactions.services.TransactionService",
  "thread_name": "http-nio-8083-exec-1",
  "level": "INFO",
  "level_value": 20000,
  "traceId": "b7ad6b144ac7d0fb",
  "spanId": "b7ad6b144ac7d0fb",
  "userId": "alice@example.com"
}
```

#### 阶段 4: Logstash 转发到 Elasticsearch
```http
POST /transaction-service-logs-2025.10.25/_doc
Content-Type: application/json

{
  "@timestamp": "2025-10-25T10:30:45.123Z",
  "message": "Processing transfer | key=abc123 | amount=200.00",
  "level": "INFO",
  "logger_name": "com.finpay.transactions.services.TransactionService",
  "thread_name": "http-nio-8083-exec-1",
  "traceId": "b7ad6b144ac7d0fb",
  "userId": "alice@example.com",
  "host": "transaction-service",
  "port": 8083
}
```

#### 阶段 5: Elasticsearch 索引
```
索引写入流程:
1. 接收 HTTP 请求
2. 解析 JSON 文档
3. 动态映射字段类型:
   - @timestamp → date
   - level → keyword
   - message → text (分词) + keyword (精确匹配)
   - traceId → keyword
4. 写入 Lucene 倒排索引
5. 返回文档 ID
```

---

## 索引结构与数据模型

### 索引映射（Mapping）

Elasticsearch 自动推断的映射结构：

```json
{
  "transaction-service-logs-2025.10.25": {
    "mappings": {
      "properties": {
        "@timestamp": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        },
        "@version": {
          "type": "keyword"
        },
        "message": {
          "type": "text",
          "fields": {
            "keyword": {
              "type": "keyword",
              "ignore_above": 256
            }
          }
        },
        "level": {
          "type": "keyword"
        },
        "level_value": {
          "type": "long"
        },
        "logger_name": {
          "type": "keyword"
        },
        "thread_name": {
          "type": "keyword"
        },
        "traceId": {
          "type": "keyword"
        },
        "spanId": {
          "type": "keyword"
        },
        "userId": {
          "type": "keyword"
        },
        "host": {
          "type": "keyword"
        },
        "port": {
          "type": "long"
        }
      }
    }
  }
}
```

### 字段类型说明

| 字段名 | 类型 | 用途 | 是否分词 |
|--------|------|------|----------|
| `@timestamp` | date | 日志时间戳，用于时间范围查询 | - |
| `message` | text + keyword | 日志消息，支持全文搜索和精确匹配 | 是 (text) |
| `level` | keyword | 日志级别（INFO/ERROR/DEBUG/WARN） | 否 |
| `logger_name` | keyword | Java 类名 | 否 |
| `thread_name` | keyword | 线程名称 | 否 |
| `traceId` | keyword | 分布式追踪 ID | 否 |
| `spanId` | keyword | Span ID | 否 |
| `userId` | keyword | 用户标识 | 否 |

### 示例文档

```json
{
  "_index": "transaction-service-logs-2025.10.25",
  "_id": "AWxyz123456",
  "_score": 1.0,
  "_source": {
    "@timestamp": "2025-10-25T10:30:45.123Z",
    "@version": "1",
    "message": "Processing TRANSFER | key=abc123 | from=550e8400-e29b-41d4-a716-446655440000 | to=650e8400-e29b-41d4-a716-446655440001 | amount=200.00",
    "logger_name": "com.finpay.transactions.services.TransactionService",
    "thread_name": "http-nio-8083-exec-1",
    "level": "INFO",
    "level_value": 20000,
    "traceId": "b7ad6b144ac7d0fb",
    "spanId": "b7ad6b144ac7d0fb",
    "userId": "alice@example.com",
    "host": "transaction-service",
    "port": 8083
  }
}
```

---

## 查询与使用

### Kibana 设置

#### 1. 创建索引模式

```bash
# 访问 Kibana
http://localhost:5601

# Stack Management → Index Patterns → Create index pattern
# 索引模式: transaction-service-logs-*
# 时间字段: @timestamp
```

#### 2. Discover 查询语法

**基础查询**

```
# 查询所有错误日志
level:ERROR

# 查询特定服务
service_name:transaction-service

# 查询特定用户
userId:"alice@example.com"

# 查询失败的交易
message:*FAILED*

# 查询慢请求（需要在日志中记录 duration）
duration:>1000
```

**复合查询**

```
# 查询过去1小时的错误
level:ERROR AND @timestamp:[now-1h TO now]

# 查询包含追踪ID的错误
level:ERROR AND traceId:*

# 查询数据库连接错误
message:*Connection* AND level:ERROR AND logger_name:*Repository*

# 查询特定交易的完整调用链
traceId:"b7ad6b144ac7d0fb"
```

**高级查询（KQL - Kibana Query Language）**

```
# 范围查询
@timestamp >= "2025-10-25T00:00:00" and @timestamp < "2025-10-26T00:00:00"

# 正则表达式
message:/Processing (TRANSFER|DEPOSIT|WITHDRAWAL).*/

# 存在字段
userId:* and traceId:*

# 不存在字段
NOT _exists_:error_message
```

### REST API 查询

#### 1. 检查索引状态

```bash
# 列出所有索引
curl -X GET "localhost:9200/_cat/indices?v"

# 查看特定索引
curl -X GET "localhost:9200/transaction-service-logs-2025.10.25"

# 查看索引映射
curl -X GET "localhost:9200/transaction-service-logs-2025.10.25/_mapping"
```

#### 2. 搜索查询

```bash
# 查询所有错误日志
curl -X GET "localhost:9200/transaction-service-logs-*/_search" -H 'Content-Type: application/json' -d'
{
  "query": {
    "term": {
      "level": "ERROR"
    }
  },
  "size": 10,
  "sort": [
    { "@timestamp": { "order": "desc" } }
  ]
}
'

# 查询特定用户的日志
curl -X GET "localhost:9200/transaction-service-logs-*/_search" -H 'Content-Type: application/json' -d'
{
  "query": {
    "bool": {
      "must": [
        { "term": { "userId.keyword": "alice@example.com" } },
        { "range": { "@timestamp": { "gte": "now-1h" } } }
      ]
    }
  }
}
'

# 聚合查询：统计各级别日志数量
curl -X GET "localhost:9200/transaction-service-logs-*/_search" -H 'Content-Type: application/json' -d'
{
  "size": 0,
  "aggs": {
    "log_levels": {
      "terms": {
        "field": "level",
        "size": 10
      }
    }
  }
}
'
```

#### 3. 追踪特定交易

```bash
# 通过 traceId 查询完整调用链
curl -X GET "localhost:9200/transaction-service-logs-*/_search" -H 'Content-Type: application/json' -d'
{
  "query": {
    "term": {
      "traceId": "b7ad6b144ac7d0fb"
    }
  },
  "sort": [
    { "@timestamp": { "order": "asc" } }
  ],
  "size": 100
}
'
```

### 常见查询场景

| 场景 | Kibana 查询 | 用途 |
|------|-------------|------|
| 错误监控 | `level:ERROR` | 查找系统错误 |
| 慢请求分析 | `duration:>1000` | 性能优化 |
| 用户行为追踪 | `userId:"alice@example.com"` | 用户审计 |
| 交易失败排查 | `message:*FAILED* AND level:ERROR` | 故障诊断 |
| 分布式追踪 | `traceId:"xxx"` | 调用链分析 |
| 数据库问题 | `message:*SQLException* OR message:*Connection*` | DB 连接问题 |

---

## 运维与监控

### 启动与停止

```bash
# 启动所有服务（包括 ELK）
docker-compose up -d

# 仅启动 ELK 栈
docker-compose up -d elasticsearch logstash kibana

# 查看日志
docker-compose logs -f elasticsearch
docker-compose logs -f logstash

# 停止服务
docker-compose down

# 停止并删除数据
docker-compose down -v
```

### 健康检查

```bash
# Elasticsearch 健康状态
curl -X GET "localhost:9200/_cluster/health?pretty"

# 预期输出
{
  "cluster_name": "docker-cluster",
  "status": "green",        # green/yellow/red
  "number_of_nodes": 1,
  "active_shards": 10
}

# Logstash 健康检查（需要进入容器）
docker exec -it logstash curl -X GET "localhost:9600/_node/stats/pipelines?pretty"

# Kibana 健康检查
curl -X GET "localhost:5601/api/status"
```

### 性能优化

#### 1. JVM 堆内存调整

```yaml
# docker-compose.yml
elasticsearch:
  environment:
    - ES_JAVA_OPTS=-Xms1g -Xmx1g  # 生产环境建议至少 2GB
```

#### 2. 索引生命周期管理（ILM）

```bash
# 创建 ILM 策略：30天后删除旧索引
curl -X PUT "localhost:9200/_ilm/policy/transaction-logs-policy" -H 'Content-Type: application/json' -d'
{
  "policy": {
    "phases": {
      "hot": {
        "actions": {
          "rollover": {
            "max_size": "50GB",
            "max_age": "1d"
          }
        }
      },
      "delete": {
        "min_age": "30d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
'
```

#### 3. 批量删除旧索引

```bash
# 删除 30 天前的索引
curl -X DELETE "localhost:9200/transaction-service-logs-2025.09.*"

# 使用脚本批量删除
for i in {01..25}; do
  curl -X DELETE "localhost:9200/transaction-service-logs-2025.09.$i"
done
```

### 常见问题排查

#### 问题 1: Elasticsearch 无法启动

```bash
# 检查日志
docker-compose logs elasticsearch

# 常见原因：vm.max_map_count 不足
# 解决方法（macOS/Linux）
sudo sysctl -w vm.max_map_count=262144

# 永久生效
echo "vm.max_map_count=262144" | sudo tee -a /etc/sysctl.conf
```

#### 问题 2: Logstash 无法连接 Elasticsearch

```bash
# 检查网络连通性
docker exec -it logstash curl -X GET "http://elasticsearch:9200"

# 检查 Logstash 配置
docker exec -it logstash cat /usr/share/logstash/pipeline/logstash.conf
```

#### 问题 3: 应用日志未出现在 Kibana

```bash
# 1. 检查应用是否发送日志
docker-compose logs transaction-service | grep "LogstashTcpSocketAppender"

# 2. 检查 Logstash 是否接收
docker-compose logs logstash | grep "Pipeline started"

# 3. 检查 Elasticsearch 索引
curl -X GET "localhost:9200/_cat/indices?v" | grep transaction

# 4. 刷新 Kibana 索引模式
# Stack Management → Index Patterns → transaction-service-logs-* → Refresh
```

### 监控指标

```bash
# Elasticsearch 节点统计
curl -X GET "localhost:9200/_nodes/stats?pretty"

# 索引统计
curl -X GET "localhost:9200/transaction-service-logs-*/_stats?pretty"

# 关键指标
{
  "indices": {
    "docs": { "count": 125847 },           # 文档总数
    "store": { "size_in_bytes": 45678123 }, # 磁盘占用
    "search": { "query_total": 5678 },     # 查询次数
    "indexing": { "index_total": 125847 }  # 索引次数
  }
}
```

---

## 扩展与最佳实践

### 1. 其他服务集成

为其他微服务（如 Account Service、Fraud Service）添加日志聚合：

**步骤**：
1. 添加 Logstash 依赖到 `pom.xml`
2. 创建 `logback-spring.xml` 配置文件
3. 修改 `logstash.conf` 支持多服务索引：

```conf
output {
  elasticsearch {
    hosts => ["http://elasticsearch:9200"]
    # 根据服务名动态路由索引
    index => "%{service_name}-logs-%{+YYYY.MM.dd}"
  }
}
```

### 2. 结构化日志最佳实践

```java
// 不推荐：纯文本日志
log.info("User alice transferred 200.00 to bob");

// 推荐：使用占位符
log.info("Transfer completed | from={} | to={} | amount={}", fromUser, toUser, amount);

// 最佳：使用 Markers 和 MDC
MDC.put("transactionId", txId);
MDC.put("fromAccount", fromId);
MDC.put("toAccount", toId);
log.info("TRANSFER_COMPLETED | amount={}", amount);
MDC.clear();
```

### 3. 分布式追踪集成

结合 Spring Cloud Sleuth：

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
```

Sleuth 会自动注入 `traceId` 和 `spanId` 到 MDC，无需手动管理。

### 4. 告警配置

在 Kibana 中配置告警规则：

```
Kibana → Stack Management → Rules and Connectors → Create rule

条件示例:
- 过去 5 分钟内错误日志数量 > 10
- 查询: level:ERROR AND @timestamp:[now-5m TO now]
- 操作: 发送邮件/Slack 通知
```

---

## 总结

### 核心要点

1. **架构定位**：ELK 栈用于日志聚合，非业务数据搜索
2. **集成方式**：通过 Logback + Logstash Encoder 实现无侵入式日志收集
3. **索引策略**：按日分片，便于生命周期管理
4. **查询方式**：Kibana UI（推荐）或 REST API
5. **运维重点**：JVM 内存调优、ILM 策略、定期清理旧索引

### 关键文件清单

| 文件路径 | 作用 |
|---------|------|
| [docker-compose.yml](docker-compose.yml#L93-L124) | ELK 栈容器定义 |
| [logstash.conf](logstash.conf) | 日志管道配置 |
| [transaction-service/pom.xml](transaction-service/pom.xml#L122-L126) | Maven 依赖 |
| [transaction-service/src/main/resources/logback-spring.xml](transaction-service/src/main/resources/logback-spring.xml) | 日志输出配置 |
| [transaction-service/src/main/resources/application.yml](transaction-service/src/main/resources/application.yml#L44-L46) | MDC 模式配置 |

### 访问地址

- **Elasticsearch**: http://localhost:9200
- **Kibana**: http://localhost:5601
- **Logstash TCP**: localhost:5001

---

## 参考资料

- [Elasticsearch 官方文档](https://www.elastic.co/guide/en/elasticsearch/reference/8.10/index.html)
- [Logstash 配置指南](https://www.elastic.co/guide/en/logstash/8.10/index.html)
- [Kibana 用户手册](https://www.elastic.co/guide/en/kibana/8.10/index.html)
- [Logstash Logback Encoder GitHub](https://github.com/logfellow/logstash-logback-encoder)
- [FinPay 学习指南](LEARNING-GUIDE.md#L680-L720) - ELK 章节
