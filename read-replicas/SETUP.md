# MySQL Master-Replica Setup Guide

This guide walks you through configuring MySQL replication between a **Master** server and one or more **Replica** (Slave) servers. MySQL replication allows data written to the Master to be automatically copied to Replica servers, enabling read scalability, high availability, and backup strategies.

> **Prerequisites:**
>
> - MySQL installed on both Master and Replica servers (same or compatible versions recommended)
> - Both servers can communicate over the network (port `3306` open in firewall)
> - Root or sudo access on both machines

---

## Understanding MySQL Replication

MySQL replication works using a **binary log (binlog)** mechanism:

1. Every write operation (INSERT, UPDATE, DELETE, DDL) on the Master is recorded in the binary log.
2. The Replica's **I/O thread** connects to the Master and copies these log events into its own **relay log**.
3. The Replica's **SQL thread** reads the relay log and replays those events on the Replica's database.

This is asynchronous by default — the Master does not wait for the Replica to confirm receipt before returning success to the client.

---

## 1. Configure the Master Server

### 1.1 Edit MySQL Configuration (`my.cnf`)

The Master must be configured to:

- Listen on a network interface accessible by the Replica
- Have a unique server ID (used to distinguish nodes in replication)
- Enable binary logging (the source of truth for replication)

Open the MySQL configuration file (typically `/etc/mysql/my.cnf` or `/etc/mysql/mysql.conf.d/mysqld.cnf`):

```bash
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
```

Add or update the following under the `[mysqld]` section:

```ini
[mysqld]
bind-address = 0.0.0.0
server-id = 1
log_bin = mysql-bin
```

**What each option does:**

| Option                   | Purpose                                                                                                                                                                               |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `bind-address = 0.0.0.0` | Makes MySQL listen on all network interfaces, not just localhost. This is required so the Replica can connect over the network. For security, you may restrict this to a specific IP. |
| `server-id = 1`          | A unique integer identifying this server in the replication topology. Every server in a replication setup **must** have a distinct `server-id`.                                       |
| `log_bin = mysql-bin`    | Enables binary logging and sets the filename prefix. Binary log files will be named `mysql-bin.000001`, `mysql-bin.000002`, etc.                                                      |

**Optional but recommended settings:**

```ini
binlog_do_db = your_database_name   # Only replicate a specific database (omit to replicate all)
expire_logs_days = 7                # Auto-purge binary logs older than 7 days to save disk space
max_binlog_size = 100M              # Rotate binary log file when it reaches 100MB
```

### 1.2 Restart MySQL

Apply the configuration changes by restarting the MySQL service:

```bash
sudo systemctl restart mysql
```

Verify MySQL is running:

```bash
sudo systemctl status mysql
```

### 1.3 Create a Dedicated Replication User

It is best practice to create a dedicated MySQL user for replication rather than reusing the `root` account. This limits privileges and makes it easier to manage access.

Log into the MySQL shell:

```bash
mysql -u root -p
```

Inside the MySQL shell, run:

```sql
CREATE USER 'replica_user'@'%' IDENTIFIED BY 'replica_password';
GRANT REPLICATION SLAVE ON *.* TO 'replica_user'@'%';
FLUSH PRIVILEGES;
```

**Breaking this down:**

- `CREATE USER 'replica_user'@'%'` — Creates a user named `replica_user` that can connect from **any host** (`%` is a wildcard). Replace `%` with the Replica's specific IP (e.g., `'192.168.1.20'`) for better security.
- `IDENTIFIED BY 'replica_password'` — Sets the password. Use a strong, unique password in production.
- `GRANT REPLICATION SLAVE ON *.*` — Grants the minimum permission needed for replication. `REPLICATION SLAVE` allows the user to read binary log events from the Master.
- `FLUSH PRIVILEGES` — Reloads the grant tables so the new user and permissions take effect immediately.

> **Security Note:** In production, replace `%` with the Replica's actual IP address to prevent unauthorized access from other hosts.

### 1.4 Lock Tables and Get Master Binary Log Position

Before setting up the Replica, you need to record the **exact point** in the Master's binary log from which the Replica should start reading. If you have an existing database to copy over, you must briefly lock writes to get a consistent snapshot.

In the MySQL shell on the Master:

