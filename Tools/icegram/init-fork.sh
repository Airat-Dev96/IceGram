#!/bin/sh
# Restore Git metadata for the downloaded source snapshot without replacing files.
# Run on a computer with Git, or in a Linux environment with Git enabled.
# ICEGRAM_METADATA_ONLY=1 skips blobs and submodules when preparing a CI upload.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../.." && pwd)
UPSTREAM_COMMIT=b4d852f48f2734a0bee1a74a0939be621612262a
command -v git >/dev/null 2>&1 || { echo 'Git is required.' >&2; exit 1; }
if [ -e "$ROOT/.git" ]; then
    echo 'Git metadata already exists. Nothing changed.' >&2
    echo 'Initialize dependencies with: git submodule update --init --recursive --depth=1' >&2
    exit 1
fi
git init -b icegram "$ROOT"
git -C "$ROOT" remote add upstream https://github.com/exteraSquad/exteraGram.git
if [ "${ICEGRAM_METADATA_ONLY:-0}" = 1 ]; then
    git -C "$ROOT" config remote.upstream.promisor true
    git -C "$ROOT" config remote.upstream.partialclonefilter blob:none
    git -C "$ROOT" -c protocol.version=2 fetch --filter=blob:none --depth=1 upstream "$UPSTREAM_COMMIT"
else
    git -C "$ROOT" fetch --depth=1 upstream "$UPSTREAM_COMMIT"
fi
git -C "$ROOT" update-ref refs/heads/icegram FETCH_HEAD
# Index only: preserves the icegram working-tree changes and the upstream gitlinks.
git -C "$ROOT" read-tree FETCH_HEAD
if [ "${ICEGRAM_METADATA_ONLY:-0}" = 1 ]; then
    echo 'Metadata-only mode: submodules will be fetched by GitHub Actions.'
else
    git -C "$ROOT" submodule update --init --recursive --depth=1
fi
echo 'icegram branch is ready. Review git status, then commit your changes.'
