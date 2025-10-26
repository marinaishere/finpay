# Debugging Guide: Learning Call Stack Sequence in Auth Service

This guide shows you how to use debugging tools to trace the execution flow and understand the call stack sequence in the auth service.

---

## 1. IntelliJ IDEA Debugging Setup

### Step 1: Set Breakpoints

Place breakpoints at key entry points to trace the flow:

#### **For Login Flow:**

1. **Controller Layer** - [AuthController.java:27](src/main/java/com/finpay/authservice/controllers/AuthController.java#L27)
   ```java
   @PostMapping("/login")
   public String login(@RequestBody LoginUserRequest loginUserRequest) {
       // ⭕ SET BREAKPOINT HERE
       Authentication authentication = authenticationManager.authenticate(...);
   ```

2. **Security Layer** - [SpringSecurityConfiguration.java](src/main/java/com/finpay/authservice/securities/SpringSecurityConfiguration.java)
   ```java
   @Bean
   public AuthenticationManager authenticationManager(...) {
       // ⭕ SET BREAKPOINT HERE
       return authenticationManagerBuilder.build();
   ```

3. **Service Layer** - [UserService.java:33](src/main/java/com/finpay/authservice/services/UserService.java#L33)
   ```java
   @Override
   public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
       // ⭕ SET BREAKPOINT HERE
       UserEntity userEntity = userRepository.findByUsername(username)...
   ```

4. **Repository Layer** - Watch the JPA query execution
   ```java
   // IntelliJ will show you when this executes
   userRepository.findByUsername(username)
   ```

#### **For User Registration Flow:**

1. [UserController.java](src/main/java/com/finpay/authservice/controllers/UserController.java)
   ```java
   @PostMapping
   public ResponseEntity<UserDto> createUser(@RequestBody CreateUserRequest request) {
       // ⭕ SET BREAKPOINT HERE
   ```

2. [UserService.java:45](src/main/java/com/finpay/authservice/services/UserService.java#L45)
   ```java
   public UserDto createUser(CreateUserRequest request) {
       // ⭕ SET BREAKPOINT HERE
       Role role = roleRepository.findByRoleName(...)
   ```

---

## 2. Running in Debug Mode

### Option A: IntelliJ IDEA

1. **Start Debug Mode:**
   - Click the bug icon next to `AuthServiceApplication`
   - Or right-click → "Debug 'AuthServiceApplication'"
   - Or press `Ctrl+D` (Mac: `Cmd+D`)

2. **Wait for Application to Start:**
   ```
   Started AuthServiceApplication in 3.456 seconds (JVM running for 4.123)
   ```

3. **Make a Request:**
   - Use Postman, curl, or Swagger UI
   - Example: POST to `http://localhost:8081/auth-services/login`

4. **Observe Debugger:**
   - Execution will pause at your first breakpoint
   - **Call Stack** panel shows the full execution path

### Option B: Maven Command Line

```bash
# Run with remote debugging enabled
mvn spring-boot:run -Dspring-boot.run.jvmArguments="-Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=5005"
```

Then in IntelliJ:
1. Run → Edit Configurations
2. Add New → Remote JVM Debug
3. Port: 5005
4. Click Debug

---

## 3. Understanding the Call Stack Panel

When execution pauses at a breakpoint, the **Call Stack** (Debug panel, bottom-left) shows:

### Example: Login Request Call Stack

```
Thread: http-nio-8081-exec-1
├── AuthController.login:27                           ← Your code
├── NativeMethodAccessorImpl.invoke0                  ← Java reflection
├── NativeMethodAccessorImpl.invoke
├── DelegatingMethodAccessorImpl.invoke
├── Method.invoke
├── InvocableHandlerMethod.doInvoke                   ← Spring MVC
├── InvocableHandlerMethod.invokeForRequest
├── ServletInvocableHandlerMethod.invokeAndHandle
├── RequestMappingHandlerAdapter.invokeHandlerMethod
├── RequestMappingHandlerAdapter.handleInternal
├── AbstractHandlerMethodAdapter.handle
├── DispatcherServlet.doDispatch                      ← Spring Dispatcher
├── DispatcherServlet.doService
├── FrameworkServlet.processRequest
├── FrameworkServlet.doPost
├── HttpServlet.service                               ← Servlet API
├── FrameworkServlet.service
├── HttpServlet.service
├── ApplicationFilterChain.internalDoFilter           ← Filter Chain
├── ApplicationFilterChain.doFilter
├── FilterChainProxy$VirtualFilterChain.doFilter      ← Spring Security
├── AuthorizationFilter.doFilter                      ← Security checks
├── FilterChainProxy$VirtualFilterChain.doFilter
├── ExceptionTranslationFilter.doFilter
├── FilterChainProxy$VirtualFilterChain.doFilter
├── SessionManagementFilter.doFilter
├── FilterChainProxy$VirtualFilterChain.doFilter
├── AuthenticationFilter.doFilter
├── FilterChainProxy$VirtualFilterChain.doFilter
├── LogoutFilter.doFilter
└── ...                                               ← More filters
```

**Read from BOTTOM to TOP** to see the execution order!

---

## 4. Step-by-Step Debugging Techniques

### Technique 1: Step Over (F8 / Cmd+F8)
- Executes current line
- Moves to next line in same method
- **Use for:** Line-by-line execution

### Technique 2: Step Into (F7 / Cmd+F7)
- Goes INTO the method being called
- **Use for:** Following the execution into service/repository methods

**Example:**
```java
// Currently at this line with breakpoint
Authentication authentication = authenticationManager.authenticate(...);
```

Press **F7** → Debugger jumps into `authenticate()` method

### Technique 3: Step Out (Shift+F8 / Cmd+Shift+F8)
- Completes current method
- Returns to caller
- **Use for:** Getting back to higher level

### Technique 4: Resume Program (F9)
- Continues until next breakpoint
- **Use for:** Jumping between key points

### Technique 5: Evaluate Expression (Alt+F8)
- Inspect variable values
- Execute code snippets
- **Use for:** Checking intermediate states

---

## 5. Practical Debugging Scenarios

### Scenario A: Trace Login Flow

**Goal:** Understand how login works from HTTP request to JWT token

1. **Set Breakpoints:**
   ```
   ✓ AuthController.login() (line 27)
   ✓ UserService.loadUserByUsername() (line 33)
   ✓ UserRepository.findByUsername() (implicit)
   ✓ AuthController.login() - after authentication (line 36)
   ✓ JwtEncoder.encode() (Spring Security internal)
   ```

2. **Make Request:**
   ```bash
   curl -X POST http://localhost:8081/auth-services/login \
     -H "Content-Type: application/json" \
     -d '{"username":"john","password":"password123"}'
   ```

3. **Observe Execution:**

   **Stop 1:** AuthController.login()
   - **Call Stack:** Shows HTTP → Spring MVC → Controller
   - **Variables:** `loginUserRequest` contains username/password
   - **Action:** Press F7 to step into `authenticate()`

   **Stop 2:** UserService.loadUserByUsername()
   - **Call Stack:** Shows AuthenticationManager → DaoAuthenticationProvider → UserService
   - **Variables:** `username = "john"`
   - **Action:** Press F7 to step into repository call

   **Stop 3:** After database query
   - **Variables:** `userEntity` contains user data from DB
   - **Action:** Press F8 to step over CustomUserDetails creation

   **Stop 4:** Back in AuthController (after authentication)
   - **Variables:** `authentication` object contains authenticated user
   - **Action:** Watch JWT claims being built

   **Stop 5:** JWT token generated
   - **Variables:** See the encoded token string
   - **Return Value:** Final JWT returned to client

4. **Inspect Call Stack at Each Stop:**
   - Right-click on stack frame → "Jump to Source"
   - See how Spring Security orchestrates the flow

---

### Scenario B: Trace JWT Validation Flow

**Goal:** See how incoming JWT tokens are validated

1. **Set Breakpoints:**
   ```
   ✓ JwtAuthenticationFilter (Spring Security internal)
   ✓ JwtDecoder.decode() (Spring Security internal)
   ✓ UserController.getAllUsers() (line ~20)
   ```

2. **First, get a token:**
   ```bash
   # Login to get token
   TOKEN=$(curl -X POST http://localhost:8081/auth-services/login \
     -H "Content-Type: application/json" \
     -d '{"username":"john","password":"password123"}')
   ```

3. **Make authenticated request:**
   ```bash
   curl -X GET http://localhost:8081/auth-services/users \
     -H "Authorization: Bearer $TOKEN"
   ```

4. **Observe Security Filter Chain:**
   - **Stop 1:** JWT filter extracts token from header
   - **Stop 2:** JwtDecoder validates signature with public.pem
   - **Stop 3:** SecurityContext populated with user authorities
   - **Stop 4:** UserController executes with authenticated user

---

### Scenario C: Trace User Registration

**Goal:** See password hashing and database save

1. **Set Breakpoints:**
   ```
   ✓ UserController.createUser()
   ✓ UserService.createUser() - before password encoding
   ✓ UserService.createUser() - after password encoding
   ✓ UserRepository.save()
   ```

2. **Make Request:**
   ```bash
   curl -X POST http://localhost:8081/auth-services/users \
     -H "Content-Type: application/json" \
     -d '{
       "username":"newuser",
       "email":"new@example.com",
       "password":"plaintext123",
       "roleName":"USER"
     }'
   ```

3. **Observe:**
   - **Before encoding:** `password = "plaintext123"`
   - **After encoding:** `password = "$2a$10$abc...xyz"` (BCrypt hash)
   - **Database save:** Watch JPA generate INSERT statement

---

## 6. Advanced Debugging: Conditional Breakpoints

### Set Conditions on Breakpoints

Right-click breakpoint → "Condition" → Enter expression

**Example 1:** Only break for specific username
```java
// In UserService.loadUserByUsername()
username.equals("admin")
```

**Example 2:** Only break when password is wrong
```java
// In authentication flow
!passwordEncoder.matches(rawPassword, encodedPassword)
```

**Example 3:** Only break for expired tokens
```java
// In JWT validation
jwt.getExpiresAt().isBefore(Instant.now())
```

---

## 7. Logging-Based Debugging

Add strategic logging to trace execution without pausing:

### Option A: Add Loggers to Your Code

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class UserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    @Override
    public UserDetails loadUserByUsername(String username) {
        log.info("🔍 Loading user by username: {}", username);

        UserEntity userEntity = userRepository.findByUsername(username)
            .orElseThrow(() -> {
                log.error("❌ User not found: {}", username);
                return new UsernameNotFoundException("User not found");
            });

        log.info("✅ User found: {} with role: {}", username, userEntity.getRole().getRoleName());

        return new CustomUserDetails(userEntity);
    }
}
```

### Option B: Enable Spring Security Debug Logging

In [application.yml](src/main/resources/application.yml):

```yaml
logging:
  level:
    org.springframework.security: DEBUG
    org.springframework.security.oauth2: DEBUG
    org.hibernate.SQL: DEBUG
    org.hibernate.type.descriptor.sql.BasicBinder: TRACE
    com.finpay.authservice: DEBUG
```

**Output shows:**
```
DEBUG o.s.security.web.FilterChainProxy : Securing POST /auth-services/login
DEBUG o.s.security.web.csrf.CsrfFilter : Invalid CSRF token found
DEBUG o.s.s.w.a.AnonymousAuthenticationFilter : Set SecurityContextHolder to anonymous
DEBUG o.s.s.authentication.ProviderManager : Authenticating request with DaoAuthenticationProvider
DEBUG o.s.s.authentication.dao.DaoAuthenticationProvider : Authenticated user
DEBUG o.s.security.web.authentication.www.BasicAuthenticationFilter : Basic Authentication Authorization header found
```

---

## 8. Spring Boot Actuator for Runtime Inspection

The service already has Spring Actuator enabled. Use it to inspect runtime state:

### Enable Additional Endpoints

Add to [application.yml](src/main/resources/application.yml):

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,httptrace,loggers,beans,mappings
  endpoint:
    health:
      show-details: always
```

### Useful Actuator Endpoints

```bash
# View all registered beans
curl http://localhost:8081/actuator/beans

# View all URL mappings (see all endpoints)
curl http://localhost:8081/actuator/mappings

# View current logger levels
curl http://localhost:8081/actuator/loggers

# View application metrics
curl http://localhost:8081/actuator/metrics

# Change log level at runtime
curl -X POST http://localhost:8081/actuator/loggers/com.finpay.authservice \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## 9. Using Postman + IntelliJ Together

### Workflow:

1. **Set breakpoints in IntelliJ**
2. **Run in debug mode**
3. **Create Postman collection:**

```json
{
  "info": {
    "name": "Auth Service Debug"
  },
  "item": [
    {
      "name": "1. Register User",
      "request": {
        "method": "POST",
        "url": "http://localhost:8081/auth-services/users",
        "body": {
          "mode": "raw",
          "raw": "{\"username\":\"testuser\",\"email\":\"test@example.com\",\"password\":\"password123\",\"roleName\":\"USER\"}"
        }
      }
    },
    {
      "name": "2. Login",
      "request": {
        "method": "POST",
        "url": "http://localhost:8081/auth-services/login",
        "body": {
          "mode": "raw",
          "raw": "{\"username\":\"testuser\",\"password\":\"password123\"}"
        }
      }
    },
    {
      "name": "3. Get All Users (Authenticated)",
      "request": {
        "method": "GET",
        "url": "http://localhost:8081/auth-services/users",
        "header": [
          {
            "key": "Authorization",
            "value": "Bearer {{token}}"
          }
        ]
      }
    }
  ]
}
```

4. **Execute requests** → Observe breakpoints hit in order

---

## 10. Call Stack Sequence Reference

Based on debugging, here's the typical call stack for each operation:

### Login Request Call Sequence

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
   ├─ UsernamePasswordAuthenticationFilter (skipped - we use custom)
   ├─ AnonymousAuthenticationFilter
   ├─ SessionManagementFilter
   ├─ ExceptionTranslationFilter
   └─ AuthorizationFilter
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

### Protected Endpoint Request Call Sequence

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

## 11. Quick Reference: Breakpoint Locations

### Critical Breakpoints for Understanding Flow

| Layer | Class | Method | Line | Purpose |
|-------|-------|--------|------|---------|
| Controller | AuthController | login() | 27 | Entry point for login |
| Service | UserService | loadUserByUsername() | 33 | User lookup |
| Service | UserService | createUser() | 45 | User creation |
| Repository | UserRepository | findByUsername() | - | DB query |
| Security | SpringSecurityConfiguration | authenticationManager() | - | Auth setup |
| Model | CustomUserDetails | constructor | - | Security context |

---

## 12. Tips & Best Practices

### ✅ DO:
- Start with breakpoints at controller level
- Use "Step Into" (F7) to go deeper
- Watch the Variables panel for state changes
- Check the Call Stack to understand the flow
- Use conditional breakpoints for specific scenarios
- Enable SQL logging to see database queries

### ❌ DON'T:
- Set too many breakpoints (you'll get lost)
- Step into Spring internal classes (stay in your code)
- Forget to remove breakpoints after debugging
- Debug in production (use logging instead)

---

## 13. Exercise: Debug Challenge

**Try this debugging exercise:**

1. Set breakpoints in:
   - UserController.createUser()
   - UserService.createUser()
   - Before passwordEncoder.encode()
   - After passwordEncoder.encode()
   - userRepository.save()

2. Create a new user via Postman

3. Answer these questions by observing the debugger:
   - What is the plain-text password value?
   - What does the BCrypt hash look like?
   - How many characters is the hash?
   - What SQL INSERT statement does Hibernate generate?
   - What is the auto-generated user ID?

4. Step through the entire flow and document the call stack

---

## Summary

By combining:
- **Breakpoints** (pausing execution)
- **Step debugging** (F7/F8 navigation)
- **Call Stack inspection** (seeing the flow)
- **Variable watching** (observing state)
- **Logging** (runtime tracing)

You can fully understand how the auth service processes requests from HTTP → Controller → Service → Repository → Database and back!

**Start with simple flows (login) and gradually move to complex ones (JWT validation with filters).**
