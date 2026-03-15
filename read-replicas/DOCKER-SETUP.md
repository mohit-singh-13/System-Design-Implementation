# MySQL Master-Replica Replication with Docker

This guide sets up a MySQL Master-Replica replication environment entirely using Docker — no system-level MySQL installation needed. It's designed for **local testing and learning** how MySQL replication works.

> **Prerequisites:**
>
> - [Docker](https://docs.docker.com/get-docker/) installed
> - [Docker Compose](https://docs.docker.com/compose/install/) installed (v2+ recommended)
> - Basic familiarity with the terminal

---

## Project Structure

We'll use Docker Compose to define both MySQL containers and a shared network so they can talk to each other.

```
mysql-replication/
├── docker-compose.yml
├── master/
│   └── my.cnf
└── replica/
    └── my.cnf
```

Create the project directory:

```bash
mkdir mysql-replication && cd mysql-replication
mkdir master replica
```

---

## 1. Create MySQL Configuration Files

### Master Configuration (`master/my.cnf`)

```ini
[mysqld]
server-id          = 1
log_bin            = mysql-bin
binlog_format      = ROW
binlog_do_db       = testdb
```

**What each option does:**

| Option                  | Purpose                                                                           |
| ----------------------- | --------------------------------------------------------------------------------- |
| `server-id = 1`         | Unique ID for this node in the replication topology. The Master is `1`.           |
| `log_bin = mysql-bin`   | Enables binary logging — this is what the Replica reads to stay in sync.          |
| `binlog_format = ROW`   | Logs the actual row changes (safer and more predictable than `STATEMENT` format). |
| `binlog_do_db = testdb` | Only replicate the `testdb` database. Omit this line to replicate all databases.  |

### Replica Configuration (`replica/my.cnf`)

```ini
[mysqld]
server-id          = 2
relay-log          = relay-bin
read_only          = 1
```

**What each option does:**

| Option                  | Purpose                                                                                              |
| ----------------------- | ---------------------------------------------------------------------------------------------------- |
| `server-id = 2`         | Unique ID for this Replica. Must differ from the Master and any other Replicas.                      |
| `relay-log = relay-bin` | Sets the prefix for relay log files, which store events copied from the Master before being applied. |
| `read_only = 1`         | Prevents accidental writes to the Replica. Only the replication SQL thread can write.                |

---

## 2. Create `docker-compose.yml`

In the `mysql-replication/` root directory, create `docker-compose.yml`:

```yaml
version: "3.8"

services:
  mysql-master:
    image: mysql:8.0
    container_name: mysql-master
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
      MYSQL_DATABASE: testdb
    ports:
      - "3307:3306" # Exposed on host port 3307 to avoid conflicts
    volumes:
      - ./master/my.cnf:/etc/mysql/conf.d/my.cnf:ro
      - master-data:/var/lib/mysql
    networks:
      - mysql-net
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-prootpassword"]
      interval: 10s
      timeout: 5s
      retries: 5

  mysql-replica:
    image: mysql:8.0
    container_name: mysql-replica
    environment:
      MYSQL_ROOT_PASSWORD: rootpassword
    ports:
      - "3308:3306" # Exposed on host port 3308
    volumes:
      - ./replica/my.cnf:/etc/mysql/conf.d/my.cnf:ro
      - replica-data:/var/lib/mysql
    networks:
      - mysql-net
    depends_on:
      mysql-master:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-prootpassword"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  master-data:
  replica-data:

networks:
  mysql-net:
    driver: bridge
```

**Key points:**

- Both containers are on the `mysql-net` bridge network — they can reach each other using their container names (`mysql-master`, `mysql-replica`) as hostnames.
- Ports `3307` and `3308` are exposed on the host so you can connect with a local MySQL client or GUI tool.
- `depends_on` with `condition: service_healthy` ensures the Replica container waits for the Master to be ready before starting.
- Named volumes (`master-data`, `replica-data`) persist MySQL data across container restarts.

---

## 3. Start the Containers

```bash
docker compose up -d
```

Verify both containers are running and healthy:

```bash
docker compose ps
```

Expected output:

```
NAME            IMAGE       STATUS                   PORTS
mysql-master    mysql:8.0   Up (healthy)             0.0.0.0:3307->3306/tcp
mysql-replica   mysql:8.0   Up (healthy)             0.0.0.0:3308->3306/tcp
```

> **Note:** MySQL takes 15–30 seconds to initialize on first run. If containers show `starting`, wait a moment and check again.

---

## 4. Configure the Master

### 4.1 Open a MySQL shell on the Master

```bash
docker exec -it mysql-master mysql -u root -prootpassword
```

> The `-p` flag and password are written together (`-prootpassword`) with no space — this avoids the interactive password prompt.

### 4.2 Create the Replication User

Inside the MySQL shell:

```sql
CREATE USER 'replica_user'@'%' IDENTIFIED WITH mysql_native_password BY 'replica_password';
GRANT REPLICATION SLAVE ON *.* TO 'replica_user'@'%';
FLUSH PRIVILEGES;
```

> **MySQL 8 note:** The `WITH mysql_native_password` clause is important for compatibility. MySQL 8 defaults to `caching_sha2_password` authentication, which requires an extra TLS/RSA setup step to work with replication. Using `mysql_native_password` avoids that complexity for local testing.

### 4.3 Get the Binary Log Position

```sql
FLUSH TABLES WITH READ LOCK;
SHOW MASTER STATUS;
```

Example output:

```
+------------------+----------+--------------+------------------+
| File             | Position | Binlog_Do_DB | Binlog_Ignore_DB |
+------------------+----------+--------------+------------------+
| mysql-bin.000003 |      157 | testdb       |                  |
+------------------+----------+--------------+------------------+
```

**Copy these values** — you'll need `File` and `Position` in the next step.

Then release the lock:

```sql
UNLOCK TABLES;
EXIT;
```

---

## 5. Configure the Replica

### 5.1 Open a MySQL shell on the Replica

```bash
docker exec -it mysql-replica mysql -u root -prootpassword
```

### 5.2 Point the Replica at the Master

Use the `File` and `Position` values from Step 4.3. The `MASTER_HOST` is the container name `mysql-master` — Docker's internal DNS resolves it automatically.

```sql
CHANGE MASTER TO
  MASTER_HOST     = 'mysql-master',
  MASTER_USER     = 'replica_user',
  MASTER_PASSWORD = 'replica_password',
  MASTER_LOG_FILE = 'mysql-bin.000003',
  MASTER_LOG_POS  = 157;
```

### 5.3 Start Replication

```sql
START SLAVE;
```

---

## 6. Verify Replication is Working

Still inside the Replica MySQL shell:

```sql
SHOW SLAVE STATUS\G
```

Look for these fields:

```
Slave_IO_Running: Yes
Slave_SQL_Running: Yes
Seconds_Behind_Master: 0
Master_Host: mysql-master
```

Both `Slave_IO_Running` and `Slave_SQL_Running` must show `Yes`. If either shows `No`, check the `Last_IO_Error` or `Last_SQL_Error` field in the same output.

Exit the shell:

```sql
EXIT;
```

---

## 7. Test Replication End-to-End

### Write to the Master

```bash
docker exec -it mysql-master mysql -u root -prootpassword testdb
```

```sql
CREATE TABLE users (
  id   INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users (name) VALUES ('Alice'), ('Bob'), ('Charlie');
SELECT * FROM users;
EXIT;
```

### Read from the Replica

```bash
docker exec -it mysql-replica mysql -u root -prootpassword testdb
```

```sql
SELECT * FROM users;
```

Expected output:

```
+----+---------+---------------------+
| id | name    | created_at          |
+----+---------+---------------------+
|  1 | Alice   | 2024-01-15 10:00:00 |
|  2 | Bob     | 2024-01-15 10:00:00 |
|  3 | Charlie | 2024-01-15 10:00:00 |
+----+---------+---------------------+
```

If you see the same rows, replication is working correctly. 🎉

---

## 8. Troubleshooting

### `Slave_IO_Running: No` — Replica can't connect to Master

**Check container-to-container connectivity:**

```bash
docker exec mysql-replica ping mysql-master
```

**Check the replication user can authenticate:**

```bash
docker exec -it mysql-replica mysql -u replica_user -preplica_password -h mysql-master
```

If this fails, the user may not exist or may have the wrong host permissions. Re-run Step 4.2 on the Master.

**Check for auth plugin issues (MySQL 8):**

```bash
docker exec -it mysql-master mysql -u root -prootpassword -e \
  "SELECT user, host, plugin FROM mysql.user WHERE user='replica_user';"
```

The `plugin` column should show `mysql_native_password`. If it shows `caching_sha2_password`, re-create the user with the correct plugin (see Step 4.2).

---

### `Slave_SQL_Running: No` — Error applying events

View the specific error:

```bash
docker exec -it mysql-replica mysql -u root -prootpassword \
  -e "SHOW SLAVE STATUS\G" | grep "Last_SQL_Error"
```

**Common fix — skip a bad event:**

```sql
STOP SLAVE;
SET GLOBAL SQL_SLAVE_SKIP_COUNTER = 1;
START SLAVE;
```

**Nuclear option — full reset and resync:**

```sql
STOP SLAVE;
RESET SLAVE ALL;
```

Then repeat Steps 4.3 and 5.2–5.3 from scratch.

---

### Containers crash or won't start

Check logs:

```bash
docker logs mysql-master
docker logs mysql-replica
```

**Volume conflict from a previous run:** If you had a failed setup, old volume data can cause conflicts. Reset everything cleanly:

```bash
docker compose down -v     # -v removes named volumes too
docker compose up -d
```

> ⚠️ This deletes all MySQL data in both containers. Fine for testing, not for production.

---

## 9. Useful Commands

**Tail MySQL error logs in real-time:**

```bash
docker exec mysql-master tail -f /var/log/mysql/error.log
# Or from Docker logs:
docker logs -f mysql-master
```

**Quick replication health check (one-liner):**

```bash
docker exec mysql-replica mysql -u root -prootpassword \
  -e "SHOW SLAVE STATUS\G" | grep -E "Slave_IO_Running|Slave_SQL_Running|Seconds_Behind"
```

**Connect from host using local MySQL client:**

```bash
# To Master
mysql -u root -prootpassword -h 127.0.0.1 -P 3307

# To Replica
mysql -u root -prootpassword -h 127.0.0.1 -P 3308
```

**Stop and remove everything when done testing:**

```bash
docker compose down -v
```

---

## 10. How the Docker Setup Maps to the Bare-Metal Guide

| Bare-Metal Concept        | Docker Equivalent                                       |
| ------------------------- | ------------------------------------------------------- |
| Two physical/VM servers   | Two containers (`mysql-master`, `mysql-replica`)        |
| Network IP of Master      | Container name `mysql-master` (Docker DNS)              |
| `/etc/mysql/my.cnf`       | `./master/my.cnf` mounted as `/etc/mysql/conf.d/my.cnf` |
| `systemctl restart mysql` | `docker compose restart mysql-master`                   |
| Port `3306` (default)     | Port `3307`/`3308` mapped on host to avoid conflicts    |
| `scp` to transfer dump    | `docker cp` or a shared volume                          |

---

## Summary

```
1. Create my.cnf for master (server-id=1, log_bin) and replica (server-id=2, read_only)
2. docker compose up -d
3. On Master:  CREATE USER replica_user, GRANT REPLICATION SLAVE, SHOW MASTER STATUS
4. On Replica: CHANGE MASTER TO ..., START SLAVE
5. Verify:     SHOW SLAVE STATUS\G  →  Slave_IO_Running: Yes, Slave_SQL_Running: Yes
6. Test:       Write on Master, read on Replica
```

---

**This completes the Docker-based MySQL replication test environment.** When you're ready to move to production, the concepts are identical — you simply replace container names with real IP addresses and harden the configuration with SSL, stronger passwords, and firewall rules.
