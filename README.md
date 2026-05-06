# 01-Blog: Full-Stack Spring Boot & Angular Material 3 Platform

A high-performance, visually polished blog platform built with Spring Boot 3.3.x and Angular 18 (Material 3). This project strictly follows the Zero-One-Blog reference architecture.

## 🚀 Key Features

- **Dynamic Feed**: Browse posts with author avatars, tags, and ISO-8601 formatted timestamps.
- **Notification System**: Real-time (UI-driven) alerts for Likes, Comments, and Follows with unread counts.
- **Admin Dashboard**: Moderation tools, user/post reporting, and system statistics.
- **Media Management**: Robust Base64-based image uploads with persistent file tracking.
- **Security**: JWT-based authentication with role-based access control (User/Admin).
- **Architecture**:
    - **Backend**: Clean Spring Boot layers (Controller, Service, DTO, Model, Repository).
    - **Frontend**: 100% Standalone components, no `@NgModule`, Material 3 design tokens.

## 🛠 Tech Stack

- **Backend**: Java 17, Spring Boot 3.3.x, Spring Data JPA, PostgreSQL, Spring Security (JWT).
- **Frontend**: Angular 18+, Angular Material 3, Zone.js, TypeScript.
- **Orchestration**: Docker, Docker Compose, GNU Make.

## 🏁 Getting Started

### Prerequisites
- Docker & Docker Compose
- Java 17+
- Node.js & npm (Angular CLI recommended)

### One-Command Setup
The project includes a `Makefile` to streamline the development environment.

```bash
# Start the database, backend, and frontend concurrently
make all

# Stop all services and clean up processes
make clean
```

## 📂 Project Structure

- `/backend`: Spring Boot API, Docker Compose configuration, and media uploads.
- `/frontend`: Angular SPA using standalone components and Material 3.
- `/todo`: Real-time progress tracker for implementation milestones.

## 📜 Commit History
The project maintains a detailed history reflecting the development lifecycle from April 29 to May 13, 2026, documenting the evolution of every major feature.

---
*Built with precision to match the Zero-One-Blog standard.*
