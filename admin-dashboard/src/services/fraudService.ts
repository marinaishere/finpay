import api from './api';

// Match actual backend response
export interface FraudCheck {
  transactionId: string;
  fraudulent: boolean;
  reason?: string;
}

export interface FraudCheckRequest {
  transactionId: string;
  amount: number;
}

export const fraudService = {
  // Check if a transaction is fraudulent
  checkFraud: async (data: FraudCheckRequest): Promise<FraudCheck> => {
    const response = await api.post('/frauds/check', data);
    return response.data;
  },

  // Get fraud check status for a transaction
  getTransactionFraudCheck: async (transactionId: string): Promise<FraudCheck> => {
    const response = await api.get(`/frauds/transactions/${transactionId}`);
    return response.data;
  },
};
