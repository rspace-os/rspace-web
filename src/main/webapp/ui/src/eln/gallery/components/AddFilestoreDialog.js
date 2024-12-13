//@flow

import React, { type Node } from "react";
import { Dialog } from "../../../components/DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import Stepper from "@mui/material/Stepper";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import StepContent from "@mui/material/StepContent";
import Result from "../../../util/result";
import axios, { type Axios } from "axios";
import useOauthToken from "../../../common/useOauthToken";
import * as Parsers from "../../../util/parsers";

type AddFilestoreDialogArgs = {|
  open: boolean,
  onClose: () => void,
|};

function FilesystemSelectionStep(props: {||}) {
  const [filesystems, setFilesystems] = React.useState<null | $ReadOnlyArray<{|
    id: number,
    name: string,
    url: string,
  |}>>(null);
  const [filestoreIds, setFilestoreIds] = React.useState<null | Set<number>>(
    null
  );
  const { getToken } = useOauthToken();
  const api = React.useRef<Promise<Axios>>(
    (async () => {
      return axios.create({
        baseURL: "/api/v1/gallery",
        headers: {
          Authorization: "Bearer " + (await getToken()),
        },
      });
    })()
  );

  React.useEffect(() => {
    void (async () => {
      const { data } = await (await api.current).get<mixed>("filesystems");
      Parsers.isArray(data)
        .flatMap((array) =>
          Result.all(
            ...array.map((m) =>
              Parsers.isObject(m)
                .flatMap(Parsers.isNotNull)
                .flatMap((obj) => {
                  try {
                    const id = Parsers.getValueWithKey("id")(obj)
                      .flatMap(Parsers.isNumber)
                      .elseThrow();
                    const name = Parsers.getValueWithKey("name")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();
                    const url = Parsers.getValueWithKey("url")(obj)
                      .flatMap(Parsers.isString)
                      .elseThrow();
                    return Result.Ok({ id, name, url });
                  } catch (e) {
                    return Result.Error<{|
                      id: number,
                      name: string,
                      url: string,
                    |}>([e]);
                  }
                })
            )
          )
        )
        .do((newFilesystems) => setFilesystems(newFilesystems));
    })();
  }, []);

  React.useEffect(() => {
    void (async () => {
      const { data } = await (await api.current).get<mixed>("filestores");
      Parsers.isArray(data)
        .flatMap((array) =>
          Result.all(
            ...array.map((m) =>
              Parsers.isObject(m)
                .flatMap(Parsers.isNotNull)
                .flatMap(Parsers.getValueWithKey("fileSystem"))
                .flatMap(Parsers.isObject)
                .flatMap(Parsers.isNotNull)
                .flatMap(Parsers.getValueWithKey("id"))
                .flatMap(Parsers.isNumber)
            )
          )
        )
        .do((newFilesystemIds) => setFilestoreIds(new Set(newFilesystemIds)));
    })();
  }, []);

  return (
    <Step key="filesystemSelection" {...props}>
      <StepLabel>Select a Filesystem</StepLabel>
      <StepContent>
        {filesystems?.length ?? "NULL"},{filestoreIds?.size ?? "NULL"}
      </StepContent>
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

  const [activeStep, setActiveStep] = React.useState(-1);
  React.useEffect(() => {
    if (open) {
      setActiveStep(0);
    } else {
      setActiveStep(-1);
    }
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
