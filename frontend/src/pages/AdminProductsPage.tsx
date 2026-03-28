import { App as AntApp, Button, Card, Form, Input, InputNumber, Modal, Space, Switch, Table, Tag } from "antd";
import type { ColumnsType } from "antd/es/table";
import { useEffect, useState } from "react";
import { createProduct, listProducts, updateProduct, updateProductStatus, updateProductStock } from "../api/services";
import type { Product } from "../api/types";
import { PageHeader } from "../components/PageHeader";

export function AdminProductsPage() {
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
      message.error("商品管理数据加载失败");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void loadData();
  }, []);

  const columns: ColumnsType<Product> = [
    { title: "商品编码", dataIndex: "product_code" },
    { title: "商品名称", dataIndex: "product_name" },
    { title: "价格", dataIndex: "price", render: (value: number) => `¥${value}` },
    { title: "库存", dataIndex: "stock" },
    { title: "分类", dataIndex: "category", render: (value: string | null) => value || "-" },
    {
      title: "状态",
      dataIndex: "status",
      render: (value: number, record) => (
        <Space>
          {value === 1 ? <Tag color="green">上架</Tag> : <Tag>下架</Tag>}
          <Switch
            checked={value === 1}
            onChange={async (checked) => {
              try {
                await updateProductStatus(record.id, checked ? 1 : 0);
                message.success("商品状态已更新");
                void loadData();
              } catch (error) {
                message.error("状态更新失败");
              }
            }}
          />
        </Space>
      ),
    },
    {
      title: "操作",
      key: "action",
      render: (_, record) => (
        <Space>
          <Button onClick={() => setEditingProduct(record)}>编辑</Button>
          <Button
            onClick={async () => {
              try {
                await updateProductStock(record.id, record.stock + 10);
                message.success("已为商品补充 10 件库存");
                void loadData();
              } catch (error) {
                message.error("库存更新失败");
              }
            }}
          >
            +10 库存
          </Button>
        </Space>
      ),
    },
  ];

  return (
    <Space direction="vertical" size={20} style={{ width: "100%" }}>
      <PageHeader title="商品管理" subtitle="管理员可以新增商品、编辑核心字段、切换上下架，并快速调整库存。" />
      <Card bordered={false} extra={<Button type="primary" onClick={() => setCreating(true)}>新增商品</Button>}>
        <Table rowKey="id" columns={columns} dataSource={items} loading={loading} pagination={false} />
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
      title={props.mode === "create" ? "新增商品" : "编辑商品"}
      onCancel={props.onClose}
      onOk={() => form.submit()}
      okText={props.mode === "create" ? "创建" : "保存"}
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
              message.success("商品已创建");
            } else if (props.product) {
              await updateProduct(props.product.id, payload);
              message.success("商品已更新");
            }
            form.resetFields();
            props.onSuccess();
          } catch (error) {
            message.error(props.mode === "create" ? "创建失败" : "更新失败");
          } finally {
            setSubmitting(false);
          }
        }}
      >
        <Form.Item label="商品名称" name="product_name" rules={[{ required: true, message: "请输入商品名称" }]}>
          <Input />
        </Form.Item>
        <Form.Item
          label="商品编码"
          name="product_code"
          rules={[{ required: props.mode === "create", message: "请输入商品编码" }]}
        >
          <Input disabled={props.mode === "edit"} />
        </Form.Item>
        <Form.Item label="价格" name="price" rules={[{ required: true, message: "请输入价格" }]}>
          <InputNumber min={0.01} step={0.01} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item label="库存" name="stock" rules={[{ required: true, message: "请输入库存" }]}>
          <InputNumber min={0} style={{ width: "100%" }} />
        </Form.Item>
        <Form.Item label="分类" name="category">
          <Input />
        </Form.Item>
        <Form.Item label="是否上架" name="is_on_sale" valuePropName="checked">
          <Switch />
        </Form.Item>
      </Form>
    </Modal>
  );
}
