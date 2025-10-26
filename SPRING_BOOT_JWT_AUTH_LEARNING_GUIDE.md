# Spring Boot JWT认证服务完整学习指南

> 本文档详细讲解了Spring Boot JWT认证服务的完整实现流程，从HTTP请求到数据库查询，从密码验证到Token生成，涵盖所有核心组件和最佳实践。

## 📑 目录

1. [项目概述](#项目概述)
2. [完整调用序列](#完整调用序列)
3. [第一部分：HTTP请求入口和Servlet基础](#第一部分http请求入口和servlet基础)
4. [第二部分：Spring MVC DispatcherServlet](#第二部分spring-mvc-dispatcherservlet)
5. [第三部分：Spring Security过滤器链](#第三部分spring-security过滤器链)
6. [第四部分：Controller层和请求映射](#第四部分controller层和请求映射)
7. [第五部分：认证管理器和Provider](#第五部分认证管理器和provider)
8. [第六部分：UserDetailsService和数据库查询](#第六部分userdetailsservice和数据库查询)
9. [第七部分：密码验证机制](#第七部分密码验证机制)
10. [第八部分：JWT生成和签名](#第八部分jwt生成和签名)
11. [第九部分：受保护端点的JWT验证流程](#第九部分受保护端点的jwt验证流程)
12. [核心组件配置清单](#核心组件配置清单)
13. [最佳实践](#最佳实践)
14. [从零开始构建项目](#从零开始构建项目)
15. [学习路径建议](#学习路径建议)

---

## 项目概述

本项目是一个基于**Spring Boot 3.x**的**JWT认证微服务**，实现了：

- ✅ 用户注册和登录
- ✅ JWT token生成和验证
- ✅ 基于角色的访问控制（RBAC）
- ✅ 无状态会话管理（STATELESS）
- ✅ RSA非对称加密签名
- ✅ BCrypt密码加密
- ✅ Spring Data JPA数据访问

### 技术栈

| 技术 | 版本/说明 |
|------|----------|
| Spring Boot | 3.x |
| Spring Security | 6.x |
| Spring Data JPA | Hibernate实现 |
| PostgreSQL | 关系型数据库 |
| JWT | RS256 (RSA签名) |
| Lombok | 简化代码 |

---

## 完整调用序列

### 🔐 登录请求流程

```
1. HTTP POST /auth-services/login
   ↓
2. Tomcat HttpServlet.service()
   ↓
3. Spring DispatcherServlet.doDispatch()
   ↓
4. Spring Security FilterChainProxy
   ├─ SecurityContextPersistenceFilter
   ├─ LogoutFilter
   ├─ UsernamePasswordAuthenticationFilter (跳过 - 使用自定义)
   ├─ AnonymousAuthenticationFilter
   ├─ SessionManagementFilter
   ├─ ExceptionTranslationFilter
   └─ AuthorizationFilter (permitAll - 放行)
   ↓
5. RequestMappingHandlerAdapter
   ↓
6. AuthController.login()
   ↓
7. AuthenticationManager.authenticate()
   ↓
8. DaoAuthenticationProvider.authenticate()
   ↓
9. UserService.loadUserByUsername()
   ↓
10. UserRepository.findByUsername()
    ↓
11. Hibernate Session.createQuery()
    ↓
12. PostgreSQL JDBC execute query
    ↓
13. Return UserEntity
    ↓
14. Create CustomUserDetails
    ↓
15. DaoAuthenticationProvider.additionalAuthenticationChecks()
    ↓
16. BCryptPasswordEncoder.matches()
    ↓
17. Return Authentication object
    ↓
18. Build JwtEncoderParameters (claims)
    ↓
19. JwtEncoder.encode() - sign with private.pem
    ↓
20. Return JWT token string
    ↓
21. Spring MVC converts to JSON response
    ↓
22. Return HTTP 200 OK
```

### 🔒 受保护端点请求流程

```
1. HTTP GET /auth-services/users + Bearer token
   ↓
2. Spring Security FilterChainProxy
   ↓
3. BearerTokenAuthenticationFilter
   ↓
4. Extract token from Authorization header
   ↓
5. JwtDecoder.decode()
   ├─ Parse JWT structure
   ├─ Verify signature with public.pem
   └─ Check expiration
   ↓
6. Create JwtAuthenticationToken
   ↓
7. Set SecurityContext with authorities
   ↓
8. AuthorizationFilter - check user has required role
   ↓
9. UserController.getAllUsers()
   ↓
10. UserService.getAllUsers()
    ↓
11. UserRepository.findAll()
    ↓
12. Hibernate query execution
    ↓
13. Map entities to DTOs
    ↓
14. Return JSON response
```

---

## 第一部分：HTTP请求入口和Servlet基础

### 1.1 什么是HTTP请求处理？

当用户发送登录请求时：

```bash
POST http://localhost:8081/auth-services/login
Content-Type: application/json

{
  "username": "user123",
  "password": "password123"
}
```

这个请求会经过以下路径：

```
用户浏览器 → 网络 → Tomcat服务器（端口8081）→ Spring应用
```

### 1.2 Tomcat容器接收请求

**Tomcat**是一个**Servlet容器**（Web服务器），它的职责是：

- 监听HTTP端口（默认8080，本项目配置为8081）
- 接收TCP/IP连接和HTTP请求
- 将HTTP请求解析为`HttpServletRequest`对象
- 将HTTP响应封装为`HttpServletResponse`对象
- 调用相应的Servlet处理请求

**配置位置**：`application.yml`

```yaml
server:
  port: 8081  # Tomcat监听这个端口
```

### 1.3 HttpServlet.service()方法

Servlet是Java Web应用的基本组件。每个HTTP请求都会触发`service()`方法：

```java
// Servlet的基本概念（Spring Boot帮你做了这些）
public class MyServlet extends HttpServlet {
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        // 根据HTTP方法（GET/POST/PUT/DELETE）分发请求
        String method = req.getMethod();
        if ("POST".equals(method)) {
            doPost(req, resp);
        } else if ("GET".equals(method)) {
            doGet(req, resp);
        }
    }
}
```

### 1.4 关键概念

**HttpServletRequest**：包含所有请求信息
- URL路径：`/auth-services/login`
- HTTP方法：`POST`
- 请求头：`Content-Type: application/json`
- 请求体：`{"username":"user123","password":"password123"}`
- 参数、Cookies等

**HttpServletResponse**：用于构建响应
- 状态码：`200 OK`、`401 Unauthorized`等
- 响应头：`Content-Type: application/json`
- 响应体：`{"token":"eyJhbG..."}`

---

## 第二部分：Spring MVC DispatcherServlet

### 2.1 什么是DispatcherServlet？

**DispatcherServlet**是Spring MVC的核心，它是一个**前端控制器**（Front Controller Pattern），负责：

1. **统一接收**所有HTTP请求
2. **路由分发**到合适的Controller
3. **协调处理**整个请求-响应流程
4. **视图渲染**（REST API中通常返回JSON）

### 2.2 工作流程

```
Tomcat → DispatcherServlet → 查找Handler → 调用Controller方法 → 返回结果
```

**DispatcherServlet内部逻辑**（简化）：

```java
// Spring Boot自动配置了DispatcherServlet，你不需要手动写
public class DispatcherServlet extends FrameworkServlet {

    protected void doDispatch(HttpServletRequest request,
                              HttpServletResponse response) {

        // 1. 根据请求URL找到处理的Handler（Controller方法）
        HandlerExecutionChain handler = getHandler(request);
        // 对于 POST /auth-services/login
        // 匹配到：AuthController.authenticate()方法

        // 2. 获取能够执行这个Handler的适配器
        HandlerAdapter adapter = getHandlerAdapter(handler);
        // 通常是 RequestMappingHandlerAdapter

        // 3. 执行Handler（调用你的Controller方法）
        ModelAndView mv = adapter.handle(request, response, handler);

        // 4. 处理返回结果（视图渲染或JSON序列化）
        processDispatchResult(request, response, mv);
    }
}
```

### 2.3 如何找到正确的Controller？

看项目中的`AuthController.java`：

```java
@RestController                  // ① 声明这是一个REST控制器
@RequestMapping("/auth-services") // ② 类级别的URL前缀
public class AuthController {

    @PostMapping("/login")       // ③ 方法级别的路径 + HTTP方法
    public JwtResponse authenticate(@RequestBody LoginRequest loginRequest) {
        // 这个方法会被调用
    }
}
```

**Spring的URL映射规则**：

| 注解 | 作用 | 示例 |
|------|------|------|
| `@RestController` | 标记为REST控制器，返回值自动序列化为JSON | - |
| `@RequestMapping("/auth-services")` | 类级别路径前缀 | `/auth-services` |
| `@PostMapping("/login")` | 方法路径 + HTTP方法限制 | `POST /login` |
| **完整路径** | 类路径 + 方法路径 | `POST /auth-services/login` ✅ |

### 2.4 Spring Boot自动配置

看项目中的`AuthServiceApplication.java`：

```java
@SpringBootApplication  // 这一个注解包含三个核心注解！
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

**@SpringBootApplication = 三合一注解**：

```java
@EnableAutoConfiguration  // ① 自动配置Tomcat、DispatcherServlet、Jackson等
@ComponentScan           // ② 扫描所有@Controller、@Service、@Repository
@Configuration           // ③ 允许定义Bean配置
public @interface SpringBootApplication { }
```

**自动配置做了什么**：
- 启动嵌入式Tomcat服务器
- 配置DispatcherServlet并映射到`/`
- 配置JSON序列化（Jackson）
- 配置数据库连接池（HikariCP）
- 配置JPA/Hibernate
- 配置Spring Security
- 扫描并注册所有带注解的Bean

---

## 第三部分：Spring Security过滤器链

### 3.1 什么是过滤器链（Filter Chain）？

想象一个**机场安检流程**：

```
旅客 → 身份检查 → 行李扫描 → 金属探测 → 人工检查 → 登机口
```

Spring Security也是类似的**多层过滤器**：

```
HTTP请求 → 过滤器1 → 过滤器2 → 过滤器3 → ... → Controller
```

每个过滤器负责一个特定的安全检查，只有通过所有检查才能到达目标资源。

### 3.2 项目中的过滤器链配置

看`SpringSecurityConfiguration.java`的核心配置：

```java
@Configuration
public class SpringSecurityConfiguration {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        // ① 会话管理：无状态（不使用Session，用JWT）
        http.sessionManagement(session ->
            session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        );

        // ② 授权规则：哪些URL需要认证？
        http.authorizeHttpRequests(auth -> auth
            // 公开端点（不需要登录）
            .requestMatchers(HttpMethod.POST, "/auth-services/users").permitAll()
            .requestMatchers(HttpMethod.POST, "/auth-services/login").permitAll()
            .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()

            // 其他所有请求都需要认证
            .anyRequest().authenticated()
        );

        // ③ 启用HTTP Basic认证（备用方案）
        http.httpBasic(withDefaults());

        // ④ 禁用CSRF（因为是无状态API）
        http.csrf(AbstractHttpConfigurer::disable);

        // ⑤ 启用OAuth2资源服务器（JWT验证）
        http.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));

        return http.build();
    }
}
```

### 3.3 实际执行的过滤器顺序

当`POST /auth-services/login`请求到达时：

```
1. SecurityContextPersistenceFilter
   作用：从Session中恢复SecurityContext
   本项目：STATELESS模式，跳过
   ↓
2. LogoutFilter
   作用：处理登出请求（/logout）
   本请求：不是/logout，跳过
   ↓
3. UsernamePasswordAuthenticationFilter
   作用：处理表单登录（username/password）
   本项目：使用自定义登录逻辑，跳过
   ↓
4. AnonymousAuthenticationFilter
   作用：如果没有认证信息，创建匿名用户
   本请求：登录端点允许匿名访问
   ↓
5. ExceptionTranslationFilter
   作用：捕获安全异常（AccessDeniedException等）
   ↓
6. AuthorizationFilter
   作用：检查是否有权限访问这个URL
   检查配置：.requestMatchers(POST, "/auth-services/login").permitAll()
   结果：✅ 允许通过
   ↓
7. 放行到 DispatcherServlet → AuthController.authenticate()
```

### 3.4 关键概念1：permitAll() vs authenticated()

```java
// 公开访问（任何人都可以访问，不需要登录）
.requestMatchers(HttpMethod.POST, "/auth-services/login").permitAll()

// 需要认证（必须有有效的JWT token）
.anyRequest().authenticated()
```

**为什么登录端点要permitAll？**
- 因为用户**还没登录**，不可能有JWT token
- 登录的目的**就是获取token**
- 如果登录也需要认证，就成了"先有鸡还是先有蛋"的问题 🐔

### 3.5 关键概念2：STATELESS会话策略

```java
session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
```

**两种会话管理方式对比**：

| 特性 | Session-Based（传统） | Token-Based（JWT） |
|------|----------------------|-------------------|
| **状态存储** | 服务器Session（内存/Redis） | 客户端Token（LocalStorage） |
| **扩展性** | 差（需要Session同步） | 好（无状态，易扩展） |
| **服务器压力** | 大（存储所有Session） | 小（只验证签名） |
| **跨域** | 复杂（需要CORS配置） | 简单（只是HTTP头） |
| **微服务** | 困难（Session共享） | 容易（每个服务验证） |
| **本项目** | ❌ | ✅ STATELESS |

**Session vs JWT流程对比**：

```
传统Session方式：
登录 → 服务器创建Session → 返回SessionID（Cookie）
后续请求 → 带上SessionID → 服务器查找Session → 验证通过

JWT方式：
登录 → 服务器生成JWT → 返回Token
后续请求 → 带上Token → 服务器验证签名 → 验证通过
```

### 3.6 关键概念3：OAuth2 Resource Server

```java
http.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
```

这一行配置启用了**BearerTokenAuthenticationFilter**，它会：

1. 从请求头提取JWT：`Authorization: Bearer <token>`
2. 使用`JwtDecoder`验证JWT签名
3. 检查Token过期时间
4. 解析用户权限（scope）
5. 将用户信息放入`SecurityContext`

### 3.7 登录请求的特殊处理

对于`POST /auth-services/login`：

- ✅ 配置为`permitAll()`，允许匿名访问
- ✅ 不需要JWT token
- ✅ 直接通过所有安全过滤器
- ✅ 到达`AuthController.authenticate()`方法
- ✅ **Controller手动调用认证管理器**验证用户名密码

```java
@PostMapping("/login")
public JwtResponse authenticate(@RequestBody LoginRequest loginRequest) {
    // 手动触发认证流程（不依赖过滤器）
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            loginRequest.getUsername(),
            loginRequest.getPassword()
        )
    );
    // 认证成功后生成JWT
    return new JwtResponse(createToken(authentication));
}
```

---

## 第四部分：Controller层和请求映射

### 4.1 RequestMappingHandlerAdapter的作用

这是Spring MVC的核心组件，负责：

1. **参数解析**：把HTTP请求转换为Java对象（如`@RequestBody`）
2. **方法调用**：反射调用Controller方法
3. **返回值处理**：把Java对象转换为HTTP响应（JSON序列化）

### 4.2 Controller方法定义

看`AuthController.java`：

```java
@RestController
@RequestMapping("/auth-services")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    // 构造器注入（推荐方式）
    public AuthController(JwtEncoder jwtEncoder,
                         AuthenticationManager authenticationManager) {
        this.jwtEncoder = jwtEncoder;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public JwtResponse authenticate(@RequestBody LoginRequest loginRequest) {
        // 认证逻辑
        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );

        // 生成JWT
        return new JwtResponse(createToken(authentication));
    }
}
```

### 4.3 @RequestBody参数绑定

**HTTP请求**：
```http
POST /auth-services/login HTTP/1.1
Content-Type: application/json

{
  "username": "user123",
  "password": "password123"
}
```

**Spring处理流程**：

```
HTTP Body (JSON字符串)
    ↓
Jackson ObjectMapper解析
    ↓
创建LoginRequest对象
    ↓
调用Controller方法
```

**等价代码**：
```java
// Spring自动做了这些
String json = request.getBody(); // {"username":"user123","password":"password123"}
LoginRequest loginRequest = objectMapper.readValue(json, LoginRequest.class);
controller.authenticate(loginRequest); // 调用你的方法
```

### 4.4 LoginRequest DTO（数据传输对象）

看`LoginRequest.java`：

```java
@Data              // Lombok: 自动生成getter、setter、toString等
@AllArgsConstructor // Lombok: 生成全参数构造函数
@NoArgsConstructor  // Lombok: 生成无参构造函数（Jackson需要）
public class LoginRequest {
    private String username;
    private String password;
}
```

**Lombok魔法**：编译后等价于：

```java
public class LoginRequest {
    private String username;
    private String password;

    // 无参构造（Jackson反序列化需要）
    public LoginRequest() {}

    // 全参构造
    public LoginRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }

    // Getter
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    // Setter
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password) { this.password = password; }

    // toString
    public String toString() {
        return "LoginRequest(username=" + username + ", password=" + password + ")";
    }

    // equals、hashCode等...
}
```

### 4.5 依赖注入

**为什么Controller能使用AuthenticationManager？**

```java
// 在 SpringSecurityConfiguration.java 中定义
@Bean
public AuthenticationManager authenticationManager(...) {
    // 创建并返回
    return new ProviderManager(authenticationProvider);
}

// Spring IoC容器自动注入到Controller
public AuthController(AuthenticationManager authenticationManager) {
    this.authenticationManager = authenticationManager; // 自动注入
}
```

**依赖注入的三种方式**：

| 方式 | 示例 | 优缺点 |
|------|------|--------|
| **构造器注入**（推荐） | `public AuthController(AuthenticationManager am)` | ✅ 不可变、易测试、明确依赖 |
| **Setter注入** | `@Autowired setAuthenticationManager(...)` | ⚠️ 可变、依赖不明确 |
| **字段注入** | `@Autowired private AuthenticationManager am;` | ❌ 难测试、隐藏依赖、需要反射 |

---

## 第五部分：认证管理器和Provider

### 5.1 认证流程架构

```
AuthController (触发认证)
    ↓
AuthenticationManager (认证管理器接口)
    ↓
ProviderManager (管理多个Provider的实现类)
    ↓
DaoAuthenticationProvider (DAO认证提供者)
    ├─ UserDetailsService (加载用户)
    └─ PasswordEncoder (验证密码)
```

### 5.2 AuthenticationManager.authenticate()

看`AuthController.java`中的调用：

```java
Authentication authentication = authenticationManager.authenticate(
    new UsernamePasswordAuthenticationToken(
        loginRequest.getUsername(),  // "user123"
        loginRequest.getPassword()   // "password123"
    )
);
```

**这里发生了什么？**

**步骤1：创建未认证的Token**
```java
UsernamePasswordAuthenticationToken token =
    new UsernamePasswordAuthenticationToken(
        "user123",     // principal（主体，通常是用户名）
        "password123"  // credentials（凭证，通常是密码）
    );

// 此时
token.isAuthenticated() == false  // 未认证状态
token.getPrincipal() == "user123"
token.getCredentials() == "password123"
```

**步骤2：调用认证管理器**
```java
Authentication result = authenticationManager.authenticate(token);

// 如果认证成功
result.isAuthenticated() == true
result.getPrincipal() == CustomUserDetails对象

// 如果认证失败
抛出 AuthenticationException
  ├─ BadCredentialsException（密码错误）
  ├─ UsernameNotFoundException（用户不存在）
  ├─ AccountExpiredException（账户过期）
  └─ DisabledException（账户被禁用）
```

### 5.3 AuthenticationManager配置

看`SpringSecurityConfiguration.java`：

```java
@Bean
public AuthenticationManager authenticationManager(
    UserService userDetailsService,      // 自定义用户加载服务
    PasswordEncoder passwordEncoder      // BCrypt密码编码器
) {
    // 创建DAO认证提供者
    DaoAuthenticationProvider authenticationProvider =
        new DaoAuthenticationProvider();

    // 设置用户详情服务（从数据库加载用户）
    authenticationProvider.setUserDetailsService(userDetailsService);

    // 设置密码编码器（验证密码）
    authenticationProvider.setPasswordEncoder(passwordEncoder);

    // 创建认证管理器（可以管理多个Provider）
    return new ProviderManager(authenticationProvider);
}
```

### 5.4 DaoAuthenticationProvider内部逻辑

```java
// Spring Security内部实现（简化版）
public class DaoAuthenticationProvider
        extends AbstractUserDetailsAuthenticationProvider {

    private UserDetailsService userDetailsService;
    private PasswordEncoder passwordEncoder;

    @Override
    public Authentication authenticate(Authentication authentication)
            throws AuthenticationException {

        // 获取用户名和密码
        String username = authentication.getName();              // "user123"
        String password = authentication.getCredentials().toString(); // "password123"

        // ① 调用UserDetailsService加载用户
        UserDetails user = userDetailsService.loadUserByUsername(username);
        // 返回：CustomUserDetails对象，包含数据库中的用户信息

        // ② 检查账户状态
        if (!user.isAccountNonExpired()) {
            throw new AccountExpiredException("Account expired");
        }
        if (!user.isAccountNonLocked()) {
            throw new LockedException("Account locked");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("Account disabled");
        }

        // ③ 验证密码
        additionalAuthenticationChecks(user, authentication);
        // 内部调用：passwordEncoder.matches(password, user.getPassword())

        // ④ 检查凭证是否过期
        if (!user.isCredentialsNonExpired()) {
            throw new CredentialsExpiredException("Credentials expired");
        }

        // ⑤ 一切正常，创建已认证的Authentication对象
        return createSuccessAuthentication(user, authentication, user);
    }

    protected void additionalAuthenticationChecks(
            UserDetails user,
            UsernamePasswordAuthenticationToken authentication) {

        String presentedPassword = authentication.getCredentials().toString();

        if (!passwordEncoder.matches(presentedPassword, user.getPassword())) {
            throw new BadCredentialsException("Bad credentials");
        }
    }
}
```

### 5.5 认证流程时序图

```
Controller                AuthenticationManager    DaoAuthenticationProvider    UserDetailsService    PasswordEncoder
    |                              |                          |                         |                   |
    |--authenticate(token)-------->|                          |                         |                   |
    |                              |--authenticate()--------->|                         |                   |
    |                              |                          |--loadUserByUsername()-->|                   |
    |                              |                          |<-----UserDetails--------|                   |
    |                              |                          |                         |                   |
    |                              |                          |--matches(password)------|------------------>|
    |                              |                          |                         |<----boolean-------|
    |                              |                          |                         |                   |
    |                              |<--Authentication---------|                         |                   |
    |<--Authentication-------------|                          |                         |                   |
    |                              |                          |                         |                   |
```

### 5.6 关键概念

**Authentication接口**：
```java
public interface Authentication extends Principal {
    Collection<? extends GrantedAuthority> getAuthorities(); // 权限列表
    Object getCredentials();  // 凭证（密码），认证后会被清除
    Object getDetails();      // 额外信息（IP地址、SessionID等）
    Object getPrincipal();    // 主体（用户信息）
    boolean isAuthenticated(); // 是否已认证
}
```

**UserDetails接口**：
```java
public interface UserDetails {
    Collection<? extends GrantedAuthority> getAuthorities(); // 权限
    String getPassword();  // 密码
    String getUsername();  // 用户名
    boolean isAccountNonExpired();     // 账户是否未过期
    boolean isAccountNonLocked();      // 账户是否未锁定
    boolean isCredentialsNonExpired(); // 凭证是否未过期
    boolean isEnabled();               // 账户是否启用
}
```

---

## 第六部分：UserDetailsService和数据库查询

### 6.1 UserService实现UserDetailsService

看`UserService.java`：

```java
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username)
            throws UsernameNotFoundException {

        // ① 调用Repository查询数据库
        UserEntity userEntity = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException(
                "User not found: " + username
            ));

        // ② 包装成CustomUserDetails（实现UserDetails接口）
        return new CustomUserDetails(userEntity);
    }
}
```

### 6.2 Spring Data JPA Repository

看`UserRepository.java`：

```java
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    /**
     * 根据用户名查找用户
     * Spring Data JPA会自动实现这个方法
     */
    Optional<UserEntity> findByUsername(String username);
}
```

**Spring Data JPA的魔法**：

你只需要**声明方法签名**，Spring会根据**方法名**自动生成实现：

| 方法名 | 生成的SQL |
|--------|-----------|
| `findByUsername(String username)` | `SELECT * FROM users WHERE username = ?` |
| `findByEmail(String email)` | `SELECT * FROM users WHERE email = ?` |
| `findByUsernameAndPassword(String u, String p)` | `SELECT * FROM users WHERE username = ? AND password = ?` |
| `findByAgeGreaterThan(int age)` | `SELECT * FROM users WHERE age > ?` |

**方法名规则**：
- `findBy` / `readBy` / `queryBy` / `getBy` - 查询
- `deleteBy` / `removeBy` - 删除
- `countBy` - 计数
- `existsBy` - 判断存在
- `And` / `Or` - 连接条件
- `GreaterThan` / `LessThan` / `Like` / `Between` - 条件操作符

### 6.3 数据库实体UserEntity

看`UserEntity.java`：

```java
@Entity                    // ① JPA实体标记
@Table(name = "users")     // ② 对应的数据库表名
@Data                      // ③ Lombok：生成getter/setter
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements UserDetails {

    @Id                    // ④ 主键
    @GeneratedValue        // ⑤ 自动生成（默认AUTO策略）
    private Long id;

    private String username;       // 用户名
    private String email;          // 邮箱
    private String firstName;      // 名
    private String lastName;       // 姓
    private String password;       // 加密后的密码

    @ManyToOne                     // ⑥ 多对一关系
    @JoinColumn(name = "role_id")  // ⑦ 外键列名
    private Role role;             // 角色（USER/ADMIN等）

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;     // 位置信息

    // 实现UserDetails接口的方法
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getRoleName()));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }

    @Override
    public boolean isAccountNonLocked() { return true; }

    @Override
    public boolean isCredentialsNonExpired() { return true; }

    @Override
    public boolean isEnabled() { return true; }
}
```

### 6.4 JPA注解详解

| 注解 | 作用 | 示例 |
|------|------|------|
| `@Entity` | 标记为JPA实体类 | 对应数据库表 |
| `@Table(name = "users")` | 指定表名 | 默认用类名 |
| `@Id` | 主键字段 | `private Long id` |
| `@GeneratedValue` | 主键生成策略 | AUTO/IDENTITY/SEQUENCE/TABLE |
| `@Column(name = "...")` | 列名映射 | 字段名与列名不同时使用 |
| `@ManyToOne` | 多对一关联 | 多个用户对应一个角色 |
| `@OneToMany` | 一对多关联 | 一个用户有多个订单 |
| `@JoinColumn` | 外键列 | `name = "role_id"` |
| `@Enumerated` | 枚举类型 | `EnumType.STRING`/`ORDINAL` |
| `@Temporal` | 日期时间类型 | `TemporalType.DATE`/`TIME`/`TIMESTAMP` |

### 6.5 Hibernate生成的SQL

当调用`userRepository.findByUsername("user123")`时：

**基础查询**（如果没有EAGER关联）：
```sql
SELECT
    u.id,
    u.username,
    u.email,
    u.first_name,
    u.last_name,
    u.password,
    u.role_id,
    u.location_id
