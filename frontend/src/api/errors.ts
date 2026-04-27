import axios from "axios";

type ApiErrorDetail = { msg?: string } | string;

interface ApiErrorPayload {
  message?: string;
  detail?: ApiErrorDetail | ApiErrorDetail[];
}

export function getApiErrorStatus(error: unknown): number | null {
  if (!axios.isAxiosError(error)) {
    return null;
  }
  return error.response?.status ?? null;
}

export function getApiErrorMessage(error: unknown): string | null {
  if (!axios.isAxiosError<ApiErrorPayload>(error)) {
    return null;
  }

  const payload = error.response?.data;
  if (!payload) {
    return error.message || null;
  }

  if (typeof payload.message === "string" && payload.message.trim()) {
    return payload.message;
  }

  if (typeof payload.detail === "string" && payload.detail.trim()) {
    return payload.detail;
  }

  if (Array.isArray(payload.detail)) {
    const first = payload.detail.find(
      (item): item is { msg?: string } => typeof item === "object" && item !== null,
    );
    if (first && typeof first.msg === "string" && first.msg.trim()) {
      return first.msg;
    }
  }

  return error.message || null;
}
