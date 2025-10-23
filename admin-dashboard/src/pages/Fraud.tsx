import React, { useEffect, useState } from 'react';
import { fraudService, type FraudCheck } from '../services/fraudService';
import { Search, AlertTriangle, CheckCircle, XCircle } from 'lucide-react';

const Fraud: React.FC = () => {
  const [fraudChecks, setFraudChecks] = useState<FraudCheck[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchTerm, setSearchTerm] = useState('');
  const [filterStatus, setFilterStatus] = useState<string>('ALL');

  useEffect(() => {
    fetchFraudChecks();
  }, []);

  const fetchFraudChecks = async () => {
    try {
      const data = await fraudService.getAllFraudChecks();
      setFraudChecks(data.sort((a, b) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
      ));
    } catch (error) {
      console.error('Failed to fetch fraud checks:', error);
    } finally {
      setLoading(false);
    }
  };

  const filteredChecks = fraudChecks.filter(check => {
    const matchesSearch = check.checkId.toLowerCase().includes(searchTerm.toLowerCase()) ||
      check.transactionId.toLowerCase().includes(searchTerm.toLowerCase());

    const matchesFilter = filterStatus === 'ALL' || check.status === filterStatus;

    return matchesSearch && matchesFilter;
  });

  const getStatusIcon = (status: string) => {
    switch (status) {
      case 'CLEAN':
        return <CheckCircle size={20} color="#10b981" />;
      case 'REVIEW':
        return <AlertTriangle size={20} color="#f59e0b" />;
      case 'DENIED':
        return <XCircle size={20} color="#ef4444" />;
      default:
        return null;
    }
  };

  const getStatusColor = (status: string) => {
    switch (status) {
      case 'CLEAN': return '#10b981';
      case 'REVIEW': return '#f59e0b';
      case 'DENIED': return '#ef4444';
      default: return '#6b7280';
    }
  };

  const getRiskLevel = (score: number) => {
    if (score < 30) return { label: 'Low', color: '#10b981' };
    if (score < 70) return { label: 'Medium', color: '#f59e0b' };
    return { label: 'High', color: '#ef4444' };
  };

  const stats = {
    total: fraudChecks.length,
    clean: fraudChecks.filter(c => c.status === 'CLEAN').length,
    review: fraudChecks.filter(c => c.status === 'REVIEW').length,
    denied: fraudChecks.filter(c => c.status === 'DENIED').length,
  };

  if (loading) {
    return <div className="loading">Loading fraud checks...</div>;
  }

  return (
    <div className="page-container">
      <div className="fraud-stats">
        <div className="stat-card-small" style={{ borderLeftColor: '#6b7280' }}>
          <div className="stat-label">Total Checks</div>
          <div className="stat-value-large">{stats.total}</div>
        </div>
        <div className="stat-card-small" style={{ borderLeftColor: '#10b981' }}>
          <div className="stat-label">Clean</div>
          <div className="stat-value-large">{stats.clean}</div>
        </div>
        <div className="stat-card-small" style={{ borderLeftColor: '#f59e0b' }}>
          <div className="stat-label">Under Review</div>
          <div className="stat-value-large">{stats.review}</div>
        </div>
        <div className="stat-card-small" style={{ borderLeftColor: '#ef4444' }}>
          <div className="stat-label">Denied</div>
          <div className="stat-value-large">{stats.denied}</div>
        </div>
      </div>

      <div className="page-header">
        <div className="search-box">
          <Search size={20} />
          <input
            type="text"
            placeholder="Search fraud checks..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </div>
        <div className="filter-group">
          <label>Status:</label>
          <select value={filterStatus} onChange={(e) => setFilterStatus(e.target.value)}>
            <option value="ALL">All</option>
            <option value="CLEAN">Clean</option>
            <option value="REVIEW">Review</option>
            <option value="DENIED">Denied</option>
          </select>
        </div>
      </div>

      <div className="table-container">
        <table className="data-table">
          <thead>
            <tr>
              <th>Check ID</th>
              <th>Transaction ID</th>
              <th>Risk Score</th>
              <th>Risk Level</th>
              <th>Status</th>
              <th>Reason</th>
              <th>Created</th>
            </tr>
          </thead>
          <tbody>
            {filteredChecks.map((check) => {
              const riskLevel = getRiskLevel(check.riskScore);
              return (
                <tr key={check.checkId}>
                  <td><code>{check.checkId}</code></td>
                  <td><code className="account-id">{check.transactionId}</code></td>
                  <td>
                    <div className="risk-score">
                      <div className="risk-bar-container">
                        <div
                          className="risk-bar"
                          style={{
                            width: `${check.riskScore}%`,
                            backgroundColor: riskLevel.color,
                          }}
                        />
                      </div>
                      <span>{check.riskScore.toFixed(1)}%</span>
                    </div>
                  </td>
                  <td>
                    <span
                      className="status-badge"
                      style={{
                        backgroundColor: `${riskLevel.color}20`,
                        color: riskLevel.color,
                      }}
                    >
                      {riskLevel.label}
                    </span>
                  </td>
                  <td>
                    <div className="status-with-icon">
                      {getStatusIcon(check.status)}
                      <span
                        className="status-badge"
                        style={{
                          backgroundColor: `${getStatusColor(check.status)}20`,
                          color: getStatusColor(check.status),
                        }}
                      >
                        {check.status}
                      </span>
                    </div>
                  </td>
                  <td>{check.reason || '-'}</td>
                  <td>{new Date(check.createdAt).toLocaleString()}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default Fraud;
