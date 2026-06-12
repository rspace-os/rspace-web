import { when } from "mobx";
// biome-ignore lint/style/useImportType: initial biome migration
import { AxiosResponse } from "@/common/axios";
// biome-ignore lint/style/useImportType: initial biome migration
import { type Id } from "../stores/definitions/BaseRecord";
// biome-ignore lint/style/useImportType: initial biome migration
import { type ApiRecordType } from "../stores/definitions/InventoryRecord";
import getRootStore from "../stores/stores/RootStore";
import ApiServiceBase from "./ApiServiceBase";

export type BulkEndpointRecordSerialisation = {
  id: Id;
  type: ApiRecordType;
};

class InvApiService extends ApiServiceBase {
  bulk<T>(
    records: ReadonlyArray<BulkEndpointRecordSerialisation>,
    operationType: "CREATE" | "UPDATE" | "DELETE" | "RESTORE" | "DUPLICATE" | "MOVE" | "CHANGE_OWNER",
    rollbackOnError: boolean,
  ): Promise<AxiosResponse<T>> {
    const params = {
      operationType,
      records,
      rollbackOnError,
    };

    return when(() => !getRootStore().authStore.isSynchronizing).then(() => {
      return this.api.post<T>(`/bulk`, params);
    });
  }
}

const invApiService: InvApiService = new InvApiService("/api/inventory/v1/");

export default invApiService;
