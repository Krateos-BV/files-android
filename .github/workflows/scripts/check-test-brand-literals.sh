#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 XeniaCloud
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Fails when a Nextcloud brand string literal appears in fork test code without
# being accounted for in the allowlist beside this script.
#
# Why only string literals: a renamed identifier breaks the build, so
# `NextcloudVersion`, `NextcloudClient` and friends cannot rot unnoticed. A
# quoted path, filename or account name can — it keeps compiling and the test
# keeps passing while it stops describing the product. That is XNT-125 and
# XNT-170, twice, and a grep for `nextcloud` across these directories matches
# over a thousand lines of legitimate package names, so it can never pass and
# therefore proves nothing.
#
# Adding a literal is not forbidden; it has to be justified once, in
# test-brand-literals-allow.txt, where a reviewer sees it.

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/../../.."

readonly ALLOWLIST=".github/workflows/scripts/test-brand-literals-allow.txt"
readonly SCAN_DIRS=("app/src/test" "app/src/androidTest")
readonly BRAND='[Nn][Ee][Xx][Tt][Cc][Ll][Oo][Uu][Dd]'

if [[ ! -f $ALLOWLIST ]]; then
    echo "::error::allowlist not found at $ALLOWLIST"
    exit 1
fi

# Rules are extended regexes matched against "<path>|<literal>", one per line.
rules=()
while IFS= read -r rule; do
    [[ -z ${rule// } || $rule == \#* ]] && continue
    rules+=("$rule")
done <"$ALLOWLIST"

if ((${#rules[@]} == 0)); then
    echo "::error file=$ALLOWLIST::allowlist holds no rules; the scan would be vacuous"
    exit 1
fi

used=()
for _ in "${rules[@]}"; do used+=(0); done

findings=0
while IFS= read -r -d '' file; do
    lineno=0
    while IFS= read -r line; do
        lineno=$((lineno + 1))

        # Imports, package declarations and comments carry the upstream package
        # name and copyright headers by design; none of them is a test fixture.
        [[ $line =~ ^[[:space:]]*(import|package)[[:space:]] ]] && continue
        [[ $line =~ ^[[:space:]]*(//|/\*|\*) ]] && continue
        [[ $line =~ $BRAND ]] || continue

        # One line can hold several literals; check each on its own.
        while IFS= read -r literal; do
            [[ $literal =~ $BRAND ]] || continue

            subject="$file|$literal"
            allowed=0
            for i in "${!rules[@]}"; do
                if [[ $subject =~ ${rules[i]} ]]; then
                    used[i]=1
                    allowed=1
                fi
            done

            if ((allowed == 0)); then
                echo "::error file=$file,line=$lineno::unaccounted Nextcloud brand literal $literal — point it at the runtime accessor, or add a rule with a reason to $ALLOWLIST"
                findings=$((findings + 1))
            fi
        done < <(grep -oE '"[^"]*"' <<<"$line" || true)
    done <"$file"
done < <(find "${SCAN_DIRS[@]}" -type f \( -name '*.kt' -o -name '*.java' \) -print0 | sort -z)

# An allowlist entry that no longer matches anything is the same rot one step
# removed: it outlives the fixture it excused and silently widens the next scan.
stale=0
for i in "${!rules[@]}"; do
    if ((used[i] == 0)); then
        echo "::error file=$ALLOWLIST::rule no longer matches any literal, delete it: ${rules[i]}"
        stale=$((stale + 1))
    fi
done

if ((findings > 0 || stale > 0)); then
    echo "FAIL: $findings unaccounted brand literal(s), $stale stale allowlist rule(s)"
    exit 1
fi

echo "OK: every Nextcloud brand literal under ${SCAN_DIRS[*]} is accounted for by ${#rules[@]} allowlist rule(s)"
