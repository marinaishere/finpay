# OpenFeign 在 FinPay 项目中的实践详解

> 完整讲解 FinPay 金融支付系统中 OpenFeign 的使用方式、配置和最佳实践

---

## 目录

- [一、OpenFeign 架构概览](#一openfeign-架构概览)
- [二、Feign 客户端详解](#二feign-客户端详解)
- [三、FeignConfig 配置详解](#三feignconfig-配置详解)
- [四、完整的交易流程](#四完整的交易流程)
- [五、异常处理机制](#五异常处理机制)
- [六、API Gateway 层的保护机制](#六api-gateway-层的保护机制)
- [七、数据传输对象（DTOs）](#七数据传输对象dtos)
- [八、分布式追踪集成](#八分布式追踪集成)
- [九、OpenFeign 最佳实践](#九openfeign-最佳实践)
- [十、改进建议](#十改进建议)

---

## 一、OpenFeign 架构概览

### 1.1 服务调用关系图

```
┌─────────────────────────────────────────────────────┐
│            API Gateway (8080)                        │
│            - 路由转发                                 │
│            - 断路器保护                               │
│            - 限流                                     │
└────────────────────┬────────────────────────────────┘
                     │
         ┌───────────┼───────────────────┐
         │           │                   │
    ┌────▼────┐ ┌───▼──────┐      ┌────▼─────┐
    │ Auth    │ │ Account  │      │  Fraud   │
    │ Service │ │ Service  │      │ Service  │
    │  8081   │ │  8082    │      │  8085    │
    └─────────┘ └────▲─────┘      └────▲─────┘
                     │                 │
                     │   Feign调用     │
              ┌──────┴─────────────────┴──────┐
              │   Transaction Service (8083)   │
              │                                │
              │   Feign Clients:               │
              │   ├─ AccountClient ────────────┤
              │   ├─ FraudClient ──────────────┤
              │   └─ NotificationClient ───────┤
              └────────────────┬───────────────┘
                               │
                        ┌──────▼──────────┐
                        │  Notification   │
                        │   Service       │
                        │    8084         │
                        └─────────────────┘
```

### 1.2 核心特点

| 特性 | 说明 |
|-----|------|
| **单一消费者** | 仅 `Transaction Service` 使用 Feign 客户端 |
| **3 个 Feign 客户端** | AccountClient、FraudClient、NotificationClient |
| **统一配置** | 通过 `FeignConfig` 实现 JWT 令牌转发 |
| **分布式追踪** | 集成 Micrometer + Zipkin |
| **异常处理** | 全局异常处理器 + 业务异常 |

### 1.3 服务端口映射

| 服务名称 | 端口 | 说明 |
|---------|-----|------|
| API Gateway | 8080 | 统一网关入口 |
| Auth Service | 8081 | 认证授权服务 |
| Account Service | 8082 | 账户管理服务 |
| Transaction Service | 8083 | 交易处理服务（Feign 消费者） |
| Notification Service | 8084 | 通知服务 |
| Fraud Service | 8085 | 欺诈检测服务 |
| Zipkin | 9411 | 分布式追踪 |

---

## 二、Feign 客户端详解

### 2.1 FraudClient - 欺诈检测客户端

**文件位置**: `transaction-service/src/main/java/com/finpay/transactions/clients/FraudClient.java`

```java
package com.finpay.transactions.clients;

import com.finpay.common.dto.frauds.FraudCheckRequest;
import com.finpay.common.dto.frauds.FraudCheckResponse;
import com.finpay.transactions.configs.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for communicating with the Fraud Detection Service.
 *
 * 用于与欺诈检测服务通信的 Feign 客户端
 *
 * 功能:
 * - 检查交易是否存在欺诈风险
 * - 分析异常交易模式
 * - 识别可疑账户行为
 * - 评估高风险转账
 */
@FeignClient(
    name = "fraud-service",                    // 服务名称（用于服务发现）
    url = "http://localhost:8085/frauds",      // 服务基础路径
    configuration = FeignConfig.class          // 自定义配置（JWT 转发）
)
public interface FraudClient {

    /**
     * 执行欺诈检查
     *
     * @param request 欺诈检查请求（包含账户ID、金额等）
     * @return FraudCheckResponse 检查结果（是否欺诈、风险评分、原因）
     */
    @PostMapping("/check")
    FraudCheckResponse checkFraud(@RequestBody FraudCheckRequest request);
    // 实际请求: POST http://localhost:8085/frauds/check
}
```

#### 注解详解

| 注解属性 | 值 | 说明 |
|---------|---|------|
| `name` | `"fraud-service"` | 微服务名称，用于日志、监控标识和服务发现 |
| `url` | `"http://localhost:8085/frauds"` | 服务的固定 URL（开发环境），生产环境应使用服务发现 |
| `configuration` | `FeignConfig.class` | 自定义配置类，包含 JWT 令牌转发拦截器 |

#### HTTP 映射

```java
@PostMapping("/check")  →  POST http://localhost:8085/frauds/check
```

#### 使用示例

```java
@Service
public class TransactionService {

    @Autowired
    private FraudClient fraudClient;

    public void processTransaction(TransferRequest request) {
        // 调用欺诈检测
        FraudCheckResponse response = fraudClient.checkFraud(
            new FraudCheckRequest(
                request.getFromAccountId(),
                request.getToAccountId(),
                request.getAmount()
            )
        );

        // 根据结果决定是否继续处理
        if (response.isFraudulent()) {
            throw new BusinessException(
                "Transaction blocked: " + response.getReason()
            );
        }
    }
}
```

#### 当前状态

> **注意**: FraudClient 目前已注入到 `TransactionService` 中，但**尚未在实际业务流程中使用**。这是一个预留的功能点，可在未来集成欺诈检测逻辑。

---

### 2.2 AccountClient - 账户服务客户端

**文件位置**: `transaction-service/src/main/java/com/finpay/transactions/clients/AccountClient.java`

```java
package com.finpay.transactions.clients;

import com.finpay.common.dto.accounts.AccountDto;
import com.finpay.common.dto.accounts.CreditRequest;
import com.finpay.common.dto.accounts.DebitRequest;
import com.finpay.transactions.configs.FeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.UUID;

/**
 * Feign client for communicating with the Account Service.
 *
 * 用于与账户服务通信的 Feign 客户端
 *
 * 功能:
 * - 查询账户信息
 * - 账户扣款（debit）
 * - 账户入账（credit）
 */
@FeignClient(
    name = "account-service",
    url = "http://localhost:8082/accounts",
    configuration = FeignConfig.class
)
public interface AccountClient {

    /**
     * 账户扣款（出账）
     *
     * @param request 扣款请求（账户ID + 金额）
     * @return AccountDto 更新后的账户信息
     */
    @PostMapping("/debit")
    AccountDto debit(DebitRequest request);
    // 实际请求: POST http://localhost:8082/accounts/debit

    /**
     * 账户入账（存款）
     *
     * @param request 入账请求（账户ID + 金额）
     * @return AccountDto 更新后的账户信息
     */
    @PostMapping("/credit")
    AccountDto credit(CreditRequest request);
    // 实际请求: POST http://localhost:8082/accounts/credit

    /**
     * 查询账户详情
     *
     * @param id 账户UUID
     * @return AccountDto 账户信息
     */
    @GetMapping("/{id}")
    AccountDto getAccount(@PathVariable("id") UUID id);
    // 实际请求: GET http://localhost:8082/accounts/{uuid}
}
```

#### HTTP 映射

| 方法 | HTTP 映射 | 说明 |
|------|----------|------|
| `debit()` | `POST /accounts/debit` | 从账户扣款 |
| `credit()` | `POST /accounts/credit` | 向账户入账 |
| `getAccount(uuid)` | `GET /accounts/{uuid}` | 查询账户详情 |

#### 实际使用示例（来自 TransactionService）

```java
@Service
@Transactional
public class TransactionService {

    @Autowired
    private AccountClient accountClient;

    public TransactionResponse transfer(TransferRequest request) {

        // 1. 查询源账户信息
        AccountDto sourceAccount = accountClient.getAccount(
            request.getFromAccountId()
        );
        // → GET http://localhost:8082/accounts/{fromAccountId}
        // → Header: Authorization: Bearer <JWT>

        // 2. 从源账户扣款
        accountClient.debit(new DebitRequest(
            request.getFromAccountId(),
            request.getAmount()
        ));
        // → POST http://localhost:8082/accounts/debit
        // → Body: {"accountId": "...", "amount": 100.0}
        // → Header: Authorization: Bearer <JWT>

        // 3. 向目标账户入账
        accountClient.credit(new CreditRequest(
            request.getToAccountId(),
            request.getAmount()
        ));
        // → POST http://localhost:8082/accounts/credit
        // → Body: {"accountId": "...", "amount": 100.0}
        // → Header: Authorization: Bearer <JWT>

        return buildResponse();
    }
}
```

---

### 2.3 NotificationClient - 通知服务客户端

**文件位置**: `transaction-service/src/main/java/com/finpay/transactions/clients/NotificationClient.java`

```java
package com.finpay.transactions.clients;

import com.finpay.common.dto.notifications.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Feign client for communicating with the Notification Service.
 *
 * 用于与通知服务通信的 Feign 客户端
 *
 * 功能:
 * - 发送交易成功通知
 * - 发送交易失败通知
 * - 支持多渠道（EMAIL、SMS、PUSH）
 */
@FeignClient(
    name = "notification-service",
    url = "http://localhost:8084/notifications"
    // 注意: 未配置 FeignConfig，说明通知服务不需要 JWT 认证
)
public interface NotificationClient {

    /**
     * 发送通知
     *
     * @param request 通知请求（用户ID、消息内容、渠道）
     */
    @PostMapping
    void sendNotification(NotificationRequest request);
    // 实际请求: POST http://localhost:8084/notifications
    // 无返回值，表示异步通知，不关心返回结果
}
```

#### 特点

| 特点 | 说明 |
|-----|------|
| **无返回值** | 使用 `void`，表示异步通知，不阻塞主流程 |
| **无 JWT 认证** | 未配置 `FeignConfig`，通知服务可能是内部服务 |
| **Fire-and-Forget** | 发送即忘模式，不等待通知发送结果 |

#### 使用示例

```java
// 交易成功通知
notificationClient.sendNotification(
    NotificationRequest.builder()
        .userId(userEmail)                           // 用户邮箱
        .message("Transaction Completed Successfully") // 通知内容
        .channel("EMAIL")                            // 通知渠道
        .build()
);

// 交易失败通知
notificationClient.sendNotification(
    NotificationRequest.builder()
        .userId(userEmail)
        .message("Transaction Failed: Insufficient Balance")
        .channel("SMS")
        .build()
);
```

---

### 2.4 启用 Feign 客户端

**文件位置**: `transaction-service/src/main/java/com/finpay/transactions/TransactionServiceApplication.java`

```java
package com.finpay.transactions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients(basePackages = "com.finpay.transactions.clients")
//                  ↑
//                  └─ 指定扫描 Feign 客户端的包路径
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
```

#### @EnableFeignClients 注解说明

| 属性 | 值 | 说明 |
|-----|---|------|
| `basePackages` | `"com.finpay.transactions.clients"` | 指定扫描 Feign 接口的包路径 |
| 作用 | - | 启用 Feign 客户端，自动生成代理实现类 |

---

## 三、FeignConfig 配置详解

### 3.1 JWT 令牌转发拦截器

**文件位置**: `transaction-service/src/main/java/com/finpay/transactions/configs/FeignConfig.java`

```java
package com.finpay.transactions.configs;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Feign HTTP 客户端配置类
 *
 * 功能:
 * - 配置请求拦截器
 * - 转发 JWT 认证令牌到下游服务
 * - 保持服务间调用的用户认证上下文
 */
@Configuration
public class FeignConfig {

    /**
     * 创建请求拦截器，用于转发 JWT 认证令牌
     *
     * 工作原理:
     * 1. 从 Spring Security 上下文获取当前认证信息
     * 2. 检查认证对象是否是 JWT
     * 3. 提取 JWT Token 并添加到 Authorization 请求头
     *
     * @return RequestInterceptor 拦截器实例
     */
    @Bean
    public RequestInterceptor requestInterceptor() {
        return (RequestTemplate template) -> {
            // 1. 从 SecurityContextHolder 获取认证信息
            Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

            // 2. 检查认证对象是否为 JWT 类型
            if (authentication != null &&
                authentication.getPrincipal() instanceof Jwt jwt) {

                // 3. 提取 JWT Token 值并添加到请求头
                template.header(
                    "Authorization",
                    "Bearer " + jwt.getTokenValue()
                );
                // 此时下游服务会收到与上游相同的 JWT Token
            }
        };
    }
}
```

---

### 3.2 JWT 转发工作原理

#### 完整请求链路

```
┌─────────────────────────────────────────────────┐
│  1. 用户请求到达 Transaction Service             │
│     Header: Authorization: Bearer <JWT_TOKEN>   │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│  2. Spring Security 解析 JWT                     │
│     - 验证签名                                    │
│     - 提取用户信息 (username, roles, etc.)        │
│     - 存入 SecurityContextHolder                 │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│  3. TransactionService 调用 Feign 客户端        │
│     accountClient.debit(request)                │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│  4. FeignConfig 拦截器自动执行                   │
│     - SecurityContextHolder.getContext()        │
│     - 获取 JWT: jwt.getTokenValue()             │
│     - 添加到请求头: Authorization: Bearer ...   │
└────────────────────┬────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│  5. HTTP 请求发送到 Account Service              │
│     POST http://localhost:8082/accounts/debit   │
│     Header: Authorization: Bearer <JWT_TOKEN>   │
└─────────────────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────┐
│  6. Account Service 接收请求                     │
│     - 再次验证 JWT 签名                          │
│     - 提取用户信息进行权限校验                    │
│     - 处理业务逻辑                                │
└─────────────────────────────────────────────────┘
```

---

### 3.3 为什么需要 JWT 转发？

#### 场景分析

假设用户 Alice（userId=123）发起转账请求：

```
用户请求 → API Gateway → Transaction Service → Account Service
   ↓             ↓               ↓                    ↓
 Alice        验证 JWT      需要知道是谁         需要知道是谁
 的 JWT                     调用的服务           的账户操作
```

#### 如果不转发 JWT 会怎样？

❌ **问题 1: 无法识别用户身份**
```java
// Account Service 无法知道是哪个用户的请求
public void debit(DebitRequest request) {
    // 谁在调用这个方法？无从得知！
    // 无法验证用户是否有权限操作该账户
}
```

❌ **问题 2: 权限验证缺失**
```java
// 恶意用户可能通过 Transaction Service 间接操作任意账户
// Account Service 无法进行权限校验
if (!currentUser.ownsAccount(request.getAccountId())) {
    throw new UnauthorizedException(); // 但 currentUser 是谁？
}
```

❌ **问题 3: 审计日志不完整**
```java
// 审计日志无法记录真实用户
auditLog.record("Unknown user performed debit operation"); // ❌
```

#### 转发 JWT 后的好处

✅ **好处 1: 完整的用户上下文**
```java
// Account Service 可以从 JWT 获取用户信息
@PreAuthorize("@accountSecurity.canAccessAccount(#request.accountId)")
public void debit(DebitRequest request) {
    String userId = SecurityContextHolder
        .getContext()
        .getAuthentication()
        .getName(); // 可以获取到 Alice 的 userId
}
```

✅ **好处 2: 细粒度权限控制**
```java
// 可以验证用户是否有权限操作该账户
public void debit(DebitRequest request) {
    String currentUserId = getCurrentUserId();
    Account account = accountRepository.findById(request.getAccountId());

    if (!account.getOwnerId().equals(currentUserId)) {
        throw new ForbiddenException("You can only debit your own account");
    }
}
```

✅ **好处 3: 完整的审计追踪**
```java
// 审计日志可以记录真实用户
auditLog.record(
    "User Alice (userId=123) debited $100 from account XYZ"
); // ✅
```

---

### 3.4 Spring Security 上下文传递

#### SecurityContextHolder 工作原理

```java
// 线程本地存储（ThreadLocal）
public class SecurityContextHolder {
    private static ThreadLocal<SecurityContext> contextHolder =
        new ThreadLocal<>();

    public static SecurityContext getContext() {
        return contextHolder.get();
    }
}
```

#### 在 Feign 调用中的生命周期

```
┌──────────────────────────────────────────────┐
│  Thread-1: HTTP Request Handler Thread       │
│                                              │
│  1. SecurityContextHolder.set(context)      │
│     [包含 JWT: Alice's Token]                │
│                                              │
│  2. TransactionService.transfer()           │
│                                              │
│  3. accountClient.debit()                   │
│     └─> FeignConfig.requestInterceptor()   │
│         └─> SecurityContextHolder.get()    │ ✅ 可以获取
│             └─> jwt.getTokenValue()        │
│                                              │
│  4. HTTP 请求发送（携带 JWT）                │
└──────────────────────────────────────────────┘
```

---

## 四、完整的交易流程

### 4.1 TransactionService 依赖注入

**文件位置**: `transaction-service/src/main/java/com/finpay/transactions/services/TransactionService.java`

```java
package com.finpay.transactions.services;

import com.finpay.transactions.clients.AccountClient;
import com.finpay.transactions.clients.FraudClient;
import com.finpay.transactions.clients.NotificationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor  // Lombok 自动生成构造器注入
public class TransactionService {

    // 数据库操作
    private final TransactionRepository repository;

    // Feign 客户端 - 账户服务
    private final AccountClient accountClient;

    // Feign 客户端 - 通知服务
    private final NotificationClient notificationClient;

    // Feign 客户端 - 欺诈检测（已注入但未使用）
    private final FraudClient fraudClient;

    // Kafka 生产者
    private final TransactionProducer transactionProducer;
}
```

---

### 4.2 转账流程详细代码

```java
/**
 * 执行转账操作
 *
 * @param idempotencyKey 幂等性键（防止重复提交）
 * @param request 转账请求（源账户、目标账户、金额）
 * @return TransactionResponse 交易结果
 */
@Transactional
public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {

    // ═══════════════════════════════════════════════════
    // 步骤 1: 幂等性检查（防止重复提交）
    // ═══════════════════════════════════════════════════
    Optional<Transaction> existing = repository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        // 如果已处理过，直接返回之前的结果
        return toResponse(existing.get());
    }

    // ═══════════════════════════════════════════════════
    // 步骤 2: 🔵 Feign 调用 - 获取源账户信息
    // ═══════════════════════════════════════════════════
    AccountDto accDto = accountClient.getAccount(request.getFromAccountId());
    //              ↑
    //              └─ HTTP 请求详情:
    //                 GET http://localhost:8082/accounts/{id}
    //                 Header: Authorization: Bearer <JWT>
    //
    //                 用途:
    //                 - 验证账户是否存在
    //                 - 获取账户所有者邮箱（用于发送通知）
    //                 - 检查账户状态

    // ═══════════════════════════════════════════════════
    // 步骤 3: 创建交易记录（PENDING 状态）
    // ═══════════════════════════════════════════════════
    Transaction tx = Transaction.builder()
        .fromAccountId(request.getFromAccountId())
        .toAccountId(request.getToAccountId())
        .amount(request.getAmount())
        .status(Transaction.Status.PENDING)      // 初始状态：待处理
        .idempotencyKey(idempotencyKey)
        .build();

    // ═══════════════════════════════════════════════════
    // 步骤 4: 发送 Kafka 事件（异步通知其他服务）
    // ═══════════════════════════════════════════════════
    transactionProducer.sendTransaction(new TransactionCreatedEvent(
        tx.getId(),
        tx.getFromAccountId(),
        tx.getToAccountId(),
        tx.getAmount(),
        tx.getStatus()
    ));
    // 其他微服务（如数据分析服务、风控服务）可以订阅此事件

    try {
        // ═══════════════════════════════════════════════════
        // 步骤 5: 🔵 Feign 调用 - 源账户扣款
        // ═══════════════════════════════════════════════════
        accountClient.debit(new DebitRequest(
            tx.getFromAccountId(),
            tx.getAmount()
        ));
        //    ↑
        //    └─ HTTP 请求详情:
        //       POST http://localhost:8082/accounts/debit
        //       Header: Authorization: Bearer <JWT>
        //       Body: {
        //         "accountId": "123e4567-e89b-12d3-a456-426614174000",
        //         "amount": 100.0
        //       }
        //
        //       Account Service 会:
        //       1. 验证 JWT 有效性
        //       2. 检查用户是否有权限操作该账户
        //       3. 检查余额是否充足
        //       4. 执行扣款操作
        //       5. 返回更新后的账户信息

        // ═══════════════════════════════════════════════════
        // 步骤 6: 🔵 Feign 调用 - 目标账户入账
        // ═══════════════════════════════════════════════════
        accountClient.credit(new CreditRequest(
            tx.getToAccountId(),
            tx.getAmount()
        ));
        //    ↑
        //    └─ HTTP 请求详情:
        //       POST http://localhost:8082/accounts/credit
        //       Header: Authorization: Bearer <JWT>
        //       Body: {
        //         "accountId": "987fcdeb-51a2-43f7-b890-123456789abc",
        //         "amount": 100.0
        //       }
        //
        //       Account Service 会:
        //       1. 验证目标账户存在
        //       2. 执行入账操作
        //       3. 返回更新后的账户信息

        // ═══════════════════════════════════════════════════
        // 步骤 7: 交易成功，更新状态
        // ═══════════════════════════════════════════════════
        tx.setStatus(Transaction.Status.COMPLETED);

        // ═══════════════════════════════════════════════════
        // 步骤 8: 🔵 Feign 调用 - 发送成功通知
        // ═══════════════════════════════════════════════════
        notificationClient.sendNotification(
            NotificationRequest.builder()
                .userId(accDto.getOwnerEmail())           // 发送给账户所有者
                .message("Transaction Completed Successfully")
                .channel("EMAIL")                         // 通过邮件通知
                .build()
        );
        //    ↑
        //    └─ HTTP 请求详情:
        //       POST http://localhost:8084/notifications
        //       Body: {
        //         "userId": "alice@example.com",
        //         "message": "Transaction Completed Successfully",
        //         "channel": "EMAIL"
        //       }
        //
        //       注意: 此请求不携带 JWT（NotificationClient 未配置 FeignConfig）

    } catch (Exception e) {
        // ═══════════════════════════════════════════════════
        // 步骤 9: 异常处理
        // ═══════════════════════════════════════════════════

        // 可能的异常类型:
        // - FeignException.BadRequest (400): 请求参数错误
        // - FeignException.Forbidden (403): 权限不足（JWT 验证失败）
        // - FeignException.NotFound (404): 账户不存在
        // - FeignException.InternalServerError (500): 账户服务内部错误
        // - RetryableException: 网络超时

        tx.setStatus(Transaction.Status.FAILED);

        // 发送失败通知
        notificationClient.sendNotification(
            NotificationRequest.builder()
                .userId(accDto.getOwnerEmail())
                .message("Transaction Failed: " + e.getMessage())
                .channel("EMAIL")
                .build()
        );

        // 注意: 这里没有手动回滚账户操作
        // 原因: @Transactional 只管理本地数据库事务
        // 跨服务的分布式事务需要其他方案（Saga、2PC 等）
    }

    // ═══════════════════════════════════════════════════
    // 步骤 10: 持久化交易状态并返回
    // ═══════════════════════════════════════════════════
    Transaction saved = repository.save(tx);
    return toResponse(saved);
}
```

---

### 4.3 调用时序图

```
User          Gateway      Transaction-Svc    Account-Svc    Notification-Svc
 │                │                │               │                │
 ├─ POST /api/transactions/transfer ──────────────>│               │
 │    Header: Authorization: Bearer <JWT>          │               │
 │                │                │               │                │
 │                │                ├─ 1. 幂等性检查 │                │
 │                │                ├─ findByIdempotencyKey()        │
 │                │                │               │                │
 │                │                ├─ 2. 获取账户信息 ──────────────>│
 │                │                │   GET /accounts/{id}           │
 │                │                │   [JWT forwarded]              │
 │                │                │<──────AccountDto───────────────┤
 │                │                │               │                │
 │                │                ├─ 3. 创建交易记录 │               │
 │                │                ├─ save(tx)     │                │
 │                │                │               │                │
 │                │                ├─ 4. 发送 Kafka 事件 │           │
 │                │                │               │                │
 │                │                ├─ 5. 源账户扣款 ────────────────>│
 │                │                │   POST /accounts/debit         │
 │                │                │   [JWT forwarded]              │
 │                │                │<──────200 OK────────────────────┤
 │                │                │               │                │
 │                │                ├─ 6. 目标账户入账 ──────────────>│
 │                │                │   POST /accounts/credit        │
 │                │                │   [JWT forwarded]              │
 │                │                │<──────200 OK────────────────────┤
 │                │                │               │                │
 │                │                ├─ 7. 更新状态 COMPLETED │        │
 │                │                ├─ save(tx)     │                │
 │                │                │               │                │
 │                │                ├─ 8. 发送成功通知 ──────────────────────>│
 │                │                │   POST /notifications          │
 │                │                │<──────200 OK─────────────────────────────┤
 │                │                │               │                │
 │<──────TransactionResponse───────┤               │                │
 │                │                │               │                │
```

---

### 4.4 异常场景处理

#### 场景 1: 余额不足

```java
try {
    accountClient.debit(...);  // 抛出 FeignException.BadRequest
} catch (FeignException.BadRequest e) {
    // Account Service 返回: {"error": "Insufficient balance"}
    tx.setStatus(Transaction.Status.FAILED);
    notificationClient.sendNotification(...);
}
```

#### 场景 2: JWT 过期

```java
try {
    accountClient.debit(...);  // 抛出 FeignException.Unauthorized
} catch (FeignException.Unauthorized e) {
    // Account Service 返回: 401 Unauthorized
    // JWT 已过期或无效
    throw new AuthenticationException("Session expired");
}
```

#### 场景 3: 网络超时

```java
try {
    accountClient.debit(...);  // 抛出 RetryableException
} catch (RetryableException e) {
    // 网络超时或连接失败
    tx.setStatus(Transaction.Status.PENDING);  // 保持 PENDING 状态
    // 可以通过定时任务重试
}
```

---

## 五、异常处理机制

### 5.1 Feign 异常类型

OpenFeign 根据 HTTP 状态码抛出不同的异常：

| HTTP 状态码 | 异常类型 | 触发条件 | 处理建议 |
|-----------|---------|---------|---------|
| 400 | `FeignException.BadRequest` | 请求参数错误、业务规则违反 | 记录日志，返回错误给用户 |
| 401 | `FeignException.Unauthorized` | JWT 无效或过期 | 要求用户重新登录 |
| 403 | `FeignException.Forbidden` | 权限不足 | 返回权限错误 |
| 404 | `FeignException.NotFound` | 资源不存在（如账户不存在） | 返回资源不存在错误 |
| 500 | `FeignException.InternalServerError` | 下游服务内部错误 | 重试或降级处理 |
| - | `RetryableException` | 网络超时、连接失败 | 自动重试 |

---

### 5.2 Feign 异常示例

```java
// 400 Bad Request - 余额不足
try {
    accountClient.debit(new DebitRequest(accountId, 10000.0));
} catch (FeignException.BadRequest e) {
    // 解析响应体
    String responseBody = e.contentUTF8();
    // {"error": "Insufficient balance", "balance": 500.0}

    log.error("Debit failed: {}", responseBody);
    throw new BusinessException("Insufficient balance");
}

// 401 Unauthorized - JWT 过期
try {
    accountClient.getAccount(accountId);
} catch (FeignException.Unauthorized e) {
    log.error("JWT expired or invalid");
    throw new AuthenticationException("Please login again");
}

// 404 Not Found - 账户不存在
try {
    accountClient.getAccount(UUID.randomUUID());
} catch (FeignException.NotFound e) {
    log.error("Account not found");
    throw new ResourceNotFoundException("Account does not exist");
}

// 500 Internal Server Error - 下游服务错误
try {
    accountClient.debit(request);
} catch (FeignException.InternalServerError e) {
    log.error("Account service error: {}", e.getMessage());
    // 可以触发重试机制或降级处理
    throw new ServiceUnavailableException("Account service is temporarily unavailable");
}

// RetryableException - 网络超时
try {
    accountClient.debit(request);
} catch (RetryableException e) {
    log.error("Request timeout: {}", e.getMessage());
    // Feign 会自动重试（如果配置了 Retryer）
    throw new TimeoutException("Request timeout, please try again");
}
```

---

### 5.3 全局异常处理器

**文件位置**: `auth-service/src/main/java/com/finpay/authservice/exceptions/GlobalExceptionHandler.java`

```java
package com.finpay.authservice.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 *
 * 作用:
 * - 捕获所有未处理的异常
 * - 返回统一的错误响应格式
 * - 避免敏感信息泄露
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理所有未捕获的异常
     *
     * @param ex 异常对象
     * @return 标准化的错误响应
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleException(Exception ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", ex.getMessage());
        error.put("timestamp", System.currentTimeMillis());

        return ResponseEntity
            .status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(error);
    }

    /**
     * 处理 Feign 异常
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<Map<String, Object>> handleFeignException(FeignException ex) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "External service error");
        error.put("details", ex.contentUTF8());  // 下游服务返回的错误详情
        error.put("status", ex.status());

        return ResponseEntity
            .status(ex.status())
            .body(error);
    }
}
```

---

### 5.4 业务异常

**文件位置**: `common/src/main/java/com/finpay/common/exception/BusinessException.java`

```java
package com.finpay.common.exception;

/**
 * 业务异常
 *
 * 用于表示业务逻辑错误（非技术错误）
 * 例如: 余额不足、账户冻结、交易限额超限等
 */
public class BusinessException extends RuntimeException {

    public BusinessException(String message) {
        super(message);
    }

    public BusinessException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

#### 使用示例

```java
@Service
public class TransactionService {

    public void transfer(TransferRequest request) {
        // 业务规则校验
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException("Transfer amount must be positive");
        }

        if (request.getAmount().compareTo(new BigDecimal("10000")) > 0) {
            throw new BusinessException("Transfer amount exceeds daily limit");
        }

        try {
            accountClient.debit(request);
        } catch (FeignException.BadRequest e) {
            // 将 Feign 异常转换为业务异常
            throw new BusinessException("Insufficient balance", e);
        }
    }
}
```

---

### 5.5 TransactionService 中的异常处理策略

```java
@Transactional
public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {

    AccountDto accDto = accountClient.getAccount(request.getFromAccountId());
    Transaction tx = createTransaction(request, idempotencyKey);

    try {
        // 尝试执行转账
        accountClient.debit(new DebitRequest(tx.getFromAccountId(), tx.getAmount()));
        accountClient.credit(new CreditRequest(tx.getToAccountId(), tx.getAmount()));

        tx.setStatus(Transaction.Status.COMPLETED);
        notificationClient.sendNotification(buildSuccessNotification(accDto));

    } catch (FeignException.BadRequest e) {
        // 余额不足或业务规则违反
        tx.setStatus(Transaction.Status.FAILED);
        tx.setFailureReason("Insufficient balance or business rule violation");
        notificationClient.sendNotification(buildFailureNotification(accDto, e.getMessage()));

    } catch (FeignException.Unauthorized | FeignException.Forbidden e) {
        // 认证或权限错误（严重问题，需要立即处理）
        tx.setStatus(Transaction.Status.FAILED);
        tx.setFailureReason("Authentication or authorization error");
        log.error("Security error in transaction {}: {}", tx.getId(), e.getMessage());
        throw new SecurityException("Invalid credentials", e);

    } catch (FeignException.NotFound e) {
        // 账户不存在
        tx.setStatus(Transaction.Status.FAILED);
        tx.setFailureReason("Account not found");
        notificationClient.sendNotification(buildFailureNotification(accDto, "Account not found"));

    } catch (RetryableException e) {
        // 网络超时 - 保持 PENDING 状态，稍后重试
        tx.setStatus(Transaction.Status.PENDING);
        tx.setFailureReason("Network timeout - will retry");
        log.warn("Transaction {} timeout, will retry later", tx.getId());

    } catch (Exception e) {
        // 其他未知错误
        tx.setStatus(Transaction.Status.FAILED);
        tx.setFailureReason("Unexpected error: " + e.getMessage());
        log.error("Unexpected error in transaction {}", tx.getId(), e);
        notificationClient.sendNotification(buildFailureNotification(accDto, "System error"));
    }

    Transaction saved = repository.save(tx);
    return toResponse(saved);
}
```

---

## 六、API Gateway 层的保护机制

### 6.1 Resilience4j 断路器配置

**文件位置**: `api-gateway/src/main/resources/application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: api-gateway
  cloud:
    gateway:
      routes:
        # Transaction Service 路由
        - id: transaction-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/transactions/**
          filters:
            - StripPrefix=1
            - name: CircuitBreaker
              args:
                name: transactionCB              # 断路器名称
                fallbackUri: forward:/fallback/transactions  # 降级端点

# Resilience4j 配置
resilience4j:
  circuitbreaker:
    instances:
      transactionCB:
        failureRateThreshold: 50              # 失败率阈值：50%
        waitDurationInOpenState: 10s          # 断路器开启后等待时间：10秒
        slidingWindowSize: 10                 # 滑动窗口大小：10次请求
        minimumNumberOfCalls: 5               # 最小调用次数（达到后才计算失败率）
        automaticTransitionFromOpenToHalfOpenEnabled: true

  timelimiter:
    instances:
      transactionCB:
        timeoutDuration: 5s                   # 请求超时时间：5秒
```

---

### 6.2 断路器工作原理

#### 三种状态

```
┌─────────────────────────────────────────────────────────┐
│                   CLOSED (关闭状态)                      │
│                   正常工作状态                           │
│                                                         │
│  - 所有请求正常转发到 Transaction Service                │
│  - 记录成功/失败统计                                     │
│  - 当失败率 >= 50% 时 → 转到 OPEN 状态                   │
└─────────────────────┬───────────────────────────────────┘
                      │
                      │ 失败率达到 50%
                      ▼
┌─────────────────────────────────────────────────────────┐
│                   OPEN (开启状态)                        │
│                   断路器激活                             │
│                                                         │
│  - 所有请求立即返回降级响应（FallbackController）        │
│  - 不再调用 Transaction Service                          │
│  - 等待 10 秒后 → 转到 HALF_OPEN 状态                    │
└─────────────────────┬───────────────────────────────────┘
                      │
                      │ 等待 10 秒
                      ▼
┌─────────────────────────────────────────────────────────┐
│                 HALF_OPEN (半开状态)                     │
│                 试探性恢复                               │
│                                                         │
│  - 允许少量请求通过（测试服务是否恢复）                   │
│  - 如果请求成功 → 转到 CLOSED 状态                       │
│  - 如果请求失败 → 转回 OPEN 状态                         │
└─────────────────────────────────────────────────────────┘
```

---

### 6.3 断路器工作流程示例

#### 场景：Transaction Service 出现故障

```
时间轴  │  请求  │  结果  │  失败率  │  断路器状态  │  说明
───────┼────────┼────────┼─────────┼─────────────┼──────────────
 0s    │  Req 1 │   ✓    │   0%    │   CLOSED    │ 正常请求
 1s    │  Req 2 │   ✓    │   0%    │   CLOSED    │ 正常请求
 2s    │  Req 3 │   ✗    │  10%    │   CLOSED    │ 开始出现失败
 3s    │  Req 4 │   ✗    │  20%    │   CLOSED    │ 失败增加
 4s    │  Req 5 │   ✗    │  30%    │   CLOSED    │ 失败继续增加
 5s    │  Req 6 │   ✗    │  40%    │   CLOSED    │ 接近阈值
 6s    │  Req 7 │   ✗    │  50%    │   CLOSED    │ 达到阈值
 7s    │  Req 8 │   ✗    │  55%    │   OPEN !!!  │ 断路器开启
───────┼────────┼────────┼─────────┼─────────────┼──────────────
 8s    │  Req 9 │ Fallback│  -     │   OPEN      │ 返回降级响应
 9s    │ Req 10 │ Fallback│  -     │   OPEN      │ 返回降级响应
 10s   │ Req 11 │ Fallback│  -     │   OPEN      │ 返回降级响应
 ...   │  ...   │  ...   │  -     │   OPEN      │ 等待 10 秒
 17s   │ Req 12 │ Fallback│  -     │   OPEN      │ 仍在等待
───────┼────────┼────────┼─────────┼─────────────┼──────────────
 18s   │ Req 13 │   ✓    │   -     │  HALF_OPEN  │ 试探性请求
 19s   │ Req 14 │   ✓    │   -     │  HALF_OPEN  │ 测试成功
 20s   │ Req 15 │   ✓    │   0%    │   CLOSED    │ 恢复正常
```

---

### 6.4 降级处理器

**文件位置**: `api-gateway/src/main/java/com/finpay/gateway/controller/FallbackController.java`

```java
package com.finpay.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 断路器降级处理器
 *
 * 当服务不可用或断路器激活时，返回用户友好的降级响应
 */
@RestController
public class FallbackController {

    /**
     * 事务服务降级端点
     *
     * 触发条件:
     * - Transaction Service 失败率 >= 50%
     * - Transaction Service 请求超时 > 5s
     * - Transaction Service 不可达
     *
     * @return 用户友好的错误消息
     */
    @RequestMapping("/fallback/transactions")
    public ResponseEntity<String> transactionFallback() {
        return ResponseEntity.ok(
            "Transaction Service is currently unavailable. " +
            "Please try again later."
        );
    }

    /**
     * 账户服务降级端点
     */
    @RequestMapping("/fallback/accounts")
    public ResponseEntity<String> accountFallback() {
        return ResponseEntity.ok(
            "Account Service is currently unavailable. " +
            "Please try again later."
        );
    }
}
```

---

### 6.5 Gateway Routes 配置

**文件位置**: `api-gateway/src/main/java/com/finpay/gateway/config/GatewayRoutesConfig.java`

```java
package com.finpay.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()

            // Transaction Service 路由 + 断路器
            .route("transaction-service", r -> r
                .path("/api/transactions/**")
                .filters(f -> f
                    .stripPrefix(1)  // 移除 /api 前缀
                    .circuitBreaker(config -> config
                        .setName("transactionCB")
                        .setFallbackUri("forward:/fallback/transactions")
                    )
                )
                .uri("http://localhost:8083")
            )

            // Account Service 路由
            .route("account-service", r -> r
                .path("/api/accounts/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://localhost:8082")
            )

            // Auth Service 路由
            .route("auth-service", r -> r
                .path("/api/auth/**")
                .filters(f -> f.stripPrefix(1))
                .uri("http://localhost:8081")
            )

            .build();
    }
}
```

---

### 6.6 限流配置（使用 Redis）

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: transaction-service
          uri: http://localhost:8083
          predicates:
            - Path=/api/transactions/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 10    # 每秒补充令牌数
                redis-rate-limiter.burstCapacity: 20    # 令牌桶容量
                key-resolver: "#{@userKeyResolver}"     # 基于用户限流
```

**UserKeyResolver.java**:
```java
@Bean
public KeyResolver userKeyResolver() {
    return exchange -> {
        // 从 JWT 中提取用户 ID 作为限流键
        String userId = exchange.getRequest()
            .getHeaders()
            .getFirst("X-User-Id");
        return Mono.just(userId != null ? userId : "anonymous");
    };
}
```

---

## 七、数据传输对象（DTOs）

### 7.1 账户相关 DTOs

#### AccountDto.java - 账户信息

**文件位置**: `common/src/main/java/com/finpay/common/dto/accounts/AccountDto.java`

```java
package com.finpay.common.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 账户数据传输对象
 *
 * 用于服务间传递账户信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountDto {

    /**
     * 账户唯一标识符
     */
    private UUID id;

    /**
     * 账户所有者邮箱
     */
    private String ownerEmail;

    /**
     * 账户余额
     */
    private BigDecimal balance;

    /**
     * 账户状态
     */
    private String status;  // ACTIVE, FROZEN, CLOSED
}
```

---

#### DebitRequest.java - 扣款请求

**文件位置**: `common/src/main/java/com/finpay/common/dto/accounts/DebitRequest.java`

```java
package com.finpay.common.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 账户扣款请求
 *
 * 用于 Transaction Service → Account Service 的扣款操作
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DebitRequest {

    /**
     * 要扣款的账户ID
     */
    private UUID accountId;

    /**
     * 扣款金额
     */
    private BigDecimal amount;
}
```

**使用示例**:
```java
accountClient.debit(new DebitRequest(
    UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
    new BigDecimal("100.00")
));
```

---

#### CreditRequest.java - 入账请求

**文件位置**: `common/src/main/java/com/finpay/common/dto/accounts/CreditRequest.java`

```java
package com.finpay.common.dto.accounts;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 账户入账请求
 *
 * 用于 Transaction Service → Account Service 的入账操作
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreditRequest {

    /**
     * 要入账的账户ID
     */
    private UUID accountId;

    /**
     * 入账金额
     */
    private BigDecimal amount;
}
```

---

### 7.2 欺诈检测 DTOs

#### FraudCheckRequest.java - 欺诈检查请求

**文件位置**: `common/src/main/java/com/finpay/common/dto/frauds/FraudCheckRequest.java`

```java
package com.finpay.common.dto.frauds;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 欺诈检查请求
 *
 * 用于 Transaction Service → Fraud Service 的欺诈检测
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckRequest {

    /**
     * 源账户ID
     */
    private UUID fromAccountId;

    /**
     * 目标账户ID
     */
    private UUID toAccountId;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 交易类型
     */
    private String transactionType;  // TRANSFER, WITHDRAWAL, DEPOSIT

    /**
     * 用户IP地址（可选）
     */
    private String ipAddress;

    /**
     * 设备指纹（可选）
     */
    private String deviceFingerprint;
}
```

---

#### FraudCheckResponse.java - 欺诈检查响应

**文件位置**: `common/src/main/java/com/finpay/common/dto/frauds/FraudCheckResponse.java`

```java
package com.finpay.common.dto.frauds;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 欺诈检查响应
 *
 * 包含欺诈检测结果和详细信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FraudCheckResponse {

    /**
     * 是否为欺诈交易
     */
    private boolean isFraudulent;

    /**
     * 欺诈原因（如果 isFraudulent = true）
     */
    private String reason;

    /**
     * 风险评分（0-100，分数越高风险越大）
     */
    private double riskScore;

    /**
     * 风险等级
     */
    private RiskLevel riskLevel;  // LOW, MEDIUM, HIGH, CRITICAL

    /**
     * 建议操作
     */
    private String recommendedAction;  // APPROVE, REVIEW, REJECT
}

enum RiskLevel {
    LOW,       // 0-25
    MEDIUM,    // 26-50
    HIGH,      // 51-75
    CRITICAL   // 76-100
}
```

**使用示例**:
```java
FraudCheckResponse response = fraudClient.checkFraud(
    new FraudCheckRequest(fromAccountId, toAccountId, amount)
);

if (response.isFraudulent()) {
    log.warn("Fraudulent transaction detected: {}", response.getReason());
    throw new BusinessException("Transaction blocked: " + response.getReason());
}

if (response.getRiskScore() > 70) {
    log.warn("High risk transaction: score={}", response.getRiskScore());
    // 发送到人工审核队列
    manualReviewService.queue(transaction);
}
```

---

### 7.3 通知相关 DTOs

#### NotificationRequest.java - 通知请求

**文件位置**: `common/src/main/java/com/finpay/common/dto/notifications/NotificationRequest.java`

```java
package com.finpay.common.dto.notifications;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 通知请求
 *
 * 用于 Transaction Service → Notification Service 的通知发送
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    /**
     * 接收用户（邮箱或手机号）
     */
    private String userId;

    /**
     * 通知消息内容
     */
    private String message;

    /**
     * 通知渠道
     */
    private String channel;  // EMAIL, SMS, PUSH, IN_APP

    /**
     * 通知类型
     */
    private String type;  // TRANSACTION_SUCCESS, TRANSACTION_FAILED, SECURITY_ALERT

    /**
     * 优先级
     */
    private Priority priority;  // LOW, NORMAL, HIGH, URGENT
}

enum Priority {
    LOW,      // 可以批量发送
    NORMAL,   // 正常发送
    HIGH,     // 优先发送
    URGENT    // 立即发送
}
```

**使用示例**:
```java
// 交易成功通知
notificationClient.sendNotification(
    NotificationRequest.builder()
        .userId("alice@example.com")
        .message("Your transfer of $100 to Bob was successful")
        .channel("EMAIL")
        .type("TRANSACTION_SUCCESS")
        .priority(Priority.NORMAL)
        .build()
);

// 安全警报通知
notificationClient.sendNotification(
    NotificationRequest.builder()
        .userId("alice@example.com")
        .message("Unusual login detected from new device")
        .channel("SMS")
        .type("SECURITY_ALERT")
        .priority(Priority.URGENT)
        .build()
);
```

---

### 7.4 交易相关 DTOs

#### TransferRequest.java - 转账请求

**文件位置**: `common/src/main/java/com/finpay/common/dto/transactions/TransferRequest.java`

```java
package com.finpay.common.dto.transactions;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * 转账请求
 *
 * 用于用户发起转账操作
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {

    /**
     * 源账户ID
     */
    @NotNull(message = "From account ID is required")
    private UUID fromAccountId;

    /**
     * 目标账户ID
     */
    @NotNull(message = "To account ID is required")
    private UUID toAccountId;

    /**
     * 转账金额
     */
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    @DecimalMax(value = "100000.00", message = "Amount cannot exceed 100,000")
    private BigDecimal amount;

    /**
     * 转账备注（可选）
     */
    @Size(max = 200, message = "Description cannot exceed 200 characters")
    private String description;
}
```

---

#### TransactionResponse.java - 交易响应

**文件位置**: `common/src/main/java/com/finpay/common/dto/transactions/TransactionResponse.java`

```java
package com.finpay.common.dto.transactions;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 交易响应
 *
 * 返回给用户的交易结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

    /**
     * 交易ID
     */
    private UUID id;

    /**
     * 源账户ID
     */
    private UUID fromAccountId;

    /**
     * 目标账户ID
     */
    private UUID toAccountId;

    /**
     * 交易金额
     */
    private BigDecimal amount;

    /**
     * 交易状态
     */
    private String status;  // PENDING, COMPLETED, FAILED

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 完成时间
     */
    private LocalDateTime completedAt;

    /**
     * 失败原因（如果 status = FAILED）
     */
    private String failureReason;

    /**
     * 幂等性键
     */
    private String idempotencyKey;
}
```

---

## 八、分布式追踪集成

### 8.1 Micrometer + Zipkin 配置

#### Maven 依赖

**文件位置**: `transaction-service/pom.xml`

```xml
<dependencies>
    <!-- Spring Cloud OpenFeign -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-openfeign</artifactId>
    </dependency>

    <!-- 分布式追踪 - Micrometer Brave Bridge -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-tracing-bridge-brave</artifactId>
    </dependency>

    <!-- Zipkin Reporter -->
    <dependency>
        <groupId>io.zipkin.reporter2</groupId>
        <artifactId>zipkin-reporter-brave</artifactId>
    </dependency>
</dependencies>
```

---

#### application.yml 配置

**文件位置**: `transaction-service/src/main/resources/application.yml`

```yaml
server:
  port: 8083

spring:
  application:
    name: transaction-service

# 分布式追踪配置
management:
  tracing:
    sampling:
      probability: 1.0  # 采样率 100%（生产环境建议 0.1 即 10%）
    zipkin:
      base-url: http://localhost:9411   # Zipkin 服务地址
      enabled: true                      # 启用 Zipkin

  # 暴露追踪端点
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

### 8.2 追踪链路示例

#### 完整的转账请求追踪

```
Trace ID: abc123xyz456  (贯穿整个请求链路的唯一标识)
│
├─ Span 1: POST /api/transactions/transfer (API Gateway)
│   ├─ Duration: 523ms
│   ├─ Tags: http.method=POST, http.status_code=200
│   └─ Span 2: POST /transactions/transfer (Transaction Service)
│       ├─ Duration: 485ms
│       ├─ Span 3: GET /accounts/{id} (Account Service) [Feign]
│       │   ├─ Duration: 45ms
│       │   ├─ Tags: feign.method=getAccount, http.status_code=200
│       │   └─ SQL Query: SELECT * FROM accounts WHERE id = ?
│       │
│       ├─ Span 4: Kafka Send (Transaction Service)
│       │   ├─ Duration: 12ms
│       │   └─ Tags: kafka.topic=transactions, kafka.partition=0
│       │
│       ├─ Span 5: POST /accounts/debit (Account Service) [Feign]
│       │   ├─ Duration: 78ms
│       │   ├─ Tags: feign.method=debit, http.status_code=200
│       │   └─ SQL Query: UPDATE accounts SET balance = balance - ? WHERE id = ?
│       │
│       ├─ Span 6: POST /accounts/credit (Account Service) [Feign]
│       │   ├─ Duration: 65ms
│       │   ├─ Tags: feign.method=credit, http.status_code=200
│       │   └─ SQL Query: UPDATE accounts SET balance = balance + ? WHERE id = ?
│       │
│       └─ Span 7: POST /notifications (Notification Service) [Feign]
│           ├─ Duration: 32ms
│           ├─ Tags: feign.method=sendNotification, http.status_code=200
│           └─ Email Sent: alice@example.com
```

---

### 8.3 Zipkin UI 可视化

#### 启动 Zipkin

```bash
# 使用 Docker 启动 Zipkin
docker run -d -p 9411:9411 openzipkin/zipkin
```

#### 访问 Zipkin UI

```
浏览器访问: http://localhost:9411
```

#### 追踪查询示例

```
1. 按服务名称查询
   Service Name: transaction-service

2. 按 Trace ID 查询
   Trace ID: abc123xyz456

3. 按时间范围查询
   Start: 2024-01-15 10:00:00
   End:   2024-01-15 11:00:00

4. 按耗时筛选
   Duration: > 1000ms (查找慢请求)
```

---

### 8.4 自定义 Span 标签

#### 添加业务信息到追踪

```java
@Service
public class TransactionService {

    @Autowired
    private Tracer tracer;  // Brave Tracer

    @Transactional
    public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {

        // 获取当前 Span
        Span span = tracer.currentSpan();

        // 添加自定义标签
        if (span != null) {
            span.tag("transaction.from_account", request.getFromAccountId().toString());
            span.tag("transaction.to_account", request.getToAccountId().toString());
            span.tag("transaction.amount", request.getAmount().toString());
            span.tag("transaction.idempotency_key", idempotencyKey);
        }

        // 创建子 Span
        Span childSpan = tracer.nextSpan().name("fraud-check").start();
        try {
            // 执行业务逻辑
            FraudCheckResponse fraudCheck = fraudClient.checkFraud(...);
            childSpan.tag("fraud.is_fraudulent", String.valueOf(fraudCheck.isFraudulent()));
            childSpan.tag("fraud.risk_score", String.valueOf(fraudCheck.getRiskScore()));
        } finally {
            childSpan.finish();
        }

        // ... 其他业务逻辑
    }
}
```

---

### 8.5 追踪数据的价值

#### 1. 性能分析

```
发现慢请求:
- 某些转账请求耗时 > 2 秒
- 深入分析发现: accountClient.debit() 耗时 1.5 秒
- 根因: Account Service 数据库查询缺少索引
- 解决: 为 accounts 表的 id 列添加索引
```

#### 2. 错误排查

```
用户报告: "我的转账失败了，但不知道为什么"
追踪分析:
- Trace ID: xyz789
- Span 5 (accountClient.debit) 返回 403 Forbidden
- 标签显示: jwt.expired=true
- 结论: JWT 令牌过期导致认证失败
```

#### 3. 依赖关系分析

```
Zipkin 依赖关系图显示:

Transaction Service
    ├─> Account Service (80% 流量)
    ├─> Notification Service (100% 流量)
    └─> Fraud Service (0% 流量)  ← 发现未使用的依赖
```

---

## 九、OpenFeign 最佳实践

### 9.1 配置层面

#### ✅ 推荐做法

**1. 使用服务发现（而非硬编码 URL）**

```java
// ❌ 不推荐 - 硬编码 URL
@FeignClient(name = "account-service", url = "http://localhost:8082")

// ✅ 推荐 - 使用服务发现
@FeignClient(name = "account-service")  // 通过 Eureka/Consul 自动发现
```

**2. 配置超时时间**

```yaml
# application.yml
feign:
  client:
    config:
      default:
        connectTimeout: 5000      # 连接超时：5秒
        readTimeout: 10000        # 读取超时：10秒

      # 为特定客户端定制超时
      account-service:
        readTimeout: 15000        # 账户服务可能需要更长时间
```

**3. 启用请求/响应压缩**

```yaml
feign:
  compression:
    request:
      enabled: true
      mime-types: text/xml,application/xml,application/json
      min-request-size: 2048    # 超过 2KB 才压缩
    response:
      enabled: true
```

**4. 配置重试策略**

```java
@Configuration
public class FeignConfig {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            100,    // 初始重试间隔 100ms
            1000,   // 最大重试间隔 1s
            3       // 最多重试 3 次
        );
    }
}
```

---

#### ❌ 避免做法

**1. 在 Feign 接口中处理业务逻辑**

```java
// ❌ 不要这样做
@FeignClient(name = "account-service")
public interface AccountClient {

    default void debitWithValidation(DebitRequest request) {
        if (request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Invalid amount");
        }
        debit(request);  // 业务逻辑不应该在 Feign 接口中
    }

    AccountDto debit(DebitRequest request);
}
```

**2. 忽略异常处理**

```java
// ❌ 不要这样做
public void transfer() {
    accountClient.debit(request);  // 如果失败怎么办？
    accountClient.credit(request);
}

// ✅ 应该这样做
public void transfer() {
    try {
        accountClient.debit(request);
        accountClient.credit(request);
    } catch (FeignException e) {
        handleError(e);
    }
}
```

---

### 9.2 安全层面

#### ✅ 推荐做法

**1. 始终转发认证令牌**

```java
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return template -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
                template.header("Authorization", "Bearer " + jwt.getTokenValue());
            }
        };
    }
}
```

**2. 在下游服务验证 JWT**

```java
// Account Service 中
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())  // 验证 JWT 签名
                )
            )
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()  // 所有请求都需要认证
            );
        return http.build();
    }
}
```

**3. 使用 HTTPS（生产环境）**

```java
@FeignClient(
    name = "account-service",
    url = "https://account-service.example.com"  // 使用 HTTPS
)
```

**4. 实现请求签名（可选，高安全场景）**

```java
@Configuration
public class FeignConfig {

