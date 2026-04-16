import { UploadOutlined } from "@ant-design/icons";
import { App as AntApp, Button, Modal, Upload } from "antd";
import { useState } from "react";
import type { UploadFile } from "antd/es/upload/interface";
import { importProducts } from "../api/services";

export function BulkImportProductsModal(props: {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
}) {
  const [fileList, setFileList] = useState<UploadFile[]>([]);
  const [submitting, setSubmitting] = useState(false);
  const { message } = AntApp.useApp();

  async function handleImport() {
    const first = fileList[0];
    const file = first?.originFileObj ?? (first as unknown as File | undefined);
    if (!file) {
      message.warning("Please choose a file first.");
      return;
    }

    setSubmitting(true);
    try {
      await importProducts(file);
      message.success("Products imported successfully.");
      setFileList([]);
      props.onSuccess();
    } catch {
      message.error("Failed to import products.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Modal
      open={props.open}
      title="Bulk Import Products"
      onCancel={props.onClose}
      onOk={handleImport}
      okText="Import"
      confirmLoading={submitting}
    >
      <Upload
        maxCount={1}
        accept=".csv,.xlsx,.xls"
        beforeUpload={(file) => {
          setFileList([{
            uid: file.uid,
            name: file.name,
            status: "done",
            originFileObj: file,
          }]);
          return false;
        }}
        fileList={fileList}
        onRemove={() => setFileList([])}
      >
        <Button icon={<UploadOutlined />}>Choose File</Button>
      </Upload>
    </Modal>
  );
}
