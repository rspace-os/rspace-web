import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { inventoryQueryKeys } from "@/modules/inventory/queries";
import type { StoichiometryRequest } from "@/modules/stoichiometry/schema";
import { stoichiometryQueryKeys } from "@/modules/stoichiometry/queries";
import { useEditableStoichiometryTable } from "@/tinyMCE/stoichiometry/useEditableStoichiometryTable";

const {
  createEditableMolecules,
  mockDeductStockMutateAsync,
  mockDeleteStoichiometryMutateAsync,
  mockGetStoichiometry,
  mockGetMoleculeInfoMutateAsync,
  mockGetToken,
  mockUseOauthTokenQuery,
  mockRefreshedStoichiometry,
  mockStoichiometryQueryData,
  mockUseSubSampleQuantitiesQuery,
  mockUpdateStoichiometryMutateAsync,
} = vi.hoisted(() => ({
  createEditableMolecules: () => [
    {
      id: 5,
      rsChemElement: {
        id: 32771,
        parentId: null,
        ecatChemFileId: null,
        dataImage: null,
        chemElements: "C1C=CC=C1",
        smilesString: null,
        chemId: null,
        reactionId: null,
        rgroupId: null,
        metadata: null,
        chemElementsFormat: "MOL",
        creationDate: 1753964548126,
        imageFileProperty: null,
      },
      inventoryLink: {
        id: 501,
        inventoryItemGlobalId: "SS123",
        stockDeducted: false,
        stoichiometryMoleculeId: 5,
        quantity: {
          numericValue: 10,
          unitId: 7,
        },
      },
      savedInventoryLink: {
        id: 501,
        inventoryItemGlobalId: "SS123",
        stockDeducted: false,
        stoichiometryMoleculeId: 5,
        quantity: {
          numericValue: 10,
          unitId: 7,
        },
      },
      deletedInventoryLink: null,
      role: "REACTANT",
      formula: "C5 H6",
      name: "Cyclopentadiene",
      smiles: "C1C=CC=C1",
      coefficient: 2,
      molecularWeight: 1,
      mass: 10,
      moles: 10,
      actualAmount: 5,
      actualMoles: 5,
      actualYield: 5,
      limitingReagent: false,
      notes: null,
    },
    {
      id: 6,
      rsChemElement: {
        id: 32772,
        parentId: null,
        ecatChemFileId: null,
        dataImage: null,
        chemElements: "C1CCCCC1",
        smilesString: null,
        chemId: null,
        reactionId: null,
        rgroupId: null,
        metadata: null,
        chemElementsFormat: "MOL",
        creationDate: 1753964548127,
        imageFileProperty: null,
      },
      inventoryLink: {
        id: 502,
        inventoryItemGlobalId: "SS124",
        stockDeducted: false,
        stoichiometryMoleculeId: 6,
        quantity: {
          numericValue: 10,
          unitId: 7,
        },
      },
      savedInventoryLink: {
        id: 502,
        inventoryItemGlobalId: "SS124",
        stockDeducted: false,
        stoichiometryMoleculeId: 6,
        quantity: {
          numericValue: 10,
          unitId: 7,
        },
      },
      deletedInventoryLink: null,
      role: "PRODUCT",
      formula: "C6 H12",
      name: "Cyclopentane",
      smiles: "C1CCCCC1",
      coefficient: 3,
      molecularWeight: 1,
      mass: 10,
      moles: 10,
      actualAmount: 5,
      actualMoles: 5,
      actualYield: 5,
      limitingReagent: false,
      notes: null,
    },
    {
      id: 7,
      rsChemElement: {
        id: 32773,
        parentId: null,
        ecatChemFileId: null,
        dataImage: null,
        chemElements: "CCO",
        smilesString: null,
        chemId: null,
        reactionId: null,
        rgroupId: null,
        metadata: null,
        chemElementsFormat: "MOL",
        creationDate: 1753964548128,
        imageFileProperty: null,
      },
      inventoryLink: {
        id: 503,
        inventoryItemGlobalId: "SS125",
        stockDeducted: false,
        stoichiometryMoleculeId: 7,
        quantity: {
          numericValue: 25,
          unitId: 3,
        },
      },
      savedInventoryLink: {
        id: 503,
        inventoryItemGlobalId: "SS125",
        stockDeducted: false,
        stoichiometryMoleculeId: 7,
        quantity: {
          numericValue: 25,
          unitId: 3,
        },
      },
      deletedInventoryLink: null,
      role: "AGENT",
      formula: "C2 H6 O",
      name: "Ethanol",
      smiles: "CCO",
      coefficient: 1,
      molecularWeight: 46.07,
      mass: 5,
      moles: 0.109,
      actualAmount: 5,
      actualMoles: 0.109,
      actualYield: null,
      limitingReagent: false,
      notes: null,
    },
    {
      id: 8,
      rsChemElement: null,
      inventoryLink: null,
      savedInventoryLink: null,
      deletedInventoryLink: null,
      role: "AGENT",
      formula: "H2 O",
      name: "Water",
      smiles: "O",
      coefficient: 1,
      molecularWeight: 18.015,
      mass: 1,
      moles: null,
      actualAmount: null,
      actualMoles: null,
      actualYield: null,
      limitingReagent: false,
      notes: null,
    },
  ],
  mockDeductStockMutateAsync: vi.fn(),
  mockDeleteStoichiometryMutateAsync: vi.fn(),
  mockGetStoichiometry: vi.fn(),
  mockGetMoleculeInfoMutateAsync: vi.fn(),
  mockGetToken: vi.fn(),
  mockUseOauthTokenQuery: vi.fn(),
  mockRefreshedStoichiometry: {
    id: 9,
    revision: 4,
    molecules: [],
  },
  mockStoichiometryQueryData: {
    id: 3,
    revision: 1,
    molecules: [],
  },
  mockUseSubSampleQuantitiesQuery: vi.fn(() =>
    new Map([
      [
        "SS123",
        {
          status: "available",
          quantity: {
            numericValue: 4,
            unitId: 7,
          },
        },
      ],
      [
        "SS124",
        {
          status: "available",
          quantity: {
            numericValue: 10,
            unitId: 7,
          },
        },
      ],
      [
        "SS125",
        {
          status: "available",
          quantity: {
            numericValue: 25,
            unitId: 3,
          },
        },
      ],
    ]),
  ),
  mockUpdateStoichiometryMutateAsync: vi.fn(),
}));

