import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import TemplateModel from "../../../stores/models/TemplateModel";

type LatestTemplateActionsArgs = {
  record: InventoryRecord;
};

function LatestTemplateActions({ record }: LatestTemplateActionsArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  // Only offer the update when the template actually has samples to update:
  // samples created from an older version of it (samplesToUpdateCount). Merely
  // being a link target (e.g. a sample links to this template) is not something
  // to update, so the button must stay hidden in that case.
  if (!(record instanceof TemplateModel) || record.historicalVersion || record.samplesToUpdateCount <= 0) return null;

  return (
    <FormControl component="fieldset" sx={{ alignItems: "flex-start" }}>
      <FormLabel component="legend">{t("moreInfo.updateSamples")}</FormLabel>
      {/* width is unified across the sidebar's action buttons in SidebarBody */}
      <FormGroup>
        <Button
          variant="outlined"
          disableElevation
          onClick={() => {
            void record.updateSamplesToLatest();
          }}
        >
          {t("moreInfo.updateSamples")}
        </Button>
      </FormGroup>
    </FormControl>
  );
}

export default observer(LatestTemplateActions);
