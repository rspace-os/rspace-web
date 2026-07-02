import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import type React from "react";
import { useContext } from "react";
import { useTranslation } from "react-i18next";
import StyledMenu from "../../../components/StyledMenu";
import SearchContext from "../../../stores/contexts/Search";
import type { DeletedItems } from "../../../stores/definitions/Search";

type StatusFilterArgs = {
  anchorEl: HTMLElement | null;
  current: DeletedItems;
  onClose: (newStatus: DeletedItems) => void;
};

export default function StatusFilter({ anchorEl, onClose, current }: StatusFilterArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
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
          <ListItemText primary={t("search.controls.status.current")} />
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
          <ListItemText primary={t("search.controls.status.inTrash")} />
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
          <ListItemText primary={t("search.controls.status.currentAndInTrash")} />
        </MenuItem>
      </StyledMenu>
    </div>
  );
}
