#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

project_dir="$(cd "$(dirname "$0")/.." && pwd)"
aapt2_binary="$project_dir/.android-tools/current-aapt2/aapt2"

if [[ ! -x "$aapt2_binary" ]]; then
  echo "Missing current ARM64 aapt2 binary: $aapt2_binary" >&2
  exit 1
fi

cd "$project_dir"
exec ./gradlew -Pandroid.aapt2FromMavenOverride="$aapt2_binary" "$@"
