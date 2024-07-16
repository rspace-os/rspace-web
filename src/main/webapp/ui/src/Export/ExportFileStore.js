//@flow

import React, { type Node, useState, useEffect } from "react";
import Grid from "@mui/material/Grid";
import Card from "@mui/material/Card";
import LoadingFade from "../components/LoadingFade";
import axios from "axios";
import { formatFileSize } from "../util/files";
import { sum } from "../util/iterators";
import RsSet, { flattenWithUnion } from "../util/set";
import FoundLinksListing from "./fileStoreComponents/FoundLinksListing";
import LoginStatus from "./fileStoreComponents/LoginStatus";
import FileFilters from "./fileStoreComponents/FileFilters";
import LinkAvailabilityScan from "./fileStoreComponents/LinkAvailabilityScan";
import { type UseState } from "../util/types";
import { type Validator } from "../util/Validator";
import useConfirm from "../util/useConfirm";
import { Optional } from "../util/optional";
import { runInAction, observable } from "mobx";
import { type ExportSelection } from "./ExportDialog";

/*
 * When exporting RSpace documents that contain references to files in
 * institutional filestores, the export process can pull those files and add
 * them to the export bundle. If the user chooses to do so when exporting to a
 * .zip bundle, then an additional step will be added to the export wizard, as
 * implemented below.
 *
 * To test this code,
 * (1) Enable the test samba file store by logging in as a sysadmin, and under
 *     System -> Configuration -> Institutional File Systems add the details
 *     stored in [HowlerSambaSetupConstants.java].
 * (2) As a regular user, go to the Filestores Gallery tab and add a new
 *     filestore. To do this, tap the "Add" button under "My Filestores" and
 *     login with the details again found in [HowlerSambaSetupConstants.java].
 *     Select a folder and give it a name in the dialog that opens.
 * (3) Add a link to a file in the filestore to an RSpace document. This is
 *     done just like any other Gallery file.
 * (4) Export that RSpace document. In the export dialog, choose one of the
 *     .ZIP options and, when it appears, tap the "Include filestore links"
 *     switch. A third page will have been added to the end of the wizard, the
 *     contents of which is the code below.
 *
 * [HowlerSambaSetupConstants.java]: src/test/java/com/researchspace/netfiles/samba/HowlerSambaSetupConstants.java
 *
 */

type Path = string;
export type FileLink = {
  type: "file",
  size: number,
  fileSystemFullPath: Path,
  path: Path,
};
type FolderLink = {
  type: "folder",
  // eslint-disable-next-line no-use-before-define
  content: Array<MixedLink>,
  fileSystemFullPath: Path,
  size: null,
};

type MixedLink = FileLink | FolderLink;

type FoundFileLink = {|
  linkType: "file" | "directory",
  path: Path,
|};

export type FileSystemId = string;
export type FileSystem = {|
  id: FileSystemId,
  name: string,
  foundNfsLinks: Array<FoundFileLink>,
  loggedAs: ?string,
  checkedNfsLinks: Array<?MixedLink>,
  checkedNfsLinkMessages: { [Path]: string },
|};

type ExportConfig = {|
  archiveType: string,
  repository: boolean,
  fileStores: boolean,
  allVersions: boolean,
  repoData: Array<mixed>,
|};

type NfsConfig = {|
  excludedFileExtensions: string, // comma-separated
  includeNfsFiles: boolean,
  maxFileSizeInMB: number,
|};

type ExportPlanId = number;

type FullExportPlan = {|
  planId: ExportPlanId,
  foundFileSystems: Array<FileSystem>,
  maxArchiveSizeMBProp: number,
  currentlyAllowedArchiveSizeMB: number,
|};

type ExportFileStoreArgs = {|
  exportConfig: ExportConfig,
  exportSelection: ExportSelection,
  nfsConfig: NfsConfig,
  updateFilters: (("maxFileSizeInMB", number) => void) &
    (("excludedFileExtensions", string) => void),
  validator: Validator,
|};

