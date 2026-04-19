import { App as AntApp, Button, Card, Form, Modal, Select, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { getApiErrorMessage } from "../api/errors";
import { approveOrder, listAdminOrders, rejectOrder } from "../api/services";
import { ORDER_STATUS } from "../api/types";
import type { Order } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { formatCny, formatLocalDateTime } from "../utils/formatters";

const statusOptions = [
  { label: "All Statuses", value: null },
  { label: "PENDING_APPROVAL", value: ORDER_STATUS.PENDING_APPROVAL },
  { label: "PAYING", value: ORDER_STATUS.PAYING },
  { label: "PAID_PENDING_APPROVAL", value: ORDER_STATUS.PAID_PENDING_APPROVAL },
  { label: "APPROVED", value: ORDER_STATUS.APPROVED },
  { label: "SHIPPING", value: ORDER_STATUS.SHIPPING },
  { label: "REFUNDING", value: ORDER_STATUS.REFUNDING },
  { label: "REJECTED", value: ORDER_STATUS.REJECTED },
  { label: "CANCELLED", value: ORDER_STATUS.CANCELLED },
  { label: "COMPLETED", value: ORDER_STATUS.COMPLETED },
];

const statusColorMap: Record<string, string> = {
  PENDING_APPROVAL: "gold",
  APPROVED: "green",
  REJECTED: "red",
  CANCELLED: "default",
  PAYING: "orange",
  PAID_PENDING_APPROVAL: "processing",
  SHIPPING: "blue",
  COMPLETED: "green",
  REFUNDING: "volcano",
};

function canReview(status: number): boolean {
  return status === ORDER_STATUS.PENDING_APPROVAL || status === ORDER_STATUS.PAID_PENDING_APPROVAL;
}

export function AdminOrdersPage() {
  const [items, setItems] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [statusFilter, setStatusFilter] = useState<number | null>(null);
  const [rejectingOrder, setRejectingOrder] = useState<Order | null>(null);
  const { message } = AntApp.useApp();

  async function loadOrders(nextPage = page, nextSize = size, nextStatus = statusFilter) {
    setLoading(true);
    try {
      const data = await listAdminOrders(nextPage, nextSize, nextStatus ?? undefined);
      setItems(data.items);
      setPage(data.page);
      setSize(data.size);
      setTotal(data.total);
    } catch (error) {
      message.error(getApiErrorMessage(error) || "Failed to load order review data.");
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
      key: "amount",
      render: (_, record) => formatCny(record.final_amount ?? record.total_amount),
    },
    {
      title: "Status",
      dataIndex: "status_label",
      render: (value: string) => <Tag color={statusColorMap[value] || "default"}>{value}</Tag>,
    },
    {
      title: "Payment Time",
      dataIndex: "payment_time",
      render: (value: string | null) => formatLocalDateTime(value),
    },
    {
      title: "Ship Time",
      dataIndex: "ship_time",
      render: (value: string | null) => formatLocalDateTime(value),
    },
    {
      title: "Delivery ETA",
      dataIndex: "expected_delivery_time",
      render: (value: string | null) => formatLocalDateTime(value),
    },
    {
      title: "Refund Time",
      dataIndex: "refund_time",
      render: (value: string | null) => formatLocalDateTime(value),
    },
    {
      title: "Created At",
      dataIndex: "create_time",
      render: (value: string | null) => formatLocalDateTime(value),
    },
    {
      title: "Action",
      key: "action",
      render: (_, record) => {
        if (!canReview(record.status)) {
          return <Typography.Text type="secondary">-</Typography.Text>;
        }

        return (
          <Space>
            <Button
              type="primary"
              onClick={async () => {
                try {
                  await approveOrder(record.id);
                  message.success("Approved. Order moved to SHIPPING.");
                  void loadOrders(page, size, statusFilter);
                } catch (error) {
                  message.error(getApiErrorMessage(error) || "Approve action failed.");
                }
              }}
            >
              Approve & Ship
            </Button>
            <Button danger onClick={() => setRejectingOrder(record)}>
              Reject & Refund
            </Button>
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader
        title="Order Review"
        subtitle="Approve transitions the order to SHIPPING. Reject triggers refund and then moves to REJECTED."
      />
      <Card bordered={false}>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Select<number | null>
            style={{ width: 260 }}
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
            message.success("Rejected. Refund completed and order moved to REJECTED.");
            form.resetFields();
            props.onSuccess();
          } catch (error) {
            message.error(getApiErrorMessage(error) || "Reject action failed.");
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