    @Bean
    public RequestInterceptor signatureInterceptor() {
        return template -> {
            String body = new String(template.body());
            String signature = HmacUtils.hmacSha256Hex(secretKey, body);
            template.header("X-Signature", signature);
        };
    }
}
```

---

### 9.3 性能层面

#### ✅ 推荐做法

**1. 配置连接池**

```yaml
feign:
  httpclient:
    enabled: true                # 使用 Apache HttpClient
    max-connections: 200         # 最大连接数
    max-connections-per-route: 50  # 每个路由的最大连接数
```

**2. 启用响应缓存（对于查询接口）**

```java
@Cacheable(value = "accounts", key = "#id")
public AccountDto getAccount(UUID id) {
    return accountClient.getAccount(id);
}
```

**3. 使用异步调用（并行调用多个服务）**

```java
@Service
public class TransactionService {

    @Async
    public CompletableFuture<AccountDto> getAccountAsync(UUID id) {
        return CompletableFuture.completedFuture(
            accountClient.getAccount(id)
        );
    }

    public void transfer(TransferRequest request) {
        // 并行获取两个账户信息
        CompletableFuture<AccountDto> fromAccount =
            getAccountAsync(request.getFromAccountId());
        CompletableFuture<AccountDto> toAccount =
            getAccountAsync(request.getToAccountId());

        // 等待两个请求完成
        CompletableFuture.allOf(fromAccount, toAccount).join();
    }
}
```

**4. 监控 Feign 调用指标**

```yaml
management:
  metrics:
    export:
      prometheus:
        enabled: true
  endpoints:
    web:
      exposure:
        include: prometheus
