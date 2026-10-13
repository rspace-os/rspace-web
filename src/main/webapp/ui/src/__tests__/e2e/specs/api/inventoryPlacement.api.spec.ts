import { expect } from "@playwright/test";
import { ApiError } from "@/__tests__/e2e/api/clients/BaseApiClient";
import { InventoryClient } from "@/__tests__/e2e/api/clients/InventoryClient";
import type { ApiInventoryBulkMoveRecord, ApiInventorySubSample } from "@/__tests__/e2e/api/models/inventory";
import { createDynamicUser } from "@/__tests__/e2e/createDynamicUser";
import { test } from "@/__tests__/e2e/fixtures/flows";
import { uniqueName } from "@/__tests__/e2e/testData";

const ML = 3;
const ONE_ML = { numericValue: 1, unitId: ML };

async function subSamples(client: InventoryClient, count: number): Promise<ApiInventorySubSample[]> {
  const sample = await client.createSample({
    name: uniqueName("e2e-api-place"),
    newSampleSubSamplesCount: count,
    quantity: { numericValue: 10, unitId: ML },
  });
  return sample.subSamples;
}

async function box(client: InventoryClient, size = 2) {
  return client.createContainer({
    name: uniqueName("e2e-api-box"),
    cType: "GRID",
    gridLayout: { columnsNumber: size, rowsNumber: size },
  });
}

function moveInto(subSample: ApiInventorySubSample, containerId: number): ApiInventoryBulkMoveRecord {
  return {
    id: subSample.id,
    globalId: subSample.globalId,
    type: "SUBSAMPLE",
    parentContainers: [{ id: containerId }],
    parentLocation: { coordX: 1, coordY: 1 },
  };
}

async function occupants(client: InventoryClient, containerId: number): Promise<string[]> {
  const container = await client.getContainer(containerId);
  return (container.locations ?? []).flatMap((l) => (l.content ? [l.content.globalId] : []));
}

function aliquotOf(subSample: ApiInventorySubSample) {
  return {
    origin: { globalId: subSample.globalId, amountTaken: ONE_ML },
    sampleName: uniqueName("e2e-api-aliquot"),
    count: 1,
    eachAmount: ONE_ML,
  };
}

test.describe("Inventory placement API", () => {
  test("As an API client, operations are refused while they are not enabled", async ({
    flowOperationsDisabled: _,
    clientInventory,
  }) => {
    const [origin] = await subSamples(clientInventory, 1);

    const res = await clientInventory.aliquot(aliquotOf(origin));

    expect(res.status()).toBe(404);
    expect((await res.json()).internalCode).toBe(50301);
  });

  test.describe("with operations enabled", () => {
    test("As an API client, I cannot move into another user's container", async ({
      flowOperationsEnabled: _,
      clientInventory,
      clientSysadmin,
      apiContext,
    }) => {
      const stranger = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2ePlaceStranger");
      const strangerBox = await box(new InventoryClient(apiContext, stranger.apiKey));
      const [mine] = await subSamples(clientInventory, 1);

      const result = await clientInventory.bulkMove([moveInto(mine, strangerBox.id)], true);

      expect(result.errorCount).toBe(1);
      expect(await occupants(new InventoryClient(apiContext, stranger.apiKey), strangerBox.id)).toEqual([]);
    });

    test("As an API client, I cannot run an operation on another user's subsample", async ({
      flowOperationsEnabled: _,
      clientInventory,
      clientSysadmin,
      apiContext,
    }) => {
      const stranger = await createDynamicUser(clientSysadmin, "ROLE_PI", "e2ePlaceStranger");
      const [theirs] = await subSamples(new InventoryClient(apiContext, stranger.apiKey), 1);
      const request = aliquotOf(theirs);

      const res = await clientInventory.aliquot(request);

      expect([403, 404]).toContain(res.status());
      const named = (await clientInventory.searchByName(request.sampleName, "SAMPLE")).records;
      expect(named.filter((r) => r.name === request.sampleName)).toHaveLength(0);
    });

    test("As an API client, I cannot move into an occupied location", async ({
      flowOperationsEnabled: _,
      clientInventory,
    }) => {
      const container = await box(clientInventory);
      const [first, second] = await subSamples(clientInventory, 2);
      await clientInventory.bulkMove([moveInto(first, container.id)], true);

      const result = await clientInventory.bulkMove([moveInto(second, container.id)], true);

      expect(result.errorCount).toBe(1);
      expect(await occupants(clientInventory, container.id)).toEqual([first.globalId]);
      expect((await clientInventory.getSubSample(second.id)).parentContainers?.[0].cType).toBe("WORKBENCH");
    });

    /** Each racer's outcome: moved, refused with a per-record error, or a server error. */
    async function race(client: InventoryClient, containerId: number, racers: ApiInventorySubSample[]) {
      const settled = await Promise.allSettled(racers.map((s) => client.bulkMove([moveInto(s, containerId)], true)));
      return settled.map((r) => {
        if (r.status === "fulfilled") return r.value.errorCount === 0 ? "moved" : "refused";
        if (r.reason instanceof ApiError) return `HTTP ${r.reason.status}`;
        throw r.reason;
      });
    }

    test("As an API client, parallel moves into one empty location never leave two items there", async ({
      flowOperationsEnabled: _,
      clientInventory,
    }) => {
      for (let round = 0; round < 3; round++) {
        const container = await box(clientInventory);
        const outcomes = await race(clientInventory, container.id, await subSamples(clientInventory, 5));

        const moved = outcomes.filter((o) => o === "moved").length;
        expect(moved).toBeLessThanOrEqual(1);
        expect(await occupants(clientInventory, container.id)).toHaveLength(moved);
      }
    });

    test("As an API client, a move that loses a race into one location is refused, not failed", {
      annotation: { type: "issue", description: "Concurrent writes answer 500 with the SQL error (e2e report F2)" },
    }, async ({ flowOperationsEnabled: _, clientInventory }) => {
      test.fail();
      const container = await box(clientInventory);

      const outcomes = await race(clientInventory, container.id, await subSamples(clientInventory, 5));

      expect(outcomes.sort()).toEqual(["moved", "refused", "refused", "refused", "refused"]);
    });

    test("As an API client, parallel operations on separate subsamples all succeed", {
      annotation: { type: "issue", description: "Concurrent writes answer 500 with the SQL error (e2e report F2)" },
    }, async ({ flowOperationsEnabled: _, clientInventory }) => {
      test.fail();
      const origins = [
        ...(await subSamples(clientInventory, 1)),
        ...(await subSamples(clientInventory, 1)),
        ...(await subSamples(clientInventory, 1)),
      ];

      const responses = await Promise.all(origins.map((o) => clientInventory.aliquot(aliquotOf(o))));

      expect(responses.map((r) => r.status())).toEqual([201, 201, 201]);
    });
  });
});
