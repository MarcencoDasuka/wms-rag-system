# ISD — Inbound, Storage & Dispatch

[![Java](https://img.shields.io/badge/Java-21-blue.svg)](https://adoptium.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4-green.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-brightgreen.svg)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Docker](https://img.shields.io/badge/Docker-28.0-blue.svg)](https://www.docker.com/)

**ISD** is a lightweight Warehouse Management System (WMS) designed to simulate real-world warehouse operations — inbound logistics, inventory storage, replenishment, and dispatch. Built as a full-stack distributed system, it serves as a practical training ground for modern enterprise software development.

> **Note:** This project was developed during a 4-week internship (June 2–26, 2026) using Agile Scrum methodology with 4 sprints.

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Tech Stack](#tech-stack)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
  - [Prerequisites](#prerequisites)
  - [Running with Docker Compose](#running-with-docker-compose)
  - [Running Locally](#running-locally)
- [API Documentation](#api-documentation)
- [Scheduled Jobs](#scheduled-jobs)
- [Testing](#testing)
- [Logging](#logging)
- [Key Technologies Explained](#key-technologies-explained)

---

## Overview

ISD implements a client-server architecture:

- **Backend** (`wmsBack/`): Java 21 + Spring Boot REST API with JWT authentication, business logic, and PostgreSQL persistence.
- **Frontend** (`wmsFront/`): Vue 3 + Vite single-page application with PrimeVue components featuring:
  - Desktop-oriented dashboard for **Supervisors**
  - Mobile-friendly console for **Operators** with barcode scanner integration
  - Dark/Light theme toggle
- **Database**: PostgreSQL 16 with versioned schema migrations (Flyway).
- **Deployment**: Docker Compose for containerized orchestration.
- **Logging**: Logback for structured application logs with daily rotation.

The system supports three primary user roles:

| Role | Responsibilities |
|------|------------------|
| **DEV** | System administration, full access to all features |
| **Supervisor** | Product management, location management, inventory adjustment, task creation, user management, dashboard monitoring, shortage management |
| **Operator** | Execute assigned replenishment and picking tasks via mobile-optimized interface with barcode scanning |

---

## Key Features

### Core Functionality

| Feature | Description                                                                                                                         |
|---------|-------------------------------------------------------------------------------------------------------------------------------------|
| **Authentication & Authorization** | JWT-based login with role-based access control (DEV, SUPERVISOR, OPERATOR) and email confirmation, passwords encrypted using BCrypt |
| **Product Management** | CRUD operations with categories, search/filter, bulk import (Excel/CSV), and soft delete                                            |
| **Warehouse Location Management** | Zone-based location codes (e.g., `PICK-A-01`, `REPL-A-01`)                                                                          |
| **Inventory Management** | Stock tracking by location with manual adjustment and complete history logging                                                      |
| **Transport Unit (TU) Tracking** | Manage pallets, containers, and other transport units with barcode identification                                                   |
| **Replenishment Workflow** | Create requests → generate tasks → operators execute → stock updates automatically                                                  |
| **Picking Workflow** | Order creation → picking tasks → operators confirm quantities → inventory updates                                                   |
| **Shortage Management** | Automated daily detection of low stock levels with configurable thresholds and notifications                                        |
| **Task Monitoring** | Supervisors view active/completed/cancelled tasks filtered by status, operator, or type                                             |
| **Inventory History** | Full audit trail: product, quantity delta, before/after, locations, user, timestamp                                                 |
| **Supervisor Dashboard** | Real-time metrics: total inventory, open tasks, completed tasks, low stock alerts, shortage warnings                                |
| **User Management** | Create/disable users, assign roles, email confirmation for new accounts                                                             |

### Operator Mobile Features

| Feature | Description |
|---------|-------------|
| **Mobile-Optimized Interface** | Responsive UI with large actionable controls for warehouse floor operations |
| **Barcode Scanner Integration** | Native camera-based scanning for products, locations, and transport units |
| **Offline-Ready Task List** | View assigned tasks with minimal data usage |
| **Quick Actions** | One-tap task completion and quantity confirmation |

### Advanced Features

| Feature | Description                                                                                               |
|---------|-----------------------------------------------------------------------------------------------------------|
| **AI Assistant (Bonus)** | Chatbot interface for supervisors to query warehouse insights (e.g., "Show products with low stock")      |
| **Dark/Light Theme** | User-configurable theme with persistent preference storage                                                |
| **Bulk Data Import** | Excel and CSV file upload for products, locations, and initial inventory                                  |
| **Email Notifications** | Automated emails for user registration confirmation                                                       |
| **Scheduled Jobs** | Daily automated tasks: old order, task, allocation cleanup (2:00 AM)                                      |
| **Auto-Delete Old Orders** | Automatically purge completed orders older than 2 weeks (runs daily at 2:00 AM)                           |
| **Comprehensive Logging** | Logback-based structured logging with daily file rotation (50 MB max. per file, 2 GB max. total for logs) |

---

## Tech Stack

### Backend
- **Java 21** (LTS)
- **Spring Boot 3.4**
  - Spring Security with JWT authentication
  - Spring Data JPA (Hibernate)
  - Spring Mail (email confirmation)
  - Spring Scheduling (cron jobs)
- **PostgreSQL 16** (via JDBC)
- **Maven** (build tool)
- **Flyway** (database migrations)
- **Logback** (logging framework)
- **Swagger/OpenAPI** (API documentation)
- **MapStruct** (DTO mapping)
- **Apache POI** (Excel import/export)

### Frontend
- **Vue 3** with Composition API
- **PrimeVue** (UI component library)
- **Vite** (build tool)
- **Vue Router** (navigation)
- **Axios** (HTTP client)
- **Pinia** (state management)
- **ZXing** (barcode scanning)
- **ESLint** (code linting)
- **TailwindCSS** (stylizing)

### Infrastructure
- **Docker** & **Docker Compose**
- **PostgreSQL 16** (containerized)
- **Git** (version control)

---

## Project Structure

```text
inbound-storage-dispatch/                       # Root-ul proiectului (Sistem WMS)
├── wmsBack/                                    # Aplicația Backend (Spring Boot)
│   ├── src/                                    # Codul sursă al backend-ului
│   │   ├── main/                               # Codul de producție al aplicației
│   │   │   ├── java/com/isd/wms/               # Pachetul principal Java
│   │   │   │   ├── config/                     # Configurări sistem (CORS, Swagger, Beans)
│   │   │   │   ├── controller/                 # Endpoint-urile REST expuse către frontend
│   │   │   │   ├── dto/                        # Obiecte pentru transferul de date (DTO)
│   │   │   │   ├── entity/                     # Modelele bazei de date (Entități JPA)
│   │   │   │   ├── enums/                      # Enumerări globale (Role, TaskStatus, etc.)
│   │   │   │   ├── exception/                  # Managementul erorilor și excepții custom
│   │   │   │   ├── job/                        # Task-uri programate automat (CRON jobs)
│   │   │   │   ├── mapper/                     # Conversii Entități ↔ DTO-uri (MapStruct)
│   │   │   │   ├── repository/                 # Interfețe pentru baza de date (Spring Data JPA)
│   │   │   │   ├── security/                   # Autentificare, autorizare, JWT și config securitate
│   │   │   │   └── service/                    # Logica de business centrală a backend-ului
│   │   │   └── resources/                      # Fișiere de configurare și resurse statice
│   │   │       ├── application.properties      # Proprietățile principale Spring Boot
│   │   │       ├── logback-spring.xml          # Configurarea nivelelor de logging
│   │   │       └── db/migration/               # Scripturile pentru baza de date (Flyway)
│   │   └── test/                               # Testele unitare (JUnit/Mockito)
│   ├── pom.xml                                 # Fișierul de configurare Maven (dependențe)
│   └── mvnw                                    # Scriptul Maven Wrapper pentru Linux/macOS
│
├── wmsFront/                                   # Aplicația Frontend (Vue 3 + Vite)
│   ├── src/                                    # Codul sursă al interfeței grafice
│   │   ├── api/                                # Instanțele Axios și apelurile HTTP
│   │   ├── assets/                             # Resurse statice (imagini, stiluri CSS)
│   │   ├── components/                         # Componente Vue reutilizabile
│   │   ├── composables/                        # Funcții reutilizabile (Composition API)
│   │   ├── layouts/                            # Structurile de pagină (Supervisor / Operator)
│   │   ├── router/                             # Configurarea rutelor (Vue Router)
│   │   ├── services/                           # Servicii frontend (procesarea datelor din API)
│   │   ├── stores/                             # Managementul stării globale (Pinia / Vuex)
│   │   ├── utils/                              # Funcții utilitare (formatări, validări)
│   │   └── views/                              # Paginile principale (Dashboard, Inventory, etc.)
│   ├── package.json                            # Dependențele NPM și scripturile de pornire
│   └── vite.config.js                          # Configurarea tool-ului de build Vite
│
├── logs/                                       # Logurile aplicației generate la rulare
├── .env                                        # Fișierul pentru variabilele de mediu (parole, chei)
├── docker-compose.yaml                         # Configurarea Docker pentru ridicarea containerelor
└── README.md                                   # Documentația principală a proiectului
```

---

## Getting Started

### Prerequisites

- **Docker** & **Docker Compose** (recommended)
- **Java 21** (for local backend development)
- **Node.js 18+** & **npm** (for local frontend development)
- **PostgreSQL 16** (if running without Docker)

### Running with Docker Compose

1. **Clone the repository:**

```text
git clone https://github.com/isd-soft/inbound-storage-dispatch.git
cd inbound-storage-dispatch
```

2. **Configure environment variables** (edit `.env` if needed):

```text
POSTGRES_DB=wmsdb
POSTGRES_USER=wmsuser
POSTGRES_PASSWORD=wmspass
SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/wmsdb
JWT_SECRET=your-jwt-secret-key-change-in-production
SPRING_MAIL_HOST=smtp.gmail.com
SPRING_MAIL_PORT=587
```

3. **Build and start all services:**

```text
docker-compose up --build
```

4. **Access the application:**
  - Frontend: http://localhost:5173
  - Backend API: http://localhost:8080
  - Swagger UI: http://localhost:8080/swagger-ui/index.html

5. **Default login credentials:**
  - Dev: `dev@isd.com` / `password`
  - Supervisor: `supervisor@isd.com` / `password`
  - Operator: `operator@isd.com` / `password`

### Running Locally (Development)

**Backend:**

```text
cd wmsBack
./mvnw spring-boot:run
```

**Frontend:**

```text
cd wmsFront
npm install
npm run dev
```

---

## API Documentation

Swagger UI: http://localhost:8080/swagger-ui/index.html

### Key Endpoints

#### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Authenticate and receive JWT token |
| POST | `/api/auth/verify` | Verify JWT token validity |

#### User Management (Supervisor)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/supervisor/users` | List all users |
| POST | `/api/supervisor/users/register` | Register new user |
| PUT | `/api/supervisor/users/{id}` | Update user details |
| DELETE | `/api/supervisor/users/{id}` | Delete user |

#### Products
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/products` | List all products |
| GET | `/api/products/{id}` | Get product by ID |
| POST | `/api/products` | Create new product |
| PUT | `/api/products/{id}` | Update product |
| DELETE | `/api/products/{id}` | Soft delete product |
| POST | `/api/products/imports` | Bulk import products from Excel/CSV |
| GET | `/api/products/search` | Search products with filters |
| GET | `/api/products/quantities` | Get product quantities summary |

#### Categories
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/categories` | List all categories |
| GET | `/api/categories/{id}` | Get category by ID |
| POST | `/api/categories` | Create new category |
| PUT | `/api/categories/{id}` | Update category |
| DELETE | `/api/categories/{id}` | Delete category |
| POST | `/api/categories/imports` | Bulk import categories |

#### Locations
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/locations` | List all warehouse locations |
| GET | `/api/locations/{id}` | Get location by ID |
| POST | `/api/locations` | Create new location |
| PUT | `/api/locations/{id}` | Update location |
| DELETE | `/api/locations/{id}` | Delete location |
| POST | `/api/locations/imports` | Bulk import locations |
| GET | `/api/locations/dispatches` | Get dispatch locations |

#### Inventory
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/inventory` | View current inventory |
| GET | `/api/inventory/{stockId}` | Get stock details by ID |
| GET | `/api/inventory/{stockId}/history` | View stock movement history |
| GET | `/api/inventory/history` | View all inventory history |
| PUT | `/api/inventory/adjust` | Manual stock adjustment |
| PATCH | `/api/inventory/{stockId}/adjust` | Partial stock adjustment |
| POST | `/api/inventory/{stockId}/adjust/preview` | Preview adjustment before applying |
| POST | `/api/inventory/add` | Add new inventory stock |
| POST | `/api/inventory/remove` | Remove inventory stock |
| POST | `/api/inventory/imports` | Bulk import inventory data |

#### Orders
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/orders` | List all orders |
| GET | `/api/v1/orders/{id}` | Get order by ID |
| GET | `/api/v1/orders/extended/{id}` | Get extended order details |
| GET | `/api/v1/orders/extended` | List all orders with extended data |
| POST | `/api/v1/orders` | Create new order |
| PUT | `/api/v1/orders/{id}` | Update order |
| PUT | `/api/v1/orders/extended/{id}` | Update order with extended data |
| DELETE | `/api/v1/orders/{id}` | Delete order |
| POST | `/api/v1/orders/{orderId}/operators/{operatorId}` | Assign operator to order |
| POST | `/api/v1/orders/imports` | Bulk import orders |
| GET | `/api/v1/orders/{id}/shortage-details` | Get shortage details for order |
| GET | `/api/v1/orders/shortages` | List all order shortages |
| GET | `/api/v1/orders/filter` | Filter orders by criteria |

#### Order Lines
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/order-lines` | List all order lines |
| GET | `/api/v1/order-lines/{id}` | Get order line by ID |
| PUT | `/api/v1/order-lines/{id}` | Update order line |
| DELETE | `/api/v1/order-lines/{id}` | Delete order line |

#### Replenishment
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/replenishments` | List all replenishment requests |
| GET | `/api/replenishments/{id}` | Get replenishment by ID |
| POST | `/api/replenishments` | Create replenishment request |
| PUT | `/api/replenishments/{id}` | Update replenishment |
| DELETE | `/api/replenishments/{id}` | Delete replenishment |
| POST | `/api/replenishments/{replenishmentId}/operators/{operatorId}` | Assign operator to replenishment |
| POST | `/api/replenishments/{id}/cancel` | Cancel replenishment request |
| POST | `/api/replenishments/search` | Search replenishments |
| POST | `/api/replenishments/imports` | Bulk import replenishments |
| POST | `/api/replenishments/filter` | Filter replenishments by criteria |

#### Allocations (Task Execution)
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/allocations` | List all allocations |
| GET | `/api/v1/allocations/operators` | Get operator allocations |
| GET | `/api/v1/allocations/operator/current/summary` | Get current operator task summary |
| POST | `/api/v1/allocations/start` | Start new allocation task |
| POST | `/api/v1/allocations/operator/current/start` | Operator starts current task |
| POST | `/api/v1/allocations/operator/current/complete` | Operator completes current task |
| POST | `/api/v1/allocations/{id}/scan-tu` | Scan transport unit barcode |
| POST | `/api/v1/allocations/{id}/product` | Confirm product in allocation |
| POST | `/api/v1/allocations/{id}/location` | Confirm location in allocation |
| POST | `/api/v1/allocations/{id}/dispatch` | Dispatch allocation |
| POST | `/api/v1/allocations/{id}/confirm-quantity` | Confirm picked/moved quantity |
| POST | `/api/v1/allocations/{id}/complete` | Complete allocation task |

#### Supervisor Dashboard
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/supervisor/dashboard` | Get dashboard metrics (inventory, tasks, shortages, alerts) |

#### AI Chat Assistant
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/chat` | Query AI assistant for warehouse insights |

---

## Scheduled Jobs

| Job | Schedule | Description |
|-----|----------|-------------|
| **Shortage Detection** | Daily at 8:00 AM | Scans inventory for items below minimum threshold |
| **Old Order Cleanup** | Daily at 2:00 AM | Deletes completed orders older than 2 weeks |
| **Log Rotation** | Daily at midnight | Rotates application logs |

---

## Testing

### Run all tests:

```text
cd wmsBack
./mvnw test
```

### Test Categories
- **Service Layer**: `ProductServiceTest`, `InventoryServiceTest`, `TaskServiceTest`
- **Controller Layer**: `ProductControllerTest`, `AuthControllerTest`
- **Repository Layer**: `ProductRepositoryTest`, `InventoryRepositoryTest`
- **Integration Tests**: `WarehouseWorkflowIntegrationTest`

---

## Logging

**Logback** configuration:

- **Console Output**: INFO level
- **File Output**:
  - `logs/app-wms.YYYY-MM-DD.0.log` (previous file)
  - `logs/app-wms.log` (recent file)
- **Log Rotation**: Daily, 30-day retention
- **Sizes**: max. 50MB (file) & max. 2 GB (all logs)
- **Pattern**: `%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n`

### Log Levels

- com.isd.wms.controller - ```INFO```
- com.isd.wms.service - ```DEBUG```
- com.isd.wms.job - ```INFO```
- org.springframework.security - ```WARN```
- org.hibernate - ```WARN```

---

## Key Technologies Explained

### PrimeVue Components
- **DataTable**: Product lists, inventory views
- **Dialog**: Modal forms
- **Toast**: Notifications
- **FileUpload**: Excel/CSV import
- **Theme Switching**: Dark/light mode

### Barcode Scanner
- **Library**: ZXing (QuaggaJS)
- **Format**: Code 128
- **Usage**: Product/location/TU verification

### Email Confirmation
1. User registers
2. System sends email with token
3. User clicks confirmation link
4. User confirm a valid strong password
5. Account activated (redirect to login page)

---

## Acknowledgments

- Built as part of **ISD Soft** internship program
- **Git Project Board** for sprint planning
- **Slack** for communication in team
- Inspired by existing WMS by ISD for Inther Group

---

## Future Enhancements

- [ ] Multi-warehouse support
- [ ] Automated mail for critical alerts
- [ ] Adding Customer & auto-mail on shortage
- [ ] Limited capacity for Transport Unit
- [ ] Voice picking
- [ ] RFID support
- [ ] Native mobile apps
- [ ] ML-based predictions
- [ ] and others...

---

**For additional help, see [HELP.md](HELP.md)**