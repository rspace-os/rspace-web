import React from "react";
import { useQueries } from "@tanstack/react-query";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";

const INVENTORY_API_BASE_URL = "/api/inventory/v1";
const SUBSAMPLE_GLOBAL_ID_PATTERN = /^SS(?<id>\d+)$/;

const InventoryQuantitySchema = v.object({
  numericValue: v.number(),
  unitId: v.number(),
});

const InventorySubSampleSchema = v.objectWithRest(
  {
    id: v.number(),
    globalId: v.string(),
    quantity: v.nullable(InventoryQuantitySchema),
  },
  v.unknown(),
);

export type InventoryQuantity = v.InferOutput<typeof InventoryQuantitySchema>;
export type InventorySubSample = v.InferOutput<typeof InventorySubSampleSchema>;

export type InventoryQuantityQueryResult =
  | {
      status: "available";
      quantity: InventoryQuantity | null;
    }
  | {
      status: "error";
    };

export const inventoryQueryKeys = {
  all: ["rspace.api.inventory"] as const,
  subSampleQuantity: (inventoryItemGlobalId: string) =>
    [
      ...inventoryQueryKeys.all,
      "subSampleQuantity",
      inventoryItemGlobalId,
    ] as const,
};

function parseSubSampleIdFromGlobalId(inventoryItemGlobalId: string): number | null {
  const matched = inventoryItemGlobalId.match(SUBSAMPLE_GLOBAL_ID_PATTERN);
  if (!matched?.groups?.id) {
    return null;
  }

  const parsedId = Number(matched.groups.id);
  return Number.isInteger(parsedId) ? parsedId : null;
}

function toInventoryQuantityQueryResult(
  subSample: InventorySubSample,
): InventoryQuantityQueryResult {
  return {
    status: "available",
    quantity: subSample.quantity,
  };
}

export async function getSubSampleQuantity({
  inventoryItemGlobalId,
  token,
}: {
  inventoryItemGlobalId: string;
  token: string;
}): Promise<InventoryQuantityQueryResult> {
  const subSampleId = parseSubSampleIdFromGlobalId(inventoryItemGlobalId);
  if (subSampleId === null) {
    return {
      status: "error",
    };
  }

  const response = await fetch(`${INVENTORY_API_BASE_URL}/subSamples/${subSampleId}`, {
    method: "GET",
    headers: {
      "X-Requested-With": "XMLHttpRequest",
      Authorization: `Bearer ${token}`,
    },
  });

  if (!response.ok) {
    return {
      status: "error",
    };
  }

  return toInventoryQuantityQueryResult(
    parseOrThrow(InventorySubSampleSchema, await response.json()),
  );
}

export function useSubSampleQuantitiesQuery({
  inventoryItemGlobalIds,
  token,
}: {
  inventoryItemGlobalIds: ReadonlyArray<string>;
  token: string;
}): ReadonlyMap<
  string,
  InventoryQuantityQueryResult
> {
  const uniqueInventoryItemGlobalIds = React.useMemo(
    () => Array.from(new Set(inventoryItemGlobalIds.filter(Boolean))),
    [inventoryItemGlobalIds],
  );

  const queryResults = useQueries({
    queries: uniqueInventoryItemGlobalIds.map((inventoryItemGlobalId) => ({
      queryKey: inventoryQueryKeys.subSampleQuantity(inventoryItemGlobalId),
      queryFn: async () =>
        getSubSampleQuantity({
          inventoryItemGlobalId,
          token,
        }),
      enabled: Boolean(token),
      retry: false,
      staleTime: 60_000,
    })),
  });

  return React.useMemo(
    () =>
      new Map(
        uniqueInventoryItemGlobalIds.flatMap((inventoryItemGlobalId, index) => {
          const result = queryResults[index]?.data;
          return result ? [[inventoryItemGlobalId, result] as const] : [];
        }),
      ),
    [queryResults, uniqueInventoryItemGlobalIds],
  );
}
