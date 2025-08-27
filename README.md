# Spring Boot & Kotlin Sandbox

Spring Boot sandbox for **Kotlin**, containerized using **Docker** with **PostgreSQL**, **Mailpit**, and **Nginx**.
#### Table of Contents:
1. **Production Build:** Prerequisites, Environment Variables, Build & Run, Stop, Remove Persistent Data, Web Access, API Access
2. **Iterative Development:** Prerequisites, Environment Variables, Recommended Setup, Alternate Setup
3. **Docker Setup:** Containers, Physical Layers, Notes

---

## Production Build

### Prerequisites

- Docker & Docker Compose

### Environment Variables

`.docker/.env` is *committed on purpose (**against best practices**)* to provide a convenient **single-command build & run** for production build.

### Build & Run

```shell
docker compose --env-file .docker/.env up -d
```

The first build will take a few minutes in order to download all the needed images and process a multi-stage layered docker build.

These are the required images that will be downloaded if not already present on your system (~405 MB):

1. eclipse-temurin:17-jdk (~190 MB)
2. eclipse-temurin:17-jre (~90 MB)
3. postgres:16.10-alpine (~105 MB)
4. nginx:1.27-alpine (~20 MB)

Please notice that in the build stage **Gradle** will need the dependencies required to run a Spring Boot project, if Gradle never cached Spring Boot dependencies on your system before, then the additional downloaded files' size is expected to be around (~400 MB). 

### Stop

Stop all containers:
```shell
docker compose --env-file .docker/.env down
```

### Remove Persistent Data

Remove the persistent database volume:
```shell
docker volume rm kotlin-sandbox-jwt-db-data
```

### Web Access

- Mailpit: [http://localhost:9999/mailpit](http://localhost:9999/mailpit) or [http://localhost:8025/mailpit](http://localhost:8025/mailpit)
- Actuator: [http://localhost:9999/actuator](http://localhost:9999/actuator)

### API Access

- Registration: `POST` [http://localhost:9999/api/v1/auth/register](http://localhost:9999/api/v1/auth/register)
- Email Verification: `GET` [http://localhost:9999/api/v1/auth/verify-email](http://localhost:9999/api/v1/auth/verify-email)

---

## Iterative Development

### Prerequisites

- JDK - v.17 up to and including v.24 (*Required*)
- PostgreSQL v.16 (*Required*)
- Mailpit (*Required*)
- Docker & Docker Compose (*Optional*)

*Docker & Docker Compose* are required if you want to use the containerized database and mail catcher.

If you want do not want to rely on Docker, local installation of *PostgreSQL & Mailpit* will be required.

### Environment Variables

`application.properties` is *preloaded with defaults matching `jwt-db` and `jwt-mail` actual credentials on purpose (**against best practices**)* to provide convenient development ability without worrying about project profiles or environment variables.

These variables are passed from docker's environment variables `./docker/.env` through the `jwt-db` container.

* `SPRING_PROFILES_ACTIVE` - Spring Boot profile (`dev`)
* `DB_*` - All variables for database.
* `MAIL_*` - All variables for mail.

### Recommended Setup

Use IDE and local JDK for iterative development builds and utilize the containerized database `jwt-db` as a data source and the containerized mail catcher `jwt-mail` to catch all outgoing mail.

1. Run the two containers:
    ```shell
    docker compose --env-file .docker/.env up -d jwt-db jwt-mail
    ```
2. Run the project from IDE

### Alternate Setup

If you prefer using your own PostgreSQL and Mailpit setup, all the default `DB_*` & `MAIL_*` environment variables set in `application.properties` must be overridden with values matching your setup.

---

## Docker Details

### Containers

- **jwt-db**
    - Database container, supports `jwt-app` container, can be utilized as a standalone database to support development.
    - Reachable via `localhost:9998`
    - `DB_*` environment variables can be modified in `.docker/.env`
    - Image: `postgres:16.10-alpine`
    - Does not depend on other containers.
- **jwt-mail**
    - Mail container, serving as a catch-all for outgoing mail from `jwt-app`, can be utilized as a standalone mail catcher to support development.
    - Dashboard reachable via `http://localhost:8025/mailpit` (standalone) or `http://localhost:9999/mailpit` (when `jwt-app` is up).
    - `MAIL_*` environment variables can be modified in `.docker/.env`
    - Image: `axllent/mailpit:latest`
    - Does not depend on other containers.
- **jwt-app**
    - Application container, serving the application's build. Default build is set to `prod`.
    - Reachable via `http://localhost:9999`
    - `SPRING_PROFILES_ACTIVE` environment variable can be modified in `.docker/.env`
    - Images: `eclipse-temurin:17-jdk` for building, `eclipse-temurin:17-jre` for runtime.
    - Depends on `jwt-db` and `jwt-mail`
- **jwt-proxy**
    - Description: Reverse proxy container, proxying both the application and the mail dashboard.
    - Image: `nginx:1.27-alpine`
    - Depends on `jwt-app` (which depends on `jwt-db` and `jwt-mail`).

### Physical Layers

- **Networks**
    - `app-network`: The network connecting all containers.
- **Volumes**
    - `db-data`: Persistent volume for `jwt-db`

### Notes

All `docker compose` commands require explicitly defining the docker's environment file location `./docker/.env` to keep the project's root clean.

Creating an alias is highly recommended in case of frequent issuing of commands.

```shell
# Change directory to user's home
cd ~

# If .bash_aliases does not exist, create it
touch .bash_aliases

# Add the alias
alias docc="docker compose --env-file .docker/.env"

# Or combine changing directory to project's root and the docker compose command
alias docc="cd ~/path/to/project && docker compose --env-file .docker/.env"

# Source the file to activate the new alias
source .bash_aliases
```

After adding the alias, you will be able to issue commands like `docc ps`, `docc up -d`, `docc down`, `docc up -d jwt-db jwt-mail`
