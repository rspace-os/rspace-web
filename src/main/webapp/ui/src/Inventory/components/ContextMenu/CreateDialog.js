//@flow

import React, { type Node, type ComponentType } from "react";
import {
  type CreateFrom,
  type InventoryRecord,
} from "../../../stores/definitions/InventoryRecord";
import docLinks from "../../../assets/DocLinks";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Stepper from "@mui/material/Stepper";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import StepContent from "@mui/material/StepContent";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import FormControlLabel from "@mui/material/FormControlLabel";
import RadioGroup from "@mui/material/RadioGroup";
import Radio from "@mui/material/Radio";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import {
  OptionHeading,
  OptionExplanation,
} from "../../../components/Inputs/RadioField";
import NumberField from "../../../components/Inputs/NumberField";
import InputAdornment from "@mui/material/InputAdornment";
import { observer } from "mobx-react-lite";
import { runInAction } from "mobx";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import NoValue from "../../../components/NoValue";
import StringField from "../../../components/Inputs/StringField";
import { type Id } from "../../../stores/definitions/BaseRecord";
import Grid from "@mui/material/Grid";
import Table from "@mui/material/Table";
import TableBody from "@mui/material/TableBody";
import TableCell from "@mui/material/TableCell";
import TableContainer from "@mui/material/TableContainer";
import TableHead from "@mui/material/TableHead";
import TableRow from "@mui/material/TableRow";
import Checkbox from "@mui/material/Checkbox";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";

type CreateDialogProps = {|
  existingRecord: CreateFrom & InventoryRecord,
  open: boolean,
  onClose: () => void,
|};

export const TemplateName: ComponentType<{|
  state: { validState: boolean, name: string },
|}> = observer(({ state }): Node => {
  return (
    <StringField
      value={state.name}
      onChange={({ target }) => {
        runInAction(() => {
          state.name = target.value;
          state.validState = state.validState && target.value.length > 0;
        });
      }}
      variant="outlined"
    />
  );
});

