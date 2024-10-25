# Source this file to use its functions

# Gets a property out of a .properties file
# usage: getProperty $key $filename
function getProperty() {
  grep "${1}" "$2" | cut -d'=' -f2
}

# Increments an input version string given a version type
# usage: increment_version $current_version $version_type
increment_version() {
    local current_version=$1
    local version_type=$2
    IFS='.' read -r major minor patch <<< "$current_version"
    case "$version_type" in
        major) ((major++)); minor=0; patch=0 ;;
        minor) ((minor++)); patch=0 ;;
        patch) ((patch++)) ;;
    esac
    echo "$major.$minor.$patch"
}