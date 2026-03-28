import { App as AntApp, Button, Card, Form, Modal, Select, Space, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { approveOrder, listAdminOrders, rejectOrder } from "../api/services";
import type { Order } from "../api/types";
import { PageHeader } from "../components/PageHeader";

const statusOptions = [
  { label: "全部状态", value: undefined },
  { label: "待审批", value: 0 },
  { label: "已通过", value: 1 },
  { label: "已驳回", value: 2 },
  { label: "已取消", value: 3 },
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
      message.error("订单管理数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders(1, size, statusFilter);
  }, [statusFilter]);

  const columns: ColumnsType<Order> = [
    { title: "订单号", dataIndex: "order_no" },
    { title: "用户 ID", dataIndex: "user_id" },
    { title: "商品 ID", dataIndex: "product_id" },
    { title: "数量", dataIndex: "quantity" },
    {
      title: "金额",
      dataIndex: "total_amount",
      render: (value: number) => `¥${value}`,
    },
    {
      title: "状态",
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
      title: "创建时间",
      dataIndex: "create_time",
      render: (value: string | null) => value || "-",
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Space>
          <Button
            type="primary"
            disabled={record.status_label !== "PENDING_APPROVAL"}
            onClick={async () => {
              try {
                await approveOrder(record.id);
                message.success("订单已审批通过");
                void loadOrders(page, size, statusFilter);
              } catch (error) {
                message.error("审批失败");
              }
            }}
          >
            通过
          </Button>
          <Button
            danger
            disabled={record.status_label !== "PENDING_APPROVAL"}
            onClick={() => setRejectingOrder(record)}
          >
            驳回
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="订单审批" subtitle="管理员可分页查看订单，筛选待审批数据，并对订单执行审批通过或驳回。" />
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
      title="驳回订单"
      onCancel={props.onClose}
      onOk={() => form.submit()}
      okText="确认驳回"
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
            message.success("订单已驳回");
            form.resetFields();
            props.onSuccess();
          } catch (error) {
            message.error("驳回失败");
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Form.Item
          label="驳回原因"
          name="rejectReason"
          rules={[{ required: true, message: "请输入驳回原因" }]}
        >
          <Select
            options={[
              { label: "库存风险待复核", value: "库存风险待复核" },
              { label: "商品信息异常", value: "商品信息异常" },
              { label: "请求不符合规则", value: "请求不符合规则" },
            ]}
          />
        </Form.Item>
      </Form>
    </Modal>
  );
}
