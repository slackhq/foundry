#!/usr/bin/env bash

# Version to increment to, optional. If blank or not specified, will auto-increment.
REQUESTED_VERSION=$1
source .github/workflows/scriptUtil.sh
# Parse the current version, strip leading zeros, increment
CURRENT_VERSION=$(getProperty 'VERSION_NAME' gradle.properties)

STRIPPED_CURRENT=$(echo "$CURRENT_VERSION" | sed 's/^0*//')
if [[ "$REQUESTED_VERSION" != "" ]]; then
  NEW_VERSION=$REQUESTED_VERSION
else
  ((STRIPPED_CURRENT++))
  NEW_VERSION=$(printf "%05d\n" $STRIPPED_CURRENT)
fi
sed -i -e "s/${CURRENT_VERSION}/${NEW_VERSION}/g" gradle.properties
echo "current: $CURRENT_VERSION"
echo "CURRENT_VERSION=$CURRENT_VERSION" >> $GITHUB_ENV
echo "new: $NEW_VERSION"
echo "NEW_VERSION=$NEW_VERSION" >> $GITHUB_ENV
