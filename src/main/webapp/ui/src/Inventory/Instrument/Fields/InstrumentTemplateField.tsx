import React from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import InputWrapper from "../../../components/Inputs/InputWrapper";
import InstrumentTemplateModel from "../../../stores/models/InstrumentTemplateModel";
import InstrumentModel from "../../../stores/models/InstrumentModel";
import { mkAlert } from "../../../stores/contexts/Alert";
import { getErrorMessage } from "../../../util/error";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import { Stack } from "@mui/material";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import ListItemSecondaryAction from "@mui/material/ListItemSecondaryAction";
import InstrumentTemplatePicker from "../../components/Picker/InstrumentTemplatePicker";
import GlobalId from "../../../components/GlobalId";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import RecordTypeIcon from "../../../components/RecordTypeIcon";

function InstrumentTemplateField(): React.ReactNode {
  const {
    searchStore: { activeResult },
    uiStore,
  } = useStores();
  if (!activeResult || !(activeResult instanceof InstrumentModel))
    throw new Error("ActiveResult must be an Instrument");

  const setTemplate = React.useCallback(
    (t: InstrumentTemplateModel | null) => {
      activeResult.setTemplate(t).catch((error) => {
        uiStore.addAlert(
          mkAlert({
            title: "Could not fetch instrument template details.",
            message: getErrorMessage(error, "Unknown reason."),
            variant: "error",
          }),
        );
        console.error("Could not set instrument template", error);
      });
    },
    [activeResult, uiStore],
  );

  const template = activeResult.template;

  if (!activeResult.id) {
    return (
      <InputWrapper
        label="Instrument Template"
        dataTestId="ChooseInstrumentTemplate"
        explanation={
          activeResult.isFieldEditable("template") ? (
            <>
              If you select an instrument template below, initial metadata and
              custom fields will be automatically generated.
            </>
          ) : null
        }
      >
        <Stack flexWrap="nowrap">
          <FormControlLabel
            value="no-template"
            control={<Radio checked={template === null} />}
            label="No template"
            onClick={() => {
              setTemplate(null);
            }}
            sx={{ mb: 2, mt: 1 }}
          />
          {activeResult.isFieldEditable("template") && (
            <InstrumentTemplatePicker
              disabled={!activeResult.isFieldEditable("template")}
              setTemplate={setTemplate}
              instrument={activeResult}
            />
          )}
        </Stack>
      </InputWrapper>
    );
  }

  // Existing instrument — show read-only template summary
  return (
    <InputWrapper label="Instrument Template" disabled>
      {template ? (
        <List dense disablePadding>
          <ListItem>
            <ListItemAvatar>
              <RecordTypeIcon record={template} />
            </ListItemAvatar>
            <ListItemText
              primary={template.name}
              secondary={`Version ${template.version}`}
              style={{ overflowWrap: "anywhere", maxWidth: "60%" }}
            />
            <ListItemSecondaryAction>
              <GlobalId record={template} />
            </ListItemSecondaryAction>
          </ListItem>
        </List>
      ) : (
        <span>No template</span>
      )}
    </InputWrapper>
  );
}

export default observer(InstrumentTemplateField);
