// @flow

import axios, {
  type Axios,
  type AxiosPromise,
  type AxiosXHRConfigBase,
} from "axios";
import { when } from "mobx";
import getRootStore from "../stores/stores/RootStore";
import JwtService from "./JwtService";
import { type URL } from "../util/types";

type JSON =
  | {}
  | { [string]: string | number | boolean | null | JSON | Array<JSON> };

export type _LINK = {|
  link: URL,
  rel: string,
|};

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

  on401Retry(error: any): mixed {
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
         * Prevent the infinite loop caused by a 401 on /inventoryOauthToken
         * resulting in another call to authenticate(), which calls
         * /inventoryOauthToken, by redirecting the user to the login screen as
         * they are probably logged out.
         */
        window.location = "/login";
        return;
      }
      return getRootStore()
        .authStore.authenticate()
        .then(() => {
          // Axios constructs url as baseURL + url(resource) and leaves the baseURL in config,
          // which then results in baseURL + baseURL + url(resource) as the url for axios request below.
          error.config.baseURL = "";
          error.config.data = { __isRetryRequest: true };

          error.config.headers.Authorization =
            "Bearer " + JwtService.getToken();

          if (getRootStore().authStore.isAuthenticated) {
            return this.api(error.config);
          }
        });
    }
    return Promise.reject(error);
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
    config?: AxiosXHRConfigBase<any, any>
  ): AxiosPromise<T, U> {
    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.post(`${resource}`, params, config);
    });
  }

  update<T, U>(
    resource: string,
    slug: string | number,
    params: JSON,
    config?: AxiosXHRConfigBase<any, any>
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