FROM users u
WHERE u.username = 'user123';
```

**完整查询**（因为配置了`FetchType.EAGER`）：
```sql
SELECT
    u.id AS user_id,
    u.username,
    u.email,
    u.first_name,
    u.last_name,
    u.password,
    -- 关联查询role
    r.id AS role_id,
    r.role_name,
    -- 关联查询location
    l.id AS location_id,
    l.place,
    l.longitude,
    l.latitude
FROM users u
LEFT JOIN roles r ON u.role_id = r.id
LEFT JOIN locations l ON u.location_id = l.id
WHERE u.username = 'user123';
```

### 6.6 查询执行流程

```
userRepository.findByUsername("user123")
    ↓
Spring Data JPA代理对象
    ↓
Hibernate Session
    ↓
生成SQL语句
    ↓
PostgreSQL JDBC驱动
    ↓
数据库执行查询
    ↓
返回ResultSet
    ↓
Hibernate映射为UserEntity对象
    {
        id: 1,
        username: "user123",
        email: "user123@example.com",
        password: "$2a$10$...",
        role: Role { id: 1, roleName: "USER" },
        location: Location { id: 1, place: "New York", ... }
    }
    ↓
返回Optional<UserEntity>
```

### 6.7 CustomUserDetails包装类

为什么要包装UserEntity？

```java
public class CustomUserDetails implements UserDetails {
    private final UserEntity user;

