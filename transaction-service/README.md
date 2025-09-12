# Transaction Service
Folder Structures
```bash
transaction-service
  |_src
    |_main
      |_java
        |_com.finpay.transactions
          |_clients
          |_configs
          |_controllers
          |_models
          |_repositories
          |_securities
          |_services
          |_AccountServiceApplication.java
    |_resources
      |_keys
        |_public.pem
      |_application.properties
  |_pom.xml
  |_README.md
```
- [application.properties](./src/main/resources/application.yml)
Have to add this to verify JWT token from Auth service
```bash
spring
    security:
      oauth2:
        resourceserver:
          jwt:
            public-key-location: classpath:keys/public.pem 
```
- And a simple security config [SecurityConfig](src/main/java/com/finpay/transactions/securities/SecurityConfig.java)
- Add [CorsConfig](./src/main/java/com/finpay/transactions/configs/CorsConfig.java) to allow other services to connect.
## Add openfeign
To call Account service REST Api
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-openfeign</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-openfeign-core</artifactId>
</dependency>
```
- Add [FeignConfig](./src/main/java/com/finpay/transactions/configs/FeignConfig.java) to add JWT token while calling to other services.
```java
@Bean
public RequestInterceptor requestInterceptor() {
    return (RequestTemplate template) -> {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            // Forward the Authorization header with the Bearer token
            template.header("Authorization", "Bearer " + jwt.getTokenValue());
        }
    };
}
```
## Swagger API docs
- Add dependency
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.2.0</version>
</dependency>
```
- Update [SecurityConfig](src/main/java/com/finpay/transactions/securities/SecurityConfig.java)
```java
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/swagger-ui.html"
                    ).permitAll()
                    .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

    return http.build();
} 
```
For Authorization headers features in Swagger UI [OpenApiConfig](src/main/java/com/finpay/transactions/securities/OpenApiConfig.java)

```bash
mvn clean install
mvn spring-boot:run
```
Now hit: http://localhost:8083/swagger-ui/index.html

# Add Logging, Tracing
- Update pom.xml
```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>

<!-- Zipkin reporter -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>

<!-- Optional: export metrics to Prometheus too -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency> 
```
Run docker container zipkin
```bash
docker run -d -p 9411:9411 openzipkin/zipkin
```
- Update application.yml
```yml
management:
  tracing:
    sampling:
      probability: 1.0
    zipkin:
      base-url: http://localhost:9411
      enabled: true
```
- Add [TracingConfig](./src/main/java/com/finpay/transactions/configs/TracingConfig.java)
```java
@Configuration
public class TracingConfig {

    @Bean
    public SamplerFunction<Tracer> defaultSampler() {
        return SamplerFunction.alwaysSample();
    }
}
```
- Also Update common service pom.xm
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-brave</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-oauth2-jose</artifactId>
</dependency>
<dependency>
    <groupId>org.apache.tomcat.embed</groupId>
    <artifactId>tomcat-embed-core</artifactId>
</dependency> 
```
- Then add [LoggingMdcFilter](./../common/src/main/java/com/finpay/common/logging/LoggingMdcFilter.java)
TODO: user id is missing