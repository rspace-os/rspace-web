import React from "react";
import axios from "@/common/axios";
import i18n from "@/modules/common/i18n";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

export function useProtocolsioEndpoint(): {
  disconnect: () => Promise<void>;
} {
  const { addAlert } = React.useContext(AlertContext);
  const api = axios.create({
    baseURL: "/apps/protocolsio",
    timeout: ONE_MINUTE_IN_MS,
  });

  const disconnect = async (): Promise<void> => {
    try {
      await api.delete<void>("/connect");
      const appName = i18n.t("integrations.protocolsIo.name", { ns: "apps" });
      addAlert(
        mkAlert({
          variant: "success",
          message: i18n.t("disconnect.success", { ns: "apps", appName }),
        }),
      );
    } catch (e) {
      console.error(e);
      const appName = i18n.t("integrations.protocolsIo.name", { ns: "apps" });
      addAlert(
        mkAlert({
          variant: "error",
          message: i18n.t("disconnect.error", { ns: "apps", appName }),
        }),
      );
    }
  };

  return { disconnect };
}
