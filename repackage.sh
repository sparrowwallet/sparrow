#!/bin/bash
set -e  # Exit on any error

# Define paths
BUILD_DIR="build"
JPACKAGE_DIR="$BUILD_DIR/jpackage"
TEMP_DIR="$BUILD_DIR/repackage"

# Find the .deb file in build/jpackage (assuming there is only one)
DEB_FILE=$(find "$JPACKAGE_DIR" -type f -name "*.deb" -print -quit)

# Check if a .deb file was found
if [ -z "$DEB_FILE" ]; then
  echo "Error: No .deb file found in $JPACKAGE_DIR"
  exit 1
fi

# Extract the filename from the path for later use
DEB_FILENAME=$(basename "$DEB_FILE")

echo "Found .deb file: $DEB_FILENAME"

# Create a temp directory inside build to avoid file conflicts
mkdir -p "$TEMP_DIR"
cd "$TEMP_DIR"

# Extract the .deb file contents
ar x "../../$DEB_FILE"

# Decompress zst files to tar
unzstd control.tar.zst
unzstd data.tar.zst

# Compress tar files to xz
xz -c control.tar > control.tar.xz
xz -c data.tar > data.tar.xz

# Remove the original .deb file
rm "../../$DEB_FILE"

# Create the new .deb file with xz compression in the original location
ar cr "../../$DEB_FILE" debian-binary control.tar.xz data.tar.xz

# Clean up temp files
cd ../..
rm -rf "$TEMP_DIR"

echo "Repackaging complete: $DEB_FILENAME"
