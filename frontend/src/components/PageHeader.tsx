import { Typography } from "antd";

export function PageHeader(props: { title: string; subtitle: string }) {
  return (
    <div className="page-header">
      <Typography.Text className="page-kicker">Phase 1 MVP</Typography.Text>
      <Typography.Title level={2} className="page-title">
        {props.title}
      </Typography.Title>
      <Typography.Paragraph className="page-subtitle">{props.subtitle}</Typography.Paragraph>
    </div>
  );
}