```

---

### 9.4 可靠性层面

#### ✅ 推荐做法

**1. 实现降级处理**

```java
@Component
public class AccountClientFallback implements AccountClient {

    @Override
    public AccountDto getAccount(UUID id) {
        // 返回缓存数据或默认值
        return AccountDto.builder()
            .id(id)
            .balance(BigDecimal.ZERO)
            .status("UNAVAILABLE")
            .build();
    }

    @Override
    public AccountDto debit(DebitRequest request) {
        throw new ServiceUnavailableException("Account service is down");
    }
}

@FeignClient(
    name = "account-service",
    fallback = AccountClientFallback.class  // 指定降级类
)
public interface AccountClient {
    // ...
}
```

**2. 实现幂等性**

```java
@Transactional
public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {
    // 检查是否已处理过
    Optional<Transaction> existing = repository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        return toResponse(existing.get());  // 返回之前的结果
    }

    // 处理新请求...
}
```

**3. 记录详细日志**

```java
@Slf4j
@Service
public class TransactionService {

    public void transfer(TransferRequest request) {
        log.info("Starting transfer: from={}, to={}, amount={}",
            request.getFromAccountId(),
            request.getToAccountId(),
            request.getAmount());

        try {
            accountClient.debit(request);
            log.info("Debit successful: account={}", request.getFromAccountId());
        } catch (FeignException e) {
            log.error("Debit failed: account={}, status={}, body={}",
                request.getFromAccountId(),
                e.status(),
                e.contentUTF8());
            throw e;
        }
    }
}
```

---

## 十、改进建议

### 10.1 当前问题与改进方案

#### 问题 1: 硬编码 URL

**当前代码**:
```java
@FeignClient(name = "account-service", url = "http://localhost:8082/accounts")
```

**问题**:
- 无法动态调整服务地址
- 不支持负载均衡
- 无法实现服务容错

**改进方案**:
```java
// 1. 添加 Eureka 依赖
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>

