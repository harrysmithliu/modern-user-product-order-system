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
      message.error("订单列表加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadOrders();
  }, []);

  const columns: ColumnsType<Order> = [
    {
      title: "订单号",
      dataIndex: "order_no",
    },
    {
      title: "商品 ID",
      dataIndex: "product_id",
    },
    {
      title: "数量",
      dataIndex: "quantity",
    },
    {
      title: "金额",
      dataIndex: "total_amount",
      render: (value: number) => `¥${value}`,
    },
    {
      title: "状态",
      dataIndex: "status_label",
      render: (value: string) => <Tag color={statusColorMap[value] || "default"}>{value}</Tag>,
    },
    {
      title: "驳回原因",
      dataIndex: "reject_reason",
      render: (value: string | null) => value || "-",
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
        <Button
          disabled={record.status_label !== "PENDING_APPROVAL"}
          onClick={async () => {
            try {
              await cancelOrder(record.id);
              message.success("订单已取消");
              void loadOrders(page, size);
            } catch (error) {
              message.error("取消订单失败");
            }
          }}
        >
          取消订单
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="我的订单" subtitle="展示当前登录用户的订单状态和审批结果，未审批订单支持主动取消。" />
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
