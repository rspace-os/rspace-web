import "@/stores/stores/RootStore";
import { ThemeProvider } from "@mui/material/styles";
import type React from "react";
import { prepareContainer } from "@/Inventory/components/Operations/placement";
import SearchContext from "@/stores/contexts/Search";
import { makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
import materialTheme from "@/theme";
import ContentGrid from "./ContentGrid";

/** An empty 2x2 grid used only to choose two locations, as in the Move dialog or the operation wizard. */
export function SelectionOnlyGrid(): React.ReactNode {
  const box = makeMockContainer({
    id: 2,
    globalId: "IC2",
    name: "Empty box",
    cType: "GRID",
    gridLayout: { columnsNumber: 2, rowsNumber: 2, columnsLabelType: "N123", rowsLabelType: "ABC" },
    locationsCount: 4,
    contentSummary: { totalCount: 0, subSampleCount: 0, containerCount: 0, instrumentCount: 0 },
    locations: [],
  });
  prepareContainer(box, 2);
  return (
    <ThemeProvider theme={materialTheme}>
      <SearchContext.Provider
        value={{
          search: box.contentSearch,
          scopedResult: box,
          differentSearchForSettingActiveResult: box.contentSearch,
        }}
      >
        <ContentGrid />
      </SearchContext.Provider>
    </ThemeProvider>
  );
}
