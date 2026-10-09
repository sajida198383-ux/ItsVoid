#!/bin/sh
set -eu

cd "$(dirname "$0")"
exec java -jar paper-26.2-121.jar nogui
