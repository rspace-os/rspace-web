import React from "react";
import { useQueries } from "@tanstack/react-query";
import * as v from "valibot";
import { parseOrThrow } from "@/modules/common/queries/parseOrThrow";
import {
  resolveToken,
  type TokenParams,
} from "@/modules/common/utils/auth";

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

type UseSubSampleQuantitiesQueryTokenParams =
  | { token: string; getToken?: never }
  | { token?: never; getToken: NonNullable<TokenParams["getToken"]> };

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

async function readJsonSafely(response: Response): Promise<unknown> {
  try {
    return await response.json();
  } catch {
    return null;
  }
}

function toInventoryError(data: unknown, fallbackMessage: string): Error {
  if (
    typeof data === "object" &&
    data !== null &&
    "message" in data &&
    typeof data.message === "string"
  ) {
    return new Error(data.message);
  }

  return new Error(fallbackMessage);
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

export async function updateSubSampleQuantity({
  inventoryItemGlobalId,
  quantity,
  token,
}: {
  inventoryItemGlobalId: string;
  quantity: InventoryQuantity;
  token: string;
}): Promise<InventorySubSample> {
  const subSampleId = parseSubSampleIdFromGlobalId(inventoryItemGlobalId);
  if (subSampleId === null) {
    throw new Error(`Unsupported inventory item global ID: ${inventoryItemGlobalId}`);
  }

  const response = await fetch(`${INVENTORY_API_BASE_URL}/subSamples/${subSampleId}`, {
    method: "PUT",
    headers: {
      "Content-Type": "application/json",
      "X-Requested-With": "XMLHttpRequest",
      Authorization: `Bearer ${token}`,
    },
    body: JSON.stringify({
      quantity: parseOrThrow(InventoryQuantitySchema, quantity),
    }),
  });

  const data = await readJsonSafely(response);

  if (!response.ok) {
    throw toInventoryError(
      data,
      `Failed to update inventory quantity: ${response.statusText}`,
    );
  }

  return parseOrThrow(InventorySubSampleSchema, data);
}

export function useSubSampleQuantitiesQuery({
  inventoryItemGlobalIds,
  token,
  getToken,
}: {
  inventoryItemGlobalIds: ReadonlyArray<string>;
} & UseSubSampleQuantitiesQueryTokenParams): ReadonlyMap<
  string,
  InventoryQuantityQueryResult
> {
  const uniqueInventoryItemGlobalIds = React.useMemo(
    () => Array.from(new Set(inventoryItemGlobalIds.filter(Boolean))),
    [inventoryItemGlobalIds],
  );
  const tokenPromiseRef = React.useRef<Promise<string> | null>(null);

  React.useEffect(() => {
    tokenPromiseRef.current = null;
  }, [getToken, token]);

  const queryResults = useQueries({
    queries: uniqueInventoryItemGlobalIds.map((inventoryItemGlobalId) => ({
      queryKey: inventoryQueryKeys.subSampleQuantity(inventoryItemGlobalId),
      queryFn: async () => {
        tokenPromiseRef.current ??= resolveToken({ token, getToken });

        return getSubSampleQuantity({
          inventoryItemGlobalId,
          token: await tokenPromiseRef.current,
        });
      },
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

export function toInventoryQuantityResult(
  quantity: InventoryQuantity | null,
): InventoryQuantityQueryResult {
  return {
    status: "available",
    quantity,
  };
}
