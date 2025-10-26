# Auth Service Flow Diagrams

## 1. Complete System Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT (Browser/App)                      │
└───────────────────────────┬─────────────────────────────────────┘
                            │
                            │ HTTP Request
                            ▼
┌─────────────────────────────────────────────────────────────────┐
│                    AUTH SERVICE (Port 8081)                      │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Spring Security Filter Chain                   │ │
│  │  • Check if endpoint is public or protected                │ │
│  │  • Validate JWT token if present                           │ │
│  │  • Extract user authorities from token                     │ │
│  └──────────────────────┬─────────────────────────────────────┘ │
│                         │                                         │
│                         ▼                                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                   CONTROLLERS LAYER                         │ │
│  │  ┌─────────────────────┐  ┌──────────────────────┐        │ │
│  │  │  AuthController     │  │  UserController      │        │ │
│  │  │  /auth-services/    │  │  /auth-services/     │        │ │
│  │  │  - login            │  │  - users (CRUD)      │        │ │
│  │  └─────────┬───────────┘  └──────────┬───────────┘        │ │
│  └────────────┼──────────────────────────┼────────────────────┘ │
│               │                          │                       │
│               ▼                          ▼                       │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                    SERVICES LAYER                           │ │
│  │  ┌─────────────────────────────────────────────────┐       │ │
│  │  │           UserService                            │       │ │
│  │  │  • loadUserByUsername()                         │       │ │
│  │  │  • createUser()                                 │       │ │
│  │  │  • getAllUsers()                                │       │ │
│  │  │  • BCryptPasswordEncoder                        │       │ │
│  │  └────────────────────┬────────────────────────────┘       │ │
│  └─────────────────────────┼────────────────────────────────────┘ │
│                            │                                       │
│                            ▼                                       │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                  REPOSITORIES LAYER                         │ │
│  │  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐     │ │
│  │  │UserRepository│  │RoleRepository│  │LocationRepo │     │ │
│  │  │(Spring Data) │  │(Spring Data) │  │(Spring Data)│     │ │
│  │  └──────┬───────┘  └──────┬───────┘  └──────┬──────┘     │ │
│  └─────────┼──────────────────┼──────────────────┼────────────┘ │
└────────────┼──────────────────┼──────────────────┼──────────────┘
             │                  │                  │
             │      JPA/Hibernate                  │
             ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│               PostgreSQL Database (Port 5432)                    │
│                                                                   │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐         │
│  │  users       │  │  roles       │  │  locations   │         │
│  │              │  │              │  │              │         │
│  │  • id        │  │  • id        │  │  • id        │         │
│  │  • username  │  │  • role_name │  │  • place     │         │
│  │  • email     │  │              │  │  • longitude │         │
│  │  • password  │  └──────────────┘  │  • latitude  │         │
│  │  • role_id   │                     └──────────────┘         │
│  │  • location_id                                               │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

---

## 2. User Registration Flow

```
CLIENT                 AuthController        UserService           Repository          Database
  │                          │                    │                     │                  │
  │  POST /users             │                    │                     │                  │
  │  {username, email,       │                    │                     │                  │
  │   password, role}        │                    │                     │                  │
  ├─────────────────────────>│                    │                     │                  │
  │                          │                    │                     │                  │
  │                          │ createUser()       │                     │                  │
  │                          ├───────────────────>│                     │                  │
  │                          │                    │                     │                  │
  │                          │                    │ findByRoleName()    │                  │
  │                          │                    ├────────────────────>│                  │
  │                          │                    │                     │ SELECT * FROM    │
  │                          │                    │                     ├─────────────────>│
  │                          │                    │                     │ roles            │
  │                          │                    │                     │<─────────────────┤
  │                          │                    │<────────────────────┤                  │
  │                          │                    │                     │                  │
  │                          │                    │ Hash password       │                  │
  │                          │                    │ (BCrypt)            │                  │
  │                          │                    │                     │                  │
  │                          │                    │ save(userEntity)    │                  │
  │                          │                    ├────────────────────>│                  │
  │                          │                    │                     │ INSERT INTO      │
  │                          │                    │                     ├─────────────────>│
  │                          │                    │                     │ users            │
  │                          │                    │                     │<─────────────────┤
  │                          │                    │<────────────────────┤                  │
  │                          │                    │                     │                  │
  │                          │ UserDTO            │                     │                  │
  │                          │<───────────────────┤                     │                  │
  │                          │                    │                     │                  │
  │  201 Created             │                    │                     │                  │
  │  {id, username, email}   │                    │                     │                  │
  │<─────────────────────────┤                    │                     │                  │
  │                          │                    │                     │                  │
```

---

## 3. Login & JWT Generation Flow

