import { App as AntApp, Button, Card, Popconfirm, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { getApiErrorMessage, getApiErrorStatus } from "../api/errors";
import { cancelOrder, listMyOrders, payOrder } from "../api/services";
import { ORDER_STATUS } from "../api/types";
import type { Order } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { formatCny, formatLocalDateTime } from "../utils/formatters";

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

function canCancelOrder(status: number): boolean {
  return (
    status === ORDER_STATUS.PAYING ||
    status === ORDER_STATUS.PENDING_APPROVAL ||
    status === ORDER_STATUS.PAID_PENDING_APPROVAL
  );
}

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
      message.error(getApiErrorMessage(error) || "Failed to load orders.");
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
      key: "amount_breakdown",
      render: (_, record) => {
        const finalAmount = record.final_amount ?? record.total_amount;
        return (
          <Space direction="vertical" size={0}>
            <Typography.Text strong>{`Final: ${formatCny(finalAmount)}`}</Typography.Text>
            <Typography.Text type="secondary">{`Origin: ${formatCny(record.origin_amount)}`}</Typography.Text>
            <Typography.Text type="secondary">{`Discount: ${formatCny(record.discount_amount)}`}</Typography.Text>
          </Space>
        );
      },
    },
    {
      title: "Status",
      dataIndex: "status_label",
      render: (value: string) => <Tag color={statusColorMap[value] || "default"}>{value}</Tag>,
    },
    {
      title: "Delivery ETA",
      dataIndex: "expected_delivery_time",
      render: (value: string | null) => formatLocalDateTime(value),
    },
    {
      title: "Reject Reason",
      dataIndex: "reject_reason",
      render: (value: string | null) => value || "-",
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
        const canPay = record.status === ORDER_STATUS.PAYING;
        const canCancel = canCancelOrder(record.status);

        if (!canPay && !canCancel) {
          return <Typography.Text type="secondary">-</Typography.Text>;
        }

        return (
          <Space>
            {canPay ? (
              <Popconfirm
                title="Confirm payment?"
                description="Are you sure you want to pay for this order now?"
                okText="Pay Now"
                cancelText="Back"
                onConfirm={async () => {
                  try {
                    await payOrder(record.id);
                    message.success("Payment successful. Order is waiting for admin approval.");
                  } catch (error) {
                    if (getApiErrorStatus(error) === 504) {
                      message.warning("Payment timed out. Please retry.");
                    } else {
                      message.error(getApiErrorMessage(error) || "Payment failed.");
                    }
                  } finally {
                    void loadOrders(page, size);
                  }
                }}
              >
                <Button type="primary">Pay Now</Button>
              </Popconfirm>
            ) : null}
            {canCancel ? (
              <Popconfirm
                title="Confirm cancellation?"
                description="Are you sure you want to cancel this order?"
                okText="Cancel Order"
                cancelText="Keep Order"
                onConfirm={async () => {
                  try {
                    await cancelOrder(record.id);
                    message.success("Order cancelled.");
                  } catch (error) {
                    message.error(getApiErrorMessage(error) || "Failed to cancel order.");
                  } finally {
                    void loadOrders(page, size);
                  }
                }}
              >
                <Button>Cancel</Button>
              </Popconfirm>
            ) : null}
          </Space>
        );
      },
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader
        title="My Orders"
        subtitle="Create order then pay in this list. Payment timeout can be retried, and shipping ETA/refund details are shown here."
      />
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
