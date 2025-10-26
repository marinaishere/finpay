import api from './api';

// Match actual backend response
export interface Account {
  id: string;
  ownerEmail: string;
  balance: number;
}

export interface CreateAccountRequest {
  ownerEmail: string;
  initialBalance: number;
}

export interface DebitCreditRequest {
  accountId: string;
  amount: number;
}

export const accountService = {
  // Get current user's account info (requires JWT)
  getMyAccount: async (): Promise<any> => {
    const response = await api.get('/accounts/me');
    return response.data;
  },

  // Get account by ID
  getAccountById: async (accountId: string): Promise<Account> => {
    const response = await api.get(`/accounts/${accountId}`);
    return response.data;
  },

  // Create a new account
  createAccount: async (data: CreateAccountRequest): Promise<Account> => {
    const response = await api.post('/accounts', data);
    return response.data;
  },

  // Debit (withdraw) from account
  debit: async (data: DebitCreditRequest): Promise<Account> => {
    const response = await api.post('/accounts/debit', data);
    return response.data;
  },

  // Credit (deposit) to account
  credit: async (data: DebitCreditRequest): Promise<Account> => {
    const response = await api.post('/accounts/credit', data);
    return response.data;
  },
};