```
CLIENT              AuthController    AuthManager    UserService    JwtEncoder    Database
  │                       │               │               │              │            │
  │ POST /login           │               │               │              │            │
  │ {username, password}  │               │               │              │            │
  ├──────────────────────>│               │               │              │            │
  │                       │               │               │              │            │
  │                       │ authenticate()│               │              │            │
  │                       ├──────────────>│               │              │            │
  │                       │               │               │              │            │
  │                       │               │loadUserByUsername()          │            │
  │                       │               ├──────────────>│              │            │
  │                       │               │               │              │            │
  │                       │               │               │ findByUsername()          │
  │                       │               │               ├─────────────────────────>│
  │                       │               │               │              │ SELECT *  │
  │                       │               │               │<─────────────────────────┤
  │                       │               │               │              │            │
  │                       │               │CustomUserDetails             │            │
  │                       │               │<──────────────┤              │            │
  │                       │               │               │              │            │
  │                       │               │ Compare       │              │            │
  │                       │               │ BCrypt hash   │              │            │
  │                       │               │               │              │            │
  │                       │ Authentication│               │              │            │
  │                       │ (if success)  │               │              │            │
  │                       │<──────────────┤               │              │            │
  │                       │               │               │              │            │
  │                       │ Build JWT Claims              │              │            │
  │                       │ • user_id                     │              │            │
  │                       │ • email                       │              │            │
  │                       │ • scope (roles)               │              │            │
  │                       │ • expiry (30 min)             │              │            │
  │                       │                               │              │            │
  │                       │ encode(claims)                │              │            │
  │                       ├──────────────────────────────────────────────>│            │
  │                       │                               │              │            │
  │                       │                               │   Sign with  │            │
  │                       │                               │  private.pem │            │
  │                       │                               │              │            │
  │                       │ JWT Token                     │              │            │
  │                       │<──────────────────────────────────────────────┤            │
  │                       │                               │              │            │
  │ 200 OK                │                               │              │            │
  │ {token: "eyJhbG..."}  │                               │              │            │
  │<──────────────────────┤                               │              │            │
  │                       │                               │              │            │
```

**JWT Token Structure:**
```json
{
  "header": {
    "alg": "RS256",
    "typ": "JWT"
  },
  "payload": {
    "user_id": 123,
    "email": "user@example.com",
    "scope": "ROLE_USER",
    "iat": 1234567890,
    "exp": 1234569690
  },
  "signature": "signed_with_private_key"
}
```

---

## 4. Protected Endpoint Access Flow

```
CLIENT              SecurityFilter   JwtDecoder    UserService    Repository    Database
  │                       │              │              │              │            │
  │ GET /users            │              │              │              │            │
  │ Authorization:        │              │              │              │            │
  │ Bearer eyJhbG...      │              │              │              │            │
  ├──────────────────────>│              │              │              │            │
  │                       │              │              │              │            │
  │                       │ Extract JWT  │              │              │            │
  │                       │              │              │              │            │
  │                       │ decode(token)│              │              │            │
  │                       ├─────────────>│              │              │            │
  │                       │              │              │              │            │
  │                       │              │ Verify with  │              │            │
  │                       │              │ public.pem   │              │            │
  │                       │              │              │              │            │
  │                       │              │ Check expiry │              │            │
  │                       │              │              │              │            │
  │                       │ Jwt (claims) │              │              │            │
  │                       │<─────────────┤              │              │            │
  │                       │              │              │              │            │
  │                       │ Set Security │              │              │            │
  │                       │ Context with │              │              │            │
  │                       │ authorities  │              │              │            │
  │                       │              │              │              │            │
  │                       │ Pass to      │              │              │            │
  │                       │ Controller   │              │              │            │
  │                       ├──────────────────────────────>              │            │
  │                       │              │              │              │            │
  │                       │              │              │ getAllUsers()│            │
  │                       │              │              ├─────────────>│            │
  │                       │              │              │              │            │
  │                       │              │              │              │ SELECT *   │
  │                       │              │              │              ├───────────>│
  │                       │              │              │              │ FROM users │
  │                       │              │              │              │<───────────┤
  │                       │              │              │<─────────────┤            │
  │                       │              │              │              │            │
  │                       │ UserDTO[]    │              │              │            │
  │                       │<──────────────────────────────              │            │
  │                       │              │              │              │            │
  │ 200 OK                │              │              │              │            │
  │ [{user1}, {user2}...] │              │              │              │            │
  │<──────────────────────┤              │              │              │            │
  │                       │              │              │              │            │
```

---

## 5. Application Startup Flow

```
┌──────────────────────────────────────────────────────────────┐
│                    Application Startup                        │
└───────────────────────────┬──────────────────────────────────┘
                            │
                            ▼
              ┌─────────────────────────┐
              │ Load application.yml    │
              │ • Database config       │
              │ • Server port 8081      │
              │ • JWT key paths         │
              └────────────┬────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │ Initialize Database     │
              │ • Connect PostgreSQL    │
              │ • Hibernate DDL update  │
              │ • Create/update tables  │
              └────────────┬────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │ Run CommandLineRunner   │
              │ RoleSeeder              │
              │ • Check if USER exists  │
              │ • Check if ADMIN exists │
              │ • Create if missing     │
              └────────────┬────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │ Initialize Security     │
              │ • Load private.pem      │
              │ • Load public.pem       │
              │ • Configure JWT decoder │
              │ • Configure JWT encoder │
              └────────────┬────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │ Start Tomcat Server     │
              │ Port: 8081              │
              │ Ready to accept requests│
              └─────────────────────────┘
```

