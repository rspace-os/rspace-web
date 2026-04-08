import React from "react";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, waitFor } from "@testing-library/react";
import { describe, expect, it, vi, beforeEach } from "vitest";
import { inventoryQueryKeys } from "@/modules/inventory/queries";
import { stoichiometryQueryKeys } from "@/modules/stoichiometry/queries";
import { useEditableStoichiometryTable } from "@/tinyMCE/stoichiometry/useEditableStoichiometryTable";

const {
  createEditableMolecules,
  mockDeductStockMutateAsync,
  mockDeleteStoichiometryMutateAsync,
  mockGetStoichiometry,
  mockGetMoleculeInfoMutateAsync,
  mockRefreshedStoichiometry,
  mockStoichiometryQueryData,
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
  ],
  mockDeductStockMutateAsync: vi.fn(),
  mockDeleteStoichiometryMutateAsync: vi.fn(),
  mockGetStoichiometry: vi.fn(),
  mockGetMoleculeInfoMutateAsync: vi.fn(),
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
  mockUpdateStoichiometryMutateAsync: vi.fn(),
}));

vi.mock("@/hooks/auth/useOauthToken", () => ({
  default: () => ({
    getToken: vi.fn(),
  }),
}));

vi.mock("@/modules/inventory/queries", async () => {
  const actual = await vi.importActual<typeof import("@/modules/inventory/queries")>(
    "@/modules/inventory/queries",
  );

  return {
    ...actual,
    useSubSampleQuantitiesQuery: () =>
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
    mockDeductStockMutateAsync.mockResolvedValue({
      stoichiometryId: 9,
      revisionNumber: 4,
      results: [{ linkId: 502, success: true }],
    });
    mockGetStoichiometry.mockResolvedValue(mockRefreshedStoichiometry);
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
      expect(latestValue?.allMolecules).toHaveLength(3);
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
      token: undefined,
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
      expect(latestValue?.allMolecules).toHaveLength(3);
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
      token: undefined,
    });
    expect(
      queryClient.getQueryData(stoichiometryQueryKeys.byId(11, 7)),
    ).toEqual({
      id: 11,
      revision: 7,
      molecules: [],
    });
  });
});