```sql
FLUSH TABLES WITH READ LOCK;
SHOW MASTER STATUS;
```

**Example output:**

```
+------------------+----------+--------------+------------------+
| File             | Position | Binlog_Do_DB | Binlog_Ignore_DB |
+------------------+----------+--------------+------------------+
| mysql-bin.000003 |      154  |              |                  |
+------------------+----------+--------------+------------------+
```

- **`File`**: The current binary log file name. The Replica will start reading from this file.
- **`Position`**: The byte offset within the file. The Replica will start from this exact position.

> **Important:** Note these values exactly. Do **not** run any other queries on this connection until you've either taken a data snapshot or confirmed the Replica is set up — keeping the lock active ensures the position remains valid.

### 1.5 (Optional) Export Existing Data

If the Master already has data that the Replica needs to start with, export it using `mysqldump` from a **separate terminal** while keeping the lock connection open:

```bash
mysqldump -u root -p --all-databases --master-data > master_dump.sql
```

The `--master-data` flag automatically includes the `CHANGE MASTER TO` statement with the correct log file and position in the dump. Transfer this file to the Replica server:

```bash
scp master_dump.sql user@replica_ip:/home/user/
```

After the dump completes, release the lock on the Master:

```sql
UNLOCK TABLES;
```

---

## 2. Configure the Replica Server

### 2.1 Edit MySQL Configuration (`my.cnf`)

The Replica also needs a unique `server-id`. Log into the **Replica server** and edit its MySQL config:

```bash
sudo nano /etc/mysql/mysql.conf.d/mysqld.cnf
```

Add under `[mysqld]`:

```ini
[mysqld]
server-id = 2
```

Each Replica must have a unique `server-id` different from the Master and all other Replicas (e.g., `2`, `3`, `4`...).

**Optional Replica-specific settings:**

```ini
relay-log = relay-bin           # Filename prefix for relay log files
read_only = 1                   # Prevents accidental writes to the Replica (recommended)
log_slave_updates = 1           # Required if this Replica will itself be a Master to other Replicas
```

### 2.2 Restart MySQL on the Replica

```bash
sudo systemctl restart mysql
```

### 2.3 (Optional) Import Existing Data

If you exported data from the Master, import it now before starting replication:

```bash
mysql -u root -p < /home/user/master_dump.sql
```

### 2.4 Connect the Replica to the Master

Log into the MySQL shell on the Replica:

```bash
mysql -u root -p
```

Run the `CHANGE MASTER TO` command with the values from Step 1.4:

```sql
CHANGE MASTER TO
  MASTER_HOST='master_ip',
  MASTER_USER='replica_user',
  MASTER_PASSWORD='replica_password',
  MASTER_LOG_FILE='mysql-bin.000003',
  MASTER_LOG_POS=154;
```

**Parameter breakdown:**

| Parameter         | Description                                        |
| ----------------- | -------------------------------------------------- |
| `MASTER_HOST`     | The IP address or hostname of the Master server    |
| `MASTER_USER`     | The replication user created on the Master         |
| `MASTER_PASSWORD` | Password for the replication user                  |
| `MASTER_LOG_FILE` | The binary log file name from `SHOW MASTER STATUS` |
| `MASTER_LOG_POS`  | The position offset from `SHOW MASTER STATUS`      |

> **Replace** `master_ip` with the actual IP of your Master server, and use the `File` and `Position` values you noted earlier.

### 2.5 Start Replication

```sql
START SLAVE;
```

This starts two background threads on the Replica:

- **I/O Thread** — connects to the Master and copies binary log events to the local relay log
- **SQL Thread** — reads from the relay log and executes the SQL statements to keep the Replica in sync

---

## 3. Verify Replication is Working

On the Replica, run:

```sql
SHOW SLAVE STATUS\G
```

The `\G` formats the output vertically for readability. Look for these key fields:

```
Slave_IO_Running: Yes
Slave_SQL_Running: Yes
Seconds_Behind_Master: 0
Master_Host: 192.168.1.10
Master_User: replica_user
Master_Log_File: mysql-bin.000003
Read_Master_Log_Pos: 154
```

**What to check:**

