#!/usr/bin/env bash
# Build the shared iOS wrapper with Android error-detail methods in a temporary copy.
set -euo pipefail

ROOT=$(cd "$(dirname "$0")/.." && pwd)
SOURCE=${TAILCAT_SOURCE_DIR:-"$ROOT/../ios/TailcatKit"}
NDK_VERSION=28.2.13676358

fail() { echo "error: $*" >&2; exit 1; }
[[ -f "$SOURCE/go.mod" && -f "$SOURCE/go.sum" ]] ||
  fail "TailcatKit source is missing. Initialize packages/ios or set TAILCAT_SOURCE_DIR."
SOURCE=$(cd "$SOURCE" && pwd)
command -v go >/dev/null || fail "Install Go and add it to PATH."
command -v javac >/dev/null || fail "Install JDK 21 and set JAVA_HOME/PATH."
: "${ANDROID_HOME:=${ANDROID_SDK_ROOT:-}}"
[[ -d "$ANDROID_HOME/platforms" ]] || fail "Set ANDROID_HOME to the Android SDK directory."
export ANDROID_HOME
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-"$ANDROID_HOME/ndk/$NDK_VERSION"}
[[ -f "$ANDROID_NDK_HOME/source.properties" ]] ||
  fail "Install the NDK: sdkmanager 'ndk;$NDK_VERSION', or set ANDROID_NDK_HOME."

GO_VERSION=$(awk '$1 == "go" {print $2; exit}' "$SOURCE/go.mod")
MOBILE_VERSION=$(awk '$1 == "golang.org/x/mobile" {print $2; exit}' "$SOURCE/go.mod")
[[ -n "$GO_VERSION" && -n "$MOBILE_VERSION" ]] || fail "Cannot read Go/mobile versions from go.mod."
export GOTOOLCHAIN=${GOTOOLCHAIN:-"go$GO_VERSION"}
export GOWORK=off

WORK=$(mktemp -d)
trap 'rm -rf "$WORK"' EXIT
mkdir -p "$WORK/source" "$WORK/bin" "$ROOT/tailcat/libs"
(cd "$SOURCE" && tar --exclude='./build' --exclude='./.git' -cf - .) | tar -xf - -C "$WORK/source"
cp "$ROOT"/tailcat/go/*.go "$WORK/source/"
cd "$WORK/source"
go test ./...
export PATH="$WORK/bin:$PATH"
echo "Installing gomobile and gobind at $MOBILE_VERSION with $GOTOOLCHAIN"
GOBIN="$WORK/bin" go install \
  "golang.org/x/mobile/cmd/gomobile@$MOBILE_VERSION" \
  "golang.org/x/mobile/cmd/gobind@$MOBILE_VERSION"
gomobile init
gomobile bind \
  -target android/arm64,android/arm,android/amd64 \
  -androidapi 26 \
  -trimpath \
  -ldflags "-s -w" \
  -o "$WORK/tailcatkit.aar" \
  .

# Publish only a complete archive. A failed build preserves any previous archive.
cp "$SOURCE/LICENSE.tailcat" "$ROOT/tailcat/libs/LICENSE.tailcat"
mv "$WORK/tailcatkit.aar" "$ROOT/tailcat/libs/tailcatkit.aar"
echo "Built $ROOT/tailcat/libs/tailcatkit.aar"
du -h "$ROOT/tailcat/libs/tailcatkit.aar"
