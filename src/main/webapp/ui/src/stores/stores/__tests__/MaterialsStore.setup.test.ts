import "@/stores/stores/RootStore";
import { expect, it, vi } from "vitest";
import MaterialsStore from "../MaterialsStore";
import type { RootStore } from "../RootStore";

it("can retry setup after authentication fails", async () => {
  const error = new Error("Service unavailable");
  const authenticate = vi.fn().mockRejectedValueOnce(error).mockResolvedValue(undefined);
  const fetchCurrentUser = vi.fn().mockResolvedValue(undefined);
  const fetchUnits = vi.fn().mockResolvedValue(undefined);
  const store = new MaterialsStore({
    authStore: { synchronizeWithSessionStorage: authenticate },
    peopleStore: { fetchCurrentUser },
    unitStore: { fetchUnits },
  } as unknown as RootStore);

  const first = store.setup();
  expect(store.setup()).toBe(first);
  await expect(first).rejects.toBe(error);
  expect(fetchCurrentUser).not.toHaveBeenCalled();
  expect(fetchUnits).not.toHaveBeenCalled();
  await store.setup();
  expect(authenticate).toHaveBeenCalledTimes(2);
  expect(fetchCurrentUser).toHaveBeenCalled();
  expect(fetchUnits).toHaveBeenCalled();
});
