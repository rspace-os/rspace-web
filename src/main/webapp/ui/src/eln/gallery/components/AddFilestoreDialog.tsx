import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Step from "@mui/material/Step";
import StepContent from "@mui/material/StepContent";
import StepLabel from "@mui/material/StepLabel";
import Stepper from "@mui/material/Stepper";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { SimpleTreeView } from "@mui/x-tree-view/SimpleTreeView";
import { TreeItem } from "@mui/x-tree-view/TreeItem";
import React from "react";
import { useTranslation } from "react-i18next";
import axios from "@/common/axios";
import { Dialog } from "../../../components/DialogBoundary";
import EventBoundary from "../../../components/EventBoundary";
import useOauthToken from "../../../hooks/auth/useOauthToken";
import AlertContext, { mkAlert } from "../../../stores/contexts/Alert";
import { Optional } from "../../../util/optional";
import * as Parsers from "../../../util/parsers";
import Result from "../../../util/result";
import { useFilestoreLogin } from "./FilestoreLoginDialog";

type AddFilestoreDialogArgs = {
  open: boolean;

  /**
   * Will be called with `true` if the user successfully added a new a
   * filestore. Will be called with `false` if the user requests that the
   * operation be cancelled for any other reason. In either case the caller
   * MUST set `open` to `false`.
   */
  onClose: (success: boolean) => void;
};

