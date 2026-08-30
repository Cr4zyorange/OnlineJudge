# AUTH Service Extraction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extract the existing AUTH module into an independently buildable, testable, deployable identity service while preserving every public AUTH API and failing closed on untrusted identity input.

**Architecture:** Add `services/auth-service` as a standalone Spring Boot 3.4.5/Maven service containing only AUTH business code plus a minimal local web/security foundation. Keep the current modular monolith unchanged as the D7 rollback baseline, prove API compatibility with copied behavior tests, and give the new service an isolated AUTH schema, image, probes, and Compose deployment.

**Tech Stack:** Java 21, Spring Boot 3.4.5, Spring JDBC, Jakarta Validation, H2/MySQL, JUnit 5, MockMvc, AssertJ, Maven 3.9.9, Docker Compose.

---

## File map

- `services/auth-service/pom.xml`: standalone build and artifact metadata.
- `services/auth-service/src/main/java/com/onlinejudge/authservice/AuthServiceApplication.java`: isolated component-scan root.
- `services/auth-service/src/main/java/com/onlinejudge/auth/**`: mechanically copied AUTH production behavior.
- `services/auth-service/src/main/java/com/onlinejudge/common/{web,exception,security}/**`: minimal service-local infrastructure only.
- `services/auth-service/src/main/java/com/onlinejudge/authservice/config/AuthWebConfig.java`: CORS, current-user resolver, and protected-path interceptor configuration.
- `services/auth-service/src/main/java/com/onlinejudge/authservice/controller/AuthSystemController.java`: liveness, readiness, and version endpoints.
- `services/auth-service/src/main/resources/application.yml`: H2 development defaults and build/version properties.
- `services/auth-service/src/main/resources/application-compose.properties`: isolated MySQL runtime settings.
- `services/auth-service/src/main/resources/schema-auth-h2.sql`: H2-only AUTH schema.
- `services/auth-service/src/main/resources/db/migration/DB-AUTH-01-auth-user-session.sql`: owned MySQL migration.
- `services/auth-service/src/test/java/com/onlinejudge/auth/**`: copied and extended AUTH behavior/security tests.
- `backend/src/test/java/com/onlinejudge/auth/AuthServiceExtractionContractTest.java`: repository-level independent-delivery contract.
- `deploy/docker/auth-service.Dockerfile`: standalone multi-stage image.
- `deploy/docker/compose.auth.yml`: isolated MySQL plus AUTH service deployment.
- `scripts/test/verify-auth-service-boundary.ps1`: build/source/SQL boundary scanner.
- `docs/开发/D6-AUTH-独立身份服务交付.md`: consumer, operation, and evidence guide.
- `output/test/issue-311/README.md`: reproducible raw-evidence index.

## Local command prefix

The host does not expose Maven on `PATH`. Use this prefix for every local Maven command in this plan:

```powershell
$env:JAVA_HOME = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\jbr'
$maven = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\plugins\maven\lib\maven3\bin\mvn.cmd'
```

CI and Linux evidence may run the equivalent `mvn` command directly.

### Task 1: Establish the independent-service repository contract

**Files:**
- Create: `backend/src/test/java/com/onlinejudge/auth/AuthServiceExtractionContractTest.java`
- Create: `services/auth-service/pom.xml`
- Create: `services/auth-service/src/main/java/com/onlinejudge/authservice/AuthServiceApplication.java`

- [ ] **Step 1: Write the failing repository contract test**

```java
package com.onlinejudge.auth;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class AuthServiceExtractionContractTest {
    private static final Path REPOSITORY = Path.of("..").toAbsolutePath().normalize();
    private static final Path SERVICE = REPOSITORY.resolve("services/auth-service");

    @Test
    void authHasIndependentBuildAndApplication() {
        assertThat(SERVICE.resolve("pom.xml")).isRegularFile();
        assertThat(SERVICE.resolve("src/main/java/com/onlinejudge/authservice/AuthServiceApplication.java")).isRegularFile();
    }
}
```

- [ ] **Step 2: Run the test and confirm RED**

```powershell
& $maven -f backend/pom.xml '-Dtest=AuthServiceExtractionContractTest' test
```

