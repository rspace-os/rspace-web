import { vi } from "vitest";
import type SubSampleModel from "@/stores/models/SubSampleModel";

/** Stands in for the server's edit locks, so a test can assert which locks are left held. */
export function fakeServerLocks(origin: SubSampleModel): Set<string> {
  const held = new Set<string>();
  const id = origin.globalId ?? "";
  vi.spyOn(origin, "acquireEditLock").mockImplementation(() => {
    if (held.has(id)) return Promise.resolve("WAS_ALREADY_LOCKED");
    held.add(id);
    return Promise.resolve("LOCKED_OK");
  });
  vi.spyOn(origin, "releaseLock").mockImplementation(() => {
    held.delete(id);
    return Promise.resolve(true);
  });
  return held;
}
