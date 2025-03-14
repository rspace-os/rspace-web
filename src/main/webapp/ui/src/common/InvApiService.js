// @flow

import ApiServiceBase from "./ApiServiceBase";
import { type AxiosPromise } from "@/common/axios";
import { when } from "mobx";
import getRootStore from "../stores/stores/RootStore";
import { type ApiRecordType } from "../stores/definitions/InventoryRecord";
import { type Id } from "../stores/definitions/BaseRecord";

export type BulkEndpointRecordSerialisation = {
  id: Id,
  type: ApiRecordType,
  ...
};

class InvApiService extends ApiServiceBase {
  bulk<T, U>(
    records: $ReadOnlyArray<BulkEndpointRecordSerialisation>,
    operationType:
      | "CREATE"
      | "UPDATE"
      | "DELETE"
      | "RESTORE"
      | "DUPLICATE"
      | "MOVE"
      | "CHANGE_OWNER",
    rollbackOnError: boolean
  ): AxiosPromise<T, U> {
    let params = {
      operationType: operationType,
      records: records,
      rollbackOnError: rollbackOnError,
    };

    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.post(`/bulk`, params);
    });
  }
}

const invApiService: InvApiService = new InvApiService("/api/inventory/v1/");

export default invApiService;
