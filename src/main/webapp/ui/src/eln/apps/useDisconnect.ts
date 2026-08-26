import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

const ONE_MINUTE_IN_MS = 60 * 1000;

/**
 * Disconnects the current user from an app that stores its credentials
 * server-side, by DELETEing `{basePath}/connect`.
 *
 * @param basePath the app's controller path, e.g. "/apps/omero"
 * @param appName the app's display name, already translated, used in the alerts
 */
export function useDisconnectEndpoint(
  basePath: string,
  appName: string,
): {
  /** Resolves true when the stored connection was actually deleted. */
  disconnect: () => Promise<boolean>;
} {
  const { t } = useTranslation("apps");
  const { addAlert } = React.useContext(AlertContext);

  const disconnect = async (): Promise<boolean> => {
    try {
      await axios.delete<void>(`${basePath}/connect`, { timeout: ONE_MINUTE_IN_MS });
      addAlert(
        mkAlert({
          variant: "success",
          message: t("disconnect.success", { appName }),
        }),
      );
      return true;
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          message: t("disconnect.error", { appName }),
        }),
      );
      /*
       * The stored credentials still exist, so the caller must not present the
       * user as disconnected. Reported rather than thrown because every call
       * site has already surfaced the failure through the alert above.
       */
      return false;
    }
  };

  return { disconnect };
}
