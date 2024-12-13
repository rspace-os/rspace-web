//@flow

import React, { type Node } from "react";
import { Dialog } from "../../../components/DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Stepper from "@mui/material/Stepper";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import StepContent from "@mui/material/StepContent";

type AddFilestoreDialogArgs = {|
  open: boolean,
  onClose: () => void,
|};

function FilesystemSelectionStep(props: {||}) {
  return (
    <Step key="filesystemSelection" {...props}>
      <StepLabel>Select a Filesystem</StepLabel>
      <StepContent>Foo</StepContent>
    </Step>
  );
}

function FolderSelectionStep(props: {||}) {
  return (
    <Step key="folderSelection" {...props}>
      <StepLabel>Select your Folder</StepLabel>
      <StepContent>Bar</StepContent>
    </Step>
  );
}

function NameStep(props: {||}) {
  return (
    <Step key="name" {...props}>
      <StepLabel>Name the Filestore</StepLabel>
      <StepContent>Baz</StepContent>
    </Step>
  );
}

export default function AddFilestoreDialog({
  open,
  onClose,
}: AddFilestoreDialogArgs): Node {
  // fetch the file systems, and provide a menu
  // once one is selected, provide a tree of the file system to pick a folder
  //   let's look at how the move dialog does it?
  // provide a text field for naming the filesystem
  // submit button with validation
  // actually maybe it should be a 3-step wizard?

  const [activeStep, setActiveStep] = React.useState(-1);
  React.useEffect(() => {
    if (!open) setActiveStep(-1);
  }, [open, setActiveStep]);

  return (
    <Dialog open={open} onClose={onClose}>
      <DialogTitle>Add a Filestore</DialogTitle>
      <DialogContent>
        <Stepper activeStep={activeStep} orientation="vertical">
          <FilesystemSelectionStep />
          <FolderSelectionStep />
          <NameStep />
        </Stepper>
      </DialogContent>
    </Dialog>
  );
}
