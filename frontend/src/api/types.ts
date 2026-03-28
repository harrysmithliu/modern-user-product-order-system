export interface ApiResponse<T> {
  code: number;
  message: string;
  data: T;
  traceId: string;
  timestamp: string;
}

export interface LoginResponse {
  access_token: string;
  token_type: string;
  expires_in: number;
  user_id: number;
  username: string;
  role: string;
}

export interface UserProfile {
  id: number;
  userno: string;
  username: string;
  nickname: string | null;
  phone: string | null;
  email: string | null;
  role: "USER" | "ADMIN";
}

export interface Product {
  id: number;
  product_name: string;
  product_code: string;
  price: number;
  stock: number;
  category: string | null;
  status: number;
  version: number;
}

export interface ProductPage {
  items: Product[];
  page: number;
  size: number;
  total: number;
}

export interface Order {
  id: number;
  order_no: string;
  request_no: string;
  user_id: number;
  product_id: number;
  quantity: number;
  total_amount: number;
  status: number;
  status_label: string;
  reject_reason: string | null;
  approve_time: string | null;
  cancel_time: string | null;
  create_time: string | null;
  update_time: string | null;
}

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
