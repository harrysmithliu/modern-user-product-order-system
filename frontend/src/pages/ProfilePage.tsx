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
      <PageHeader title="Profile" subtitle="View the current account profile, update basic information, and change the password separately." />
      <Row gutter={[20, 20]}>
        <Col xs={24} xl={14}>
          <Card title="Basic Information" bordered={false}>
            <Form
              form={profileForm}
              layout="vertical"
              onFinish={async (values) => {
                setProfileSaving(true);
                try {
                  await updateMyProfile(values);
                  await refreshUser();
                  message.success("Profile updated.");
                } catch (error) {
                  message.error("Failed to update profile.");
                } finally {
                  setProfileSaving(false);
                }
              }}
            >
              <Form.Item label="Username">
                <Input value={user?.username} disabled />
              </Form.Item>
              <Form.Item label="Nickname" name="nickname">
                <Input placeholder="Enter a nickname" />
              </Form.Item>
              <Form.Item label="Phone" name="phone">
                <Input placeholder="Enter a phone number" />
              </Form.Item>
              <Form.Item label="Email" name="email">
                <Input placeholder="Enter an email address" />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={profileSaving}>
                Save Profile
              </Button>
            </Form>
          </Card>
        </Col>
        <Col xs={24} xl={10}>
          <Card title="Change Password" bordered={false}>
            <Form
              form={passwordForm}
              layout="vertical"
              onFinish={async (values) => {
                setPasswordSaving(true);
                try {
                  await changeMyPassword(values.oldPassword, values.newPassword);
                  message.success("Password updated.");
                  passwordForm.resetFields();
                } catch (error) {
                  message.error("Failed to update password.");
                } finally {
                  setPasswordSaving(false);
                }
              }}
            >
              <Form.Item
                label="Current Password"
                name="oldPassword"
                rules={[{ required: true, message: "Please enter your current password." }]}
              >
                <Input.Password />
              </Form.Item>
              <Form.Item
                label="New Password"
                name="newPassword"
                rules={[{ required: true, message: "Please enter a new password." }]}
              >
                <Input.Password />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={passwordSaving}>
                Update Password
              </Button>
            </Form>
          </Card>
        </Col>
      </Row>
    </Space>
  );
}
