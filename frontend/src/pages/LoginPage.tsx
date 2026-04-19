import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { App as AntApp, Button, Card, Form, Input, Space, Typography } from "antd";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/services";
import { useAuth } from "../modules/auth/AuthProvider";
import axios from "axios";

export function LoginPage() {
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const { refreshUser } = useAuth();

  async function handleSubmit(values: { username: string; password: string }) {
    setLoading(true);
    try {
      await login(values.username, values.password);
      const user = await refreshUser();
      message.success("Signed in successfully.");
      navigate(user?.role === "ADMIN" ? "/admin/orders" : "/products");
    } catch (error: unknown) {
      // if (axios.isAxiosError<{ detail?: string }>(error)) {
      //   const detail = error.response?.data?.detail;
      //   message.error(detail || "Sign-in failed. Please try again later.");
      //   return;
      // }
      // message.error("Sign-in failed. Please try again later.");
      if (axios.isAxiosError(error)) {
        const status = error.response?.status;
        const detail = error.response?.data?.detail;

        if (status === 422 && Array.isArray(detail)) {
          const firstMsg = detail[0]?.msg;
          message.error(firstMsg || "Username or password format is invalid.");
          return;
        }

        if (typeof detail === "string") {
          message.error(detail);
          return;
        }

        message.error("Sign-in failed. Please try again later.");
        return;
      }

      message.error("Sign-in failed. Please try again later.");
      
    } finally {
      setLoading(false);
    }
  }
  return (
    <div className="login-page">
      <div className="login-shell">
        <div className="login-hero">
          <Typography.Text className="login-eyebrow">Polyglot Microservices Demo</Typography.Text>
          <Typography.Title className="login-title">
            Modern User-Product-Order System
          </Typography.Title>
          <Typography.Paragraph className="login-description">
            A portfolio-focused modern commerce demo built around user, product, and order domains,
            with a unified gateway, JWT-based authentication, and a cloud-ready microservices layout.
          </Typography.Paragraph>
          <Space direction="vertical" size={4}>
            <Typography.Text>
              Admin account: <Typography.Text code>admin / Admin@123</Typography.Text>
            </Typography.Text>
            <Typography.Text>
              Demo user: <Typography.Text code>john_smith / User@123</Typography.Text>
            </Typography.Text>
          </Space>
        </div>
        <Card className="login-card" bordered={false}>
          <Typography.Title level={3}>Sign In</Typography.Title>
          <Typography.Paragraph type="secondary">
            This MVP focuses on the core business loop first. Registration is reserved for a later phase.
          </Typography.Paragraph>
          <Form layout="vertical" onFinish={handleSubmit} autoComplete="off">
            <Form.Item
              label="Username"
              name="username"
              rules={[{ required: true, message: "Please enter your username." }]}
            >
              <Input prefix={<UserOutlined />} placeholder="Enter your username" />
            </Form.Item>
            <Form.Item
              label="Password"
              name="password"
              rules={[{ required: true, message: "Please enter your password." }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="Enter your password" />
            </Form.Item>
            <Button type="primary" htmlType="submit" block size="large" loading={loading}>
              Sign In
            </Button>
          </Form>
          <Typography.Text className="login-author-mark">
            Built by harrysmithliu
          </Typography.Text>
        </Card>
      </div>
    </div>
  );
}
