import { beforeEach, describe, expect, test, vi } from "vitest";
import getRootStore from "../../../stores/getRootStore";
import { makeMockContainer } from "./mocking";

vi.mock("../../../use-stores", () => () => {});
vi.mock("../../../stores/getRootStore", () => ({
  default: vi.fn(),
}));

type MockMoveStore = {
  selectedResultsIncludesContainers: boolean;
  selectedResultsIncludesSubSamples: boolean;
  selectedResultsIncludesInstruments: boolean;
};

const mockMoveStore = (opts: Partial<MockMoveStore> = {}) => {
  vi.mocked(getRootStore).mockReturnValue({
    moveStore: {
      selectedResultsIncludesContainers: false,
      selectedResultsIncludesSubSamples: false,
      selectedResultsIncludesInstruments: false,
      ...opts,
    },
  } as ReturnType<typeof getRootStore>);
};

beforeEach(() => {
  mockMoveStore();
});

describe("canStoreInstruments", () => {
  describe("computed: canStore", () => {
    test("includes 'instruments' when canStoreInstruments is true", () => {
      const container = makeMockContainer({ canStoreInstruments: true });
      expect(container.canStore).toContain("instruments");
    });

    test("excludes 'instruments' when canStoreInstruments is false", () => {
      const container = makeMockContainer({ canStoreInstruments: false });
      expect(container.canStore).not.toContain("instruments");
    });

    test("returns only 'instruments' when canStoreContainers and canStoreSamples are false", () => {
      const container = makeMockContainer({
        canStoreContainers: false,
        canStoreSamples: false,
        canStoreInstruments: true,
      });
      expect(container.canStore).toEqual(["instruments"]);
    });
  });

  describe("computed: allowedTypeFilters", () => {
    test("includes 'INSTRUMENT' when canStoreInstruments is true", () => {
      const container = makeMockContainer({ canStoreInstruments: true });
      expect(container.allowedTypeFilters.has("INSTRUMENT")).toBe(true);
    });

    test("excludes 'INSTRUMENT' when canStoreInstruments is false", () => {
      const container = makeMockContainer({ canStoreInstruments: false });
      expect(container.allowedTypeFilters.has("INSTRUMENT")).toBe(false);
    });

    test("includes 'ALL' when canStoreInstruments and at least one other type are both enabled", () => {
      const container = makeMockContainer({
        canStoreContainers: true,
        canStoreSamples: false,
        canStoreInstruments: true,
      });
      expect(container.allowedTypeFilters.has("ALL")).toBe(true);
    });

    test("does not include 'ALL' when only canStoreInstruments is enabled", () => {
      const container = makeMockContainer({
        canStoreContainers: false,
        canStoreSamples: false,
        canStoreInstruments: true,
      });
      expect(container.allowedTypeFilters.has("ALL")).toBe(false);
    });
  });

  describe("computed: canStoreRecordTypes", () => {
    test("returns false when moving instruments into a container that cannot store them", () => {
      mockMoveStore({ selectedResultsIncludesInstruments: true });
      const container = makeMockContainer({ canStoreInstruments: false });
      expect(container.canStoreRecordTypes).toBe(false);
    });

    test("returns true when moving instruments into a container that can store them", () => {
      mockMoveStore({ selectedResultsIncludesInstruments: true });
      const container = makeMockContainer({ canStoreInstruments: true });
      expect(container.canStoreRecordTypes).toBe(true);
    });

    test("returns true when moving non-instruments into a container that cannot store instruments", () => {
      mockMoveStore({ selectedResultsIncludesSubSamples: true });
      const container = makeMockContainer({
        canStoreSamples: true,
        canStoreInstruments: false,
      });
      expect(container.canStoreRecordTypes).toBe(true);
    });
  });

  describe("validate: canStore", () => {
    test("is invalid when all three can-store flags are false", () => {
      const container = makeMockContainer({
        canStoreContainers: false,
        canStoreSamples: false,
        canStoreInstruments: false,
      });
      expect(container.validate().isError).toBe(true);
    });

    test("is valid when only canStoreInstruments is true", () => {
      const container = makeMockContainer({
        canStoreContainers: false,
        canStoreSamples: false,
        canStoreInstruments: true,
      });
      expect(container.validate().isOk).toBe(true);
    });
  });

  describe("computed: paramsForBackend", () => {
    test("includes canStoreInstruments: true when in create state", () => {
      const container = makeMockContainer({ id: null, canStoreInstruments: true });
      expect(container.paramsForBackend).toHaveProperty("canStoreInstruments", true);
    });

    test("includes canStoreInstruments: false when the flag is disabled", () => {
      const container = makeMockContainer({ id: null, canStoreInstruments: false });
      expect(container.paramsForBackend).toHaveProperty("canStoreInstruments", false);
    });
  });
});
