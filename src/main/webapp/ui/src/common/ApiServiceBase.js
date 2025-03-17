// @flow

import axios, {
  type Axios,
  type AxiosPromise,
  type AxiosRequestConfig,
} from "@/common/axios";
import { when } from "mobx";
import getRootStore from "../stores/stores/RootStore";
import JwtService from "./JwtService";
import { type URL } from "../util/types";
import { sleep } from "../util/Util";
import { mkAlert } from "../stores/contexts/Alert";

type JSON =
  | {}
  | { [string]: string | number | boolean | null | JSON | Array<JSON> };

export type _LINK = {|
  link: URL,
  rel: string,
|};

const toast = mkAlert({
  variant: "warning",
  title: "Could not authenticate via API",
  message:
    "Some functionality will not be available until an authenticated session can be established. Please try logging in again in another window. If the issue persists, please contact support.",
  isInfinite: true,
});

// Axios wrapper for making requests to RSpace APIs
class ApiServiceBase {
  api: Axios;

  constructor(baseUrl: string) {
    this.api = axios.create({
      baseURL: baseUrl,
      timeout: 360000,
    });

    this.api.interceptors.response.use(null, (...args) =>
      this.on401Retry(...args)
    );
    // Global because in some places we use axios instead of this.api for requesting /api/v1/
    axios.interceptors.response.use(null, (...args) =>
      this.on401Retry(...args)
    );
  }

  async on401Retry(error: any): mixed {
    if (
      error.config &&
      error.response &&
      error.response.status === 401 &&
      !(error.config.data && !error.config.data.__isRetryRequest)
    ) {
      if (
        /\/userform\/ajax\/inventoryOauthToken/.test(error.request.responseURL)
      ) {
        /*
         * Prevent the immediate infinite loop caused by a 401 on
         * /inventoryOauthToken resulting in another call to authenticate(),
         * which calls /inventoryOauthToken. We keep trying in case the user
         * logs-in in another window but only once every 10 seconds so as not
         * to cause too much overhead on both client and server.
         */
        getRootStore().uiStore.removeAlert(toast);
        getRootStore().uiStore.addAlert(toast);
        await sleep(10 * 1000);
      }
      await getRootStore().authStore.authenticate();
      getRootStore().uiStore.removeAlert(toast);
      // Axios constructs url as baseURL + url(resource) and leaves the baseURL in config,
      // which then results in baseURL + baseURL + url(resource) as the url for axios request below.
      error.config.baseURL = "";
      error.config.data = { __isRetryRequest: true };

      error.config.headers.Authorization = "Bearer " + JwtService.getToken();

      if (getRootStore().authStore.isAuthenticated) {
        return this.api(error.config);
      }
    }
    throw error;
  }

  setAuthorizationHeader() {
    // for when you use the bearer token
    this.api.defaults.headers.common = {
      Authorization: "Bearer " + JwtService.getToken(),
    };

    // for when using the api key
    // this.api.defaults.headers.common.apiKey = getToken();
  }

  query<T, U>(
    resource: string,
    params: URLSearchParams,
    isBlob: boolean = false
  ): AxiosPromise<T, U> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() =>
      this.api.get(resource, {
        params,
        responseType: isBlob ? "blob" : "json",
      })
    );
  }

  get<T, U>(resource: string, slug: string | number = ""): AxiosPromise<T, U> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.get(`${resource}/${slug}`);
    });
  }

  post<T, U>(
    resource: string,
    params: JSON | FormData,
    config?: AxiosRequestConfig<any, any>
  ): AxiosPromise<T, U> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.post(`${resource}`, params, config);
    });
  }

  update<T, U>(
    resource: string,
    slug: string | number,
    params: JSON,
    config?: AxiosRequestConfig<any, any>
  ): AxiosPromise<T, U> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.put(`${resource}/${slug}`, params, config);
    });
  }

  put<T, U>(resource: string, params: JSON): AxiosPromise<T, U> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.put(`${resource}`, params);
    });
  }

  delete<T, U>(resource: string, slug: string | number): AxiosPromise<T, U> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.delete(`${resource}/${slug ? slug : ""}`);
    });
  }
}

export default ApiServiceBase;
