import CustomToggleButtonGroup from "../../components/CustomToggleButtonGroup";
import CustomToggleButton from "../../components/CustomToggleButton";
import React from "react";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";
import { observer } from "mobx-react-lite";

function ColumnVisibilitySettings({
  visibleColumnIds,
  setVisibleColumnIds,
  allTableHeaderCells,
}) {
  const { width } = useViewportDimensions();

  return (
    <CustomToggleButtonGroup
      sx={width < 900 ? { display: "block !important" } : undefined}
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
