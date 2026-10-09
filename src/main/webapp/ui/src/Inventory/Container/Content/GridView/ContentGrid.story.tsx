import "@/stores/stores/RootStore";
import { ThemeProvider } from "@mui/material/styles";
import { runInAction } from "mobx";
import React from "react";
import { prepareContainer } from "@/Inventory/components/Operations/placement";
import SearchContext from "@/stores/contexts/Search";
import { makeMockSubSample } from "@/stores/models/__tests__/SubSampleModel/mocking";
import type ContainerModel from "@/stores/models/ContainerModel";
import type Search from "@/stores/models/Search";
import getRootStore from "@/stores/stores/getRootStore";
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

/** An empty 2x2 grid configured as MoveStore.setIsMoving does for the Move dialog's destination panel, moving one item. */
export function MoveDestinationGrid(): React.ReactNode {
  const [box] = React.useState(() => {
    const b = emptyBox();
    const { moveStore } = getRootStore();
    runInAction(() => {
      moveStore.isMoving = true;
      moveStore.search = { activeResult: b } as unknown as Search;
      moveStore.selectedResults = [makeMockSubSample({ id: 1, globalId: "SS1" })];
      Object.assign(b.contentSearch.uiConfig, {
        selectionMode: "MULTIPLE",
        selectionLimit: 1,
        onlyAllowSelectingEmptyLocations: true,
        dragAndDropDisabled: true,
      });
    });
    return b;
  });
  React.useEffect(
    () => () => {
      runInAction(() => {
        const { moveStore } = getRootStore();
        moveStore.isMoving = false;
        moveStore.search = null;
        moveStore.selectedResults = [];
      });
    },
    [],
  );
  return <Grid box={box} />;
}