// 2. 修改 Feign 客户端
@FeignClient(name = "account-service")  // 移除 url 属性
public interface AccountClient {
    // ...
}

// 3. 配置 Eureka
spring:
  cloud:
    discovery:
      enabled: true
eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka
```

---

#### 问题 2: 缺少超时配置

**当前状态**:
- 使用默认超时时间（可能过长）
- 可能导致请求堆积

**改进方案**:
```yaml
# application.yml
feign:
  client:
    config:
      default:
        connectTimeout: 5000      # 连接超时 5秒
        readTimeout: 10000        # 读取超时 10秒

      # 为不同服务定制超时
      account-service:
        connectTimeout: 3000
        readTimeout: 8000

      fraud-service:
        connectTimeout: 2000
        readTimeout: 5000          # 欺诈检测要求快速响应
```

---

#### 问题 3: FraudClient 未使用

**当前状态**:
```java
@Service
@RequiredArgsConstructor
public class TransactionService {
    private final FraudClient fraudClient;  // 已注入但未使用
}
```

**改进方案**:
```java
@Transactional
public TransactionResponse transfer(String idempotencyKey, TransferRequest request) {

    AccountDto accDto = accountClient.getAccount(request.getFromAccountId());

    // ✅ 添加欺诈检测
    FraudCheckResponse fraudCheck = fraudClient.checkFraud(
        new FraudCheckRequest(
            request.getFromAccountId(),
            request.getToAccountId(),
            request.getAmount()
        )
    );

    if (fraudCheck.isFraudulent()) {
        log.warn("Fraudulent transaction detected: reason={}, riskScore={}",
            fraudCheck.getReason(),
            fraudCheck.getRiskScore());

        throw new BusinessException(
            "Transaction blocked due to fraud suspicion: " + fraudCheck.getReason()
        );
    }

    // 对于高风险交易，发送到人工审核队列
    if (fraudCheck.getRiskScore() > 70) {
        manualReviewService.queueForReview(transaction, fraudCheck);
        transaction.setStatus(Transaction.Status.PENDING_REVIEW);
        return toResponse(transaction);
    }

    // 继续正常交易流程...
}
```

---

#### 问题 4: 缺少重试机制

**改进方案**:
```java
@Configuration
public class FeignConfig {

