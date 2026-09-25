import { when } from "mobx";
import axios, { type AxiosInstance, type AxiosRequestConfig, type AxiosResponse } from "@/common/axios";
import getRootStore from "../stores/stores/getRootStore";
import JwtService from "./JwtService";

type JSON = unknown;

// Axios wrapper for making requests to RSpace APIs
class ApiServiceBase {
  api: AxiosInstance;

  constructor(baseUrl: string) {
    const api = axios.create({
      baseURL: baseUrl,
      timeout: 360000,
    });
    this.api = api;
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
