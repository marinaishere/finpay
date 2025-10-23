import React, { useEffect, useState } from 'react';
import { CreditCard, User, Mail, DollarSign, ArrowUpRight, ArrowDownRight, Search } from 'lucide-react';
import { accountService, type Account } from '../services/accountService';
import { transactionService, type Transaction } from '../services/transactionService';
import { useAuth } from '../context/AuthContext';

const Dashboard: React.FC = () => {
  const [accountInfo, setAccountInfo] = useState<any>(null);
  const [account, setAccount] = useState<Account | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [accountId, setAccountId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const { username } = useAuth();

  useEffect(() => {
    const fetchAccountInfo = async () => {
      try {
        const data = await accountService.getMyAccount();
        setAccountInfo(data);
      } catch (err: any) {
        console.error('Failed to fetch account info:', err);
        setError('Unable to load account information');
      } finally {
        setLoading(false);
      }
    };

    fetchAccountInfo();
  }, []);

  const handleLoadAccount = async () => {
    if (!accountId.trim()) {
      setError('Please enter an account ID');
      return;
    }

    setLoading(true);
    setError('');

    try {
      // Fetch account details
      const accountData = await accountService.getAccountById(accountId);
      setAccount(accountData);

      // Fetch transaction history
      const txData = await transactionService.getTransactionsByAccountId(accountId);
      setTransactions(txData);
    } catch (err: any) {
      console.error('Failed to load account:', err);
      setError(err.response?.data?.message || 'Failed to load account details');
      setAccount(null);
      setTransactions([]);
    } finally {
      setLoading(false);
    }
  };

  if (loading && !accountInfo) {
    return <div className="loading">Loading dashboard...</div>;
  }

  return (
    <div className="dashboard">
      <div className="welcome-section">
        <h2>Welcome back, {username}!</h2>
        <p>Manage your FinPay account and transactions</p>
      </div>

      {error && (
        <div className="error-message">{error}</div>
      )}

      {accountInfo && (
        <div className="stats-grid">
          <div className="stat-card" style={{ borderLeftColor: '#3b82f6' }}>
            <div className="stat-icon" style={{ backgroundColor: '#3b82f620', color: '#3b82f6' }}>
              <User size={24} />
            </div>
            <div className="stat-content">
              <div className="stat-title">User ID</div>
              <div className="stat-value" style={{ fontSize: '18px' }}>{accountInfo.user_id || 'N/A'}</div>
            </div>
          </div>

          <div className="stat-card" style={{ borderLeftColor: '#10b981' }}>
            <div className="stat-icon" style={{ backgroundColor: '#10b98120', color: '#10b981' }}>
              <Mail size={24} />
            </div>
            <div className="stat-content">
              <div className="stat-title">Email</div>
              <div className="stat-value" style={{ fontSize: '18px' }}>{accountInfo.email || 'N/A'}</div>
            </div>
          </div>

          <div className="stat-card" style={{ borderLeftColor: '#8b5cf6' }}>
            <div className="stat-icon" style={{ backgroundColor: '#8b5cf620', color: '#8b5cf6' }}>
              <CreditCard size={24} />
            </div>
            <div className="stat-content">
              <div className="stat-title">Role</div>
              <div className="stat-value" style={{ fontSize: '18px' }}>{accountInfo.roles || 'USER'}</div>
            </div>
          </div>
        </div>
      )}

      <div className="card" style={{ marginTop: '30px' }}>
        <h3>View Account & Transaction History</h3>
        <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
          <input
            type="text"
            placeholder="Enter Account ID (UUID)"
            value={accountId}
            onChange={(e) => setAccountId(e.target.value)}
            style={{ flex: 1 }}
          />
          <button
            onClick={handleLoadAccount}
            disabled={loading}
            style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
          >
            <Search size={18} />
            Load Account
          </button>
        </div>
      </div>

      {account && (
        <div className="card" style={{ marginTop: '20px' }}>
          <h3>Account Details</h3>
          <div className="stats-grid" style={{ marginTop: '20px' }}>
            <div className="stat-card" style={{ borderLeftColor: '#f59e0b' }}>
              <div className="stat-icon" style={{ backgroundColor: '#f59e0b20', color: '#f59e0b' }}>
                <CreditCard size={24} />
              </div>
              <div className="stat-content">
                <div className="stat-title">Account ID</div>
                <div className="stat-value" style={{ fontSize: '14px', wordBreak: 'break-all' }}>{account.id}</div>
              </div>
            </div>

            <div className="stat-card" style={{ borderLeftColor: '#10b981' }}>
              <div className="stat-icon" style={{ backgroundColor: '#10b98120', color: '#10b981' }}>
                <DollarSign size={24} />
              </div>
              <div className="stat-content">
                <div className="stat-title">Balance</div>
                <div className="stat-value">${account.balance.toFixed(2)}</div>
              </div>
            </div>

            <div className="stat-card" style={{ borderLeftColor: '#3b82f6' }}>
              <div className="stat-icon" style={{ backgroundColor: '#3b82f620', color: '#3b82f6' }}>
                <Mail size={24} />
              </div>
              <div className="stat-content">
                <div className="stat-title">Owner</div>
                <div className="stat-value" style={{ fontSize: '16px' }}>{account.ownerEmail}</div>
              </div>
            </div>
          </div>
        </div>
      )}

      {transactions.length > 0 && (
        <div className="card" style={{ marginTop: '20px' }}>
          <h3>Transaction History ({transactions.length})</h3>
          <div style={{ marginTop: '20px', overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid #e5e7eb', textAlign: 'left' }}>
                  <th style={{ padding: '12px', fontWeight: 600 }}>Type</th>
                  <th style={{ padding: '12px', fontWeight: 600 }}>From</th>
                  <th style={{ padding: '12px', fontWeight: 600 }}>To</th>
                  <th style={{ padding: '12px', fontWeight: 600 }}>Amount</th>
                  <th style={{ padding: '12px', fontWeight: 600 }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {transactions.map((tx) => {
                  const isOutgoing = tx.fromAccountId === accountId;
                  return (
                    <tr key={tx.id} style={{ borderBottom: '1px solid #e5e7eb' }}>
                      <td style={{ padding: '12px' }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                          {isOutgoing ? (
                            <ArrowUpRight size={18} color="#ef4444" />
                          ) : (
                            <ArrowDownRight size={18} color="#10b981" />
                          )}
                          <span>{isOutgoing ? 'Sent' : 'Received'}</span>
                        </div>
                      </td>
                      <td style={{ padding: '12px', fontSize: '13px', fontFamily: 'monospace' }}>
                        {tx.fromAccountId.substring(0, 8)}...
                      </td>
                      <td style={{ padding: '12px', fontSize: '13px', fontFamily: 'monospace' }}>
                        {tx.toAccountId.substring(0, 8)}...
                      </td>
                      <td style={{ padding: '12px', fontWeight: 600, color: isOutgoing ? '#ef4444' : '#10b981' }}>
                        {isOutgoing ? '-' : '+'}${tx.amount.toFixed(2)}
                      </td>
                      <td style={{ padding: '12px' }}>
                        <span style={{
                          padding: '4px 12px',
                          borderRadius: '12px',
                          fontSize: '12px',
                          fontWeight: 600,
                          backgroundColor: tx.status === 'COMPLETED' ? '#10b98120' : '#f59e0b20',
                          color: tx.status === 'COMPLETED' ? '#10b981' : '#f59e0b'
                        }}>
                          {tx.status}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {account && transactions.length === 0 && (
        <div className="card" style={{ marginTop: '20px', textAlign: 'center', padding: '40px' }}>
          <p style={{ color: '#6b7280' }}>No transactions found for this account</p>
        </div>
      )}
    </div>
  );
};

export default Dashboard;
