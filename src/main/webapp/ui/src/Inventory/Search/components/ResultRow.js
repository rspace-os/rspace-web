// @flow

import React, { useContext, type Node, type ComponentType } from "react";
import useStores from "../../../stores/use-stores";
import { observer } from "mobx-react-lite";
import SearchContext from "../../../stores/contexts/Search";
import NavigateContext from "../../../stores/contexts/Navigate";
import { type AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
import TableCell from "./TableCell";
import TableRow from "@mui/material/TableRow";
import Checkbox from "@mui/material/Checkbox";
import { makeStyles } from "tss-react/mui";
import NameWithBadge from "../../components/NameWithBadge";
import clsx from "clsx";
import { globalStyles } from "../../../theme";
import AdjustableCell from "../../components/Tables/AdjustableCell";
import { match } from "../../../util/Util";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { UserCancelledAction } from "../../../util/error";

type ResultRowArgs = {|
  result: InventoryRecord,
  adjustableColumns: Array<AdjustableTableRowLabel>,
|};

const useStyles = makeStyles()((theme) => ({
  tableRow: {
    transition: theme.transitions.filterToggle,
    "&.Mui-selected": {
      backgroundColor: "#e3f2fd",
    },
    "&.Mui-selected:hover": {
      backgroundColor: "rgba(0, 0, 0, 0.04) !important",
    },
  },
  checkbox: {
    padding: `${theme.spacing(0.25)} !important`,
  },
  defaultCursor: {
    cursor: "default",
  },
}));

function ResultRow({ result, adjustableColumns }: ResultRowArgs): Node {
  const {
    isChild,
    differentSearchForSettingActiveResult,
    search,
    scopedResult,
  } = useContext(SearchContext);
  const multiselect = search.uiConfig.selectionMode === "MULTIPLE";
  const noSelection = search.uiConfig.selectionMode === "NONE";
  const { uiStore } = useStores();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { classes: globalClasses } = globalStyles();
  const { classes } = useStyles();

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

  return (
    <TableRow
      hover={!uiStore.isSingleColumnLayout}
      tabIndex={-1}
      className={clsx(
        classes.tableRow,
        search.alwaysFilterOut(result) && globalClasses.greyOut
      )}
      onClick={match<void, () => void>([
        [() => noSelection, () => {}],
        [() => Boolean(isChild), navigateToResult],
        [() => true, activateResult],
      ])()}
      selected={
        search.activeResult &&
        result.globalId === search.activeResult.globalId &&
        search.uiConfig.highlightActiveResult
      }
    >
      {multiselect && (
        <TableCell scope="row" align="left" className={classes.checkbox}>
          <Checkbox
            checked={result.selected}
            onChange={() => result.toggleSelected()}
            onClick={(e) => e.stopPropagation()}
            color="default"
            name="Select result item"
            inputProps={{ "aria-label": "Select result item" }}
            className={classes.defaultCursor}
          />
        </TableCell>
      )}
      <TableCell align="left" className={classes.defaultCursor}>
        <NameWithBadge record={result} />
      </TableCell>
      <AdjustableCell
        dataSource={result}
        selectedOption={adjustableColumns[0]}
      />
      {!uiStore.isSmall &&
        !uiStore.isVerySmall &&
        uiStore.isSingleColumnLayout && (
          <AdjustableCell
            dataSource={result}
            selectedOption={adjustableColumns[1]}
          />
        )}
      {uiStore.isLarge && uiStore.isSingleColumnLayout && (
        <AdjustableCell
          dataSource={result}
          selectedOption={adjustableColumns[2]}
        />
      )}
    </TableRow>
  );
}

export default (observer(ResultRow): ComponentType<ResultRowArgs>);
