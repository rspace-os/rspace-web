import Button from "@mui/material/Button";
import FormControl from "@mui/material/FormControl";
import FormGroup from "@mui/material/FormGroup";
import FormLabel from "@mui/material/FormLabel";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import TemplateModel from "../../../stores/models/TemplateModel";

type LatestTemplateActionsArgs = {
  record: InventoryRecord;
};

function LatestTemplateActions({ record }: LatestTemplateActionsArgs): React.ReactNode {
  const { t } = useTranslation("inventory");

  // Only offer the update when the template actually has records to update (created
  // from an older version). Being a mere link target does not count.
  const isSampleTemplate =
    record instanceof TemplateModel && !record.historicalVersion && record.samplesToUpdateCount > 0;

  const isInstrumentTemplate =
    record instanceof InstrumentTemplateModel && !record.historicalVersion && record.instrumentsToUpdateCount > 0;

  if (!isSampleTemplate && !isInstrumentTemplate) return null;

  return (
    <FormControl component="fieldset" sx={{ alignItems: "flex-start" }}>
      <FormLabel component="legend">
        {isSampleTemplate ? t("moreInfo.updateSamples") : t("moreInfo.updateInstruments")}
      </FormLabel>
      {/* width is unified across the sidebar's action buttons in SidebarBody */}
      <FormGroup>
        {isSampleTemplate && (
          <Button
            variant="outlined"
            disableElevation
            onClick={() => {
              void (record as TemplateModel).updateSamplesToLatest();
            }}
          >
            {t("moreInfo.updateSamples")}
          </Button>
        )}
        {isInstrumentTemplate && (
          <Button
            variant="outlined"
            disableElevation
            onClick={() => {
              void (record as InstrumentTemplateModel).updateInstrumentsToLatest();
            }}
          >
            {t("moreInfo.updateInstruments")}
          </Button>
        )}
      </FormGroup>
    </FormControl>
  );
}

export default observer(LatestTemplateActions);
