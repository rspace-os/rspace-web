import React, { useContext } from "react";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import SearchContext from "../../../stores/contexts/Search";
import NavigateContext from "../../../stores/contexts/Navigate";
import { type AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
import TableCell from "./TableCell";
import TableRow from "@mui/material/TableRow";
import Checkbox from "@mui/material/Checkbox";
import NameWithBadge from "../../components/NameWithBadge";
import AdjustableCell from "../../components/Tables/AdjustableCell";
import { match } from "../../../util/Util";
import {
  hasRequiredPermissions,
  type InventoryRecord,
} from "../../../stores/definitions/InventoryRecord";
import { UserCancelledAction } from "../../../util/error";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";
import { alpha } from "@mui/material/styles";
import Radio from "@mui/material/Radio";
import Tooltip from "@mui/material/Tooltip";

type ResultRowArgs = {
  result: InventoryRecord;
  adjustableColumns: Array<AdjustableTableRowLabel>;
};

const REQUIRED_PERMISSIONS_TOOLTIP =
  "You do not have permission to select this item.";

function ResultRow({
  result,
  adjustableColumns,
}: ResultRowArgs): React.ReactNode {
  const {
    isChild,
    differentSearchForSettingActiveResult,
    search,
    scopedResult,
  } = useContext(SearchContext);
  const multiSelect = search.uiConfig.selectionMode === "MULTIPLE";
  const singleSelect = search.uiConfig.selectionMode === "SINGLE";
  const noSelection = search.uiConfig.selectionMode === "NONE";
  const { uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const isFilteredOut = search.alwaysFilterOut(result);
  const hasPermission = hasRequiredPermissions(
    result.permittedActions,
    search.uiConfig?.requiredPermissions,
  );
  const rowIsFilteredOut = isFilteredOut || !hasPermission;
  const filteredOutReason = isFilteredOut
    ? search.uiConfig?.alwaysFilteredOutReason
    : undefined;
  const tooltipText =
    filteredOutReason ??
    (!hasPermission ? REQUIRED_PERMISSIONS_TOOLTIP : undefined);
  const rowIsSelected = Boolean(
    search.activeResult &&
      result.globalId === search.activeResult.globalId &&
      search.uiConfig.highlightActiveResult,
  );

  /*
   * Here we use `differentSearchForSettingActiveResult` because there are
   * various places where list view is used for selecting records where
   * slightly different actions are taken. In the right panel, tapping a row
   * will set that record as the `activeResult` of `searchStore` (thereby
   * changing the contents of the right panel). In the move dialog, the right
   * panel of the dialog is updated to show the current contents of the
   * container. In the template picker, tapping the row will set the
   * `activeResult` of the picker's search which will ultimately change the
   * `template` of the new sample that is the `searchStore`'s `activeResult`.
   * Ultimately, this means that replacing
   * `differentSearchForSettingActiveResult` with a solution that just looks at
   * the current search context and its parent is not sufficiently flexible.
   */

  const activateResult = () => {
    differentSearchForSettingActiveResult
      .setActiveResult(result)
      .then(() => {
        uiStore.setVisiblePanel("right");
      })
      .catch((e) => {
        if (e instanceof UserCancelledAction) return;
        throw e;
      });
  };

  const navigateToResult = () => {
    if (!scopedResult)
      throw new Error("A scoped record has not been assigned to this search");
    const params =
      differentSearchForSettingActiveResult.fetcher.generateNewQuery({
        parentGlobalId: scopedResult.globalId,
      });
    navigate(`/inventory/search?${params.toString()}`, {
      skipToParentContext: true,
    });
    activateResult();
  };

  const tableRow = (
    <TableRow
      hover={!isSingleColumnLayout && !rowIsFilteredOut}
      tabIndex={-1}
      aria-disabled={rowIsFilteredOut || undefined}
      sx={(theme) => ({
        transition: theme.transitions.filterToggle,
        ...(rowIsFilteredOut && {
          filter: "grayscale(1)",
          opacity: 0.6,
          ...(!tooltipText && {
            pointerEvents: "none !important",
          }),
        }),
        "&.Mui-selected": {
          backgroundColor: theme.palette.primary.background,
          color: theme.palette.primary.contrastText,
          "& .MuiTableCell-root": {
            color: theme.palette.primary.contrastText,
          },
        },
        "@media (prefers-contrast: more)": {
          "&.Mui-selected .MuiCheckbox-root, &.Mui-selected .MuiRadio-root": {
            color: theme.palette.primary.contrastText,
          },
        },
        ...(!rowIsFilteredOut && {
          "&.Mui-selected:hover": {
            backgroundColor: `${alpha(theme.palette.primary.background, 0.8)} !important`,
          },
          "&:hover": {
            backgroundColor: `${alpha(theme.palette.primary.background, 0.2)} !important`,
          },
        }),
      })}
      onClick={match<void, () => void>([
        [() => rowIsFilteredOut, () => {}],
        [() => noSelection, () => {}],
        [() => Boolean(isChild), navigateToResult],
        [() => true, activateResult],
      ])()}
      selected={rowIsSelected}
    >
      {multiSelect && (
        <TableCell
          scope="row"
          align="left"
          sx={(theme) => ({
            padding: `${theme.spacing(0.25)} !important`,
          })}
        >
          <Checkbox
            checked={result.selected}
            disabled={rowIsFilteredOut}
            onChange={() => result.toggleSelected()}
            onClick={(e) => e.stopPropagation()}
            name={`Select result ${result.globalId}`}
            inputProps={{ "aria-label": "Select result item" }}
            sx={{ cursor: "default" }}
          />
        </TableCell>
      )}
      {singleSelect && (
        <TableCell
          scope="row"
          align="left"
          sx={(theme) => ({
            padding: `${theme.spacing(0.25)} !important`,
          })}
        >
          <Radio
            checked={Boolean(
              search.activeResult &&
                result.globalId === search.activeResult.globalId,
            )}
            disabled={rowIsFilteredOut}
            onChange={() => activateResult()}
            onClick={(e) => e.stopPropagation()}
            name={`Select result ${result.globalId}`}
            inputProps={{ "aria-label": "Select result item" }}
            sx={{ cursor: "default" }}
          />
        </TableCell>
      )}
      <TableCell align="left" sx={{ cursor: "default" }}>
        <NameWithBadge record={result} />
      </TableCell>
      <AdjustableCell
        dataSource={result}
        selectedOption={adjustableColumns[0]}
      />
      {!uiStore.isSmall && !uiStore.isVerySmall && isSingleColumnLayout && (
        <AdjustableCell
          dataSource={result}
          selectedOption={adjustableColumns[1]}
        />
      )}
      {uiStore.isLarge && isSingleColumnLayout && (
        <AdjustableCell
          dataSource={result}
          selectedOption={adjustableColumns[2]}
        />
      )}
    </TableRow>
  );

  return tooltipText ? (
    <Tooltip title={tooltipText} describeChild>
      {tableRow}
    </Tooltip>
  ) : (
    tableRow
  );
}

export default observer(ResultRow);
