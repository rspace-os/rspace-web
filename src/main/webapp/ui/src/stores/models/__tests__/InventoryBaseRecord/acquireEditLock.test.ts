import { describe, expect, test, vi } from "vitest";
import { RecordLockedError } from "../../InventoryBaseRecord";
import { makeMockSubSample } from "../SubSampleModel/mocking";

vi.mock("../../../../common/InvApiService", () => ({
  default: {
    query: vi.fn(() => ({})),
  },
}));
vi.mock("../../../../stores/stores/getRootStore", () => ({
  default: () => ({
    uiStore: {
      addAlert: () => {},
      setPageNavigationConfirmation: () => {},
      setDirty: () => {},
      confirmationDialogProps: null,
    },
    trackingStore: { trackEvent: () => {} },
    unitStore: { getUnit: () => ({ label: "ml" }) },
  }),
}));

const owner = { firstName: "Carol", lastName: "Holder", username: "carol" };

describe("acquireEditLock", () => {
  test("takes the lock without putting the record into edit state", async () => {
    const subsample = makeMockSubSample();
    vi.spyOn(subsample, "checkLock").mockResolvedValue({
      status: "LOCKED_OK",
      remainingTimeInSeconds: 300,
      lockOwner: owner,
    });
    const fetchSpy = vi.spyOn(subsample, "fetchAdditionalInfo").mockResolvedValue(undefined);

    expect(await subsample.acquireEditLock()).toBe("LOCKED_OK");

    expect(subsample.state).toBe("preview");
    expect(subsample.editing).toBe(false);
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  test("records when the lock lapses, without starting the form's expiry prompt", async () => {
    const subsample = makeMockSubSample();
    vi.spyOn(subsample, "checkLock").mockResolvedValue({
      status: "LOCKED_OK",
      remainingTimeInSeconds: 300,
      lockOwner: owner,
    });
    const expiryCheck = vi.spyOn(subsample, "expiryCheck");

    await subsample.acquireEditLock();

    expect(subsample.lockExpiry.getTime()).toBeGreaterThan(Date.now());
    expect(subsample.lockExpired).toBe(false);
    expect(expiryCheck).not.toHaveBeenCalled();
  });

  test("an extension of the caller's own lock is a pass", async () => {
    const subsample = makeMockSubSample();
    vi.spyOn(subsample, "checkLock").mockResolvedValue({
      status: "WAS_ALREADY_LOCKED",
      remainingTimeInSeconds: 300,
      lockOwner: owner,
    });

    expect(await subsample.acquireEditLock()).toBe("WAS_ALREADY_LOCKED");
    expect(subsample.state).toBe("preview");
  });

  test("a lock held by someone else throws, naming them", async () => {
    const subsample = makeMockSubSample();
    vi.spyOn(subsample, "checkLock").mockResolvedValue({
      status: "CANNOT_LOCK",
      remainingTimeInSeconds: 0,
      lockOwner: owner,
    });

    await expect(subsample.acquireEditLock()).rejects.toThrow(RecordLockedError);
    expect(subsample.state).toBe("preview");
  });

  test("the thrown error carries the holder so the alert can name them", async () => {
    const subsample = makeMockSubSample();
    vi.spyOn(subsample, "checkLock").mockResolvedValue({
      status: "CANNOT_LOCK",
      remainingTimeInSeconds: 0,
      lockOwner: owner,
    });

    const error = await subsample.acquireEditLock().catch((e: unknown) => e);

    expect(error).toBeInstanceOf(RecordLockedError);
    expect((error as RecordLockedError).lockOwner).toEqual(owner);
    expect((error as RecordLockedError).record).toBe(subsample);
  });
});