    @Bean
    public Retryer retryer() {
        return new Retryer.Default(
            100,    // 初始间隔 100ms
            1000,   // 最大间隔 1s
            3       // 最多重试 3 次
        );
    }

    // 或者使用 Resilience4j Retry
    @Bean
    public Retry retry() {
        return Retry.of("feign-retry", RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryExceptions(FeignException.ServiceUnavailable.class)
            .build());
    }
}
```

---

#### 问题 5: 分布式事务缺失

**当前问题**:
```java
try {
    accountClient.debit(request);   // ✓ 成功
    accountClient.credit(request);  // ✗ 失败（网络超时）
} catch (Exception e) {
    // 此时 debit 已执行，但 credit 失败
    // 数据不一致！源账户已扣款，但目标账户未入账
}
```

**改进方案 1: Saga 模式（推荐）**

```java
@Service
public class TransactionSaga {

    @Transactional
    public TransactionResponse transfer(TransferRequest request) {
        Transaction tx = createTransaction(request);

        try {
            // Step 1: Debit
            accountClient.debit(new DebitRequest(...));
            tx.addStep("DEBIT_COMPLETED");

            // Step 2: Credit
            accountClient.credit(new CreditRequest(...));
            tx.addStep("CREDIT_COMPLETED");

            tx.setStatus(COMPLETED);

        } catch (Exception e) {
            // 补偿操作：回滚已完成的步骤
            compensate(tx);
            tx.setStatus(FAILED);
        }

        return toResponse(tx);
    }

