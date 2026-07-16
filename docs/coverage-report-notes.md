# Coverage Report Notes — oci-j/funeral

Date: 2026-07-12

## Project setup

- **Backend**: Quarkus 3.37.2 + Maven, JDK 25.
- **Frontend**: Vue 3 + Vite + Vitest with `@vitest/coverage-v8`.
- **CI**: GitHub Actions in `.github/workflows/build.yml`.
- **Coverage hosting**: GitHub Pages via shields.io endpoint badges.

## What worked

1. Using `jacoco-maven-plugin:0.8.15` directly instead of `quarkus-jacoco`.
   - `quarkus-jacoco` only instruments `@QuarkusTest` classes; the `build-jvm` job runs mostly plain JUnit tests, so the report was empty.
2. Running `mvn -B verify -Dtest='!*MirrorResourceTest'` to generate the backend report and exclude the network-dependent test.
3. Keeping the Vitest `coverage` config unchanged (`reporter: ['text', 'html', 'json-summary']`).
4. Uploading the raw `jacoco.exec` from `build-jvm` and merging it in `containerd-image-store-integration` with `jacoco:merge`.
5. Deploying only `pages/` to GitHub Pages with:
   - JSON badge endpoints at root.
   - Backend report under `/backend/coverage.html`.
   - Frontend report under `/frontend/coverage.html`.
6. Wrapping the shields.io images in Markdown links so the badge is clickable.

## Pitfalls hit

| Pitfall | Symptom | Fix |
|---|---|---|
| `quarkus-jacoco` does not cover plain JUnit | `target/jacoco-report/` empty after tests | Switched to `jacoco-maven-plugin` |
| `mvn test` does not trigger Quarkus report | No report directory | Used `mvn verify` |
| Artifact name mismatch | "Artifact not found" in coverage-pages job | Unified names with `${{ github.sha }}` and correct `needs:` |
| GitHub Pages not enabled | Badge JSON 404 | Enabled Pages via `gh api -X POST ... -f build_type=workflow` |
| Badge not clickable | Image only, no link | Wrapped `![alt](image)` in `[![alt](image)](url)` |
| `index.html` occupied | User wanted the slot freed | Renamed report entry to `coverage.html` |
| `download-artifact` path is a directory | Downloaded file landed in wrong place | Downloaded to `/tmp` and `mv` to target |

## Final URLs

- Backend badge JSON: `https://oci-j.github.io/funeral/coverage-backend.json`
- Frontend badge JSON: `https://oci-j.github.io/funeral/coverage-frontend.json`
- Backend report: `https://oci-j.github.io/funeral/backend/coverage.html`
- Frontend report: `https://oci-j.github.io/funeral/frontend/coverage.html`

## Follow-up ideas

- Consider running the full backend test suite (excluding `MirrorResourceTest`) in `build-jvm` for more representative coverage.
- Consider adding coverage thresholds once the project is stable, or keep the current "collect only, no gate" policy.
- If more CI jobs produce coverage in the future, reuse the `jacoco:merge` pattern.

## 2026-07-17 update

- `build-jvm` now runs the full `mvn -B test` **without any exclusions**:
  `MirrorResourceTest` was made offline via the `RegistryClient` abstraction
  (see `docs/complete-tasks-plan.md` phase B), so the `-Dtest='!*MirrorResourceTest'`
  exclusion mentioned above no longer exists in `build.yml`.
- `ImportExportIntegrationTest` now also passes locally: surefire sets
  `jdk.httpclient.allowRestrictedHeaders=Host` in `funeral-backend/pom.xml`,
  so the JDK "restricted header name: Host" failure noted below is resolved.

## Post-merge check

- 2026-07-12: Merged PR #28 (`build(deps-dev): bump vite from 5.4.21 to 8.1.4 in /funeral-frontend`).
- Next `main` run `Build Native Binary` (run `29189547557`) succeeded, including `Deploy Coverage Badges to GitHub Pages`.
- Confirmed the frontend coverage pipeline is still compatible with Vite 8.

## 2026-07-12 test expansion

- Added backend tests: `AdminResourceTest`, `OciV2ResourceTest`, extended `ManifestResourceHandlerTest` and `BlobResourceHandlerTest`.
- Added frontend tests: `src/stores/index.test.js`, extended `src/api/registry.test.js`.
- Fixed `AdminResource` password hashing: it used SHA-256 while `AuthService` verifies BCrypt, so admin-created users could not log in.
- Backend coverage (build-jvm, excluding `MirrorResourceTest` and `ImportExportIntegrationTest`): ~40% instruction (up from 26%).
- Frontend coverage: ~91% statements / 70% branches / 68% functions.
- Local run: `mvn -B verify -Dtest='!*MirrorResourceTest,!*ImportExportIntegrationTest'` succeeds; `ImportExportIntegrationTest` fails locally due to JDK "restricted header name: Host" but passes in CI.
- Coverage tooling remains a hybrid: `jacoco-maven-plugin` for plain JUnit plus `quarkus-jacoco` for `@QuarkusTest`, both writing to `target/jacoco-quarkus.exec`.

## Skill updates

The following items were folded back into `/home/xenoamess/workspace/coverage-report-skill/SKILL.md`:

- `quarkus-jacoco` only works for `@QuarkusTest`.
- `mvn verify` is required for Quarkus JaCoCo report generation.
- Renaming `index.html` to `coverage.html` frees the slot.
- `download-artifact` path is a directory even for single-file artifacts.
- After merging a dependency update that touches frontend tooling, verify the next `main` `coverage-pages` run.
