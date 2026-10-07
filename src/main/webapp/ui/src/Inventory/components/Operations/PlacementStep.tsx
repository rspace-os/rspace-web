import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import SearchContext from "@/stores/contexts/Search";
import type { SearchView as SearchViewType } from "@/stores/definitions/Search";
import ContainerModel from "@/stores/models/ContainerModel";
import MemoisedFactory from "@/stores/models/Factory/MemoisedFactory";
import Search from "@/stores/models/Search";
import { menuIDs } from "@/util/menuIDs";
import Searchbar from "../../Search/components/Searchbar";
import ToggleView from "../../Search/components/ToggleView";
import SearchView from "../../Search/SearchView";
import InnerSearchNavigationContext from "../InnerSearchNavigationContext";
import { type PlacementBlocker, type PlacementSelection, placementBlocker } from "./placement";

const RESULT_VIEWS: Array<SearchViewType> = ["LIST", "TREE"];

function PlacementStep({
  value,
  onChange,
  count,
}: {
  value: PlacementSelection;
  onChange: (next: PlacementSelection) => void;
  /** How many subsamples the operation creates. */
  count: number;
}): React.ReactNode {
  const { t } = useTranslation("inventory");
  const descriptionId = React.useId();
  const onChangeRef = React.useRef(onChange);
  onChangeRef.current = onChange;

  // Step-local, not the global moveStore: ContainerModel's move checks read moveStore's selection.
  const [search] = React.useState(
    () =>
      new Search({
        fetcherParams: { resultType: "CONTAINER" },
        treeArgs: { filteredTypes: ["container"] },
        uiConfig: { allowedTypeFilters: new Set(["CONTAINER"]), selectionMode: "SINGLE" },
        factory: new MemoisedFactory(),
        callbacks: {
          setActiveResult: (record) => {
            if (!(record instanceof ContainerModel)) return;
            record.refreshAssociatedSearch();
            onChangeRef.current({ mode: "container", container: record });
          },
        },
      }),
  );
  const choosingContainer = value.mode === "container";
  React.useEffect(() => {
    if (choosingContainer && search.searchView !== "TREE") void search.setSearchView("TREE");
  }, [choosingContainer, search]);

  const container = value.mode === "container" ? value.container : null;
  const blocker = container ? placementBlocker(container, count) : null;

  const blockerMessage = (b: PlacementBlocker): string => {
    switch (b.reason) {
      case "loading":
        return t("operations.placement.status.loading");
      case "deleted":
        return t("operations.placement.status.deleted");
      case "noPermission":
        return t("operations.placement.status.noPermission");
      case "image":
        return t("operations.placement.status.image");
      case "workbench":
        return t("operations.placement.status.workbench");
      case "cannotStoreSamples":
        return t("operations.placement.status.cannotStoreSamples");
      case "notEnoughSpace":
        return t("operations.placement.status.notEnoughSpace", { free: b.free, count });
      case "selectSlots":
        return t("operations.placement.status.selectSlots", { count: b.remaining });
    }
  };

  const status = (): React.ReactNode => {
    if (!container) return null;
    if (!blocker)
      return (
        <Alert severity="success" role="status">
          {t("operations.placement.status.ready", { container: container.name })}
        </Alert>
      );
    // role="alert" for a dead end the user must change containers to get out of; "status" for
    // progress they make in place.
    const inProgress = blocker.reason === "loading" || blocker.reason === "selectSlots";
    return (
      <Alert severity={inProgress ? "info" : "error"} role={inProgress ? "status" : "alert"}>
        {blockerMessage(blocker)}
      </Alert>
    );
  };

  const showGrid = container?.cType === "GRID" && (blocker === null || blocker.reason === "selectSlots");

  return (
    <Stack spacing={1}>
      <Typography variant="body2" id={descriptionId}>
        {t("operations.placement.description")}
      </Typography>
      <RadioGroup
        aria-labelledby={descriptionId}
        value={value.mode}
        onChange={(e) =>
          onChange(e.target.value === "container" ? { mode: "container", container: null } : { mode: "workbench" })
        }
      >
        <FormControlLabel value="workbench" control={<Radio />} label={t("operations.placement.workbench")} />
        <FormControlLabel value="container" control={<Radio />} label={t("operations.placement.container")} />
      </RadioGroup>
      {value.mode === "container" ? (
        // The search box and the results only: the shared picker's type, status, owner and basket
        // controls and its parameter chips have nothing to offer when choosing one container.
        <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search, isChild: false }}>
          <InnerSearchNavigationContext>
            <Stack spacing={1} sx={{ maxHeight: 320, overflowY: "auto" }} data-test-id="placementPicker">
              <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
                <Box sx={{ flexGrow: 1 }}>
                  <Searchbar
                    handleSearch={(query) =>
                      void search.fetcher.performInitialSearch({ query, resultType: "CONTAINER" })
                    }
                  />
                </Box>
                <ToggleView
                  onChange={(view) => search.setSearchView(view)}
                  currentView={search.searchView}
                  views={RESULT_VIEWS}
                />
              </Stack>
              <SearchView contextMenuId={menuIDs.PICKER} />
            </Stack>
          </InnerSearchNavigationContext>
        </SearchContext.Provider>
      ) : null}
      {status()}
      {container && showGrid ? (
        <SearchContext.Provider
          value={{
            search: container.contentSearch,
            scopedResult: container,
            differentSearchForSettingActiveResult: container.contentSearch,
          }}
        >
          <SearchView contextMenuId={menuIDs.NONE} />
        </SearchContext.Provider>
      ) : null}
    </Stack>
  );
}

export default observer(PlacementStep);
