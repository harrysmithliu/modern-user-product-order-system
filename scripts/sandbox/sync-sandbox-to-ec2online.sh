#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
SOURCE_BRANCH="${SOURCE_BRANCH:-sandbox}"
TARGET_BRANCH="${TARGET_BRANCH:-sandbox-ec2-online}"
COMMIT_MESSAGE="${COMMIT_MESSAGE:-chore: sync sandbox changes into ec2online excluding prod paths}"

cd "${ROOT_DIR}"

if git rev-parse -q --verify MERGE_HEAD >/dev/null; then
  echo "A merge is already in progress. Commit or abort it first."
  exit 1
fi

if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
  echo "Tracked changes already exist in the worktree. Commit or stash them first."
  git status --short
  exit 1
fi

git fetch origin --prune
git checkout "${TARGET_BRANCH}"
git pull --ff-only origin "${TARGET_BRANCH}"

candidates=()
while IFS= read -r path; do
  [[ -n "${path}" ]] || continue
  candidates+=("${path}")
done < <(git diff --name-only "origin/${TARGET_BRANCH}..origin/${SOURCE_BRANCH}")

picked_paths=()
removed_paths=()
skipped_paths=()

for path in "${candidates[@]}"; do
  case "${path}" in
    infra/aws/prod/*|infra/k8s/prod/*|infra/aws/sandbox-ec2/*)
      skipped_paths+=("${path}")
      continue
      ;;
  esac

  if git cat-file -e "origin/${SOURCE_BRANCH}:${path}" 2>/dev/null; then
    git checkout "origin/${SOURCE_BRANCH}" -- "${path}"
    picked_paths+=("${path}")
  else
    if git cat-file -e "origin/${TARGET_BRANCH}:${path}" 2>/dev/null; then
      git rm -f -- "${path}"
      removed_paths+=("${path}")
    else
      skipped_paths+=("${path}")
    fi
  fi
done

if [[ "${#picked_paths[@]}" -eq 0 && "${#removed_paths[@]}" -eq 0 ]]; then
  echo "No shared files need to be synced."
  exit 0
fi

if [[ "${#picked_paths[@]}" -gt 0 ]]; then
  git add -- "${picked_paths[@]}"
fi

echo "Picked paths:"
printf '  %s\n' "${picked_paths[@]}"

if [[ "${#skipped_paths[@]}" -gt 0 ]]; then
  echo "Skipped paths:"
  printf '  %s\n' "${skipped_paths[@]}"
fi

if [[ "${#removed_paths[@]}" -gt 0 ]]; then
  echo "Removed paths:"
  printf '  %s\n' "${removed_paths[@]}"
fi

git commit -m "${COMMIT_MESSAGE}"
git push origin "${TARGET_BRANCH}"
