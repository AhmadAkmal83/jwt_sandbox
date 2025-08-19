# Spring Boot & Kotlin Sandbox

Spring Boot sandbox for **Kotlin**, containerized using **Docker** with **PostgreSQL** and **Nginx**.

---

## Production Build

### Prerequisites

- Docker & Docker Compose

### Environment

`.docker/.env` is *committed on purpose (against best-practices)* to provide a convenient **single-command build & run** for production build.

### Build & Run

```shell
docker compose --env-file .docker/.env up -d
```

The first build will take a few minutes in order to download all the needed images and process a multi-stage layered docker build.

These are the required images that will be downloaded if not already present on your system (~400 MB):
1. eclipse-temurin:17-jdk (~190 MB)
2. eclipse-temurin:17-jre (~90 MB)
3. postgres:16.10-alpine (~105 MB)
4. nginx:1.27-alpine (~20 MB)

Please notice that in the build stage **Gradle** will need the dependencies required to run a Spring Boot project, if Gradle never cached Spring Boot dependencies on your system before, then the downloaded files' size is expected to be around (~400 MB). 

### Stop

Stop all containers:
```shell
docker compose --env-file .docker/.env down
```

### Remove persistent data

Remove the persistent database volume:
```shell
docker volume rm kotlin-sandbox-jwt-db-data
```

### Web Access

- Application: [http://localhost:9999](http://localhost:9999)
- Health: [http://localhost:9999/actuator/health](http://localhost:9999/actuator/health)


---

## Iterative Development

### Prerequisites

- JDK - v.17 up to and including v.24 (Required)
- Docker & Docker Compose (Optional)
- PostgreSQL (Optional)

*Docker & Docker Compose* are required if you want to use the database container as a data source.

*PostgreSQL* is required if you want a completely localized development without relying on Docker.

### Docker's exposed services:

- **localhost:9999**
    - Requires `jwt-db`, `jwt-app`, and `jwt-proxy` services to be *up*
- **localhost:9998**
    - Requires `jwt-db` service to be *up*

### Environment variables

`application.properties` is *preloaded with defaults matching `jwt-db` actual credentials on purpose (against best-practices)* to provide convenient development ability without worrying about project profiles or environment variables.

* `DB_HOST` - Database host (`localhost`)
* `DB_PORT` - Database port (`9998`)
* `DB_DATABASE` - Database name (`jwt_sandbox_db`)
* `DB_USERNAME` - Database user's username (`jwt_sandbox_db`)
* `DB_PASSWORD` - Database user's password (`raNdom_paSS_WOrD`)
* `SPRING_PROFILES_ACTIVE` - Spring profile (`dev`)

### Recommended Setup

Use IDE and local JDK for iterative development builds and utilize the containerized database `jwt-db` as a data source

1. Run the database container:
    ```shell
    docker compose --env-file .docker/.env up -d jwt-db
    ```
2. Run the project from IDE

Note: The database container must be **up before running** the project.

### Alternate Setup

If you prefer using your own PostgreSQL setup, all the default `DB_*` environment variables set in `application.properties` must be overridden to point to your own setup.

Add all 5 keys (`DB_HOST`, `DB_PORT`, `DB_DATABASE`, `DB_USERNAME`, `DB_PASSWORD`) to the environment variables of your IDE with values matching your own PostgreSQL setup.
