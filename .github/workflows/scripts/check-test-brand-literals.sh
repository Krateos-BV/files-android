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
# Globbed rather than listed by name so a new flavour test source set (e.g. a
# future app/src/androidTestFoo) is scanned automatically instead of silently
# falling outside the guard. XNT-221 (FN-3): androidTestGeneric and
# androidTestGplay were previously unscanned.
readonly SCAN_DIRS=(app/src/*[Tt]est*)
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

report_literal() {
    local file=$1 lineno=$2 literal=$3 note=$4
    local subject="$file|$literal"
    local allowed=0
    for i in "${!rules[@]}"; do
        if [[ $subject =~ ${rules[i]} ]]; then
            used[i]=1
            allowed=1
        fi
    done
    if ((allowed == 0)); then
        echo "::error file=$file,line=$lineno::unaccounted Nextcloud brand literal $literal$note — point it at the runtime accessor, or add a rule with a reason to $ALLOWLIST"
        findings=$((findings + 1))
    fi
}

findings=0
while IFS= read -r -d '' file; do
    lineno=0
    # XNT-221 (FN-2): tracks whether the line about to be read starts inside a
    # """ raw string that was opened on an earlier line. A bare continuation
    # line of such a string (e.g. a plain-text fixture row) carries no quotes
    # of its own, so the extraction below would never see it otherwise.
    in_raw_string=0
    while IFS= read -r line; do
        lineno=$((lineno + 1))
        was_in_raw_string=$in_raw_string

        # Toggle raw-string state once per literal """ occurrence on this line.
        scan="$line"
        while [[ $scan == *'"""'* ]]; do
            in_raw_string=$((1 - in_raw_string))
            scan=${scan#*'"""'}
        done

        # Imports, package declarations and comments carry the upstream package
        # name and copyright headers by design; none of them is a test fixture.
        [[ $line =~ ^[[:space:]]*(import|package)[[:space:]] ]] && continue
        [[ $line =~ ^[[:space:]]*(//|/\*|\*) ]] && continue
        # XML/HTML comments (used by AndroidManifest.xml fixtures etc.).
        [[ $line =~ ^[[:space:]]*\<!-- ]] && continue

        if ((was_in_raw_string)) && [[ ! $line == *'"'* ]]; then
            # A bare continuation line inside a """ block: no quote pairing is
            # possible or needed, the whole line is already string content.
            [[ $line =~ $BRAND ]] || continue
            while IFS= read -r literal; do
                [[ -n $literal ]] || continue
                report_literal "$file" "$lineno" "$literal" " (bare »\"\"\"« raw-string continuation line, XNT-221 FN-2)"
            done < <(grep -oE "^[[:space:]]*.*$BRAND.*\$" <<<"$line" || true)
            continue
        fi

        [[ $line =~ $BRAND ]] || continue

        # Extract each top-level quoted string literal on the line, honouring
        # backslash escapes so an escaped quote inside the literal (\") does
        # not end the span early.
        while IFS= read -r outer; do
            [[ -n $outer ]] || continue
            inner=${outer:1:-1}
            inner_unescaped=${inner//\\\"/\"}

            if [[ $inner_unescaped == *'"'* ]]; then
                # XNT-221 (FN-1): the literal itself contains an escaped-quote
                # inner string (e.g. a JSON fixture embedded as a Kotlin
                # string: "{\"key\":\"nextcloud...\"}"). Pairing quotes on the
                # raw line put the outer Kotlin-string delimiters into the
                # same pairing sequence as the inner JSON quotes, so a naive
                # '"[^"]*"' scan on the raw line pairs across that boundary
                # and the brand text falls in the gap between spans, invisible
                # to either extraction. Re-pairing on the unescaped inner
                # content alone (with the outer delimiters already stripped)
                # recovers the nested literals correctly.
                while IFS= read -r nested; do
                    [[ $nested =~ $BRAND ]] || continue
                    report_literal "$file" "$lineno" "$nested" " (nested inside escaped-quote literal $outer, XNT-221 FN-1)"
                done < <(grep -oE '"[^"]*"' <<<"$inner_unescaped" || true)
            else
                [[ $outer =~ $BRAND ]] || continue
                report_literal "$file" "$lineno" "$outer" ""
            fi
        done < <(grep -oE '"(\\.|[^"\\])*"' <<<"$line" || true)
    done <"$file"
done < <(find "${SCAN_DIRS[@]}" -type f \( -name '*.kt' -o -name '*.java' -o -name '*.json' -o -name '*.xml' \) -print0 | sort -z)

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
