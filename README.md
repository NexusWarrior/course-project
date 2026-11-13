# Инфраструктура разработки и тестирования Android-приложения

Учебное Android-приложение управляет задачами через API Ktor. PostgreSQL хранит название и состояние каждой задачи. Docker Compose запускает локальную среду, GitHub Actions собирает APK и проверяет приложение, Docker Swarm запускает тестовый стенд.

## Локальный запуск

Понадобятся Docker с Compose, Android Studio, SDK Android 37.0, JDK 25 и эмулятор Android API 31 или новее. Выполните из корня репозитория:

```bash
docker compose up --build --wait
bash scripts/check-api.sh
```

Откройте `android/NiaspoProject` в Android Studio и запустите конфигурацию `app` на эмуляторе. Адрес `10.0.2.2:8080` ведёт к порту 8080 компьютера. Создайте задачу, измените её название, отметьте выполненной, обновите список и удалите её.

| Метод | Путь | Результат |
|---|---|---|
| GET | `/health` | 200 при доступной таблице задач, 503 при ошибке базы |
| GET | `/tasks` | 200 и список задач |
| POST | `/tasks` | 201 и созданная задача |
| PUT | `/tasks/{id}` | 200 и обновлённая задача, 404 если её нет |
| DELETE | `/tasks/{id}` | 204 при удалении, 404 если задачи нет |

Для POST и PUT передаётся JSON вида `{"title":"Проверить стенд","completed":false}`. Сервер обрезает пробелы по краям названия и принимает от 1 до 120 символов. Поле `completed` по умолчанию имеет значение `false`. Для PUT передавайте оба поля, чтобы сохранить текущее состояние задачи.

## Проверки и сборка APK

```bash
# Серверные тесты. Gradle использует JDK 21.
(cd backend/ktor-project && ./gradlew test)

# Сквозная проверка API с PostgreSQL. Нужен jq.
bash scripts/check-api.sh http://127.0.0.1:8080 crud

# Сборка и JVM-тесты Android. Gradle использует JDK 25.
(cd android/NiaspoProject && ./gradlew testDebugUnitTest assembleDebug)

# Сквозные Android-тесты на запущенном эмуляторе с работающим Compose.
(cd android/NiaspoProject && ./gradlew connectedDebugAndroidTest)
```

В Linux и CI Android использует каталог `android/NiaspoProject/app/build/`. На macOS результаты сборки модуля хранятся в `~/Library/Caches/niaspo/<идентификатор проекта>/app/`, вне синхронизируемого рабочего стола. APK находится в подкаталоге `outputs/apk/debug/`, отчёты JVM-тестов и тестов на эмуляторе — в `test-results/` и `outputs/androidTest-results/` соответственно. Серверные тесты покрывают операции с задачами, невалидные запросы и недоступность базы. Проверка с Compose отдельно прогоняет эти операции с реальной PostgreSQL. Тесты Android проверяют загрузку данных и полный сценарий изменения задачи через интерфейс.

`docker compose down` останавливает локальный стенд. Именованный том сохраняет данные. Для тестов с чистой базой используйте отдельный временный проект Compose или удалите локальный том командой `docker compose down --volumes`, если данные больше не нужны. Скрипт `db/init.sql` выполняется при инициализации пустого тома.

## Тестовый стенд Swarm

Понадобятся две Linux-машины с Docker Engine. Для опубликованного в GitHub Actions образа используйте узлы x86-64. На manager выполните `docker swarm init --advertise-addr <PRIVATE_MANAGER_IP>`, на worker выполните команду `docker swarm join`, выданную manager. Между узлами обеспечьте связь по частной сети. Проверьте, что `docker node ls` показывает оба узла в статусе Ready.

В стеке пять контейнеров: Nginx, PostgreSQL и три реплики Ktor. Nginx обращается к Ktor через сеть `application`, Ktor и PostgreSQL взаимодействуют через внутреннюю сеть `database`. PostgreSQL закреплён за узлом с томом. Одна база и один gateway рассчитаны на учебный стенд.

На manager из корня репозитория:

```bash
docker node update --label-add tasks_db=true <DB_NODE>
openssl rand -hex 24 | docker secret create tasks_db_password -
export BACKEND_IMAGE=ghcr.io/<owner>/<repo>:<commit-sha>
docker stack deploy --with-registry-auth -c deploy/stack.yml tasks
docker stack services tasks
docker stack ps tasks
bash scripts/check-api.sh
```

В `BACKEND_IMAGE` укажите опубликованный образ. Для закрытого пакета сначала выполните `docker login ghcr.io` на manager с учётными данными для чтения пакета. Оставьте порт 8080 доступным только в частной сети стенда. Для демонстрации с локального эмулятора остановите Compose и откройте туннель:

```bash
ssh -N -L 127.0.0.1:8080:127.0.0.1:8080 <user>@<manager>
```

Приложение с адресом по умолчанию будет обращаться через этот туннель. Если у стенда есть HTTPS-адрес, его можно задать при сборке через `-PtaskApiUrl=https://<host>`. Локальный HTTP разрешён в debug-сборке для `10.0.2.2` и `127.0.0.1`. Стенд использует общий список учебных задач.

## GitHub Actions и деплой

На push в `main` и на pull request запускаются серверные проверки, сборка и тесты Android. После успешных проверок для `main` публикуется серверный образ в GHCR с тегом SHA коммита. APK и результаты тестов доступны в Artifacts соответствующего запуска.

Для автоматического обновления предварительно разверните стек `tasks`. Подготовьте окружение `staging` в GitHub с разрешённым деплоем из `main`. Задайте переменную репозитория `SWARM_DEPLOY_ENABLED=true`, переменные окружения `SWARM_SSH_HOST`, `SWARM_SSH_USER` и секреты `SWARM_SSH_KEY`, `SWARM_SSH_KNOWN_HOSTS`. SSH-адрес manager должен быть достижим для GitHub Actions, а пользователь должен иметь доступ к Docker. На manager нужны `bash`, `curl` и `jq`.

Скрипт `deploy/update.sh` обновляет реплики Ktor по очереди и проверяет API через Nginx. При неудачной проверке после обновления скрипт запрашивает откат сервиса и завершает деплой с ошибкой. Если проверка сервера или Android в CI упадёт, публикация и деплой будут пропущены.

## Сценарий защиты

1. Открыть успешный запуск Actions и показать результаты тестов и APK.
2. На manager показать `docker node ls`, `docker stack services tasks` и `docker stack ps tasks`.
3. Запустить `bash scripts/check-api.sh` и показать путь запроса от Nginx до Ktor и PostgreSQL.
4. Добавить задачу в Android-приложении, отметить выполненной, перезапустить приложение и показать сохранённое состояние.
5. Показать успешное обновление образа и запуск с ошибкой проверки, в котором деплой пропущен.
