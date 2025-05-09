import ApiServiceBase from "./ApiServiceBase";
import { when } from "mobx";
import getRootStore from "../stores/stores/RootStore";
import { type ApiRecordType } from "../stores/definitions/InventoryRecord";
import { type Id } from "../stores/definitions/BaseRecord";

export type BulkEndpointRecordSerialisation = {
  id: Id;
  type: ApiRecordType;
};

class InvApiService extends ApiServiceBase {
  bulk<T>(
    records: ReadonlyArray<BulkEndpointRecordSerialisation>,
    operationType:
      | "CREATE"
      | "UPDATE"
      | "DELETE"
      | "RESTORE"
      | "DUPLICATE"
      | "MOVE"
      | "CHANGE_OWNER",
    rollbackOnError: boolean
  ): Promise<Axios.AxiosXHR<T>> {
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
