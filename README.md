# OrderCheck backend

Spring Boot 3.5 / Java 21 / PostgreSQL. The default Spring profile is `local`, configured **only in `src/main/resources/application.yml`**.

## Local development with `application-local.yaml`

1. Copy `src/main/resources/application-local.example.yaml` to `src/main/resources/application-local.yaml`.
2. The target file is intentionally ignored by Git. Edit its database settings to match your local PostgreSQL.
3. If you already have an `application-local.yml`, move your custom settings into the new `application-local.yaml` and rename or remove the old file. In particular, **never put `spring.profiles.default` or `spring.profiles.active` inside any profile-specific configuration file**.
4. In IntelliJ's **Run → Edit Configurations → PackFlowApplication → Environment variables**, set `JWT_SECRET` to a secure random secret of at least 32 UTF-8 bytes. The Spring Boot project does not read `.env` automatically. Use `.env.example` as a list of available variables.
5. Ensure PostgreSQL is running; start `PackFlowApplication`. There is no need to set `spring.profiles.active` for normal local runs: `application.yml` already defaults to `local`.

## Local demo administrator (one environment variable)

The demo manager username is always **`admin`**. In IntelliJ's **Run → Edit Configurations → Environment variables**, set `ADMIN_PASSWORD` to your own 8–72-character password and start the backend. When `local` is active, the application creates an enabled `MANAGER` named `admin` if it does not exist, even if another manager already exists. Login with username `admin` and your configured password via `POST /api/auth/login`.

On subsequent local restarts, `ADMIN_PASSWORD` remains the password for this demo account. Changing it resets this account's password and invalidates previous JWTs. If an existing `admin` account is disabled or has a different role, the bootstrap does **not** silently elevate it: use the existing manager account to resolve that conflict. An empty `ADMIN_PASSWORD` skips demo account bootstrap.

**Production:** this bootstrap runs only under the `local` Spring profile; production must explicitly activate a separate profile and provision managed administrator accounts through an approved deployment process. Never use the `local` profile or demo account on an exposed deployment. The previous `APP_BOOTSTRAP_ADMIN_USERNAME` and `APP_BOOTSTRAP_ADMIN_PASSWORD` variables are no longer used.

Flyway V4 disables the three historical demo accounts only when they still use the published passwords. On first startup after V3, previously issued tokens must be replaced by logging in again. Integration tests re-enable fixture users only in isolated Testcontainers PostgreSQL.

## Verification

Run `mvn test` with Java 21 and Docker available: integration tests use Testcontainers PostgreSQL. Before deploying, verify local-profile values are not used in production and supply production-specific database, JWT and bootstrap settings.
