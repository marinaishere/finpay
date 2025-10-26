# FinPay Backend API Structure

## Overview
The FinPay backend has a **simplified API structure** compared to typical REST APIs. The frontend has been updated to match the actual backend implementation.

## Available Endpoints

### Account Service (Port 8082)

**GET /accounts/me**
- Returns current user's info from JWT token
- Response: `{ user_id, email, roles }`

**GET /accounts/{id}**
- Get account by UUID
- Response: `{ id, ownerEmail, balance }`

**POST /accounts**
- Create new account
- Body: `{ ownerEmail, initialBalance }`
- Response: `{ id, ownerEmail, balance }`

**POST /accounts/debit**
- Withdraw from account
- Body: `{ accountId, amount }`

**POST /accounts/credit**
- Deposit to account
- Body: `{ accountId, amount }`

### Transaction Service (Port 8083)

**GET /transactions/me**
- Returns current user's info from JWT token
- Response: `{ user_id, email, roles }`

**GET /transactions/{id}**
- Get transaction by UUID
- Response: `{ id, fromAccountId, toAccountId, amount, status }`

**POST /transactions/transfer** ⚠️ Requires `Idempotency-Key` header
- Transfer money between accounts
- Body: `{ fromAccountId, toAccountId, amount }`
- Returns 202 Accepted

### Fraud Service (Port 8085)

**POST /frauds/check**
- Check if transaction is fraudulent
- Body: `{ transactionId, amount }`
- Response: `{ transactionId, fraudulent, reason }`

**GET /frauds/transactions/{transactionId}**
- Get fraud status for transaction
- Response: `{ transactionId, fraudulent, reason }`

## Key Differences from Initial Assumptions

### What's NOT Available:
❌ `GET /accounts` - No endpoint to list all accounts
❌ `GET /transactions` - No endpoint to list all transactions
❌ `GET /frauds` - No endpoint to list all fraud checks
❌ Complex account fields (accountHolderName, currency, accountType, kycVerified)

### What the Database Actually Has:
The accounts table only has:
- `id` (UUID)
- `owner_email` (string)
- `balance` (decimal)

## Frontend Updates Made

1. **Service Layer** - Updated all service files to match actual API endpoints
2. **Dashboard** - Now shows user info from `/accounts/me` endpoint
3. **Accounts Page** - Needs to be rewritten (currently broken)
4. **Transactions Page** - Needs to be rewritten (currently broken)
5. **Fraud Page** - Needs to be rewritten (currently broken)

## Current Status

✅ **Working:**
- Login/Register
- Dashboard (shows user info)
- API authentication with JWT

⚠️ **Partially Working:**
- Account creation (requires ownerEmail field)

❌ **Not Working Yet:**
- Listing accounts (no API endpoint)
- Listing transactions (no API endpoint)
- Listing fraud checks (no API endpoint)

## Recommendations

### Option 1: Use What's Available
Focus on:
- Creating accounts for the logged-in user
- Making transfers between known account IDs
- Checking fraud for specific transactions

### Option 2: Store Account IDs Locally
- Store created account IDs in localStorage
- Display only user's own accounts
- Manual entry of account IDs for transfers

### Option 3: Add Backend Endpoints
Add these endpoints to the backend:
- `GET /accounts` - List all accounts
- `GET /transactions` - List all transactions
- Enhanced account model with more fields

## Testing the Dashboard

1. Login at: http://localhost:5174/
2. Dashboard will show your user ID, email, and role
3. The existing accounts in the database won't show up (no list endpoint)
4. You can create accounts via "Accounts" page (but UI needs update)

## Next Steps

The dashboard LOGIN is now fully working! The existing database accounts don't show up because there's no backend endpoint to list them. You have two options:

1. **Use the simplified dashboard** - Focus on single-account operations
2. **Add backend endpoints** - Implement GET /accounts and GET /transactions in the backend

