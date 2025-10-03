import React, { useEffect, useState } from "react";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import FormControlLabel from "@mui/material/FormControlLabel";
import Switch from "@mui/material/Switch";
import Grid from "@mui/material/Grid";
import Stack from "@mui/material/Stack";
import axios from "@/common/axios";
import { type ExportSelection } from "./common";
import { type Validator } from "../util/Validator";
import { observer } from "mobx-react-lite";
import { type Repo } from "./repositories/common";
import { Optional } from "../util/optional";
import { useDeploymentProperty } from "../eln/useDeploymentProperty";
import * as FetchingData from "../util/fetchingData";
import * as Parsers from "../util/parsers";
import {
  OptionHeading,
  OptionExplanation,
} from "../components/Inputs/RadioField";

export type ArchiveType = "pdf" | "doc" | "xml" | "html" | "eln";

const WORD_ERRORS = [
  "Word export is only available for a single document, and you have selected more than one.",
  "Word export is only available for a single document, and you've selected a folder.",
  "Word export is only available for a single document or notebook entry, and you've selected a Notebook.",
  "All selected items are attachments — there are no RSpace documents to export.",
];

type FormatChoiceArgs = {
  exportConfigUpdate: {
    (key: "repoData", repos: ReadonlyArray<Repo>): void;
    (key: "archiveType", archiveType: ArchiveType): void;
    (key: "fileStores", includeFilesInFilestores: boolean): void;
    (key: "allVersions", allVersions: boolean): void;
    (key: "repository", exportToRepository: boolean): void;
  };
  exportSelection: ExportSelection;
  validator: Validator;
  archiveType: ArchiveType | "";
  allowFileStores: boolean;
  repoSelected: boolean;
  allVersions: boolean;
  fileStoresSelected: boolean;
  updateFileStores: (key: "includeNfsFiles", incluedNfsFiles: boolean) => void;
};