vi.mock("@/hooks/auth/useOauthToken", () => ({
  default: () => ({
    getToken: mockGetToken,
  }),
}));

vi.mock("@/modules/common/hooks/auth", () => ({
  useOauthTokenQuery: mockUseOauthTokenQuery,
}));

vi.mock("@/modules/inventory/queries", async () => {
  const actual = await vi.importActual<typeof import("@/modules/inventory/queries")>(
    "@/modules/inventory/queries",
  );

  return {
    ...actual,
    useSubSampleQuantitiesQuery: mockUseSubSampleQuantitiesQuery,
  };
});

vi.mock("@/modules/stoichiometry/mutations", () => ({
  useUpdateStoichiometryMutation: () => ({
    isPending: false,
    mutateAsync: mockUpdateStoichiometryMutateAsync,
  }),
  useDeductStockMutation: () => ({
    isPending: false,
    mutateAsync: mockDeductStockMutateAsync,
  }),
  useDeleteStoichiometryMutation: () => ({
    isPending: false,
    mutateAsync: mockDeleteStoichiometryMutateAsync,
  }),
  useGetMoleculeInfoMutation: () => ({
    isPending: false,
    mutateAsync: mockGetMoleculeInfoMutateAsync,
  }),
}));

vi.mock("@/modules/stoichiometry/queries", () => ({
  getStoichiometry: mockGetStoichiometry,
  stoichiometryQueryKeys: {
    all: ["rspace.api.stoichiometry"],
    byId: (stoichiometryId: number, revision?: number) => [
      "rspace.api.stoichiometry",
      "byId",
      stoichiometryId,
      revision ?? "latest",
    ],
  },
  useGetStoichiometryQuery: () => ({
    data: mockStoichiometryQueryData,
    isFetching: false,
  }),
}));