    private void compensate(Transaction tx) {
        if (tx.hasStep("DEBIT_COMPLETED")) {
            // 回滚 debit：向源账户退款
            accountClient.credit(new CreditRequest(
                tx.getFromAccountId(),
                tx.getAmount()
            ));
        }
    }
}
```

**改进方案 2: 两阶段提交（2PC）**

```java
// Phase 1: Prepare
boolean debitPrepared = accountClient.prepareDebit(request);
boolean creditPrepared = accountClient.prepareCredit(request);

if (debitPrepared && creditPrepared) {
    // Phase 2: Commit
    accountClient.commitDebit(request);
    accountClient.commitCredit(request);
} else {
    // Phase 2: Rollback
    accountClient.rollbackDebit(request);
    accountClient.rollbackCredit(request);
}
```

---

### 10.2 生产环境增强清单

#### 1. 服务发现与负载均衡

```xml
<!-- 添加 Eureka Client -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
</dependency>

<!-- 添加 LoadBalancer -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-loadbalancer</artifactId>
</dependency>
```

```yaml
spring:
  cloud:
    loadbalancer:
      ribbon:
        enabled: false
      cache:
        enabled: true
        ttl: 30s
```

---

#### 2. 限流保护

```java
@Configuration
public class FeignConfig {

    @Bean
    public RateLimiter rateLimiter() {
        return RateLimiter.of("feign-rate-limiter", RateLimiterConfig.custom()
            .limitForPeriod(100)               // 每个周期最多 100 个请求
            .limitRefreshPeriod(Duration.ofSeconds(1))  // 周期 1 秒
            .timeoutDuration(Duration.ofMillis(500))    // 等待超时 500ms
            .build());
    }
}
```

---

#### 3. 监控与告警

```yaml
# Prometheus + Grafana
management:
  metrics:
    export:
      prometheus:
        enabled: true
    tags:
      application: ${spring.application.name}
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

