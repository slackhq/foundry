#!/bin/bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir/../.."

temp_dir="${RUNNER_TEMP:-$(mktemp -d)}"
cache_dir="$temp_dir/foundry-isolated-projects-project-cache"
check_log="$temp_dir/foundry-isolated-projects-check.log"
common_args=(
  check
  --configuration-cache
  --configuration-cache-problems=fail
  --isolated-projects
  -Dorg.gradle.projectcachedir="$cache_dir"
  --console=plain
)

# Exercise the production check graph. The functional test below owns the cache-reuse assertion:
# it creates a multi-project fixture, then requires an identical second invocation to reuse its
# configuration cache.
./gradlew "${common_args[@]}" 2>&1 | tee "$check_log"
grep -F "Configuration cache entry stored." "$check_log"

./gradlew :platforms:gradle:foundry-gradle-plugin:test \
  --tests foundry.gradle.ProjectIsolationFunctionalTest \
  --rerun-tasks \
  --configuration-cache \
  --configuration-cache-problems=fail \
  --isolated-projects \
  --console=plain \
  2>&1 | tee "$temp_dir/foundry-project-isolation-functional-test.log"
