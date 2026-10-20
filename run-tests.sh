#!/usr/bin/env bash
# Runs the tests of both services inside a Maven container, so no local JDK or Maven is needed.
# The Docker socket is shared so Testcontainers can start PostgreSQL and RabbitMQ for the integration tests.
#
# Phase 1 runs the consumer contract tests of every service, which (re)write the pact files in /pacts.
# Phase 2 runs every test, including the provider tests that verify those pact files.
set -euo pipefail

cd "$(dirname "$0")"

SERVICES=(account-service transaction-service)

run_maven() {
  local service=$1
  shift
  docker run --rm \
    -v "$PWD":/workspace -w "/workspace/$service" \
    -v simplebank-m2:/root/.m2 \
    -v /var/run/docker.sock:/var/run/docker.sock \
    -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
    maven:3.9-eclipse-temurin-21 \
    mvn -B "$@"
}

for service in "${SERVICES[@]}"; do
  echo "==> Generating contracts: $service"
  run_maven "$service" test -Dtest='*ConsumerPactTest' -Dsurefire.failIfNoSpecifiedTests=false
done

for service in "${SERVICES[@]}"; do
  echo "==> Testing $service"
  run_maven "$service" test
done