vi.mock("@/tinyMCE/stoichiometry/editableMolecules", () => ({
  toEditableMolecules: () => createEditableMolecules(),
}));

function HookHarness({
  onValue,
}: {
  onValue: (value: ReturnType<typeof useEditableStoichiometryTable>) => void;
}) {
  const value = useEditableStoichiometryTable({
    stoichiometryId: 3,
    stoichiometryRevision: 1,
  });

  React.useEffect(() => {
    onValue(value);
  }, [onValue, value]);

  return null;
}

describe("useEditableStoichiometryTable", () => {
  beforeEach(() => {
    mockUpdateStoichiometryMutateAsync.mockReset();
    mockDeductStockMutateAsync.mockReset();
    mockDeleteStoichiometryMutateAsync.mockReset();
    mockGetStoichiometry.mockReset();
    mockGetMoleculeInfoMutateAsync.mockReset();
    mockGetToken.mockReset();
    mockUseOauthTokenQuery.mockReset();
    mockUseSubSampleQuantitiesQuery.mockClear();
    mockDeductStockMutateAsync.mockResolvedValue({
      stoichiometryId: 9,
      revisionNumber: 4,
      results: [{ linkId: 502, success: true }],
    });
    mockGetToken.mockResolvedValue("resolved-token");
    mockUseOauthTokenQuery.mockReturnValue({ data: "inventory-token" });
    mockUpdateStoichiometryMutateAsync.mockResolvedValue({ revision: 2 });
    mockGetStoichiometry.mockResolvedValue(mockRefreshedStoichiometry);
  });

  it("gets the inventory token from useOauthTokenQuery before calling the sub-sample quantity hook", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness onValue={() => {}} />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(mockUseSubSampleQuantitiesQuery).toHaveBeenLastCalledWith({
        inventoryItemGlobalIds: ["SS123", "SS124", "SS125"],
        token: "inventory-token",
      });
    });
  });

  it("assigns distinct numeric temporary ids when reagent additions overlap", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    let latestValue: ReturnType<typeof useEditableStoichiometryTable> | null = null;
    let resolveFirstRequest: ((value: { molecularWeight: number; formula: string }) => void) | null = null;
    let resolveSecondRequest: ((value: { molecularWeight: number; formula: string }) => void) | null = null;

    mockGetMoleculeInfoMutateAsync
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveFirstRequest = resolve;
          }),
      )
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveSecondRequest = resolve;
          }),
      );

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness
          onValue={(value) => {
            latestValue = value;
          }}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(4);
    });

    act(() => {
      void latestValue?.tableController.addReagent(
        "O",
        "New Water",
        "manual",
      );
      void latestValue?.tableController.addReagent(
        "CC(=O)O",
        "Acetic acid",
        "manual",
      );
    });

    act(() => {
      resolveSecondRequest?.({ molecularWeight: 60.052, formula: "C2H4O2" });
    });
    act(() => {
      resolveFirstRequest?.({ molecularWeight: 18.015, formula: "H2O" });
    });

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(6);
    });

    const addedReagents = latestValue!.allMolecules.filter(
      ({ name, id }) =>
        (name === "New Water" || name === "Acetic acid") &&
        typeof id === "number",
    );

    expect(addedReagents).toHaveLength(2);
    expect(addedReagents.map(({ id }) => id)).toEqual(
      expect.arrayContaining([expect.any(Number), expect.any(Number)]),
    );
    expect(new Set(addedReagents.map(({ id }) => id)).size).toBe(2);
    expect(addedReagents.every(({ id }) => id < 0)).toBe(true);
  });

  it("soft-deletes saved inventory links, blocks replacement until undo, and restores them on undo", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    let latestValue: ReturnType<typeof useEditableStoichiometryTable> | null = null;

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness
          onValue={(value) => {
            latestValue = value;
          }}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(4);
    });
    expect(latestValue!.hasChanges).toBe(false);

    act(() => {
      latestValue?.tableController.removeInventoryLink(5);
    });

    await waitFor(() => {
      expect(
        latestValue?.allMolecules.find((molecule) => molecule.id === 5),
      ).toMatchObject({
        inventoryLink: null,
        deletedInventoryLink: {
          inventoryItemGlobalId: "SS123",
        },
      });
      expect(latestValue?.hasChanges).toBe(true);
    });

    act(() => {
      latestValue?.tableController.pickInventoryLink(5, 999, "SS999");
    });

    expect(
      latestValue!.allMolecules.find((molecule) => molecule.id === 5),
    ).toMatchObject({
      inventoryLink: null,
      deletedInventoryLink: {
        inventoryItemGlobalId: "SS123",
      },
    });

    act(() => {
      latestValue?.tableController.undoRemoveInventoryLink(5);
    });

    await waitFor(() => {
      expect(
        latestValue?.allMolecules.find((molecule) => molecule.id === 5),
      ).toMatchObject({
        inventoryLink: {
          inventoryItemGlobalId: "SS123",
        },
        deletedInventoryLink: null,
      });
      expect(latestValue?.hasChanges).toBe(false);
    });
  });

  it("removes newly added unsaved inventory links immediately without entering deleted placeholder state", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    let latestValue: ReturnType<typeof useEditableStoichiometryTable> | null = null;

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness
          onValue={(value) => {
            latestValue = value;
          }}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(4);
    });
    expect(latestValue!.hasChanges).toBe(false);

    act(() => {
      latestValue?.tableController.pickInventoryLink(8, 999, "SS999");
    });

    await waitFor(() => {
      expect(
        latestValue?.allMolecules.find((molecule) => molecule.id === 8),
      ).toMatchObject({
        inventoryLink: {
          inventoryItemGlobalId: "SS999",
        },
        deletedInventoryLink: null,
      });
      expect(latestValue?.hasChanges).toBe(true);
    });

    act(() => {
      latestValue?.tableController.removeInventoryLink(8);
    });

    await waitFor(() => {
      expect(
        latestValue?.allMolecules.find((molecule) => molecule.id === 8),
      ).toMatchObject({
        inventoryLink: null,
        deletedInventoryLink: null,
      });
      expect(latestValue?.hasChanges).toBe(false);
    });
  });

  it("serializes soft-deleted saved links as null and clears temporary deleted state after save", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    let latestValue: ReturnType<typeof useEditableStoichiometryTable> | null = null;

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness
          onValue={(value) => {
            latestValue = value;
          }}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(4);
    });

    act(() => {
      latestValue?.tableController.removeInventoryLink(5);
    });

    await act(async () => {
      await latestValue?.save();
    });

    const updateRequest = mockUpdateStoichiometryMutateAsync.mock.calls[0]?.[0] as
      | {
          stoichiometryId: number;
          stoichiometryData: StoichiometryRequest;
        }
      | undefined;

    expect(updateRequest?.stoichiometryId).toBe(3);
    expect(updateRequest?.stoichiometryData.id).toBe(3);
    expect(
      updateRequest?.stoichiometryData.molecules.find(
        (molecule) => "id" in molecule && molecule.id === 5,
      ),
    ).toMatchObject({
      id: 5,
      inventoryLink: null,
    });
    expect(
      latestValue!.allMolecules.find((molecule) => molecule.id === 5),
    ).toMatchObject({
      inventoryLink: null,
      savedInventoryLink: null,
      deletedInventoryLink: null,
    });
  });

  it("refreshes stoichiometry and seeds the returned query key after a successful stock deduction", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    const invalidateQueriesSpy = vi.spyOn(queryClient, "invalidateQueries");
    let latestValue: ReturnType<typeof useEditableStoichiometryTable> | null = null;

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness
          onValue={(value) => {
            latestValue = value;
          }}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(4);
    });

    let result:
      | Awaited<ReturnType<ReturnType<typeof useEditableStoichiometryTable>["tableController"]["updateInventoryStock"]>>
      | undefined;

    await act(async () => {
      result = await latestValue?.tableController.updateInventoryStock([6]);
    });

    expect(result).toEqual({
      refreshedStoichiometry: {
        id: 9,
        revision: 4,
      },
      results: [
        {
          moleculeId: 6,
          moleculeName: "Cyclopentane",
          success: true,
          errorMessage: null,
        },
      ],
    });
    expect(mockDeductStockMutateAsync).toHaveBeenCalledWith({
      stoichiometryId: 3,
      linkIds: [502],
    });
    expect(mockGetStoichiometry).toHaveBeenCalledWith({
      stoichiometryId: 9,
      revision: 4,
      token: "resolved-token",
    });
    expect(
      queryClient.getQueryData(stoichiometryQueryKeys.byId(9, 4)),
    ).toEqual(mockRefreshedStoichiometry);
    expect(invalidateQueriesSpy).toHaveBeenCalledTimes(2);

    expect(invalidateQueriesSpy.mock.calls[0]?.[0]).toMatchObject({
      queryKey: stoichiometryQueryKeys.all,
    });

    const queryFilters = invalidateQueriesSpy.mock.calls[1]?.[0];
    const matchesQueryFilter = (queryKey: readonly unknown[]) =>
      queryFilters?.predicate?.({ queryKey } as never);

    expect(queryFilters).toMatchObject({
      queryKey: inventoryQueryKeys.all,
    });
    expect(matchesQueryFilter(inventoryQueryKeys.subSampleQuantity("SS123"))).toBe(
      true,
    );
    expect(matchesQueryFilter(inventoryQueryKeys.subSampleQuantity("SS124"))).toBe(
      true,
    );
    expect(matchesQueryFilter(inventoryQueryKeys.subSampleQuantity("SS125"))).toBe(
      true,
    );
    expect(matchesQueryFilter(inventoryQueryKeys.subSampleQuantity("SS999"))).toBe(
      false,
    );
  });

  it("refreshes stoichiometry even when the deductStock response contains failed deductions", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    let latestValue: ReturnType<typeof useEditableStoichiometryTable> | null = null;

    mockDeductStockMutateAsync.mockResolvedValueOnce({
      stoichiometryId: 11,
      revisionNumber: 7,
      results: [
        {
          linkId: 502,
          success: false,
          errorMessage: "Insufficient stock",
        },
      ],
    });
    mockGetStoichiometry.mockResolvedValueOnce({
      id: 11,
      revision: 7,
      molecules: [],
    });

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness
          onValue={(value) => {
            latestValue = value;
          }}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(4);
    });

    let result:
      | Awaited<ReturnType<ReturnType<typeof useEditableStoichiometryTable>["tableController"]["updateInventoryStock"]>>
      | undefined;

    await act(async () => {
      result = await latestValue?.tableController.updateInventoryStock([6]);
    });

    expect(result).toEqual({
      refreshedStoichiometry: {
        id: 11,
        revision: 7,
      },
      results: [
        {
          moleculeId: 6,
          moleculeName: "Cyclopentane",
          success: false,
          errorMessage: "Insufficient stock",
        },
      ],
    });
    expect(mockGetStoichiometry).toHaveBeenCalledWith({
      stoichiometryId: 11,
      revision: 7,
      token: "resolved-token",
    });
    expect(
      queryClient.getQueryData(stoichiometryQueryKeys.byId(11, 7)),
    ).toEqual({
      id: 11,
      revision: 7,
      molecules: [],
    });
  });

  it("allows stock deduction to proceed even when the link was already marked as deducted", async () => {
    const queryClient = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    let latestValue: ReturnType<typeof useEditableStoichiometryTable> | null = null;

    render(
      <QueryClientProvider client={queryClient}>
        <HookHarness
          onValue={(value) => {
            latestValue = value;
          }}
        />
      </QueryClientProvider>,
    );

    await waitFor(() => {
      expect(latestValue?.allMolecules).toHaveLength(4);
    });

    act(() => {
      const molecule = latestValue?.allMolecules.find(({ id }) => id === 6);
      if (!molecule?.inventoryLink) {
        throw new Error("Cyclopentane inventory link not found");
      }
      molecule.inventoryLink.stockDeducted = true;
    });

    await act(async () => {
      await latestValue?.tableController.updateInventoryStock([6]);
    });

    expect(mockDeductStockMutateAsync).toHaveBeenCalledWith({
      stoichiometryId: 3,
      linkIds: [502],
    });
  });
});


