#!/bin/bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir/../.."

temp_dir="${RUNNER_TEMP:-$(mktemp -d)}"
cache_dir="$temp_dir/foundry-cli-project-cache"
service_file="META-INF/services/foundry.cli.CommandFactory"

# runCommand() discovers commands through Java ServiceLoader; KSP generates this descriptor.
# KSP requires isolated projects to be disabled for the CLI packaging check.
./gradlew :tools:cli:jar \
  --configuration-cache-problems=fail \
  -Dorg.gradle.isolated-projects=false \
  -Dorg.gradle.projectcachedir="$cache_dir" \
  --console=plain

cli_jar="tools/cli/build/libs/cli.jar"
test -f "$cli_jar"
unzip -p "$cli_jar" "$service_file" | tee "$temp_dir/foundry-cli-command-factories"
grep -Fqx 'foundry.cli.gradle.GradleProjectFlattenerCli$Factory' \
  "$temp_dir/foundry-cli-command-factories"
