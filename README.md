# OrderCheck backend

Spring Boot 3.5 / Java 21 / PostgreSQL. The default Spring profile is `local`, configured **only in `src/main/resources/application.yml`**.

## Local development with `application-local.yaml`

1. Copy `src/main/resources/application-local.example.yaml` to `src/main/resources/application-local.yaml`.
2. The target file is intentionally ignored by Git. Edit its database settings to match your local PostgreSQL.
3. If you already have an `application-local.yml`, move your custom settings into the new `application-local.yaml` and rename or remove the old file. In particular, **never put `spring.profiles.default` or `spring.profiles.active` inside any profile-specific configuration file**.
4. In IntelliJ's **Run → Edit Configurations → PackFlowApplication → Environment variables**, set `JWT_SECRET` to a secure random secret of at least 32 UTF-8 bytes. The Spring Boot project does not read `.env` automatically. Use `.env.example` as a list of available variables.
5. Ensure PostgreSQL is running; start `PackFlowApplication`. There is no need to set `spring.profiles.active` for normal local runs: `application.yml` already defaults to `local`.

## First admin account after the security migration

Flyway V4 disables the three historical demo accounts **only when their stored password hashes still equal the published default hashes**. If no active manager exists, set **both** `APP_BOOTSTRAP_ADMIN_USERNAME` (a distinct 3–50-character username) and `APP_BOOTSTRAP_ADMIN_PASSWORD` (12–72 characters) as environment variables before the first startup. The application creates one real manager only when there is no active manager and will not overwrite or reactivate an existing account. After successful bootstrap, remove the bootstrap password from your persistent Run Configuration/environment to reduce credential exposure.

On first startup after V3, previously issued tokens must be replaced by logging in again. The known demo account passwords are not intended for regular development or production. Integration tests re-enable demo users **only** in their isolated Testcontainers PostgreSQL database.

## Verification

Run `mvn test` with Java 21 and Docker available: integration tests use Testcontainers PostgreSQL. Before deploying, verify local-profile values are not used in production and supply production-specific database, JWT and bootstrap settings.
