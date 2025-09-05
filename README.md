# JWT AuthN - Spring Boot & Kotlin Sandbox

**JWT** implementation in a sandbox for **Spring Boot & Kotlin**.

Containerized using **Docker** with **Java 17**, **PostgreSQL**, **Mailpit**, and **Nginx**.
#### Table of Contents:
- **Production Build**
    - Prerequisites, Environment Variables, Build & Run, Stop, Remove Persistent Data, Web Access, API Access
- **Iterative Development**
    - Prerequisites, Environment Variables, Recommended Setup, Alternate Setup
- **Docker Setup**
    - Containers, Physical Layers, Notes
- **Postman Collection**
    - Description, Endpoints, Variables, Download Link

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
- Login: `POST` [http://localhost:9999/api/v1/auth/login](http://localhost:9999/api/v1/auth/login)
- Get Current User: `GET` [http://localhost:9999/api/v1/users/me](http://localhost:9999/api/v1/users/me)
- Refresh Access Token: `POST` [http://localhost:9999/api/v1/auth/refresh](http://localhost:9999/api/v1/auth/refresh)
- Logout: `POST` [http://localhost:9999/api/v1/auth/logout](http://localhost:9999/api/v1/auth/logout)
- Forgot Password: `POST` [http://localhost:9999/api/v1/auth/forgot-password](http://localhost:9999/api/v1/auth/forgot-password)
- Reset Password: `POST` [http://localhost:9999/api/v1/auth/reset-password](http://localhost:9999/api/v1/auth/reset-password)

---

## Iterative Development

### Prerequisites

- JDK - v.17 up to and including v.24 (*Required*)
- PostgreSQL v.16 `^`
- Mailpit `^`
- Docker & Docker Compose `^^`

`^` *PostgreSQL & Mailpit* local installations are required if you do not want to rely on Docker.

`^^` *Docker & Docker Compose* are required if you will rely on the containerized database and mail server.

### Environment Variables

`application.yaml` is *preloaded with defaults matching `jwt-db` and `jwt-mail` actual credentials on purpose (**against best practices**)* to provide convenient development ability without worrying about project profiles or environment variables.

These variables are passed from docker's environment variables `./docker/.env` through the `jwt-app` container.

* `SPRING_PROFILES_ACTIVE` - Spring Boot profile (`dev`)
* `DB_*` - All variables for database.
* `MAIL_*` - All variables for mail.

### Recommended Setup

Use IDE and local JDK for iterative development builds and utilize the containerized database `jwt-db` as a data source and the containerized mail server `jwt-mail` to catch all outgoing mail.

1. Run the two containers:
    ```shell
    docker compose --env-file .docker/.env up -d jwt-db jwt-mail
    ```
2. Run the project from IDE

### Alternate Setup

If you prefer using your own PostgreSQL and Mailpit setup, all the default `DB_*` & `MAIL_*` environment variables set in `application.yaml` must be overridden with values matching your setup.

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
    - Mail container, serving as a catch-all for outgoing mail from `jwt-app`, can be utilized as a standalone mail server to support development.
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
# Ensure .bash_aliases exists, this will not overwrite the file's content if it already exists.
touch ~/.bash_aliases

# Add the alias to .bash_aliases
echo 'alias docc="docker compose --env-file .docker/.env"' >> ~/.bash_aliases

# OR combine changing directory to project's root and the docker compose command
echo 'alias docc="cd ~/path/to/project && docker compose --env-file .docker/.env"' >> ~/.bash_aliases

# Source the file to activate the new alias
source .bash_aliases
```

After adding the alias, you will be able to issue commands like `docc ps`, `docc up -d`, `docc down`, `docc up -d jwt-db jwt-mail`

---

## Postman Collection

### Description

This collection contains all the endpoints needed to manually interact with the application.

Protected endpoints are pre-set with correct authorization type `Bearer Token` and a value that is linked to the variable `accessToken`.

### Endpoints

- Authentication:
    - `POST` `{{baseUrl}}{{apiPath}}/auth/register` *(public)*
    - `GET` `{{baseUrl}}{{apiPath}}/auth/verify-email` *(public)*
    - `POST` `{{baseUrl}}{{apiPath}}/auth/login` *(public)*
    - `POST` `{{baseUrl}}{{apiPath}}/auth/refresh` *(public)*
    - `POST` `{{baseUrl}}{{apiPath}}/auth/logout` *(protected)*
    - `POST` `{{baseUrl}}{{apiPath}}/auth/forgot-password` *(public)*
    - `POST` `{{baseUrl}}{{apiPath}}/auth/reset-password` *(public)*
- Users:
    - `GET` `{{baseUrl}}{{apiPath}}/users/me` *(protected)*

### Variables

Variables are set on the top-level directory `JwtSandbox`
- `baseUrl`: Scheme, host, and port.
    - `http://localhost:9999` when running `jwt-app` docker container. *Default*
    - `http://localhost:8080` when running development build.
- `apiPath`: The API path.
    - `/api/v1` *Default*
- `accessToken` The access token.
    - *Default* is empty, should be set to the received token after hitting `login` or `refresh` endpoints.

### Download Link

[JwtSandbox Postman Collection](https://drive.google.com/file/d/1cVaip9osYJQMimoKtT8U056CnsAnnTzp/view)

---