    public CustomUserDetails(UserEntity user) {
        this.user = user;
    }

    // ① 添加额外的便捷方法
    public Long getId() {
        return user.getId();
    }

    public String getEmail() {
        return user.getEmail();
    }

    // ② 实现UserDetails接口
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        // 注意：这里加了"ROLE_"前缀（Spring Security约定）
        return List.of(
            new SimpleGrantedAuthority("ROLE_" + user.getRole().getRoleName())
        );
    }

    // ... 其他方法
}
```

**包装的好处**：
1. **职责分离**：Entity负责持久化，UserDetails负责安全
2. **额外方法**：可以添加`getId()`、`getEmail()`等方便的方法
3. **自定义权限**：可以自定义`getAuthorities()`的逻辑
4. **安全隔离**：不直接暴露Entity到Security层

---

## 第七部分：密码验证机制

### 7.1 additionalAuthenticationChecks()

这是`DaoAuthenticationProvider`的内部方法：

```java
// Spring Security源码（简化）
protected void additionalAuthenticationChecks(
    UserDetails userDetails,                          // 从数据库加载的用户
    UsernamePasswordAuthenticationToken authentication // 用户提交的认证信息
) throws AuthenticationException {

    // ① 获取用户提交的明文密码
    String presentedPassword = authentication.getCredentials().toString();
    // presentedPassword = "password123"

    // ② 获取数据库中的加密密码
    String encodedPassword = userDetails.getPassword();
    // encodedPassword = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"

    // ③ 使用PasswordEncoder验证
    if (!passwordEncoder.matches(presentedPassword, encodedPassword)) {
        throw new BadCredentialsException("Bad credentials");
    }
}
```

### 7.2 BCryptPasswordEncoder配置

看`SpringSecurityConfiguration.java`：

```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

