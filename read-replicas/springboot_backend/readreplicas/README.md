# Read Replicas - SpringBoot Application

A practical SpringBoot application demonstrating **read replica routing** for database scaling. This application shows how to intelligently route read and write operations to different database nodes (master and replica) to improve performance, availability, and scalability.

## 🎯 Overview

This is a backend service that implements read replica pattern using Spring Data JPA and custom routing logic. It demonstrates how to:

- Route write operations (CREATE, UPDATE, DELETE) to the **master** database
- Route read operations (SELECT) to **replica** databases
- Automatically switch datasources based on transaction type
- Scale read operations without affecting write performance

## 🏗️ Architecture

### Key Components

1. **ReplicationRoutingDataSource** - Custom datasource router that determines whether to use master or replica based on transaction properties
2. **DataSourceConfig** - Spring configuration that sets up master and replica datasources with lazy connection proxying
3. **UserService** - Business logic layer with transactional methods marked for read-only or write operations
4. **RequestController** - REST API endpoints for user CRUD operations

### How It Works

The application uses Spring's `@Transactional` annotation with `readOnly` parameter to determine routing:

```
Request → Controller → Service → ReplicationRoutingDataSource
                                       ↓
                    Is readOnly=true? → Route to Replica
                                       ↗
                    Is readOnly=false? → Route to Master
```

## 🚀 Getting Started

### Prerequisites

- Java 25+
- Maven 3.8+
- MySQL 8.0+
- Docker & Docker Compose (optional, for containerized setup)

### Setup

1. **Start the databases** (choose one option):

   **Option A: Bare Metal**
   ```bash
   # Follow SETUP.md for master-replica configuration
   ```

   **Option B: Docker**
   ```bash
   cd ../  # Go to parent directory with docker-compose.yaml
   docker-compose up -d
   ```

2. **Build the application**:
   ```bash
   mvn clean install
   ```

3. **Run the application**:
   ```bash
   mvn spring-boot:run
   ```

   The application will start on `http://localhost:8080`

## 📡 API Endpoints

### Get User (Read Operation → Routes to Replica)
```bash
GET /user/{userId}

Example:
curl http://localhost:8080/user/1
```

**Response**:
```json
{
  "id": 1,
  "name": "John Doe",
}
```

### Create User (Write Operation → Routes to Master)
```bash
POST /user/
Content-Type: application/json

Example:
curl -X POST http://localhost:8080/user/ \
  -H "Content-Type: application/json" \
  -d '{
    "id": 2
    "name": "Jane Smith",
  }'
```

**Response**:
```json
"User has been created successfully"
```

## 🔍 Configuration

Edit `src/main/resources/application.yaml` to modify datasource connections:

```yaml
spring:
  datasource:
    master:
      jdbc-url: jdbc:mysql://localhost:3308/db_scaling_demo
      username: root
      password: password

    replica:
      jdbc-url: jdbc:mysql://localhost:3309/db_scaling_demo
      username: root
      password: password
```

### Enable SQL Logging

The application is configured with SQL logging enabled by default:

```yaml
spring:
  jpa:
    show-sql: true
    hibernate:
      format_sql: true
```

Watch the console to see which database operations are routed to.

## 📊 Project Structure

```
readreplicas/
├── src/main/java/systemdesign/backend/readreplicas/
│   ├── controller/
│   │   └── RequestController.java        # REST endpoints
│   ├── service/
│   │   └── UserService.java              # Business logic
│   ├── model/
│   │   └── User.java                     # JPA entity
│   ├── dto/
│   │   └── UserDTO.java                  # Data transfer object
│   ├── repository/
│   │   └── UserRepository.java           # Spring Data JPA
│   ├── utils/
│   │   └── ReplicationRoutingDataSource.java  # Custom router
│   ├── config/
│   │   └── DataSourceConfig.java         # Datasource configuration
│   └── ReadReplicasApplication.java      # Spring Boot main class
├── pom.xml                               # Maven dependencies
└── application.yaml                      # Configuration
```

## 🧪 Testing the Setup

1. **Verify Master-Replica Replication**:
   - Create a user via POST endpoint
   - Query the user via GET endpoint
   - Check the console logs to confirm:
     - POST routed to `master` datasource
     - GET routed to `replica` datasource

2. **Monitor SQL Execution**:
   - The application logs all SQL statements with formatting
   - Look for different connection details for read vs. write operations

3. **Test Failover** (advanced):
   - Stop the replica database
   - GET requests should fail (or timeout)
   - POST requests should still work (master is unaffected)

## 🛠️ Development

### Key Points

- Methods with `@Transactional(readOnly = true)` automatically route to replicas
- Methods without `readOnly` or with `readOnly = false` route to master
- The routing happens transparently through `ReplicationRoutingDataSource`

## ⚠️ Important Notes

- This is a **learning/demonstration** project, not production-ready
- Credentials and connection strings are hardcoded for testing
- No authentication or authorization is implemented
- Error handling is minimal
- For production, use external configuration management (environment variables, secrets manager)

## 📚 References

- [Spring Data JPA Documentation](https://spring.io/projects/spring-data-jpa)
- [Spring AbstractRoutingDataSource](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/jdbc/datasource/lookup/AbstractRoutingDataSource.html)

## 🤝 Disclaimer

This is a **personal learning repository** created to understand database scaling techniques. While the implementation is functional, it's designed for educational purposes. Some intermediate setup steps may not be fully documented. Feel free to reach out or leverage GenAI to fill in any gaps.

---

Happy learning! 🚀
