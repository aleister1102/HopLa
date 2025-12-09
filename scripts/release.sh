#!/bin/bash

# Ensure the script exits on any error
set -e

# Ensure we are in the project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_ROOT="$SCRIPT_DIR/.."
cd "$PROJECT_ROOT"

# Check required commands
if ! command -v gradle &> /dev/null; then
    echo "Error: 'gradle' command not found."
    exit 1
fi

if ! command -v gh &> /dev/null; then
    echo "Error: 'gh' command not found."
    exit 1
fi

if ! command -v git &> /dev/null; then
    echo "Error: 'git' command not found."
    exit 1
fi

# Get current version from build.gradle
# Assumes format: version = 'x.y.z'
CURRENT_VERSION=$(grep "^version =" build.gradle | sed "s/version = '//" | sed "s/'//")

if [ -z "$CURRENT_VERSION" ]; then
    echo "Error: Could not determine current version from build.gradle"
    exit 1
fi

echo "Current version: $CURRENT_VERSION"

# Determine bump type
BUMP_TYPE=$1
if [ -z "$BUMP_TYPE" ]; then
    echo "Select release type:"
    echo "1) Major"
    echo "2) Minor"
    echo "3) Patch"
    read -p "Enter choice [1-3]: " CHOICE
    case $CHOICE in
        1) BUMP_TYPE="major";;
        2) BUMP_TYPE="minor";;
        3) BUMP_TYPE="patch";;
        *) echo "Invalid choice"; exit 1;;
    esac
fi

# Parse version
IFS='.' read -r -a PARTS <<< "$CURRENT_VERSION"
MAJOR=${PARTS[0]}
MINOR=${PARTS[1]}
PATCH=${PARTS[2]}

# Calculate new version
case $BUMP_TYPE in
    major)
        MAJOR=$((MAJOR + 1))
        MINOR=0
        PATCH=0
        ;;
    minor)
        MINOR=$((MINOR + 1))
        PATCH=0
        ;;
    patch)
        PATCH=$((PATCH + 1))
        ;;
    *)
        echo "Error: Invalid bump type '$BUMP_TYPE'. Use major, minor, or patch."
        exit 1
        ;;
esac

NEW_VERSION="$MAJOR.$MINOR.$PATCH"
echo "Bumping version to: $NEW_VERSION"

# Update build.gradle
# macOS sed requires empty string extension for -i
sed -i '' "s/version = '$CURRENT_VERSION'/version = '$NEW_VERSION'/" build.gradle

# Update Constants.java
echo "Running gradle updateVersion..."
gradle updateVersion

# Build JAR
echo "Building JAR..."
gradle jar

# Define JAR path
JAR_PATH="releases/HopLa.jar"

if [ ! -f "$JAR_PATH" ]; then
    echo "Error: Built JAR not found at $JAR_PATH"
    exit 1
fi

echo "JAR built successfully at $JAR_PATH"

# Git operations
echo "Committing version bump..."
git add build.gradle src/main/java/com/hopla/Constants.java
git commit -m "Bump version to $NEW_VERSION"

# Push changes
echo "Pushing changes..."
git push

# Create GitHub Release
TAG="v$NEW_VERSION"
echo "Creating GitHub release $TAG..."
gh release create "$TAG" "$JAR_PATH" --generate-notes --title "$TAG"

echo "Success! Release $TAG published."
