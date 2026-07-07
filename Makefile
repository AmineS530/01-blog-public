# ANSI Color Codes
GREEN = \033[0;32m
YELLOW = \033[0;33m
BLUE = \033[0;34m
NC = \033[0m # No Color


all: run-production

help:
	@echo "${BLUE}Available commands:${NC}"
	@echo "  ${GREEN}make all${NC}             - Start the backend and frontend"
	@echo "  ${GREEN}make run-production${NC} - Build and start the backend and frontend in production mode"

run-production: run-production-backend run-production-frontend

run-production-backend:
	@echo "${YELLOW}Building and Starting Spring Boot Backend in Production...${NC}"
	@mkdir -p backend/logs
	@cd backend && ./mvnw clean package -DskipTests > logs/build.log 2>&1
	@cd backend && java -jar target/backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod > logs/console.log 2>&1 &
	@echo "📝 Backend logs are being written to ${BLUE}backend/logs/console.log${NC}"
	@echo "🚀 Backend is running at ${BLUE}http://localhost:8080${NC}"

run-production-frontend: node_modules
	@echo "${YELLOW}Building Angular Frontend in Production...${NC}"
	@cd frontend && npx ng build --configuration production > console.log 2>&1
	@echo "${YELLOW}Starting Angular Frontend in Production (with npx)...${NC}"
	@cd frontend && npx -y serve -s dist/frontend/browser -l 4200 > console.log 2>&1 &
	@echo "📝 Frontend logs are being written to ${BLUE}frontend/console.log${NC}"
	@echo "🚀 Frontend is running at ${BLUE}http://localhost:4200${NC}"

node_modules: frontend/package.json
	@if [ -d "frontend/node_modules" ]; then \
		echo "✅ Dependencies already installed."; \
	else \
		echo "📦 Installing frontend dependencies..."; \
		cd frontend && npm install; \
	fi

clean:
	@fuser -k 8080/tcp > /dev/null 2>&1 || true
	@fuser -k 4200/tcp > /dev/null 2>&1 || true
	@echo "✅ ${GREEN}All processes stopped!${NC}"

re: clean all

.PHONY: all help run-backend run-frontend run-production run-production-backend run-production-frontend