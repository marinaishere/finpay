#!/bin/bash

# FinPay Microservices Startup Script
# This script starts all services in the correct order

set -e

PROJECT_DIR="/Users/mengruwang/Github/finpay"
LOG_DIR="$PROJECT_DIR/logs"

# Create logs directory if it doesn't exist
mkdir -p "$LOG_DIR"

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${YELLOW}Starting FinPay Microservices...${NC}"

# Check if Docker is running
echo ""
echo "================================================"
echo "Checking Docker Status..."
echo "================================================"
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}ERROR: Docker is not running!${NC}"
    echo -e "${RED}Please start Docker Desktop and try again.${NC}"
    echo ""
    echo "To start Docker:"
    echo "  1. Open Docker Desktop application"
    echo "  2. Wait for Docker to start (whale icon in menu bar)"
    echo "  3. Run this script again"
    exit 1
fi
echo -e "${GREEN}Docker is running!${NC}"

# Check if required Docker containers are running
echo ""
echo "Checking required Docker services..."
REQUIRED_SERVICES=("postgres" "redis" "kafka" "zookeeper")
MISSING_SERVICES=()

for service in "${REQUIRED_SERVICES[@]}"; do
    if ! docker ps --format '{{.Names}}' | grep -q "$service"; then
        MISSING_SERVICES+=("$service")
    fi
done

if [ ${#MISSING_SERVICES[@]} -ne 0 ]; then
    echo -e "${YELLOW}Warning: The following Docker services are not running:${NC}"
    for service in "${MISSING_SERVICES[@]}"; do
        echo "  - $service"
    done
    echo ""
    echo "Starting Docker services..."
    cd "$PROJECT_DIR"
    docker compose up -d
    echo -e "${GREEN}Docker services started!${NC}"
    echo "Waiting 10 seconds for services to be ready..."
    sleep 10
else
    echo -e "${GREEN}All required Docker services are running!${NC}"
fi

# Function to start a service
start_service() {
    local service_name=$1
    local service_dir=$2
    local port=$3

    echo -e "${GREEN}Starting $service_name on port $port...${NC}"

    cd "$PROJECT_DIR/$service_dir"
    nohup mvn spring-boot:run > "$LOG_DIR/$service_name.log" 2>&1 &
    echo $! > "$LOG_DIR/$service_name.pid"

    echo "  PID: $(cat $LOG_DIR/$service_name.pid)"
    echo "  Logs: $LOG_DIR/$service_name.log"
}

# Function to wait for service to be ready
wait_for_service() {
    local service_name=$1
    local port=$2
    local max_attempts=30
    local attempt=1

    echo -e "${YELLOW}Waiting for $service_name to start on port $port...${NC}"

    while [ $attempt -le $max_attempts ]; do
        if lsof -ti:$port > /dev/null 2>&1; then
            echo -e "${GREEN}$service_name is ready!${NC}"
            return 0
        fi
        echo "  Attempt $attempt/$max_attempts..."
        sleep 2
        attempt=$((attempt + 1))
    done

    echo -e "${YELLOW}Warning: $service_name did not start within expected time${NC}"
    return 1
}

# Start services in order
echo ""
echo "================================================"
echo "Starting Auth Service (Port 8081)"
echo "================================================"
start_service "auth-service" "auth-service" 8081
wait_for_service "auth-service" 8081

echo ""
echo "================================================"
echo "Starting Account Service (Port 8082)"
echo "================================================"
start_service "account-service" "account-service" 8082
wait_for_service "account-service" 8082

echo ""
echo "================================================"
echo "Starting Transaction Service (Port 8083)"
echo "================================================"
start_service "transaction-service" "transaction-service" 8083
wait_for_service "transaction-service" 8083

echo ""
echo "================================================"
echo "Starting Notification Service (Port 8084)"
echo "================================================"
start_service "notification-service" "notification-service" 8084
wait_for_service "notification-service" 8084

echo ""
echo "================================================"
echo "Starting Fraud Service (Port 8085)"
echo "================================================"
start_service "fraud-service" "fraud-service" 8085
wait_for_service "fraud-service" 8085

echo ""
echo "================================================"
echo "Starting API Gateway (Port 8080)"
echo "================================================"
start_service "api-gateway" "api-gateway" 8080
wait_for_service "api-gateway" 8080

echo ""
echo -e "${GREEN}================================================${NC}"
echo -e "${GREEN}All services started successfully!${NC}"
echo -e "${GREEN}================================================${NC}"
echo ""
echo "Service URLs:"
echo "  - API Gateway:    http://localhost:8080"
echo "  - Swagger UI:     http://localhost:8080/swagger-ui.html"
echo "  - Auth Service:   http://localhost:8081"
echo "  - Account Service: http://localhost:8082"
echo "  - Transaction Service: http://localhost:8083"
echo "  - Notification Service: http://localhost:8084"
echo "  - Fraud Service:  http://localhost:8085"
echo ""
echo "Logs are available in: $LOG_DIR"
echo ""
echo "To stop all services, run: ./stop-services.sh"
