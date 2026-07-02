import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

export function useDigitalCommonsDataEndpoint(): {
  disconnect: () => Promise<void>;
} {
  const { addAlert } = React.useContext(AlertContext);
  const { t } = useTranslation("apps");
  const api = axios.create({
    baseURL: "/apps/digitalcommonsdata",
    timeout: ONE_MINUTE_IN_MS,
  });

  const disconnect = async (): Promise<void> => {
    try {
      await api.delete<void>("/connect");
      const appName = t("integrations.digitalCommonsData.name");
      addAlert(
        mkAlert({
          variant: "success",
          message: t("disconnect.success", { appName }),
        }),
      );
    } catch (e) {
      console.error(e);
      const appName = t("integrations.digitalCommonsData.name");
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
