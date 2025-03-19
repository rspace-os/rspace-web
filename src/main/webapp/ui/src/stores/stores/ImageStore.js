// @flow

import axios from "@/common/axios";
import type { RootStore } from "./RootStore";
import JwtService from "../../common/JwtService";
import { type URL as URLType, type BlobUrl } from "../../util/types";

/*
 * A single, centralised image store for fetching and processing images,
 * thereby ensuring the efficient usage of memory and network resources.
 */
export default class ImageStore {
  rootStore: RootStore;
  cache: Map<URLType, BlobUrl>;
  waiting: Map<URLType, Promise<mixed>>;

  constructor(rootStore: RootStore) {
    this.rootStore = rootStore;
    this.cache = new Map();
    this.waiting = new Map();
  }

  async fetchImage(url: URLType): Promise<BlobUrl> {
    const img = this.cache.get(url);
    if (img) return img;

    const waitingPromise = this.waiting.get(url);
    if (waitingPromise) {
      await waitingPromise;
      return this.fetchImage(url);
    }

    const promise = axios<void, Blob>({
      method: "get",
      url,
      responseType: "blob",
      headers: {
        Authorization: `Bearer ${JwtService.getToken() ?? ""}`,
      },
    });
    this.waiting.set(url, promise);
    const { data } = await promise;
    this.waiting.delete(url);

    const objectUrl = URL.createObjectURL(data);
    this.cache.set(url, objectUrl);
    return objectUrl;
  }
}
