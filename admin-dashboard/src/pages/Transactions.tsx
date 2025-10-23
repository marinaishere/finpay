import React, { useEffect, useState } from 'react';
import { transactionService, type Transaction, type CreateTransferRequest } from '../services/transactionService';
import { accountService } from '../services/accountService';
import { Plus, Search, ArrowRight } from 'lucide-react';

const Transactions: React.FC = () => {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);

  useEffect(() => {
    fetchTransactions();
  }, []);

  const fetchTransactions = async () => {
    try {
      const data = await transactionService.getAllTransactions();
      setTransactions(data.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      ));
    } catch (error) {
      console.error('Failed to fetch transactions:', error);
    } finally {
      setLoading(false);
    }
  };

  const filteredTransactions = transactions.filter(tx =>
    tx.transactionId.toLowerCase().includes(searchTerm.toLowerCase()) ||
    tx.fromAccount.toLowerCase().includes(searchTerm.toLowerCase()) ||
    tx.toAccount.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'COMPLETED': return '#10b981';
      case 'PENDING': return '#f59e0b';
      case 'REVERSED': return '#6b7280';
      case 'FLAGGED': return '#ef4444';
      default: return '#6b7280';
    }
  };

  if (loading) {
    return <div className="loading">Loading transactions...</div>;
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="search-box">
          <Search size={20} />
          <input
            type="text"
            placeholder="Search transactions..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <button className="btn-primary" onClick={() => setShowCreateModal(true)}>
          <Plus size={20} />
          New Transfer
        </button>
      </div>

      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Transaction ID</th>
              <th>From</th>
              <th></th>
              <th>To</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Type</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {filteredTransactions.map((tx) => (
              <tr key={tx.transactionId}>
                <td><code>{tx.transactionId}</code></td>
                <td><code className="account-id">{tx.fromAccount}</code></td>
                <td><ArrowRight size={16} color="#6b7280" /></td>
                <td><code className="account-id">{tx.toAccount}</code></td>
                <td className="amount">
                  ${tx.amount.toFixed(2)} {tx.currency}
                </td>
                <td>
                  <span
                    className="status-badge"
                    style={{ backgroundColor: `${getStatusColor(tx.status)}20`, color: getStatusColor(tx.status) }}
                  >
                    {tx.status}
                  </span>
                </td>
                <td><span className="badge">{tx.transactionType}</span></td>
                <td>{new Date(tx.createdAt).toLocaleString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreateModal && (
        <CreateTransferModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            fetchTransactions();
          }}
        />
      )}
    </div>
  );
};

const CreateTransferModal: React.FC<{
  onClose: () => void;
  onSuccess: () => void;
}> = ({ onClose, onSuccess }) => {
  const [formData, setFormData] = useState<CreateTransferRequest>({
    fromAccount: '',
    toAccount: '',
    amount: 0,
    currency: 'USD',
    reference: '',
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [accounts, setAccounts] = useState<any[]>([]);

  useEffect(() => {
    const fetchAccounts = async () => {
      try {
        const data = await accountService.getAllAccounts();
        setAccounts(data);
      } catch (err) {
        console.error('Failed to fetch accounts:', err);
      }
    };
    fetchAccounts();
  }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    if (formData.fromAccount === formData.toAccount) {
      setError('From and To accounts must be different');
      setLoading(false);
      return;
    }

    try {
      await transactionService.createTransfer(formData);
      onSuccess();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create transfer');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>Create New Transfer</h2>
        {error && <div className="error-message">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>From Account</label>
            <select
              value={formData.fromAccount}
              onChange={(e) =>
                setFormData({ ...formData, fromAccount: e.target.value })
              }
              required
            >
              <option value="">Select account</option>
              {accounts.map((acc) => (
                <option key={acc.accountId} value={acc.accountId}>
                  {acc.accountHolderName} ({acc.accountId}) - ${acc.balance.toFixed(2)}
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label>To Account</label>
            <select
              value={formData.toAccount}
              onChange={(e) =>
                setFormData({ ...formData, toAccount: e.target.value })
              }
              required
            >
              <option value="">Select account</option>
              {accounts.map((acc) => (
                <option key={acc.accountId} value={acc.accountId}>
                  {acc.accountHolderName} ({acc.accountId})
                </option>
              ))}
            </select>
          </div>

          <div className="form-group">
            <label>Amount</label>
            <input
              type="number"
              step="0.01"
              min="0.01"
              value={formData.amount}
              onChange={(e) =>
                setFormData({ ...formData, amount: parseFloat(e.target.value) })
              }
              required
            />
          </div>

          <div className="form-group">
            <label>Currency</label>
            <select
              value={formData.currency}
              onChange={(e) => setFormData({ ...formData, currency: e.target.value })}
            >
              <option value="USD">USD</option>
              <option value="EUR">EUR</option>
              <option value="GBP">GBP</option>
            </select>
          </div>

          <div className="form-group">
            <label>Reference (Optional)</label>
            <input
              type="text"
              value={formData.reference}
              onChange={(e) =>
                setFormData({ ...formData, reference: e.target.value })
              }
              placeholder="Payment reference"
            />
          </div>

          <div className="modal-actions">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancel
            </button>
            <button type="submit" disabled={loading} className="btn-primary">
              {loading ? 'Processing...' : 'Create Transfer'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default Transactions;
