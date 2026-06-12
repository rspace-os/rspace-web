import { observer } from "mobx-react-lite";
// biome-ignore lint/correctness/noUnusedImports: initial biome migration
import React from "react";
import CustomToggleButton from "../../components/CustomToggleButton";
import CustomToggleButtonGroup from "../../components/CustomToggleButtonGroup";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";

function ColumnVisibilitySettings({
  visibleColumnIds,
  setVisibleColumnIds,
  allTableHeaderCells,
}: {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  visibleColumnIds: any;
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  setVisibleColumnIds: any;
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  allTableHeaderCells: any;
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
      {/* biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion */}
      {allTableHeaderCells.map((cell: any) => (
        <CustomToggleButton key={cell.id} value={cell.id} aria-label={cell.label}>
          {cell.label}
        </CustomToggleButton>
      ))}
    </CustomToggleButtonGroup>
  );
}

export default observer(ColumnVisibilitySettings);
