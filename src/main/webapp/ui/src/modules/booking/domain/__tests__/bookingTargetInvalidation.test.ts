import { QueryClient } from "@tanstack/react-query";
import { expect, it } from "vitest";
import { getSearchBookingConfigurationTargetsQueryKey } from "@/modules/common/api/v2/generated/booking-configuration-targets/booking-configuration-targets";
import {
  getCreateBookingConfigurationMutationOptions,
  getCreateManyBookingConfigurationsMutationOptions,
  getDeleteBookingConfigurationMutationOptions,
  getDeleteManyBookingConfigurationsMutationOptions,
  getGetBookingConfigurationQueryKey,
  getUpdateBookingConfigurationMutationOptions,
  getUpdateManyBookingConfigurationsMutationOptions,
} from "@/modules/common/api/v2/generated/booking-configurations/booking-configurations";

const actions = {
  create: (client: QueryClient) =>
    client
      .getMutationCache()
      .build(client, {
        ...getCreateBookingConfigurationMutationOptions(client),
        mutationFn: async () => ({ id: 7 }),
      })
      .execute({ data: { target: { relationTo: "instruments", value: 7 } } }),
  restore: (client: QueryClient) =>
    client
      .getMutationCache()
      .build(client, {
        ...getUpdateBookingConfigurationMutationOptions(client),
        mutationFn: async () => ({ id: 7 }),
      })
      .execute({ id: 7, data: { state: "ACTIVE" }, headers: { "If-Match": '"1"' } }),
  permanentDelete: (client: QueryClient) =>
    client
      .getMutationCache()
      .build(client, {
        ...getDeleteBookingConfigurationMutationOptions(client),
        mutationFn: async () => ({ id: 7 }),
      })
      .execute({ id: 7, params: { permanent: true }, headers: { "If-Match": '"1"' } }),
  bulkCreate: (client: QueryClient) =>
    client
      .getMutationCache()
      .build(client, {
        ...getCreateManyBookingConfigurationsMutationOptions(client),
        mutationFn: async () => ({ docs: [{ id: 7 }] }),
      })
      .execute({ data: { docs: [{ target: { relationTo: "instruments", value: 7 } }] } }),
  bulkUpdate: (client: QueryClient) =>
    client
      .getMutationCache()
      .build(client, {
        ...getUpdateManyBookingConfigurationsMutationOptions(client),
        mutationFn: async () => ({ docs: [{ id: 7 }] }),
      })
      .execute({ data: { enabled: true }, params: { where: "id==7" } }),
  bulkDelete: (client: QueryClient) =>
    client
      .getMutationCache()
      .build(client, {
        ...getDeleteManyBookingConfigurationsMutationOptions(client),
        mutationFn: async () => ({ docs: [{ id: 7 }] }),
      })
      .execute({ params: { where: "id==7" } }),
};

it.each(Object.entries(actions))(
  "%s refreshes every target-search variant and leaves other records alone",
  async (_name, mutate) => {
    const client = new QueryClient();
    const affected = [
      getSearchBookingConfigurationTargetsQueryKey(),
      getSearchBookingConfigurationTargetsQueryKey({ q: "IN7" }),
      getSearchBookingConfigurationTargetsQueryKey({ q: "microscope" }),
    ];
    const retained = getGetBookingConfigurationQueryKey(999);
    for (const key of [...affected, retained]) client.setQueryData(key, {});
    try {
      await mutate(client);
      for (const key of affected) expect(client.getQueryState(key)?.isInvalidated).toBe(true);
      expect(client.getQueryState(retained)?.isInvalidated).toBe(false);
    } finally {
      client.clear();
    }
  },
);
