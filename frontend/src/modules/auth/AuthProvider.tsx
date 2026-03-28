import { App as AntApp } from "antd";
import { createContext, useContext, useEffect, useState } from "react";
import type { ReactNode } from "react";
import { apiClient, loadToken } from "../../api/client";
import { fetchMe } from "../../api/services";
import type { UserProfile } from "../../api/types";

interface AuthContextValue {
  user: UserProfile | null;
  ready: boolean;
  refreshUser: () => Promise<UserProfile | null>;
  logout: () => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider(props: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfile | null>(null);
  const [ready, setReady] = useState(false);
  const { message } = AntApp.useApp();

  async function refreshUser() {
    try {
      const currentUser = await fetchMe();
      setUser(currentUser);
      return currentUser;
    } catch (error) {
      apiClient.clearToken();
      setUser(null);
      return null;
    } finally {
      setReady(true);
    }
  }

  function logout() {
    apiClient.clearToken();
    setUser(null);
  }

  useEffect(() => {
    if (!loadToken()) {
      setReady(true);
      return;
    }

    void refreshUser().catch(() => {
      message.error("登录态已失效，请重新登录");
    });
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user,
        ready,
        refreshUser,
        logout,
      }}
    >
      {props.children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return value;
}
