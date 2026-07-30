#!/bin/sh
if command -v gradle >/dev/null 2>&1; then exec gradle "$@"; fi
echo "Gradle is not installed. Push to GitHub and use the included Actions workflow." >&2
exit 1
