/** Import Axios through this module to include browser headers and UI token recovery. */

import axios, { type AxiosInstance, type InternalAxiosRequestConfig } from "axios";
import { getUiAuthorization, isUiApiUrl, recoverUiToken } from "@/modules/common/utils/fetchWithUiToken";

// Prevent API requests from becoming the server's post-login redirect target.
axios.defaults.headers.common["X-Requested-With"] = "XMLHttpRequest";

function installUiTokenRecovery(client: AxiosInstance): AxiosInstance {
  client.interceptors.request.use(
    (config) => {
      const authorization = config.headers.get("Authorization");
      if (
        isUiApiUrl(client.getUri(config)) &&
        typeof authorization === "string" &&
        authorization.startsWith("Bearer ")
      ) {
        config.headers.set("Authorization", getUiAuthorization(authorization));
      }
      return config;
    },
    undefined,
    // This interceptor never awaits, so let Axios send requests immediately instead of a microtask later.
    { synchronous: true },
  );
  client.interceptors.response.use(undefined, async (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401 && error.config) {
      const config: InternalAxiosRequestConfig & { uiTokenRetried?: boolean } = error.config;
      const authorization = config.headers.get("Authorization");
      const url = new URL(client.getUri(config), globalThis.location.href);
      if (
        !config.uiTokenRetried &&
        isUiApiUrl(url.href) &&
        typeof authorization === "string" &&
        authorization.startsWith("Bearer ")
      ) {
        const token = await recoverUiToken(
          authorization,
          url.pathname.replace(/\/$/, "") === "/api/v1/userDetails/whoami",
        );
        if (token && config.method?.toUpperCase() === "GET") {
          config.uiTokenRetried = true;
          config.headers.set("Authorization", `Bearer ${token}`);
          return client.request(config);
        }
      }
    }
    throw error;
  });
  return client;
}

installUiTokenRecovery(axios);
// Axios instances do not inherit the default instance's interceptors.
const create = axios.create.bind(axios);
axios.create = (config) => installUiTokenRecovery(create(config));

export default axios;

export * from "axios";
