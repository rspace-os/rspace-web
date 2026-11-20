import { StyledEngineProvider, ThemeProvider } from "@mui/material/styles";
import type React from "react";
import { useState } from "react";
import CommonEditActions from "@/Inventory/components/CommonEditActions";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { makeMockInstrument } from "@/stores/models/__tests__/InstrumentModel/mocking";
import type InstrumentModel from "@/stores/models/InstrumentModel";
import type { StoreContainer } from "@/stores/stores/RootStore";
import { storesContext } from "@/stores/stores-context";
import materialTheme from "@/theme";
import TemplateFields from "../TemplateFields";

const SERVICED_KEY = "6f1c2e0a-0000-4000-8000-000000000051";

/** A Yes/No question, answered Yes, and an empty optional number shown only while the answer is Yes. */
function makeInstrument(): InstrumentModel {
  const instrument = makeMockInstrument({
    fields: [
      {
        id: 1,
        globalId: "IF1",
        name: "Serviced?",
        type: "radio",
        definition: { options: ["Yes", "No"] },
        selectedOptions: ["Yes"],
        columnIndex: 1,
        attachment: null,
        mandatory: false,
        fieldKey: SERVICED_KEY,
      },
      {
        id: 2,
        globalId: "IF2",
        name: "Hours run",
        type: "number",
        content: "",
        selectedOptions: null,
        columnIndex: 2,
        attachment: null,
        mandatory: false,
        fieldKey: "6f1c2e0a-0000-4000-8000-000000000052",
        displayRule: createDisplayRule(SERVICED_KEY, "IS_ANY_OF", ["Yes"]),
      },
    ],
  });
  instrument.currentlyEditableFields.add("fields");
  return instrument;
}

// Save and its reasons need only the store that Cancel reads
const stores = { uiStore: { confirmDiscardAnyChanges: () => Promise.resolve() } } as unknown as StoreContainer;

export function HiddenInvalidFieldsStory(): React.ReactNode {
  const [instrument] = useState(makeInstrument);
  return (
    <StyledEngineProvider injectFirst>
      <ThemeProvider theme={materialTheme}>
        <storesContext.Provider value={stores}>
          <TemplateFields onErrorStateChange={() => {}} instrument={instrument} />
          <CommonEditActions editableObject={instrument} />
        </storesContext.Provider>
      </ThemeProvider>
    </StyledEngineProvider>
  );
}
