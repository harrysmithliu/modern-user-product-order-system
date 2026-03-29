import type { ReactElement } from "react";
import { Result } from "antd";
import { Navigate } from "react-router-dom";
import { useAuth } from "../modules/auth/AuthProvider";

export function ProtectedRoute(props: { children: ReactElement; adminOnly?: boolean }) {
  const { user } = useAuth();

  if (!user) {
    return <Navigate to="/login" replace />;
  }

  if (props.adminOnly && user.role !== "ADMIN") {
    return <Result status="403" title="403" subTitle="This account does not have permission to view this page." />;
  }

  return props.children;
}
