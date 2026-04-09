import React, { useCallback } from "react";
import { produce } from "immer";
import { useForm, useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import AnalyticsContext from "../../stores/contexts/Analytics";
import useOauthToken from "@/hooks/auth/useOauthToken";
import {
  inventoryQueryKeys,
  useSubSampleQuantitiesQuery,
} from "@/modules/inventory/queries";
import {
  useDeductStockMutation,
  useDeleteStoichiometryMutation,
  useGetMoleculeInfoMutation,
  useUpdateStoichiometryMutation,
} from "@/modules/stoichiometry/mutations";
import {
  getStoichiometry,
  stoichiometryQueryKeys,
  useGetStoichiometryQuery,
} from "@/modules/stoichiometry/queries";
import type {
  ExistingMoleculeUpdate,
  NewMolecule,
  RsChemElement,
  StoichiometryRequest,
} from "@/modules/stoichiometry/schema";
import { resolveToken } from "@/modules/common/utils/auth";
import { toEditableMolecules } from "@/tinyMCE/stoichiometry/editableMolecules";
import type { StoichiometryTableController } from "@/tinyMCE/stoichiometry/StoichiometryTableControllerContext";
import type { EditableMolecule } from "@/tinyMCE/stoichiometry/types";
import {
  calculateMoles,
  calculateUpdatedMolecules,
  getInventoryUpdateEligibility,
  hasDuplicateInventoryLink,
} from "@/tinyMCE/stoichiometry/utils";

type StoichiometryFormValues = {
  allMolecules: Array<EditableMolecule>;
};

function toStoichiometryRequest(
  stoichiometryId: number,
  molecules: ReadonlyArray<EditableMolecule>,
): StoichiometryRequest {
  return {
    id: stoichiometryId,
    molecules: molecules.map((m) => {
      if (m.id >= 0) {
        const existingMoleculeUpdate: ExistingMoleculeUpdate = {
          id: m.id,
          role: m.role,
          smiles: m.smiles,
          name: m.name ?? undefined,
          formula: m.formula ?? undefined,
          molecularWeight: m.molecularWeight ?? undefined,
          coefficient: m.coefficient ?? undefined,
          mass: m.mass,
          actualAmount: m.actualAmount,
          actualYield: m.actualYield,
          limitingReagent: m.limitingReagent ?? undefined,
          notes: m.notes,
          inventoryLink: m.inventoryLink ?? null,
        };
        return existingMoleculeUpdate;
      }

      const smiles = m.smiles.trim();
      if (!smiles) {
        throw new Error("New reagents must have a SMILES string");
      }

      const name = m.name?.trim();
      if (!name) {
        throw new Error("New reagents must have a name");
      }

      return {
        role: "AGENT",
        smiles,
        name,
        coefficient: m.coefficient ?? undefined,
        mass: m.mass,
        actualAmount: m.actualAmount,
        actualYield: m.actualYield,
        limitingReagent: m.limitingReagent ?? undefined,
        notes: m.notes,
        inventoryLink: m.inventoryLink ?? null,
      } as NewMolecule;
    }),
  };
}

function serialiseStoichiometryRequest(
  stoichiometryId: number,
  molecules: ReadonlyArray<EditableMolecule>,
): string {
  return JSON.stringify(toStoichiometryRequest(stoichiometryId, molecules));
}

function normaliseMoleculesAfterSave(
  molecules: ReadonlyArray<EditableMolecule>,
): Array<EditableMolecule> {
  return produce(Array.from(molecules), (draftMolecules) => {
    for (const molecule of draftMolecules) {
      molecule.savedInventoryLink = molecule.inventoryLink ?? null;
      molecule.deletedInventoryLink = null;
    }
  });
}

export function useEditableStoichiometryTable({
  stoichiometryId,
  stoichiometryRevision,
}: {
  stoichiometryId: number;
  stoichiometryRevision: number;
}) {
  const { getToken } = useOauthToken();
  const { trackEvent } = React.useContext(AnalyticsContext);
  const queryClient = useQueryClient();
  const updateStoichiometryMutation = useUpdateStoichiometryMutation({
    getToken,
  });
  const deductStockMutation = useDeductStockMutation({
    getToken,
  });
  const deleteStoichiometryMutation = useDeleteStoichiometryMutation({
    getToken,
  });
  const getMoleculeInfoMutation = useGetMoleculeInfoMutation({
    getToken,
  });
  const { data: queriedStoichiometry, isFetching } = useGetStoichiometryQuery({
    stoichiometryId,
    revision: stoichiometryRevision,
    getToken,
  });
  const data = queriedStoichiometry;
  const stoichiometryVersionKey = `${queriedStoichiometry.id}:${queriedStoichiometry.revision}`;
  const initialMolecules = React.useMemo(
    () => toEditableMolecules(queriedStoichiometry),
    [stoichiometryVersionKey],
  );
  const [formDefaultValues, setFormDefaultValues] = React.useState<
    StoichiometryFormValues
  >(() => ({
    allMolecules: Array.from(initialMolecules),
  }));
  React.useEffect(() => {
    setFormDefaultValues({
      allMolecules: Array.from(initialMolecules),
    });
  }, [stoichiometryVersionKey]);
  const versionFormDefaultValues = React.useMemo(
    () => ({
      allMolecules: Array.from(initialMolecules),
    } satisfies StoichiometryFormValues),
    [stoichiometryVersionKey],
  );
  const initialisedVersionRef = React.useRef<string | null>(null);
  const [baselineSerialisation, setBaselineSerialisation] = React.useState(
    () => serialiseStoichiometryRequest(queriedStoichiometry.id, initialMolecules),
  );
  const form = useForm({
    defaultValues: formDefaultValues,
  });
  const allMolecules = useStore(
    form.store,
    (state) => state.values.allMolecules as ReadonlyArray<EditableMolecule>,
  );
  const isSaving = updateStoichiometryMutation.isPending;
  const isUpdatingInventoryStock = deductStockMutation.isPending;
  const isDeleting = deleteStoichiometryMutation.isPending;
  const isGettingMoleculeInfo = getMoleculeInfoMutation.isPending;
  const isBusy =
    isSaving ||
    isUpdatingInventoryStock ||
    isDeleting ||
    isGettingMoleculeInfo ||
    isFetching;
  const linkedInventoryItemGlobalIds = React.useMemo(
    () =>
      Array.from(
        new Set(
          allMolecules
            .map((molecule) => molecule.inventoryLink?.inventoryItemGlobalId)
            .filter((id): id is string => Boolean(id)),
        ),
      ),
    [allMolecules],
  );
  const subSampleQuantitiesByGlobalId = useSubSampleQuantitiesQuery({
    inventoryItemGlobalIds: linkedInventoryItemGlobalIds,
    getToken,
  });
  const hasChanges = React.useMemo(
    () =>
      serialiseStoichiometryRequest(queriedStoichiometry.id, allMolecules) !==
      baselineSerialisation,
    [allMolecules, baselineSerialisation, queriedStoichiometry.id],
  );

  const updateAllMolecules = useCallback(
    (
      updater: (
        previousMolecules: ReadonlyArray<EditableMolecule>,
      ) => Array<EditableMolecule>,
    ) => {
      form.setFieldValue(
        "allMolecules",
        (previousMolecules) =>
          updater(
            (previousMolecules ?? []) as ReadonlyArray<EditableMolecule>,
          ),
        {
          dontValidate: true,
        },
      );
    },
    [form],
  );

  React.useEffect(() => {
    if (initialisedVersionRef.current === stoichiometryVersionKey) {
      return;
    }

    initialisedVersionRef.current = stoichiometryVersionKey;
    setFormDefaultValues(versionFormDefaultValues);
    form.reset(versionFormDefaultValues);
    setBaselineSerialisation(
      serialiseStoichiometryRequest(queriedStoichiometry.id, initialMolecules),
    );
  }, [
    form,
    initialMolecules,
    queriedStoichiometry.id,
    stoichiometryVersionKey,
    versionFormDefaultValues,
  ]);

  const save = useCallback(async () => {
    if (!data || !data.id) {
      throw new Error("No stoichiometry data to save");
    }

    const updatedData = toStoichiometryRequest(data.id, allMolecules);

    const updatedStoichiometry = await updateStoichiometryMutation.mutateAsync({
      stoichiometryId: data.id,
      stoichiometryData: updatedData,
    });

    const savedMolecules = normaliseMoleculesAfterSave(allMolecules);
    const nextFormDefaultValues = {
      allMolecules: savedMolecules,
    } satisfies StoichiometryFormValues;
    setFormDefaultValues(nextFormDefaultValues);
    form.reset(nextFormDefaultValues);
    setBaselineSerialisation(serialiseStoichiometryRequest(data.id, savedMolecules));
    return updatedStoichiometry.revision;
  }, [allMolecules, data, form, updateStoichiometryMutation]);

  const deleteTable = useCallback(async () => {
    if (!data || !data.id) {
      return;
    }

    await deleteStoichiometryMutation.mutateAsync({
      stoichiometryId: data.id,
    });
    const nextFormDefaultValues = {
      allMolecules: [],
    } satisfies StoichiometryFormValues;
    setFormDefaultValues(nextFormDefaultValues);
    form.reset(nextFormDefaultValues);
    setBaselineSerialisation(serialiseStoichiometryRequest(data.id, []));
  }, [data, deleteStoichiometryMutation, form]);

  const addReagent = useCallback(
    async (smilesString: string, name: string, source: string) => {
      trackEvent("user:add:stoichiometry_reagent:document_editor", {
        source,
      });

      if (isGettingMoleculeInfo) {
        throw new Error(
          "Please wait for the current reagent to be processed before adding another.",
        );
      }

      try {
        const temporaryIdBase = -Date.now();

        const moleculeInfo = await getMoleculeInfoMutation.mutateAsync({
          smiles: smilesString,
        });

        const limitingReagent = allMolecules.find((m) => m.limitingReagent);

        const limitingReagentMoles = limitingReagent ? calculateMoles(
          limitingReagent.mass,
          limitingReagent.molecularWeight,
        ) : null;
        const ratio =
          !limitingReagent?.coefficient || limitingReagentMoles === null
            ? null
            : limitingReagentMoles / limitingReagent.coefficient;

        updateAllMolecules((prevMolecules) =>
          produce(Array.from(prevMolecules), (draftMolecules) => {
            const usedIds = new Set(draftMolecules.map(({ id }) => id));
            let tempId = temporaryIdBase;

            while (usedIds.has(tempId)) {
              tempId -= 1;
            }

            const mockRsChemElement: RsChemElement = {
              id: tempId,
              parentId: null,
              ecatChemFileId: null,
              dataImage: null,
              chemElements: smilesString,
              smilesString,
              chemId: null,
              reactionId: null,
              rgroupId: null,
              metadata: null,
              chemElementsFormat: "SMILES",
              creationDate: Date.now(),
              imageFileProperty: null,
            };

            const newMolecule: EditableMolecule = {
              id: tempId,
              rsChemElement: mockRsChemElement,
              inventoryLink: null,
              savedInventoryLink: null,
              deletedInventoryLink: null,
              role: "AGENT",
              formula: moleculeInfo.formula,
              name,
              smiles: smilesString,
              coefficient: 1,
              molecularWeight: moleculeInfo.molecularWeight,
              mass: ratio ? ratio * moleculeInfo.molecularWeight : 0,
              moles: null,
              actualAmount: null,
              actualMoles: null,
              actualYield: null,
              limitingReagent: false,
              notes: null,
            };

            draftMolecules.push(newMolecule);
          }),
        );
      } catch (error) {
        console.error("Failed to fetch molecule information:", error);
      }
    },
    [
      allMolecules,
      getMoleculeInfoMutation,
      isGettingMoleculeInfo,
      trackEvent,
      updateAllMolecules,
    ],
  );

  const pickInventoryLink = useCallback(
    (moleculeId: number, inventoryItemId: number, inventoryItemGlobalId: string) => {
      const molecule = allMolecules.find((m) => m.id === moleculeId);
      if (!molecule) {
        return;
      }

      // Cannot pick a new link if the current one has not been removed yet (not allowed in the UI either way)
      if (molecule.deletedInventoryLink && !molecule.inventoryLink) {
        return;
      }

      // Don't duplicate links
      if (molecule.inventoryLink?.inventoryItemGlobalId === inventoryItemGlobalId) {
        return;
      }

      if (
        hasDuplicateInventoryLink(
          allMolecules,
          moleculeId,
          inventoryItemGlobalId,
        )
      ) {
        return;
      }

      updateAllMolecules((prevMolecules) =>
        produce(Array.from(prevMolecules), (draftMolecules) => {
          const draftMolecule = draftMolecules.find((m) => m.id === moleculeId);
          if (!draftMolecule) {
            return;
          }

          const existingLink = draftMolecule.inventoryLink;
          draftMolecule.inventoryLink = {
            id: existingLink?.id ?? inventoryItemId,
            inventoryItemGlobalId,
            stoichiometryMoleculeId: draftMolecule.id,
            quantity: existingLink?.quantity,
            stockDeducted: existingLink?.stockDeducted,
          };
          draftMolecule.deletedInventoryLink = null;
        }),
      );
    },
    [allMolecules, updateAllMolecules],
  );

  const removeInventoryLink = useCallback(
    (moleculeId: number) => {
      const molecule = allMolecules.find((m) => m.id === moleculeId);
      if (!molecule?.inventoryLink) {
        return;
      }

      const shouldSoftDeleteSavedLink =
        molecule.savedInventoryLink?.inventoryItemGlobalId ===
        molecule.inventoryLink.inventoryItemGlobalId;

      updateAllMolecules((prevMolecules) =>
        produce(Array.from(prevMolecules), (draftMolecules) => {
          const draftMolecule = draftMolecules.find((m) => m.id === moleculeId);
          if (!draftMolecule?.inventoryLink) {
            return;
          }

          if (shouldSoftDeleteSavedLink) {
            draftMolecule.deletedInventoryLink = draftMolecule.inventoryLink;
          } else {
            draftMolecule.deletedInventoryLink = null;
          }

          draftMolecule.inventoryLink = null;
        }),
      );
    },
    [allMolecules, updateAllMolecules],
  );

  const undoRemoveInventoryLink = useCallback(
    (moleculeId: number) => {
      const molecule = allMolecules.find((m) => m.id === moleculeId);
      if (!molecule?.deletedInventoryLink || molecule.inventoryLink) {
        return;
      }

      updateAllMolecules((prevMolecules) =>
        produce(Array.from(prevMolecules), (draftMolecules) => {
          const draftMolecule = draftMolecules.find((m) => m.id === moleculeId);
          if (!draftMolecule?.deletedInventoryLink || draftMolecule.inventoryLink) {
            return;
          }

          draftMolecule.inventoryLink = draftMolecule.deletedInventoryLink;
          draftMolecule.deletedInventoryLink = null;
        }),
      );
    },
    [allMolecules, updateAllMolecules],
  );

  const invalidateLinkedInventoryAmounts = useCallback(
    async (inventoryItemGlobalIds: ReadonlyArray<string>) => {
      const uniqueInventoryItemGlobalIdSet = new Set(
        inventoryItemGlobalIds.filter(Boolean),
      );

      if (uniqueInventoryItemGlobalIdSet.size === 0) {
        return;
      }

      await queryClient.invalidateQueries({
        queryKey: inventoryQueryKeys.all,
        predicate: (query) => {
          const [, keyType, inventoryItemGlobalId] = query.queryKey;

          return (
            keyType === "subSampleQuantity" &&
            typeof inventoryItemGlobalId === "string" &&
            uniqueInventoryItemGlobalIdSet.has(inventoryItemGlobalId)
          );
        },
      });
    },
    [queryClient],
  );

  const updateInventoryStock = useCallback(
    async (selectedMoleculeIds: number[]) => {
      if (!data?.id) {
        throw new Error("No stoichiometry data available to update stock");
      }

      const preflightResults: Array<{
        moleculeId: number;
        moleculeName: string;
        success: boolean;
        errorMessage: string | null;
      }> = [];
      const deductibleLinks: Array<{
        linkId: number;
        moleculeId: number;
        moleculeName: string;
        inventoryItemGlobalId: string;
      }> = [];

      for (const moleculeId of selectedMoleculeIds) {
        const molecule = allMolecules.find(({ id }) => id === moleculeId);
        const moleculeName = molecule?.name ?? "Unnamed molecule";
        const inventoryItemGlobalId = molecule?.inventoryLink?.inventoryItemGlobalId;

        if (
          !molecule ||
          !inventoryItemGlobalId ||
          typeof molecule.inventoryLink?.id !== "number"
        ) {
          preflightResults.push({
            moleculeId,
            moleculeName,
            success: false,
            errorMessage: "Link an inventory item before updating stock.",
          });
          continue;
        }

        const eligibility = getInventoryUpdateEligibility(
          molecule,
          subSampleQuantitiesByGlobalId,
        );

        if (eligibility.disabledReason !== null) {
          preflightResults.push({
            moleculeId,
            moleculeName,
            success: false,
            errorMessage:
              eligibility.helperText ??
              eligibility.stockDisplay.warningText ??
              "This molecule cannot be updated.",
          });
          continue;
        }

        deductibleLinks.push({
          linkId: molecule.inventoryLink.id,
          moleculeId,
          moleculeName,
          inventoryItemGlobalId,
        });
      }

      if (deductibleLinks.length === 0) {
        return {
          results: preflightResults,
        };
      }

      const attemptedInventoryItemGlobalIds = deductibleLinks.map(
        ({ inventoryItemGlobalId }) => inventoryItemGlobalId,
      );

      try {
        const deductionResult = await deductStockMutation.mutateAsync({
          stoichiometryId: data.id,
          linkIds: deductibleLinks.map(({ linkId }) => linkId),
        });
        const deductionResultByLinkId = new Map(
          deductionResult.results.map((result) => [result.linkId, result]),
        );
        const batchResults = deductibleLinks.map(
          ({ linkId, moleculeId, moleculeName }) => {
            const result = deductionResultByLinkId.get(linkId);

            if (!result) {
              return {
                moleculeId,
                moleculeName,
                success: false,
                errorMessage:
                  "No deduction result was returned for this molecule.",
              };
            }

            return {
              moleculeId,
              moleculeName,
              success: result.success,
              errorMessage: result.success
                ? null
                : result.errorMessage ?? "Failed to update inventory stock.",
            };
          },
        );

        await queryClient.invalidateQueries({
          queryKey: stoichiometryQueryKeys.all,
        });

        const refreshedStoichiometry = await queryClient.fetchQuery({
          queryKey: stoichiometryQueryKeys.byId(
            deductionResult.stoichiometryId,
            deductionResult.revisionNumber,
          ),
          queryFn: async () =>
            getStoichiometry({
              stoichiometryId: deductionResult.stoichiometryId,
              revision: deductionResult.revisionNumber,
              token: await resolveToken({ getToken }),
            }),
        });

        queryClient.setQueryData(
          stoichiometryQueryKeys.byId(
            deductionResult.stoichiometryId,
            deductionResult.revisionNumber,
          ),
          refreshedStoichiometry,
        );

        await invalidateLinkedInventoryAmounts(linkedInventoryItemGlobalIds);

        return {
          refreshedStoichiometry: {
            id: refreshedStoichiometry.id,
            revision: refreshedStoichiometry.revision,
          },
          results: [...preflightResults, ...batchResults],
        };
      } catch (error) {
        await invalidateLinkedInventoryAmounts(attemptedInventoryItemGlobalIds);
        throw error;
      }
    },
    [
      allMolecules,
      data,
      deductStockMutation,
      getToken,
      invalidateLinkedInventoryAmounts,
      linkedInventoryItemGlobalIds,
      queryClient,
      subSampleQuantitiesByGlobalId,
    ],
  );

  const deleteReagent = useCallback(
    (moleculeId: number) => {
      const moleculeToDelete = allMolecules.find((m) => m.id === moleculeId);
      if (!moleculeToDelete || moleculeToDelete.role.toLowerCase() !== "agent") {
        return;
      }
      updateAllMolecules((prevMolecules) =>
        prevMolecules.filter((m) => m.id !== moleculeId),
      );
    },
    [allMolecules, updateAllMolecules],
  );

  const selectLimitingReagent = useCallback(
    (molecule: EditableMolecule) => {
      const updatedRow = { ...molecule, limitingReagent: true };
      const newMolecules = calculateUpdatedMolecules(allMolecules, updatedRow);
      form.setFieldValue("allMolecules", Array.from(newMolecules), {
        dontValidate: true,
      });
    },
    [allMolecules, form],
  );

  const processRowUpdate = useCallback(
    (newRow: EditableMolecule, oldRow: EditableMolecule) => {
      try {
        const numericalFields = [
          "coefficient",
          "mass",
          "moles",
          "actualAmount",
          "actualMoles",
        ];
        for (const field of numericalFields) {
          const value = newRow[field as keyof EditableMolecule];
          if (
            value !== null &&
            value !== undefined &&
            Number(value) < 0
          ) {
            throw new Error(`${field} cannot be negative`);
          }
        }

        const newMolecules = calculateUpdatedMolecules(allMolecules, newRow);
        form.setFieldValue("allMolecules", Array.from(newMolecules), {
          dontValidate: true,
        });
        return newMolecules.find((m) => m.id === newRow.id) || oldRow;
      } catch (error) {
        console.error("Error updating row:", (error as Error).message);
        return oldRow;
      }
    },
    [allMolecules, form],
  );

  const tableController = React.useMemo<StoichiometryTableController>(
    () => ({
      allMolecules,
      linkedInventoryQuantityInfoByGlobalId: subSampleQuantitiesByGlobalId,
      isGettingMoleculeInfo,
      addReagent,
      deleteReagent,
      updateInventoryStock,
      pickInventoryLink,
      removeInventoryLink,
      undoRemoveInventoryLink,
      selectLimitingReagent,
      processRowUpdate,
    }),
    [
      addReagent,
      allMolecules,
      deleteReagent,
      isGettingMoleculeInfo,
      pickInventoryLink,
      processRowUpdate,
      removeInventoryLink,
      selectLimitingReagent,
      subSampleQuantitiesByGlobalId,
      undoRemoveInventoryLink,
      updateInventoryStock,
    ],
  );

  return {
    data,
    allMolecules,
    hasChanges,
    isBusy,
    isDeleting,
    isGettingMoleculeInfo,
    isSaving,
    tableController,
    save,
    deleteTable,
  };
}

