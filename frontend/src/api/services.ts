import { apiClient, saveToken } from "./client";
import type { ApiResponse, LoginResponse, Order, PageResponse, Product, ProductPage, UserProfile } from "./types";

export async function login(username: string, password: string) {
  const response = await apiClient.post<ApiResponse<LoginResponse>>("/api/auth/login", { username, password });
  saveToken(response.data.data.access_token);
  return response.data.data;
}

export async function logout() {
  await apiClient.post<ApiResponse<null>>("/api/auth/logout");
}

export async function fetchMe() {
  const response = await apiClient.get<ApiResponse<UserProfile>>("/api/users/me");
  return response.data.data;
}

export async function updateMyProfile(payload: Pick<UserProfile, "nickname" | "phone" | "email">) {
  const response = await apiClient.put<ApiResponse<UserProfile>>("/api/users/me/profile", payload);
  return response.data.data;
}

export async function changeMyPassword(oldPassword: string, newPassword: string) {
  return apiClient.put<ApiResponse<null>>("/api/users/me/password", {
    old_password: oldPassword,
    new_password: newPassword,
  });
}

export async function listProducts(params: {
  page: number;
  size: number;
  keyword?: string;
  include_off_sale?: boolean;
}) {
  const response = await apiClient.get<ApiResponse<ProductPage>>("/api/products", { params });
  return response.data.data;
}

export async function createOrder(payload: { request_no: string; product_id: number; quantity: number }) {
  const response = await apiClient.post<ApiResponse<Order>>("/api/orders", payload);
  return response.data.data;
}

export async function listMyOrders(page: number, size: number) {
  const response = await apiClient.get<ApiResponse<PageResponse<Order>>>("/api/orders/my", {
    params: { page, size },
  });
  return response.data.data;
}

export async function cancelOrder(orderId: number) {
  const response = await apiClient.post<ApiResponse<Order>>(`/api/orders/${orderId}/cancel`);
  return response.data.data;
}

export async function listAdminOrders(page: number, size: number, status?: number) {
  const response = await apiClient.get<ApiResponse<PageResponse<Order>>>("/api/admin/orders", {
    params: { page, size, status },
  });
  return response.data.data;
}

export async function approveOrder(orderId: number) {
  const response = await apiClient.post<ApiResponse<Order>>(`/api/admin/orders/${orderId}/approve`);
  return response.data.data;
}

export async function rejectOrder(orderId: number, rejectReason: string) {
  const response = await apiClient.post<ApiResponse<Order>>(`/api/admin/orders/${orderId}/reject`, {
    reject_reason: rejectReason,
  });
  return response.data.data;
}

export async function createProduct(payload: {
  product_name: string;
  product_code: string;
  price: number;
  stock: number;
  category?: string;
  status: number;
}) {
  const response = await apiClient.post<ApiResponse<Product>>("/api/admin/products", payload);
  return response.data.data;
}

export async function updateProduct(
  productId: number,
  payload: {
    product_name: string;
    price: number;
    stock: number;
    category?: string;
    status: number;
  },
) {
  const response = await apiClient.put<ApiResponse<Product>>(`/api/admin/products/${productId}`, payload);
  return response.data.data;
}

export async function updateProductStatus(productId: number, status: number) {
  const response = await apiClient.put<ApiResponse<Product>>(`/api/admin/products/${productId}/status`, { status });
  return response.data.data;
}

export async function updateProductStock(productId: number, stock: number) {
  const response = await apiClient.put<ApiResponse<Product>>(`/api/admin/products/${productId}/stock`, { stock });
  return response.data.data;
}

export async function importProducts(file: File) {
  const formData = new FormData();
  formData.append("file", file);

  const response = await apiClient.post<ApiResponse<unknown>>(
    "/api/admin/products/import",
    formData,
  );

  return response.data.data;
}