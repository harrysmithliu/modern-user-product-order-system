import { App as AntApp, Button, Card, Form, Modal, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { approveOrder, listAdminOrders, rejectOrder } from "../api/services";
import type { Order } from "../api/types";
import { PageHeader } from "../components/PageHeader";

const statusOptions = [
  { label: "All Statuses", value: undefined },
  { label: "Pending Approval", value: 0 },
  { label: "Approved", value: 1 },
  { label: "Rejected", value: 2 },
  { label: "Cancelled", value: 3 },
];

export function AdminOrdersPage() {
  const [items, setItems] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [statusFilter, setStatusFilter] = useState<number | undefined>(undefined);
  const [rejectingOrder, setRejectingOrder] = useState<Order | null>(null);
  const { message } = AntApp.useApp();

  async function loadOrders(nextPage = page, nextSize = size, nextStatus = statusFilter) {
    setLoading(true);
    try {
      const data = await listAdminOrders(nextPage, nextSize, nextStatus);
      setItems(data.items);
      setPage(data.page);
      setSize(data.size);
      setTotal(data.total);
    } catch (error) {
      message.error("Failed to load order review data.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders(1, size, statusFilter);
  }, [statusFilter]);

  const columns: ColumnsType<Order> = [
    { title: "Order No.", dataIndex: "order_no" },
    { title: "User ID", dataIndex: "user_id" },
    { title: "Product ID", dataIndex: "product_id" },
    { title: "Quantity", dataIndex: "quantity" },
    {
      title: "Amount",
      dataIndex: "total_amount",
      render: (value: number) => `CNY ${value}`,
    },
    {
      title: "Status",
      dataIndex: "status_label",
      render: (value: string) => {
        const color =
          value === "APPROVED"
            ? "green"
            : value === "REJECTED"
              ? "red"
              : value === "PENDING_APPROVAL"
                ? "gold"
                : "default";
        return <Tag color={color}>{value}</Tag>;
      },
    },
    {
      title: "Created At",
      dataIndex: "create_time",
      render: (value: string | null) => value || "-",
    },
    {
      title: "Action",
      key: "action",
      render: (_, record) => (
        <Space>
          <Button
            type="primary"
            disabled={record.status_label !== "PENDING_APPROVAL"}
            onClick={async () => {
              try {
                await approveOrder(record.id);
                message.success("Order approved.");
                void loadOrders(page, size, statusFilter);
              } catch (error) {
                message.error("Approval failed.");
              }
            }}
          >
            Approve
          </Button>
          <Button
            danger
            disabled={record.status_label !== "PENDING_APPROVAL"}
            onClick={() => setRejectingOrder(record)}
          >
            Reject
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="Order Review" subtitle="Review all orders, filter pending items, and approve or reject them from the admin console." />
      <Card bordered={false}>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Select
            style={{ width: 220 }}
            options={statusOptions}
            value={statusFilter}
            onChange={(value) => setStatusFilter(value)}
          />
          <Table
            rowKey="id"
            columns={columns}
            dataSource={items}
            loading={loading}
            pagination={{
              current: page,
              pageSize: size,
              total,
              onChange: (nextPage, nextSize) => {
                void loadOrders(nextPage, nextSize, statusFilter);
              },
            }}
          />
        </Space>
      </Card>
      <RejectOrderModal
        order={rejectingOrder}
        onClose={() => setRejectingOrder(null)}
        onSuccess={() => {
          setRejectingOrder(null);
          void loadOrders(page, size, statusFilter);
        }}
      />
    </Space>
  );
}

function RejectOrderModal(props: {
  order: Order | null;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [form] = Form.useForm<{ rejectReason: string }>();
  const [submitting, setSubmitting] = useState(false);
  const { message } = AntApp.useApp();

  return (
    <Modal
      open={Boolean(props.order)}
      title="Reject Order"
      onCancel={props.onClose}
      onOk={() => form.submit()}
      okText="Confirm Reject"
      confirmLoading={submitting}
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={async (values) => {
          if (!props.order) {
            return;
          }
          setSubmitting(true);
          try {
            await rejectOrder(props.order.id, values.rejectReason);
            message.success("Order rejected.");
            form.resetFields();
            props.onSuccess();
          } catch (error) {
            message.error("Reject action failed.");
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Form.Item
          label="Reject Reason"
          name="rejectReason"
          rules={[{ required: true, message: "Please provide a reject reason." }]}
        >
          <Select
            options={[
              { label: "Inventory risk needs review", value: "Inventory risk needs review" },
              { label: "Product information is invalid", value: "Product information is invalid" },
              { label: "Request does not meet policy", value: "Request does not meet policy" },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
