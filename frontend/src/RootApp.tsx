import {
  AppstoreOutlined,
  LoginOutlined,
  LogoutOutlined,
  OrderedListOutlined,
  ShoppingCartOutlined,
  TeamOutlined,
  UserOutlined,
} from "@ant-design/icons";
import { Button, Layout, Menu, Space, Spin, Typography } from "antd";
import { Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { apiClient } from "./api/client";
import { ProtectedRoute } from "./components/ProtectedRoute";
import { useAuth } from "./modules/auth/AuthProvider";
import { AdminOrdersPage } from "./pages/AdminOrdersPage";
import { AdminProductsPage } from "./pages/AdminProductsPage";
import { LoginPage } from "./pages/LoginPage";
import { MyOrdersPage } from "./pages/MyOrdersPage";
import { ProductsPage } from "./pages/ProductsPage";
import { ProfilePage } from "./pages/ProfilePage";

const { Header, Sider, Content } = Layout;

export function RootApp() {
  const { user, ready, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const menuItems = !user
    ? [
        {
          key: "/login",
          icon: <LoginOutlined />,
          label: "Sign In",
        },
      ]
    : [
        { key: "/products", icon: <AppstoreOutlined />, label: "Products" },
        { key: "/orders", icon: <ShoppingCartOutlined />, label: "My Orders" },
        { key: "/profile", icon: <UserOutlined />, label: "Profile" },
        ...(user.role === "ADMIN"
          ? [
              { key: "/admin/orders", icon: <OrderedListOutlined />, label: "Order Review" },
              { key: "/admin/products", icon: <TeamOutlined />, label: "Product Admin" },
            ]
          : []),
      ];

  if (!ready) {
    return (
      <div className="page-spinner">
        <Spin size="large" />
      </div>
    );
  }

  if (!user) {
    return (
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    );
  }

  return (
    <Layout className="app-layout">
      <Sider breakpoint="lg" collapsedWidth="0" width={260} theme="light" className="app-sider">
        <div className="brand-block">
          <Typography.Text className="brand-eyebrow">Portfolio Demo</Typography.Text>
          <Typography.Title level={4} className="brand-title">
            Modern UPO
          </Typography.Title>
          <Typography.Paragraph className="brand-subtitle">
            User, Product, and Order microservices platform
          </Typography.Paragraph>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={({ key }) => navigate(key)}
          className="side-menu"
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <div>
            <Typography.Text strong>{user.nickname || user.username}</Typography.Text>
            <Typography.Text type="secondary" className="header-role">
              {user.role}
            </Typography.Text>
          </div>
          <Space>
            <Button
              onClick={async () => {
                await logout();
                navigate("/login");
              }}
              icon={<LogoutOutlined />}
            >
              Sign Out
            </Button>
          </Space>
        </Header>
        <Content className="app-content">
          <Routes>
            <Route path="/login" element={<Navigate to="/products" replace />} />
            <Route
              path="/products"
              element={
                <ProtectedRoute>
                  <ProductsPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/orders"
              element={
                <ProtectedRoute>
                  <MyOrdersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/profile"
              element={
                <ProtectedRoute>
                  <ProfilePage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/orders"
              element={
                <ProtectedRoute adminOnly>
                  <AdminOrdersPage />
                </ProtectedRoute>
              }
            />
            <Route
              path="/admin/products"
              element={
                <ProtectedRoute adminOnly>
                  <AdminProductsPage />
                </ProtectedRoute>
              }
            />
            <Route path="*" element={<Navigate to="/products" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
}
