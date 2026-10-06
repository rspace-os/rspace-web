import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React from "react";
import { useTranslation } from "react-i18next";
import ContainerModel from "@/stores/models/ContainerModel";
import MemoisedFactory from "@/stores/models/Factory/MemoisedFactory";
import Search from "@/stores/models/Search";
import InventoryPicker from "../Picker/Picker";
import type { PlacementSelection } from "./placement";

const ignoreAddition = () => {};

function PlacementStep({
  value,
  onChange,
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
  const [search] = React.useState(() => {
    const s = new Search({
      fetcherParams: { resultType: "CONTAINER" },
      treeArgs: { filteredTypes: ["container"] },
      uiConfig: { allowedTypeFilters: new Set(["CONTAINER"]), selectionMode: "SINGLE" },
      factory: new MemoisedFactory(),
      callbacks: {
        setActiveResult: (record) => {
          if (record instanceof ContainerModel) onChangeRef.current({ mode: "container", container: record });
        },
      },
    });
    void s.setSearchView("TREE");
    return s;
  });

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
        <InventoryPicker search={search} onAddition={ignoreAddition} paddingless testId="placementPicker" />
      ) : null}
    </Stack>
  );
}

export default observer(PlacementStep);
