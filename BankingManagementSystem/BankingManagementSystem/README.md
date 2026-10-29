# Banking Management System

A learning project built with **Core Java, JDBC, MySQL, HTML, CSS and JavaScript**. The Java `HttpServer` exposes a small REST-style API and serves the browser UI. JDBC persists accounts and transactions in MySQL.

## Features
- Create savings/current accounts with email validation and duplicate-email handling
- Deposit and withdraw money
- Check account balances
- View accounts and recent transaction history
- Input validation, prepared SQL statements, SQL constraints, exception handling
- Database transaction with row locking for money movements to prevent concurrent balance updates

## Requirements
- JDK 17+
- Maven 3.8+
- MySQL 8+

## 1. Create the database
Open MySQL Workbench or the MySQL CLI and run `database/banking_db.sql`.

## 2. Configure the database connection
Defaults in the Java code are:
- URL: `jdbc:mysql://localhost:3306/banking_db?useSSL=false&serverTimezone=UTC`
- User: `root`
- Password: `root`

**Change these to match your local MySQL setup.** Prefer environment variables rather than committing credentials:
- Windows PowerShell:
  ```powershell
  $env:DB_URL="jdbc:mysql://localhost:3306/banking_db?useSSL=false&serverTimezone=UTC"
  $env:DB_USER="root"
  $env:DB_PASSWORD="your_mysql_password"
  ```
- macOS/Linux:
  ```bash
  export DB_URL='jdbc:mysql://localhost:3306/banking_db?useSSL=false&serverTimezone=UTC'
  export DB_USER='root'
  export DB_PASSWORD='your_mysql_password'
  ```

## 3. Run the application
From this project directory:
```bash
mvn clean package
mvn exec:java -Dexec.mainClass="com.example.banking.BankingServer"
```

If the `exec:java` goal is not available in your Maven setup, add the following plugin inside `pom.xml`'s `<build><plugins>` section and retry:
```xml
<plugin>
  <groupId>org.codehaus.mojo</groupId>
  <artifactId>exec-maven-plugin</artifactId>
  <version>3.5.0</version>
</plugin>
```

Then open **http://localhost:8080** in your browser.

## API overview
| Method | Endpoint | Purpose |
|---|---|---|
| GET | `/api/accounts` | List accounts |
| POST | `/api/accounts` | Create account (`name`, `email`, `type`) |
| POST | `/api/deposit` | Deposit (`accountId`, `amount`) |
| POST | `/api/withdraw` | Withdraw (`accountId`, `amount`) |
| GET | `/api/balance?accountId=1` | Balance inquiry |
| GET | `/api/transactions?accountId=1` | Transaction history |

## Project structure
```text
BankingManagementSystem/
├── database/
│   └── banking_db.sql
├── src/main/java/com/example/banking/
│   └── BankingServer.java
├── src/main/resources/web/
│   ├── index.html
│   ├── styles.css
│   └── app.js
├── pom.xml
└── README.md
```

## Important security notes
This is a **portfolio/college demo**, not production banking software. It has no login/authentication, authorization, CSRF protection, rate limiting, audit controls, or TLS. Do not expose it to the internet or use real customer data. A production banking system needs a security review, authenticated users, role-based access, secure secrets management, stronger audit logging, and appropriate regulatory controls.
