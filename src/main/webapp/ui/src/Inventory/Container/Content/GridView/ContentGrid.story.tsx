import "@/stores/stores/RootStore";
import { ThemeProvider } from "@mui/material/styles";
import type React from "react";
import { prepareContainer } from "@/Inventory/components/Operations/placement";
import SearchContext from "@/stores/contexts/Search";
import type ContainerModel from "@/stores/models/ContainerModel";
import materialTheme from "@/theme";
import { emptyBox } from "./__tests__/gridFixtures";
import ContentGrid from "./ContentGrid";

function Grid({ box }: { box: ContainerModel }): React.ReactNode {
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

/** An empty 2x2 grid used only to choose two locations, as in the Move dialog or the operation wizard. */
export function SelectionOnlyGrid(): React.ReactNode {
  const box = emptyBox();
  prepareContainer(box, 2);
  return <Grid box={box} />;
}

/** An empty 2x2 grid as shown on the container's own page, with drag-and-drop on. */
export function ContainerPageGrid(): React.ReactNode {
  return <Grid box={emptyBox()} />;
}