function FilesystemSelectionStep(props: {
  setSelectedFilesystem: ({
    id,
    name,
    url,
    clientType,
  }: {
    id: number;
    name: string;
    url: string;
    clientType: string;
  }) => void;
  selectedFilesystem: Optional<{
    id: number;
    name: string;
    url: string;
    clientType: string;
  }>;
}) {
  const { selectedFilesystem, setSelectedFilesystem, ...rest } = props;
  const { t } = useTranslation("gallery");
  const [chosenFilesystem, setChosenFilesysem] = React.useState<{
    id: number;
    name: string;
    url: string;
    clientType: string;
  } | null>(null);
  const [filesystems, setFilesystems] = React.useState<null | ReadonlyArray<{
    id: number;
    name: string;
    url: string;
    canRead: boolean;
    clientType: string;
  }>>(null);
  const { getToken } = useOauthToken();
  const api = React.useRef(
    (async () => {
      return axios.create({
        baseURL: "/api/v1/gallery",
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
    })(),
  );

  React.useEffect(() => {
    void (async () => {
      const { data } = await (await api.current).get<unknown>("filesystems");
      Parsers.isArray(data)
        .flatMap((array) =>
          Result.all(
            ...array.map((m) =>
              Parsers.isObject(m)
                .flatMap(Parsers.isNotNull)
                .flatMap((obj) => {
                  try {
                    const id = Parsers.getValueWithKey("id")(obj).flatMap(Parsers.isNumber).elseThrow();
                    const name = Parsers.getValueWithKey("name")(obj).flatMap(Parsers.isString).elseThrow();
                    const url = Parsers.getValueWithKey("url")(obj).flatMap(Parsers.isString).elseThrow();
                    const canRead = Parsers.getValueWithKey("userPermissions")(obj)
                      .flatMap(Parsers.isObject)
                      .flatMap(Parsers.isNotNull)
                      .flatMap(Parsers.getValueWithKey("canRead"))
                      .flatMap(Parsers.isBoolean)
                      .orElse(true);
                    const clientType = Parsers.getValueWithKey("clientType")(obj).flatMap(Parsers.isString).orElse("");
                    return Result.Ok({ id, name, url, canRead, clientType });
                  } catch (e) {
                    return Result.Error<{
                      id: number;
                      name: string;
                      url: string;
                      canRead: boolean;
                      clientType: string;
                    }>([e instanceof Error ? e : new Error("Unknown error")]);
                  }
                }),
            ),
          ),
        )
        .do((newFilesystems) => setFilesystems(newFilesystems));
    })();
  }, []);

  return (
    <Step key="filesystemSelection" component="div" {...rest}>
      <StepLabel optional={<Typography variant="body2">{t("addFilestoreDialog.filesystemHelp")}</Typography>}>
        {t("addFilestoreDialog.filesystemStepLabel")}
      </StepLabel>
      <StepContent>
        <RadioGroup
          value={chosenFilesystem?.id ?? selectedFilesystem.map(({ id }) => id).orElse(null)}
          onChange={({ target: { value } }) => {
            const chosenId = parseInt(value, 10);
            Optional.fromNullable(filesystems)
              .flatMap((fss) => Optional.fromNullable(fss.find(({ id }) => id === chosenId)))
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
              disabled={!fs.canRead}
              label={
                fs.canRead ? (
                  fs.name
                ) : (
                  <>
                    {fs.name}
                    <Typography component="span" variant="body2" sx={{ ml: 1, color: "text.secondary" }}>
                      {t("addFilestoreDialog.noReadAccess")}
                    </Typography>
                  </>
                )
              }
            />
          ))}
        </RadioGroup>
        <Box sx={{ mb: 2 }}>
          <Button
            disabled={!chosenFilesystem}
            variant="contained"
            color="primary"
            onClick={() => {
              if (chosenFilesystem !== null) setSelectedFilesystem(chosenFilesystem);
            }}
            sx={{ mt: 1, mr: 1 }}
          >
            {t("addFilestoreDialog.chooseFilesystem")}
          </Button>
        </Box>
      </StepContent>
    </Step>
  );
}

type FilesystemListing = ReadonlyArray<{
  nfsId: number;
  name: string;
  folder: boolean;
}>;

function TreeListing({
  fsId,
  fsName,
  path,
  onFailToAuthenticate,
  showBucketTopLevel = false,
}: {
  fsId: number;
  fsName: string;
  path: string;
  onFailToAuthenticate: () => void;
  /** Root S3 listing only: prepend the "(bucket top level)" option once the folders have loaded. */
  showBucketTopLevel?: boolean;
}): React.ReactNode {
  const { t } = useTranslation("gallery");
  const { getToken } = useOauthToken();
  const { addAlert } = React.useContext(AlertContext);
  const api = React.useRef(
    (async () => {
      return axios.create({
        baseURL: "/api/v1/gallery",
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
    })(),
  );

  const [listing, setListing] = React.useState<FilesystemListing>([]);
  const [loading, setLoading] = React.useState(true);
  const { login } = useFilestoreLogin();
  React.useEffect(() => {
    setLoading(true);
    async function browse(): Promise<void> {
      try {
        const { data } = await (await api.current).get<{ content: FilesystemListing }>(
          `filesystems/${fsId}/browse?remotePath=${path}`,
        );
        if (!data.content) throw new Error("No content");
        setListing(data.content);
      } catch (e) {
        if (!(e instanceof Error)) return;
        Parsers.getValueWithKey("response")(e).do(
          (response) =>
            void Parsers.isObject(response)
              .flatMap(Parsers.isNotNull)
              .flatMap(Parsers.getValueWithKey("status"))
              .flatMap(Parsers.isNumber)
              .doAsync(async (status) => {
                if (status === 403) {
                  const success = await login({
                    filesystemName: fsName,
                    filesystemId: fsId,
                  });
                  if (success) {
                    await browse();
                  } else {
                    onFailToAuthenticate();
                  }
                } else {
                  addAlert(
                    mkAlert({
                      variant: "error",
                      title: t("addFilestoreDialog.browseFailed"),
                      message: Parsers.objectPath(["data", "message"], response)
                        .flatMap(Parsers.isString)
                        .orElse(e.message),
                    }),
                  );
                }
              }),
        );
      } finally {
        setLoading(false);
      }
    }
    void browse();
  }, [fsId, path]);

  // Root S3 listing: hold back the "(bucket top level)" option until the folders have loaded, so
  // every option appears at once rather than top-level looking like the only choice.
  if (showBucketTopLevel && loading) {
    return <TreeItem itemId="__loading__" label={t("addFilestoreDialog.loading")} disabled />;
  }
  return (
    <>
      {showBucketTopLevel && <TreeItem itemId={TOP_LEVEL_ITEM_ID} label={t("addFilestoreDialog.bucketTopLevel")} />}
      {listing.map(
        ({ folder, name }) =>
          folder && (
            <TreeItem itemId={`${path}${name}/`} label={name} key={`${path}${name}/`}>
              <TreeListing
                fsId={fsId}
                fsName={name}
                path={path + encodeURIComponent(`${name}/`)}
                onFailToAuthenticate={onFailToAuthenticate}
              />
            </TreeItem>
          ),
      )}
    </>
  );
}

// Sentinel tree-item id for "the bucket top level" (an empty filestore path). Distinct from any
// real folder id, which always ends in "/".
const TOP_LEVEL_ITEM_ID = "__top_level__";

function FolderSelectionStep(props: {
  selectedFilesystem: Optional<{ id: number; name: string; url: string; clientType: string }>;
  onConfirm: (folderPath: string) => void;
  onCancel: () => void;
}) {
  const { selectedFilesystem, onConfirm, onCancel, ...rest } = props;
  const { t } = useTranslation(["gallery", "common"]);
  const [expandedItems, setExpandedItems] = React.useState<Array<string>>([]);
  // null = nothing chosen yet; "" = the bucket top level (S3); otherwise a subfolder path.
  const [selectedFolderPath, setSelectedFolderPath] = React.useState<string | null>(null);
  // S3 folders are virtual, so an S3 filestore may be rooted at the bucket top level (empty path).
  const isS3 = selectedFilesystem.map(({ clientType }) => clientType === "S3").orElse(false);

  return (
    <Step key="folderSelection" component="div" {...rest}>
      <StepLabel optional={<Typography variant="body2">{t("addFilestoreDialog.folderHelp")}</Typography>}>
        {t("addFilestoreDialog.folderStepLabel")}
      </StepLabel>
      <StepContent>
        <SimpleTreeView
          expandedItems={expandedItems}
          onExpandedItemsChange={(_event, nodeIds) => {
            setExpandedItems(nodeIds);
          }}
          onItemSelectionToggle={(_event, itemId: string | ReadonlyArray<string>, selected) => {
            if (!(typeof itemId === "string")) return;
            if (!selected) return;
            setSelectedFolderPath(itemId === TOP_LEVEL_ITEM_ID ? "" : decodeURIComponent(itemId));
          }}
        >
          {selectedFilesystem
            .map(({ id, name }) => (
              <TreeListing
                path="%2F"
                fsId={id}
                fsName={name}
                key={null}
                showBucketTopLevel={isS3}
                onFailToAuthenticate={() => {
                  onCancel();
                }}
              />
            ))
            .orElse(null)}
        </SimpleTreeView>
        <Box sx={{ mb: 2 }}>
          <Button
            disabled={selectedFolderPath === null}
            variant="contained"
            color="primary"
            onClick={() => {
              if (selectedFolderPath !== null) onConfirm(selectedFolderPath);
            }}
            sx={{ mt: 1, mr: 1 }}
          >
            {t("addFilestoreDialog.chooseFolder")}
          </Button>
          <Button onClick={onCancel} sx={{ mt: 1, mr: 1 }}>
            {t("common:actions.back")}
          </Button>
        </Box>
      </StepContent>
    </Step>
  );
}

function NameStep(props: { onConfirm: (name: string) => void; onCancel: () => void }) {
  const { onConfirm, onCancel, ...rest } = props;
  const { t } = useTranslation(["gallery", "common"]);
  const [name, setName] = React.useState("");
  return (
    <Step key="name" component="div" {...rest}>
      <StepLabel optional={<Typography variant="body2">{t("addFilestoreDialog.nameHelp")}</Typography>}>
        {t("addFilestoreDialog.nameStepLabel")}
      </StepLabel>
      <StepContent>
        <TextField
          value={name}
          onChange={({ target: { value } }) => {
            setName(value);
          }}
          slotProps={{
            htmlInput: {
              "aria-label": t("addFilestoreDialog.nameLabel"),
            },
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
            {t("addFilestoreDialog.addFilestore")}
          </Button>
          <Button onClick={onCancel} sx={{ mt: 1, mr: 1 }}>
            {t("common:actions.back")}
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
export default function AddFilestoreDialog({ open, onClose }: AddFilestoreDialogArgs): React.ReactNode {
  const { t } = useTranslation(["gallery", "common"]);
  const [activeStep, setActiveStep] = React.useState(-1);
  React.useEffect(() => {
    if (open) {
      setActiveStep(0);
    } else {
      setActiveStep(-1);
    }
  }, [open, setActiveStep]);

  const [selectedFilesystem, setSelectedFilesystem] = React.useState<
    Optional<{
      id: number;
      name: string;
      url: string;
      clientType: string;
    }>
  >(Optional.empty());

  const [pathOfSelectedFolder, setPathOfSelectedFolder] = React.useState("");

  const { addAlert } = React.useContext(AlertContext);
  const { getToken } = useOauthToken();
  const api = React.useRef(
    (async () => {
      return axios.create({
        baseURL: "/api/v1/gallery",
        headers: {
          Authorization: `Bearer ${await getToken()}`,
        },
      });
    })(),
  );
  async function addFilestore(name: string) {
    try {
      const filesystemId = selectedFilesystem
        .toResult(() => new Error("No filestore has been selected,"))
        .elseThrow().id;
      await (await api.current).post<unknown>(
        "filestores",
        {},
        {
          params: {
            filesystemId,
            name,
            pathToSave: pathOfSelectedFolder,
          },
        },
      );
      addAlert(
        mkAlert({
          variant: "success",
          message: t("addFilestoreDialog.addSuccess"),
        }),
      );
      onClose(true);
    } catch (e) {
      console.error(e);
      if (e instanceof Error) {
        const message = Parsers.objectPath(["response", "data", "message"], e)
          .flatMap(Parsers.isString)
          .orElse(e.message);
        addAlert(
          mkAlert({
            variant: "error",
            title: t("addFilestoreDialog.addFailed"),
            message,
          }),
        );
      }
    }
  }

  return (
    <EventBoundary>
      <Dialog fullWidth maxWidth="sm" open={open} onClose={() => onClose(false)}>
        <DialogTitle>{t("addFilestoreDialog.addFilestore")}</DialogTitle>
        <DialogContent>
          <Stepper activeStep={activeStep} component="div" orientation="vertical">
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
            {t("common:actions.cancel")}
          </Button>
        </DialogActions>
      </Dialog>
    </EventBoundary>
  );
}
