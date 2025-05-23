//@flow

import React, { type Node, type ComponentType } from "react";
import { observer } from "mobx-react-lite";
import NewField from "./NewField";
import UpdateField from "./UpdateField";
import CloseIcon from "@mui/icons-material/Close";
import SettingsIcon from "@mui/icons-material/Settings";
import { makeStyles } from "tss-react/mui";
import NoValue from "../../../../components/NoValue";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import FormField from "../../../../components/Inputs/FormField";
import TextField from "../../../../components/Inputs/TextField";
import NumberField from "../../../../components/Inputs/NumberField";
import Box from "@mui/material/Box";
import Result from "../../../../stores/models/InventoryBaseRecord";

const useStyles = makeStyles()((theme) => ({
  actionsWrapper: {
    paddingBottom: theme.spacing(0.5),
    textAlign: "right",
  },
  textSpacer: {
    marginTop: theme.spacing(2),
    marginBottom: theme.spacing(1),
  },
}));

type ExtraFieldsArgs = {|
  onErrorStateChange: (string, boolean) => void,
  result: Result,
|};

function ExtraFields({ onErrorStateChange, result }: ExtraFieldsArgs): Node {
  const { classes } = useStyles();

  const EachField = observer(({ editable }: { editable: boolean }): Node => {
    const extraFieldsDisabled = !editable;
    return result.visibleExtraFields.map((ef, i) => (
      <div key={ef.name} aria-live="polite">
        {ef.editing ? (
          <UpdateField extraField={ef} index={i} record={result} />
        ) : (
          <>
            {ef.type === "Number" ? (
              <FormField
                label={ef.name}
                disabled={!editable}
                value={ef.content}
                renderInput={(props) => (
                  <>
                    {extraFieldsDisabled ? null : (
                      <div
                        style={{
                          position: "absolute",
                          top: 0,
                          right: 0,
                        }}
                      >
                        <IconButtonWithTooltip
                          title="Field settings"
                          size="small"
                          onClick={() => ef.setEditing(true)}
                          icon={<SettingsIcon fontSize="small" />}
                        />
                        <IconButtonWithTooltip
                          title="Delete field"
                          size="small"
                          onClick={() =>
                            result.removeExtraField(
                              ef.id,
                              result.extraFields.indexOf(ef)
                            )
                          }
                          icon={<CloseIcon fontSize="small" />}
                        />
                      </div>
                    )}
                    <NumberField
                      {...props}
                      inputProps={{
                        step: "any",
                      }}
                      onChange={({ target }) => {
                        ef.setInvalidInput(!target.checkValidity());
                        ef.setAttributesDirty({ content: target.value });
                        onErrorStateChange(`extra_${ef.name}`, ef.invalidInput);
                      }}
                    />
                  </>
                )}
                error={ef.invalidInput}
                helperText="Must be a valid number."
              />
            ) : (
              <FormField
                label={ef.name}
                disabled={!editable}
                value={ef.content}
                // ID is not used because TinyMCE does not expose an HTMLInputElement to attach it to
                doNotAttachIdToLabel
                renderInput={({ error: _error, id: _id, ...props }) => (
                  <>
                    {extraFieldsDisabled ? null : (
                      <div
                        style={{
                          position: "absolute",
                          top: 0,
                          right: 0,
                        }}
                      >
                        <IconButtonWithTooltip
                          title="Field settings"
                          size="small"
                          onClick={() => ef.setEditing(true)}
                          icon={<SettingsIcon fontSize="small" />}
                        />
                        <IconButtonWithTooltip
                          title="Delete field"
                          size="small"
                          onClick={() =>
                            result.removeExtraField(
                              ef.id,
                              result.extraFields.indexOf(ef)
                            )
                          }
                          icon={<CloseIcon fontSize="small" />}
                        />
                      </div>
                    )}
                    <Box mt={1}>
                      <TextField
                        {...props}
                        onChange={({ target }) => {
                          ef.setAttributesDirty({ content: target.value });
                          onErrorStateChange(
                            `extra_${ef.name}`,
                            ef.content.length > 250 || ef.invalidInput
                          );
                        }}
                      />
                    </Box>
                  </>
                )}
                maxLength={250}
                error={ef.content.length > 250 || ef.invalidInput}
                helperText="Must be no more than 250 characters."
              />
            )}
          </>
        )}
      </div>
    ));
  });

  const editable = result.isFieldEditable("extraFields");
  const extraFields = result.extraFields;
  return (
    <>
      {extraFields.length > 0 ? (
        <EachField editable={editable} />
      ) : (
        <div className={classes.textSpacer}>
          <NoValue label="No more fields" />
        </div>
      )}
      {editable && <NewField record={result} />}
    </>
  );
}

export default (observer(ExtraFields): ComponentType<ExtraFieldsArgs>);
