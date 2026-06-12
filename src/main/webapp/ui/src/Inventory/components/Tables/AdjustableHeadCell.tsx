import SettingsOutlinedIcon from "@mui/icons-material/SettingsOutlined";
import Grid from "@mui/material/Grid";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import TableCell from "@mui/material/TableCell";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import StyledMenu from "@/components/StyledMenu";
import type { AdjustableTableRowLabel } from "@/stores/definitions/Tables";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";
import SearchContext from "../../../stores/contexts/Search";
// biome-ignore lint/style/useImportType: initial biome migration
import RsSet from "../../../util/set";
import SortableProperty, { type SortProperty } from "./SortableProperty";

type AdjustableHeadCellArgs<T extends AdjustableTableRowLabel> = {
  options: RsSet<T>;
  onChange: (newlyChosenOption: T) => void;
  current: T;
  sortableProperties?: Array<SortProperty>;
};

function AdjustableHeadCell<T extends AdjustableTableRowLabel>({
  options,
  onChange,
  current,
  sortableProperties,
}: AdjustableHeadCellArgs<T>): React.ReactNode {
  const { search } = useContext(SearchContext);
  const { order } = search.fetcher;

  const [adjustableColumnMenu, setAdjustableColumnMenu] = useState<Element | null>(null);

  const currentAdjustableProperty: SortProperty | null = sortableProperties?.find((p) => p.label === current) ?? null;

  const content = () => {
    if (!currentAdjustableProperty) return <span>{current}</span>;
    const cAP: SortProperty = currentAdjustableProperty;
    return <SortableProperty property={cAP} />;
  };

  return (
    <>
      <TableCell
        variant="head"
        padding="normal"
        sx={(theme) => ({ pr: `${theme.spacing(0.5)} !important` })}
        sortDirection={order}
      >
        <Grid container sx={{ alignItems: "center", flexWrap: "nowrap" }}>
          <Grid>{content()}</Grid>
          <Grid>
            <IconButtonWithTooltip
              title="Column options"
              sx={{ p: 0.5 }}
              onClick={({ currentTarget }) => setAdjustableColumnMenu(currentTarget)}
              icon={<SettingsOutlinedIcon sx={{ fontSize: "1.1em" }} />}
              color="standardIcon"
              aria-haspopup="menu"
              size="small"
            />
          </Grid>
        </Grid>
      </TableCell>
      <StyledMenu
        anchorEl={adjustableColumnMenu}
        keepMounted
        open={Boolean(adjustableColumnMenu)}
        onClose={() => setAdjustableColumnMenu(null)}
      >
        {options.map((option) => (
          <MenuItem
            onClick={() => {
              setAdjustableColumnMenu(null);
              onChange(option);
            }}
            key={option}
            selected={option === current}
            aria-current={option === current}
          >
            <ListItemText primary={option} />
          </MenuItem>
        ))}
      </StyledMenu>
    </>
  );
}

export default observer(AdjustableHeadCell);
