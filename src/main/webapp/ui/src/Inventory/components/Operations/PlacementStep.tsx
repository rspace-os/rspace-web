import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { runInAction } from "mobx";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import Searchbar from "@/Inventory/Search/components/Searchbar";
import ToggleView from "@/Inventory/Search/components/ToggleView";
import SearchView from "@/Inventory/Search/SearchView";
import SearchContext from "@/stores/contexts/Search";
import type { SearchView as SearchViewType } from "@/stores/definitions/Search";
import ContainerModel from "@/stores/models/ContainerModel";
import MemoisedFactory from "@/stores/models/Factory/MemoisedFactory";
import Search from "@/stores/models/Search";
import { menuIDs } from "@/util/menuIDs";
import InnerSearchNavigationContext from "../InnerSearchNavigationContext";
import { type PlacementBlocker, type PlacementSelection, placementBlocker } from "./placement";

const RESULT_VIEWS: Array<SearchViewType> = ["LIST", "TREE"];
const STAYS_PICKED: ReadonlySet<PlacementBlocker["reason"]> = new Set([
  "loading",
  "selectSlots",
  "occupiedSlot",
  "tooManySlots",
]);

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
  const mounted = React.useRef(true);
  React.useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const pickerRef = React.useRef<HTMLDivElement>(null);
  const changeButtonRef = React.useRef<HTMLButtonElement>(null);
  // The picker unmounts when a pick lands, which would drop focus to the page.
  const pickedFromPicker = React.useRef(false);

  // Step-local, not the global moveStore: ContainerModel's move checks read moveStore's selection.
  const [search] = React.useState(() => {
    const s: Search = new Search({
      fetcherParams: { resultType: "CONTAINER" },
      treeArgs: { filteredTypes: ["container"] },
      uiConfig: { allowedTypeFilters: new Set(["CONTAINER"]), selectionMode: "SINGLE" },
      factory: new MemoisedFactory(),
      callbacks: {
        // A pick resolves only after the container loads, by which time the user may have picked
        // another, switched to the workbench (which clears activeResult) or left the step.
        setActiveResult: (record) => {
          if (!(record instanceof ContainerModel) || !mounted.current || record !== s.activeResult) return;
          record.refreshAssociatedSearch();
          pickedFromPicker.current = pickerRef.current?.contains(document.activeElement) ?? false;
          onChangeRef.current({ mode: "container", container: record });
        },
      },
    });
    return s;
  });
  const choosingContainer = value.mode === "container";
  React.useEffect(() => {
    if (choosingContainer && search.searchView !== "TREE") void search.setSearchView("TREE");
  }, [choosingContainer, search]);

  const container = value.mode === "container" ? value.container : null;
  const blocker = container ? placementBlocker(container, count) : null;
  // Once the picked container can be used, the picker gives way to its name, so the grid follows it
  // directly on a short screen. A container that cannot be used leaves the picker open for another.
  const picked = container !== null && (blocker === null || STAYS_PICKED.has(blocker.reason));
  React.useEffect(() => {
    if (!picked || !pickedFromPicker.current) return;
    pickedFromPicker.current = false;
    changeButtonRef.current?.focus();
  }, [picked]);

  const blockerMessage = (b: PlacementBlocker, containerName: string): string => {
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
        return t("operations.placement.status.selectSlots", { count: b.remaining, container: containerName });
      case "occupiedSlot":
        return t("operations.placement.status.occupiedSlot");
      case "tooManySlots":
        return t("operations.placement.status.tooManySlots", { count: b.surplus });
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
    const fixInGrid = blocker.reason === "occupiedSlot" || blocker.reason === "tooManySlots";
    const severity = inProgress ? "info" : fixInGrid ? "warning" : "error";
    return (
      <Alert severity={severity} role={inProgress || fixInGrid ? "status" : "alert"}>
        {blockerMessage(blocker, container.name)}
      </Alert>
    );
  };

  const showGrid =
    container?.cType === "GRID" &&
    (blocker === null || ["selectSlots", "occupiedSlot", "tooManySlots"].includes(blocker.reason));

  return (
    <Stack spacing={1}>
      <Typography variant="body2" id={descriptionId}>
        {t("operations.placement.description")}
      </Typography>
      <RadioGroup
        aria-labelledby={descriptionId}
        value={value.mode}
        onChange={(e) => {
          if (e.target.value === "container") {
            onChange({ mode: "container", container: null });
            return;
          }
          // Direct assignment: setActiveResult would await the record and fire the callback again.
          runInAction(() => {
            search.activeResult = null;
          });
          onChange({ mode: "workbench" });
        }}
      >
        <FormControlLabel value="workbench" control={<Radio />} label={t("operations.placement.workbench")} />
        <FormControlLabel value="container" control={<Radio />} label={t("operations.placement.container")} />
      </RadioGroup>
      {picked ? (
        <Stack direction="row" spacing={1} sx={{ alignItems: "center" }}>
          <Typography variant="body2">
            {t("operations.placement.chosen", { container: container.name, globalId: container.globalId ?? "" })}
          </Typography>
          <Button
            ref={changeButtonRef}
            size="small"
            onClick={() => {
              runInAction(() => {
                search.activeResult = null;
              });
              onChange({ mode: "container", container: null });
            }}
          >
            {t("operations.placement.changeContainer")}
          </Button>
        </Stack>
      ) : value.mode === "container" ? (
        // The search box and the results only: the shared picker's type, status, owner and basket
        // controls and its parameter chips have nothing to offer when choosing one container.
        <SearchContext.Provider value={{ search, differentSearchForSettingActiveResult: search, isChild: false }}>
          <InnerSearchNavigationContext>
            <Stack
              ref={pickerRef}
              spacing={1}
              sx={{ maxHeight: 320, overflowY: "auto" }}
              data-test-id="placementPicker"
            >
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
