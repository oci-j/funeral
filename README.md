# FUNERAL

[![Backend Coverage](https://img.shields.io/endpoint?url=https://oci-j.github.io/funeral/coverage-backend.json)](https://oci-j.github.io/funeral/backend/coverage.html)
[![Frontend Coverage](https://img.shields.io/endpoint?url=https://oci-j.github.io/funeral/coverage-frontend.json)](https://oci-j.github.io/funeral/frontend/coverage.html)

FUNERAL is an oci image registry following [oci-distribution-spec](https://github.com/opencontainers/distribution-spec.git)

In java.

# Demo

There be a demo page at https://funeral.xenoamess.com , at AUTH_ENABLED=false, NO_MONGO=true, NO_MINIO=true mode.

Related docker image at here https://hub.docker.com/r/xenoamess/funeral

# Usage

Startup in no auth mode:

```shell
export AUTH_ENABLED=false
./funeral-0.1.8-runner
```

Or, startup in default auth mode:

```shell
export AUTH_REALM=http://your-local-ip:8911/v2/token
./funeral-0.1.8-runner
```

Otherwise, you can copy&modify the [application.yml](funeral-backend/src/main/resources/application.yml) to config minio & mongo connection & other configs

Like this:

```shell
./funeral-0.1.8-runner -Dquarkus.config.locations=file:/home/xenoamess/funeral/application.yml
```

# Develop

1. setup

```shell
cd funeral-frontend
pnpm install
pnpm build
cd ../funeral-backend
mvn quarkus:dev
```

2. run tests

```shell
cd funeral-frontend
pnpm run test:coverage

cd ../funeral-backend
mvn test
```

3. run with local disk storage (no MongoDB/MinIO required)

```shell
export NO_MONGO=true
export NO_MINIO=true
export LOCAL_STORAGE_PATH=/tmp/funeral-storage
./funeral-0.2.0-runner
```

# Current Status

The project has a full test suite and CI pipeline:

- Backend: `mvn test` passes; OCI Distribution Spec conformance tests pass (74/79 runnable specs, 5 skipped)
- Frontend: `pnpm run test:coverage` passes with high coverage
- CI: GitHub Actions builds JVM and native binaries, runs unit tests, performs native smoke tests, and verifies Docker push/pull against a Funeral registry

```
xenoamess@xenoamessum890pro:~/workspace/distribution-spec/conformance$ ./conformance.test
Running Suite: conformance tests - /home/xenoamess/workspace/distribution-spec/conformance
==========================================================================================
Random Seed: 1755533753

Will run 79 of 79 specs
•••••S••••••••••••••••••••••••••••••••••••S•SS••••••••••S•••••••••••••••••••••••••••••••
HTML report was created: /home/xenoamess/workspace/distribution-spec/conformance/report.html

Ran 74 of 79 Specs in 1.636 seconds
SUCCESS! -- 74 Passed | 0 Failed | 0 Pending | 5 Skipped
PASS
```

Note: filtering by `artifact-type` is not yet implemented (one of the skipped conformance specs).

# Short-Term Goal

This repo is a subject of a bigger project to make OCI better, with less bandwidth.
[Add an import mechanism to OCI image format, to reduce bandwidth cost on image upgrades](https://github.com/users/XenoAmess/projects/1)

The main efforts are on:

1. Improve test coverage and CI reliability.
2. Refine OCI Pull/Push API conformance.
3. Make the registry usable for the `image import block` research.

# Ultimate Goal

The ultimate goal for the repo is to be used as a light-weighted image registry.

It would focus more on usability, and become straightforward to use/establish.

1. make middlewares optional (done: local disk storage with `NO_MONGO=true` and `NO_MINIO=true`)
2. more light-weighted (done: GraalVM native binary releases)
3. stability refines
4. storage compression (long-term research goal)