**关键指标**:
- `feign.client.requests.total` - 总请求数
- `feign.client.requests.duration` - 请求耗时
- `feign.client.errors.total` - 错误数
- `feign.client.retries.total` - 重试次数

---

#### 4. 缓存优化

```java
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new CaffeineCacheManager("accounts", "fraudChecks");
    }
}

@Service
public class TransactionService {

    @Cacheable(value = "accounts", key = "#id")
    public AccountDto getAccount(UUID id) {
        return accountClient.getAccount(id);  // 只有缓存未命中时才调用
    }
}
```

---

#### 5. 请求日志与审计

```java
@Configuration
public class FeignConfig {

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;  // 记录完整的请求和响应
    }

    @Bean
    public RequestInterceptor auditInterceptor() {
        return template -> {
            String requestId = UUID.randomUUID().toString();
            template.header("X-Request-ID", requestId);

            log.info("Feign Request: method={}, url={}, requestId={}",
                template.method(),
                template.url(),
                requestId);
        };
    }
}
```

---

## 总结

### FinPay 项目中 OpenFeign 的核心价值

| 方面 | 实现方式 | 优势 |
|-----|---------|------|
| **简化调用** | 声明式 HTTP 客户端 | 无需手动编写 RestTemplate 代码 |
| **安全保障** | FeignConfig JWT 拦截器 | 自动转发认证令牌，保持用户上下文 |
| **弹性设计** | Resilience4j 断路器 | 防止故障扩散，提供降级保护 |
| **可观测性** | Micrometer + Zipkin | 完整的分布式追踪链路 |
| **标准化** | 统一的 DTOs | 服务间通信协议清晰 |

### 关键要点

1. **Transaction Service** 是唯一的 Feign 消费者，通过 3 个客户端调用下游服务
2. **FeignConfig** 提供 JWT 令牌自动转发，确保服务间认证一致性
3. **API Gateway** 在更上层提供断路器、限流和降级保护
4. **幂等性设计** 通过 `idempotencyKey` 防止重复交易
5. **异常处理** 完整，从 Feign 异常到业务异常都有对应策略
6. **分布式追踪** 通过 Zipkin 实现完整的调用链路可视化

### 后续优化方向

- [ ] 实现服务发现（Eureka/Consul）替代硬编码 URL
- [ ] 集成欺诈检测到交易流程
- [ ] 实现分布式事务（Saga 模式）
- [ ] 添加请求/响应缓存
- [ ] 完善监控和告警机制

---

**文档版本**: v1.0
**最后更新**: 2024-01-15
**维护者**: FinPay Team
