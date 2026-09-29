#!/bin/bash
set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$script_dir/../.."

temp_dir="${RUNNER_TEMP:-$(mktemp -d)}"
cache_dir="$(mktemp -d "$temp_dir/foundry-cli-project-cache.XXXXXX")"
service_file="META-INF/services/foundry.cli.CommandFactory"

# runCommand() discovers commands through Java ServiceLoader; KSP generates this descriptor.
./gradlew :tools:cli:jar \
  --configuration-cache \
  --configuration-cache-problems=fail \
  --isolated-projects \
  -Dorg.gradle.projectcachedir="$cache_dir" \
  --console=plain

cli_jar="tools/cli/build/libs/cli.jar"
test -f "$cli_jar"
unzip -p "$cli_jar" "$service_file" | tee "$temp_dir/foundry-cli-command-factories"

# The dollar signs are literal JVM nested-class separators.
# shellcheck disable=SC2016
expected_factories=(
  'foundry.cli.gradle.GradleProjectFlattenerCli$Factory'
  'foundry.cli.gradle.GradleSettingsVerifierCli$Factory'
  'foundry.cli.gradle.GradleTestFixturesMigratorCli$Factory'
  'foundry.cli.lint.LintBaselineMergerCli$Factory'
  'foundry.cli.sarif.ApplyBaselinesToSarifs$Factory'
  'foundry.cli.sarif.MergeSarifReports$Factory'
  'foundry.cli.shellsentry.ShellSentryCli$Factory'
)

for factory in "${expected_factories[@]}"; do
  grep -Fqx "$factory" "$temp_dir/foundry-cli-command-factories"
done
