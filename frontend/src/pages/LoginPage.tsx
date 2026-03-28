import { LockOutlined, UserOutlined } from "@ant-design/icons";
import { App as AntApp, Button, Card, Form, Input, Space, Typography } from "antd";
import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "../api/services";
import { useAuth } from "../modules/auth/AuthProvider";

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
      message.success("登录成功");
      navigate(user?.role === "ADMIN" ? "/admin/orders" : "/products");
    } catch (error: unknown) {
      message.error("登录失败，请检查用户名和密码");
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-page">
      <div className="login-hero">
        <Typography.Text className="login-eyebrow">Polyglot Microservices Demo</Typography.Text>
        <Typography.Title className="login-title">
          Modern User-Product-Order System
        </Typography.Title>
        <Typography.Paragraph className="login-description">
          一个面向作品集展示的最简现代电商微服务系统，包含用户、商品、订单三大核心域，以及统一网关、JWT 鉴权和可扩展的云原生架构。
        </Typography.Paragraph>
        <Space direction="vertical" size={4}>
          <Typography.Text>
            管理员账号：<Typography.Text code>admin / Admin@123</Typography.Text>
          </Typography.Text>
          <Typography.Text>
            普通用户示例：<Typography.Text code>john_smith / User@123</Typography.Text>
          </Typography.Text>
        </Space>
      </div>
      <Card className="login-card" bordered={false}>
        <Typography.Title level={3}>登录系统</Typography.Title>
        <Typography.Paragraph type="secondary">
          当前版本优先打通核心业务闭环，注册页预留到后续增强阶段。
        </Typography.Paragraph>
        <Form layout="vertical" onFinish={handleSubmit} autoComplete="off">
          <Form.Item
            label="用户名"
            name="username"
            rules={[{ required: true, message: "请输入用户名" }]}
          >
            <Input prefix={<UserOutlined />} placeholder="请输入用户名" />
          </Form.Item>
          <Form.Item
            label="密码"
            name="password"
            rules={[{ required: true, message: "请输入密码" }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="请输入密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block size="large" loading={loading}>
            登录
          </Button>
        </Form>
      </Card>
    </div>
  );
}
