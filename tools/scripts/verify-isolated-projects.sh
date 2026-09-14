#!/bin/bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir/../.."

temp_dir="${RUNNER_TEMP:-$(mktemp -d)}"
cache_dir="$temp_dir/foundry-isolated-projects-project-cache"
check_log="$temp_dir/foundry-isolated-projects-check.log"
help_first_log="$temp_dir/foundry-isolated-projects-help-first.log"
help_second_log="$temp_dir/foundry-isolated-projects-help-second.log"
common_args=(
  --configuration-cache
  --configuration-cache-problems=fail
  --isolated-projects
  -Dorg.gradle.projectcachedir="$cache_dir"
  --console=plain
)
check_args=(
  check
  # Spotless snapshots the project tree before filtering source targets. Running this production
  # graph in parallel can race test tasks that create reports under build/.
  --no-parallel
  "${common_args[@]}"
)
help_args=(help --no-parallel "${common_args[@]}")

./gradlew "${check_args[@]}" 2>&1 | tee "$check_log"
grep -F "Configuration cache entry stored." "$check_log"

# Isolated projects configures every project even for `help`, without executing tasks that mutate
# IntelliJ Platform state. This makes it a stable production configuration-cache reuse probe.
./gradlew "${help_args[@]}" 2>&1 | tee "$help_first_log"
grep -F "Configuration cache entry stored." "$help_first_log"
./gradlew "${help_args[@]}" 2>&1 | tee "$help_second_log"
grep -F "Reusing configuration cache." "$help_second_log"

./gradlew :platforms:gradle:foundry-gradle-plugin:test \
  --tests foundry.gradle.ProjectIsolationFunctionalTest \
  --rerun-tasks \
  --configuration-cache \
  --configuration-cache-problems=fail \
  --isolated-projects \
  --console=plain \
  2>&1 | tee "$temp_dir/foundry-project-isolation-functional-test.log"
