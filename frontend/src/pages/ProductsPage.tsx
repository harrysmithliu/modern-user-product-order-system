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
      message.error("Failed to load products.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadProducts(1, size);
  }, [deferredKeyword]);

  const columns: ColumnsType<Product> = [
    {
      title: "Product",
      dataIndex: "product_name",
      render: (_, record) => (
        <div>
          <Typography.Text strong>{record.product_name}</Typography.Text>
          <div className="muted-row">{record.product_code}</div>
        </div>
      ),
    },
    {
      title: "Category",
      dataIndex: "category",
      render: (value) => value || "-",
    },
    {
      title: "Price",
      dataIndex: "price",
      render: (value: number) => `CNY ${value}`,
    },
    {
      title: "Stock",
      dataIndex: "stock",
    },
    {
      title: "Status",
      dataIndex: "status",
      render: (value: number) =>
        value === 1 ? <Tag color="green">On Sale</Tag> : <Tag color="default">Off Sale</Tag>,
    },
    {
      title: "Action",
      key: "action",
      render: (_, record) => (
        <Button
          type="primary"
          disabled={record.status !== 1 || record.stock <= 0}
          onClick={() => setOrderingProduct(record)}
        >
          Order
        </Button>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="Products" subtitle="Browse the catalog, search by keyword, and place an order directly from the main MVP entry point." />
      <Card bordered={false}>
        <Space direction="vertical" size={16} style={{ width: "100%" }}>
          <Input.Search
            placeholder="Search product name"
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
      message.success("Order created and waiting for admin review.");
      form.resetFields();
      props.onSuccess();
    } catch (error) {
      message.error("Failed to create order. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={Boolean(props.product)}
      title={props.product ? `Create Order: ${props.product.product_name}` : "Create Order"}
      onCancel={props.onClose}
      onOk={() => form.submit()}
      okText="Submit Order"
      confirmLoading={submitting}
    >
      <Form form={form} layout="vertical" onFinish={handleSubmit} initialValues={{ quantity: 1 }}>
        <Form.Item label="Unit Price">
          <Typography.Text>{props.product ? `CNY ${props.product.price}` : "-"}</Typography.Text>
        </Form.Item>
        <Form.Item
          label="Quantity"
          name="quantity"
          rules={[{ required: true, message: "Please enter a quantity." }]}
        >
          <InputNumber min={1} max={props.product?.stock || 1} style={{ width: "100%" }} />
        </Form.Item>
      </Form>
    </Modal>
  );
}
