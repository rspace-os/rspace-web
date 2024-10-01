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
import FormHelperText from "@mui/material/FormHelperText";
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
import * as Parsers from "../../../util/parsers";

type CreateDialogProps = {|
  existingRecord: CreateFrom & InventoryRecord,
  open: boolean,
  onClose: () => void,
|};

export const SplitCount: ComponentType<{|
  state: { validState: boolean, ... },
|}> = observer(({ state }): Node => {
  const MIN = 2;
  const MAX = 100;

  const count = Parsers.getValueWithKey("count")(state)
    .flatMap(Parsers.isNumber)
    .elseThrow();

  return (
    <Box>
      <FormControl>
        <NumberField
          name="copies"
          autoFocus
          value={count}
          onChange={({ target }) => {
            runInAction(() => {
              // $FlowExpectedError[prop-missing]
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
        {/* FormHelperText used rather than NumberField's helperText prop so that the text is always shown, not only when there's an error. */}
        <FormHelperText error={!state.validState}>
          {`The total number of subsamples wanted, including the source (min ${MIN}
        , max ${MAX})`}
        </FormHelperText>
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

  const handleSubmit = () => {
    void (async () => {
      if (!selectedCreateOptionIndex)
        throw new Error("Cannot submit until an option is chosen");
      await existingRecord.createOptions[selectedCreateOptionIndex].onSubmit(
        existingRecord,
        existingRecord.createOptions[selectedCreateOptionIndex].parametersState
      );
      onClose();
    })();
  };

  return (
    <Dialog open={open} onClose={onClose}>
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
          <Stepper
            activeStep={selectedCreateOptionIndex ? 1 : 0}
            orientation="vertical"
          >
            <Step>
              <StepLabel
                optional={
                  selectedCreateOptionIndex !== null && (
                    <Button
                      onClick={() => {
                        setSelectedCreateOptionIndex(null);
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
            {selectedCreateOptionIndex !== null && (
              <Step>
                <StepLabel>
                  {
                    existingRecord.createOptions[selectedCreateOptionIndex]
                      .parametersLabel
                  }
                </StepLabel>
                <StepContent>
                  {existingRecord.createOptions[
                    selectedCreateOptionIndex
                  ].parametersComponent(
                    existingRecord.createOptions[selectedCreateOptionIndex]
                      .parametersState
                  )}
                </StepContent>
              </Step>
            )}
          </Stepper>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose}>Cancel</Button>
          <SubmitSpinner
            label="Create"
            onClick={handleSubmit}
            disabled={
              !selectedCreateOptionIndex ||
              !existingRecord.createOptions[selectedCreateOptionIndex]
                .parametersState.validState
            }
            loading={false}
          />
        </DialogActions>
      </form>
    </Dialog>
  );
}

export default (observer(CreateDialog): typeof CreateDialog);
