import React, { useContext } from "react";
import ListItemText from "@mui/material/ListItemText";
import StyledMenu from "../../../components/StyledMenu";
import MenuItem from "@mui/material/MenuItem";
import { type DeletedItems } from "../../../stores/definitions/Search";
import SearchContext from "../../../stores/contexts/Search";

type StatusFilterArgs = {
  anchorEl: HTMLElement | null;
  current: DeletedItems;
  onClose: (newStatus: DeletedItems) => void;
};

export default function StatusFilter({
  anchorEl,
  onClose,
  current,
}: StatusFilterArgs): React.ReactNode {
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
        <MenuItem
          disabled={!search.allowedStatusFilters.has("EXCLUDE")}
          selected={current === "EXCLUDE"}
          aria-current={current === "EXCLUDE"}
          onClick={() => {
            onClose("EXCLUDE");
          }}
          data-test-id="currentStatus"
        >
          <ListItemText primary="Current" />
        </MenuItem>
        <MenuItem
          disabled={!search.allowedStatusFilters.has("DELETED_ONLY")}
          selected={current === "DELETED_ONLY"}
          aria-current={current === "DELETED_ONLY"}
          onClick={() => {
            onClose("DELETED_ONLY");
          }}
          data-test-id="deletedStatus"
        >
          <ListItemText primary="In Trash" />
        </MenuItem>
        <MenuItem
          disabled={!search.allowedStatusFilters.has("INCLUDE")}
          selected={current === "INCLUDE"}
          aria-current={current === "INCLUDE"}
          onClick={() => {
            onClose("INCLUDE");
          }}
          data-test-id="allStatus"
        >
          <ListItemText primary="Current & In Trash" />
        </MenuItem>
      </StyledMenu>
    </div>
  );
}
