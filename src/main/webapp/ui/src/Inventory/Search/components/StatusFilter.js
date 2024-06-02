//@flow

import React, { type Node, useContext } from "react";
import ListItemText from "@mui/material/ListItemText";
import { StyledMenu, StyledMenuItem } from "../../../components/StyledMenu";
import { type DeletedItems } from "../../../stores/definitions/Search";
import SearchContext from "../../../stores/contexts/Search";

type StatusFilterArgs = {|
  anchorEl: ?HTMLElement,
  current: DeletedItems,
  onClose: (DeletedItems) => void,
|};

export default function StatusFilter({
  anchorEl,
  onClose,
  current,
}: StatusFilterArgs): Node {
  const { search } = useContext(SearchContext);
  return (
    <div data-test-id="statusDropdown">
      <StyledMenu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={() => {
          onClose(current);
        }}
      >
        <StyledMenuItem
          disabled={!search.allowedStatusFilters.has("EXCLUDE")}
          selected={current === "EXCLUDE"}
          aria-current={current === "EXCLUDE"}
          onClick={() => {
            onClose("EXCLUDE");
          }}
          data-test-id="currentStatus"
        >
          <ListItemText primary="Current" />
        </StyledMenuItem>
        <StyledMenuItem
          disabled={!search.allowedStatusFilters.has("DELETED_ONLY")}
          selected={current === "DELETED_ONLY"}
          aria-current={current === "DELETED_ONLY"}
          onClick={() => {
            onClose("DELETED_ONLY");
          }}
          data-test-id="deletedStatus"
        >
          <ListItemText primary="In Trash" />
        </StyledMenuItem>
        <StyledMenuItem
          disabled={!search.allowedStatusFilters.has("INCLUDE")}
          selected={current === "INCLUDE"}
          aria-current={current === "INCLUDE"}
          onClick={() => {
            onClose("INCLUDE");
          }}
          data-test-id="allStatus"
        >
          <ListItemText primary="Current & In Trash" />
        </StyledMenuItem>
      </StyledMenu>
    </div>
  );
}
