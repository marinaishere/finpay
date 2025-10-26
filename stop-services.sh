#!/bin/bash

# FinPay Microservices Stop Script
# This script stops all running services

PROJECT_DIR="/Users/mengruwang/Github/finpay"
LOG_DIR="$PROJECT_DIR/logs"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${RED}Stopping FinPay Microservices...${NC}"

# Function to stop a service
stop_service() {
    local service_name=$1
    local pid_file="$LOG_DIR/$service_name.pid"

    if [ -f "$pid_file" ]; then
        local pid=$(cat "$pid_file")
        if ps -p $pid > /dev/null 2>&1; then
            echo -e "Stopping $service_name (PID: $pid)..."
            kill $pid 2>/dev/null || kill -9 $pid 2>/dev/null
            rm "$pid_file"
            echo -e "${GREEN}  $service_name stopped${NC}"
        else
            echo -e "${RED}  $service_name is not running${NC}"
            rm "$pid_file"
        fi
    else
        echo -e "${RED}  No PID file found for $service_name${NC}"
    fi
}

# Stop services in reverse order
stop_service "api-gateway"
stop_service "fraud-service"
stop_service "notification-service"
stop_service "transaction-service"
stop_service "account-service"
stop_service "auth-service"

# Also kill any remaining Java processes on the ports
echo ""
echo "Cleaning up any remaining processes on ports 8080-8085..."
lsof -ti:8080,8081,8082,8083,8084,8085 | xargs kill -9 2>/dev/null || true

echo ""
echo -e "${GREEN}All services stopped!${NC}"
