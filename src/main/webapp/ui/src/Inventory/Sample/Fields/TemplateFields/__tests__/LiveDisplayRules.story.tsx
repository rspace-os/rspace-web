import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import ValidatingSubmitButton, { allAreValid } from "@/components/ValidatingSubmitButton";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import type SampleModel from "@/stores/models/SampleModel";
import materialTheme from "@/theme";
import Fields from "../Fields";

const WEIGHT_KEY = "6f1c2e0a-0000-4000-8000-000000000021";

/**
 * A sample whose Weight, loaded as 4, shows a mandatory "Heavy goods permit"
 * while it is greater than 2 and a "Light goods note" while it is at most 2.
 */
function makeSample(): SampleModel {
  const sample = makeMockSample({
    fields: [
      {
        id: 1,
        globalId: "SF1",
        name: "Weight",
        type: "number",
        content: "4",
        selectedOptions: null,
        columnIndex: 1,
        attachment: null,
        mandatory: false,
        fieldKey: WEIGHT_KEY,
      },
      {
        id: 2,
        globalId: "SF2",
        name: "Heavy goods permit",
        type: "string",
        content: "",
        selectedOptions: null,
        columnIndex: 2,
        attachment: null,
        mandatory: true,
        fieldKey: "6f1c2e0a-0000-4000-8000-000000000022",
        displayRule: createDisplayRule(WEIGHT_KEY, "GREATER_THAN", ["2"]),
      },
      {
        id: 3,
        globalId: "SF3",
        name: "Light goods note",
        type: "string",
        content: "",
        selectedOptions: null,
        columnIndex: 3,
        attachment: null,
        mandatory: false,
        fieldKey: "6f1c2e0a-0000-4000-8000-000000000023",
        displayRule: createDisplayRule(WEIGHT_KEY, "AT_MOST", ["2"]),
      },
    ],
  });
  sample.currentlyEditableFields.add("fields");
  return sample;
}

/** Save is gated on the fields' validation, as the Inventory form's Save is on the record's. */
const SaveButton = observer(function SaveButton({ sample, onSave }: { sample: SampleModel; onSave: () => void }) {
  const { t } = useTranslation("common");
  const validation = allAreValid(sample.fields.map((field) => field.validate()));
  return (
    <ValidatingSubmitButton loading={false} validationResult={validation} disabled={!validation.isOk} onClick={onSave}>
      {t("actions.save")}
    </ValidatingSubmitButton>
  );
});

export function LiveDisplayRulesStory({ onSave }: { onSave: () => void }): React.ReactNode {
  const [sample] = useState(makeSample);
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <form>
          <Fields onErrorStateChange={() => {}} sample={sample} />
          <SaveButton sample={sample} onSave={onSave} />
        </form>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