export const TemplateFields: ComponentType<{|
  state: {
    validState: boolean,
    copyFieldContent: $ReadOnlyArray<{|
      id: Id,
      name: string,
      content: string,
      hasContent: boolean,
      selected: boolean,
    |}>,
  },
|}> = observer(({ state }): Node => {
  if (state.copyFieldContent.length === 0)
    return <NoValue label="No fields." />;
  return (
    <TableContainer>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell variant="head" padding="checkbox">
              <Checkbox
                indeterminate={
                  state.copyFieldContent.some(({ selected }) => selected) &&
                  !state.copyFieldContent.every(({ selected }) => selected)
                }
                checked={state.copyFieldContent.every(
                  ({ selected }) => selected
                )}
                onChange={({ target: { checked } }) => {
                  runInAction(() => {
                    state.copyFieldContent.forEach((f) => {
                      f.selected = checked;
                    });
                  });
                }}
              />
            </TableCell>
            <TableCell width="70%">Field</TableCell>
            <TableCell width="30%">Default Value</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {state.copyFieldContent.map((f) => (
            <TableRow key={f.id}>
              <TableCell variant="head" padding="checkbox">
                <Checkbox
                  checked={f.selected}
                  onChange={({ target: { checked } }) => {
                    runInAction(() => {
                      f.selected = checked;
                    });
                  }}
                  color="default"
                  disabled={!f.hasContent}
                />
              </TableCell>
              <TableCell>{f.name}</TableCell>
              <TableCell
                style={{
                  opacity: f.selected ? 1.0 : 0.3,
                }}
              >
                {f.content}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  );
});

export const SplitCount: ComponentType<{|
  state: { validState: boolean, count: number },
|}> = observer(({ state }): Node => {
  const MIN = 2;
  const MAX = 100;

  return (
    <Box>
      <FormControl>
        <NumberField
          name="copies"
          autoFocus
          value={state.count}
          onChange={({ target }) => {
            runInAction(() => {
              state.count = parseInt(target.value, 10);
              state.validState = target.checkValidity() && target.value !== "";
            });
          }}
          error={!state.validState}
          variant="outlined"
          size="small"
          InputProps={{
            startAdornment: (
              <InputAdornment position="start">Copies</InputAdornment>
            ),
          }}
          inputProps={{
            min: MIN,
            max: MAX,
            step: 1,
          }}
        />
      </FormControl>
    </Box>
  );
});

function CreateDialog({
  existingRecord,
  open,
  onClose,
}: CreateDialogProps): Node {
  const [selectedCreateOptionIndex, setSelectedCreateOptionIndex] =
    React.useState<null | number>(null);
  const [activeStep, setActiveStep] = React.useState<number>(0);

  React.useEffect(() => {
    if (activeStep === 0) setSelectedCreateOptionIndex(null);
  }, [activeStep]);

  const handleSubmit = () => {
    void (async () => {
      if (!selectedCreateOptionIndex)
        throw new Error("Cannot submit until an option is chosen");
      await existingRecord.createOptions[selectedCreateOptionIndex].onSubmit();
      onClose();
    })();
  };

  return (
    <Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>
        Create new items
        <HelpLinkIcon
          link={docLinks.createDialog}
          title="Info on creating new items."
        />
      </DialogTitle>
      <form
        onSubmit={(e) => {
          e.preventDefault();
          handleSubmit();
        }}
      >
        <DialogContent>
          <Stepper activeStep={activeStep} orientation="vertical">
            <Step>
              <StepLabel
                optional={
                  selectedCreateOptionIndex !== null && (
                    <Button
                      onClick={() => {
                        setSelectedCreateOptionIndex(null);
                        setActiveStep(0);
                      }}
                    >
                      Change
                    </Button>
                  )
                }
              >
                {selectedCreateOptionIndex
                  ? existingRecord.createOptions[selectedCreateOptionIndex]
                      .label
                  : "Create What?"}
              </StepLabel>
              <StepContent>
                <FormControl>
                  <FormLabel sx={{ fontWeight: 400 }}>
                    What kind of record would you like to create from{" "}
                    <strong>{existingRecord.name}</strong>?
                  </FormLabel>
                  <RadioGroup
                    value={selectedCreateOptionIndex}
                    onChange={(_event, index) => {
                      setSelectedCreateOptionIndex(index);
                      setActiveStep(1);
                    }}
                  >
                    {existingRecord.createOptions.length === 0 && (
                      <NoValue label="No options available." />
                    )}
                    {existingRecord.createOptions.map(
                      ({ label, explanation, disabled }, index) => (
                        <FormControlLabel
                          key={index}
                          value={index}
                          control={<Radio />}
                          disabled={disabled}
                          label={
                            <>
                              <OptionHeading>{label}</OptionHeading>
                              <OptionExplanation>
                                {explanation}
                              </OptionExplanation>
                            </>
                          }
                          sx={{ mt: 2 }}
                        />
                      )
                    )}
                  </RadioGroup>
                </FormControl>
              </StepContent>
            </Step>
            {selectedCreateOptionIndex !== null &&
              existingRecord.createOptions[selectedCreateOptionIndex]
                .parameters &&
              existingRecord.createOptions[
                selectedCreateOptionIndex
              ].parameters.map(({ label, component, explanation }, index) => (
                <Step key={index}>
                  <StepLabel>
                    {label}
                    <Typography variant="body2">{explanation}</Typography>
                  </StepLabel>
                  <StepContent>
                    <Grid container direction="column" spacing={1}>
                      <Grid item>{component()}</Grid>
                      <Grid item>
                        <Stack spacing={1} direction="row">
                          {index <
                            (
                              existingRecord.createOptions[
                                selectedCreateOptionIndex
                              ].parameters ?? []
                            ).length -
                              1 && (
                            <Button
                              color="primary"
                              variant="contained"
                              disableElevation
                              onClick={() => {
                                setActiveStep(activeStep + 1);
                              }}
                            >
                              Next
                            </Button>
                          )}
                          <Button
                            variant="outlined"
                            onClick={() => {
                              setActiveStep(activeStep - 1);
                            }}
                          >
                            Back
                          </Button>
                        </Stack>
                      </Grid>
                    </Grid>
                  </StepContent>
                </Step>
              ))}
          </Stepper>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <SubmitSpinner
            label="Create"
            onClick={handleSubmit}
            disabled={
              !selectedCreateOptionIndex ||
              activeStep <
                (
                  existingRecord.createOptions[selectedCreateOptionIndex]
                    .parameters ?? []
                ).length ||
              !(
                existingRecord.createOptions[selectedCreateOptionIndex]
                  .parametersState?.validState ?? true
              )
            }
            loading={false}
          />
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default (observer(CreateDialog): typeof CreateDialog);
