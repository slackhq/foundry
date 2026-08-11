#!/bin/bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir/../.."

temp_dir="${RUNNER_TEMP:-$(mktemp -d)}"
cache_dir="$temp_dir/foundry-project-cache"
args=(
  check
  --configuration-cache
  --configuration-cache-problems=fail
  -Dorg.gradle.unsafe.isolated-projects=true
  -Dorg.gradle.projectcachedir="$cache_dir"
  --console=plain
)

# IntelliJ Platform creates its layout index during the first check, after Gradle has captured
# configuration inputs. Bootstrap that state before checking cache reuse.
./gradlew "${args[@]}" 2>&1 | tee "$temp_dir/foundry-bootstrap.log"
./gradlew "${args[@]}" 2>&1 | tee "$temp_dir/foundry-first.log"
./gradlew "${args[@]}" 2>&1 | tee "$temp_dir/foundry-second.log"
grep -F "Reusing configuration cache." "$temp_dir/foundry-second.log"
