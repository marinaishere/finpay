import api from './api';

export interface RegisterRequest {
  firstName: string;
  lastName: string;
  email: string;
  username: string;
  password: string;
  role: string;
  location: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface AuthResponse {
  token: string;
}

export interface User {
  id: number;
  username: string;
  email: string;
  role: string;
  createdAt: string;
}

export const authService = {
  register: async (data: RegisterRequest): Promise<User> => {
    const response = await api.post('/auth-services/users', data);
    return response.data;
  },

  login: async (data: LoginRequest): Promise<AuthResponse> => {
    const response = await api.post('/auth-services/login', data);
    return response.data;
  },

  getUsers: async (): Promise<User[]> => {
    const response = await api.get('/auth-services/users');
    return response.data;
  },
};
