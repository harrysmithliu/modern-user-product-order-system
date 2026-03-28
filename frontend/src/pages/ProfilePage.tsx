import { App as AntApp, Button, Card, Col, Form, Input, Row, Space } from "antd";
import { useEffect, useState } from "react";
import { changeMyPassword, updateMyProfile } from "../api/services";
import { PageHeader } from "../components/PageHeader";
import { useAuth } from "../modules/auth/AuthProvider";

export function ProfilePage() {
  const { user, refreshUser } = useAuth();
  const [profileForm] = Form.useForm();
  const [passwordForm] = Form.useForm();
  const [profileSaving, setProfileSaving] = useState(false);
  const [passwordSaving, setPasswordSaving] = useState(false);
  const { message } = AntApp.useApp();

  useEffect(() => {
    profileForm.setFieldsValue({
      nickname: user?.nickname,
      phone: user?.phone,
      email: user?.email,
    });
  }, [user]);

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="个人资料" subtitle="支持查看当前登录用户资料，并分别完成资料修改和密码修改。" />
      <Row gutter={[20, 20]}>
        <Col xs={24} xl={14}>
          <Card title="基础资料" bordered={false}>
            <Form
              form={profileForm}
              layout="vertical"
              onFinish={async (values) => {
                setProfileSaving(true);
                try {
                  await updateMyProfile(values);
                  await refreshUser();
                  message.success("资料已更新");
                } catch (error) {
                  message.error("资料更新失败");
                } finally {
                  setProfileSaving(false);
                }
              }}
            >
              <Form.Item label="用户名">
                <Input value={user?.username} disabled />
              </Form.Item>
              <Form.Item label="昵称" name="nickname">
                <Input placeholder="请输入昵称" />
              </Form.Item>
              <Form.Item label="手机号" name="phone">
                <Input placeholder="请输入手机号" />
              </Form.Item>
              <Form.Item label="邮箱" name="email">
                <Input placeholder="请输入邮箱" />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={profileSaving}>
                保存资料
              </Button>
            </Form>
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card title="修改密码" bordered={false}>
            <Form
              form={passwordForm}
              layout="vertical"
              onFinish={async (values) => {
                setPasswordSaving(true);
                try {
                  await changeMyPassword(values.oldPassword, values.newPassword);
                  message.success("密码修改成功");
                  passwordForm.resetFields();
                } catch (error) {
                  message.error("密码修改失败");
                } finally {
                  setPasswordSaving(false);
                }
              }}
            >
              <Form.Item
                label="旧密码"
                name="oldPassword"
                rules={[{ required: true, message: "请输入旧密码" }]}
              >
                <Input.Password />
              </Form.Item>
              <Form.Item
                label="新密码"
                name="newPassword"
                rules={[{ required: true, message: "请输入新密码" }]}
              >
                <Input.Password />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={passwordSaving}>
                更新密码
              </Button>
            </Form>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
