#!/bin/bash

# This script requires two parameters
# 1. A mapping file of old properties to nw properties. This is available in the foundry repo at foundry-migration/mapping.txt
# 2. Project directory to search for .properties and .kts files in.
if [ "$#" -lt 2 ]; then
    echo "Usage: $0 <mapping_file> <directory_to_search>"
    exit 1
fi

# Input parameters
MAPPING_FILE="$1"
SEARCH_DIR="$2"

# Check if the mapping file exists
if [ ! -f "$MAPPING_FILE" ]; then
    echo "Mapping file '$MAPPING_FILE' not found!"
    exit 1
fi

# Find all .properties and .kts files in the directory and its subdirectories
find "$SEARCH_DIR" -type f \( -name "*.properties" -o -name "*.kts" \) | while read -r file; do
    echo "Processing file: $file"

    # Loop through each line in the mapping file
    while IFS="=" read -r old_string new_string; do
        # Skip empty lines or lines that don't contain '='
        [[ -z "$old_string" || -z "$new_string" ]] && continue
        # Use sed to perform the in-place replacement
        sed -i '' "s/$old_string/$new_string/g" "$file"
    done < "$MAPPING_FILE"
done


echo "Replacement completed."