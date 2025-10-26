# VS Code Debugging Guide: Auth Service

Complete guide to debugging the auth service in VS Code, focusing on the Call Stack panel and tracing execution flow.

---

## Table of Contents
1. [Initial Setup](#1-initial-setup)
2. [VS Code Debug Interface Overview](#2-vs-code-debug-interface-overview)
3. [Setting Breakpoints](#3-setting-breakpoints)
4. [Understanding the Call Stack Panel](#4-understanding-the-call-stack-panel)
5. [Step-by-Step Debugging](#5-step-by-step-debugging)
6. [Practical Debugging Scenarios](#6-practical-debugging-scenarios)
7. [Advanced Features](#7-advanced-features)

---

## 1. Initial Setup

### Prerequisites

1. **Install Java Extension Pack** in VS Code:
   - Open VS Code
   - Press `Cmd+Shift+X` (Mac) or `Ctrl+Shift+X` (Windows)
   - Search for "Extension Pack for Java"
   - Install (includes Language Support, Debugger, Test Runner, Maven)

2. **Verify Java Installation:**
   ```bash
   java -version
   # Should show Java 17 or higher
   ```

3. **Open the Project:**
   ```bash
   cd /Users/mengruwang/Github/finpay/auth-service
   code .
   ```

### Create Debug Configuration

1. **Create `.vscode/launch.json`:**

Press `Cmd+Shift+D` (Mac) or `Ctrl+Shift+D` (Windows) → Click "create a launch.json file" → Select "Java"

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug Auth Service",
      "request": "launch",
      "mainClass": "com.finpay.authservice.AuthServiceApplication",
      "projectName": "auth-service",
      "console": "integratedTerminal",
      "args": ""
    },
    {
      "type": "java",
      "name": "Debug with Maven",
      "request": "launch",
      "mainClass": "com.finpay.authservice.AuthServiceApplication",
      "projectName": "auth-service",
      "preLaunchTask": "maven-compile"
    },
    {
      "type": "java",
      "name": "Attach to Process (Port 5005)",
      "request": "attach",
      "hostName": "localhost",
      "port": 5005
    }
  ]
}
```

2. **Create `.vscode/tasks.json`** (optional - for pre-build):

```json
{
  "version": "2.0.0",
  "tasks": [
    {
      "label": "maven-compile",
      "type": "shell",
      "command": "mvn clean compile",
      "group": "build"
    }
  ]
}
```

---

## 2. VS Code Debug Interface Overview

When you start debugging, VS Code shows several panels:

```
┌────────────────────────────────────────────────────────────────┐
│  VS Code Window                                                 │
├────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │  Editor Area (Code with Breakpoints)                     │  │
│  │  ●  Line 33: public UserDetails loadUserByUsername(...)  │  │
│  │     Line 34:     UserEntity userEntity = ...             │  │
│  │                                                           │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  LEFT SIDEBAR: RUN AND DEBUG (Cmd+Shift+D)               │ │
│  │                                                            │ │
│  │  ┌──────────────────────────────────────────────────┐   │ │
│  │  │ 1. VARIABLES                                      │   │ │
│  │  │    ▼ Local                                        │   │ │
│  │  │      username: "john"                             │   │ │
│  │  │      this: UserService@1234                       │   │ │
│  │  │    ▼ Static                                       │   │ │
│  │  │      log: Logger@5678                             │   │ │
│  │  └──────────────────────────────────────────────────┘   │ │
│  │                                                            │ │
│  │  ┌──────────────────────────────────────────────────┐   │ │
│  │  │ 2. WATCH                                          │   │ │
│  │  │    + Add Expression                               │   │ │
│  │  │    username.length()                  : 4         │   │ │
│  │  │    userEntity != null                 : true      │   │ │
│  │  └──────────────────────────────────────────────────┘   │ │
│  │                                                            │ │
│  │  ┌──────────────────────────────────────────────────┐   │ │
│  │  │ 3. CALL STACK ⭐ (Most Important!)               │   │ │
│  │  │    ▼ Thread: main                                 │   │ │
│  │  │      ▶ loadUserByUsername() UserService.java:33  │   │ │
│  │  │      ▶ retrieveUser() DaoAuthProvider.java:123   │   │ │
│  │  │      ▶ authenticate() DaoAuthProvider.java:89    │   │ │
│  │  │      ▶ authenticate() ProviderManager.java:234   │   │ │
│  │  │      ▶ login() AuthController.java:27            │   │ │
│  │  │      ▶ invoke() InvocableHandlerMethod.java:...  │   │ │
│  │  │      ▶ doDispatch() DispatcherServlet.java:...   │   │ │
│  │  │      ▶ doFilter() FilterChainProxy.java:...      │   │ │
│  │  └──────────────────────────────────────────────────┘   │ │
│  │                                                            │ │
│  │  ┌──────────────────────────────────────────────────┐   │ │
│  │  │ 4. BREAKPOINTS                                    │   │ │
│  │  │    ☑ AuthController.java:27                      │   │ │
│  │  │    ☑ UserService.java:33                         │   │ │
│  │  │    ☐ UserService.java:45 (disabled)              │   │ │
│  │  └──────────────────────────────────────────────────┘   │ │
│  │                                                            │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  DEBUG TOOLBAR (Top Center)                              │ │
│  │  [Continue▶] [Step Over] [Step Into] [Step Out] [Stop■] │ │
│  │     F5          F10         F11        Shift+F11   Shift+F5│ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  BOTTOM PANEL: DEBUG CONSOLE                             │ │
│  │  > System.out.println() output                           │ │
│  │  > Expressions: username                                 │ │
│  │  "john"                                                   │ │
│  └──────────────────────────────────────────────────────────┘ │
│                                                                  │
└────────────────────────────────────────────────────────────────┘
```

---

## 3. Setting Breakpoints

### Method 1: Click in the Gutter

1. Open [UserService.java](src/main/java/com/finpay/authservice/services/UserService.java)
2. Click to the **left of the line number** (in the gutter)
3. A **red dot** appears = breakpoint set

**Example locations:**
- Line 33: `public UserDetails loadUserByUsername(...)`
- Line 34: `UserEntity userEntity = userRepository.findByUsername(...)`
- Line 40: `return new CustomUserDetails(userEntity);`

### Method 2: Right-Click for Advanced Options

Right-click on the red dot → Options:
- **Edit Breakpoint** → Add condition
- **Disable Breakpoint** → Keep but don't trigger
- **Remove Breakpoint** → Delete it

### Conditional Breakpoints

**Example:** Only break when username is "admin"

1. Right-click in gutter → "Add Conditional Breakpoint"
2. Choose "Expression"
3. Enter: `username.equals("admin")`
4. Now breakpoint only triggers for that username!

### Logpoint (Don't Pause, Just Log)

1. Right-click in gutter → "Add Logpoint"
2. Enter: `Loading user: {username}`
3. Execution continues but logs message to Debug Console

---

## 4. Understanding the Call Stack Panel

### What is the Call Stack?

The **Call Stack** shows the sequence of method calls that led to the current execution point.

**Read from BOTTOM to TOP** to see the execution flow!

### Example: Login Request Call Stack

When debugging stops at `UserService.loadUserByUsername()` line 33:

```
CALL STACK Panel in VS Code:
┌────────────────────────────────────────────────────────────┐
│ ▼ main                                                      │
│   ▶ loadUserByUsername() UserService.java:33      ← YOU ARE HERE
│   ▶ retrieveUser() DaoAuthenticationProvider.java:123
│   ▶ authenticate() DaoAuthenticationProvider.java:89
│   ▶ authenticate() ProviderManager.java:234
│   ▶ login() AuthController.java:27
│   ▶ invokeForRequest() InvocableHandlerMethod.java:145
│   ▶ invokeAndHandle() ServletInvocableHandlerMethod.java:117
│   ▶ handleInternal() RequestMappingHandlerAdapter.java:829
│   ▶ doDispatch() DispatcherServlet.java:1089
│   ▶ doService() DispatcherServlet.java:979
│   ▶ doFilter() FilterChainProxy.java:337
│   ▶ doFilter() AuthorizationFilter.java:121
│   ▶ run() NativeMethodAccessorImpl.java:-2
└────────────────────────────────────────────────────────────┘
```

### Reading the Call Stack (Bottom to Top):

```
Step 14 (Current): loadUserByUsername() ← UserService.java:33
    ↑ Called by
Step 13: retrieveUser() ← DaoAuthenticationProvider.java:123
    ↑ Called by
Step 12: authenticate() ← DaoAuthenticationProvider.java:89
    ↑ Called by
Step 11: authenticate() ← ProviderManager.java:234
    ↑ Called by
Step 10: login() ← AuthController.java:27
    ↑ Called by
Step 9: invokeForRequest() ← Spring MVC
    ↑ Called by
Step 8: invokeAndHandle() ← Spring MVC
    ↑ Called by
...
Step 1: HTTP Request arrives ← Tomcat
```

### Interacting with Call Stack

**Click on any stack frame** to:
- Jump to that code location
- See variables at that point in time
- Understand the context

**Example:**
1. Currently at `UserService.loadUserByUsername()`
2. Click on `AuthController.login()` in Call Stack
3. Editor jumps to AuthController
4. Variables panel shows `loginUserRequest` object
5. Click back on `UserService.loadUserByUsername()` to return

---

## 5. Step-by-Step Debugging

### Debug Toolbar Controls

```
┌──────────────────────────────────────────────────────────┐
│  [▶ Continue] [↷ Step Over] [↓ Step Into] [↑ Step Out]  │
│     F5            F10            F11         Shift+F11    │
└──────────────────────────────────────────────────────────┘
```

### 1. Continue (F5 / Play Button)
- Resumes execution until next breakpoint
- **Use when:** Jumping between breakpoints

### 2. Step Over (F10)
- Executes current line
- Moves to next line **in same method**
- Does NOT go into called methods
- **Use when:** You don't care about method internals

**Example:**
```java
// Currently stopped at line 34 ●
UserEntity userEntity = userRepository.findByUsername(username); // ← HERE
    .orElseThrow(...);

// Press F10 → Moves to next line (line 35)
// Executes repository call but doesn't step into it
```

### 3. Step Into (F11)
- Goes **INTO** the method being called
- **Use when:** You want to trace execution deeper

**Example:**
```java
// Currently at line 34 ●
UserEntity userEntity = userRepository.findByUsername(username); // ← HERE

// Press F11 → Debugger jumps INTO findByUsername() method
// Now you're inside Spring Data JPA code!
```

### 4. Step Out (Shift+F11)
- Completes current method
- Returns to the caller
- **Use when:** You're done inspecting this method

**Example:**
```java
// Currently deep inside userRepository code
// Press Shift+F11 → Returns to UserService.loadUserByUsername()
```

### 5. Restart (Ctrl+Shift+F5)
- Restarts the debug session
- **Use when:** Want to debug from the beginning

### 6. Stop (Shift+F5 / Red Square)
- Stops debugging
- Terminates the application

---

## 6. Practical Debugging Scenarios

### Scenario A: Debug Login Flow End-to-End

**Goal:** Trace a login request from controller to JWT token generation

#### Step 1: Set Breakpoints

Open these files and set breakpoints:

1. [AuthController.java](src/main/java/com/finpay/authservice/controllers/AuthController.java)
   - Line 27: `public String login(@RequestBody LoginUserRequest loginUserRequest)`

2. [UserService.java](src/main/java/com/finpay/authservice/services/UserService.java)
   - Line 33: `public UserDetails loadUserByUsername(String username)`

3. [AuthController.java](src/main/java/com/finpay/authservice/controllers/AuthController.java)
   - Line 36 (after authentication): Where JWT token is generated

#### Step 2: Start Debug Session

1. Press `Cmd+Shift+D` (Mac) or `Ctrl+Shift+D` (Windows)
2. Select "Debug Auth Service" from dropdown
3. Press `F5` or click green play button
4. Wait for application to start (see in Terminal):
   ```
   Started AuthServiceApplication in 3.456 seconds
   ```

#### Step 3: Make Login Request

Open a new terminal and run:

```bash
curl -X POST http://localhost:8081/auth-services/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "password": "password123"
  }'
```

Or use **REST Client extension** in VS Code:

Create `test.http`:
```http
### Login Request
POST http://localhost:8081/auth-services/login
Content-Type: application/json

{
  "username": "testuser",
  "password": "password123"
}
```

Click "Send Request" above the ###

#### Step 4: Observe Execution

**Breakpoint 1: AuthController.login() - Line 27**

VS Code pauses here. Observe:

**VARIABLES Panel:**
```
▼ Local
  loginUserRequest: LoginUserRequest
    ▶ username: "testuser"
    ▶ password: "password123"
  this: AuthController@1a2b3c
```

**CALL STACK Panel:**
```
▼ http-nio-8081-exec-1
  ▶ login() AuthController.java:27                    ← YOU ARE HERE
  ▶ invoke() InvocableHandlerMethod.java:145
  ▶ invokeAndHandle() ServletInvocableHandlerMethod.java:117
  ▶ handleInternal() RequestMappingHandlerAdapter.java:829
  ▶ doDispatch() DispatcherServlet.java:1089
  ▶ doFilter() FilterChainProxy.java:337
  ...
```

**What this tells you:**
- Request came through Spring Security filters (FilterChainProxy)
- Spring MVC DispatcherServlet routed it
- Request mapped to AuthController.login()

**Action:** Press **F11 (Step Into)** on the `authenticationManager.authenticate()` line

---

**Breakpoint 2: UserService.loadUserByUsername() - Line 33**

After stepping, execution pauses here.

**VARIABLES Panel:**
```
▼ Local
  username: "testuser"
  this: UserService@4d5e6f
```

**CALL STACK Panel:**
```
▼ http-nio-8081-exec-1
  ▶ loadUserByUsername() UserService.java:33         ← YOU ARE HERE
  ▶ retrieveUser() DaoAuthenticationProvider.java:123
  ▶ authenticate() DaoAuthenticationProvider.java:89
  ▶ authenticate() ProviderManager.java:234
  ▶ login() AuthController.java:27
  ...
```

**What this tells you:**
- AuthenticationManager delegated to ProviderManager
- ProviderManager delegated to DaoAuthenticationProvider
- DaoAuthenticationProvider called your UserService

**Try this:**
1. Click on `authenticate() DaoAuthenticationProvider.java:89` in Call Stack
2. Editor jumps to Spring Security code
3. See how it calls `loadUserByUsername()`
4. Click back on `loadUserByUsername() UserService.java:33`

**Inspect Database Query:**

**Action:** Press **F10 (Step Over)** on line 34 to execute the repository call

**DEBUG CONSOLE:**
```
Hibernate: select ... from users where username=?
binding parameter [1] as [VARCHAR] - [testuser]
```

**VARIABLES Panel** now shows:
```
▼ Local
  username: "testuser"
  userEntity: UserEntity
    ▶ id: 1
    ▶ username: "testuser"
    ▶ email: "test@example.com"
    ▶ password: "$2a$10$abc123..."
    ▶ role: Role
      ▶ roleName: "USER"
```

**Action:** Press **F5 (Continue)** to move to next breakpoint

---

**Breakpoint 3: AuthController After Authentication**

Execution pauses after authentication succeeds.

**VARIABLES Panel:**
```
▼ Local
  authentication: UsernamePasswordAuthenticationToken
    ▶ principal: CustomUserDetails
      ▶ userEntity: UserEntity@...
    ▶ authorities: [ROLE_USER]
    ▶ authenticated: true
```

**Watch the JWT being created:**

**Action:** Press **F10 (Step Over)** through JWT claim creation

You'll see:
```java
JwtClaimsSet claims = JwtClaimsSet.builder()
    .issuer("self")
    .issuedAt(now)
    .expiresAt(now.plus(30, ChronoUnit.MINUTES))  // ← 30 min expiry
    .subject(authentication.getName())
    .claim("user_id", customUserDetails.getId())
    .claim("email", customUserDetails.getEmail())
    .claim("scope", createScope(authentication))
    .build();
```

**VARIABLES Panel shows:**
```
claims: JwtClaimsSet
  ▶ iss: "self"
  ▶ sub: "testuser"
  ▶ user_id: 1
  ▶ email: "test@example.com"
  ▶ scope: "ROLE_USER"
  ▶ exp: 2024-10-23T15:30:00Z
```

**Action:** Press **F10** to execute JWT encoding

```java
String token = jwtEncoder.encode(parameters).getTokenValue();
```

**VARIABLES Panel:**
```
token: "eyJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJzZWxmIi..."
```

**Action:** Press **F5 (Continue)** to complete the request

---

#### Step 5: Analyze Complete Call Stack

At any breakpoint, look at the full Call Stack to understand the flow:

```
CALL STACK (Read Bottom → Top for execution order):
┌──────────────────────────────────────────────────────────┐
│ 1. run() Thread.java                        ← START      │
│ 2. service() Http11Processor.java           ← Tomcat     │
│ 3. service() ApplicationFilterChain.java    ← Filters    │
│ 4. doFilter() FilterChainProxy.java         ← Security   │
│ 5. doFilter() AuthorizationFilter.java                   │
│ 6. doService() DispatcherServlet.java       ← Spring MVC │
│ 7. doDispatch() DispatcherServlet.java                   │
│ 8. handle() RequestMappingHandlerAdapter.java            │
│ 9. invokeHandlerMethod() ...                             │
│10. login() AuthController.java:27           ← Your Code  │
│11. authenticate() ProviderManager.java      ← Auth       │
│12. authenticate() DaoAuthenticationProvider.java         │
│13. retrieveUser() DaoAuthenticationProvider.java         │
│14. loadUserByUsername() UserService.java:33 ← YOU HERE   │
└──────────────────────────────────────────────────────────┘
```

---

### Scenario B: Debug User Registration

**Goal:** See password hashing in action

#### Breakpoints:
1. [UserController.java](src/main/java/com/finpay/authservice/controllers/UserController.java) - `createUser()` method
2. [UserService.java](src/main/java/com/finpay/authservice/services/UserService.java) - Line 45: `createUser()` method
3. Line before `passwordEncoder.encode()`
4. Line after `passwordEncoder.encode()`

#### Request:
```bash
curl -X POST http://localhost:8081/auth-services/users \
  -H "Content-Type: application/json" \
  -d '{
    "username": "newuser",
    "email": "new@example.com",
    "password": "PlainText123",
    "roleName": "USER"
  }'
```

#### Observations:

**Before encoding:**
```
VARIABLES:
  request.password: "PlainText123"
```

**After encoding:**
```
VARIABLES:
  userEntity.password: "$2a$10$N9qo8uLOickgx2ZMRZoMye..."
```

**Length comparison:**
- Plain: 12 characters
- BCrypt: 60 characters

---

### Scenario C: Debug JWT Validation

**Goal:** See how incoming JWT tokens are validated

#### Setup:

1. First, get a valid token (login first)
2. Set breakpoints in Spring Security filter code (if available) or in controller

#### Request:
```bash
TOKEN="eyJhbGciOiJSUzI1NiJ9..."

curl -X GET http://localhost:8081/auth-services/users \
  -H "Authorization: Bearer $TOKEN"
```

#### Observations in Call Stack:

```
CALL STACK:
  ▶ getAllUsers() UserController.java
  ▶ doFilter() AuthorizationFilter.java        ← Security check
  ▶ doFilterInternal() BearerTokenAuthFilter   ← JWT extraction
  ▶ decode() NimbusJwtDecoder.java             ← JWT validation
```

---

## 7. Advanced Features

### A. Watch Expressions

Add expressions to monitor continuously:

1. In **WATCH** panel, click "+ Add Expression"
2. Enter expressions:
   ```
   username.length()
   userEntity != null
   authentication.isAuthenticated()
   claims.getExpiresAt()
   ```

3. Values update as you step through code

### B. Debug Console

Execute code while debugging:

1. While paused at breakpoint, open **DEBUG CONSOLE**
2. Type Java expressions:
   ```java
   username
   // Output: "testuser"

   username.toUpperCase()
   // Output: "TESTUSER"

   userEntity.getRole().getRoleName()
   // Output: "USER"

   passwordEncoder.matches("wrong", userEntity.getPassword())
   // Output: false
   ```

### C. Conditional Breakpoints

**Example 1:** Only break for specific username
```java
username.equals("admin")
```

**Example 2:** Only break when password is wrong
```java
!passwordEncoder.matches(rawPassword, encodedPassword)
```

**Example 3:** Only break for specific role
```java
userEntity.getRole().getRoleName().equals("ADMIN")
```

### D. Exception Breakpoints

Break whenever an exception is thrown:

1. In **BREAKPOINTS** panel
2. Click "+ Add Exception Breakpoint"
3. Enter exception class: `UsernameNotFoundException`
4. Choose "Caught" or "Uncaught"

Now debugger pauses whenever that exception occurs!

### E. Logpoints (No-Pause Logging)

Instead of `System.out.println()`:

1. Right-click in gutter → "Add Logpoint"
2. Enter: `User {username} loaded with role {userEntity.getRole().getRoleName()}`
3. Execution continues, but message appears in Debug Console

**Output:**
```
User testuser loaded with role USER
User admin loaded with role ADMIN
```

### F. Data Breakpoints (Field Watch)

Break when a field value changes:

1. While debugging, in **VARIABLES** panel
2. Right-click on a field → "Break When Value Changes"
3. Debugger pauses whenever that field is modified

**Example:** Watch `userEntity.password` to see when it's set

---

## 8. VS Code Keyboard Shortcuts Reference

### macOS

| Action | Shortcut |
|--------|----------|
| Start/Continue Debugging | `F5` |
| Stop Debugging | `Shift+F5` |
| Restart Debugging | `Cmd+Shift+F5` |
| Step Over | `F10` |
| Step Into | `F11` |
| Step Out | `Shift+F11` |
| Toggle Breakpoint | `F9` |
| Open Debug View | `Cmd+Shift+D` |
| Focus Debug Console | `Cmd+Shift+Y` |

### Windows/Linux

| Action | Shortcut |
|--------|----------|
| Start/Continue Debugging | `F5` |
| Stop Debugging | `Shift+F5` |
| Restart Debugging | `Ctrl+Shift+F5` |
| Step Over | `F10` |
| Step Into | `F11` |
| Step Out | `Shift+F11` |
| Toggle Breakpoint | `F9` |
| Open Debug View | `Ctrl+Shift+D` |
| Focus Debug Console | `Ctrl+Shift+Y` |

---

## 9. Tips & Tricks

### ✅ Best Practices

1. **Start Shallow, Go Deep**
   - Set breakpoint at controller first
   - Step into only when needed
   - Don't get lost in Spring internal code

2. **Use the Call Stack to Navigate**
   - Click frames to see context
   - Understand who called what
   - Find where values were set

3. **Watch Panel is Your Friend**
   - Add key expressions
   - Monitor state changes
   - Quick calculations

4. **Use Conditional Breakpoints**
   - Avoid breaking on every request
   - Focus on specific scenarios
   - Save time!

5. **Logpoints for Production Code**
   - No need to add/remove print statements
   - Clean code
   - Easy to toggle

### ❌ Common Mistakes

1. **Too Many Breakpoints**
   - You'll get lost
   - Start with 2-3 key points

2. **Stepping Into Spring Code**
   - Use Step Over (F10) for Spring methods
   - Only Step Into (F11) your own code

3. **Forgetting to Stop Debug Session**
   - Port 8081 stays occupied
   - Next debug fails to start
   - Always press Shift+F5 when done

4. **Not Checking Call Stack**
   - Call Stack shows the full story
   - Use it to understand flow

---

## 10. Troubleshooting

### Issue 1: Breakpoints Not Hitting

**Symptoms:** Red dot has a grey border, or is crossed out

**Solutions:**
1. Make sure debug is running (not normal run)
2. Rebuild project: `Cmd+Shift+B`
3. Check breakpoint is in executable code (not comments/imports)
4. Verify class is actually loaded

### Issue 2: Cannot Start Debugger

**Symptoms:** "Cannot find main class"

**Solutions:**
1. Run `mvn clean install` in terminal
2. Reload VS Code Java projects: `Cmd+Shift+P` → "Java: Clean Java Language Server Workspace"
3. Check `launch.json` has correct main class
4. Ensure `pom.xml` is valid

### Issue 3: Port Already in Use

**Symptoms:** "Port 8081 is already in use"

**Solutions:**
1. Stop previous debug session: `Shift+F5`
2. Find and kill process:
   ```bash
   lsof -i :8081
   kill -9 <PID>
   ```

### Issue 4: Variables Show "Not Available"

**Symptoms:** Can't see variable values

**Solutions:**
1. Compile with debug info (should be default)
2. Step Over (F10) the line first
3. Check variable is in scope

---

## 11. Quick Start Checklist

- [ ] Install "Extension Pack for Java" in VS Code
- [ ] Create `.vscode/launch.json` with debug config
- [ ] Open auth-service folder in VS Code
- [ ] Set breakpoint at `AuthController.login()` line 27
- [ ] Press `F5` to start debugging
- [ ] Wait for "Started AuthServiceApplication"
- [ ] Send POST request to `/auth-services/login`
- [ ] Observe breakpoint hit
- [ ] Check **CALL STACK** panel (bottom → top)
- [ ] Check **VARIABLES** panel (local variables)
- [ ] Press `F10` to step over
- [ ] Press `F11` to step into
- [ ] Press `F5` to continue
- [ ] Press `Shift+F5` to stop

---

## 12. Visual Guide: Call Stack Example

### Real Example from Debugging Login

```
┌─────────────────────────────────────────────────────────────┐
│ CALL STACK Panel (VS Code)                                  │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ ▼ http-nio-8081-exec-1                                      │
│                                                              │
│   loadUserByUsername() UserService.java:33      ⭐ Current  │
│   retrieveUser() AbstractUserDetailsAuthProvider.java:123   │
│   authenticate() DaoAuthenticationProvider.java:89          │
│   authenticate() ProviderManager.java:234                   │
│   login() AuthController.java:27                            │
│   invokeForRequest() InvocableHandlerMethod.java:145        │
│   invokeAndHandle() ServletInvocableHandlerMethod.java:117  │
│   handleInternal() RequestMappingHandlerAdapter.java:829    │
│   doDispatch() DispatcherServlet.java:1089                  │
│   doService() DispatcherServlet.java:979                    │
│   doFilter() FilterChainProxy$VirtualFilterChain.java:337   │
│   doFilter() AuthorizationFilter.java:121                   │
│   doFilter() FilterChainProxy$VirtualFilterChain.java:324   │
│   doFilter() ExceptionTranslationFilter.java:124            │
│   doFilter() FilterChainProxy$VirtualFilterChain.java:324   │
│   doFilter() SessionManagementFilter.java:82               │
│   ...                                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘

Reading this stack:

16 ← Session filter ran
15 ← Exception handler filter ran
14 ← Authorization filter ran
13 ← Security filter chain started
12 ← Spring DispatcherServlet received request
11 ← Dispatcher preparing to handle request
10 ← Request mapping handler found
 9 ← Invoking handler method
 8 ← Invoking for request
 7 ← YOUR CODE: AuthController.login() executed
 6 ← AuthenticationManager.authenticate() called
 5 ← ProviderManager delegated to DaoAuthProvider
 4 ← DaoAuthProvider needs to load user
 3 ← YOUR CODE: UserService.loadUserByUsername() ⭐ YOU ARE HERE
 2 ← Next: Query database via repository
 1 ← Next: Return CustomUserDetails
```

---

## Summary

**VS Code debugging workflow:**

1. **Set breakpoints** (click in gutter)
2. **Start debug** (F5)
3. **Trigger request** (curl/Postman/REST Client)
4. **Observe execution** pause at breakpoint
5. **Check Call Stack** (bottom→top shows execution flow)
6. **Check Variables** (see current state)
7. **Step through code** (F10/F11)
8. **Continue** (F5) to next breakpoint
9. **Stop** (Shift+F5) when done

**Most Important Panels:**
- **CALL STACK** - Shows execution sequence (read bottom→top)
- **VARIABLES** - Shows current values
- **WATCH** - Monitor specific expressions
- **DEBUG CONSOLE** - Execute code while paused

**Pro Tip:** Click on any frame in the Call Stack to jump to that code and see its variables. This is the fastest way to understand the execution flow!

Now you can debug like a pro in VS Code! 🚀
