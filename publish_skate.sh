#!/usr/bin/env bash

source tools/scripts/scriptUtil.sh

changeNotes=platforms/intellij/skate/change-notes.html
# Default values
specific_version=""
increment_type=""

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --major|--minor|--patch)
            increment_type="${1/--/}"  # Remove -- prefix
            ;;
        *)
            specific_version="$1"  # Assume it's a specific version
            ;;
    esac
    shift
done

# Fetch the latest version from the changelog if no specific version provided
if [[ -z "$specific_version" ]]; then
    latest_version=$(awk '/<h2>[0-9]+\.[0-9]+\.[0-9]+<\/h2>/ {
        gsub(/<\/?h2>/, "", $0);  # Remove <h2> tags
        print $0;
        exit;
    }' $changeNotes)
    version=$(increment_version "$latest_version" "$increment_type")
else
    version="$specific_version"
fi

# Export the new version for Gradle's version name
export ORG_GRADLE_PROJECT_VERSION_NAME=$version

# Update change-notes.xml
# Use inline editing with compatibility for both macOS and Linux
awk -v version="$version" '
    /<h2>Unreleased<\/h2>/ {
        print;
        getline;  # Move to the next line containing <ul>...</ul>
        print "<ul>\n</ul>";
        print "\n<h2>" version "</h2>";
        print $0;  # Print the original <ul> line, maintaining content
        next;
    }
    { print }
' $changeNotes > tmpfile && mv tmpfile $changeNotes

./gradlew :platforms:intellij:skate:uploadPluginToArtifactory --no-configuration-cache --stacktrace

# Prepare release
git commit -am "Prepare for Skate release $ORG_GRADLE_PROJECT_VERSION_NAME."
git tag -a "skate-$ORG_GRADLE_PROJECT_VERSION_NAME" -m "Skate Version $ORG_GRADLE_PROJECT_VERSION_NAME"

# Push it all up
git push && git push --tags