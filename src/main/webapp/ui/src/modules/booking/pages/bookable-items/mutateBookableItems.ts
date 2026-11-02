import * as v from "valibot";
import { parseApiV2Problem } from "@/modules/booking/domain/booking";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import { serializeRsqlExpression } from "@/modules/common/table-list/rsql";
import { fetchWithUiToken } from "@/modules/common/utils/fetchWithUiToken";
import type { BookableItemsBulkAction } from "./bookableItemLifecycleHelpers";
import type { BookingConfigurationRow } from "./bookingConfiguration";

const maximumBookableItemsSelection = 100;
export async function mutateBookableItems(
  action: BookableItemsBulkAction,
  selectedRowIds: readonly string[],
  token: string,
): Promise<readonly { id: number }[] | null> {
  if (selectedRowIds.length === 0) throw new Error("A bulk booking action requires at least one row ID");
  if (selectedRowIds.length > maximumBookableItemsSelection) {
    throw new Error(`A bulk booking action cannot contain more than ${maximumBookableItemsSelection} row IDs`);
  }

  const where = serializeRsqlExpression<BookingConfigurationRow>({
    kind: "comparison",
    field: "id",
    operator: "in",
    value: selectedRowIds,
  });
  const search = new URLSearchParams({ where });
  const isDelete = action === "archive";
  const response = await fetchWithUiToken(`/api/v2/booking-configurations?${search}`, {
    method: isDelete ? "DELETE" : "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      ...(isDelete ? {} : { "Content-Type": "application/json" }),
      "X-Requested-With": "XMLHttpRequest",
    },
    ...(isDelete ? {} : { body: JSON.stringify({ enabled: action === "enable" }) }),
  });
  if (!response.ok) throw await parseApiV2Problem(response);
  // The server has committed a successful response even if its receipt cannot be read.
  try {
    return parseOrThrow(
      v.object({ docs: v.array(v.object({ id: v.pipe(v.number(), v.integer(), v.minValue(1)) })) }),
      await response.json(),
    ).docs;
  } catch {
    return null;
  }
}
