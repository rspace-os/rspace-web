import CustomToggleButtonGroup from "../../components/CustomToggleButtonGroup";
import CustomToggleButton from "../../components/CustomToggleButton";
import React from "react";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";

const useStyles = makeStyles()(() => ({
  smallWidth: {
    display: "block !important",
  },
}));

function ColumnVisibilitySettings({
  visibleColumnIds,
  setVisibleColumnIds,
  allTableHeaderCells,
}) {
  const { classes } = useStyles();
  const { width } = useViewportDimensions();

  return (
    <CustomToggleButtonGroup
      className={width < 900 ? classes.smallWidth : null}
      value={visibleColumnIds}
      size="small"
      onChange={(_, columns) => setVisibleColumnIds(columns)}
      aria-label="Select visible columns"
    >
      {allTableHeaderCells.map((cell) => (
        <CustomToggleButton
          key={cell.id}
          value={cell.id}
          aria-label={cell.label}
        >
          {cell.label}
        </CustomToggleButton>
      ))}
    </CustomToggleButtonGroup>
  );
}

export default observer(ColumnVisibilitySettings);
