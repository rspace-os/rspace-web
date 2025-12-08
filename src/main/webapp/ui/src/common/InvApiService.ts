import { when } from "mobx";
import type { AxiosResponse } from "@/common/axios";
import type { Id } from "../stores/definitions/BaseRecord";
import type { ApiRecordType } from "../stores/definitions/InventoryRecord";
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
