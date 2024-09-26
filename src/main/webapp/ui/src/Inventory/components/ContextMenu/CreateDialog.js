//@flow

import React, { type Node } from "react";
import { type CreateFrom } from "../../../stores/definitions/InventoryRecord";
import { type BaseRecord } from "../../../stores/definitions/BaseRecord";
import docLinks from "../../../assets/DocLinks";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
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
import {
  OptionHeading,
  OptionExplanation,
} from "../../../components/Inputs/RadioField";

type CreateDialogProps = {|
  existingRecord: CreateFrom & BaseRecord,
  open: boolean,
  onClose: () => void,
|};

export default function CreateDialog({
  existingRecord,
  open,
  onClose,
}: CreateDialogProps): Node {
  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>
        Create new items
        <HelpLinkIcon
          link={docLinks.createDialog}
          title="Info on creating new items."
        />
      </DialogTitle>
      <DialogContent>
        <Stepper activeStep={0} orientation="vertical">
          <Step>
            <StepLabel>Create What?</StepLabel>
            <StepContent>
              <FormControl>
                <FormLabel sx={{ fontWeight: 400 }}>
                  What kind of record would you like to create from{" "}
                  <strong>{existingRecord.name}</strong>?
                </FormLabel>
                <RadioGroup>
                  {existingRecord.createOptions.map(
                    ({ label, explanation }) => (
                      <FormControlLabel
                        value={label}
                        control={<Radio />}
                        label={
                          <>
                            <OptionHeading>{label}</OptionHeading>
                            <OptionExplanation>{explanation}</OptionExplanation>
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
          <Step>
            <StepLabel>
              {existingRecord.createOptions[0].parametersLabel ?? "unknown"}
            </StepLabel>
            <StepContent></StepContent>
          </Step>
        </Stepper>
      </DialogContent>
    </Dialog>
  );
}
