import axios, { isAxiosError } from "axios";
import type { AxiosInstance } from "axios";
import { useAuthStore } from "../store/authStore";

const createApi = (baseURL: string): AxiosInstance => {
  const instance = axios.create({
    baseURL,
    headers: {
      "Content-Type": "application/json",
    },
  });

  instance.interceptors.request.use((config) => {
    const token = useAuthStore.getState().token;
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  });

  instance.interceptors.response.use(
    (response) => response,
    (error) => {
      if (error.response?.status === 401 || error.response?.status === 403) {
        useAuthStore.getState().logout();
      }
      return Promise.reject(error);
    },
  );

  return instance;
};

export const userApi = createApi(
  import.meta.env.VITE_USER_SERVICE_URL || "http://localhost:8080",
);
export const expenseApi = createApi(
  import.meta.env.VITE_EXPENSE_SERVICE_URL || "http://localhost:8081",
);

export { isAxiosError };
export default userApi;
