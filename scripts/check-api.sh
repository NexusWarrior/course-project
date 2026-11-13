#!/usr/bin/env bash
set -euo pipefail

base_url=${1:-http://127.0.0.1:8080}

curl --fail --silent --show-error --retry 12 --retry-all-errors --retry-delay 2 \
    --connect-timeout 3 --max-time 8 "$base_url/health" |
    jq -e '.status == "ok"'

request() {
    curl --fail-with-body --silent --show-error --max-time 8 "$@"
}

case ${2:-read-only} in
    read-only)
        request "$base_url/tasks" | jq -e 'type == "array"'
        exit 0
        ;;
    crud) ;;
    *) echo "Неизвестный режим проверки" >&2; exit 2 ;;
esac

request "$base_url/tasks" | jq -e 'any(.[]; .title == "Подключить Android-приложение")'

task_id=''
cleanup() {
    if [[ -n $task_id ]]; then
        request -X DELETE "$base_url/tasks/$task_id" >/dev/null || true
    fi
}
trap cleanup EXIT

task_id=$(request -H 'Content-Type: application/json' \
    -d '{"title":"Проверка API"}' "$base_url/tasks" | jq -er '.id | select(type == "number")')
request -X PUT -H 'Content-Type: application/json' \
    -d '{"title":"Проверка API завершена","completed":true}' "$base_url/tasks/$task_id" |
    jq -e --argjson id "$task_id" '.id == $id and .title == "Проверка API завершена" and .completed'
request "$base_url/tasks" |
    jq -e --argjson id "$task_id" 'any(.[]; .id == $id and .completed)'
request -X DELETE "$base_url/tasks/$task_id" >/dev/null
request "$base_url/tasks" | jq -e --argjson id "$task_id" 'all(.[]; .id != $id)'
task_id=''