### 7.3 BCrypt工作原理

#### 注册时：加密密码

看`UserService.java`中的用户创建：

```java
public UserEntity createUser(CreateUserRequest request) {
    BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    UserEntity userEntity = new UserEntity();
    userEntity.setUsername(request.getUsername());
    // 加密密码
    userEntity.setPassword(passwordEncoder.encode(request.getPassword()));
    // ...

    return userRepository.save(userEntity);
}
```

**加密过程**：
```java
原始密码: "password123"
    ↓
BCrypt.encode("password123")
    ↓
生成随机salt（盐值）
    ↓
使用salt和cost factor进行多轮hash
    ↓
组合为最终哈希
    ↓
加密结果: "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
```

#### BCrypt哈希结构

```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
 │  │  │                    │                              │
 │  │  │                    │                              └─ 哈希值（31字符）
 │  │  │                    └─ Salt（盐值，22字符）
 │  │  └─ Cost factor（工作因子）10 = 2^10 = 1024次迭代
 │  └─ Minor version
 └─ 算法版本（2a）
```

**关键参数**：
- **Algorithm**: `2a`（BCrypt算法版本）
- **Cost Factor**: `10`（默认值，每增加1，计算时间翻倍）
  - Cost=10: ~0.1秒
  - Cost=12: ~0.4秒
  - Cost=14: ~1.6秒
- **Salt**: 随机生成，每次加密都不同
- **Hash**: 最终的密码哈希值

#### 登录时：验证密码

```java
boolean matches = passwordEncoder.matches(
    "password123",  // ① 用户输入的明文密码
    "$2a$10$N9qo..." // ② 数据库中的加密密码
);

// BCrypt内部流程：
// 1. 从加密密码中提取salt和cost factor
// 2. 使用相同的salt和cost factor对明文密码加密
// 3. 比较两个哈希值是否相同
```

**验证流程**：
```java
用户输入: "password123"
数据库哈希: "$2a$10$N9qo...Wy"
    ↓
提取salt: "N9qo8uLOickgx2ZMRZoMye"
提取cost: 10
    ↓
用相同的salt和cost加密用户输入
    ↓
生成新哈希: "$2a$10$N9qo...Wy"
    ↓
比较两个哈希
    ↓
相同 → 密码正确 ✅
不同 → 密码错误 ❌
```

### 7.4 为什么BCrypt比MD5/SHA更安全？

| 特性 | MD5/SHA | BCrypt |
|------|---------|--------|
| **速度** | 极快（每秒数百万次） | 慢（可调节，每秒几千次） |
| **盐值** | 需要手动生成和存储 | 自动生成并嵌入哈希中 |
| **彩虹表攻击** | 容易（预计算哈希表） | 困难（每个密码有不同salt） |
| **暴力破解** | 容易（GPU加速） | 困难（专门设计抵抗GPU） |
| **适应性** | 固定（硬件升级需改算法） | 可调整cost factor |
| **专业性** | 通用哈希算法 | 专门为密码设计 |

**破解难度对比**（8位纯数字密码）：

```
硬件：高性能GPU

MD5速度：1000亿次/秒
破解时间：100,000,000 ÷ 100,000,000,000 = 0.001秒 ⚠️

BCrypt (cost=10)速度：约5000次/秒
破解时间：100,000,000 ÷ 5000 = 20,000秒 ≈ 5.5小时 ✅

BCrypt (cost=12)速度：约1250次/秒
破解时间：100,000,000 ÷ 1250 = 80,000秒 ≈ 22小时 ✅✅
```

### 7.5 BCrypt vs Argon2 vs PBKDF2

| 算法 | 优点 | 缺点 | 推荐度 |
|------|------|------|--------|
| **BCrypt** | 成熟稳定、广泛使用、抗GPU | 内存使用固定 | ⭐⭐⭐⭐ |
| **Argon2** | 最新标准、抗GPU+ASIC、可配置内存 | 相对较新 | ⭐⭐⭐⭐⭐ |
| **PBKDF2** | 标准算法、简单 | 容易GPU加速 | ⭐⭐⭐ |
| **scrypt** | 抗GPU、高内存 | 实现复杂 | ⭐⭐⭐⭐ |

**本项目使用BCrypt**的原因：
- ✅ Spring Security原生支持
- ✅ 成熟稳定，经过时间考验
- ✅ 配置简单，开箱即用
- ✅ 安全性足够（对于大多数应用）

### 7.6 认证成功后的流程

```java
// DaoAuthenticationProvider
if (passwordEncoder.matches(presentedPassword, userDetails.getPassword())) {
    // 密码验证成功

    // 创建已认证的Authentication对象
    UsernamePasswordAuthenticationToken result =
        new UsernamePasswordAuthenticationToken(
            userDetails,              // principal（用户信息）
            null,                     // credentials（密码清空，安全考虑）
            userDetails.getAuthorities() // 权限列表
        );
    result.setAuthenticated(true);    // 标记为已认证

    return result;
}
```

**返回的Authentication对象**：
```java
UsernamePasswordAuthenticationToken {
    principal: CustomUserDetails {
        username: "user123",
        password: "$2a$10$...",  // 注意：这里仍保留（UserDetails需要）
        authorities: [ROLE_USER]
    },
    credentials: null,  // ⚠️ 密码已清空（安全）
    authenticated: true, // ✅ 已认证
    authorities: [ROLE_USER],
    details: WebAuthenticationDetails {
        remoteAddress: "192.168.1.100",
        sessionId: null
    }
}
```

---

## 第八部分：JWT生成和签名

### 8.1 构建JWT Claims

认证成功后，Controller生成JWT：

看`AuthController.java`：

