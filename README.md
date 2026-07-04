# PDF2Data Platform

An enterprise-grade, dual-database backend engine built to streamline PDF data extraction, user authentication, and administrative monitoring.

---

## Tech Stack & Core Architecture

* **Backend Framework:** Java 21 / Spring Boot 3.x
* **Security:** Stateless JSON Web Tokens (JWT) & BCrypt Encryption
* **Relational Database (SQL):** MySQL (Handles User Context, Profiles, Roles, and Logs)
* **NoSQL Database:** MongoDB (Handles complex document schemas and unstructured data extractions)

---

## Project Setup & Installation

### 1. Prerequisites
Ensure you have the following installed locally:
* Java 21 JDK
* MySQL Server (running on port 3306)
* MongoDB Community Server (running on port 27017)
* Maven / IntelliJ IDEA

### 2. Run the Application
1. Clone the repository and navigate to the project directory.
2. Verify database connection credentials in `src/main/resources/application.yml`.
3. Build and launch the system via your IDE or terminal:
   ```bash
   mvn spring-boot:run
