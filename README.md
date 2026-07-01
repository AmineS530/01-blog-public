# 01Blog - Student Learning Social Platform

01Blog is a social blogging platform designed to help students document and share their learning journey. It enables users to post content, subscribe to other users' "blocks", comment, like, receive notifications, and report inappropriate content, while providing administrators with robust moderation tools.

This project is a full-stack web application featuring a **Java Spring Boot** backend and an **Angular** frontend styled with **Angular Material**.

---

## 🚀 Key Features

### 🔐 Authentication & Security
- **Secure Auth**: User registration, login, and secure password hashing.
- **JWT Session Security**: Stateless JWT-based authentication flow with robust token validation.
- **Role-Based Access Control (RBAC)**: Enforces distinct levels of access for regular Users, Admins, and Super Admins.
- **Rate Limiting**: Protects critical endpoints (auth registration, logins, likes, comments, and follows) from spam and DDoS attempts.

### 📝 User Blocks (Profiles) & Posts
- **Personal Block**: Every user has a customizable public profile (their "block") showcasing all their posts.
- **Rich Post CRUD**: Users can create, update, read, and delete posts containing rich text and media.
- **Media Upload**: Supports Base64-based image and video uploads with preview capabilities.
- **Social Interactivity**: Real-time post and comment likes, threaded commenting, and dynamic websocket broadcasts of interaction counters.

### 🔔 Subscriptions & Notifications
- **Follow Flow**: Users can subscribe to (follow) other blocks to customize their homepage feed.
- **Interactive Feed**: A personalized feed populated with posts published by subscribed users.
- **Real-Time Notifications**: Unread notifications with instant updates for likes, comments, and new posts from followed profiles.

### 🛡️ Moderation & Admin Control
- **Content Reporting**: Flag inappropriate posts or blocks with custom reasons.
- **Admin Panel Control Center**:
  - Live community metrics telemetry.
  - Ban/unban users with custom reasons and temporary timers (e.g., 1 hour, 7 days, or permanent).
  - Modify display names and usernames.
  - Promote users to Admin roles (restricted to Super Admins).
  - Delete posts or comments violating guidelines.

---

## 🛠️ Technology Stack

### Backend
- **Core**: Java 17+, Spring Boot 3.3.x
- **Security**: Spring Security (JWT-based)
- **Database Access**: Spring Data JPA, Hibernate
- **Database**: PostgreSQL
- **Rate Limiting**: Bucket4j, Caffeine Cache
- **WebSockets**: Spring WebSocket

### Frontend
- **Framework**: Angular 18 (Standalone architecture)
- **Styling**: Angular Material 3, Vanilla CSS
- **State & Flow**: RxJS Observables

---

## 🏁 Getting Started

### Prerequisites
- Java 17+ Installed
- Node.js & npm (v18+ recommended)
- PostgreSQL running locally or in Docker

### Local Execution

#### 1. Backend Setup
1. Navigate to `/backend`.
2. Configure `.env` or application properties for your database connection:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/zero1blog
   spring.datasource.username=postgres
   spring.datasource.password=yourpassword
   ```
3. Run the Spring Boot application using Maven:
   ```bash
   ./mvnw spring-boot:run
   ```

#### 2. Frontend Setup
1. Navigate to `/frontend`.
2. Install project dependencies:
   ```bash
   npm install
   ```
3. Run the Angular development server:
   ```bash
   npm start
   ```
4. Access the application at `http://localhost:4200`.

### Orchestrated Execution (Makefile)
The project root contains a `Makefile` to simplify start and cleanup tasks:
```bash
# Start backend, database, and frontend concurrently
make all

# Stop services and clean up running instances
make clean
```
