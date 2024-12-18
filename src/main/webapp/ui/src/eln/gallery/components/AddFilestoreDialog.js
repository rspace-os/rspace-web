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
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import { Optional } from "../../../util/optional";
import * as ArrayUtils from "../../../util/ArrayUtils";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { useFilestoreLogin } from "./FilestoreLoginDialog";

type AddFilestoreDialogArgs = {|
  open: boolean,
  onClose: (boolean) => void,
|};

function FilesystemSelectionStep(props: {|
  setSelectedFilesystem: ({|
    id: number,
    name: string,
    url: string,
  |}) => void,
  selectedFilesystem: Optional<{|
    id: number,
    name: string,
    url: string,
  |}>,
|}) {
  const { selectedFilesystem, setSelectedFilesystem, ...rest } = props;
  const [filesystems, setFilesystems] = React.useState<null | $ReadOnlyArray<{|
    id: number,
    name: string,
    url: string,
  |}>>(null);
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

  return (
    <Step key="filesystemSelection" {...rest}>
      <StepLabel>Select a Filesystem</StepLabel>
      <StepContent>
        <RadioGroup
          value={selectedFilesystem.orElse(null)}
          onChange={({ target: { value } }) => {
            const chosenId = parseInt(value, 10);
            Optional.fromNullable(filesystems)
              .flatMap((fss) =>
                ArrayUtils.find(({ id }) => id === chosenId, fss)
              )
              .do((fs) => {
                setSelectedFilesystem(fs);
              });
          }}
        >
          {(filesystems ?? []).map((fs) => (
            <FormControlLabel
              key={fs.id}
              value={fs.id}
              control={<Radio />}
              label={fs.name}
            />
          ))}
        </RadioGroup>
      </StepContent>
    </Step>
  );
}

type FilesystemListing = $ReadOnlyArray<{|
  nfsId: number,
  name: string,
  folder: boolean,
|}>;

function TreeListing({
  fsId,
  fsName,
  path,
  onFailToAuthenticate,
}: {|
  fsId: number,
  fsName: string,
  path: string,
  onFailToAuthenticate: () => void,
|}): Node {
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

  const [listing, setListing] = React.useState<FilesystemListing>([]);
  const { login } = useFilestoreLogin();
  React.useEffect(() => {
    async function browse(): Promise<void> {
      try {
        const {
          data: { content },
        } = await (
          await api.current
        ).get<{ content: FilesystemListing, ... }>(
          `filesystems/${fsId}/browse?remotePath=${path}`
        );
        setListing(content);
      } catch (e) {
        if (e.response?.status === 403) {
          if (
            await login({
              filesystemName: fsName,
              filesystemId: fsId,
            })
          ) {
            await browse();
          } else {
            onFailToAuthenticate();
          }
        } else {
          throw e;
        }
      }
    }
    void browse();
  }, [fsId, path]);

  return (
    <>
      {listing.map(
        ({ folder, name }) =>
          folder && (
            <TreeItem
              itemId={`${path}${name}/`}
              label={name}
              key={`${path}${name}/`}
            >
              <TreeListing
                fsId={fsId}
                fsName={name}
                path={`${path}${name}/`}
                onFailToAuthenticate={onFailToAuthenticate}
              />
            </TreeItem>
          )
      )}
    </>
  );
}

