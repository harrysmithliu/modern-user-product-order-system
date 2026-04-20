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
  origin_amount: number | null;
  discount_amount: number | null;
  final_amount: number | null;
  status: number;
  status_label: string;
  reject_reason: string | null;
  payment_time: string | null;
  ship_time: string | null;
  expected_delivery_time: string | null;
  complete_time: string | null;
  refund_time: string | null;
  approve_time: string | null;
  cancel_time: string | null;
  create_time: string | null;
  update_time: string | null;
}

export interface UserCouponBalanceItem {
  coupon_type: number;
  discount_rate: string;
  quantity: number;
}

export interface UserCouponBalanceResponse {
  user_id: number;
  items: UserCouponBalanceItem[];
}

export interface OrderIssuedCouponResponse {
  user_id: number;
  order_no: string;
  order_amount: string;
  issued: boolean;
  coupon_type: number;
  discount_rate: string;
  balance_after_issue: number;
  message: string;
}

export interface OrderSelectedCouponResponse {
  user_id: number;
  order_no: string;
  order_amount: string;
  claimed: boolean;
  coupon_type: number;
  discount_rate: string;
  discount_amount: string;
  final_amount: string;
  message: string;
}

export const ORDER_STATUS = {
  PENDING_APPROVAL: 0,
  APPROVED: 1,
  REJECTED: 2,
  CANCELLED: 3,
  PAYING: 4,
  PAID_PENDING_APPROVAL: 5,
  SHIPPING: 6,
  COMPLETED: 7,
  REFUNDING: 8,
} as const;

export interface PageResponse<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}
