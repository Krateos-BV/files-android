#!/bin/bash
#
# SPDX-FileCopyrightText: 2026 XeniaCloud
# SPDX-License-Identifier: AGPL-3.0-or-later
#
# Fails when a commit carries a coding-agent Co-Authored-By trailer but not the
# Assisted-by trailer AGENTS.md requires. Kept as a script rather than inline YAML
# so it can be run against any range locally:
#
#   .github/workflows/scripts/check-assisted-by.sh origin/master HEAD
set -euo pipefail

BASE=${1:?usage: check-assisted-by.sh <base-ref> [head-ref]}
HEAD=${2:-HEAD}

# Coding-agent identities, kept in step with ai-policy.yml's own patterns.
AGENT_EMAILS='copilot@github\.com|noreply@anthropic\.com|devin@cognition\.ai|devin@cognition-labs\.com|aider@aider\.chat|noreply@aider\.chat|codex@openai\.com|cursor@anysphere\.com|windsurf@codeium\.com|codeium@codeium\.com|amazon-q@amazon\.com|codewhisperer@amazon\.com|gemini-code-assist@google\.com|openhands@all-hands\.dev|swe-agent@princeton\.edu'
AGENT_NAMES='GitHub Copilot|Claude( [A-Za-z0-9. -]+)?|Devin( AI)?|aider( \(.*\))?|OpenAI Codex|Cursor( AI)?|Windsurf|Amazon Q|CodeWhisperer|Gemini Code Assist|OpenHands|SWE-agent|AutoCodeRover|Tabnine'

# AGENTS.md mandates "Assisted-by: AGENT_NAME:MODEL_VERSION".
WELL_FORMED='^Assisted-by:[[:space:]]*[^[:space:]]+:[^[:space:]]+[[:space:]]*$'

if ! MERGE_BASE=$(git merge-base "$BASE" "$HEAD" 2>/dev/null); then
    echo "::error::Could not find a merge base between $BASE and $HEAD"
    exit 2
fi

failed=0
checked=0
agent_authored=0

while read -r sha; do
    [ -n "$sha" ] || continue
    checked=$((checked + 1))
    message=$(git log -1 --format=%B "$sha")
    described=$(git log -1 --format='%h %s' "$sha")

    co_authored=$(printf '%s\n' "$message" \
        | grep -iE "^Co-Authored-By:.*<(${AGENT_EMAILS})>|^Co-Authored-By:[[:space:]]*(${AGENT_NAMES})[[:space:]]*[<(]" || true)

    # No machine evidence of agent authorship: nothing to disclose, nothing to enforce.
    [ -n "$co_authored" ] || continue
    agent_authored=$((agent_authored + 1))

    assisted=$(printf '%s\n' "$message" | grep -iE '^Assisted-by:' || true)

    if [ -z "$assisted" ]; then
        echo "::error title=Missing Assisted-by trailer::$described"
        echo "  agent co-author: $(printf '%s' "$co_authored" | head -1)"
        echo "  AGENTS.md requires every commit containing AI-assisted content to carry"
        echo "  an 'Assisted-by: AGENT_NAME:MODEL_VERSION' trailer. It is what records how"
        echo "  the code was produced, and it is what replaces a DCO sign-off in this fork."
        echo "  Amend the commit to add, for example:"
        echo ""
        echo "    Assisted-by: ClaudeCode:claude-opus-5"
        failed=1
    elif ! printf '%s\n' "$assisted" | grep -qE "$WELL_FORMED"; then
        echo "::warning title=Malformed Assisted-by trailer::$described"
        echo "  found:    $(printf '%s' "$assisted" | head -1)"
        echo "  expected: Assisted-by: AGENT_NAME:MODEL_VERSION (e.g. ClaudeCode:claude-opus-5)"
        echo "  Not a failure: the shape is a fork convention, and the disclosure is present."
    fi
done < <(git rev-list --no-merges "${MERGE_BASE}..${HEAD}")

# Say so explicitly. A guard that passes silently cannot be told apart from one
# that never ran, which is the failure mode worth designing against.
echo "Checked ${checked} commit(s) in ${MERGE_BASE}..${HEAD}; ${agent_authored} carried a coding-agent Co-Authored-By trailer."

exit "$failed"
