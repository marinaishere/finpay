# 从0到1实现 Spring Cloud Gateway API网关完整教程

## 目录

1. [第一步：项目初始化](#第一步项目初始化)
2. [第二步：添加Maven依赖](#第二步添加maven依赖)
3. [第三步：创建主应用类](#第三步创建主应用类)
4. [第四步：基础配置文件](#第四步基础配置文件)
5. [第五步：实现基础路由](#第五步实现基础路由)
6. [第六步：添加熔断器 (Circuit Breaker)](#第六步添加熔断器-circuit-breaker)
7. [第七步：添加限流器 (Rate Limiter)](#第七步添加限流器-rate-limiter)
8. [第八步：配置CORS跨域支持](#第八步配置cors跨域支持)
9. [第九步：集成Swagger API文档](#第九步集成swagger-api文档)
10. [第十步：测试和运行](#第十步测试和运行)

---

## 第一步：项目初始化

### 1.1 创建Maven项目结构

```bash
api-gateway/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── finpay/
        │           └── gateway/
        │               ├── ApiGatewayApplication.java
        │               ├── config/
        │               └── controller/
        └── resources/
            └── application.yml
```

### 1.2 为什么需要API网关？

在微服务架构中，API网关的作用：

```
客户端
  │
  ├─ 没有网关：需要知道5个服务的地址
  │   ├─ http://auth-service:8081/login
  │   ├─ http://account-service:8082/accounts
  │   ├─ http://transaction-service:8083/transactions
  │   ├─ http://notification-service:8084/notifications
  │   └─ http://fraud-service:8085/frauds
  │
  └─ 有网关：只需要一个地址
      └─ http://api-gateway:8080/
          ├─ /auth-services/** → auth-service
          ├─ /accounts/** → account-service
          ├─ /transactions/** → transaction-service
          ├─ /notifications/** → notification-service
          └─ /frauds/** → fraud-service
```

**API网关提供的核心功能：**
- 统一入口点
- 路由转发
- 负载均衡
- 认证授权
- 限流熔断
- 日志监控

---

## 第二步：添加Maven依赖

### 2.1 创建 `pom.xml`

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.0</version>
    </parent>

    <groupId>com.finpay</groupId>
    <artifactId>api-gateway</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <name>API Gateway</name>
    <description>Gateway for routing FinPay microservices</description>

    <properties>
        <java.version>17</java.version>
        <spring-cloud.version>2023.0.0</spring-cloud.version>
    </properties>

    <dependencies>
        <!-- 核心依赖：Spring Cloud Gateway -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-gateway</artifactId>
        </dependency>

        <!-- WebFlux：响应式Web框架（Gateway的基础） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-webflux</artifactId>
        </dependency>

        <!-- Actuator：健康检查和监控 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- 熔断器：Resilience4j -->
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-starter-circuitbreaker-reactor-resilience4j</artifactId>
        </dependency>

        <!-- 限流器：Redis -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
        </dependency>

        <!-- Swagger文档聚合 -->
        <dependency>
            <groupId>org.springdoc</groupId>
            <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
            <version>2.2.0</version>
        </dependency>
    </dependencies>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>org.springframework.cloud</groupId>
                <artifactId>spring-cloud-dependencies</artifactId>
                <version>${spring-cloud.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2.2 依赖说明

| 依赖 | 作用 | 是否必需 |
|------|------|----------|
| `spring-cloud-starter-gateway` | 核心网关功能 | ✅ 必需 |
| `spring-boot-starter-webflux` | 响应式Web框架 | ✅ 必需 |
| `spring-boot-starter-actuator` | 健康检查 | 推荐 |
| `spring-cloud-starter-circuitbreaker-reactor-resilience4j` | 熔断器 | 可选 |
| `spring-boot-starter-data-redis-reactive` | 限流器 | 可选 |
| `springdoc-openapi-starter-webflux-ui` | API文档 | 可选 |

---

## 第三步：创建主应用类

### 3.1 创建 `ApiGatewayApplication.java`

```java
package com.finpay.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ApiGatewayApplication {
    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
```

### 3.2 说明

- `@SpringBootApplication`：这是一个Spring Boot应用
- 非常简洁，所有配置都通过配置文件和配置类完成

---

## 第四步：基础配置文件

### 4.1 创建 `application.yml`

```yaml
# 服务器配置
server:
  port: 8080  # 网关监听端口

# Spring应用配置
spring:
  application:
    name: gateway  # 应用名称
```

### 4.2 启动测试

```bash
# 编译项目
mvn clean install

# 启动网关
mvn spring-boot:run
```

访问 `http://localhost:8080`，如果看到错误页面（因为还没配置路由），说明网关已经启动成功。

---

## 第五步：实现基础路由

路由是网关的核心功能，将客户端请求转发到后端服务。

### 5.1 创建路由配置类 `GatewayRoutesConfig.java`

```java
package com.finpay.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // 路由1：认证服务
            .route("auth-service", r -> r
                .path("/auth-services/**")  // 匹配路径
                .uri("http://localhost:8081"))  // 转发到的目标服务

            // 路由2：账户服务
            .route("account-service", r -> r
                .path("/accounts/**")
                .uri("http://localhost:8082"))

            // 路由3：交易服务
            .route("transaction-service", r -> r
                .path("/transactions/**")
                .uri("http://localhost:8083"))

            // 路由4：通知服务
            .route("notification-service", r -> r
                .path("/notifications/**")
                .uri("http://localhost:8084"))

            // 路由5：风控服务
            .route("fraud-service", r -> r
                .path("/frauds/**")
                .uri("http://localhost:8085"))

            .build();
    }
}
```

### 5.2 路由工作原理

```
客户端请求：http://localhost:8080/accounts/123

网关处理流程：
1. 接收请求：/accounts/123
2. 匹配路由：找到 "account-service" 路由（匹配 /accounts/**）
3. 转发请求：http://localhost:8082/accounts/123
4. 返回响应：将后端响应返回给客户端
```

### 5.3 路由匹配规则

| 模式 | 说明 | 示例 |
|------|------|------|
| `/path/**` | 匹配该路径下所有子路径 | `/accounts/**` 匹配 `/accounts/123`, `/accounts/user/info` |
| `/path/*` | 匹配单层子路径 | `/accounts/*` 匹配 `/accounts/123`，不匹配 `/accounts/user/info` |
| `/path` | 精确匹配 | `/accounts` 只匹配 `/accounts` |

### 5.4 测试路由

启动一个后端服务（例如账户服务在8082端口），然后：

```bash
# 直接访问后端服务
curl http://localhost:8082/accounts

# 通过网关访问（应该返回相同结果）
curl http://localhost:8080/accounts
```

---

## 第六步：添加熔断器 (Circuit Breaker)

### 6.1 为什么需要熔断器？

想象这个场景：
```
正常情况：
客户端 → 网关 → 交易服务 → 数据库
         (200ms)  (响应成功)

交易服务宕机：
客户端 → 网关 → 交易服务 (超时5秒) → 返回错误
         ↓
      100个请求都在等待，线程被占用

有熔断器：
客户端 → 网关 → [熔断器检测到故障，立即返回降级响应]
         ↓
      保护系统，避免资源浪费
```

### 6.2 添加熔断器配置到 `application.yml`

```yaml
# 熔断器配置
resilience4j:
  circuitbreaker:
    instances:
      transactionCB:  # 熔断器名称
        failureRateThreshold: 50  # 失败率达到50%时开启熔断
        waitDurationInOpenState: 10s  # 熔断开启后等待10秒再尝试
        slidingWindowSize: 10  # 统计最近10个请求

  timelimiter:
    instances:
      transactionCB:
        timeoutDuration: 5s  # 请求超时时间5秒
```

### 6.3 创建降级控制器 `FallbackController.java`

```java
package com.finpay.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FallbackController {

    @RequestMapping("/fallback/transactions")
    public ResponseEntity<String> transactionFallback() {
        return ResponseEntity.ok(
            "Transaction Service is currently unavailable. Please try again later."
        );
    }
}
```

### 6.4 在路由中应用熔断器

修改 `GatewayRoutesConfig.java`：

```java
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // ... 其他路由 ...

            // 交易服务 - 添加熔断器
            .route("transaction-service", r -> r
                .path("/transactions/**")
                .filters(f -> f.circuitBreaker(c -> c
                    .setName("transactionCB")  // 使用上面配置的熔断器
                    .setFallbackUri("forward:/fallback/transactions")))  // 降级端点
                .uri("http://localhost:8083"))

            .build();
    }
}
```

### 6.5 熔断器状态机

```
┌─────────────┐
│   CLOSED    │  正常状态：请求正常通过
│  (关闭)     │  监控失败率
└──────┬──────┘
       │ 失败率 ≥ 50%
       ↓
┌─────────────┐
│    OPEN     │  熔断状态：直接返回降级响应
│   (开启)    │  不调用后端服务
└──────┬──────┘
       │ 等待10秒
       ↓
┌─────────────┐
│ HALF-OPEN   │  半开状态：尝试一个请求
│  (半开)     │  成功→关闭，失败→开启
└─────────────┘
```

### 6.6 测试熔断器

```bash
# 1. 关闭交易服务（模拟服务宕机）
# 停止 transaction-service

# 2. 多次请求（触发熔断）
for i in {1..15}; do
  curl http://localhost:8080/transactions/123
done

# 应该看到降级响应：
# "Transaction Service is currently unavailable. Please try again later."
```

---

## 第七步：添加限流器 (Rate Limiter)

### 7.1 为什么需要限流？

限流防止API被滥用：
```
没有限流：
恶意用户发送10000个请求/秒 → 服务器崩溃

有限流：
每个用户限制10个请求/秒 → 超出部分返回429错误
```

### 7.2 启动Redis

限流需要Redis存储请求计数：

```bash
# 使用Docker启动Redis
docker run -d -p 6379:6379 redis:7-alpine

# 或使用Homebrew安装
brew install redis
redis-server
```

### 7.3 添加Redis配置到 `application.yml`

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 7.4 创建限流配置类 `RateLimiterConfig.java`

```java
package com.finpay.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * 创建Redis限流器
     * 参数1：replenishRate = 1  每秒补充1个令牌
     * 参数2：burstCapacity = 10  令牌桶容量10个（允许突发10个请求）
     */
    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(1, 10);
    }

    /**
     * 定义限流的Key（如何识别用户）
     * 这里使用JWT Token来区分不同用户
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> {
            // 从请求头获取Authorization
            String authHeader = exchange.getRequest()
                .getHeaders()
                .getFirst("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                // 使用JWT token作为限流key（每个用户独立限流）
                return Mono.just(authHeader.substring(7));
            }

            // 匿名用户共享限流配额
            return Mono.just("anonymous");
        };
    }
}
```

### 7.5 在路由中应用限流器

修改 `GatewayRoutesConfig.java`：

```java
@Configuration
public class GatewayRoutesConfig {

    private final RedisRateLimiter redisRateLimiter;
    private final KeyResolver userKeyResolver;

    // 构造函数注入
    public GatewayRoutesConfig(RedisRateLimiter redisRateLimiter,
                               KeyResolver userKeyResolver) {
        this.redisRateLimiter = redisRateLimiter;
        this.userKeyResolver = userKeyResolver;
    }

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // ... 其他路由 ...

            // 风控服务 - 添加限流器
            .route("fraud-service", r -> r
                .path("/frauds/**")
                .filters(f -> f.requestRateLimiter(c -> {
                    c.setRateLimiter(redisRateLimiter);
                    c.setKeyResolver(userKeyResolver);
                }))
                .uri("http://localhost:8085"))

            .build();
    }
}
```

### 7.6 令牌桶算法原理

```
令牌桶 (Bucket)
┌─────────────────┐
│ ○ ○ ○ ○ ○      │  容量：10个令牌
│ ○ ○ ○ ○ ○      │  补充速率：1个/秒
└─────────────────┘

请求处理：
1. 请求到来 → 从桶中取1个令牌
2. 有令牌 → 放行请求
3. 没令牌 → 返回 429 Too Many Requests

示例：
- 用户一次发送10个请求 → 成功（用完10个令牌）
- 立即再发1个请求 → 失败（桶空了）
- 等待1秒 → 成功（补充了1个令牌）
```

### 7.7 测试限流

```bash
# 快速发送20个请求
for i in {1..20}; do
  curl -w "\nStatus: %{http_code}\n" http://localhost:8080/frauds/check
done

# 前10个应该成功（200）
# 后10个应该被限流（429 Too Many Requests）
```

---

## 第八步：配置CORS跨域支持

### 8.1 为什么需要CORS？

```
浏览器同源策略：
前端：http://localhost:5173
API：http://localhost:8080

直接请求会被浏览器阻止：
❌ Access to fetch at 'http://localhost:8080/accounts' from origin
   'http://localhost:5173' has been blocked by CORS policy

配置CORS后：
✅ 请求成功
```

### 8.2 在 `application.yml` 中配置CORS

```yaml
spring:
  cloud:
    gateway:
      globalcors:  # 全局CORS配置
        cors-configurations:
          '[/**]':  # 对所有路径生效
            allowedOrigins:  # 允许的来源
              - "http://localhost:5173"  # React开发服务器
              - "http://localhost:5174"  # Vue开发服务器
              - "http://localhost:8080"  # 网关自己
            allowedMethods:  # 允许的HTTP方法
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"  # 允许所有请求头
            allowCredentials: true  # 允许携带Cookie
```

### 8.3 CORS预检请求

浏览器会先发送OPTIONS请求（预检）：

```
客户端 → OPTIONS /accounts/123
         Headers:
           Origin: http://localhost:5173
           Access-Control-Request-Method: POST

网关 → 200 OK
       Headers:
         Access-Control-Allow-Origin: http://localhost:5173
         Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS

客户端 → 收到许可，发送真实的POST请求
```

---

## 第九步：集成Swagger API文档

### 9.1 为什么需要聚合API文档？

```
没有聚合：
- Auth Service文档：http://localhost:8081/swagger-ui.html
- Account Service文档：http://localhost:8082/swagger-ui.html
- Transaction Service文档：http://localhost:8083/swagger-ui.html
需要访问多个URL，很麻烦

有聚合：
- 统一入口：http://localhost:8080/swagger-ui.html
- 一个页面查看所有服务的API
```

### 9.2 配置Swagger路由

在 `GatewayRoutesConfig.java` 中添加：

```java
@Bean
public RouteLocator customRoutes(RouteLocatorBuilder builder) {
    return builder.routes()
        // ... 业务路由 ...

        // Swagger文档路由 - 聚合所有服务的API文档
        .route("auth-docs", r -> r
            .path("/v3/api-docs/auth")
            .filters(f -> f.rewritePath("/v3/api-docs/auth", "/v3/api-docs"))
            .uri("http://localhost:8081"))

        .route("account-docs", r -> r
            .path("/v3/api-docs/account")
            .filters(f -> f.rewritePath("/v3/api-docs/account", "/v3/api-docs"))
            .uri("http://localhost:8082"))

        .route("transaction-docs", r -> r
            .path("/v3/api-docs/transaction")
            .filters(f -> f.rewritePath("/v3/api-docs/transaction", "/v3/api-docs"))
            .uri("http://localhost:8083"))

        .route("notification-docs", r -> r
            .path("/v3/api-docs/notification")
            .filters(f -> f.rewritePath("/v3/api-docs/notification", "/v3/api-docs"))
            .uri("http://localhost:8084"))

        .route("fraud-docs", r -> r
            .path("/v3/api-docs/fraud")
            .filters(f -> f.rewritePath("/v3/api-docs/fraud", "/v3/api-docs"))
            .uri("http://localhost:8085"))

        .build();
}
```

### 9.3 配置Swagger UI

在 `application.yml` 中添加：

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html  # Swagger UI访问路径
    urls:  # 文档来源列表
      - url: /v3/api-docs/auth
        name: Auth Service
      - url: /v3/api-docs/account
        name: Account Service
      - url: /v3/api-docs/transaction
        name: Transaction Service
      - url: /v3/api-docs/notification
        name: Notification Service
      - url: /v3/api-docs/fraud
        name: Fraud Service
    urls-primary-name: Auth Service  # 默认选中的服务
    display-request-duration: true  # 显示请求耗时
    filter: true  # 启用过滤功能
```

### 9.4 访问文档

启动所有服务后，访问：
```
http://localhost:8080/swagger-ui.html
```

你会看到一个下拉菜单，可以切换查看不同服务的API文档。

---

## 第十步：测试和运行

### 10.1 完整的项目结构

```
api-gateway/
├── pom.xml
└── src/
    └── main/
        ├── java/
        │   └── com/
        │       └── finpay/
        │           └── gateway/
        │               ├── ApiGatewayApplication.java
        │               ├── config/
        │               │   ├── GatewayRoutesConfig.java
        │               │   └── RateLimiterConfig.java
        │               └── controller/
        │                   └── FallbackController.java
        └── resources/
            └── application.yml
```

### 10.2 启动顺序

```bash
# 1. 启动Redis（限流需要）
redis-server

# 2. 启动后端服务
cd auth-service && mvn spring-boot:run &
cd account-service && mvn spring-boot:run &
cd transaction-service && mvn spring-boot:run &
cd notification-service && mvn spring-boot:run &
cd fraud-service && mvn spring-boot:run &

# 3. 启动API网关
cd api-gateway && mvn spring-boot:run
```

### 10.3 测试场景

#### 场景1：基础路由测试

```bash
# 通过网关访问账户服务
curl http://localhost:8080/accounts

# 应该返回和直接访问账户服务相同的结果
curl http://localhost:8082/accounts
```

#### 场景2：熔断器测试

```bash
# 1. 停止交易服务
pkill -f transaction-service

# 2. 请求交易服务（应该返回降级响应）
curl http://localhost:8080/transactions/123

# 预期响应：
# "Transaction Service is currently unavailable. Please try again later."
```

#### 场景3：限流测试

```bash
# 快速发送20个请求
for i in {1..20}; do
  curl -w "\nHTTP Status: %{http_code}\n" \
       http://localhost:8080/frauds/check
  sleep 0.1
done

# 预期结果：
# - 前10个请求：200 OK
# - 后10个请求：429 Too Many Requests
```

#### 场景4：CORS测试

创建一个简单的HTML页面：

```html
<!DOCTYPE html>
<html>
<head>
    <title>CORS Test</title>
</head>
<body>
    <button onclick="testCors()">Test CORS</button>
    <script>
        async function testCors() {
            try {
                const response = await fetch('http://localhost:8080/accounts');
                const data = await response.json();
                console.log('Success:', data);
                alert('CORS is working!');
            } catch (error) {
                console.error('CORS Error:', error);
                alert('CORS failed: ' + error.message);
            }
        }
    </script>
</body>
</html>
```

用浏览器打开这个HTML文件，点击按钮测试CORS。

### 10.4 监控和日志

#### 查看网关日志

```yaml
# 在 application.yml 中添加
logging:
  level:
    org.springframework.cloud.gateway: DEBUG  # 网关详细日志
    reactor.netty: INFO  # Netty日志
```

#### 使用Actuator监控

```bash
# 查看网关健康状态
curl http://localhost:8080/actuator/health

# 查看所有路由
curl http://localhost:8080/actuator/gateway/routes

# 查看熔断器状态
curl http://localhost:8080/actuator/health/circuitBreakers
```

### 10.5 性能测试

使用Apache Bench进行压力测试：

```bash
# 安装Apache Bench
brew install httpd

# 测试网关性能（1000个请求，并发10）
ab -n 1000 -c 10 http://localhost:8080/accounts/

# 查看结果
# Requests per second: XXX [#/sec]
# Time per request: XX [ms]
```

---

## 总结：从0到1完成的功能

### 你已经实现了：

✅ **基础功能**
- 统一入口点
- 路由转发
- 多服务聚合

✅ **高级功能**
- 熔断器：防止服务雪崩
- 限流器：防止API滥用
- CORS支持：跨域访问
- API文档聚合：统一文档入口

✅ **企业级特性**
- 响应式架构：高并发支持
- 健康检查：Actuator监控
- 日志记录：DEBUG级别日志

### 完整配置文件清单

#### `pom.xml`
- Spring Cloud Gateway
- WebFlux
- Resilience4j
- Redis Reactive
- SpringDoc OpenAPI

#### `application.yml`
- 服务器端口：8080
- CORS配置
- Redis配置
- 熔断器配置
- Swagger配置
- 日志配置

#### Java类
- `ApiGatewayApplication`：主启动类
- `GatewayRoutesConfig`：路由配置
- `RateLimiterConfig`：限流配置
- `FallbackController`：降级处理

---

## 下一步优化方向

### 1. 服务发现（Eureka/Consul）

当前配置是硬编码服务地址：
```java
.uri("http://localhost:8083")  // 写死的地址
```

使用服务发现后：
```java
.uri("lb://transaction-service")  // 从注册中心获取地址
```

### 2. 认证和授权（OAuth2/JWT）

添加全局过滤器验证JWT：
```java
@Component
public class AuthFilter implements GlobalFilter {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // 验证JWT Token
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (!isValidToken(token)) {
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
```

### 3. 请求日志和链路追踪（Sleuth/Zipkin）

添加依赖：
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-sleuth</artifactId>
</dependency>
```

每个请求会自动生成TraceID：
```
2024-01-15 10:30:45.123 [gateway,a1b2c3d4,a1b2c3d4] INFO ...
```

### 4. 动态路由（从数据库加载路由配置）

```java
@Service
public class DynamicRouteService {
    @Autowired
    private RouteDefinitionWriter routeDefinitionWriter;

    public void addRoute(RouteDefinition definition) {
        routeDefinitionWriter.save(Mono.just(definition)).subscribe();
    }
}
```

### 5. 灰度发布（按比例路由到不同版本）

```java
.route("account-service-v1", r -> r
    .path("/accounts/**")
    .and()
    .weight("account-group", 90)  // 90%流量
    .uri("http://localhost:8082"))

.route("account-service-v2", r -> r
    .path("/accounts/**")
    .and()
    .weight("account-group", 10)  // 10%流量
    .uri("http://localhost:8092"))
```

---

## 常见问题FAQ

### Q1: 网关启动失败，提示端口被占用

```bash
# 查看占用8080端口的进程
lsof -i :8080

# 杀死进程
kill -9 <PID>
```

### Q2: Redis连接失败

```bash
# 检查Redis是否启动
redis-cli ping
# 应该返回 PONG

# 如果没启动
redis-server
```

### Q3: 熔断器不生效

确保：
1. 依赖已添加：`spring-cloud-starter-circuitbreaker-reactor-resilience4j`
2. 配置正确：`resilience4j.circuitbreaker.instances.transactionCB`
3. 路由中使用：`.setName("transactionCB")`
4. 触发足够失败请求（至少10个中5个失败）

### Q4: CORS仍然报错

检查：
1. `allowedOrigins` 是否包含前端地址
2. 是否使用了精确的URL（包括http/https和端口）
3. 前端是否携带了自定义Header（需要在`allowedHeaders`中配置）

### Q5: Swagger UI无法访问

确保：
1. 依赖已添加：`springdoc-openapi-starter-webflux-ui`
2. 后端服务的Swagger已配置
3. 路由配置正确（`/v3/api-docs/xxx`）
4. 访问的URL正确：`http://localhost:8080/swagger-ui.html`

---

## 参考资料

- [Spring Cloud Gateway官方文档](https://docs.spring.io/spring-cloud-gateway/docs/current/reference/html/)
- [Resilience4j官方文档](https://resilience4j.readme.io/)
- [Spring Cloud Gateway最佳实践](https://spring.io/blog/2022/08/26/spring-cloud-gateway-best-practices)
- [响应式编程指南](https://projectreactor.io/docs/core/release/reference/)

---

## 结语

恭喜你从0到1完成了一个企业级API网关的实现！

这个网关具备：
- 高性能的响应式架构
- 完善的容错机制（熔断、限流）
- 良好的可观测性（日志、监控）
- 开发友好的特性（Swagger聚合）

你现在可以：
1. 基于这个网关构建自己的微服务系统
2. 根据实际需求添加认证、服务发现等功能
3. 将网关部署到生产环境

Happy Coding! 🚀
