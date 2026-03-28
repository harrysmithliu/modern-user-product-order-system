import axios from "axios";

const TOKEN_KEY = "modern-upo-token";

export const apiClient = axios.create({
  baseURL: "http://localhost:8000",
  timeout: 15000,
});

apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY);
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export function saveToken(token: string) {
  localStorage.setItem(TOKEN_KEY, token);
}

export function loadToken() {
  return localStorage.getItem(TOKEN_KEY);
}

apiClient.clearToken = () => {
  localStorage.removeItem(TOKEN_KEY);
};

declare module "axios" {
  interface AxiosInstance {
    clearToken: () => void;
  }
}
