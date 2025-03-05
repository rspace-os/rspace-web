//@flow

import React, { type Node } from "react";
import { Dialog } from "../../../components/DialogBoundary";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import Stepper from "@mui/material/Stepper";
import Step from "@mui/material/Step";
import StepLabel from "@mui/material/StepLabel";
import StepContent from "@mui/material/StepContent";
import Result from "../../../util/result";
import axios, { type Axios } from "@/common/axios";
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
import Typography from "@mui/material/Typography";
import EventBoundary from "../../../components/EventBoundary";

type AddFilestoreDialogArgs = {|
  open: boolean,

  /**
   * Will be called with `true` if the user successfully added a new a
   * filestore. Will be called with `false` if the user requests that the
   * operation be cancelled for any other reason. In either case the caller
   * MUST set `open` to `false`.
   */
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
  const [chosenFilesystem, setChosenFilesysem] = React.useState<{|
    id: number,
    name: string,
    url: string,
  |} | null>(null);
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
      <StepLabel
        optional={
          <Typography variant="body2">
            Your sysadmin needs to configure a file system before it appears
            here.
          </Typography>
        }
      >
        Select a File system
      </StepLabel>
      <StepContent>
        <RadioGroup
          value={
            chosenFilesystem?.id ??
            selectedFilesystem.map(({ id }) => id).orElse(null)
          }
          onChange={({ target: { value } }) => {
            const chosenId = parseInt(value, 10);
            Optional.fromNullable(filesystems)
              .flatMap((fss) =>
                ArrayUtils.find(({ id }) => id === chosenId, fss)
              )
              .do((fs) => {
                setChosenFilesysem(fs);
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
        <Box sx={{ mb: 2 }}>
          <Button
            disabled={!chosenFilesystem}
            variant="contained"
            color="primary"
            onClick={() => {
              if (chosenFilesystem !== null)
                setSelectedFilesystem(chosenFilesystem);
            }}
            sx={{ mt: 1, mr: 1 }}
          >
            Choose Filesystem
          </Button>
        </Box>
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
  const { addAlert } = React.useContext(AlertContext);
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
        const { data } = await (
          await api.current
        ).get<{ content: FilesystemListing, ... }>(
          `filesystems/${fsId}/browse?remotePath=${path}`
        );
        if (!data.content) throw new Error("No content");
        setListing(data.content);
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
          addAlert(
            mkAlert({
              variant: "error",
              title: "Failed to browse filestore",
              message: Parsers.objectPath(["response", "data", "message"], e)
                .flatMap(Parsers.isString)
                .orElse(e.message),
            })
          );
        }
      }
    }
    void browse();
    /* eslint-disable-next-line react-hooks/exhaustive-deps --
     * - fsName will only change when fsId does
     * - login wont change
     * - onFailToAuthenticate will not meaningfully change
     */
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
                path={encodeURIComponent(`${path}${name}/`)}
                onFailToAuthenticate={onFailToAuthenticate}
              />
            </TreeItem>
          )
      )}
    </>
  );
}

function FolderSelectionStep(props: {|
  selectedFilesystem: Optional<{| id: number, name: string, url: string |}>,
  onConfirm: (string) => void,
  onCancel: () => void,
|}) {
  const { selectedFilesystem, onConfirm, onCancel, ...rest } = props;
  const [expandedItems, setExpandedItems] = React.useState<
    $ReadOnlyArray<number>
  >([]);
  const [selectedFolderPath, setSelectedFolderPath] = React.useState("");

  return (
    <Step key="folderSelection" {...rest}>
      <StepLabel
        optional={
          <Typography variant="body2">
            You can configure multiple Filestores from the same File system with
            different top-level folders, to facilitate accessing deeply-nested
            content.
          </Typography>
        }
      >
        Select the top-level Folder for the Filestore
      </StepLabel>
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
          {selectedFilesystem
            .map(({ id, name }) => (
              <TreeListing
                path="/"
                fsId={id}
                fsName={name}
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
      <StepLabel
        optional={
          <Typography variant="body2">
            This name is used in RSpace to help you identify the Filestore.
          </Typography>
        }
      >
        Name the Filestore
      </StepLabel>
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

/**
 * A three-step wizard for adding a new filestore to the filestores section of
 * the Gallery. The user can choose from amongst the filesystems that the
 * sysadmin has already configured, they can pick their folder on that
 * filesystem, and they can give it a name. Once submitted, a new filesystem
 * appears in the filesystems section.
 */
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
      const message = Parsers.objectPath(["response", "data", "message"], e)
        .flatMap(Parsers.isString)
        .orElse(e.message);
      console.error(e);
      addAlert(
        mkAlert({
          variant: "error",
          title: "Failed to add new filestore",
          message,
        })
      );
    }
  }

  return (
    <EventBoundary>
      <Dialog
        fullWidth
        maxWidth="sm"
        open={open}
        onClose={() => onClose(false)}
      >
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
              selectedFilesystem={selectedFilesystem}
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
        <DialogActions>
          <Button
            onClick={() => {
              onClose(false);
            }}
          >
            Cancel
          </Button>
        </DialogActions>
      </Dialog>
    </EventBoundary>
  );
}