| Field                   | Expected Value | Meaning                                                                        |
| ----------------------- | -------------- | ------------------------------------------------------------------------------ |
| `Slave_IO_Running`      | `Yes`          | The I/O thread is successfully connected to the Master and reading binary logs |
| `Slave_SQL_Running`     | `Yes`          | The SQL thread is successfully applying events from the relay log              |
| `Seconds_Behind_Master` | `0` (or low)   | How many seconds the Replica lags behind the Master. `0` means fully in sync.  |
| `Last_IO_Error`         | _(empty)_      | Any I/O errors (e.g., connection issues, authentication failure)               |
| `Last_SQL_Error`        | _(empty)_      | Any SQL errors (e.g., duplicate key, missing table)                            |

### Quick Test

On the **Master**, create a test table:

```sql
CREATE DATABASE replication_test;
USE replication_test;
CREATE TABLE hello (id INT PRIMARY KEY, msg VARCHAR(50));
INSERT INTO hello VALUES (1, 'Replication is working!');
```

On the **Replica**, verify the data appeared:

```sql
USE replication_test;
SELECT * FROM hello;
```

If you see the row, replication is functioning correctly.

---

## 4. Troubleshooting

### Issue: `Slave_IO_Running: No`

This means the Replica cannot connect to the Master.

**Steps to diagnose:**

1. Check network connectivity: `ping master_ip` from the Replica
2. Verify port `3306` is open on the Master: `telnet master_ip 3306` or `nc -zv master_ip 3306`
3. Confirm the replication user can connect from the Replica:
   ```bash
   mysql -u replica_user -p -h master_ip
   ```
4. Check the Master firewall:
   ```bash
   sudo ufw allow from replica_ip to any port 3306
   ```
5. Confirm `bind-address` on the Master is not set to `127.0.0.1`

---

### Issue: `Slave_SQL_Running: No`

This means a SQL error occurred while applying events.

**Steps to diagnose:**

1. Check `Last_SQL_Error` in `SHOW SLAVE STATUS\G` for the specific error
2. Common causes:
   - Duplicate key error (data already exists on Replica)
   - A table that exists on the Master doesn't exist on the Replica
3. To skip a single problematic event and resume (use carefully):
   ```sql
   STOP SLAVE;
   SET GLOBAL SQL_SLAVE_SKIP_COUNTER = 1;
   START SLAVE;
   ```

---

### Issue: High `Seconds_Behind_Master`

The Replica is falling behind the Master.

**Possible causes:**

- Heavy write load on the Master
- Replica hardware is underpowered
- Long-running queries on the Replica blocking the SQL thread

**Solutions:**

- Enable **parallel replication** (MySQL 5.7+):
  ```ini
  slave_parallel_workers = 4
  slave_parallel_type = LOGICAL_CLOCK
  ```
- Optimize slow queries on the Replica

---

### General Checks

- **View MySQL error logs:**
  ```bash
  sudo tail -f /var/log/mysql/error.log
  ```
- **Check user privileges on Master:**
  ```sql
  SHOW GRANTS FOR 'replica_user'@'%';
  ```
- **Verify server IDs are unique:**
  ```sql
  SHOW VARIABLES LIKE 'server_id';
  ```

---

## 5. Stopping and Resetting Replication

To **stop** replication on the Replica:

```sql
STOP SLAVE;
```

To **fully reset** replication configuration (e.g., to reconfigure):

```sql
STOP SLAVE;
RESET SLAVE ALL;
```

---

## Summary

| Step | Server  | Action                                                   |
| ---- | ------- | -------------------------------------------------------- |
| 1    | Master  | Enable binary logging and set `server-id = 1`            |
| 2    | Master  | Create `replica_user` with `REPLICATION SLAVE` privilege |
| 3    | Master  | Run `SHOW MASTER STATUS` to get log file and position    |
| 4    | Replica | Set a unique `server-id` (e.g., `2`)                     |
| 5    | Replica | Run `CHANGE MASTER TO ...` with Master's details         |
| 6    | Replica | Run `START SLAVE`                                        |
| 7    | Replica | Verify with `SHOW SLAVE STATUS\G`                        |

---

**This completes the MySQL Master-Replica replication setup.** For production environments, consider additional hardening such as SSL/TLS for the replication connection, using `replica_preserve_commit_order` for consistency, and monitoring tools like Percona Monitoring and Management (PMM).