function FolderSelectionStep(props: {|
  selectedFilestoreId: Optional<number>,
  onConfirm: (string) => void,
  onCancel: () => void,
|}) {
  const { selectedFilestoreId, onConfirm, onCancel, ...rest } = props;
  const [expandedItems, setExpandedItems] = React.useState<
    $ReadOnlyArray<number>
  >([]);
  const [selectedFolderPath, setSelectedFolderPath] = React.useState("");

  return (
    <Step key="folderSelection" {...rest}>
      <StepLabel>Select your Folder</StepLabel>
      <StepContent>
        <SimpleTreeView
          expandedItems={expandedItems}
          onExpandedItemsChange={(_event, nodeIds) => {
            setExpandedItems(nodeIds);
          }}
          onItemSelectionToggle={(
            event,
            itemId: string | $ReadOnlyArray<string>,
            selected
          ) => {
            if (Array.isArray(itemId)) return;
            if (!selected) return;
            setSelectedFolderPath(itemId);
          }}
        >
          {selectedFilestoreId
            .map((fsId) => (
              <TreeListing
                path="/"
                fsId={fsId}
                fsName={"Foo"}
                key={null}
                onFailToAuthenticate={() => {
                  onCancel();
                }}
              />
            ))
            .orElse(null)}
        </SimpleTreeView>
        <Box sx={{ mb: 2 }}>
          <Button
            disabled={!selectedFolderPath}
            variant="contained"
            color="primary"
            onClick={() => {
              onConfirm(selectedFolderPath);
            }}
            sx={{ mt: 1, mr: 1 }}
          >
            Choose Folder
          </Button>
          <Button onClick={onCancel} sx={{ mt: 1, mr: 1 }}>
            Back
          </Button>
        </Box>
      </StepContent>
    </Step>
  );
}

function NameStep(props: {|
  onConfirm: (string) => void,
  onCancel: () => void,
|}) {
  const { onConfirm, onCancel, ...rest } = props;
  const [name, setName] = React.useState("");
  return (
    <Step key="name" {...rest}>
      <StepLabel>Name the Filestore</StepLabel>
      <StepContent>
        <TextField
          value={name}
          onChange={({ target: { value } }) => {
            setName(value);
          }}
        />
        <Box sx={{ mb: 2 }}>
          <Button
            disabled={name.length === 0}
            variant="contained"
            color="primary"
            onClick={() => {
              onConfirm(name);
            }}
            sx={{ mt: 1, mr: 1 }}
          >
            Add Filestore
          </Button>
          <Button onClick={onCancel} sx={{ mt: 1, mr: 1 }}>
            Back
          </Button>
        </Box>
      </StepContent>
    </Step>
  );
}

export default function AddFilestoreDialog({
  open,
  onClose,
}: AddFilestoreDialogArgs): Node {
  const [activeStep, setActiveStep] = React.useState(-1);
  React.useEffect(() => {
    if (open) {
      setActiveStep(0);
    } else {
      setActiveStep(-1);
    }
  }, [open, setActiveStep]);

  const [selectedFilesystem, setSelectedFilesystem] = React.useState<
    Optional<{|
      id: number,
      name: string,
      url: string,
    |}>
  >(Optional.empty());

  const [pathOfSelectedFolder, setPathOfSelectedFolder] = React.useState("");

  const { addAlert } = React.useContext(AlertContext);
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
  async function addFilestore(name: string) {
    try {
      const filesystemId = selectedFilesystem
        .toResult(() => new Error("No filestore has been selected,"))
        .elseThrow().id;
      await (
        await api.current
      ).post<_, mixed>(
        "filestores",
        {},
        {
          //$FlowExpectedError[incompatible-call] Flow types are wrong; plain object is allowed for `params`
          params: {
            filesystemId,
            name,
            pathToSave: pathOfSelectedFolder,
          },
        }
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: "Successfully added new filestore",
        })
      );
      onClose(true);
    } catch (e) {
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to add new filestore",
          message: e.message,
        })
      );
    }
  }

  return (
    <Dialog open={open} onClose={() => onClose(false)}>
      <DialogTitle>Add a Filestore</DialogTitle>
      <DialogContent>
        <Stepper activeStep={activeStep} orientation="vertical">
          <FilesystemSelectionStep
            selectedFilesystem={selectedFilesystem}
            setSelectedFilesystem={(fs) => {
              setSelectedFilesystem(Optional.present(fs));
              setActiveStep(1);
            }}
          />
          <FolderSelectionStep
            selectedFilestoreId={selectedFilesystem.map((fs) => fs.id)}
            onConfirm={(newPath) => {
              setPathOfSelectedFolder(newPath);
              setActiveStep(2);
            }}
            onCancel={() => {
              setActiveStep(0);
            }}
          />
          <NameStep
            onConfirm={(name) => {
              void addFilestore(name);
            }}
            onCancel={() => {
              setActiveStep(1);
            }}
          />
        </Stepper>
      </DialogContent>
    </Dialog>
  );
}
