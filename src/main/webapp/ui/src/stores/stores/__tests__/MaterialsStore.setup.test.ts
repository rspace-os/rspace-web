import "@/stores/stores/RootStore";
import { expect, it, vi } from "vitest";
import MaterialsStore from "../MaterialsStore";
import type { RootStore } from "../RootStore";

it.each(["authentication", "current user", "units"] as const)("can retry setup after %s fails", async (failure) => {
  const authenticate = vi.fn().mockResolvedValue(undefined);
  const fetchCurrentUser = vi.fn().mockResolvedValue(undefined);
  const fetchUnits = vi.fn().mockResolvedValue(undefined);
  const failingStep = { authentication: authenticate, "current user": fetchCurrentUser, units: fetchUnits }[failure];
  const error = new Error("Service unavailable");
  failingStep.mockRejectedValueOnce(error);
  const store = new MaterialsStore({
    authStore: { synchronizeWithSessionStorage: authenticate },
    peopleStore: { fetchCurrentUser },
    unitStore: { fetchUnits },
  } as unknown as RootStore);

  const first = store.setup();
  expect(store.setup()).toBe(first);
  await expect(first).rejects.toBe(error);
  if (failure === "authentication") {
    expect(fetchCurrentUser).not.toHaveBeenCalled();
    expect(fetchUnits).not.toHaveBeenCalled();
  }
  await store.setup();
  expect(authenticate).toHaveBeenCalledTimes(2);
  expect(fetchCurrentUser).toHaveBeenCalled();
  expect(fetchUnits).toHaveBeenCalled();
});
