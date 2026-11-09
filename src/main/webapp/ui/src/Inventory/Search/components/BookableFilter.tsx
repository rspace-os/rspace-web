import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import type React from "react";
import { useTranslation } from "react-i18next";
import StyledMenu from "../../../components/StyledMenu";

type Props = {
  anchorEl: HTMLElement | null;
  current: boolean | null;
  onClose: (value: boolean | null) => void;
};

export default function BookableFilter({ anchorEl, current, onClose }: Props): React.ReactNode {
  const { t } = useTranslation("inventory");
  const options = [
    [null, t("search.controls.bookable.any")],
    [true, t("search.controls.bookable.yes")],
    [false, t("search.controls.bookable.no")],
  ] as const;
  return (
    <StyledMenu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={() => onClose(current)}>
      {options.map(([value, label]) => (
        <MenuItem
          key={String(value)}
          selected={current === value}
          aria-current={current === value}
          onClick={() => onClose(value)}
        >
          <ListItemText primary={label} />
        </MenuItem>
      ))}
    </StyledMenu>
  );
}
