# 🚀 Finance Dashboard Backend

A **production-ready Spring Boot backend** for a finance management system with **secure authentication, role-based access control, analytics dashboard, and audit logging**.

Designed with **real-world backend architecture principles**, focusing on scalability, security, and maintainability.

---

## 🔥 Key Features

### 🔐 Authentication & Security

* JWT-based authentication (Access + Refresh Tokens)
* Role-based access control (ADMIN / VIEWER)
* Secure API endpoints with Spring Security
* Custom exception handling for consistent API responses

---

### 👥 User Management

* Create, update, deactivate users (ADMIN only)
* Fetch user profiles and details
* Soft delete support for safe data handling

---

### 💳 Financial Records

* Full CRUD operations for financial transactions
* Supports categories and record types (INCOME / EXPENSE)
* BigDecimal used for precise financial calculations
* Date-based filtering and search support

---

### 📊 Analytics Dashboard

* Total income, expense, and balance summary
* Category-wise aggregation
* Monthly trend analysis
* Recent activity tracking

---

### 🧾 Audit Logging (Advanced Feature)

* Tracks every critical system action:

  * User creation
  * Record creation/update/delete
* Stores actor, action, entity, timestamps, and changes
* Supports filtering and history tracking
* Designed for compliance and debugging

---

## 🧠 Architecture Highlights

* Layered architecture (Controller → Service → Repository)
* DTO-based request/response structure
* Centralized exception handling
* Soft delete using `@SQLRestriction`
* Clean separation of concerns
* Pagination & filtering implemented across modules

---

## 🛠️ Tech Stack

* **Backend:** Spring Boot 3, Spring Security, Spring Data JPA
* **Database:** PostgreSQL
* **Authentication:** JWT (jjwt)
* **Build Tool:** Maven
* **Other:** Lombok, Validation API

---

## ⚙️ API Base URL

```bash
http://localhost:8080/v1
```

---

## 🧪 Sample API Endpoints

### 🔐 Auth

* `POST /v1/auth/register`
* `POST /v1/auth/login`

### 👥 Users

* `GET /v1/users`
* `POST /v1/users`
* `PUT /v1/users/{id}`
* `PUT /v1/users/{id}/deactivate`

### 💳 Records

* `POST /v1/records`
* `GET /v1/records`
* `PUT /v1/records/{id}`
* `DELETE /v1/records/{id}`

### 📊 Dashboard

* `GET /v1/dashboard/summary`
* `GET /v1/dashboard/by-category`
* `GET /v1/dashboard/monthly-trend`
* `GET /v1/dashboard/recent-activity`

### 🧾 Audit Logs

* `GET /v1/audit-logs`
* `GET /v1/audit-logs/actor/{actorId}`
* `GET /v1/audit-logs/entity/{entityType}/{entityId}`

---

## 🚀 Getting Started

### 1️⃣ Clone the repository

```bash
git clone https://github.com/bhargavibattula/finance-dashboard-system.git
cd finance-dashboard-system
```

---

### 2️⃣ Configure Database

Update `application.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/finance_db
    username: your_username
    password: your_password
```

---

### 3️⃣ Run the application

```bash
mvn clean install
mvn spring-boot:run
```

---

### 4️⃣ Access APIs

Use Postman or Swagger:

```
http://localhost:8080/swagger-ui/index.html
```

---

## 🔐 Default Admin Access

To ensure smooth testing, the application automatically seeds a default admin user on first startup.

### 👤 Admin Credentials

```
Email: admin@test.com  
Password: Password@123
```

### ⚠️ Note

* This admin is created only if no users exist in the database.
* After deployment, you can log in immediately using these credentials and test all APIs.

---


## 💡 Design Decisions

* **BigDecimal over Double** → avoids floating-point precision issues
* **Soft Delete** → ensures auditability and data recovery
* **Audit Logs** → enables traceability for every action
* **JWT Tokens** → stateless and scalable authentication

---

## 📈 Future Enhancements

* AI-powered financial insights (spending patterns, recommendations)
* Export reports (PDF/CSV)
* Caching using Redis
* Rate limiting & monitoring

