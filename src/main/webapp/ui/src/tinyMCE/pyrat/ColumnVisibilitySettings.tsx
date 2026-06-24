import { observer } from "mobx-react-lite";
import { useTranslation } from "react-i18next";
import CustomToggleButton from "../../components/CustomToggleButton";
import CustomToggleButtonGroup from "../../components/CustomToggleButtonGroup";
import useViewportDimensions from "../../hooks/browser/useViewportDimensions";

function ColumnVisibilitySettings({
  visibleColumnIds,
  setVisibleColumnIds,
  allTableHeaderCells,
}: {
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  visibleColumnIds: any;
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  setVisibleColumnIds: any;
  // biome-ignore lint/suspicious/noExplicitAny: initial biome migration
  allTableHeaderCells: any;
}) {
  const { width } = useViewportDimensions();
  const { t } = useTranslation("apps");

  return (
    <CustomToggleButtonGroup
      sx={width < 900 ? { display: "block !important" } : undefined}
      value={visibleColumnIds}
      size="small"
      onChange={(_, columns) => setVisibleColumnIds(columns)}
      aria-label={t("pyrat.columnVisibility.selectVisibleColumns")}
    >
      {/** biome-ignore lint/suspicious/noExplicitAny: initial biome migration */}
      {allTableHeaderCells.map((cell: any) => (
        <CustomToggleButton key={cell.id} value={cell.id} aria-label={cell.label}>
          {cell.label}
        </CustomToggleButton>
      ))}
    </CustomToggleButtonGroup>
  );
}

export default observer(ColumnVisibilitySettings);
