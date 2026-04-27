import { App as AntApp, Button, Card, Descriptions, Modal, Popconfirm, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { getApiErrorMessage, getApiErrorStatus } from "../api/errors";
import {
  cancelOrder,
  fetchIssuedCouponByOrder,
  fetchMyCouponBalances,
  fetchSelectedCouponByOrder,
  listMyOrders,
  payOrder,
} from "../api/services";
import { ORDER_STATUS } from "../api/types";
import type {
  Order,
  OrderIssuedCouponResponse,
  OrderSelectedCouponResponse,
  UserCouponBalanceResponse,
} from "../api/types";
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

const couponTypeLabels: Record<number, string> = {
  10: "10% Coupon",
  20: "20% Coupon",
  30: "30% Coupon",
};

function canCancelOrder(status: number): boolean {
  return (
    status === ORDER_STATUS.PAYING ||
    status === ORDER_STATUS.PENDING_APPROVAL ||
    status === ORDER_STATUS.PAID_PENDING_APPROVAL
  );
}

function formatRate(rate: string | undefined): string {
  if (!rate) {
    return "-";
  }
  const parsed = Number(rate);
  if (Number.isNaN(parsed)) {
    return rate;
  }
  return `${Math.round(parsed * 100)}%`;
}

export function MyOrdersPage() {
  const [items, setItems] = useState<Order[]>([]);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);
  const [total, setTotal] = useState(0);
  const [couponBalances, setCouponBalances] = useState<UserCouponBalanceResponse | null>(null);
  const [couponLoading, setCouponLoading] = useState(false);
  const [couponDetailOrderNo, setCouponDetailOrderNo] = useState<string | null>(null);
  const [couponDetailLoading, setCouponDetailLoading] = useState(false);
  const [issuedCoupon, setIssuedCoupon] = useState<OrderIssuedCouponResponse | null>(null);
  const [selectedCoupon, setSelectedCoupon] = useState<OrderSelectedCouponResponse | null>(null);
  const [issuedCouponNotFound, setIssuedCouponNotFound] = useState(false);
  const [selectedCouponNotFound, setSelectedCouponNotFound] = useState(false);
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

  async function loadCouponBalances() {
    setCouponLoading(true);
    try {
      const data = await fetchMyCouponBalances();
      setCouponBalances(data);
    } catch (error) {
      message.error(getApiErrorMessage(error) || "Failed to load coupon balances.");
    } finally {
      setCouponLoading(false);
    }
  }

  async function openCouponDetail(orderNo: string) {
    setCouponDetailOrderNo(orderNo);
    setCouponDetailLoading(true);
    setIssuedCoupon(null);
    setSelectedCoupon(null);
    setIssuedCouponNotFound(false);
    setSelectedCouponNotFound(false);

    const [issuedResult, selectedResult] = await Promise.allSettled([
      fetchIssuedCouponByOrder(orderNo),
      fetchSelectedCouponByOrder(orderNo),
    ]);

    if (issuedResult.status === "fulfilled") {
      setIssuedCoupon(issuedResult.value);
    } else {
      const status = getApiErrorStatus(issuedResult.reason);
      if (status === 404) {
        setIssuedCouponNotFound(true);
      } else {
        message.error(getApiErrorMessage(issuedResult.reason) || "Failed to query issued coupon record.");
      }
    }

    if (selectedResult.status === "fulfilled") {
      setSelectedCoupon(selectedResult.value);
    } else {
      const status = getApiErrorStatus(selectedResult.reason);
      if (status === 404) {
        setSelectedCouponNotFound(true);
      } else {
        message.error(getApiErrorMessage(selectedResult.reason) || "Failed to query selected coupon record.");
      }
    }

    setCouponDetailLoading(false);
  }

  useEffect(() => {
    void loadOrders();
    void loadCouponBalances();
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

        return (
          <Space wrap>
            <Button onClick={() => void openCouponDetail(record.order_no)}>Coupon Detail</Button>
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
                    void loadCouponBalances();
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

  const couponItems = couponBalances?.items ?? [];
  const couponQuantityByType = new Map<number, number>(couponItems.map((item) => [item.coupon_type, item.quantity]));
  const couponRateByType = new Map<number, string>(couponItems.map((item) => [item.coupon_type, item.discount_rate]));

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader
        title="My Orders"
        subtitle="Create order then pay in this list. Payment timeout can be retried, and shipping ETA/refund details are shown here."
      />
      <Card title="Coupon Center" bordered={false} loading={couponLoading}>
        <Space wrap size={10}>
          {[10, 20, 30].map((couponType) => (
            <Tag key={couponType} color={(couponQuantityByType.get(couponType) || 0) > 0 ? "green" : "default"}>
              {`${couponTypeLabels[couponType]} (${formatRate(couponRateByType.get(couponType))}) · Remaining ${
                couponQuantityByType.get(couponType) || 0
              }`}
            </Tag>
          ))}
        </Space>
      </Card>
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
      <CouponOrderDetailModal
        orderNo={couponDetailOrderNo}
        loading={couponDetailLoading}
        issuedCoupon={issuedCoupon}
        selectedCoupon={selectedCoupon}
        issuedCouponNotFound={issuedCouponNotFound}
        selectedCouponNotFound={selectedCouponNotFound}
        onClose={() => setCouponDetailOrderNo(null)}
      />
    </Space>
  );
}

function CouponOrderDetailModal(props: {
  orderNo: string | null;
  loading: boolean;
  issuedCoupon: OrderIssuedCouponResponse | null;
  selectedCoupon: OrderSelectedCouponResponse | null;
  issuedCouponNotFound: boolean;
  selectedCouponNotFound: boolean;
  onClose: () => void;
}) {
  return (
    <Modal
      open={Boolean(props.orderNo)}
      title={props.orderNo ? `Coupon Detail · ${props.orderNo}` : "Coupon Detail"}
      footer={null}
      onCancel={props.onClose}
    >
      {props.loading ? (
        <Typography.Text type="secondary">Loading coupon records...</Typography.Text>
      ) : (
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Card size="small" title="Selected For Payment">
            {props.selectedCoupon ? (
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="Claimed">{props.selectedCoupon.claimed ? "Yes" : "No"}</Descriptions.Item>
                <Descriptions.Item label="Coupon Type">{props.selectedCoupon.coupon_type}</Descriptions.Item>
                <Descriptions.Item label="Discount Rate">{formatRate(props.selectedCoupon.discount_rate)}</Descriptions.Item>
                <Descriptions.Item label="Discount Amount">{`CNY ${props.selectedCoupon.discount_amount}`}</Descriptions.Item>
                <Descriptions.Item label="Final Amount">{`CNY ${props.selectedCoupon.final_amount}`}</Descriptions.Item>
                <Descriptions.Item label="Message">{props.selectedCoupon.message}</Descriptions.Item>
              </Descriptions>
            ) : (
              <Typography.Text type="secondary">
                {props.selectedCouponNotFound ? "No coupon selection record for this order." : "No data."}
              </Typography.Text>
            )}
          </Card>
          <Card size="small" title="Issued After Payment">
            {props.issuedCoupon ? (
              <Descriptions size="small" column={1}>
                <Descriptions.Item label="Issued">{props.issuedCoupon.issued ? "Yes" : "No"}</Descriptions.Item>
                <Descriptions.Item label="Coupon Type">{props.issuedCoupon.coupon_type}</Descriptions.Item>
                <Descriptions.Item label="Discount Rate">{formatRate(props.issuedCoupon.discount_rate)}</Descriptions.Item>
                <Descriptions.Item label="Balance After Issue">{props.issuedCoupon.balance_after_issue}</Descriptions.Item>
                <Descriptions.Item label="Message">{props.issuedCoupon.message}</Descriptions.Item>
              </Descriptions>
            ) : (
              <Typography.Text type="secondary">
                {props.issuedCouponNotFound ? "No coupon issue record for this order." : "No data."}
              </Typography.Text>
            )}
          </Card>
        </Space>
      )}
    </Modal>
  );
}
