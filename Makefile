# ANSI Color Codes
GREEN = \033[0;32m
YELLOW = \033[0;33m
BLUE = \033[0;34m
NC = \033[0m # No Color

.PHONY: all help install-docker run-container run-backend run-frontend

all: run-container run-backend run-frontend

help:
	@echo "${BLUE}Available commands:${NC}"
	@echo "  ${GREEN}make all${NC}             - Start the database, backend, and frontend"
	@echo "  ${GREEN}make run-container${NC}   - Check for Docker and start PostgreSQL"
	@echo "  ${GREEN}make run-backend${NC}     - Start the Spring Boot API"
	@echo "  ${GREEN}make run-frontend${NC}    - Start the Angular UI"

install-docker:
	@echo "${YELLOW}Checking if Docker is installed...${NC}"
	@if ! command -v docker >/dev/null 2>&1; then \
		echo "❌ ${BLUE}Docker is not installed! Please install Docker to continue.${NC}"; \
		exit 1; \
	else \
		echo "✅ ${GREEN}Docker is installed!${NC}"; \
	fi

run-container: install-docker
	@echo "${YELLOW}Starting PostgreSQL container...${NC}"
	@cd backend && sudo docker compose up -d
	@echo "✅ ${GREEN}Database container is up and running!${NC}"

run-backend: run-container
	@echo "${YELLOW}Starting Spring Boot Backend...${NC}"
	@cd backend && ./mvnw clean spring-boot:run > /dev/null 2>&1 &

run-frontend:
	@echo "${YELLOW}Starting Angular Frontend...${NC}"
	@cd frontend && ng serve > /dev/null 2>&1 &

clean:
	@echo "${YELLOW}Stopping Docker containers...${NC}"
	@cd backend && sudo docker compose down
	@echo "✅ ${GREEN}Docker containers stopped!${NC}"
	@echo "${YELLOW}Killing backend and frontend processes...${NC}"
	@fuser -k 8080/tcp > /dev/null 2>&1 || true
	@fuser -k 4200/tcp > /dev/null 2>&1 || true
	@echo "✅ ${GREEN}All processes stopped!${NC}"

re: clean all