#!/usr/bin/env bash
set -euo pipefail

image=${1:?Укажите образ Ktor из GHCR}
service=tasks_backend

if [[ ! $image =~ ^ghcr\.io/[a-z0-9_.-]+/[a-z0-9_.-]+:[a-f0-9]{40}$ ]]; then
    echo 'Ожидается ссылка GHCR с тегом SHA коммита' >&2
    exit 2
fi

previous_image=$(docker service inspect --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}' "$service")
case "$previous_image" in
    "$image"|"$image"@*) ;;
    *) docker service update --detach=false --with-registry-auth --image "$image" "$service" ;;
esac

current_image=$(docker service inspect --format '{{.Spec.TaskTemplate.ContainerSpec.Image}}' "$service")
case "$current_image" in
    "$image"|"$image"@*) ;;
    *) echo 'Обновление сервиса не вступило в силу' >&2; exit 1 ;;
esac

if ! curl --fail --silent --show-error --retry 5 --retry-all-errors --retry-delay 2 \
    --connect-timeout 3 --max-time 8 http://127.0.0.1:8080/health ||
    ! curl --fail --silent --show-error --max-time 8 http://127.0.0.1:8080/tasks |
        jq -e 'type == "array"'; then
    if [[ $current_image != "$previous_image" ]]; then
        docker service rollback --detach=false "$service"
    fi
    exit 1
fi
