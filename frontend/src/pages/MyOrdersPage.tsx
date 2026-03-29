import { App as AntApp, Button, Card, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { cancelOrder, listMyOrders } from "../api/services";
import type { Order } from "../api/types";
import { PageHeader } from "../components/PageHeader";

const statusColorMap: Record<string, string> = {
  PENDING_APPROVAL: "gold",
  APPROVED: "green",
  REJECTED: "red",
  CANCELLED: "default",
};

export function MyOrdersPage() {
  const [items, setItems] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const { message } = AntApp.useApp();

  async function loadOrders(nextPage = page, nextSize = size) {
    setLoading(true);
    try {
      const data = await listMyOrders(nextPage, nextSize);
      setItems(data.items);
      setPage(data.page);
      setSize(data.size);
      setTotal(data.total);
    } catch (error) {
      message.error("Failed to load orders.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders();
  }, []);

  const columns: ColumnsType<Order> = [
    {
      title: "Order No.",
      dataIndex: "order_no",
    },
    {
      title: "Product ID",
      dataIndex: "product_id",
    },
    {
      title: "Quantity",
      dataIndex: "quantity",
    },
    {
      title: "Amount",
      dataIndex: "total_amount",
      render: (value: number) => `CNY ${value}`,
    },
    {
      title: "Status",
      dataIndex: "status_label",
      render: (value: string) => <Tag color={statusColorMap[value] || "default"}>{value}</Tag>,
    },
    {
      title: "Reject Reason",
      dataIndex: "reject_reason",
      render: (value: string | null) => value || "-",
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
        <Button
          disabled={record.status_label !== "PENDING_APPROVAL"}
          onClick={async () => {
            try {
              await cancelOrder(record.id);
              message.success("Order cancelled.");
              void loadOrders(page, size);
            } catch (error) {
              message.error("Failed to cancel order.");
            }
          }}
        >
          Cancel
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="My Orders" subtitle="Review your order history, approval results, and cancel orders that are still pending review." />
      <Card bordered={false}>
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
              void loadOrders(nextPage, nextSize);
            },
          }}
        />
      </Card>
    </Space>
  );
}
