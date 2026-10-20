#!/usr/bin/env bash
# Runs the tests of both services inside a Maven container, so no local JDK or Maven is needed.
# The Docker socket is shared so Testcontainers can start PostgreSQL for the integration tests.
set -euo pipefail

cd "$(dirname "$0")"

services=("$@")

if [ ${#services[@]} -eq 0 ]; then
  services=(account-service transaction-service)
fi

for service in "${services[@]}"; do
  echo "==> Testing $service"
  docker run --rm \
    -v "$PWD/$service":/app -w /app \
    -v simplebank-m2:/root/.m2 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    maven:3.9-eclipse-temurin-21 \
    mvn -B test
done
