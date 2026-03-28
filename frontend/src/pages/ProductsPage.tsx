import { App as AntApp, Button, Card, Form, Input, InputNumber, Modal, Space, Table, Tag, Typography } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useDeferredValue, useEffect, useState } from "react";
import { createOrder, listProducts } from "../api/services";
import type { Product } from "../api/types";
import { PageHeader } from "../components/PageHeader";

export function ProductsPage() {
  const [items, setItems] = useState<Product[]>([]);
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(8);
  const [total, setTotal] = useState(0);
  const [keywordInput, setKeywordInput] = useState("");
  const deferredKeyword = useDeferredValue(keywordInput);
  const [loading, setLoading] = useState(false);
  const [orderingProduct, setOrderingProduct] = useState<Product | null>(null);
  const { message } = AntApp.useApp();

  async function loadProducts(nextPage = page, nextSize = size) {
    setLoading(true);
    try {
      const data = await listProducts({
        page: nextPage,
        size: nextSize,
        keyword: deferredKeyword || undefined,
      });
      setItems(data.items);
      setPage(data.page);
      setSize(data.size);
      setTotal(data.total);
    } catch (error) {
      message.error("商品列表加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadProducts(1, size);
  }, [deferredKeyword]);

  const columns: ColumnsType<Product> = [
    {
      title: "商品",
      dataIndex: "product_name",
      render: (_, record) => (
        <div>
          <Typography.Text strong>{record.product_name}</Typography.Text>
          <div className="muted-row">{record.product_code}</div>
        </div>
      ),
    },
    {
      title: "分类",
      dataIndex: "category",
      render: (value) => value || "-",
    },
    {
      title: "价格",
      dataIndex: "price",
      render: (value: number) => `¥${value}`,
    },
    {
      title: "库存",
      dataIndex: "stock",
    },
    {
      title: "状态",
      dataIndex: "status",
      render: (value: number) =>
        value === 1 ? <Tag color="green">在售</Tag> : <Tag color="default">下架</Tag>,
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Button
          type="primary"
          disabled={record.status !== 1 || record.stock <= 0}
          onClick={() => setOrderingProduct(record)}
        >
          下单
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="商品列表" subtitle="支持关键字搜索、分页查询和直接下单，是当前 MVP 的主要用户入口。" />
      <Card bordered={false}>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Input.Search
            placeholder="搜索商品名称"
            allowClear
            value={keywordInput}
            onChange={(event) => setKeywordInput(event.target.value)}
            onSearch={() => void loadProducts(1, size)}
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
                void loadProducts(nextPage, nextSize);
              },
            }}
          />
        </Space>
      </Card>
      <CreateOrderModal
        product={orderingProduct}
        onClose={() => setOrderingProduct(null)}
        onSuccess={() => {
          setOrderingProduct(null);
          void loadProducts(page, size);
        }}
      />
    </Space>
  );
}

function CreateOrderModal(props: {
  product: Product | null;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [form] = Form.useForm<{ quantity: number }>();
  const [submitting, setSubmitting] = useState(false);
  const { message } = AntApp.useApp();

  async function handleSubmit(values: { quantity: number }) {
    if (!props.product) {
      return;
    }

    setSubmitting(true);
    try {
      await createOrder({
        request_no: crypto.randomUUID(),
        product_id: props.product.id,
        quantity: values.quantity,
      });
      message.success("订单已创建，等待管理员审批");
      form.resetFields();
      props.onSuccess();
    } catch (error) {
      message.error("下单失败，请稍后重试");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={Boolean(props.product)}
      title={props.product ? `下单: ${props.product.product_name}` : "创建订单"}
      onCancel={props.onClose}
      onOk={() => form.submit()}
      okText="提交订单"
      confirmLoading={submitting}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} initialValues={{ quantity: 1 }}>
        <Form.Item label="商品单价">
          <Typography.Text>{props.product ? `¥${props.product.price}` : "-"}</Typography.Text>
        </Form.Item>
        <Form.Item
          label="数量"
          name="quantity"
          rules={[{ required: true, message: "请输入数量" }]}
        >
          <InputNumber min={1} max={props.product?.stock || 1} style={{ width: "100%" }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
