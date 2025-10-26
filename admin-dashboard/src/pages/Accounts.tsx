import React, { useEffect, useState } from 'react';
import { accountService, type Account, type CreateAccountRequest } from '../services/accountService';
import { Plus, Search, CheckCircle, XCircle, Eye } from 'lucide-react';

const Accounts: React.FC = () => {
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [selectedAccount, setSelectedAccount] = useState<Account | null>(null);

  useEffect(() => {
    fetchAccounts();
  }, []);

  const fetchAccounts = async () => {
    try {
      const data = await accountService.getAllAccounts();
      setAccounts(data);
    } catch (error) {
      console.error('Failed to fetch accounts:', error);
    } finally {
      setLoading(false);
    }
  };

  const filteredAccounts = accounts.filter(account =>
    account.accountHolderName.toLowerCase().includes(searchTerm.toLowerCase()) ||
    account.accountId.toLowerCase().includes(searchTerm.toLowerCase())
  );

  if (loading) {
    return <div className="loading">Loading accounts...</div>;
  }

  return (
    <div className="page-container">
      <div className="page-header">
        <div className="search-box">
          <Search size={20} />
          <input
            type="text"
            placeholder="Search accounts..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <button className="btn-primary" onClick={() => setShowCreateModal(true)}>
          <Plus size={20} />
          Create Account
        </button>
      </div>

      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Account ID</th>
              <th>Holder Name</th>
              <th>Balance</th>
              <th>Currency</th>
              <th>Type</th>
              <th>KYC</th>
              <th>Created</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredAccounts.map((account) => (
              <tr key={account.accountId}>
                <td><code>{account.accountId}</code></td>
                <td>{account.accountHolderName}</td>
                <td className="amount">${account.balance.toFixed(2)}</td>
                <td>{account.currency}</td>
                <td><span className="badge">{account.accountType}</span></td>
                <td>
                  {account.kycVerified ? (
                    <CheckCircle size={20} color="#10b981" />
                  ) : (
                    <XCircle size={20} color="#ef4444" />
                  )}
                </td>
                <td>{new Date(account.createdAt).toLocaleDateString()}</td>
                <td>
                  <button
                    className="btn-icon"
                    onClick={() => setSelectedAccount(account)}
                    title="View Details"
                  >
                    <Eye size={18} />
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {showCreateModal && (
        <CreateAccountModal
          onClose={() => setShowCreateModal(false)}
          onSuccess={() => {
            setShowCreateModal(false);
            fetchAccounts();
          }}
        />
      )}

      {selectedAccount && (
        <AccountDetailsModal
          account={selectedAccount}
          onClose={() => setSelectedAccount(null)}
        />
      )}
    </div>
  );
};

const CreateAccountModal: React.FC<{
  onClose: () => void;
  onSuccess: () => void;
}> = ({ onClose, onSuccess }) => {
  const [formData, setFormData] = useState<CreateAccountRequest>({
    accountHolderName: '',
    initialBalance: 0,
    currency: 'USD',
    accountType: 'SAVINGS',
    kycVerified: false,
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLoading(true);
    setError('');

    try {
      await accountService.createAccount(formData);
      onSuccess();
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to create account');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>Create New Account</h2>
        {error && <div className="error-message">{error}</div>}

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label>Account Holder Name</label>
            <input
              type="text"
              value={formData.accountHolderName}
              onChange={(e) =>
                setFormData({ ...formData, accountHolderName: e.target.value })
              }
              required
            />
          </div>

          <div className="form-group">
            <label>Initial Balance</label>
            <input
              type="number"
              step="0.01"
              value={formData.initialBalance}
              onChange={(e) =>
                setFormData({ ...formData, initialBalance: parseFloat(e.target.value) })
              }
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
            <label>Account Type</label>
            <select
              value={formData.accountType}
              onChange={(e) => setFormData({ ...formData, accountType: e.target.value })}
            >
              <option value="SAVINGS">Savings</option>
              <option value="CHECKING">Checking</option>
              <option value="BUSINESS">Business</option>
            </select>
          </div>

          <div className="form-group-checkbox">
            <input
              type="checkbox"
              id="kycVerified"
              checked={formData.kycVerified}
              onChange={(e) =>
                setFormData({ ...formData, kycVerified: e.target.checked })
              }
            />
            <label htmlFor="kycVerified">KYC Verified</label>
          </div>

          <div className="modal-actions">
            <button type="button" onClick={onClose} className="btn-secondary">
              Cancel
            </button>
            <button type="submit" disabled={loading} className="btn-primary">
              {loading ? 'Creating...' : 'Create Account'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

const AccountDetailsModal: React.FC<{
  account: Account;
  onClose: () => void;
}> = ({ account, onClose }) => {
  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={(e) => e.stopPropagation()}>
        <h2>Account Details</h2>

        <div className="detail-grid">
          <div className="detail-item">
            <label>Account ID</label>
            <code>{account.accountId}</code>
          </div>
          <div className="detail-item">
            <label>Holder Name</label>
            <div>{account.accountHolderName}</div>
          </div>
          <div className="detail-item">
            <label>Balance</label>
            <div className="amount">${account.balance.toFixed(2)}</div>
          </div>
          <div className="detail-item">
            <label>Currency</label>
            <div>{account.currency}</div>
          </div>
          <div className="detail-item">
            <label>Account Type</label>
            <span className="badge">{account.accountType}</span>
          </div>
          <div className="detail-item">
            <label>KYC Status</label>
            <div>
              {account.kycVerified ? (
                <span style={{ color: '#10b981' }}>Verified</span>
              ) : (
                <span style={{ color: '#ef4444' }}>Not Verified</span>
              )}
            </div>
          </div>
          <div className="detail-item">
            <label>Created At</label>
            <div>{new Date(account.createdAt).toLocaleString()}</div>
          </div>
          <div className="detail-item">
            <label>Updated At</label>
            <div>{new Date(account.updatedAt).toLocaleString()}</div>
          </div>
        </div>

        <div className="modal-actions">
          <button onClick={onClose} className="btn-secondary">Close</button>
        </div>
      </div>
    </div>
  );
};

export default Accounts;
