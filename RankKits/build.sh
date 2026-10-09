#!/bin/sh
set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SERVER_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
PAPER_API="$SERVER_ROOT/libraries/io/papermc/paper/paper-api/26.2.build.121-stable/paper-api-26.2.build.121-stable.jar"

if [ ! -f "$PAPER_API" ]; then
    printf '%s\n' "Paper API was not found at $PAPER_API" >&2
    exit 1
fi

BUILD_DIR=$(mktemp -d "${TMPDIR:-/tmp}/rankkits-build.XXXXXX")
trap 'rm -rf "$BUILD_DIR"' EXIT HUP INT TERM
CLASSPATH="$PAPER_API:$(find "$SERVER_ROOT/libraries" -type f -name '*.jar' -print | paste -sd: -)"

mkdir -p "$BUILD_DIR/classes"
javac --release 21 -encoding UTF-8 -classpath "$CLASSPATH" \
    -d "$BUILD_DIR/classes" \
    "$SCRIPT_DIR/src/main/java/net/itsvoid/rankkits/RankKits.java"
cp -R "$SCRIPT_DIR/src/main/resources/." "$BUILD_DIR/classes/"
jar --create --file "$SERVER_ROOT/plugins/RankKits-1.0.0.jar" -C "$BUILD_DIR/classes" .
printf '%s\n' "Built $SERVER_ROOT/plugins/RankKits-1.0.0.jar"