```java
@PostMapping("/login")
public JwtResponse authenticate(@RequestBody LoginRequest loginRequest) {
    // ① 认证用户（已讲解）
    Authentication authentication = authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(
            loginRequest.getUsername(),
            loginRequest.getPassword()
        )
    );

    // ② 生成JWT token
    String token = createToken(authentication);

    // ③ 返回给客户端
    return new JwtResponse(token);
}

private String createToken(Authentication authentication) {
    // 从Authentication中提取用户信息
    CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

    // 构建JWT Claims（载荷/声明）
    JwtClaimsSet claims = JwtClaimsSet.builder()
        .issuer("self")                               // 签发者
        .issuedAt(Instant.now())                      // 签发时间
        .expiresAt(Instant.now().plusSeconds(60 * 30)) // 过期时间（30分钟）
        .subject(authentication.getName())             // 主题（用户名）
        .claim("user_id", user.getId())               // 自定义：用户ID
        .claim("email", user.getEmail())              // 自定义：邮箱
        .claim("scope", createScope(authentication))  // 自定义：权限范围
        .build();

    // 使用JwtEncoder编码并签名
    return jwtEncoder.encode(JwtEncoderParameters.from(claims))
                     .getTokenValue();
}

private String createScope(Authentication authentication) {
    // 提取所有权限，用空格连接
    return authentication.getAuthorities()
                        .stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.joining(" "));
    // 例如："ROLE_USER" 或 "ROLE_USER ROLE_ADMIN"
}
```

### 8.2 JWT结构详解

JWT由三部分组成，用`.`（点）分隔：

```
<Header>.<Payload>.<Signature>
```

#### Part 1: Header（头部）

```json
{
  "alg": "RS256",     // 签名算法：RSA + SHA-256
  "typ": "JWT"        // 令牌类型
}
```

**Base64URL编码后**：
```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9
```

#### Part 2: Payload（载荷/声明）

```json
{
  "iss": "self",                           // issuer（签发者）
  "iat": 1640000000,                       // issued at（签发时间，Unix时间戳）
  "exp": 1640001800,                       // expires（过期时间，30分钟后）
  "sub": "user123",                        // subject（主题，通常是用户名）
  "user_id": 1,                            // 自定义字段
  "email": "user123@example.com",          // 自定义字段
  "scope": "ROLE_USER"                     // 权限范围
}
```

**标准声明（Registered Claims）**：

| 声明 | 全称 | 说明 |
|------|------|------|
| `iss` | Issuer | 签发者 |
| `sub` | Subject | 主题（用户标识） |
| `aud` | Audience | 接收方 |
| `exp` | Expiration Time | 过期时间（必须） |
| `nbf` | Not Before | 生效时间 |
| `iat` | Issued At | 签发时间 |
| `jti` | JWT ID | JWT唯一标识 |

**Base64URL编码后**：
```
eyJpc3MiOiJzZWxmIiwiaWF0IjoxNjQwMDAwMDAwLCJleHAiOjE2NDAwMDE4MDAsInN1YiI6InVzZXIxMjMiLCJ1c2VyX2lkIjoxLCJlbWFpbCI6InVzZXIxMjNAZXhhbXBsZS5jb20iLCJzY29wZSI6IlJPTEVfVVNFUiJ9
```

#### Part 3: Signature（签名）

```
RSASHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  privateKey  // 使用RSA私钥签名
)
```

**签名的作用**：
- ✅ 验证JWT未被篡改
- ✅ 验证签发者身份
- ❌ 不提供加密（Payload是Base64编码，可解码）

**Base64URL编码后**：
```
qwertyuiopasdfghjklzxcvbnm1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ
```

#### 完整JWT示例

```
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzZWxmIiwiaWF0IjoxNjQwMDAwMDAwLCJleHAiOjE2NDAwMDE4MDAsInN1YiI6InVzZXIxMjMiLCJ1c2VyX2lkIjoxLCJlbWFpbCI6InVzZXIxMjNAZXhhbXBsZS5jb20iLCJzY29wZSI6IlJPTEVfVVNFUiJ9.qwertyuiopasdfghjklzxcvbnm1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ
    │                                                           │                                                                                                                                                                                                                    │
  Header                                                      Payload                                                                                                                                                                                                            Signature
```

### 8.3 RSA密钥配置

看`SpringSecurityConfiguration.java`：

```java
// ① 加载RSA密钥对
@Bean
public RSAKey rsaKey() throws Exception {
    // 从PEM文件加载公钥和私钥
    RSAPublicKey publicKey = PemUtils.loadPublicKey("keys/public.pem");
    RSAPrivateKey privateKey = PemUtils.loadPrivateKey("keys/private.pem");

    // 构建RSAKey对象
    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())  // 密钥ID（可选）
        .build();
}

// ② 创建JWK Source（JSON Web Key源）
@Bean
public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
    JWKSet jwkSet = new JWKSet(rsaKey);
    return (selector, context) -> selector.select(jwkSet);
}

// ③ 创建JWT编码器（用于签名）
@Bean
JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
}

// ④ 创建JWT解码器（用于验证）
@Bean
public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
    return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
}
```

### 8.4 生成RSA密钥对

```bash
# 创建密钥目录
mkdir -p src/main/resources/keys

# 生成2048位RSA私钥
openssl genrsa -out src/main/resources/keys/private.pem 2048

# 从私钥提取公钥
openssl rsa -in src/main/resources/keys/private.pem \
            -pubout \
            -out src/main/resources/keys/public.pem
```

**private.pem**（私钥，保密）：
```
-----BEGIN PRIVATE KEY-----
MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC...
...（省略数百行）...
-----END PRIVATE KEY-----
```

**public.pem**（公钥，可公开）：
```
-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvL8w...
...（省略数十行）...
-----END PUBLIC KEY-----
```

### 8.5 RSA vs HMAC对比

| 特性 | HMAC (HS256) | RSA (RS256) |
|------|--------------|-------------|
| **密钥类型** | 对称密钥（一个密钥） | 非对称密钥（公钥+私钥） |
| **签名** | 使用共享密钥 | 使用**私钥**签名 |
| **验证** | 使用共享密钥 | 使用**公钥**验证 |
| **安全性** | 密钥泄露=完全失效 | 私钥泄露才失效，公钥可公开 |
| **密钥分发** | 需要安全传输密钥 | 只需分发公钥 |
| **性能** | 快（对称加密） | 慢（非对称加密） |
| **微服务架构** | 所有服务需要共享密钥 | 只需分发公钥 |
| **本项目** | ❌ | ✅ |

**微服务架构中RSA的优势**：

```
Auth Service（认证服务）
    拥有：私钥（private.pem）
    功能：生成JWT（签名）
    ↓
    ↓ 只需分发公钥
    ↓
User Service（用户服务）
    拥有：公钥（public.pem）
    功能：验证JWT

Payment Service（支付服务）
    拥有：公钥（public.pem）
    功能：验证JWT

Order Service（订单服务）
    拥有：公钥（public.pem）
    功能：验证JWT
```

**如果使用HMAC**：
- ❌ 所有服务都需要共享密钥
- ❌ 任何服务都能生成JWT（安全隐患）
- ❌ 密钥泄露需要更新所有服务

### 8.6 JWT编码流程

```java
// Spring Security + Nimbus JOSE库实现
public Jwt encode(JwtEncoderParameters parameters) {
    JwtClaimsSet claims = parameters.getClaims();

    // ① 创建Header
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
        .type(JOSEObjectType.JWT)
        .build();

    // ② 创建Payload
    Payload payload = new Payload(claims.toJSONObject());

    // ③ 组合为未签名的JWT
    JWSObject jwsObject = new JWSObject(header, payload);

    // ④ 使用RSA私钥签名
    JWSSigner signer = new RSASSASigner(privateKey);
    jwsObject.sign(signer);

    // ⑤ 序列化为字符串
    String token = jwsObject.serialize();
    // token = "header.payload.signature"

    return new Jwt(token, claims.getIssuedAt(), claims.getExpiresAt(),
                   header.toJSONObject(), claims.getClaims());
}
```

**详细步骤**：

```
Step 1: 创建Header
{
  "alg": "RS256",
  "typ": "JWT"
}
    ↓ Base64URL编码
"eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9"

Step 2: 创建Payload
{
  "iss": "self",
  "sub": "user123",
  "exp": 1640001800,
  ...
}
    ↓ Base64URL编码
"eyJpc3MiOiJzZWxmIiwic3ViIjoidXNlcjEyMyIsImV4cCI6MTY0MDAwMTgwMH0"

Step 3: 组合待签名数据
encodedHeader + "." + encodedPayload

Step 4: 使用RSA-SHA256签名
signature = RSA_SHA256_Sign(
    "eyJhbG...9.eyJpc3M...",
    privateKey
)
    ↓ Base64URL编码
"qwertyuiop..."

Step 5: 组合最终JWT
header + "." + payload + "." + signature
```

### 8.7 返回JWT给客户端

```java
return new JwtResponse(token);
```

**HTTP响应**：
```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzZWxmIi..."
}
```

**客户端处理**：
```javascript
// 浏览器端代码
fetch('/auth-services/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'user123', password: 'password123' })
})
.then(res => res.json())
.then(data => {
    // 保存token到LocalStorage
    localStorage.setItem('jwt_token', data.token);
});

// 后续请求带上token
fetch('/auth-services/users', {
    headers: {
        'Authorization': 'Bearer ' + localStorage.getItem('jwt_token')
    }
})
.then(res => res.json())
.then(users => console.log(users));
```

