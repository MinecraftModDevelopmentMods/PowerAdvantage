#!/usr/bin/env bash
set -euo pipefail

repository="${1:?Usage: stage-reborncore-release.sh <repository-directory>}"
value() { sed -n "s/^$1=//p" gradle.properties; }

project_id="$(value reborncore_curse_project_id)"
file_id="$(value reborncore_curse_file_id)"
expected_sha="$(value reborncore_sha256)"
download_url="$(value reborncore_download_url)"
for required in project_id file_id expected_sha download_url; do
  if [[ -z "${!required}" ]]; then
    echo "Missing RebornCore release metadata: $required" >&2
    exit 1
  fi
done

artifact_dir="$repository/curse/maven/reborncore-$project_id/$file_id"
artifact="$artifact_dir/reborncore-$project_id-$file_id.jar"
pom="$artifact_dir/reborncore-$project_id-$file_id.pom"
mkdir -p "$artifact_dir"
curl --fail --location --silent --show-error \
  --retry 5 --retry-all-errors --retry-delay 5 \
  "$download_url" \
  --output "$artifact"

actual_sha="$(sha256sum "$artifact" | awk '{print toupper($1)}')"
if [[ "$actual_sha" != "${expected_sha^^}" ]]; then
  echo "RebornCore file $file_id has SHA-256 $actual_sha, expected ${expected_sha^^}" >&2
  exit 1
fi
printf '%s\n' \
  '<?xml version="1.0" encoding="UTF-8"?>' \
  '<project xmlns="http://maven.apache.org/POM/4.0.0">' \
  '  <modelVersion>4.0.0</modelVersion>' \
  '  <groupId>curse.maven</groupId>' \
  "  <artifactId>reborncore-$project_id</artifactId>" \
  "  <version>$file_id</version>" \
  '</project>' > "$pom"
echo "Staged exact RebornCore file $file_id in $repository"
