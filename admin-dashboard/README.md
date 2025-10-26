# FinPay Admin Dashboard

A modern, responsive React-based admin dashboard for managing the FinPay microservices platform. Built with TypeScript, React Router, and Axios for seamless integration with the FinPay backend APIs.

## Features

- **Authentication**: Secure login and registration with JWT token management
- **Dashboard**: Real-time overview of accounts, transactions, and fraud checks
- **Account Management**: Create, view, and manage customer accounts
- **Transaction Monitoring**: Track and create money transfers between accounts
- **Fraud Detection**: Monitor fraud checks with risk scoring and status tracking
- **Responsive Design**: Mobile-friendly interface that works on all devices

## Tech Stack

- **React 18** with TypeScript
- **Vite** for fast development and building
- **React Router** for navigation
- **Axios** for API communication
- **Lucide React** for icons
- **Modern CSS** with CSS Variables for theming

## Prerequisites

Before running the admin dashboard, ensure you have:

1. **Node.js** (v18 or higher) and npm installed
2. **FinPay Backend Services** running:
   - API Gateway (port 8080)
   - Auth Service (port 8081)
   - Account Service (port 8082)
   - Transaction Service (port 8083)
   - Notification Service (port 8084)
   - Fraud Service (port 8085)
3. **Infrastructure** (PostgreSQL, Kafka, Redis, etc.) running via Docker Compose

## Getting Started

### 1. Install Dependencies

```bash
cd admin-dashboard
npm install
```

### 2. Configure API Endpoint (Optional)

The dashboard connects to `http://localhost:8080` by default (API Gateway). To change this, edit `src/services/api.ts`:

```typescript
const API_BASE_URL = 'http://your-api-gateway-url';
```

### 3. Start the Development Server

```bash
npm run dev
```

The dashboard will be available at `http://localhost:5173`

### 4. Build for Production

```bash
npm run build
```

The production-ready files will be in the `dist` folder.

## Starting the Complete FinPay System

To run the entire FinPay platform with the admin dashboard:

### 1. Start Infrastructure Services

```bash
cd /path/to/finpay
docker-compose up -d
```

This starts PostgreSQL, Kafka, Redis, Prometheus, Grafana, Elasticsearch, Kibana, and Zipkin.

### 2. Build Backend Services

```bash
cd /path/to/finpay
mvn clean install
```

### 3. Start Each Microservice

Open separate terminal windows for each service:

```bash
# Terminal 1: API Gateway
cd api-gateway && mvn spring-boot:run

# Terminal 2: Auth Service
cd auth-service && mvn spring-boot:run

# Terminal 3: Account Service
cd account-service && mvn spring-boot:run

# Terminal 4: Transaction Service
cd transaction-service && mvn spring-boot:run

# Terminal 5: Notification Service
cd notification-service && mvn spring-boot:run

# Terminal 6: Fraud Service
cd fraud-service && mvn spring-boot:run
```

### 4. Start the Admin Dashboard

```bash
# Terminal 7: Admin Dashboard
cd admin-dashboard && npm run dev
```

## Usage Guide

### First Time Setup

1. Navigate to `http://localhost:5173/register`
2. Create a new account with:
   - Username
   - Email
   - Password
3. You'll be automatically logged in and redirected to the dashboard

### Managing Accounts

1. Click **Accounts** in the sidebar
2. Click **Create Account** to add a new account
3. Fill in account holder details:
   - Account Holder Name
   - Initial Balance
   - Currency (USD, EUR, GBP)
   - Account Type (Savings, Checking, Business)
   - KYC Verification status
4. View account details by clicking the eye icon

### Creating Transactions

1. Click **Transactions** in the sidebar
2. Click **New Transfer** to create a transfer
3. Select:
   - From Account
   - To Account
   - Amount
   - Currency
   - Optional reference
4. The transaction will be processed and appear in the list

### Monitoring Fraud Detection

1. Click **Fraud Detection** in the sidebar
2. View fraud checks with:
   - Risk scores (Low/Medium/High)
   - Status (Clean/Review/Denied)
   - Associated transaction IDs
3. Filter by status to focus on specific checks

## API Integration

The dashboard integrates with the following FinPay APIs:

### Auth Service (`/auth-services`)
- `POST /users` - Register new user
- `POST /login` - User login
- `GET /users` - List users

### Account Service (`/accounts`)
- `GET /` - List all accounts
- `POST /` - Create account
- `GET /{accountId}` - Get account details
- `GET /{accountId}/balance` - Get account balance

### Transaction Service (`/transactions`)
- `GET /` - List all transactions
- `POST /transfer` - Create transfer
- `GET /{transactionId}` - Get transaction details
- `GET /account/{accountId}` - Get account transactions

### Fraud Service (`/frauds`)
- `GET /` - List all fraud checks
- `GET /{checkId}` - Get fraud check details
- `GET /transaction/{transactionId}` - Get transaction fraud check

## Project Structure

```
admin-dashboard/
├── src/
│   ├── components/           # Reusable UI components
│   │   ├── Layout.tsx       # Main layout with sidebar
│   │   └── ProtectedRoute.tsx
│   ├── context/             # React Context providers
│   │   └── AuthContext.tsx  # Authentication state
│   ├── pages/               # Page components
│   │   ├── Login.tsx
│   │   ├── Register.tsx
│   │   ├── Dashboard.tsx
│   │   ├── Accounts.tsx
│   │   ├── Transactions.tsx
│   │   └── Fraud.tsx
│   ├── services/            # API service layer
│   │   ├── api.ts           # Axios configuration
│   │   ├── authService.ts
│   │   ├── accountService.ts
│   │   ├── transactionService.ts
│   │   └── fraudService.ts
│   ├── App.tsx              # Main app component
│   ├── App.css              # Global styles
│   └── main.tsx             # Entry point
├── package.json
└── vite.config.ts
```

## Key Features Explained

### Authentication Flow

1. User logs in via `/login` page
2. JWT token is stored in localStorage
3. Token is automatically included in all API requests
4. Expired tokens redirect to login page
5. Protected routes require authentication

### State Management

- **AuthContext**: Manages user authentication state
- **Local State**: Component-level state for forms and UI
- **No External State Library**: Keeps the app lightweight

### Error Handling

- API errors display user-friendly messages
- 401 responses automatically log out users
- Form validation prevents invalid submissions

## Customization

### Changing Colors

Edit CSS variables in `src/App.css`:

```css
:root {
  --primary-color: #3b82f6;  /* Change primary color */
  --success-color: #10b981;  /* Change success color */
  --error-color: #ef4444;    /* Change error color */
}
```

### Adding New Pages

1. Create component in `src/pages/`
2. Add route in `src/App.tsx`
3. Add navigation item in `src/components/Layout.tsx`

## Troubleshooting

### CORS Errors

If you see CORS errors, ensure the API Gateway has CORS configured:

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins: "http://localhost:5173"
            allowedMethods: "*"
            allowedHeaders: "*"
```

### Connection Refused

- Verify all backend services are running
- Check that API Gateway is accessible at `http://localhost:8080`
- Test endpoints with: `curl http://localhost:8080/accounts`

### Authentication Issues

- Clear localStorage: `localStorage.clear()`
- Ensure auth-service is running
- Check JWT keys are properly configured

## Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm run lint` - Run ESLint

## Browser Support

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

## Contributing

This dashboard is part of the FinPay microservices project. To contribute:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is part of the FinPay demonstration platform.

## Support

For issues or questions:
- Check the main FinPay README
- Review API documentation at `http://localhost:8080/swagger-ui.html`
- Examine browser console for error messages

---

**Built with React + TypeScript + Vite**