export default function ExportFileStore({
  exportConfig,
  exportSelection,
  nfsConfig,
  updateFilters,
  validator,
}: ExportFileStoreArgs): Node {
  const [loadingQuickPlan, setLoadingQuickPlan]: UseState<boolean> =
    useState(false);
  const [loadingFullPlan, setLoadingFullPlan]: UseState<boolean> =
    useState(false);

  const [totalFilesFound, setTotalFilesFound]: UseState<number> = useState(0);
  const [fileSystems, setFileSystems]: UseState<Array<FileSystem>> = useState(
    []
  );
  const [checkedFileSystems, setCheckedFileSystems]: UseState<
    Array<FileSystem>
  > = useState([]);
  const [planId, setPlanId]: UseState<ExportPlanId> = useState(0);
  const [maxFileSizeInMB, setMaxFileSizeInMB]: UseState<number> = useState(
    nfsConfig.maxFileSizeInMB
  );
  const [excludedFileExtensions, setExcludedFileExtensions]: UseState<string> =
    useState(nfsConfig.excludedFileExtensions);

  // extracted and computed data from full export plan scan
  const [scanResultsPresent, setScanResultsPresent] = useState(false);
  const [scanResultsOmittedCount, setScanResultsOmittedCount] = useState(0);
  const [scanResultsAvailableCount, setScanResultsAvailableCount] = useState(0);
  const [scanResultsTotalFileSize, setScanResultsTotalFileSize] = useState(0);

  const [validationData] = useState(
    observable({
      totalFilesFound: 0,
      loggedOut: ([]: Array<FileSystem>),
      scanResultsPresent: false,
      maxArchiveSizeBytes: 0,
      scanResultsTotalFileSize: 0,
      currentlyAllowedArchiveSizeBytes: 0,
    })
  );

  const confirm = useConfirm();

  useEffect(() => {
    validator.setValidFunc(async () => {
      if (
        validationData.totalFilesFound > 0 &&
        validationData.loggedOut.length > 0
      ) {
        if (
          !(await confirm(
            "",
            "You are not logged into all required File Systems and some filestore links won't be exported. Do you want to proceed without logging in?",
            "Yes, proceed"
          ))
        )
          return false;
      }

      if (!validationData.scanResultsPresent) {
        if (
          !(await confirm(
            "",
            "You have not performed links availability scan. We strongly recommend running it, as it will report any potential problems with accessing filestore link. Do you want to proceed without running the scan?",
            "Yes, proceed"
          ))
        )
          return false;
      }

      if (
        validationData.scanResultsPresent &&
        validationData.maxArchiveSizeBytes !== 0 &&
        validationData.scanResultsTotalFileSize >=
          validationData.currentlyAllowedArchiveSizeBytes
      ) {
        await confirm(
          "",
          <>
            The size of filestore files to be included in export (
            {formatFileSize(validationData.scanResultsTotalFileSize)})
            {validationData.scanResultsTotalFileSize >
            validationData.maxArchiveSizeBytes ? (
              <span>
                {" "}
                exceeds the global limit set for size of RSpace archive file.
                Use file filters to exclude some files, or ask your System Admin
                to raise the limit on archive size.
              </span>
            ) : (
              <span>
                {" "}
                exceeds disk space currently available on RSpace server. Use
                file filters to exclude some files, or contact your System
                Admin.
              </span>
            )}
          </>,
          "OK",
          ""
        );
        return false;
      }

      return true;
    });
  }, []);

  const fileStoreCheck = () => {
    const url = "/nfsExport/ajax/createQuickExportPlan";
    setLoadingQuickPlan(true);

    axios
      .post<
        {|
          exportConfig: typeof exportConfig,
          exportSelection: typeof exportSelection,
          nfsConfig: typeof nfsConfig,
        |},
        {| foundFileSystems: Array<FileSystem>, planId: ExportPlanId |}
      >(url, { exportConfig, exportSelection, nfsConfig })
      .then((response) => {
        console.log(response);

        const fileSystems = response.data.foundFileSystems;
        setFileSystems(fileSystems);

        const newTotalFilesFound = sum(
          fileSystems.map((fs) => fs.foundNfsLinks.length)
        );
        setTotalFilesFound(newTotalFilesFound);

        runInAction(() => {
          validationData.loggedOut = fileSystems.filter((fs) => !fs.loggedAs);
          validationData.totalFilesFound = newTotalFilesFound;
        });

        setPlanId(response.data.planId);
      })
      .catch((error) => {
        console.log(error);
      })
      .finally(() => {
        setLoadingQuickPlan(false);
      });
  };

  const calculateFullScanSummary = (plan: FullExportPlan) => {
    console.log("full scan result", plan);

    const fileSystems: RsSet<FileSystem> = new RsSet(plan.foundFileSystems);

    const omittedCount = sum(
      fileSystems.map(
        // checkedNfsLinkMessages will contain an error message for each link that is to be omitted
        (fileSystem) => Object.keys(fileSystem.checkedNfsLinkMessages).length
      )
    );

    const isFile = (link: MixedLink): link is FileLink => link.type === "file";
    const isFolder = (link: MixedLink): link is FolderLink => link.type === "folder";

    // FileSystem is bundled with each link for further filtering
    const links: RsSet<{ fs: FileSystem, link: MixedLink }> = flattenWithUnion(
      fileSystems.map((fs) =>
        new RsSet(fs.checkedNfsLinks).mapOptional((link) =>
          link ? Optional.present({ fs, link }) : Optional.empty()
        )
      )
    );

    const includedLinks: RsSet<MixedLink> = links
      .filter(
        // again, checkedNfsLinkMessages contains an error for each omitted link
        ({ fs, link }) => !fs.checkedNfsLinkMessages[link.fileSystemFullPath]
      )
      .map(({ link }) => link);

    const rootFileLinks: RsSet<FileLink> = includedLinks.mapOptional((link) =>
      isFile(link) ? Optional.present(link) : Optional.empty()
    );
    const rootFolderLinks: RsSet<FolderLink> = includedLinks.mapOptional(
      (link) => (isFolder(link) ? Optional.present(link) : Optional.empty())
    );

    const linksToFilesInsideAFolder: RsSet<FileLink> = flattenWithUnion(
      rootFolderLinks.map((link) =>
        new RsSet(link.content).mapOptional((link) =>
          isFile(link) ? Optional.present(link) : Optional.empty()
        )
      )
    );
    const fileLinks: RsSet<FileLink> = rootFileLinks.union(
      linksToFilesInsideAFolder
    );

    setScanResultsOmittedCount(omittedCount);
    setScanResultsAvailableCount(fileLinks.size);
    setScanResultsTotalFileSize(sum(fileLinks.map((link) => link.size)));
    runInAction(() => {
      validationData.scanResultsTotalFileSize = sum(
        fileLinks.map((link) => link.size)
      );
    });
  };

  const fileStoreScan = () => {
    const url = `/nfsExport/ajax/createFullExportPlan?planId=${planId}`;
    setLoadingFullPlan(true);
    setCheckedFileSystems([]);
    axios
      .post<
        {|
          exportConfig: typeof exportConfig,
          exportSelection: typeof exportSelection,
          nfsConfig: typeof nfsConfig,
        |},
        FullExportPlan
      >(url, { exportConfig, exportSelection, nfsConfig })
      .then((response) => {
        calculateFullScanSummary(response.data);
        setCheckedFileSystems(response.data.foundFileSystems);
        setScanResultsPresent(true);
        runInAction(() => {
          validationData.scanResultsPresent = true;
          validationData.maxArchiveSizeBytes =
            response.data.maxArchiveSizeMBProp * 1024 * 1024;
          validationData.currentlyAllowedArchiveSizeBytes =
            response.data.currentlyAllowedArchiveSizeMB * 1024 * 1024;
        });
        console.log(response);
      })
      .catch((error) => {
        console.log(error);
      })
      .finally(() => {
        setLoadingFullPlan(false);
      });
  };

  useEffect(() => {
    fileStoreCheck();
  }, []);

  return (
    <Grid container spacing={1} direction="column">
      {/* whilst loading, showing loading animation */}
      {loadingQuickPlan && (
        <Grid item>
          <Card sx={{ p: 1 }}>
            <LoadingFade loading={true} />
          </Card>
        </Grid>
      )}

      {/* if the user intended for there to be some links to filestores in
       * their selection, but we didn't find any then a warning it shown.
       */}
      {!loadingQuickPlan && totalFilesFound === 0 && (
        <Grid item>
          <Card sx={{ p: 1 }}>
            <div>
              <h4>No filestore links found in exported content.</h4>
              <h4>
                If that&apos;s unexpected, you should check your export
                selection.
              </h4>
              <h4>Otherwise you can proceed with the export.</h4>
            </div>
          </Card>
        </Grid>
      )}

      {!loadingQuickPlan && totalFilesFound > 0 && (
        <>
          <Grid item>
            <FoundLinksListing
              filesCount={totalFilesFound}
              fileSystems={fileSystems}
            />
          </Grid>

          <Grid item>
            <LoginStatus
              fileSystems={fileSystems}
              fileStoreCheck={fileStoreCheck}
            />
          </Grid>

          <Grid item>
            <FileFilters
              maxFileSizeInMB={maxFileSizeInMB}
              setMaxFileSizeInMB={(max) => {
                setMaxFileSizeInMB(max);
                updateFilters("maxFileSizeInMB", max);
              }}
              excludedFileExtensions={excludedFileExtensions}
              setExcludedFileExtensions={(ext) => {
                setExcludedFileExtensions(ext);
                updateFilters("excludedFileExtensions", ext);
              }}
            />
          </Grid>

          <Grid item>
            <LinkAvailabilityScan
              scanResultsPresent={scanResultsPresent}
              scanResultsAvailableCount={scanResultsAvailableCount}
              scanResultsTotalFileSize={scanResultsTotalFileSize}
              scanResultsOmittedCount={scanResultsOmittedCount}
              fileStoreScan={fileStoreScan}
              loadingScanResults={loadingFullPlan}
              checkedFileSystems={checkedFileSystems}
            />
          </Grid>
        </>
      )}
    </Grid>
  );
}
