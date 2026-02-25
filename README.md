# Durdans LIMS - Core Service

An enterprise-level, highly scalable backend service for the Durdans Hospital Laboratory Information Management System (LIMS). This project is built using **Java 21** and **Spring Boot 3.5**, implementing a robust multi-module architecture.

---

## 🏗 Architecture & Repository Structure

The project strictly follows a **multi-module structure** to enforce separation of concerns between API definitions and core business logic.

- `lims-core-service-api/`: Defines DTOs, API Interfaces, and shared models. It acts as the contract for the REST APIs, making it easy to share API definitions across different services or even client SDKs.
- `lims-core-service-app/`: The main executable Spring Boot application containing all domain entities, repositories, services, configurations, and controllers implementing the API endpoints.

---

## 🚀 Tech Stack

- **Framework**: Spring Boot 3.5.10 (Java 21)
- **Database Layer**: Spring Data JPA & PostgreSQL
- **Database Migrations**: Liquibase
- **Security**: Spring Security + OAuth2 Resource Server (Keycloak Integration for JWT validation)
- **File Storage**: Amazon S3 / LocalStack (AWS SDK v2)
- **Event Driven / Messaging**: Spring Kafka
- **Caching**: Caffeine Cache
- **Documentation**: Swagger/OpenAPI 3
- **Email Service**: Spring Boot Mail
- **Build Tool**: Gradle

---

## ✨ Features

- **Patient Management**: Complete lifecycle and record management for hospital patients.
- **Document Management**: Securely handles uploading, storage, and retrieval of documents (PDFs, Images up to 10MB) via Amazon S3.
- **Role-Based Access Control**: Hardened security layer intercepting requests and authenticating JWTs via Keycloak (`lims-realm`).
- **Event-Driven Workflows**: Real-time communication and notification handling via Kafka topics.
- **Auditing & Metadata**: Comprehensive tracking and logging of domain operations.

---

## ⚙️ Prerequisites

Ensure the following tools and dependencies are running on your local machine before starting the application:

- **Java 21 JDK**
- **PostgreSQL**: Port `5433` (as configured for local development)
- **Keycloak**: Port `8081` (Realm: `lims-realm`)
- **Apache Kafka**: Port `9092`
- **LocalStack**: Port `4566` (Used to mock Amazon S3 for `lims-patient-documents` bucket locally)

> **Note**: Typically, the infrastructure components (DB, Keycloak, Kafka, LocalStack) are managed via Docker Compose (`lims-infrastructure`). Ensure those containers are spun up first.

---

## 🛠 Running the Application Locally

1. **Clone the repository** and navigate to the project root:
   ```bash
   cd e:/2nd year project/durdans-lims/lims-core-service
   ```

2. **Setup environment properties**
   The main `application.yml` is committed to Git and uses environment variable placeholders with safe local defaults (e.g., `postgres` / `eta8827`).
   
   If you need to use different local credentials (like a different Postgres password) **do not modify `application.yml` directly**. Instead, create a local override file:
   
   Create `lims-core-service-app/src/main/resources/application-local.yml`:
   ```yaml
   spring:
     datasource:
       password: your_custom_local_password
   ```
   *(Note: `application-local.yml` is ignored by Git, so your local secrets are safe).*

3. **Build the application**:
   ```bash
   ./gradlew clean build
   ```

4. **Run the Spring Boot application**:
   - To run with default mocked properties:
     ```bash
     ./gradlew :lims-core-service-app:bootRun
     ```
   - To run utilizing your custom `application-local.yml`:
     ```bash
     ./gradlew :lims-core-service-app:bootRun --args='--spring.profiles.active=local'
     ```
   *The server will start locally on port `11000`.*

---

## 📂 Key Configuration Highlights

- **Database**: Connects to `durdans_lims_db` via standard JDBC URL configured in `application.yml`. Flyway/Liquibase handles schemas on boot.
- **S3 Upload Defaults**: PDF and standard Images (png/jpg) are permitted. Max file size is constrained to **10MB**.
- **Kafka Topics**: JSON-serialized payloads are distributed via the `StringSerializer`/`JsonSerializer` approach for scalable microservice interconnectivity.

---

## 📚 API Documentation

Once the application is running, the Open API documentation (Swagger UI) can be accessed to view all exposed endpoints, models, and test API calls:

- **Swagger UI**: `http://localhost:11000/swagger-ui.html` (or the equivalent mapped endpoint based on your security configuration)
