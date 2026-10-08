import { when } from "mobx";
import axios, {
  type AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type AxiosResponse,
} from "@/common/axios";
import i18n from "@/modules/common/i18n";
import { mkAlert } from "../stores/contexts/Alert";
import getRootStore from "../stores/stores/getRootStore";
import JwtService from "./JwtService";

type JSON = unknown;

// Lazily memoised: i18n initialises asynchronously, so the strings can't be
// resolved at module load. Caching the single alert keeps a stable id so the
// later removeAlert calls actually match the alert that was added.
let authWarningToast: ReturnType<typeof mkAlert> | undefined;
const getAuthWarningToast = (): ReturnType<typeof mkAlert> =>
  (authWarningToast ??= mkAlert({
    variant: "warning",
    title: i18n.t("apiAuthentication.warningTitle"),
    message: i18n.t("apiAuthentication.warningMessage"),
    isInfinite: true,
  }));

// Axios wrapper for making requests to RSpace APIs
class ApiServiceBase {
  api: AxiosInstance;

  constructor(baseUrl: string) {
    const api = axios.create({
      baseURL: baseUrl,
      timeout: 360000,
    });
    this.api = api;

    api.interceptors.response.use(
      (response) => response,
      (...args) => this.on401Retry(...args),
    );
    // Global because in some places we use axios instead of this.api for requesting /api/v1/
    axios.interceptors.response.use(
      (response) => response,
      (...args) => this.on401Retry(...args),
    );
  }

  async on401Retry(
    error: AxiosError & {
      config?: NonNullable<AxiosError["config"]> & { authRetried?: boolean };
    },
  ): Promise<unknown> {
    if (
      error.config &&
      error.response &&
      error.response.status === 401 &&
      !error.config.authRetried &&
      !error.config.data
    ) {
      const requestUrl = error.request?.responseURL ?? error.config.url ?? "";
      if (/\/(?:userform\/ajax\/inventoryOauthToken|api\/v2\/oauth\/tokens)(?:[/?#]|$)/.test(requestUrl)) {
        // Token minting is already the authentication attempt; retrying it would recurse.
        getRootStore().uiStore.removeAlert(getAuthWarningToast());
        getRootStore().uiStore.addAlert(getAuthWarningToast());
        throw error;
      }
      await getRootStore().authStore.authenticate();
      getRootStore().uiStore.removeAlert(getAuthWarningToast());
      error.config.baseURL ??= "";
      error.config.authRetried = true;

      error.config.headers.Authorization = `Bearer ${JwtService.getToken() ?? ""}`;

      if (getRootStore().authStore.isAuthenticated) {
        return this.api(error.config);
      }
    }
    throw error;
  }

  setAuthorizationHeader() {
    // for when you use the bearer token
    this.api.defaults.headers.common = {
      Authorization: `Bearer ${JwtService.getToken() ?? ""}`,
    };

    // for when using the api key
    // this.api.defaults.headers.common.apiKey = getToken();
  }

  query<T>(resource: string, params: URLSearchParams, isBlob: boolean = false): Promise<AxiosResponse<T>> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() =>
      this.api.get<T>(resource, {
        params,
        responseType: isBlob ? "blob" : "json",
      }),
    );
  }

  get<T>(resource: string, slug: string | number = ""): Promise<AxiosResponse<T>> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.get<T>(`${resource}/${slug}`);
    });
  }

  post<T>(
    resource: string,
    params: object | FormData,
    config?: AxiosRequestConfig<unknown>,
  ): Promise<AxiosResponse<T>> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.post<T>(`${resource}`, params, config);
    });
  }

  update<T>(
    resource: string,
    slug: string | number,
    params: JSON,
    config?: AxiosRequestConfig<unknown>,
  ): Promise<AxiosResponse<T>> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.put<T>(`${resource}/${slug}`, params, config);
    });
  }

  put<T>(resource: string, params: JSON): Promise<AxiosResponse<T>> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.put(`${resource}`, params);
    });
  }

  delete<T>(resource: string, slug: string | number): Promise<AxiosResponse<T>> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.delete(`${resource}/${slug ? slug : ""}`);
    });
  }
}

export default ApiServiceBase;
