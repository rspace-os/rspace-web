import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import type React from "react";
import { useTranslation } from "react-i18next";
import StyledMenu from "../../../components/StyledMenu";

type RequestableFilterArgs = {
  anchorEl: HTMLElement | null;
  current: boolean | null;
  onClose: (newValue: boolean | null) => void;
};

export default function RequestableFilter({ anchorEl, onClose, current }: RequestableFilterArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  return (
    <div data-test-id="requestableDropdown">
      <StyledMenu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => onClose(current)}>
        <MenuItem
          selected={current !== true}
          aria-current={current !== true}
          onClick={() => onClose(null)}
          data-test-id="requestableAllItems"
        >
          <ListItemText primary={t("search.controls.requestable.no")} />
        </MenuItem>
        <MenuItem
          selected={current === true}
          aria-current={current === true}
          onClick={() => onClose(true)}
          data-test-id="requestableOnly"
        >
          <ListItemText primary={t("search.controls.requestable.yes")} />
        </MenuItem>
      </StyledMenu>
    </div>
  );
}
