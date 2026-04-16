import { App as AntApp, Button, Card, Form, Input, InputNumber, Modal, Space, Switch, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { createProduct, listProducts, updateProduct, updateProductStatus, updateProductStock } from "../api/services";
import type { Product } from "../api/types";
import { PageHeader } from "../components/PageHeader";
import { UploadOutlined } from "@ant-design/icons";
import { BulkImportProductsModal } from "../components/BulkImportProductsModal";

export function AdminProductsPage() {
  const [importing, setImporting] = useState(false);
  const [items, setItems] = useState<Product[]>([]);
  const [loading, setLoading] = useState(false);
  const [editingProduct, setEditingProduct] = useState<Product | null>(null);
  const [creating, setCreating] = useState(false);
  const { message } = AntApp.useApp();

  async function loadData() {
    setLoading(true);
    try {
      const data = await listProducts({
        page: 1,
        size: 100,
        include_off_sale: true,
      });
      setItems(data.items);
    } catch (error) {
      message.error("Failed to load product administration data.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  const columns: ColumnsType<Product> = [
    { title: "Product Code", dataIndex: "product_code" },
    { title: "Product Name", dataIndex: "product_name" },
    { title: "Price", dataIndex: "price", render: (value: number) => `CNY ${value}` },
    { title: "Stock", dataIndex: "stock" },
    { title: "Category", dataIndex: "category", render: (value: string | null) => value || "-" },
    {
      title: "Status",
      dataIndex: "status",
      render: (value: number, record) => (
        <Space>
          {value === 1 ? <Tag color="green">On Sale</Tag> : <Tag>Off Sale</Tag>}
          <Switch
            checked={value === 1}
            onChange={async (checked) => {
              try {
                await updateProductStatus(record.id, checked ? 1 : 0);
                message.success("Product status updated.");
                void loadData();
              } catch (error) {
                message.error("Failed to update status.");
              }
            }}
          />
        </Space>
      ),
    },
    {
      title: "Action",
      key: "action",
      render: (_, record) => (
        <Space>
          <Button onClick={() => setEditingProduct(record)}>Edit</Button>
          <Button
            onClick={async () => {
              try {
                await updateProductStock(record.id, record.stock + 10);
                message.success("Added 10 units of stock.");
                void loadData();
              } catch (error) {
                message.error("Failed to update stock.");
              }
            }}
          >
            +10 Stock
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader
        title="Product Admin"
        subtitle="Create products, edit core fields, toggle sale status, and adjust stock from the admin console."
      />
      <Card
        bordered={false}
        extra={
          <Space>
            <Button type="primary" onClick={() => setCreating(true)}>
              Create Product
            </Button>
            <Button
              onClick={() => setImporting(true)}
              icon={<UploadOutlined />}
            >
              Bulk Import Products
            </Button>
          </Space>
        }
      >
        <Table
          rowKey="id"
          columns={columns}
          dataSource={items}
          loading={loading}
          pagination={false}
        />
      </Card>
      <ProductModal
        mode="create"
        open={creating}
        product={null}
        onClose={() => setCreating(false)}
        onSuccess={() => {
          setCreating(false);
          void loadData();
        }}
      />
      <ProductModal
        mode="edit"
        open={Boolean(editingProduct)}
        product={editingProduct}
        onClose={() => setEditingProduct(null)}
        onSuccess={() => {
          setEditingProduct(null);
          void loadData();
        }}
      />
      <BulkImportProductsModal
        open={importing}
        onClose={() => setImporting(false)}
        onSuccess={() => {
          setImporting(false);
          void loadData();
        }}
      />
    </Space>
  );
}

function ProductModal(props: {
  mode: "create" | "edit";
  open: boolean;
  product: Product | null;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [form] = Form.useForm();
  const [submitting, setSubmitting] = useState(false);
  const { message } = AntApp.useApp();

  useEffect(() => {
    if (props.product) {
      form.setFieldsValue({
        ...props.product,
        is_on_sale: props.product.status === 1,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({ is_on_sale: true, stock: 0, status: 1 });
    }
  }, [props.product, props.open]);

  return (
    <Modal
      open={props.open}
      title={props.mode === "create" ? "Create Product" : "Edit Product"}
      onCancel={props.onClose}
      onOk={() => form.submit()}
      okText={props.mode === "create" ? "Create" : "Save"}
      confirmLoading={submitting}
    >
      <Form
        form={form}
        layout="vertical"
        onFinish={async (values) => {
          setSubmitting(true);
          try {
            const payload = {
              ...values,
              status: values.is_on_sale ? 1 : 0,
            };
            delete payload.is_on_sale;
            if (props.mode === "create") {
              await createProduct(payload);
              message.success("Product created.");
            } else if (props.product) {
              await updateProduct(props.product.id, payload);
              message.success("Product updated.");
            }
            form.resetFields();
            props.onSuccess();
          } catch (error) {
            message.error(props.mode === "create" ? "Failed to create product." : "Failed to update product.");
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Form.Item label="Product Name" name="product_name" rules={[{ required: true, message: "Please enter a product name." }]}>
          <Input />
        </Form.Item>
        <Form.Item
          label="Product Code"
          name="product_code"
          rules={[{ required: props.mode === "create", message: "Please enter a product code." }]}
        >
          <Input disabled={props.mode === "edit"} />
        </Form.Item>
        <Form.Item label="Price" name="price" rules={[{ required: true, message: "Please enter a price." }]}>
          <InputNumber min={0.01} step={0.01} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item label="Stock" name="stock" rules={[{ required: true, message: "Please enter stock." }]}>
          <InputNumber min={0} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item label="Category" name="category">
          <Input />
        </Form.Item>
        <Form.Item label="On Sale" name="is_on_sale" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}
