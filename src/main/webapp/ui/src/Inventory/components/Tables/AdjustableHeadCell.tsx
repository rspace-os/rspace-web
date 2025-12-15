import React, { useContext, useState } from "react";
import { observer } from "mobx-react-lite";
import SearchContext from "../../../stores/contexts/Search";
import { makeStyles } from "tss-react/mui";
import TableCell from "@mui/material/TableCell";
import RsSet from "../../../util/set";
import { StyledMenu, StyledMenuItem } from "../../../components/StyledMenu";
import SettingsOutlinedIcon from "@mui/icons-material/SettingsOutlined";
import Grid from "@mui/material/Grid";
import SortableProperty, { type SortProperty } from "./SortableProperty";
import { type AdjustableTableRowLabel } from "../../../stores/definitions/Tables";
import ListItemText from "@mui/material/ListItemText";
import IconButtonWithTooltip from "../../../components/IconButtonWithTooltip";

const useStyles = makeStyles()((theme) => ({
  adjustableColumnMenuButton: {
    padding: theme.spacing(0.5),
  },
  adjustableColumnMenuCell: {
    paddingRight: `${theme.spacing(0.5)} !important`,
  },
  current: {
    color: theme.palette.primary.main,
  },
}));

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

  const [adjustableColumnMenu, setAdjustableColumnMenu] =
    useState<Element | null>(null);

  const currentAdjustableProperty: SortProperty | null =
    sortableProperties?.find((p) => p.label === current) ?? null;

  const content = () => {
    if (!currentAdjustableProperty) return <span>{current}</span>;
    const cAP: SortProperty = currentAdjustableProperty;
    return <SortableProperty property={cAP} />;
  };

  const { classes } = useStyles();
  return (
    <>
      <TableCell
        variant="head"
        padding="normal"
        className={classes.adjustableColumnMenuCell}
        sortDirection={order}
      >
        <Grid container alignItems="center" wrap="nowrap">
          <Grid item>{content()}</Grid>
          <Grid item>
            <IconButtonWithTooltip
              title="Column options"
              className={classes.adjustableColumnMenuButton}
              onClick={({ currentTarget }) =>
                setAdjustableColumnMenu(currentTarget)
              }
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
          <StyledMenuItem
            onClick={() => {
              setAdjustableColumnMenu(null);
              onChange(option);
            }}
            key={option}
            selected={option === current}
            aria-current={option === current}
          >
            <ListItemText primary={option} />
          </StyledMenuItem>
        ))}
      </StyledMenu>
    </>
  );
}

export default observer(AdjustableHeadCell);
