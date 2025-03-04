//@flow strict

import React from "react";
import axios from "@/common/axios";
import AlertContext, { mkAlert } from "../../stores/contexts/Alert";

const ONE_MINUTE_IN_MS = 60 * 60 * 1000;

export function useNextcloudEndpoint(): {|
  disconnect: () => Promise<void>,
|} {
  const { addAlert } = React.useContext(AlertContext);
  const api = axios.create({
    baseURL: "/apps/nextcloud",
    timeout: ONE_MINUTE_IN_MS,
  });

  const disconnect = async (): Promise<void> => {
    try {
      await api.delete<void>("/connect");
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully disconnected from NextCloud.",
        })
      );
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          message: "Could not disconnect from NextCloud.",
        })
      );
    }
  };

  return { disconnect };
}
