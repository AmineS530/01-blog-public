
all: run-backend run-frontend

install-docker: 

run-container: install-docker
	cd backend && sudo docker compose up -d 
# cd backend && docker compose up -d 

run-backend: run-container
	cd backend && ./mvnw clean spring-boot:run

run-frontend:
	cd backend && ng serve
#npm install for extended