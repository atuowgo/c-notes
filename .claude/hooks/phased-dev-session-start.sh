#!/usr/bin/env bash
# SessionStart hook: inject phased-dev skill rules into every session context
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
SKILL_FILE="${PROJECT_ROOT}/skills/phased-dev/SKILL.md"

skill_content=$(cat "${SKILL_FILE}" 2>&1 || echo "Error reading phased-dev skill")

escape_for_json() {
    local input="$1"
    local output=""
    local i char
    for (( i=0; i<${#input}; i++ )); do
        char="${input:$i:1}"
        case "$char" in
            $'\\') output+='\\' ;;
            '"') output+='\"' ;;
            $'\n') output+='\n' ;;
            $'\r') output+='\r' ;;
            $'\t') output+='\t' ;;
            *) output+="$char" ;;
        esac
    done
    printf '%s' "$output"
}

skill_escaped=$(escape_for_json "$skill_content")

cat <<EOF
{
  "hookSpecificOutput": {
    "hookEventName": "SessionStart",
    "additionalContext": "<EXTREMELY_IMPORTANT>\nThis project uses a phased-dev orchestration rule. Full skill content below — follow it whenever brainstorming/planning/executing a feature in this session:\n\n${skill_escaped}\n</EXTREMELY_IMPORTANT>"
  }
}
EOF

exit 0