function FormatChoice({
  exportConfigUpdate,
  exportSelection,
  validator,
  archiveType,
  allowFileStores,
  repoSelected,
  allVersions,
  fileStoresSelected,
  updateFileStores,
}: FormatChoiceArgs): React.ReactNode {
  const [msgBlockingRepoChoice, setMsgBlockingRepoChoice] = useState(
    Optional.present("Loading")
  );
  const [pdfAvailable, setPdfAvailable] = useState(false);
  const [wordAvailable, setWordAvailable] = useState(false);
  const [wordAvailabilityMessage, setWordAvailabilityMessage] = useState("");
  const asposeEnabled = useDeploymentProperty("aspose.enabled");

  const repoCheck = () => {
    const url = "/repository/ajax/repo/uiConfig";

    axios
      .get<
        | Array<Omit<Repo, "repoCfg"> & { options: object }>
        | { exceptionMessage: string }
      >(url)
      .then((response) => {
        const repos = response.data;
        if (!Array.isArray(repos)) throw new Error(repos.exceptionMessage);
        if (repos.length === 0) {
          setMsgBlockingRepoChoice(
            Optional.present(
              "You have not setup a repository, to do so please activate them within Apps"
            )
          );
          return;
        }
        setMsgBlockingRepoChoice(Optional.empty());
        exportConfigUpdate(
          "repoData",
          repos.flatMap((repo) => {
            if (repo.repoName === "app.dataverse") {
              /*
               * On the apps page, users can configure multiple dataverses, so
               * here we process that so each dataverse config is treated as a
               * separate repository. The `keys` for each option is the string
               * of a integer, and so too is `repoCfg`, but that is not really
               * an important detail.
               */
              const keys = Object.keys(repo.options);
              if (keys.length) {
                return keys.map(
                  (k) =>
                    ({
                      repoCfg: k,
                      //@ts-expect-error Options is poorly typed
                      // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access
                      label: repo.options[k]._label,
                    //@ts-expect-error Options is poorly typed
                    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment, @typescript-eslint/no-unsafe-member-access
                      metadataLanguages: repo.options[k].metadataLanguages,
                      ...repo,
                    } as Repo)
                );
              }
              return [];
            }
            /*
             * On the apps page, users can only configure a just one
             * destination for each of the other repository services so we
             * just copy the object from the API
             */
            return [{ repoCfg: -1, ...repo }];
          })
        );
      })
      .catch(() => {
        setMsgBlockingRepoChoice(
          Optional.present(
            "Export to repository is not available because there was an error fetching repository configurations."
          )
        );
      });
  };

  const pdfCheck = () => {
    let disabled = false;
    let allMedia = false;
    let isSystem = false;
    // @ts-expect-error Global variable
    // eslint-disable-next-line @typescript-eslint/no-unsafe-assignment
    const isGallery = typeof isGalleryPage !== "undefined" && isGalleryPage;

    if (exportSelection.type === "selection") {
      // if all are media, there's nothing to export in this format: RSpac1333
      allMedia = exportSelection.exportTypes.every((n) => n === "MEDIA_FILE");
      // @ts-expect-error Global variable
    } else if (typeof isSystemFolderChecked === "function") {
      isSystem = true;
    }
    if (allMedia || isSystem || isGallery) {
      disabled = true;
    }

    setPdfAvailable(!disabled);
  };

  const wordCheck = () => {
    let disabledBecauseMultiple = false;
    let disabledBecauseFolder = false;
    let disabledBecauseNotebook = false;
    let disabledBecauseAllMedia = false;

    if (
      exportSelection.type === "selection" &&
      exportSelection.exportIds.length === 1
    ) {
      const selectedType = exportSelection.exportTypes[0];
      if (selectedType.indexOf("FOLDER") >= 0) {
        disabledBecauseFolder = true;
      } else if (selectedType === "NOTEBOOK") {
        disabledBecauseNotebook = true;
      }
    } else {
      disabledBecauseMultiple = true;
    }
    if (exportSelection.type === "selection") {
      disabledBecauseAllMedia = exportSelection.exportTypes.every(
        (n) => n === "MEDIA_FILE"
      );
    }

    const wordExportAllowed =
      !disabledBecauseMultiple &&
      !disabledBecauseFolder &&
      !disabledBecauseNotebook &&
      !disabledBecauseAllMedia;

    setWordAvailable(wordExportAllowed);

    if (disabledBecauseMultiple) setWordAvailabilityMessage(WORD_ERRORS[0]);
    if (disabledBecauseFolder) setWordAvailabilityMessage(WORD_ERRORS[1]);
    if (disabledBecauseNotebook) setWordAvailabilityMessage(WORD_ERRORS[2]);
    if (disabledBecauseAllMedia) setWordAvailabilityMessage(WORD_ERRORS[3]);
  };

  useEffect(() => {
    repoCheck();
    pdfCheck();
    wordCheck();
  }, []);

  useEffect(() => {
    validator.setValidFunc(() => Promise.resolve(true));
  }, [validator]);

  const handleChange = ({
    target: { value },
  }: {
    target: { value: ArchiveType };
  }) => {
    exportConfigUpdate("archiveType", value);
    if (value === "pdf" || value === "doc")
      exportConfigUpdate("fileStores", false);
    if (value !== "xml") exportConfigUpdate("allVersions", false);
  };

  return (
    <Grid container>
      <h3>Choose an appropriate format for your export</h3>
      <RadioGroup
        aria-label="Select Export"
        name="exportType"
        value={archiveType}
        // @ts-expect-error TypeScript doesn't realise that the value can only be one of the ArchiveType values
        onChange={handleChange}
      >
        <Stack spacing={2}>
          <FormControlLabel
            value="html"
            control={<Radio data-test-id="zip-html" color="primary" />}
            label={
              <>
                <OptionHeading>
                  .ZIP bundle containing .HTML files
                </OptionHeading>
                <OptionExplanation>
                  Exported data, notebooks and attached files can be accessed
                  offline with a browser.
                </OptionExplanation>
              </>
            }
          />
          <FormControlLabel
            value="xml"
            control={<Radio data-test-id="zip-xml" color="primary" />}
            label={
              <>
                <OptionHeading>.ZIP bundle containing .XML files</OptionHeading>
                <OptionExplanation>
                  Exported data is machine readable. Good for archiving, or
                  transferring data from one RSpace server or user to another.
                </OptionExplanation>
              </>
            }
          />
          <FormControlLabel
            value="pdf"
            disabled={!pdfAvailable}
            control={<Radio data-test-id="pdf" color="primary" />}
            label={
              <>
                <OptionHeading>PDF file</OptionHeading>
                <OptionExplanation>
                  {pdfAvailable ? (
                    <>
                      A read-only version of your RSpace documents will be
                      placed in the &apos;Exports&apos; area of the Gallery
                    </>
                  ) : (
                    <>
                      All selected items are attachments &mdash; there are no
                      RSpace documents to export.
                    </>
                  )}
                </OptionExplanation>
              </>
            }
          />
          <FormControlLabel
            value="eln"
            control={<Radio data-test-id="zip-eln" color="primary" />}
            label={
              <>
                <OptionHeading>RO-Crate</OptionHeading>
                <OptionExplanation>
                  An XML bundle with an RO-Crate metadata file, zipped into a
                  .eln archive.
                </OptionExplanation>
              </>
            }
          />
          {FetchingData.getSuccessValue(asposeEnabled)
            .flatMap(Parsers.isBoolean)
            .orElse(false) && (
            <FormControlLabel
              value="doc"
              disabled={!wordAvailable}
              control={<Radio data-test-id="doc" color="primary" />}
              label={
                <>
                  <OptionHeading>.DOC file</OptionHeading>
                  <OptionExplanation>
                    {wordAvailable ? (
                      <>
                        MS Word version of your RSpace documents will be placed
                        in the &apos;Exports&apos; area of the Gallery.
                      </>
                    ) : (
                      wordAvailabilityMessage
                    )}
                  </OptionExplanation>
                </>
              }
            />
          )}
        </Stack>
      </RadioGroup>

      <h3 style={{ marginTop: "20px" }}>Choose additional destinations</h3>
      <Grid item xs={12}>
        <FormControlLabel
          control={
            <Switch
              checked={repoSelected}
              onChange={({ target: { checked } }) =>
                exportConfigUpdate("repository", checked)
              }
              value="repository"
              color="primary"
              disabled={msgBlockingRepoChoice.isPresent()}
              data-test-id="repo"
            />
          }
          label={msgBlockingRepoChoice.orElse("Export to a repository")}
        />
      </Grid>
      {allowFileStores &&
        (archiveType === "html" ||
          archiveType === "xml" ||
          archiveType === "eln") && (
          <Grid item xs={12}>
            <h3>Filestores</h3>
            <FormControlLabel
              control={
                <Switch
                  checked={fileStoresSelected}
                  onChange={({ target: { checked } }) => {
                    exportConfigUpdate("fileStores", checked);
                    updateFileStores("includeNfsFiles", checked);
                  }}
                  value="fileStores"
                  color="primary"
                  data-test-id="filestores"
                />
              }
              label="Include filestore links"
            />
          </Grid>
        )}
      {(archiveType === "xml" || archiveType === "eln") && (
        <Grid item xs={12}>
          <h3>Revisions</h3>
          <FormControlLabel
            control={
              <Switch
                checked={allVersions}
                onChange={({ target: { checked } }) => {
                  exportConfigUpdate("allVersions", checked);
                }}
                value="allVersions"
                color="primary"
                data-test-id="allVersions"
              />
            }
            label="Check to include all previous versions of your documents, or leave unchecked for only current version"
          />
        </Grid>
      )}
    </Grid>
  );
}

export default observer(FormatChoice);
