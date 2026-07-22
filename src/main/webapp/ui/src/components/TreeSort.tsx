/* global $, clientUISettings, sortFileTreeBrowser, updateClientUISetting */

import { faSortAmountDown } from "@fortawesome/free-solid-svg-icons/faSortAmountDown";
import { faSortAmountUpAlt } from "@fortawesome/free-solid-svg-icons/faSortAmountUpAlt";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Box from "@mui/material/Box";
import MenuItem from "@mui/material/MenuItem";
import Select, { type SelectChangeEvent } from "@mui/material/Select";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";
import AnalyticsContext from "../stores/contexts/Analytics";

// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const $: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const clientUISettings: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const sortFileTreeBrowser: any;
// biome-ignore lint/suspicious/noExplicitAny: initial biome migration
declare const updateClientUISetting: any;

/**
 * Sorting controls for the workspace file tree.
 */
type TreeSortProps = {
  justifyContent?: React.CSSProperties["justifyContent"];
  selectPaddingLeft?: number;
};

export default function TreeSort({ justifyContent = "flex-end", selectPaddingLeft = 10 }: TreeSortProps = {}) {
  const { t } = useTranslation("common");
  const [orderBy, setOrderBy] = React.useState("name");
  const [sortOrder, setSortOrder] = React.useState("DESC");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const selectSx = {
    background: "white",
    height: "100%",
    padding: 0,
    borderRadius: 1,
    boxShadow: "0px 1px 3px 0px rgba(0,0,0,0.2),0px 1px 1px 0px rgba(0,0,0,0.14),0px 2px 1px -1px rgba(0,0,0,0.12)",
    "&::before, &::after": {
      display: "none",
    },
    "& .MuiSelect-selectMenu": {
      paddingLeft: selectPaddingLeft,
      height: 35,
      display: "flex",
      alignItems: "center",
    },
  };

  function applySettings(order: string, sort: string) {
    updateClientUISetting("treeSort", `${order}.${sort}`);
    sortFileTreeBrowser(order, sort);
    trackEvent("user:sorts:file_tree:document_editor", { order, sort });
  }

  function handleChangeOrderBy(event: SelectChangeEvent<string>) {
    setOrderBy(event.target.value);
    applySettings(event.target.value, sortOrder);
  }

  function handleChangeSortOrder(event: SelectChangeEvent<string>) {
    setSortOrder(event.target.value);
    applySettings(orderBy, event.target.value);
  }

  useEffect(() => {
    $(() => {
      if (clientUISettings) {
        const [order, sort] = clientUISettings.treeSort.split(".");
        setOrderBy(order || "name");
        setSortOrder(sort || "DESC");
      }
    });
  }, []);

  return (
    <Box
      className="sortingSettings"
      sx={{
        display: "flex",
        flexGrow: 1,
        alignItems: "center",
        justifyContent,
      }}
    >
      <Select
        className="orderBy"
        SelectDisplayProps={{ "aria-label": t("treeSort.orderLabel") }}
        value={orderBy}
        onChange={handleChangeOrderBy}
        variant="standard"
        sx={{ ...selectSx, mr: "10px" }}
      >
        <MenuItem value="name" data-test-id="order-name">
          {t("treeSort.order.name")}
        </MenuItem>
        <MenuItem value="creationdate" data-test-id="order-creation-date">
          {t("treeSort.order.creationDate")}
        </MenuItem>
        <MenuItem value="modificationdate" data-test-id="order-modification-date">
          {t("treeSort.order.lastModified")}
        </MenuItem>
      </Select>
      <Select
        className="sortOrder"
        SelectDisplayProps={{ "aria-label": t("treeSort.directionLabel") }}
        value={sortOrder}
        onChange={handleChangeSortOrder}
        variant="standard"
        sx={selectSx}
      >
        <MenuItem value="ASC" data-test-id="sort-asc">
          <FontAwesomeIcon icon={faSortAmountUpAlt} style={{ marginRight: "10px" }} />
          {t("treeSort.direction.ascending")}
        </MenuItem>
        <MenuItem value="DESC" data-test-id="sort-desc">
          <FontAwesomeIcon icon={faSortAmountDown} style={{ marginRight: "10px" }} />
          {t("treeSort.direction.descending")}
        </MenuItem>
      </Select>
    </Box>
  );
}