---

## 第九部分：受保护端点的JWT验证流程

### 9.1 客户端请求示例

```http
GET /auth-services/users HTTP/1.1
Host: localhost:8081
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 9.2 Spring Security过滤器链处理

```
HTTP请求 → SecurityFilterChain
    ↓
1. SecurityContextPersistenceFilter
   作用：恢复SecurityContext
   本项目：STATELESS，跳过
   ↓
2. LogoutFilter
   作用：处理/logout
   本请求：不是登出，跳过
   ↓
3. BearerTokenAuthenticationFilter ← 🔥 重点！
   作用：提取并验证JWT
   检测到：Authorization: Bearer <token>
   ↓
4. 提取JWT token
   ↓
5. 调用JwtDecoder.decode()验证
   ↓
6. 创建JwtAuthenticationToken
   ↓
7. 设置到SecurityContext
   ↓
8. ExceptionTranslationFilter
   作用：处理安全异常
   ↓
9. AuthorizationFilter
   作用：检查权限
   检查：anyRequest().authenticated()
   结果：✅ SecurityContext中有认证信息
   ↓
10. 放行到Controller
```

### 9.3 BearerTokenAuthenticationFilter详解

这个过滤器由以下配置启用：

```java
http.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
```

**内部逻辑**（Spring Security实现）：

```java
public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private JwtDecoder jwtDecoder;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // ① 从请求头提取token
        String token = extractToken(request);

        if (token == null) {
            // 没有token，继续过滤器链（可能是公开端点）
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // ② 验证JWT
            Jwt jwt = jwtDecoder.decode(token);

            // ③ 创建Authentication对象
            JwtAuthenticationToken authentication =
                new JwtAuthenticationToken(
                    jwt,
                    extractAuthorities(jwt),
                    jwt.getSubject()
                );

            // ④ 设置到SecurityContext
            SecurityContextHolder.getContext()
                                .setAuthentication(authentication);

            // ⑤ 继续过滤器链
            filterChain.doFilter(request, response);

        } catch (JwtException e) {
            // JWT验证失败
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("Invalid JWT token");
        }
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        // header = "Bearer eyJhbGciOiJSUzI1NiIs..."

        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);  // 去掉 "Bearer " 前缀（7个字符）
        }
        return null;
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        // 从JWT的scope声明提取权限
        String scope = jwt.getClaimAsString("scope");
        // scope = "ROLE_USER" 或 "ROLE_USER ROLE_ADMIN"

        if (scope == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(scope.split(" "))
                     .map(SimpleGrantedAuthority::new)
                     .collect(Collectors.toList());
        // 返回：[ROLE_USER]
    }
}
```

### 9.4 JwtDecoder验证流程

```java
@Bean
public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
    return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
}
```

**验证过程**（Nimbus库实现）：

```java
public Jwt decode(String token) throws JwtException {

    // ① 拆分JWT
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
        throw new JwtException("Invalid JWT format");
    }

    String encodedHeader = parts[0];
    String encodedPayload = parts[1];
    String encodedSignature = parts[2];

    // ② 解码Header
    String headerJson = base64UrlDecode(encodedHeader);
    JWSHeader header = JWSHeader.parse(headerJson);

    // ③ 验证算法
    if (!header.getAlgorithm().equals(JWSAlgorithm.RS256)) {
        throw new JwtException("Unsupported algorithm: " + header.getAlgorithm());
    }

    // ④ 验证签名
    String signingInput = encodedHeader + "." + encodedPayload;
    byte[] signatureBytes = base64UrlDecode(encodedSignature);

    boolean valid = RSA_SHA256_Verify(
        signingInput.getBytes(),
        signatureBytes,
        publicKey  // 使用公钥验证
    );

    if (!valid) {
        throw new JwtValidationException("Invalid JWT signature");
    }

    // ⑤ 解析Payload
    String payloadJson = base64UrlDecode(encodedPayload);
    JWTClaimsSet claims = JWTClaimsSet.parse(payloadJson);

    // ⑥ 验证过期时间
    Date expirationTime = claims.getExpirationTime();
    if (expirationTime != null && expirationTime.before(new Date())) {
        throw new JwtValidationException("JWT expired");
    }

    // ⑦ 验证生效时间（如果有）
    Date notBefore = claims.getNotBeforeTime();
    if (notBefore != null && notBefore.after(new Date())) {
        throw new JwtValidationException("JWT not yet valid");
    }

    // ⑧ 返回Jwt对象
    return new Jwt(
        token,
        claims.getIssueTime().toInstant(),
        expirationTime.toInstant(),
        header.toJSONObject(),
        claims.getClaims()
    );
}
```

**RSA签名验证原理**：

```
签名时（Auth Service）：
    signature = RSA_Encrypt(hash(data), privateKey)

验证时（其他Service）：
    hash1 = hash(data)                      // 重新计算哈希
    hash2 = RSA_Decrypt(signature, publicKey) // 用公钥解密签名

    if (hash1 == hash2) {
        ✅ 签名有效（数据未被篡改，且确实由私钥持有者签发）
    } else {
        ❌ 签名无效
    }
```

### 9.5 验证成功后的Jwt对象

```java
Jwt {
    tokenValue: "eyJhbGciOiJSUzI1NiIs...",

    issuedAt: 2023-12-20T10:00:00Z,
    expiresAt: 2023-12-20T10:30:00Z,

    headers: {
        "alg": "RS256",
        "typ": "JWT"
    },

    claims: {
        "iss": "self",
        "sub": "user123",
        "user_id": 1,
        "email": "user123@example.com",
        "scope": "ROLE_USER",
        "iat": 1640000000,
        "exp": 1640001800
    }
}
```

### 9.6 创建JwtAuthenticationToken

```java
// Spring Security自动创建
JwtAuthenticationToken authentication = new JwtAuthenticationToken(
    jwt,                          // JWT对象
    extractAuthorities(jwt),      // 从scope提取的权限列表
    jwt.getSubject()              // principal（用户名："user123"）
);

// 设置到SecurityContext（ThreadLocal线程变量）
SecurityContextHolder.getContext().setAuthentication(authentication);
```

**JwtAuthenticationToken内容**：
```java
JwtAuthenticationToken {
    principal: "user123",           // 用户名
    credentials: jwt,               // 完整的JWT对象
    authorities: [                  // 权限列表
        SimpleGrantedAuthority("ROLE_USER")
    ],
    authenticated: true,            // 已认证
    details: null
}
```

### 9.7 AuthorizationFilter检查权限

```java
// 检查配置规则
http.authorizeHttpRequests(auth -> auth
    .requestMatchers(POST, "/auth-services/login").permitAll()
    .anyRequest().authenticated()  ← 检查这个规则
);

// AuthorizationFilter内部
Authentication authentication = SecurityContextHolder.getContext()
                                                     .getAuthentication();

boolean isAuthenticated = authentication != null
                       && authentication.isAuthenticated();

if (!isAuthenticated) {
    throw new AccessDeniedException("Full authentication required");
}

// ✅ 验证通过，放行
```

### 9.8 Controller处理请求

看`UserController.java`：

```java
@RestController
@RequestMapping("/auth-services")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")  // 受保护的端点
    public List<UserDTO> getAllUsers() {
        // 此时SecurityContext中有认证信息
        Authentication auth = SecurityContextHolder.getContext()
                                                   .getAuthentication();
        String username = auth.getName(); // "user123"

        // 查询所有用户
        return userService.getAllUsers();
    }
}
```

### 9.9 Service层处理

```java
@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                            .stream()
                            .map(this::convertToDTO)
                            .collect(Collectors.toList());
    }

    private UserDTO convertToDTO(UserEntity user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFirstName(user.getFirstName());
        dto.setLastName(user.getLastName());
        dto.setRole(RoleEnum.valueOf(user.getRole().getRoleName()));
        dto.setLocation(user.getLocation().getPlace());
        return dto;
    }
}
```

### 9.10 返回JSON响应

```http
HTTP/1.1 200 OK
Content-Type: application/json

[
  {
    "id": 1,
    "username": "user123",
    "email": "user123@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "role": "USER",
    "location": "New York"
  },
  {
    "id": 2,
    "username": "admin",
    "email": "admin@example.com",
    "firstName": "Admin",
    "lastName": "User",
    "role": "ADMIN",
    "location": "San Francisco"
  }
]
```

### 9.11 完整对比：登录 vs 访问受保护端点

| 阶段 | 登录请求 | 受保护端点 |
|------|----------|-----------|
| **URL** | `POST /auth-services/login` | `GET /auth-services/users` |
| **Authorization头** | 无 | `Bearer <JWT>` |
| **Security配置** | `permitAll()` | `authenticated()` |
| **过滤器处理** | 直接放行 | `BearerTokenAuthenticationFilter`验证JWT |
| **认证方式** | 手动调用`authenticationManager` | 自动从JWT提取用户信息 |
| **SecurityContext** | 空（登录前无认证） | `JwtAuthenticationToken` |
| **数据库查询** | 查询用户验证密码 | 不查询（JWT已包含信息） |
| **返回内容** | JWT token | 业务数据（用户列表） |

---

## 核心组件配置清单

### 1. 应用程序入口

**文件**：`AuthServiceApplication.java`

```java
@SpringBootApplication  // = @Configuration + @EnableAutoConfiguration + @ComponentScan
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

