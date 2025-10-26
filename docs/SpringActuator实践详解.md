# Spring Actuator 实践详解

## 目录
- [一、Spring Actuator 概述](#一spring-actuator-概述)
- [二、FinPay 项目中的实现](#二finpay-项目中的实现)
- [三、工作原理深度解析](#三工作原理深度解析)
- [四、监控技术栈集成](#四监控技术栈集成)
- [五、从 0 到 1 实现部署](#五从-0-到-1-实现部署)
- [六、生产环境优化](#六生产环境优化)
- [七、最佳实践](#七最佳实践)

---

## 一、Spring Actuator 概述

### 1.1 什么是 Spring Actuator？

Spring Boot Actuator 是 Spring Boot 提供的**生产级特性模块**，用于监控和管理应用程序。它提供了一系列开箱即用的端点（Endpoints），帮助我们了解应用的运行状态。

**核心功能：**
- 📊 **健康检查 (Health Checks)**: 监控应用程序和依赖服务的健康状态
- 📈 **指标收集 (Metrics)**: 收集应用性能指标（JVM、HTTP请求、数据库连接等）
- ℹ️ **应用信息 (Info)**: 暴露应用元数据和构建信息
- 🔧 **运行时管理**: 动态修改日志级别、查看配置、线程dump等

### 1.2 核心架构

```
┌─────────────────────────────────────────────────────┐
│           Spring Boot Application                   │
│                                                      │
│  ┌───────────────────────────────────────────────┐  │
│  │         Spring Actuator                       │  │
│  │                                               │  │
│  │  ┌──────────┐  ┌──────────┐  ┌───────────┐  │  │
│  │  │ Health   │  │ Metrics  │  │   Info    │  │  │
│  │  │ Endpoint │  │ Endpoint │  │ Endpoint  │  │  │
│  │  └──────────┘  └──────────┘  └───────────┘  │  │
│  │                                               │  │
│  │  ┌──────────────────────────────────────┐   │  │
│  │  │     Micrometer (Metrics Facade)      │   │  │
│  │  └──────────────────────────────────────┘   │  │
│  └───────────────────────────────────────────────┘  │
│                        ↓                             │
└────────────────────────┼─────────────────────────────┘
                         ↓
            ┌────────────────────────┐
            │   Prometheus Registry   │
            └────────────────────────┘
                         ↓
            ┌────────────────────────┐
            │   Prometheus Server     │
            └────────────────────────┘
                         ↓
            ┌────────────────────────┐
            │      Grafana           │
            └────────────────────────┘
```

### 1.3 常用端点

| 端点 | 说明 | 默认启用 | 默认暴露 |
|------|------|---------|---------|
| `/actuator/health` | 应用健康状态 | ✅ | ✅ |
| `/actuator/info` | 应用信息 | ✅ | ✅ |
| `/actuator/metrics` | 指标信息 | ✅ | ❌ |
| `/actuator/prometheus` | Prometheus 格式指标 | ❌ | ❌ |
| `/actuator/loggers` | 日志配置 | ✅ | ❌ |
| `/actuator/env` | 环境变量 | ✅ | ❌ |
| `/actuator/beans` | Spring Bean 列表 | ✅ | ❌ |
| `/actuator/threaddump` | 线程dump | ✅ | ❌ |
| `/actuator/heapdump` | 堆dump | ✅ | ❌ |

---

## 二、FinPay 项目中的实现

### 2.1 当前实现状态

**服务状态总览：**

| 服务 | Actuator 依赖 | 端点配置 | Prometheus | 分布式追踪 |
|------|--------------|---------|-----------|----------|
| Auth Service | ✅ | ❌ | ❌ | ❌ |
| Account Service | ✅ | ❌ | ❌ | ❌ |
| Transaction Service | ✅ | ✅ | ✅ | ✅ |
| Notification Service | ❌ | ❌ | ❌ | ✅ |
| Fraud Service | ✅ | ✅ | ✅ | ❌ |
| API Gateway | ✅ | ❌ | ❌ | ❌ |

### 2.2 依赖配置

在 `pom.xml` 中添加依赖：

```xml
<!-- Spring Boot Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Prometheus 指标导出 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>

<!-- 分布式追踪 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Zipkin 报告 -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

**文件位置：**
- Transaction Service: `finpay/transaction-service/pom.xml:22-24`
- Fraud Service: `finpay/fraud-service/pom.xml:100-101`

### 2.3 配置示例

**Transaction Service 完整配置** (`application.yml`):

```yaml
server:
  port: 8083

management:
  # 分布式追踪配置
  tracing:
    sampling:
      probability: 1.0  # 100% 采样率（生产环境建议 0.1）
    zipkin:
      base-url: http://localhost:9411
      enabled: true

  # 端点暴露配置
  endpoints:
    web:
      exposure:
        include: prometheus,health,info  # 暴露的端点
      base-path: /actuator

  # 单个端点配置
  endpoint:
    health:
      show-details: when-authorized  # 需要授权才显示详情
      probes:
        enabled: true  # 启用 K8s liveness/readiness 探针
    prometheus:
      enabled: true

  # 指标配置
  metrics:
    tags:
      application: ${spring.application.name}  # 为所有指标添加 app 标签

# 日志配置 - 集成追踪信息
logging:
  pattern:
    level: "%5p [traceId=%X{traceId}, spanId=%X{spanId}, user=%X{userId}]"
```

**配置说明：**

| 配置项 | 说明 | 推荐值 |
|-------|------|--------|
| `management.endpoints.web.exposure.include` | 暴露的端点列表 | `health,info,prometheus` |
| `management.endpoint.health.show-details` | 健康详情显示策略 | `when-authorized` |
| `management.tracing.sampling.probability` | 追踪采样率 | 生产: 0.1, 开发: 1.0 |
| `management.metrics.tags` | 全局指标标签 | 添加 application 标签 |

### 2.4 安全配置

在 Spring Security 配置中允许 Actuator 端点访问：

**文件位置：** `transactions/securities/SecurityConfig.java:37-43`

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // 允许公开访问文档和监控端点
            .requestMatchers(
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/actuator/**"  // ⚠️ 生产环境应该添加认证
            ).permitAll()
            // 其他请求需要认证
            .anyRequest().authenticated()
        );

        return http.build();
    }
}
```

**生产环境安全建议：**

```java
// 仅允许特定端点公开访问
.requestMatchers("/actuator/health", "/actuator/info").permitAll()
// 其他 actuator 端点需要 ADMIN 角色
.requestMatchers("/actuator/**").hasRole("ADMIN")
```

### 2.5 分布式追踪配置

**TracingConfig.java** (`transactions/configs/TracingConfig.java`):

```java
@Configuration
public class TracingConfig {

    /**
     * 创建一个采样器函数，总是采样所有追踪。
     * 这意味着所有请求都会被追踪，用于监控和调试。
     * 在生产环境中，建议使用概率采样器来降低开销。
     *
     * @return SamplerFunction 配置为采样所有追踪
     */
    @Bean
    public SamplerFunction<Tracer> defaultSampler() {
        return SamplerFunction.alwaysSample();
    }
}
```

### 2.6 访问端点

**端点 URL 示例：**

```bash
# 健康检查
curl http://localhost:8083/actuator/health

# Prometheus 指标
curl http://localhost:8083/actuator/prometheus

# 查看所有可用端点
curl http://localhost:8083/actuator

# 查看具体指标
curl http://localhost:8083/actuator/metrics/jvm.memory.used
```

---

## 三、工作原理深度解析

### 3.1 Health Endpoint 工作原理

```
HTTP GET /actuator/health
         ↓
┌─────────────────────────────┐
│  HealthEndpoint             │
│  - 聚合所有 HealthIndicator │
└─────────────────────────────┘
         ↓
    并行执行检查
         ↓
┌──────────────┬──────────────┬──────────────┐
│   DB Check   │  Disk Check  │  Redis Check │
│              │              │              │
│  ✅ UP       │  ✅ UP       │  ✅ UP       │
└──────────────┴──────────────┴──────────────┘
         ↓
    聚合结果（取最差状态）
         ↓
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500000000000,
        "free": 300000000000,
        "threshold": 10485760
      }
    }
  }
}
```

**健康状态层级：**
- `UP`: 正常
- `DOWN`: 故障
- `OUT_OF_SERVICE`: 停止服务
- `UNKNOWN`: 未知状态

**聚合策略：** 取所有 HealthIndicator 中最差的状态

### 3.2 自定义 Health Indicator

```java
package com.finpay.transactions.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 自定义健康检查 - Kafka 连接状态
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaTemplate<String, ?> kafkaTemplate;

    public KafkaHealthIndicator(KafkaTemplate<String, ?> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public Health health() {
        try {
            // 尝试获取 Kafka 元数据
            kafkaTemplate.getDefaultTopic();

            return Health.up()
                .withDetail("kafka", "Connected")
                .withDetail("broker", "localhost:9092")
                .build();
        } catch (Exception e) {
            return Health.down()
                .withDetail("kafka", "Disconnected")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

**自定义 Health Indicator 示例：**

```java
/**
 * Fraud Service 健康检查
 */
@Component
public class FraudServiceHealthIndicator implements HealthIndicator {

    private final FraudClient fraudClient;

    @Override
    public Health health() {
        try {
            // 调用 Fraud Service 健康检查
            ResponseEntity<String> response = fraudClient.healthCheck();

            if (response.getStatusCode().is2xxSuccessful()) {
                return Health.up()
                    .withDetail("fraud-service", "Available")
                    .build();
            } else {
                return Health.down()
                    .withDetail("fraud-service", "Unavailable")
                    .withDetail("status", response.getStatusCode())
                    .build();
            }
        } catch (Exception e) {
            return Health.down()
                .withDetail("fraud-service", "Unreachable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### 3.3 Metrics 收集原理

```
应用运行时
    ↓
┌─────────────────────────────────┐
│  Micrometer (指标门面)           │
│  - Counter (计数器)              │
│  - Gauge (仪表)                  │
│  - Timer (计时器)                │
│  - DistributionSummary (分布总结)│
└─────────────────────────────────┘
    ↓
自动收集各种指标
    ↓
┌──────────┬──────────┬──────────┬──────────┐
│ JVM 内存 │ HTTP请求 │ 数据库   │ 自定义   │
│          │          │ 连接池   │ 指标     │
└──────────┴──────────┴──────────┴──────────┘
    ↓
存储到 MeterRegistry
    ↓
┌─────────────────────────────┐
│  PrometheusRegistry         │
│  - 转换为 Prometheus 格式   │
└─────────────────────────────┘
    ↓
Prometheus 定期抓取
GET /actuator/prometheus
    ↓
# HELP jvm_memory_used_bytes The amount of used memory
# TYPE jvm_memory_used_bytes gauge
jvm_memory_used_bytes{area="heap",id="PS Eden Space"} 1.2345678E8
jvm_memory_used_bytes{area="heap",id="PS Old Gen"} 5.6789012E7

# HELP http_server_requests_seconds Duration of HTTP requests
# TYPE http_server_requests_seconds summary
http_server_requests_seconds_count{method="GET",uri="/api/transactions",status="200"} 150
http_server_requests_seconds_sum{method="GET",uri="/api/transactions",status="200"} 2.5
```

**Micrometer 核心概念：**

| 指标类型 | 说明 | 使用场景 |
|---------|------|---------|
| Counter | 单调递增计数器 | 请求数、错误数 |
| Gauge | 可增可减的值 | 内存使用量、线程数 |
| Timer | 记录时长和频率 | 请求响应时间 |
| DistributionSummary | 分布统计 | 请求大小、响应大小 |

### 3.4 自定义指标

**示例 1：计数器 (Counter)**

```java
@Service
public class TransactionService {

    private final Counter transactionCounter;
    private final Counter failedTransactionCounter;

    public TransactionService(MeterRegistry registry) {
        this.transactionCounter = Counter.builder("transactions.created")
            .description("Total transactions created")
            .tag("type", "all")
            .register(registry);

        this.failedTransactionCounter = Counter.builder("transactions.failed")
            .description("Total failed transactions")
            .tag("type", "error")
            .register(registry);
    }

    public TransactionDto createTransaction(TransactionRequestDto request) {
        try {
            // 业务逻辑
            Transaction transaction = processTransaction(request);
            transactionCounter.increment();  // 计数 +1
            return mapToDto(transaction);
        } catch (Exception e) {
            failedTransactionCounter.increment();  // 失败计数 +1
            throw e;
        }
    }
}
```

**示例 2：计时器 (Timer)**

```java
@Service
public class TransactionService {

    private final Timer transactionTimer;

    public TransactionService(MeterRegistry registry) {
        this.transactionTimer = Timer.builder("transactions.processing.time")
            .description("Transaction processing time")
            .tag("service", "transaction")
            .publishPercentileHistogram()  // 启用百分位数
            .register(registry);
    }

    public TransactionDto createTransaction(TransactionRequestDto request) {
        return transactionTimer.record(() -> {
            // 这里的代码执行时间会被自动记录
            return processTransaction(request);
        });
    }
}
```

**示例 3：仪表 (Gauge)**

```java
@Service
public class AccountService {

    private final List<Account> activeAccounts = new ArrayList<>();

    public AccountService(MeterRegistry registry) {
        // 监控活跃账户数量
        Gauge.builder("accounts.active", activeAccounts, List::size)
            .description("Number of active accounts")
            .tag("status", "active")
            .register(registry);
    }
}
```

**使用注解方式：**

```java
@Service
public class TransactionService {

    @Timed(value = "transactions.create", description = "Time to create transaction")
    public TransactionDto createTransaction(TransactionRequestDto request) {
        // 方法执行时间会被自动记录
        return processTransaction(request);
    }

    @Counted(value = "transactions.count", description = "Number of transactions")
    public List<TransactionDto> getAllTransactions() {
        // 方法调用次数会被自动计数
        return findAll();
    }
}
```

### 3.5 分布式追踪原理

```
用户请求 → API Gateway (8080)
    ↓ [生成 traceId: abc123-def456-ghi789]
    ↓ [生成 spanId: span-001]
    │
    ├─→ Transaction Service (8083)
    │   ↓ [继承 traceId: abc123-def456-ghi789]
    │   ↓ [生成 spanId: span-002, parentId: span-001]
    │   │
    │   ├─→ Fraud Service (8085)
    │   │   ↓ [继承 traceId: abc123-def456-ghi789]
    │   │   ↓ [生成 spanId: span-003, parentId: span-002]
    │   │   ↓ [查询风控规则]
    │   │   ↓ [返回结果]
    │   │
    │   └─→ Database Query
    │       ↓ [继承 traceId: abc123-def456-ghi789]
    │       ↓ [生成 spanId: span-004, parentId: span-002]
    │       ↓ [SQL 执行]
    │
    └─→ 所有 span 上报到 Zipkin (9411)

在 Zipkin UI 中可视化：
┌─────────────────────────────────────────────┐
│ trace: abc123-def456-ghi789                 │
├─────────────────────────────────────────────┤
│ API Gateway       [━━━━━━━━━━━━━━━━] 250ms  │
│   Transaction Svc   [━━━━━━━━━━] 200ms     │
│     Fraud Svc         [━━━] 50ms            │
│     DB Query          [━━━━] 100ms          │
└─────────────────────────────────────────────┘
```

**日志中包含追踪信息：**

```yaml
logging:
  pattern:
    level: "%5p [traceId=%X{traceId}, spanId=%X{spanId}, user=%X{userId}]"
```

**输出示例：**
```
2025-01-20 10:30:45.123 INFO [traceId=abc123-def456, spanId=span-002, user=user789] Processing transaction for account 12345
2025-01-20 10:30:45.150 INFO [traceId=abc123-def456, spanId=span-003, user=user789] Calling fraud service
2025-01-20 10:30:45.200 INFO [traceId=abc123-def456, spanId=span-002, user=user789] Transaction completed successfully
```

---

## 四、监控技术栈集成

### 4.1 完整监控架构

```
┌─────────────────────────────────────────────────────────┐
│                 Application Layer                       │
│  ┌──────────────────────────────────────────────────┐   │
│  │ Spring Boot Apps (8081-8085)                     │   │
│  │ - Actuator Endpoints                             │   │
│  │ - Micrometer Metrics                             │   │
│  │ - Brave Tracing                                  │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘
         ↓                    ↓                    ↓
    /actuator/           traces to           logs to
    prometheus           Zipkin              Logstash
         ↓                    ↓                    ↓
┌─────────────────────────────────────────────────────────┐
│                Monitoring & Logging Layer               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │ Prometheus  │  │   Zipkin    │  │  Elasticsearch  │ │
│  │   :9090     │  │   :9411     │  │     :9200       │ │
│  │ - Scrapes   │  │ - Collects  │  │ - Stores logs   │ │
│  │   metrics   │  │   traces    │  │ - Full-text     │ │
│  │ - Stores TS │  │ - Shows UI  │  │   search        │ │
│  └─────────────┘  └─────────────┘  └─────────────────┘ │
└─────────────────────────────────────────────────────────┘
         ↓                                       ↓
    PromQL query                           Query API
         ↓                                       ↓
┌─────────────────────────────────────────────────────────┐
│              Visualization Layer                        │
│  ┌─────────────┐               ┌─────────────────────┐  │
│  │  Grafana    │               │      Kibana         │  │
│  │   :3000     │               │      :5601          │  │
│  │ - Dashboards│               │ - Log analysis      │  │
│  │ - Alerts    │               │ - Visualizations    │  │
│  └─────────────┘               └─────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### 4.2 Docker Compose 配置

**文件位置：** `finpay/docker-compose.yml`

```yaml
services:
  # 分布式追踪
  zipkin:
    image: openzipkin/zipkin
    ports:
      - "9411:9411"

  # 指标收集
  prometheus:
    image: prom/prometheus:latest
    container_name: prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
    ports:
      - "9090:9090"

  # 可视化仪表板
  grafana:
    image: grafana/grafana:latest
    container_name: grafana
    ports:
      - "3000:3000"
    depends_on:
      - prometheus
    environment:
      - GF_SECURITY_ADMIN_USER=admin
      - GF_SECURITY_ADMIN_PASSWORD=admin

  # 日志聚合
  elasticsearch:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.10.2
    container_name: elasticsearch
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
      - ES_JAVA_OPTS=-Xms512m -Xmx512m
    ports:
      - "9200:9200"
      - "9300:9300"

  # 日志可视化
  kibana:
    image: docker.elastic.co/kibana/kibana:8.10.2
    container_name: kibana
    environment:
      - ELASTICSEARCH_HOSTS=http://elasticsearch:9200
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch
```

### 4.3 Prometheus 配置

**文件位置：** `finpay/prometheus.yml`

```yaml
global:
  scrape_interval: 5s  # 每5秒抓取一次指标

scrape_configs:
  # Spring Boot 应用指标
  - job_name: 'spring-boot-apps'
    metrics_path: '/actuator/prometheus'  # Actuator 端点路径
    static_configs:
      - targets:
          - 'host.docker.internal:8083'  # Transaction Service
          - 'host.docker.internal:8085'  # Fraud Service
    # 可以添加更多服务
    # - 'host.docker.internal:8081'  # Auth Service
    # - 'host.docker.internal:8082'  # Account Service

  # Kafka JMX 指标
  - job_name: 'kafka-jmx'
    static_configs:
      - targets: ['host.docker.internal:7071']
```

**配置说明：**
- `scrape_interval`: 抓取频率（5秒一次）
- `metrics_path`: Actuator Prometheus 端点路径
- `host.docker.internal`: 从 Docker 容器访问宿主机服务

**添加更多目标：**

```yaml
scrape_configs:
  - job_name: 'all-services'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets:
          - 'host.docker.internal:8081'  # Auth
          - 'host.docker.internal:8082'  # Account
          - 'host.docker.internal:8083'  # Transaction
          - 'host.docker.internal:8084'  # Notification
          - 'host.docker.internal:8085'  # Fraud
        labels:
          group: 'finpay-services'
```

### 4.4 常用 PromQL 查询

**JVM 内存使用率：**
```promql
# Heap 内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# 按应用分组
sum(jvm_memory_used_bytes{area="heap"}) by (application)
```

**HTTP 请求统计：**
```promql
# 每秒请求数 (RPS)
rate(http_server_requests_seconds_count[1m])

# 平均响应时间
rate(http_server_requests_seconds_sum[1m]) / rate(http_server_requests_seconds_count[1m])

# 按状态码分组
sum(rate(http_server_requests_seconds_count[5m])) by (status)
```

**数据库连接池：**
```promql
# 活跃连接数
hikaricp_connections_active

# 空闲连接数
hikaricp_connections_idle

# 等待连接数
hikaricp_connections_pending

# 连接超时数
rate(hikaricp_connections_timeout_total[1m])
```

**自定义业务指标：**
```promql
# 每分钟创建的交易数
rate(transactions_created_total[1m]) * 60

# 交易失败率
rate(transactions_failed_total[5m]) / rate(transactions_created_total[5m]) * 100

# P95 响应时间
histogram_quantile(0.95, rate(transactions_processing_time_seconds_bucket[5m]))
```

### 4.5 Grafana 仪表板配置

**推荐的社区仪表板：**

| Dashboard ID | 名称 | 说明 |
|-------------|------|------|
| 12900 | Spring Boot 2.1 Statistics | Spring Boot 应用统计 |
| 4701 | JVM Micrometer | JVM 详细指标 |
| 11378 | Spring Boot Actuator | Actuator 端点监控 |
| 6417 | Spring Boot APM Dashboard | 应用性能监控 |

**导入步骤：**
1. 访问 http://localhost:3000
2. 登录 (admin/admin)
3. 左侧菜单 → Dashboards → Import
4. 输入 Dashboard ID (如 12900)
5. 选择 Prometheus 数据源
6. 点击 Import

**自定义面板示例：**

```json
{
  "title": "Transaction Service Metrics",
  "panels": [
    {
      "title": "Transactions Per Second",
      "targets": [
        {
          "expr": "rate(transactions_created_total[1m])",
          "legendFormat": "{{application}}"
        }
      ]
    },
    {
      "title": "Transaction Processing Time (P95)",
      "targets": [
        {
          "expr": "histogram_quantile(0.95, rate(transactions_processing_time_seconds_bucket[5m]))",
          "legendFormat": "P95"
        }
      ]
    }
  ]
}
```

### 4.6 告警配置

**Prometheus 告警规则：**

创建 `prometheus-alerts.yml`:

```yaml
groups:
  - name: spring-boot-alerts
    rules:
      # 内存使用率超过 90%
      - alert: HighMemoryUsage
        expr: |
          (jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"}) > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High heap memory usage on {{ $labels.application }}"
          description: "Heap memory usage is {{ $value | humanizePercentage }}"

      # HTTP 错误率超过 5%
      - alert: HighErrorRate
        expr: |
          (sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (application)
          /
          sum(rate(http_server_requests_seconds_count[5m])) by (application)) > 0.05
        for: 3m
        labels:
          severity: critical
        annotations:
          summary: "High error rate on {{ $labels.application }}"
          description: "Error rate is {{ $value | humanizePercentage }}"

      # 数据库连接池耗尽
      - alert: DatabaseConnectionPoolExhausted
        expr: hikaricp_connections_active / hikaricp_connections_max > 0.9
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool almost exhausted"
          description: "{{ $labels.application }} is using {{ $value | humanizePercentage }} of connections"

      # 服务不可用
      - alert: ServiceDown
        expr: up{job="spring-boot-apps"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.instance }} is down"
```

---

## 五、从 0 到 1 实现部署

### 5.1 部署架构图

```
┌─────────────────────────────────────────────────────┐
│              Infrastructure Layer                   │
│  ┌──────────┬──────────┬──────────┬──────────────┐  │
│  │PostgreSQL│  Redis   │  Kafka   │  Zookeeper   │  │
│  │  :5432   │  :6379   │  :9092   │   :2181      │  │
│  └──────────┴──────────┴──────────┴──────────────┘  │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│              Monitoring Layer                       │
│  ┌──────────┬──────────┬───────────┬─────────────┐ │
│  │ Zipkin   │Prometheus│  Grafana  │Elasticsearch│ │
│  │  :9411   │  :9090   │  :3000    │   :9200     │ │
│  └──────────┴──────────┴───────────┴─────────────┘ │
└─────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────┐
│              Application Layer                      │
│  ┌──────────┬──────────┬──────────┬──────────────┐ │
│  │   Auth   │ Account  │Transaction│Notification │ │
│  │  :8081   │  :8082   │  :8083    │   :8084     │ │
│  └──────────┴──────────┴──────────┴──────────────┘ │
│  ┌──────────┬──────────────────────────────────── ┐ │
│  │  Fraud   │        API Gateway                   │ │
│  │  :8085   │          :8080                       │ │
│  └──────────┴──────────────────────────────────── ┘ │
└─────────────────────────────────────────────────────┘
```

### 5.2 前置准备

**系统要求：**
- Docker Desktop 4.0+
- Java 21
- Maven 3.8+
- 至少 8GB RAM
- 20GB 可用磁盘空间

**验证环境：**

```bash
# 检查 Docker
docker --version
docker compose version

# 检查 Java
java -version

# 检查 Maven
mvn -version
```

### 5.3 部署步骤

#### Step 1: 克隆项目

```bash
cd /Users/mengruwang/Github
git clone <your-repo-url>
cd finpay
```

#### Step 2: 启动基础设施

```bash
# 启动所有 Docker 服务
docker compose up -d

# 查看服务状态
docker compose ps

# 查看日志
docker compose logs -f
```

**启动的服务：**
- ✅ PostgreSQL (数据库) - :5432
- ✅ Redis (缓存) - :6379
- ✅ Kafka + Zookeeper (消息队列) - :9092
- ✅ Zipkin (分布式追踪) - :9411
- ✅ Prometheus (指标收集) - :9090
- ✅ Grafana (可视化) - :3000
- ✅ Elasticsearch (日志存储) - :9200
- ✅ Kibana (日志可视化) - :5601
- ✅ Logstash (日志处理) - :5001

#### Step 3: 构建项目

```bash
# 在项目根目录执行
mvn clean install -DskipTests

# 如果需要运行测试
mvn clean install
```

#### Step 4: 启动微服务

**方式 1：使用启动脚本（推荐）**

```bash
# 添加执行权限
chmod +x start-services.sh

# 启动所有服务
./start-services.sh
```

**脚本功能：**
- ✅ 检查 Docker 状态
- ✅ 验证基础服务运行
- ✅ 按顺序启动微服务
- ✅ 等待每个服务就绪
- ✅ 记录 PID 和日志

**启动顺序：**
1. Auth Service (8081) - 5秒
2. Account Service (8082) - 5秒
3. Transaction Service (8083) - 5秒
4. Notification Service (8084) - 5秒
5. Fraud Service (8085) - 5秒
6. API Gateway (8080) - 5秒

**方式 2：手动启动**

```bash
# 每个服务在单独的终端窗口运行

# Terminal 1 - Auth Service
cd auth-service
mvn spring-boot:run

# Terminal 2 - Account Service
cd account-service
mvn spring-boot:run

# Terminal 3 - Transaction Service
cd transaction-service
mvn spring-boot:run

# Terminal 4 - Notification Service
cd notification-service
mvn spring-boot:run

# Terminal 5 - Fraud Service
cd fraud-service
mvn spring-boot:run

# Terminal 6 - API Gateway
cd api-gateway
mvn spring-boot:run
```

#### Step 5: 验证部署

**检查服务健康状态：**

```bash
# Auth Service
curl http://localhost:8081/actuator/health

# Account Service
curl http://localhost:8082/actuator/health

# Transaction Service
curl http://localhost:8083/actuator/health
curl http://localhost:8083/actuator/prometheus

# Notification Service
curl http://localhost:8084/actuator/health

# Fraud Service
curl http://localhost:8085/actuator/health
curl http://localhost:8085/actuator/prometheus

# API Gateway
curl http://localhost:8080/actuator/health
```

**预期输出：**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "diskSpace": {
      "status": "UP"
    },
    "ping": {
      "status": "UP"
    }
  }
}
```

#### Step 6: 访问监控面板

| 服务 | URL | 凭据 | 说明 |
|------|-----|------|------|
| API Gateway | http://localhost:8080 | - | 统一入口 |
| Swagger UI | http://localhost:8080/swagger-ui.html | - | API 文档 |
| Prometheus | http://localhost:9090 | - | 指标查询 |
| Grafana | http://localhost:3000 | admin/admin | 可视化仪表板 |
| Zipkin | http://localhost:9411 | - | 分布式追踪 |
| Kibana | http://localhost:5601 | - | 日志分析 |
| PgAdmin | http://localhost:5050 | admin@example.com/finpay | 数据库管理 |

#### Step 7: 配置 Grafana

**首次登录配置：**

1. 访问 http://localhost:3000
2. 登录 (admin/admin)
3. 修改密码（可选）

**添加 Prometheus 数据源：**

```bash
Configuration → Data Sources → Add data source
- 选择 Prometheus
- URL: http://prometheus:9090
- Access: Server (default)
- 点击 "Save & Test"
```

**导入仪表板：**

```bash
Create → Import
- 输入 Dashboard ID: 12900
- 选择 Prometheus 数据源
- 点击 Import
```

**推荐仪表板：**
- 12900: Spring Boot Statistics
- 4701: JVM Micrometer
- 11378: Spring Boot Actuator

#### Step 8: 测试完整流程

**创建测试请求：**

```bash
# 1. 注册用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "email": "test@example.com",
    "password": "password123"
  }'

# 2. 登录获取 token
TOKEN=$(curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }' | jq -r '.token')

# 3. 创建交易
curl -X POST http://localhost:8080/api/transactions \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "fromAccountId": 1,
    "toAccountId": 2,
    "amount": 100.00,
    "description": "Test transaction"
  }'

# 4. 查看 Zipkin 追踪
# 访问 http://localhost:9411
# 搜索刚才的请求，查看完整调用链路
```

#### Step 9: 停止服务

```bash
# 停止所有微服务
./stop-services.sh

# 停止 Docker 服务（可选）
docker compose down

# 完全清理（包括数据卷）
docker compose down -v
```

### 5.4 启动脚本详解

**start-services.sh** 功能说明：

```bash
#!/bin/bash

# 1. 环境检查
- 检查 Docker 是否运行
- 验证必需的 Docker 服务状态
- 创建日志目录

# 2. 启动 Docker 服务（如果未运行）
docker compose up -d

# 3. 按顺序启动微服务
for service in auth account transaction notification fraud gateway; do
  - 进入服务目录
  - 使用 nohup 后台运行
  - 保存 PID
  - 记录日志到 logs/$service.log
  - 等待服务就绪（检查端口）
done

# 4. 输出服务信息
- 显示所有服务 URL
- 显示日志位置
- 显示停止命令
```

**日志位置：**
```
finpay/
└── logs/
    ├── auth-service.log
    ├── account-service.log
    ├── transaction-service.log
    ├── notification-service.log
    ├── fraud-service.log
    └── api-gateway.log
```

**查看日志：**
```bash
# 实时查看某个服务日志
tail -f logs/transaction-service.log

# 查看所有日志
tail -f logs/*.log

# 搜索错误
grep -r "ERROR" logs/
```

### 5.5 故障排查

**问题 1：Docker 服务无法启动**

```bash
# 检查端口占用
lsof -i :5432  # PostgreSQL
lsof -i :6379  # Redis
lsof -i :9092  # Kafka

# 停止冲突进程
kill -9 <PID>

# 重启 Docker
docker compose down
docker compose up -d
```

**问题 2：微服务启动失败**

```bash
# 检查日志
tail -100 logs/transaction-service.log

# 常见问题：
# - 数据库连接失败 → 检查 PostgreSQL 是否运行
# - 端口被占用 → 使用 lsof -i :8083 检查
# - 依赖服务未就绪 → 等待 Docker 服务完全启动
```

**问题 3：Prometheus 无法抓取指标**

```bash
# 检查 Actuator 端点
curl http://localhost:8083/actuator/prometheus

# 检查 Prometheus targets
访问 http://localhost:9090/targets

# 常见问题：
# - 端点未暴露 → 检查 application.yml 配置
# - 防火墙阻止 → 检查网络设置
# - 目标地址错误 → 检查 prometheus.yml
```

---

## 六、生产环境优化

### 6.1 改进清单

根据当前实现，需要以下改进：

| 改进项 | 当前状态 | 目标状态 | 优先级 |
|-------|---------|---------|--------|
| 统一 Actuator 配置 | 2/6 服务 | 6/6 服务 | 🔴 高 |
| 自定义健康检查 | 0 个 | 每服务 2-3 个 | 🔴 高 |
| Actuator 端点安全 | 完全公开 | 基于角色认证 | 🔴 高 |
| 采样率配置 | 100% | 10% | 🟡 中 |
| 自定义业务指标 | 0 个 | 每服务 5+ 个 | 🟡 中 |
| Docker 镜像 | 不存在 | 所有服务 | 🟡 中 |
| K8s 部署配置 | 不存在 | 完整清单 | 🟢 低 |
| 告警规则 | 不存在 | 10+ 规则 | 🟡 中 |

### 6.2 统一 Actuator 配置

**创建公共配置文件：** `common/src/main/resources/application-actuator.yml`

```yaml
management:
  endpoints:
    web:
      exposure:
        # 生产环境只暴露必要端点
        include: health,info,prometheus
      base-path: /actuator

  endpoint:
    health:
      # 需要授权才显示详细信息
      show-details: when-authorized
      # 启用 Kubernetes 探针支持
      probes:
        enabled: true
    prometheus:
      enabled: true

  # 指标配置
  metrics:
    tags:
      # 为所有指标添加应用标签
      application: ${spring.application.name}
      environment: ${spring.profiles.active:default}
    distribution:
      # 启用百分位数统计
      percentiles-histogram:
        http.server.requests: true

  # 健康检查配置
  health:
    # Kubernetes liveness 探针
    livenessState:
      enabled: true
    # Kubernetes readiness 探针
    readinessState:
      enabled: true
    # 数据库健康检查
    db:
      enabled: true
    # 磁盘空间检查
    diskspace:
      enabled: true
      threshold: 10MB

  # 分布式追踪（生产环境）
  tracing:
    sampling:
      probability: 0.1  # 10% 采样率
    zipkin:
      base-url: ${ZIPKIN_URL:http://localhost:9411}
      enabled: true

# 日志模式 - 包含追踪信息
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

**在每个服务的 application.yml 中引入：**

```yaml
spring:
  profiles:
    include: actuator  # 引入公共配置
```

### 6.3 自定义健康检查实现

**Database Health Indicator:**

```java
package com.finpay.common.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 数据库健康检查
 * 检查数据库连接和响应时间
 */
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthIndicator(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Health health() {
        try {
            long startTime = System.currentTimeMillis();

            // 执行简单查询
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);

            long responseTime = System.currentTimeMillis() - startTime;

            if (responseTime > 1000) {
                return Health.down()
                    .withDetail("database", "PostgreSQL")
                    .withDetail("responseTime", responseTime + "ms")
                    .withDetail("reason", "Slow response")
                    .build();
            }

            return Health.up()
                .withDetail("database", "PostgreSQL")
                .withDetail("responseTime", responseTime + "ms")
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("database", "PostgreSQL")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

**Kafka Health Indicator:**

```java
package com.finpay.transactions.health;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Kafka 健康检查
 */
@Component
public class KafkaHealthIndicator implements HealthIndicator {

    private final KafkaAdmin kafkaAdmin;

    public KafkaHealthIndicator(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    @Override
    public Health health() {
        try (AdminClient adminClient = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {

            DescribeClusterResult cluster = adminClient.describeCluster();

            // 设置 3 秒超时
            int nodeCount = cluster.nodes().get(3, TimeUnit.SECONDS).size();
            String clusterId = cluster.clusterId().get(3, TimeUnit.SECONDS);

            return Health.up()
                .withDetail("kafka", "Connected")
                .withDetail("clusterId", clusterId)
                .withDetail("nodes", nodeCount)
                .build();

        } catch (Exception e) {
            return Health.down()
                .withDetail("kafka", "Disconnected")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

**Downstream Service Health Indicator (Fraud Service):**

```java
package com.finpay.transactions.health;

import com.finpay.transactions.clients.FraudClient;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * 下游服务健康检查 - Fraud Service
 */
@Component
public class FraudServiceHealthIndicator implements HealthIndicator {

    private final FraudClient fraudClient;

    public FraudServiceHealthIndicator(FraudClient fraudClient) {
        this.fraudClient = fraudClient;
    }

    @Override
    public Health health() {
        try {
            // 调用 Fraud Service 健康检查端点
            String response = fraudClient.healthCheck();

            if ("UP".equals(response)) {
                return Health.up()
                    .withDetail("fraud-service", "Available")
                    .withDetail("url", "http://localhost:8085")
                    .build();
            } else {
                return Health.down()
                    .withDetail("fraud-service", "Unhealthy")
                    .withDetail("status", response)
                    .build();
            }

        } catch (Exception e) {
            return Health.down()
                .withDetail("fraud-service", "Unreachable")
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

**Feign Client 添加健康检查方法：**

```java
@FeignClient(name = "fraud-service", url = "${fraud.service.url:http://localhost:8085}")
public interface FraudClient {

    @GetMapping("/actuator/health")
    String healthCheck();

    // 其他业务方法...
}
```

### 6.4 Actuator 端点安全

**SecurityConfig 安全加固：**

```java
package com.finpay.transactions.securities;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            // 公开访问的端点（不需要认证）
            .requestMatchers(
                "/actuator/health",           // 健康检查
                "/actuator/health/liveness",  // K8s liveness 探针
                "/actuator/health/readiness", // K8s readiness 探针
                "/actuator/info"              // 应用信息
            ).permitAll()

            // 监控端点需要 ADMIN 角色
            .requestMatchers(
                "/actuator/**"
            ).hasRole("ADMIN")

            // API 文档需要认证
            .requestMatchers(
                "/swagger-ui/**",
                "/v3/api-docs/**"
            ).authenticated()

            // 其他所有请求需要认证
            .anyRequest().authenticated()
        )
        .oauth2ResourceServer(oauth2 -> oauth2.jwt())  // JWT 认证
        .csrf(csrf -> csrf.disable());  // API 不需要 CSRF

        return http.build();
    }
}
```

**application.yml 安全配置：**

```yaml
management:
  endpoints:
    web:
      exposure:
        # 只暴露最少必要端点
        include: health,info,prometheus

  endpoint:
    health:
      # 根据认证状态显示详情
      show-details: when-authorized
      # 启用角色权限
      roles: ADMIN
```

**创建监控用户：**

```sql
-- 创建专门的监控账户
INSERT INTO users (username, password, role, enabled)
VALUES ('prometheus-exporter', '$2a$10$...', 'ROLE_ADMIN', true);
```

### 6.5 自定义业务指标

**Transaction Service 指标：**

```java
package com.finpay.transactions.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * 交易服务业务指标
 */
@Component
public class TransactionMetrics {

    private final Counter transactionCreatedCounter;
    private final Counter transactionFailedCounter;
    private final Counter fraudDetectedCounter;
    private final Timer transactionProcessingTimer;
    private final MeterRegistry registry;

    public TransactionMetrics(MeterRegistry registry) {
        this.registry = registry;

        // 交易创建计数
        this.transactionCreatedCounter = Counter.builder("transactions.created")
            .description("Total number of transactions created")
            .tag("service", "transaction")
            .register(registry);

        // 交易失败计数
        this.transactionFailedCounter = Counter.builder("transactions.failed")
            .description("Total number of failed transactions")
            .tag("service", "transaction")
            .register(registry);

        // 欺诈检测计数
        this.fraudDetectedCounter = Counter.builder("transactions.fraud.detected")
            .description("Total number of fraud transactions detected")
            .tag("service", "transaction")
            .register(registry);

        // 交易处理时间
        this.transactionProcessingTimer = Timer.builder("transactions.processing.time")
            .description("Time taken to process a transaction")
            .tag("service", "transaction")
            .publishPercentileHistogram()  // 启用百分位数
            .register(registry);
    }

    public void incrementCreated() {
        transactionCreatedCounter.increment();
    }

    public void incrementFailed() {
        transactionFailedCounter.increment();
    }

    public void incrementFraudDetected() {
        fraudDetectedCounter.increment();
    }

    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public void recordProcessingTime(Timer.Sample sample) {
        sample.stop(transactionProcessingTimer);
    }

    // 按交易类型计数
    public void incrementByType(String type) {
        Counter.builder("transactions.by.type")
            .description("Transactions grouped by type")
            .tag("type", type)
            .register(registry)
            .increment();
    }
}
```

**在 Service 中使用指标：**

```java
@Service
public class TransactionService {

    private final TransactionMetrics metrics;

    public TransactionDto createTransaction(TransactionRequestDto request) {
        Timer.Sample sample = metrics.startTimer();

        try {
            // 业务逻辑
            Transaction transaction = processTransaction(request);

            // 检查欺诈
            if (fraudService.isFraudulent(transaction)) {
                metrics.incrementFraudDetected();
                throw new FraudException("Fraudulent transaction detected");
            }

            // 保存交易
            transactionRepository.save(transaction);

            // 记录成功指标
            metrics.incrementCreated();
            metrics.incrementByType(transaction.getType());

            return mapToDto(transaction);

        } catch (Exception e) {
            // 记录失败指标
            metrics.incrementFailed();
            throw e;

        } finally {
            // 记录处理时间
            metrics.recordProcessingTime(sample);
        }
    }
}
```

**Account Service 指标：**

```java
@Component
public class AccountMetrics {

    private final Gauge activeAccountsGauge;
    private final Counter accountCreatedCounter;

    public AccountMetrics(MeterRegistry registry, AccountRepository accountRepository) {
        // 活跃账户数量（实时）
        this.activeAccountsGauge = Gauge.builder("accounts.active",
            accountRepository, repo -> repo.countByStatus("ACTIVE"))
            .description("Number of active accounts")
            .tag("service", "account")
            .register(registry);

        // 账户创建计数
        this.accountCreatedCounter = Counter.builder("accounts.created")
            .description("Total accounts created")
            .tag("service", "account")
            .register(registry);
    }
}
```

### 6.6 采样率环境配置

**使用 Spring Profiles 管理采样率：**

**application-dev.yml** (开发环境):
```yaml
management:
  tracing:
    sampling:
      probability: 1.0  # 100% 采样，方便调试
```

**application-prod.yml** (生产环境):
```yaml
management:
  tracing:
    sampling:
      probability: 0.1  # 10% 采样，降低开销
```

**启动时指定 profile：**
```bash
# 开发环境
java -jar transaction-service.jar --spring.profiles.active=dev

# 生产环境
java -jar transaction-service.jar --spring.profiles.active=prod
```

### 6.7 Docker 镜像构建

**Dockerfile** (每个服务):

```dockerfile
# 多阶段构建
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# 复制 pom.xml 和依赖
COPY pom.xml .
COPY ../common/pom.xml ../common/
RUN mvn dependency:go-offline

# 复制源码并构建
COPY src ./src
COPY ../common/src ../common/src
RUN mvn clean package -DskipTests

# 运行阶段
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# 复制 jar 文件
COPY --from=builder /app/target/*.jar app.jar

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8083/actuator/health || exit 1

# 暴露端口
EXPOSE 8083

# 启动命令
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
```

**docker-compose.yml** 更新：

```yaml
services:
  transaction-service:
    build:
      context: ./transaction-service
      dockerfile: Dockerfile
    image: finpay/transaction-service:latest
    container_name: transaction-service
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=docker
      - SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/finpay
      - KAFKA_BOOTSTRAP_SERVERS=kafka:29092
      - ZIPKIN_BASE_URL=http://zipkin:9411
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_started
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8083/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
    networks:
      - finpay-network

networks:
  finpay-network:
    driver: bridge
```

### 6.8 Kubernetes 部署配置

**deployment.yaml** (Transaction Service):

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: transaction-service
  namespace: finpay
  labels:
    app: transaction-service
    version: v1
spec:
  replicas: 3
  selector:
    matchLabels:
      app: transaction-service
  template:
    metadata:
      labels:
        app: transaction-service
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: "8083"
    spec:
      containers:
      - name: transaction-service
        image: finpay/transaction-service:1.0.0
        ports:
        - containerPort: 8083
          name: http
          protocol: TCP

        # 环境变量
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "k8s"
        - name: SPRING_DATASOURCE_URL
          valueFrom:
            configMapKeyRef:
              name: finpay-config
              key: database.url
        - name: SPRING_DATASOURCE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: finpay-secrets
              key: database.password

        # 资源限制
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"

        # 存活探针 (Liveness Probe)
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8083
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3

        # 就绪探针 (Readiness Probe)
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8083
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3

        # 启动探针 (Startup Probe)
        startupProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8083
          initialDelaySeconds: 0
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 30

---
apiVersion: v1
kind: Service
metadata:
  name: transaction-service
  namespace: finpay
  labels:
    app: transaction-service
spec:
  type: ClusterIP
  ports:
  - port: 8083
    targetPort: 8083
    protocol: TCP
    name: http
  selector:
    app: transaction-service

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: transaction-service-hpa
  namespace: finpay
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: transaction-service
  minReplicas: 2
  maxReplicas: 10
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

**ServiceMonitor** (Prometheus Operator):

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: transaction-service
  namespace: finpay
  labels:
    app: transaction-service
spec:
  selector:
    matchLabels:
      app: transaction-service
  endpoints:
  - port: http
    path: /actuator/prometheus
    interval: 30s
    scrapeTimeout: 10s
```

---

## 七、最佳实践

### 7.1 健康检查最佳实践

**1. 分层健康检查**

```java
// Liveness: 应用是否活着（是否需要重启）
@Component
public class LivenessHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // 只检查应用本身
        // 不检查外部依赖
        return Health.up().build();
    }
}

// Readiness: 应用是否就绪（是否可以接收流量）
@Component
public class ReadinessHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // 检查所有依赖
        // 数据库、缓存、下游服务等
        if (!database.isHealthy() || !kafka.isHealthy()) {
            return Health.down().build();
        }
        return Health.up().build();
    }
}
```

**2. 超时控制**

```java
@Component
public class DatabaseHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        try {
            // 设置合理的超时时间
            CompletableFuture<Boolean> future = CompletableFuture.supplyAsync(() -> {
                return checkDatabase();
            });

            boolean isHealthy = future.get(3, TimeUnit.SECONDS);

            return isHealthy ? Health.up().build() : Health.down().build();

        } catch (TimeoutException e) {
            return Health.down()
                .withDetail("error", "Health check timeout")
                .build();
        }
    }
}
```

**3. 缓存健康状态**

```java
@Component
public class CachedHealthIndicator implements HealthIndicator {

    private volatile Health cachedHealth = Health.up().build();
    private volatile long lastCheck = 0;
    private static final long CACHE_DURATION = 30_000; // 30秒

    @Override
    public Health health() {
        long now = System.currentTimeMillis();

        if (now - lastCheck > CACHE_DURATION) {
            cachedHealth = performHealthCheck();
            lastCheck = now;
        }

        return cachedHealth;
    }
}
```

### 7.2 指标命名规范

**遵循 Prometheus 命名约定：**

```java
// ✅ 好的命名
transactions_created_total          // 计数器加 _total 后缀
transactions_processing_seconds     // 时间单位加 _seconds
jvm_memory_used_bytes              // 内存单位加 _bytes
http_requests_in_flight            // 当前值无后缀

// ❌ 不好的命名
transactionCount                    // 使用下划线而非驼峰
transaction_time_ms                 // 应该用标准单位 seconds
getTotalTransactions                // 不要用动词前缀
```

**标签使用：**

```java
// ✅ 好的标签
Counter.builder("http_requests_total")
    .tag("method", "GET")
    .tag("status", "200")
    .tag("uri", "/api/transactions")
    .register(registry);

// ❌ 避免高基数标签
Counter.builder("http_requests_total")
    .tag("user_id", userId)        // ❌ 用户ID基数太高
    .tag("transaction_id", txId)   // ❌ 交易ID基数太高
    .register(registry);
```

### 7.3 分布式追踪最佳实践

**1. 添加自定义标签**

```java
@Service
public class TransactionService {

    private final Tracer tracer;

    public TransactionDto createTransaction(TransactionRequestDto request) {
        Span span = tracer.currentSpan();

        if (span != null) {
            // 添加业务标签
            span.tag("account.id", request.getAccountId().toString());
            span.tag("transaction.type", request.getType());
            span.tag("transaction.amount", request.getAmount().toString());
        }

        // 业务逻辑
        return processTransaction(request);
    }
}
```

**2. 手动创建 Span**

```java
@Service
public class TransactionService {

    private final Tracer tracer;

    public void processTransaction(Transaction tx) {
        // 创建自定义 span
        Span span = tracer.nextSpan().name("fraud-check").start();

        try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
            // 执行业务逻辑
            fraudService.check(tx);

            span.tag("fraud.score", calculateScore(tx).toString());
            span.event("fraud-check-completed");

        } catch (Exception e) {
            span.error(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

**3. 日志关联**

```yaml
# application.yml
logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

```java
// 在代码中使用
log.info("Processing transaction");
// 输出: INFO [transaction-service,abc123,span001] Processing transaction
```

### 7.4 监控告警阈值

**推荐阈值：**

| 指标 | 警告 | 严重 |
|------|------|------|
| JVM Heap 使用率 | 70% | 90% |
| HTTP 错误率 | 1% | 5% |
| 响应时间 P95 | 500ms | 1000ms |
| 数据库连接池 | 70% | 90% |
| CPU 使用率 | 70% | 90% |
| 磁盘使用率 | 80% | 95% |

**告警规则示例：**

```yaml
groups:
  - name: transaction-service
    rules:
      # SLO: 99% 可用性
      - alert: ServiceAvailabilitySLO
        expr: |
          (sum(rate(http_server_requests_seconds_count{status!~"5.."}[5m]))
          /
          sum(rate(http_server_requests_seconds_count[5m]))) < 0.99
        for: 5m
        labels:
          severity: critical
          slo: availability
        annotations:
          summary: "Service availability below SLO"
          description: "Only {{ $value | humanizePercentage }} requests successful"

      # SLO: P95 响应时间 < 500ms
      - alert: LatencySLO
        expr: |
          histogram_quantile(0.95,
            rate(http_server_requests_seconds_bucket[5m])) > 0.5
        for: 5m
        labels:
          severity: warning
          slo: latency
        annotations:
          summary: "P95 latency above SLO"
          description: "P95 latency is {{ $value }}s"
```

### 7.5 性能优化建议

**1. 异步健康检查**

```java
@Configuration
public class HealthConfig {

    @Bean
    public HealthContributorRegistry healthContributorRegistry() {
        // 并行执行健康检查
        return new DefaultHealthContributorRegistry();
    }
}
```

**2. 指标采样优化**

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        # 只为关键端点启用百分位数
        http.server.requests: false
      slo:
        # 定义 SLO 边界
        http.server.requests: 50ms,100ms,200ms,500ms,1s
```

**3. 端点缓存**

```yaml
management:
  endpoint:
    health:
      # 缓存健康状态 10 秒
      cache:
        time-to-live: 10s
```

---

## 总结

### 关键要点

1. **Actuator 是生产级监控的基础**
   - 提供开箱即用的健康检查和指标收集
   - 与 Prometheus、Grafana 无缝集成
   - 支持 Kubernetes 探针

2. **安全性至关重要**
   - 生产环境必须启用认证
   - 最小化暴露的端点
   - 使用 HTTPS 传输敏感数据

3. **自定义指标增强可观测性**
   - 添加业务相关的指标
   - 遵循命名规范
   - 避免高基数标签

4. **分布式追踪提升调试效率**
   - 追踪跨服务请求链路
   - 日志中包含追踪ID
   - 生产环境降低采样率

5. **持续监控和告警**
   - 定义 SLO 和告警规则
   - 创建可视化仪表板
   - 定期审查和优化

### FinPay 项目改进路线图

**Phase 1 (1-2 周)：基础完善**
- ✅ 为所有服务添加 Actuator 配置
- ✅ 实现基础健康检查
- ✅ 配置 Prometheus 抓取所有服务

**Phase 2 (2-3 周)：增强功能**
- ✅ 实现自定义健康检查
- ✅ 添加业务指标
- ✅ 配置 Grafana 仪表板

**Phase 3 (3-4 周)：安全加固**
- ✅ 添加 Actuator 端点认证
- ✅ 配置告警规则
- ✅ 调整采样率

**Phase 4 (4-6 周)：容器化部署**
- ✅ 创建 Dockerfile
- ✅ 编写 K8s 部署清单
- ✅ 配置自动扩缩容

### 参考资源

- [Spring Boot Actuator 官方文档](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer 文档](https://micrometer.io/docs)
- [Prometheus 最佳实践](https://prometheus.io/docs/practices/)
- [Grafana 仪表板](https://grafana.com/grafana/dashboards/)
- [Zipkin 文档](https://zipkin.io/)

---

**文档版本：** 1.0
**最后更新：** 2025-01-20
**作者：** FinPay Team
