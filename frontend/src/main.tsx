import React from "react";
import ReactDOM from "react-dom/client";
import { App, ConfigProvider, Typography } from "antd";

const Root = () => (
  <ConfigProvider
    theme={{
      token: {
        colorPrimary: "#1768ac",
      },
    }}
  >
    <App>
      <div style={{ padding: 32 }}>
        <Typography.Title>Modern User-Product-Order System</Typography.Title>
        <Typography.Paragraph>
          Frontend Phase 1 pages will be added after the backend core loop is in place.
        </Typography.Paragraph>
      </div>
    </App>
  </ConfigProvider>
);

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <Root />
  </React.StrictMode>,
);
