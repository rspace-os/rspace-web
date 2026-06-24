import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

export function useDmpOnlineEndpoint(): {
  disconnect: () => Promise<void>;
} {
  const { t } = useTranslation("apps");
  const { addAlert } = React.useContext(AlertContext);
  const appName = "DMPOnline";
  const api = axios.create({
    baseURL: "/apps/dmponline",
    timeout: ONE_MINUTE_IN_MS,
  });

  const disconnect = async (): Promise<void> => {
    try {
      await api.delete<void>("/connect");
      addAlert(
        mkAlert({
          variant: "success",
          message: t("disconnect.success", { appName }),
        }),
      );
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          message: t("disconnect.error", { appName }),
        }),
      );
    }
  };

  return { disconnect };
}
