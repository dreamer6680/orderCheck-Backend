# OrderCheck backend

Spring Boot 3.5 / Java 21 / PostgreSQL. **All committed Spring configuration is in [`src/main/resources/application.yaml`](src/main/resources/application.yaml)**. It contains shared settings and `local` / `prod` profile-specific sections in one file.

## Start locally in IntelliJ

1. Update your checkout to the current `master` branch. In your local project, **remove or rename any previous `src/main/resources/application-local.yml` and `application-local.yaml`**; they are ignored by Git and may still override the new file or contain the invalid `spring.profiles.default` property. The old `application.yml` was replaced by `application.yaml`.
2. Edit the `local` section at the end of `src/main/resources/application.yaml` if necessary. The defaults are PostgreSQL at `localhost:5432/packflow`, DB username/password `packflow`, local development JWT signing key, and demo administrator password `123456`.
3. Start PostgreSQL, rebuild the backend and run `PackFlowApplication`. The default Spring profile is `local`. **No IntelliJ environment variables, `.env` file or separate `application-local.yaml` is required for local development with the default values.**
4. Login using `POST /api/auth/login` with username **`admin`** and password **`123456`** (or the password you set at `app.demo.admin-password` in the local YAML section).

The local-only `AdminBootstrap` creates an enabled manager called `admin` on first startup, even if other managers exist. A changed demo password updates the existing active manager's password at next startup and invalidates its previously issued JWTs. If an existing `admin` has a different role or is disabled, the application will **not** silently elevate it; fix that record through user management. Leave `app.demo.admin-password` blank if you do not want the demo account initialized.

**Important:** The demo password and local JWT secret in `application.yaml` are development-only, public values, not production credentials. Never run the `local` profile on a publicly reachable deployment or connect it to real business data. For a shared development server, use your own secret/password and prevent the values from being committed.

## Production

Explicitly activate `prod` (`SPRING_PROFILES_ACTIVE=prod`) and supply `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `JWT_SECRET` externally. Production configuration is declared in the `prod` section of `application.yaml` without default secrets or demo credentials. The demo-admin bean is only active under `local`; provision production users separately.

Flyway V4 disables the legacy seeded users only if they still use the published initial passwords. Following Flyway V3, earlier tokens require a fresh login. Integration tests provision their legacy fixture users in a separate Testcontainers PostgreSQL instance.

## Verification

Run `mvn test` with Java 21 and Docker; integration tests require Testcontainers PostgreSQL.
