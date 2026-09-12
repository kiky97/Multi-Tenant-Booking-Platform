# Testing and CI

## Backend Verification

From `backend/`:

```bash
./mvnw clean test
./mvnw clean verify
./mvnw spotbugs:spotbugs
./mvnw org.owasp:dependency-check-maven:check
./mvnw -Psecurity-scan verify
```

Windows PowerShell:

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd clean verify
.\mvnw.cmd spotbugs:spotbugs
.\mvnw.cmd org.owasp:dependency-check-maven:check
.\mvnw.cmd -Psecurity-scan verify
```

Notes:

- `clean test` runs the automated backend test suite and JaCoCo checks configured for service and controller packages.
- `clean verify` runs the Maven verify lifecycle.
- `spotbugs:spotbugs` is a manual static-analysis report command; it is not bound to `clean verify` or GitHub Actions by default.
- `org.owasp:dependency-check-maven:check` generates Dependency Check reports under `backend/target/dependency-check`.
- `-Psecurity-scan verify` enables the fail-closed dependency scan profile.

## Frontend Verification

From `frontend/`:

```bash
npm ci
npm run lint
npm run test
npm run build
```

The frontend test stack uses Vitest, Testing Library, ESLint, and Vite production builds.

## GitHub Actions

`.github/workflows/ci.yml` currently defines these jobs:

- `backend-tests`: sets up Java 21 and runs `./mvnw -B clean test` from `backend/`.
- `backend-dependency-security-scan`: runs OWASP Dependency Check with the `security-scan` profile and `NVD_API_KEY` from repository secrets when available.
- `frontend`: sets up Node.js 20, runs `npm ci`, `npm run lint`, `npm run test`, and `npm run build` from `frontend/`.

SpotBugs is available as a manual Maven report command, but it is not currently enforced by GitHub Actions.