Expected: FAIL because `services/auth-service/pom.xml` and the standalone application do not exist.

- [ ] **Step 3: Add the minimal independent Maven build and application**

Create `services/auth-service/pom.xml` with Spring Boot parent `3.4.5`, Java `21`, artifactId `onlinejudge-auth-service`, and dependencies `spring-boot-starter-jdbc`, `spring-boot-starter-validation`, `spring-boot-starter-web`, `h2` runtime, `mysql-connector-j` runtime, and `spring-boot-starter-test` test scope:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.4.5</version>
        <relativePath/>
    </parent>
    <groupId>com.onlinejudge</groupId>
    <artifactId>onlinejudge-auth-service</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <properties><java.version>21</java.version></properties>
    <dependencies>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-jdbc</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-validation</artifactId></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-web</artifactId></dependency>
        <dependency><groupId>com.h2database</groupId><artifactId>h2</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>com.mysql</groupId><artifactId>mysql-connector-j</artifactId><scope>runtime</scope></dependency>
        <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
    </dependencies>
    <build><plugins><plugin><groupId>org.springframework.boot</groupId><artifactId>spring-boot-maven-plugin</artifactId></plugin></plugins></build>
</project>
```

Create the application root:

```java
package com.onlinejudge.authservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.onlinejudge")
public class AuthServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
```

- [ ] **Step 4: Run the targeted contract and confirm GREEN**

Run the command from Step 2. Expected: PASS because the standalone build and application root now exist.

- [ ] **Step 5: Commit the RED and minimal service build**

```powershell
git add backend/src/test/java/com/onlinejudge/auth/AuthServiceExtractionContractTest.java services/auth-service
git commit -m "feat(auth): establish standalone service build"
```

### Task 2: Migrate AUTH behavior into the standalone service

**Files:**
- Create: `services/auth-service/src/test/java/com/onlinejudge/auth/AuthControllerTest.java`
- Create: `services/auth-service/src/test/java/com/onlinejudge/auth/AuthAdminControllerTest.java`
- Create: `services/auth-service/src/main/java/com/onlinejudge/auth/**`
- Create: `services/auth-service/src/main/java/com/onlinejudge/common/web/ApiResponse.java`
- Create: `services/auth-service/src/main/java/com/onlinejudge/common/exception/ApiException.java`
- Create: `services/auth-service/src/main/java/com/onlinejudge/common/security/{AccessDeniedException,AuthenticationRequiredException,AuthRequiredInterceptor,CurrentUser,CurrentUserArgumentResolver,CurrentUserProvider}.java`
- Create: `services/auth-service/src/main/java/com/onlinejudge/authservice/config/AuthWebConfig.java`
- Create: `services/auth-service/src/main/java/com/onlinejudge/authservice/exception/AuthServiceExceptionHandler.java`
- Create: `services/auth-service/src/test/resources/application.properties`
- Create: `services/auth-service/src/main/resources/schema-auth-h2.sql`
- Modify: `services/auth-service/src/main/java/com/onlinejudge/auth/security/TokenCurrentUserProvider.java`

- [ ] **Step 1: Copy the AUTH controller tests first and make service-specific endpoint substitutions**

Mechanically copy `AuthControllerTest.java` and `AuthAdminControllerTest.java`. In the copied `AuthControllerTest`, replace the two monolith-only `/api/v1/courses...` assertions with protected AUTH paths:

```java
@Test
void protectedAuthApiRejectsHeaderOnlyIdentityWhenSessionTokenIsMissing() throws Exception {
    mockMvc.perform(get("/api/v1/users/me")
                    .header("X-User-Id", "501")
                    .header("X-User-Role", "TEACHER"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
}
```

Add `services/auth-service/src/test/resources/application.properties`:

```properties
spring.datasource.url=jdbc:h2:mem:auth_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1
spring.datasource.driver-class-name=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.sql.init.mode=always
spring.sql.init.schema-locations=classpath:schema-auth-h2.sql
onlinejudge.auth.seed-data-enabled=false
```

- [ ] **Step 2: Run the standalone tests and confirm RED**

```powershell
& $maven -f services/auth-service/pom.xml test
```

Expected: test compilation fails because AUTH controllers, domain classes, services, and minimal common contracts have not been migrated.

- [ ] **Step 3: Mechanically copy the existing AUTH package and only the required common contracts**

```powershell
Copy-Item -Recurse backend/src/main/java/com/onlinejudge/auth services/auth-service/src/main/java/com/onlinejudge/
Copy-Item backend/src/main/java/com/onlinejudge/common/web/ApiResponse.java services/auth-service/src/main/java/com/onlinejudge/common/web/ApiResponse.java
Copy-Item backend/src/main/java/com/onlinejudge/common/exception/ApiException.java services/auth-service/src/main/java/com/onlinejudge/common/exception/ApiException.java
$securityFiles = @('AccessDeniedException','AuthenticationRequiredException','AuthRequiredInterceptor','CurrentUser','CurrentUserArgumentResolver','CurrentUserProvider')
foreach ($securityFile in $securityFiles) {
    Copy-Item "backend/src/main/java/com/onlinejudge/common/security/$securityFile.java" "services/auth-service/src/main/java/com/onlinejudge/common/security/$securityFile.java"
}
```

Do not copy `HeaderCurrentUserProvider`, evaluation, event, storage, CRS, LAB, HWK, GRD, or LRN packages.

- [ ] **Step 4: Remove header fallback from the service-local token provider**

Change the copied `TokenCurrentUserProvider` constructor and `getCurrentUser()` so the service accepts only Bearer sessions:

```java
@Primary
@Component
public class TokenCurrentUserProvider implements CurrentUserProvider {
    private final SessionTokenService sessionTokenService;

    public TokenCurrentUserProvider(SessionTokenService sessionTokenService) {
        this.sessionTokenService = sessionTokenService;
    }

    @Override
    public Optional<CurrentUser> getCurrentUser() {
        return bearerToken()
                .flatMap(sessionTokenService::resolveCurrentUser)
                .map(this::toCurrentUser);
    }
}
```

Keep the existing `bearerToken()` and `toCurrentUser()` methods; remove `HeaderCurrentUserProvider`, `allowHeaderAuth`, and `isAuthSessionEndpoint()`.

- [ ] **Step 5: Add service-local web and exception configuration**

Create `AuthWebConfig` that protects every AUTH path except register, login, and probes:

```java
@Configuration
public class AuthWebConfig implements WebMvcConfigurer {
    private final CurrentUserArgumentResolver resolver;
    private final AuthRequiredInterceptor interceptor;

    public AuthWebConfig(CurrentUserArgumentResolver resolver, AuthRequiredInterceptor interceptor) {
        this.resolver = resolver;
        this.interceptor = interceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(resolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/register",
                        "/api/v1/system/health", "/api/v1/system/readiness", "/api/v1/system/version");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                .allowedMethods("*").allowedHeaders("*");
    }
}
```

Create `AuthServiceExceptionHandler` with this complete service-local mapping:

```java
package com.onlinejudge.authservice.exception;

import com.onlinejudge.common.exception.ApiException;
import com.onlinejudge.common.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthServiceExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(AuthServiceExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> api(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(ApiResponse.error(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> unreadable() {
        return ResponseEntity.badRequest().body(ApiResponse.error("AUTH_400", "请求参数不合法"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> invalid(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getDefaultMessage() == null ? error.getField() + " 不合法" : error.getDefaultMessage())
                .distinct().reduce((left, right) -> left + "；" + right).orElse("请求参数不合法");
        return ResponseEntity.badRequest().body(ApiResponse.error("AUTH_400", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> mismatch(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.badRequest()
                .body(ApiResponse.error("AUTH_400", "参数错误：" + exception.getName() + " 不合法"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> unexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("500", "系统错误，请联系管理员"));
    }
}
```

- [ ] **Step 6: Add the H2-only AUTH schema**

Copy only the seven `t_auth_*` table definitions and indexes from the beginning of `backend/src/main/resources/schema.sql` into `services/auth-service/src/main/resources/schema-auth-h2.sql` using this mechanical extraction, then inspect the result:

```powershell
$schemaLines = Get-Content backend/src/main/resources/schema.sql
$firstForeignTable = [Array]::IndexOf($schemaLines, 'CREATE TABLE IF NOT EXISTS crs_course (')
$schemaLines[0..($firstForeignTable - 1)] | Set-Content services/auth-service/src/main/resources/schema-auth-h2.sql
rg -n 'crs_|lab_|t_hwk_|lrn_|t_grade_|t_course_grade_summary' services/auth-service/src/main/resources/schema-auth-h2.sql
```

The final search must return no matches.

- [ ] **Step 7: Run standalone AUTH tests and reach GREEN**

```powershell
& $maven -f services/auth-service/pom.xml test
```

Expected: all copied AUTH controller/admin tests pass; Header-only identity remains `401 / ERR-AUTH-04`.

- [ ] **Step 8: Commit the behavior migration**

```powershell
git add services/auth-service
git commit -m "feat(auth): migrate identity behavior into service"
```

### Task 3: Add service probes, build version, and dependency failure behavior

**Files:**
- Create: `services/auth-service/src/test/java/com/onlinejudge/authservice/AuthSystemControllerTest.java`
- Create: `services/auth-service/src/test/java/com/onlinejudge/authservice/AuthReadinessFailureTest.java`
- Create: `services/auth-service/src/test/java/com/onlinejudge/authservice/AuthDependencyFailureTest.java`
- Create: `services/auth-service/src/main/java/com/onlinejudge/authservice/controller/AuthSystemController.java`
- Create: `services/auth-service/src/main/java/com/onlinejudge/authservice/config/AuthBuildProperties.java`
- Create: `services/auth-service/src/main/resources/application.yml`

- [ ] **Step 1: Write failing health/readiness/version tests**

```java
@SpringBootTest(properties = {
        "onlinejudge.auth.seed-data-enabled=false",
        "onlinejudge.build.version=0.1.0-test",
        "onlinejudge.build.revision=abc123"
})
@AutoConfigureMockMvc
class AuthSystemControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void exposesMinimalHealthReadinessAndVersion() throws Exception {
        mockMvc.perform(get("/api/v1/system/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UP"));
        mockMvc.perform(get("/api/v1/system/readiness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UP"));
        mockMvc.perform(get("/api/v1/system/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.service").value("auth-service"))
                .andExpect(jsonPath("$.data.version").value("0.1.0-test"))
                .andExpect(jsonPath("$.data.revision").value("abc123"));
    }
}
```

`AuthReadinessFailureTest` must mock `JdbcTemplate.queryForObject("SELECT 1", Integer.class)` to throw `DataAccessResourceFailureException` and assert `503`, code `503`, message `service unavailable`, and absence of JDBC URL/password text. `AuthDependencyFailureTest` must make `POST /api/v1/auth/login` encounter `DataAccessResourceFailureException("jdbc:mysql://auth-db/onlinejudge_auth password=secret")` and assert `500 / 系统错误，请联系管理员` while the response omits `jdbc`, `auth-db`, `onlinejudge_auth`, and `secret`.

- [ ] **Step 2: Run probe tests and confirm RED**

```powershell
& $maven -f services/auth-service/pom.xml '-Dtest=AuthSystemControllerTest,AuthReadinessFailureTest,AuthDependencyFailureTest' test
```

Expected: FAIL with 404 because the standalone probe controller does not exist.

- [ ] **Step 3: Implement build properties and probes**

```java
@ConfigurationProperties(prefix = "onlinejudge.build")
public record AuthBuildProperties(String version, String revision) {
    public AuthBuildProperties {
        version = valueOrUnknown(version);
        revision = valueOrUnknown(revision);
    }
    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value.trim();
    }
}
```

Enable it on the application with `@EnableConfigurationProperties(AuthBuildProperties.class)`. Implement `AuthSystemController` with public `health`, `readiness`, and `version` mappings; readiness executes `SELECT 1`, catches `DataAccessException`, and returns the exact safe `503` response asserted above.

Create `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:auth_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1
    driver-class-name: org.h2.Driver
    username: sa
    password:
  sql:
    init:
      mode: always
      schema-locations: classpath:schema-auth-h2.sql
server:
  port: 8081
onlinejudge:
  auth:
    seed-data-enabled: false
  build:
    version: ${AUTH_SERVICE_VERSION:0.1.0-SNAPSHOT}
    revision: ${AUTH_SERVICE_REVISION:unknown}
```

- [ ] **Step 4: Verify GREEN and commit**

```powershell
& $maven -f services/auth-service/pom.xml '-Dtest=AuthSystemControllerTest,AuthReadinessFailureTest,AuthDependencyFailureTest' test
git add services/auth-service
git commit -m "feat(auth): expose service probes and version"
```

Expected: both probe test classes pass.

### Task 4: Isolate AUTH schema, migration, account, and seed behavior

**Files:**
- Create: `services/auth-service/src/test/java/com/onlinejudge/auth/AuthSchemaOwnershipTest.java`
- Create: `services/auth-service/src/test/java/com/onlinejudge/auth/AuthSeedDataDisabledTest.java`
- Create: `services/auth-service/src/test/java/com/onlinejudge/auth/AuthSeedDataEnabledTest.java`
- Create: `services/auth-service/src/main/resources/db/migration/DB-AUTH-01-auth-user-session.sql`
- Create: `services/auth-service/src/main/resources/application-compose.properties`
- Modify: `services/auth-service/src/main/java/com/onlinejudge/auth/config/AuthSeedDataInitializer.java`

- [ ] **Step 1: Write failing ownership and seed-default tests**

`AuthSchemaOwnershipTest` must read every `.sql` file under `src/main/resources`, join the text, and assert that all seven `t_auth_*` names are present while these patterns are absent: `crs_`, `lab_`, `t_hwk_`, `lrn_`, `t_grade_`, `t_course_grade_summary`.

`AuthSeedDataDisabledTest` must start the application with `onlinejudge.auth.seed-data-enabled=false`, query `t_auth_user`, and assert the count is zero. `AuthSeedDataEnabledTest` starts a separate context with the property `true` and asserts `student001`, `teacher001`, and `admin001` exist and have the expected roles.

- [ ] **Step 2: Run tests and confirm RED**

```powershell
& $maven -f services/auth-service/pom.xml '-Dtest=AuthSchemaOwnershipTest,AuthSeedDataDisabledTest,AuthSeedDataEnabledTest' test
```

Expected: FAIL because the owned MySQL migration is absent and the copied initializer seeds unconditionally.

- [ ] **Step 3: Add the owned migration and conditional seed**

Mechanically copy `database/migrations/DB-AUTH-01-auth-user-session.sql` to the service migration path. Annotate the copied seed configuration:

```java
@Configuration
@ConditionalOnProperty(
        prefix = "onlinejudge.auth",
        name = "seed-data-enabled",
        havingValue = "true"
)
public class AuthSeedDataInitializer {
    // existing three-role and three-user seed behavior remains unchanged
}
```

Create `application-compose.properties`:

```properties
spring.datasource.url=jdbc:mysql://${AUTH_DB_HOST:auth-db}:${AUTH_DB_PORT:3306}/${AUTH_DB_NAME:onlinejudge_auth}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver
spring.datasource.username=${AUTH_DB_USER:onlinejudge_auth}
spring.datasource.password=${AUTH_DB_PASSWORD}
spring.datasource.hikari.initialization-fail-timeout=60000
spring.sql.init.mode=never
onlinejudge.auth.seed-data-enabled=${AUTH_SEED_DATA_ENABLED:false}
onlinejudge.build.version=${AUTH_SERVICE_VERSION:0.1.0-SNAPSHOT}
onlinejudge.build.revision=${AUTH_SERVICE_REVISION:unknown}
server.port=8081
```

- [ ] **Step 4: Verify schema and seeds, then commit**

```powershell
& $maven -f services/auth-service/pom.xml '-Dtest=AuthSchemaOwnershipTest,AuthSeedDataDisabledTest,AuthSeedDataEnabledTest' test
git add services/auth-service
git commit -m "feat(auth): isolate schema and seed data"
```

Expected: ownership and both seed modes pass.

### Task 5: Prove security and concurrent-session boundaries

**Files:**
- Create: `services/auth-service/src/test/java/com/onlinejudge/auth/AuthSubjectSecurityTest.java`
- Modify: `services/auth-service/src/main/java/com/onlinejudge/auth/**` only if a newly failing assertion exposes a real compatibility gap.

- [ ] **Step 1: Write the concurrent-session security contract**

Add a test that registers one user, logs in twice, asserts the two Bearer values differ, logs out with the first token, then asserts the first token returns `401 / ERR-AUTH-04` while the second still returns the user from `/api/v1/auth/me`. Add a password-change case that asserts all prior sessions are revoked.

Also assert:

```java
mockMvc.perform(get("/api/v1/auth/me")
        .header("X-User-Id", "1")
        .header("X-User-Role", "ADMIN"))
    .andExpect(status().isUnauthorized())
    .andExpect(jsonPath("$.code").value("ERR-AUTH-04"));
```

- [ ] **Step 2: Run the security contract against the migrated implementation**

```powershell
& $maven -f services/auth-service/pom.xml '-Dtest=AuthSubjectSecurityTest' test
```

Expected: PASS when the migrated implementation preserves the documented concurrent-session behavior. This task verifies existing behavior and does not require a production change. If an assertion fails, treat that assertion as RED and implement only the documented missing boundary in Step 3.

- [ ] **Step 3: Implement only a boundary proven missing by Step 2**

If Step 2 is GREEN, make no production edit and proceed to Step 4. If it is RED, use the existing `SessionTokenService` and `AuthRepository` APIs. Do not add Header fallback, JWT acceptance, refresh tokens, or global single-session enforcement. Revocation updates only the intended token for logout and all active tokens for password/status changes.

- [ ] **Step 4: Run the standalone security and full AUTH suites**

```powershell
& $maven -f services/auth-service/pom.xml '-Dtest=AuthSubjectSecurityTest,AuthControllerTest,AuthAdminControllerTest' test
```

Expected: all tests pass with no sensitive Bearer or password values in responses.

- [ ] **Step 5: Commit**

```powershell
git add services/auth-service
git commit -m "test(auth): cover trusted subject and session boundaries"
```

### Task 6: Add independent image and Compose deployment contracts

**Files:**
- Modify: `backend/src/test/java/com/onlinejudge/auth/AuthServiceExtractionContractTest.java`
- Create: `deploy/docker/auth-service.Dockerfile`
- Create: `deploy/docker/compose.auth.yml`
- Modify: `deploy/docker/.env.example`

- [ ] **Step 1: Extend the repository contract and confirm RED**

Add assertions that the Dockerfile contains `services/auth-service/pom.xml`, packages `onlinejudge-auth-service-0.1.0-SNAPSHOT.jar`, exposes `8081`, pins both base images by digest, runs as a non-root numeric user, and contains OCI revision/version/source metadata. Assert that `compose.auth.yml` defines only `auth-db` and `auth-service`, references the exact `${GIT_SHA:?…}` image rather than an implicit build, requires database secrets, uses `onlinejudge_auth`, passes `AUTH_DB_*`, and checks `/api/v1/system/readiness`.

Run:

```powershell
& $maven -f backend/pom.xml '-Dtest=AuthServiceExtractionContractTest' test
```

Expected: FAIL because the Dockerfile and Compose contract are absent.

- [ ] **Step 2: Add the standalone Dockerfile**

```dockerfile
# syntax=docker/dockerfile:1.7
FROM maven:3.9.9-eclipse-temurin-21@sha256:3a4ab3276a087bf276f79cae96b1af04f53731bec53fb2e651aca79e4b10211e AS build
WORKDIR /workspace
COPY services/auth-service/pom.xml services/auth-service/pom.xml
RUN --mount=type=cache,target=/root/.m2 mvn -f services/auth-service/pom.xml -q -Dmaven.test.skip=true dependency:go-offline
COPY services/auth-service services/auth-service
RUN --mount=type=cache,target=/root/.m2 mvn -f services/auth-service/pom.xml -Dmaven.test.skip=true package

FROM eclipse-temurin:21-jre@sha256:7a65df4b22d2de92d4e04056e884f3b9122d70b21e2847fd66084278bd0ce037
WORKDIR /opt/onlinejudge-auth
ARG GIT_SHA
ARG IMAGE_SOURCE=https://github.com/Cr4zyorange/OnlineJudge
LABEL org.opencontainers.image.revision="$GIT_SHA" \
      org.opencontainers.image.version="$GIT_SHA" \
      org.opencontainers.image.source="$IMAGE_SOURCE"
RUN command -v wget >/dev/null \
    && groupadd --system --gid 10001 onlinejudge \
    && useradd --system --uid 10001 --gid 10001 --home-dir /opt/onlinejudge-auth --shell /usr/sbin/nologin onlinejudge
COPY --from=build --chown=10001:10001 /workspace/services/auth-service/target/onlinejudge-auth-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8081
USER 10001:10001
ENTRYPOINT ["java", "-jar", "/opt/onlinejudge-auth/app.jar", "--spring.config.additional-location=classpath:/application-compose.properties"]
```

- [ ] **Step 3: Add isolated Compose deployment and safe examples**

Define `auth-db` from `mysql:8.4` with fixed database/user defaults but required `${AUTH_DB_PASSWORD:?...}` and `${AUTH_DB_ROOT_PASSWORD:?...}` secrets; mount only `DB-AUTH-01-auth-user-session.sql` into `/docker-entrypoint-initdb.d/01-auth-schema.sql:ro`. Define `auth-service` from `onlinejudge/auth-service:${GIT_SHA:?GIT_SHA must be the current full 40-character commit SHA}` (no Compose `build` section), pass only AUTH variables, depend on the database health check, and check `http://127.0.0.1:8081/api/v1/system/readiness`.

Add only placeholder/default-safe AUTH entries to `.env.example`; do not add real secrets.

- [ ] **Step 4: Run the static contract and commit**

```powershell
& $maven -f backend/pom.xml '-Dtest=AuthServiceExtractionContractTest' test
git add backend/src/test/java/com/onlinejudge/auth/AuthServiceExtractionContractTest.java deploy/docker
git commit -m "feat(auth): add independent container deployment"
```

Expected: the static delivery contract passes.

### Task 7: Add an executable boundary verifier and delivery documentation

**Files:**
- Create: `scripts/test/verify-auth-service-boundary.ps1`
- Create: `docs/开发/D6-AUTH-独立身份服务交付.md`
- Create: `output/test/issue-311/README.md`

- [ ] **Step 1: Write the verifier with a deliberate RED check**

The PowerShell script must:

1. Resolve the repository root from `$PSScriptRoot`.
2. Fail when `services/auth-service` contains packages `crs`, `lab`, `hwk`, `grd`, `lrn`, `integration`, `evaluation`, `storage`, or `event`.
3. Fail when service SQL contains non-AUTH table prefixes.
4. Run standalone `test` and `package` using `$env:MAVEN_CMD` when set, otherwise `mvn`.
5. Emit the tested SHA and exit code.

Before finalizing the script, temporarily point its service root at `backend` and run it. Expected: non-zero exit because the monolith contains forbidden packages/tables. Restore the real service root and rerun.

- [ ] **Step 2: Verify GREEN against the extracted service**

```powershell
$env:MAVEN_CMD = 'C:\Program Files\JetBrains\IntelliJ IDEA 2025.2.3\plugins\maven\lib\maven3\bin\mvn.cmd'
& scripts/test/verify-auth-service-boundary.ps1
```

Expected: exit `0`, standalone tests pass, package exists, and forbidden package/table count is zero.

- [ ] **Step 3: Write the consumer and operator guide**

Document exact public endpoints, `Authorization: Bearer` format, `/me` subject fields, error mapping (`ERR-AUTH-04`, `ERR-AUTH-05`), Header rejection, AUTH-unavailable fail-closed rule, database variables, seed-data switch, probe paths, build commands, and the known dependency on #310/#317. State that other services must not query `t_auth_*`.

Create `output/test/issue-311/README.md` with slots filled by commands from Task 8: baseline SHA `2a3d355`, tested SHA, environment, commands, counts, exit codes, image digest/revision, and raw record filenames. Do not claim Docker PASS before it runs.

- [ ] **Step 4: Check docs and commit**

```powershell
git diff --check
git add scripts/test/verify-auth-service-boundary.ps1 docs/开发/D6-AUTH-独立身份服务交付.md output/test/issue-311/README.md
git commit -m "docs(auth): document independent service delivery"
```

### Task 8: Run final verification and prepare the non-draft PR

**Files:**
- Modify: `output/test/issue-311/README.md`
- Create: `output/test/issue-311/raw/auth-service-test.txt`
- Create: `output/test/issue-311/raw/auth-service-package.txt`
- Create: `output/test/issue-311/raw/auth-boundary.txt`
- Create: `output/test/issue-311/raw/auth-image.txt`
- Create: `output/test/issue-311/raw/auth-compose-smoke.txt`
- Create: `output/test/issue-311/raw/backend-auth-regression.txt`
- Create: `output/test/issue-311/raw/backend-full-regression.txt`

- [ ] **Step 1: Run service and boundary verification with raw logs**

```powershell
& $maven -f services/auth-service/pom.xml test *> output/test/issue-311/raw/auth-service-test.txt
& $maven -f services/auth-service/pom.xml package -DskipTests *> output/test/issue-311/raw/auth-service-package.txt
$env:MAVEN_CMD = $maven
& scripts/test/verify-auth-service-boundary.ps1 *> output/test/issue-311/raw/auth-boundary.txt
```

Expected: all exit codes `0`; record exact test totals from Surefire reports.

- [ ] **Step 2: Run monolith AUTH compatibility and full regression**

```powershell
& $maven -f backend/pom.xml '-Dtest=AuthControllerTest,AuthAdminControllerTest,AuthMigrationScriptTest,AuthServiceExtractionContractTest' test *> output/test/issue-311/raw/backend-auth-regression.txt
& $maven -f backend/pom.xml test *> output/test/issue-311/raw/backend-full-regression.txt
```

Expected: AUTH compatibility passes. Compare the full run with baseline `408 / 1 failure / 7 skipped`; any new failure is a #311 regression and must be fixed. The known `GrdLrnIntegrationTest` ordering failure remains separately identified if reproduced.

- [ ] **Step 3: Build and smoke the image when Docker Engine is available**

```powershell
$testedSha = git rev-parse HEAD
$env:GIT_SHA = $testedSha
docker build --build-arg "GIT_SHA=$testedSha" -f deploy/docker/auth-service.Dockerfile -t "onlinejudge/auth-service:$testedSha" . *> output/test/issue-311/raw/auth-image.txt
docker compose -f deploy/docker/compose.auth.yml up -d *> output/test/issue-311/raw/auth-compose-smoke.txt
Invoke-RestMethod http://127.0.0.1:8081/api/v1/system/health
Invoke-RestMethod http://127.0.0.1:8081/api/v1/system/readiness
Invoke-RestMethod http://127.0.0.1:8081/api/v1/system/version
docker image inspect "onlinejudge/auth-service:$testedSha" --format '{{.Id}} {{index .Config.Labels "org.opencontainers.image.revision"}} {{.Config.User}}'
docker compose -f deploy/docker/compose.auth.yml exec auth-db sh -lc 'mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SHOW GRANTS FOR CURRENT_USER"'
docker compose -f deploy/docker/compose.auth.yml exec auth-db sh -lc '! mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" -e "SELECT User FROM mysql.user"'
```

Expected: three probe responses are UP/valid, image revision equals `$testedSha`, the image digest is recorded, configured runtime user is `10001:10001`, grants mention only `onlinejudge_auth`, and reading `mysql.user` is denied. If Docker remains unavailable, mark this evidence BLOCKED with the exact named-pipe/service-permission error; do not report the Issue complete.

- [ ] **Step 4: Update evidence, verify the diff, and commit**

Populate `output/test/issue-311/README.md` with actual SHA, counts, exit codes, image digest, and log paths.

```powershell
git diff --check
git status --short
git add output/test/issue-311
git commit -m "test(auth): record standalone service evidence"
```

- [ ] **Step 5: Push and create the required non-draft PR**

```powershell
git push -u origin feature/311-auth-service
gh pr create --repo Cr4zyorange/OnlineJudge --base dev --head feature/311-auth-service --title "feat(auth): extract standalone identity service" --body "$(Get-Content -Raw output/test/issue-311/README.md)`n`ncloses #311"
```

Expected: a non-draft PR targeting `dev`, linked to #311, with no unrelated GRD/LRN fix.
