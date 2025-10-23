import React, { createContext, useContext, useState, useEffect } from 'react';
import { authService, type LoginRequest, type RegisterRequest, type AuthResponse } from '../services/authService';

interface AuthContextType {
  token: string | null;
  username: string | null;
  role: string | null;
  login: (data: LoginRequest) => Promise<void>;
  register: (data: RegisterRequest) => Promise<void>;
  logout: () => void;
  isAuthenticated: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'));
  const [username, setUsername] = useState<string | null>(localStorage.getItem('username'));
  const [role, setRole] = useState<string | null>(localStorage.getItem('role'));

  useEffect(() => {
    if (token) {
      localStorage.setItem('token', token);
    } else {
      localStorage.removeItem('token');
    }
  }, [token]);

  const login = async (data: LoginRequest) => {
    console.log('Login attempt with:', data.username);
    const response: AuthResponse = await authService.login(data);
    console.log('Login response received:', response);

    if (!response.token) {
      throw new Error('No token received from server');
    }

    setToken(response.token);
    setUsername(data.username);
    setRole('USER');
    localStorage.setItem('token', response.token);
    localStorage.setItem('username', data.username);
    localStorage.setItem('role', 'USER');
    console.log('Login successful, token saved');
  };

  const register = async (data: RegisterRequest) => {
    const user = await authService.register(data);
    // After registration, login to get token
    const loginResponse = await authService.login({
      username: data.username,
      password: data.password
    });
    setToken(loginResponse.token);
    setUsername(user.username);
    setRole(user.role);
    localStorage.setItem('token', loginResponse.token);
    localStorage.setItem('username', user.username);
    localStorage.setItem('role', user.role);
  };

  const logout = () => {
    setToken(null);
    setUsername(null);
    setRole(null);
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
  };

  return (
    <AuthContext.Provider
      value={{
        token,
        username,
        role,
        login,
        register,
        logout,
        isAuthenticated: !!token,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
