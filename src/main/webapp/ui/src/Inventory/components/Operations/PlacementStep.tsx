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
import ContainerModel from "@/stores/models/ContainerModel";
import MemoisedFactory from "@/stores/models/Factory/MemoisedFactory";
import Search from "@/stores/models/Search";
import { menuIDs } from "@/util/menuIDs";
import SearchView from "../../Search/SearchView";
import InventoryPicker from "../Picker/Picker";
import { type PlacementBlocker, type PlacementSelection, placementBlocker } from "./placement";

const ignoreAddition = () => {};

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
        <Box sx={{ maxHeight: 320, overflowY: "auto" }}>
          <InventoryPicker search={search} onAddition={ignoreAddition} paddingless testId="placementPicker" />
        </Box>
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