### 2. Security配置

**文件**：`SpringSecurityConfiguration.java`

#### a) 过滤器链
```java
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    return http
        .sessionManagement(s -> s.sessionCreationPolicy(STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(POST, "/auth-services/login").permitAll()
            .requestMatchers(POST, "/auth-services/users").permitAll()
            .anyRequest().authenticated()
        )
        .csrf(AbstractHttpConfigurer::disable)
        .httpBasic(withDefaults())
        .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()))
        .build();
}
```

#### b) 认证管理器
```java
@Bean
public AuthenticationManager authenticationManager(
    UserService userDetailsService,
    PasswordEncoder passwordEncoder
) {
    DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    provider.setUserDetailsService(userDetailsService);
    provider.setPasswordEncoder(passwordEncoder);
    return new ProviderManager(provider);
}
```

#### c) 密码编码器
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```

#### d) JWT密钥和编解码器
```java
@Bean
public RSAKey rsaKey() throws Exception {
    RSAPublicKey publicKey = PemUtils.loadPublicKey("keys/public.pem");
    RSAPrivateKey privateKey = PemUtils.loadPrivateKey("keys/private.pem");
    return new RSAKey.Builder(publicKey)
        .privateKey(privateKey)
        .keyID(UUID.randomUUID().toString())
        .build();
}

@Bean
public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
    return (selector, context) -> selector.select(new JWKSet(rsaKey));
}

@Bean
public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
    return new NimbusJwtEncoder(jwkSource);
}

@Bean
public JwtDecoder jwtDecoder(RSAKey rsaKey) throws JOSEException {
    return NimbusJwtDecoder.withPublicKey(rsaKey.toRSAPublicKey()).build();
}
```

### 3. Controller层

**文件**：`AuthController.java`

```java
@RestController
@RequestMapping("/auth-services")
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtEncoder jwtEncoder;

    public AuthController(JwtEncoder jwtEncoder,
                         AuthenticationManager authenticationManager) {
        this.jwtEncoder = jwtEncoder;
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public JwtResponse authenticate(@RequestBody LoginRequest loginRequest) {
        Authentication auth = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(
                loginRequest.getUsername(),
                loginRequest.getPassword()
            )
        );
        return new JwtResponse(createToken(auth));
    }

    private String createToken(Authentication authentication) {
        CustomUserDetails user = (CustomUserDetails) authentication.getPrincipal();

        var claims = JwtClaimsSet.builder()
            .issuer("self")
            .issuedAt(Instant.now())
            .expiresAt(Instant.now().plusSeconds(60 * 30))
            .subject(authentication.getName())
            .claim("user_id", user.getId())
            .claim("email", user.getEmail())
            .claim("scope", createScope(authentication))
            .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(claims))
                        .getTokenValue();
    }

    private String createScope(Authentication authentication) {
        return authentication.getAuthorities()
                           .stream()
                           .map(GrantedAuthority::getAuthority)
                           .collect(Collectors.joining(" "));
    }
}
```

**文件**：`UserController.java`

```java
@RestController
@RequestMapping("/auth-services")
public class UserController {

    @Autowired
    private UserService userService;

    @GetMapping("/users")
    public List<UserDTO> getAllUsers() {
        return userService.getAllUsers();
    }

    @PostMapping("/users")
    public ResponseEntity<UserDTO> createUser(@RequestBody CreateUserRequest request) {
        UserEntity user = userService.createUser(request);
        // ... 转换并返回
    }
}
```

### 4. Service层

**文件**：`UserService.java`

```java
@Service
public class UserService implements UserDetailsService {

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        UserEntity user = userRepository.findByUsername(username)
            .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new CustomUserDetails(user);
    }

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll()
                           .stream()
                           .map(this::convertToDTO)
                           .collect(Collectors.toList());
    }

    public UserEntity createUser(CreateUserRequest request) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UserEntity user = new UserEntity();
        user.setUsername(request.getUsername());
        user.setPassword(encoder.encode(request.getPassword()));
        // ... 设置其他字段
        return userRepository.save(user);
    }
}
```

**文件**：`CustomUserDetails.java`

```java
public class CustomUserDetails implements UserDetails {
    private final UserEntity user;

    public CustomUserDetails(UserEntity user) {
        this.user = user;
    }

    public Long getId() { return user.getId(); }
    public String getEmail() { return user.getEmail(); }

    @Override
    public String getUsername() { return user.getUsername(); }

    @Override
    public String getPassword() { return user.getPassword(); }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(
            "ROLE_" + user.getRole().getRoleName()
        ));
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return true; }
}
```

### 5. Repository层

**文件**：`UserRepository.java`

```java
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
}
```

### 6. Entity层

**文件**：`UserEntity.java`

```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements UserDetails {

    @Id
    @GeneratedValue
    private Long id;

    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private String password;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id")
    private Location location;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.getRoleName()));
    }

    // ... 其他UserDetails方法
}
```

### 7. 配置文件

**文件**：`application.yml`

```yaml
server:
  port: 8081

spring:
  application:
    name: auth-service

  datasource:
    url: jdbc:postgresql://localhost:5432/finpay
    username: finpay
    password: finpay

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect

  security:
    oauth2:
      authorizationserver:
        jwt:
          private-key-location: classpath:keys/private.pem
          public-key-location: classpath:keys/public.pem
```

### 8. Maven依赖

**文件**：`pom.xml`

```xml
<dependencies>
    <!-- Spring Boot Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <!-- Spring Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>

    <!-- OAuth2 Resource Server (JWT) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
    </dependency>

    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

---

## 最佳实践

### 1. 安全性

#### ✅ 推荐做法

- **密码加密**：使用BCrypt（cost=10以上）
- **JWT签名**：使用RSA而非HMAC（微服务架构）
- **Token过期**：设置合理过期时间（建议15-30分钟）
- **HTTPS**：生产环境必须使用HTTPS
- **密钥管理**：使用环境变量或密钥管理服务（AWS KMS、Azure Key Vault）
- **最小权限**：只授予必要的权限

#### ❌ 禁止做法

- 在日志中打印密码或Token
- 把私钥提交到Git仓库
- 使用弱密码策略
- JWT存储敏感信息（如完整信用卡号）
- 无限期的Token

### 2. 架构设计

#### ✅ 推荐做法

- **无状态会话**：使用JWT，避免服务器Session
- **分层架构**：Controller → Service → Repository
- **DTO模式**：不直接暴露Entity
- **构造器注入**：优于字段注入

```java
// ✅ 推荐：构造器注入
public class UserService {
    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }
}

// ❌ 不推荐：字段注入
public class UserService {
    @Autowired
    private UserRepository userRepository;
}
```

### 3. 错误处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(
            UsernameNotFoundException ex) {
        // ❌ 不要暴露"用户不存在"（安全风险）
        // return new ErrorResponse("User not found: " + username);

        // ✅ 推荐：统一提示
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("Invalid username or password"));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(
            BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("Invalid username or password"));
    }

    @ExceptionHandler(JwtException.class)
    public ResponseEntity<ErrorResponse> handleJwtException(
            JwtException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .body(new ErrorResponse("Invalid or expired token"));
    }
}
```

### 4. 日志

```java
@Slf4j  // Lombok
public class AuthController {

    @PostMapping("/login")
    public JwtResponse authenticate(@RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.getUsername());

        try {
            Authentication auth = authenticationManager.authenticate(...);
            log.info("Login successful for user: {}", request.getUsername());
            return new JwtResponse(createToken(auth));
        } catch (BadCredentialsException ex) {
            log.warn("Failed login attempt for user: {}", request.getUsername());
            throw ex;
        }
    }
}
```

### 5. JWT最佳实践

#### Token过期时间

```java
// 短期Access Token（15-30分钟）
.expiresAt(Instant.now().plusSeconds(60 * 15))  // 15分钟

// 长期Refresh Token（7-30天）
.expiresAt(Instant.now().plusSeconds(60 * 60 * 24 * 7))  // 7天
```

#### Refresh Token流程

```java
@PostMapping("/refresh")
public JwtResponse refresh(@RequestBody RefreshTokenRequest request) {
    // 验证Refresh Token
    Jwt refreshToken = jwtDecoder.decode(request.getRefreshToken());

    // 检查是否是Refresh Token
    String tokenType = refreshToken.getClaimAsString("token_type");
    if (!"refresh".equals(tokenType)) {
        throw new InvalidTokenException("Not a refresh token");
    }

    // 生成新的Access Token
    String newAccessToken = createAccessToken(refreshToken.getSubject());

    return new JwtResponse(newAccessToken);
}
```

### 6. 数据库优化

```java
// ✅ 使用索引
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_username", columnList = "username", unique = true),
    @Index(name = "idx_email", columnList = "email", unique = true)
})
public class UserEntity {
    // ...
}

