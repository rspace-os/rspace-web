import React, { useEffect } from "react";
import Select from "@mui/material/Select";
import MenuItem from "@mui/material/MenuItem";
import styled from "@emotion/styled";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import {
  faSort,
  faSortAmountUpAlt,
  faSortAmountDown,
} from "@fortawesome/free-solid-svg-icons";
library.add(faSort, faSortAmountUpAlt, faSortAmountDown);

const SortWrapper = styled.div`
  display: flex;
  flex-grow: 1;
  align-items: center;
  justify-content: flex-end;

  .orderBy {
    margin-right: 10px;
  }
  .orderBy,
  .sortOrder {
    background: white;
    height: 100%;
    padding: 0px;
    border-radius: 4px;
    box-shadow: 0px 1px 3px 0px rgba(0, 0, 0, 0.2),
      0px 1px 1px 0px rgba(0, 0, 0, 0.14), 0px 2px 1px -1px rgba(0, 0, 0, 0.12);

    ::before,
    ::after {
      content: none;
    }

    .MuiSelect-selectMenu {
      padding-left: 10px;
      height: 35px;
      display: flex;
      align-items: center;
    }
  }
`;

export default function TreeSort(props) {
  const [orderBy, setOrderBy] = React.useState("name");
  const [sortOrder, setSortOrder] = React.useState("DESC");

  function handleChangeOrderBy(event) {
    setOrderBy(event.target.value);
    applySettings(event.target.value, sortOrder);
  }

  function handleChangeSortOrder(event) {
    setSortOrder(event.target.value);
    applySettings(orderBy, event.target.value);
  }

  function applySettings(order, sort) {
    updateClientUISetting("treeSort", order + "." + sort);
    sortFileTreeBrowser(order, sort);
  }

  useEffect(() => {
    $(function () {
      if (clientUISettings) {
        let [order, sort] = clientUISettings.treeSort.split(".");
        setOrderBy(order || "name");
        setSortOrder(sort || "DESC");
      }
    });
  }, []);

  return (
    <SortWrapper className="sortingSettings">
      <Select
        className="orderBy"
        value={orderBy}
        onChange={handleChangeOrderBy}
        variant="standard"
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
      >
        <MenuItem value="ASC" data-test-id="sort-asc">
          <FontAwesomeIcon
            icon="sort-amount-up-alt"
            style={{ marginRight: "10px" }}
          />
          Ascending
        </MenuItem>
        <MenuItem value="DESC" data-test-id="sort-desc">
          <FontAwesomeIcon
            icon="sort-amount-down"
            style={{ marginRight: "10px" }}
          />
          Descending
        </MenuItem>
      </Select>
    </SortWrapper>
  );
}
