import Chip from "@mui/material/Chip";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import DropdownButton from "../../../components/DropdownButton";
import { useIntegrationIsAllowedAndEnabled } from "../../../hooks/api/integrationHelpers";
import { useDeploymentProperty } from "../../../hooks/api/useDeploymentProperty";
import SearchContext from "../../../stores/contexts/Search";
import type { ChemicalFilter } from "../../../stores/definitions/Search";
import * as FetchingData from "../../../util/fetchingData";
import * as Parser from "../../../util/parsers";
import ChemicalStructureDialog from "./ChemicalStructureDialog";

const MAX_CHIP_STRUCTURE_LENGTH = 30;

/**
 * Structure search is offered under the same conditions as the Workspace
 * chemical search: the Indigo chemistry service is configured, and the
 * Chemistry app is allowed by the sysadmin and enabled by the user.
 */
export function useStructureSearchAvailable(): boolean {
  const chemistryAppEnabled = FetchingData.getSuccessValue(useIntegrationIsAllowedAndEnabled("CHEMISTRY")).orElse(
    false,
  );
  const indigoConfigured = FetchingData.getSuccessValue(useDeploymentProperty("chemistry.provider"))
    .flatMap(Parser.isString)
    .map((provider) => provider === "indigo")
    .orElse(false);
  return chemistryAppEnabled && indigoConfigured;
}

function useCurrentChemicalFilter(): ChemicalFilter | null {
  const { search } = useContext(SearchContext);
  const { chemQuery, chemSearchType } = search.fetcher;
  return chemQuery ? { chemQuery, chemSearchType: chemSearchType ?? "SUBSTRUCTURE" } : null;
}

/** The "Chemical" button in the search filter row. */
export const ChemicalFilterButton = observer(function ChemicalFilterButton(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const { search } = useContext(SearchContext);
  const currentFilter = useCurrentChemicalFilter();
  const available = useStructureSearchAvailable();
  const [open, setOpen] = useState(false);

  if (!available) return null;
  return (
    <DropdownButton
      name={t("search.controls.chemical.label")}
      onClick={() => setOpen(true)}
      disabled={search.chemicalFilterConflict}
      disabledReason={t("search.controls.chemical.blockedByScope")}
    >
      <ChemicalStructureDialog
        open={open}
        initialFilter={currentFilter}
        onSubmit={(filter) => {
          setOpen(false);
          search.setChemicalFilter(filter);
        }}
        onClose={() => setOpen(false)}
      />
    </DropdownButton>
  );
});

/**
 * Shows the active structure filter. Clicking it reopens the editor with the
 * structure loaded; it turns to an error state when the search fails, e.g.
 * when a saved search with a structure is opened while chemistry is
 * unavailable.
 */
export const ChemicalFilterChip = observer(function ChemicalFilterChip(): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);
  const { search } = useContext(SearchContext);
  const currentFilter = useCurrentChemicalFilter();
  const available = useStructureSearchAvailable();
  const [open, setOpen] = useState(false);

  if (!currentFilter) return null;
  const { chemQuery } = currentFilter;
  const matchType =
    currentFilter.chemSearchType === "EXACT"
      ? t("common:ketcher.structureSearch.searchTypeExact")
      : t("common:ketcher.structureSearch.searchTypeSubstructure");
  const shortStructure =
    chemQuery.length > MAX_CHIP_STRUCTURE_LENGTH ? `${chemQuery.slice(0, MAX_CHIP_STRUCTURE_LENGTH - 1)}…` : chemQuery;

  return (
    <>
      <Chip
        size="small"
        color={search.fetcher.error ? "error" : "default"}
        label={t("search.parameterChips.chemical", { matchType, structure: shortStructure })}
        {...(available
          ? {
              onClick: () => setOpen(true),
              "aria-label": t("search.parameterChips.chemicalEdit", { matchType, structure: chemQuery }),
            }
          : {})}
        onDelete={() => search.setChemicalFilter(null)}
      />
      <ChemicalStructureDialog
        open={open}
        initialFilter={currentFilter}
        onSubmit={(filter) => {
          setOpen(false);
          search.setChemicalFilter(filter);
        }}
        onClose={() => setOpen(false)}
      />
    </>
  );
});