// ✅ 避免N+1查询
@ManyToOne(fetch = FetchType.LAZY)  // 懒加载
private Role role;

// 需要时使用JOIN FETCH
@Query("SELECT u FROM UserEntity u JOIN FETCH u.role WHERE u.username = :username")
Optional<UserEntity> findByUsernameWithRole(@Param("username") String username);
```

### 7. 测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldAuthenticateValidUser() throws Exception {
        mockMvc.perform(post("/auth-services/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"password123\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void shouldRejectInvalidCredentials() throws Exception {
        mockMvc.perform(post("/auth-services/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"test\",\"password\":\"wrong\"}"))
            .andExpect(status().isUnauthorized());
    }
}
```

---

## 从零开始构建项目

### 步骤1：创建Spring Boot项目

使用Spring Initializr创建项目：

```bash
curl https://start.spring.io/starter.zip \
  -d dependencies=web,security,data-jpa,postgresql,lombok,oauth2-resource-server \
  -d type=maven-project \
  -d javaVersion=17 \
  -d bootVersion=3.2.0 \
  -d groupId=com.finpay \
  -d artifactId=auth-service \
  -o auth-service.zip

unzip auth-service.zip
cd auth-service
```

或访问：https://start.spring.io/

### 步骤2：生成RSA密钥

```bash
# 创建密钥目录
mkdir -p src/main/resources/keys

# 生成2048位RSA私钥
openssl genrsa -out src/main/resources/keys/private.pem 2048

# 从私钥提取公钥
openssl rsa -in src/main/resources/keys/private.pem \
            -pubout \
            -out src/main/resources/keys/public.pem

# 添加到.gitignore
echo "src/main/resources/keys/*.pem" >> .gitignore
```

### 步骤3：配置数据库

```bash
# 启动PostgreSQL（Docker）
docker run --name finpay-postgres \
  -e POSTGRES_USER=finpay \
  -e POSTGRES_PASSWORD=finpay \
  -e POSTGRES_DB=finpay \
  -p 5432:5432 \
  -d postgres:15

# 验证连接
psql -h localhost -U finpay -d finpay
```

### 步骤4：创建项目结构

```
auth-service/
├── src/main/java/com/finpay/authservice/
│   ├── AuthServiceApplication.java
│   ├── controllers/
│   │   ├── AuthController.java
│   │   └── UserController.java
│   ├── services/
│   │   ├── UserService.java
│   │   └── CustomUserDetails.java
│   ├── repositories/
│   │   ├── UserRepository.java
│   │   ├── RoleRepository.java
│   │   └── LocationRepository.java
│   ├── models/
│   │   ├── UserEntity.java
│   │   ├── Role.java
│   │   └── Location.java
│   ├── securities/
│   │   └── SpringSecurityConfiguration.java
│   └── exceptions/
│       └── GlobalExceptionHandler.java
├── src/main/resources/
│   ├── application.yml
│   └── keys/
│       ├── private.pem
│       └── public.pem
└── pom.xml
```

### 步骤5：创建Entity（按顺序）

#### 5.1 Role.java

```java
@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true)
    private String roleName;  // USER, ADMIN, MODERATOR
}
```

#### 5.2 Location.java

```java
@Entity
@Table(name = "locations")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Location {
    @Id
    @GeneratedValue
    private Long id;

    private String place;
    private String description;
    private Double longitude;
    private Double latitude;
}
```

#### 5.3 UserEntity.java

```java
@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity implements UserDetails {
    @Id
    @GeneratedValue
    private Long id;

    @Column(unique = true)
    private String username;

    @Column(unique = true)
    private String email;

    private String firstName;
    private String lastName;
    private String password;

    @ManyToOne
    @JoinColumn(name = "role_id")
    private Role role;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "location_id")
    private Location location;

    // 实现UserDetails接口...
}
```

### 步骤6：创建Repository

```java
public interface UserRepository extends JpaRepository<UserEntity, Long> {
    Optional<UserEntity> findByUsername(String username);
}

public interface RoleRepository extends JpaRepository<Role, Long> {
    Optional<Role> findByRoleName(String roleName);
}

public interface LocationRepository extends JpaRepository<Location, Long> {
}
```

### 步骤7：创建Service

按照本文档的代码创建`UserService`和`CustomUserDetails`。

### 步骤8：创建Security配置

按照本文档的代码创建`SpringSecurityConfiguration`。

### 步骤9：创建Controller

按照本文档的代码创建`AuthController`和`UserController`。

### 步骤10：配置application.yml

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/finpay
    username: finpay
    password: finpay

  jpa:
    hibernate:
      ddl-auto: update
    show-sql: true
```

### 步骤11：运行和测试

```bash
# 编译运行
mvn spring-boot:run

# 或使用IDE运行
# AuthServiceApplication → Run

# 测试登录
curl -X POST http://localhost:8081/auth-services/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"password123"}'

# 使用Token访问
curl http://localhost:8081/auth-services/users \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..."
```

---

## 学习路径建议

### 阶段1：Java基础（2-3周）

- Java语法、数据类型、控制流
- 面向对象编程（类、继承、多态、接口）
- 集合框架（List、Set、Map）
- 异常处理
- Lambda表达式和Stream API

### 阶段2：Spring Core（2-3周）

- IoC（控制反转）和DI（依赖注入）
- Bean生命周期
- @Component、@Service、@Repository注解
- @Configuration和@Bean
- AOP（面向切面编程）

### 阶段3：Spring MVC（2周）

- MVC设计模式
- @Controller和@RestController
- 请求映射（@GetMapping、@PostMapping等）
- 参数绑定（@RequestBody、@PathVariable、@RequestParam）
- 返回值处理（@ResponseBody、ResponseEntity）
- 异常处理（@ExceptionHandler、@ControllerAdvice）

### 阶段4：数据访问（2-3周）

- JDBC基础
- JPA/Hibernate原理
- 实体映射（@Entity、@Table、@Column）
- 关系映射（@OneToMany、@ManyToOne、@ManyToMany）
- Spring Data JPA
- 查询方法（方法命名规则、@Query）

### 阶段5：Spring Security（3-4周）

- 认证（Authentication）vs 授权（Authorization）
- Security Filter Chain
- UserDetails和UserDetailsService
- PasswordEncoder
- JWT原理和实现
- OAuth2基础

### 阶段6：实战项目（4-6周）

- 构建完整认证服务
- 微服务架构（Spring Cloud）
- API网关（Spring Cloud Gateway）
- 服务注册与发现（Eureka/Consul）
- 配置中心（Spring Cloud Config）
- 消息队列（RabbitMQ/Kafka）

### 阶段7：DevOps和部署（2-3周）

- Docker容器化
- Docker Compose
- CI/CD（GitHub Actions、GitLab CI）
- Kubernetes基础
- 监控和日志（Prometheus、ELK）

---

## 总结

本项目实现了一个**完整的JWT认证系统**，涵盖了以下核心技术：

### 核心知识点回顾

1. **Spring Boot自动配置**：`@SpringBootApplication`启动一切
2. **Spring MVC**：DispatcherServlet路由请求到Controller
3. **Spring Security过滤器链**：层层验证，保护端点
4. **认证管理器**：DaoAuthenticationProvider + UserDetailsService
5. **JPA/Hibernate**：自动生成SQL，对象关系映射
6. **BCrypt密码加密**：强安全性，防暴力破解
7. **JWT无状态认证**：RSA签名，微服务友好
8. **依赖注入**：Spring IoC容器管理所有Bean

### 完整调用链

```
登录请求：
HTTP POST → Tomcat → DispatcherServlet → Security过滤器链(permitAll)
→ AuthController → AuthenticationManager → DaoAuthenticationProvider
→ UserDetailsService → Repository → 数据库 → BCrypt验证密码
→ 生成JWT(私钥签名) → 返回Token

受保护端点：
HTTP GET + Bearer Token → Security过滤器链 → BearerTokenAuthenticationFilter
→ JwtDecoder验证(公钥) → SecurityContext → AuthorizationFilter
→ Controller → Service → Repository → 数据库 → 返回数据
```

通过理解这个完整的调用链，你已经掌握了现代Spring Boot应用的核心架构和安全机制。

现在可以开始构建自己的认证服务了！🚀

---

## 参考资源

- **官方文档**
  - [Spring Boot Reference](https://docs.spring.io/spring-boot/docs/current/reference/html/)
  - [Spring Security Reference](https://docs.spring.io/spring-security/reference/index.html)
  - [Spring Data JPA](https://docs.spring.io/spring-data/jpa/docs/current/reference/html/)

- **JWT相关**
  - [JWT.io](https://jwt.io/) - JWT调试工具
  - [RFC 7519 - JWT](https://tools.ietf.org/html/rfc7519) - JWT规范

- **密码学**
  - [BCrypt Calculator](https://bcrypt-generator.com/)
  - [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)

- **学习资源**
  - [Baeldung Spring Security](https://www.baeldung.com/security-spring)
  - [Spring官方指南](https://spring.io/guides)

---

**文档生成时间**：2024年

**项目版本**：
- Spring Boot: 3.x
- Spring Security: 6.x
- Java: 17+

祝你学习顺利！💪
