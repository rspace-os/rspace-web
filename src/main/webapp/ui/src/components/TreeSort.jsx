/* global $, clientUISettings, sortFileTreeBrowser, updateClientUISetting */
import React, { useEffect } from "react";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import PropTypes from "prop-types";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSortAmountUpAlt } from "@fortawesome/free-solid-svg-icons/faSortAmountUpAlt";
import { faSortAmountDown } from "@fortawesome/free-solid-svg-icons/faSortAmountDown";
import AnalyticsContext from "../stores/contexts/Analytics";

/**
 * Sorting controls for the workspace file tree.
 */
export default function TreeSort({
  justifyContent = "flex-end",
  selectPaddingLeft = 10,
} = {}) {
  const [orderBy, setOrderBy] = React.useState("name");
  const [sortOrder, setSortOrder] = React.useState("DESC");
  const { trackEvent } = React.useContext(AnalyticsContext);
  const selectSx = {
    background: "white",
    height: "100%",
    padding: 0,
    borderRadius: 1,
    boxShadow:
      "0px 1px 3px 0px rgba(0,0,0,0.2),0px 1px 1px 0px rgba(0,0,0,0.14),0px 2px 1px -1px rgba(0,0,0,0.12)",
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

  function applySettings(order, sort) {
    updateClientUISetting("treeSort", order + "." + sort);
    sortFileTreeBrowser(order, sort);
    trackEvent("user:sorts:file_tree:document_editor", { order, sort });
  }

  function handleChangeOrderBy(event) {
    setOrderBy(event.target.value);
    applySettings(event.target.value, sortOrder);
  }

  function handleChangeSortOrder(event) {
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
    <div
      className="sortingSettings"
      style={{
        display: "flex",
        flexGrow: 1,
        alignItems: "center",
        justifyContent,
      }}
    >
      <Select
        className="orderBy"
        value={orderBy}
        onChange={handleChangeOrderBy}
        variant="standard"
        sx={{ ...selectSx, mr: "10px" }}
      >
        <MenuItem value="name" data-test-id="order-name">
          Name
        </MenuItem>
        <MenuItem value="creationdate" data-test-id="order-creation-date">
          Creation Date
        </MenuItem>
        <MenuItem
          value="modificationdate"
          data-test-id="order-modification-date"
        >
          Last Modified
        </MenuItem>
      </Select>
      <Select
        className="sortOrder"
        value={sortOrder}
        onChange={handleChangeSortOrder}
        variant="standard"
        sx={selectSx}
      >
        <MenuItem value="ASC" data-test-id="sort-asc">
          <FontAwesomeIcon
            icon={faSortAmountUpAlt}
            style={{ marginRight: "10px" }}
          />
          Ascending
        </MenuItem>
        <MenuItem value="DESC" data-test-id="sort-desc">
          <FontAwesomeIcon
            icon={faSortAmountDown}
            style={{ marginRight: "10px" }}
          />
          Descending
        </MenuItem>
      </Select>
    </div>
  );
}

TreeSort.propTypes = {
  justifyContent: PropTypes.string,
  selectPaddingLeft: PropTypes.number,
};