---

## 6. Security Decision Flow

```
                        ┌──────────────────┐
                        │ HTTP Request     │
                        │ Arrives          │
                        └────────┬─────────┘
                                 │
                                 ▼
                    ┌────────────────────────┐
                    │ Is endpoint public?    │
                    │ • /login               │
                    │ • /users (POST)        │
                    │ • /swagger-ui/**       │
                    │ • /actuator/**         │
                    └────────┬───────────────┘
                             │
                  ┌──────────┴──────────┐
                  │ YES                 │ NO
                  ▼                     ▼
        ┌─────────────────┐   ┌─────────────────────┐
        │ Allow Access    │   │ Check Authorization │
        │ No Auth Required│   │ Header              │
        └─────────────────┘   └──────────┬──────────┘
                                         │
                              ┌──────────┴──────────┐
                              │ Token Present?      │
                              └──────────┬──────────┘
                                         │
                              ┌──────────┴──────────┐
                              │ NO                  │ YES
                              ▼                     ▼
                    ┌──────────────────┐  ┌──────────────────┐
                    │ 401 Unauthorized │  │ Validate JWT     │
                    │ Access Denied    │  │ • Verify sig     │
                    └──────────────────┘  │ • Check expiry   │
                                          └────────┬─────────┘
                                                   │
                                        ┌──────────┴──────────┐
                                        │ Valid?              │
                                        └──────────┬──────────┘
                                                   │
                                        ┌──────────┴──────────┐
                                        │ NO                  │ YES
                                        ▼                     ▼
                              ┌──────────────────┐  ┌──────────────────┐
                              │ 403 Forbidden    │  │ Extract Roles    │
                              │ Invalid Token    │  │ Set Security     │
                              └──────────────────┘  │ Context          │
                                                    │ Allow Access     │
                                                    └──────────────────┘
```

---

## 7. Database Relationships

```
┌──────────────────────────┐
│       roles              │
│  ┌────────────────────┐  │
│  │ id (PK)            │  │
│  │ role_name          │  │
│  │ • "USER"           │  │
│  │ • "ADMIN"          │  │
│  └────────────────────┘  │
└──────────┬───────────────┘
           │
           │ Many-to-One
           │
           ▼
┌──────────────────────────┐         ┌──────────────────────────┐
│       users              │         │      locations           │
│  ┌────────────────────┐  │         │  ┌────────────────────┐  │
│  │ id (PK)            │  │         │  │ id (PK)            │  │
│  │ username (unique)  │  │         │  │ place              │  │
│  │ email              │  │         │  │ description        │  │
│  │ first_name         │  │         │  │ longitude          │  │
│  │ last_name          │  │         │  │ latitude           │  │
│  │ password (BCrypt)  │  │         │  └────────────────────┘  │
│  │ role_id (FK)       │──┘         └──────────────────────────┘
│  │ location_id (FK)   │────────────────────────▲
│  └────────────────────┘                        │
└─────────────────────────────────────────────────┘
                                    Many-to-One
```

---

## 8. Key Components Interaction

```
┌─────────────────────────────────────────────────────────────────┐
│                    Spring Boot Application                       │
│                                                                   │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │          SpringSecurityConfiguration                        │ │
│  │  • SecurityFilterChain                                     │ │
│  │  • JwtDecoder (public.pem)                                │ │
│  │  • JwtEncoder (private.pem)                               │ │
│  │  • AuthenticationManager                                   │ │
│  │  • BCryptPasswordEncoder                                   │ │
│  └────────────────────┬───────────────────────────────────────┘ │
│                       │ Uses                                     │
│                       ▼                                          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │                 UserService                                 │ │
│  │  • Implements UserDetailsService                           │ │
│  │  • loadUserByUsername() ────> Authentication              │ │
│  │  • createUser() ────────────> User Management             │ │
│  │  • getAllUsers() ───────────> User Retrieval              │ │
│  └────────────────────┬───────────────────────────────────────┘ │
│                       │ Uses                                     │
│                       ▼                                          │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              CustomUserDetails                              │ │
│  │  • Wraps UserEntity                                        │ │
│  │  • Provides authorities (roles)                            │ │
│  │  • Used by Spring Security                                 │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
```

---

## 9. Complete Request/Response Lifecycle

```
1. User Registration
   └─> POST /auth-services/users → UserController → UserService
       → Hash password (BCrypt) → Save to DB → Return UserDTO

2. User Login
   └─> POST /auth-services/login → AuthController → Authenticate
       → Load user from DB → Verify password → Generate JWT
       → Sign with private.pem → Return token (30 min expiry)

3. Access Protected Resource
   └─> GET /auth-services/users + Bearer token → SecurityFilter
       → Decode JWT with public.pem → Verify signature & expiry
       → Extract roles → Set SecurityContext → UserController
       → UserService → Query DB → Return UserDTO[]

4. Invalid/Expired Token
   └─> GET /auth-services/users + invalid token → SecurityFilter
       → JWT decode fails → 403 Forbidden

5. Missing Token
   └─> GET /auth-services/users (no token) → SecurityFilter
       → No Authorization header → 401 Unauthorized
```

