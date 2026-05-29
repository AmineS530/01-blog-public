# ANSI Color Codes
GREEN = \033[0;32m
YELLOW = \033[0;33m
BLUE = \033[0;34m
NC = \033[0m # No Color


all: run-backend run-frontend

help:
	@echo "${BLUE}Available commands:${NC}"
	@echo "  ${GREEN}make all${NC}             - Start the backend and frontend (connecting to Supabase)"
	@echo "  ${GREEN}make run-backend${NC}     - Start the Spring Boot API (connecting to Supabase)"
	@echo "  ${GREEN}make run-frontend${NC}    - Start the Angular UI"
	
run-backend:
	@echo "${YELLOW}Starting Spring Boot Backend...${NC}"
	@mkdir -p backend/logs
	@cd backend && env -u SPRING_DATASOURCE_URL -u SPRING_DATASOURCE_USERNAME -u SPRING_DATASOURCE_PASSWORD -u DATABASE_URL -u JDBC_DATABASE_URL -u JDBC_DATABASE_USERNAME -u JDBC_DATABASE_PASSWORD ./mvnw clean spring-boot:run > logs/console.log 2>&1 &
	@echo "📝 Backend logs are being written to ${BLUE}backend/logs/console.log${NC}"

run-frontend:
	@echo "${YELLOW}Starting Angular Frontend...${NC}"
	@cd frontend && ng serve > console.log 2>&1 &
	@echo "📝 Frontend logs are being written to ${BLUE}frontend/console.log${NC}"

clean:
	@fuser -k 8080/tcp > /dev/null 2>&1 || true
	@fuser -k 4200/tcp > /dev/null 2>&1 || true
	@echo "✅ ${GREEN}All processes stopped!${NC}"

re: clean all

.PHONY: all help run-backend run-frontend