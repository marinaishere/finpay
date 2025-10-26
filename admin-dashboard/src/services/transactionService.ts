import api from './api';

// Match actual backend response
export interface Transaction {
  id: string;
  fromAccountId: string;
  toAccountId: string;
  amount: number;
  status: string;
}

export interface CreateTransferRequest {
  fromAccountId: string;
  toAccountId: string;
  amount: number;
}

export const transactionService = {
  // Get current user's transaction info (requires JWT)
  getMyTransactions: async (): Promise<any> => {
    const response = await api.get('/transactions/me');
    return response.data;
  },

  // Get all transactions for a specific account
  getTransactionsByAccountId: async (accountId: string): Promise<Transaction[]> => {
    const response = await api.get(`/transactions/account/${accountId}`);
    return response.data;
  },

  // Get transaction by ID
  getTransactionById: async (transactionId: string): Promise<Transaction> => {
    const response = await api.get(`/transactions/${transactionId}`);
    return response.data;
  },

  // Create a transfer (requires Idempotency-Key header)
  createTransfer: async (data: CreateTransferRequest): Promise<Transaction> => {
    // Generate a unique idempotency key for this transfer
    const idempotencyKey = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;

    const response = await api.post('/transactions/transfer', data, {
      headers: {
        'Idempotency-Key': idempotencyKey
      }
    });
    return response.data;
  },
};
