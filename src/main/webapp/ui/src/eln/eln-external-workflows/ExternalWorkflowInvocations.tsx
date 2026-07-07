import Badge from "@mui/material/Badge";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Fab from "@mui/material/Fab";
import Grid from "@mui/material/Grid";
import Modal from "@mui/material/Modal";
import { createSvgIcon } from "@mui/material/SvgIcon";
import { useEffect, useState } from "react";
import axios from "@/common/axios";
import ExternalWorkflowDialog from "@/eln/eln-external-workflows/ExternalWorkflowDialog";
import useLocalStorage from "../../hooks/browser/useLocalStorage";
import { ErrorReason } from "./Enums";
import ErrorView from "./ErrorView";
import type { GalaxyDataSummary } from "./GalaxyData";

export type ExternalWorkflowInvocationsArgs = {
  fieldId: string | null;
  isForNotebookPage: boolean;
};
export type RSpaceErrorResponse = {
  data: { exceptionMessage?: string; errorId?: string };
};
export type RSpaceError = { message: string; response: RSpaceErrorResponse };
export type InvocationsAndDataCount = {
  invocationCount: number;
  dataCount: number;
};
type GalaxyUsedEventDetail = {
  fieldId?: string;
};
const NO_DATA: InvocationsAndDataCount = { invocationCount: 0, dataCount: 0 };

function ExternalWorkflowInvocations({ isForNotebookPage = false, fieldId }: ExternalWorkflowInvocationsArgs) {
  const [errorReason, setErrorReason] = useState<(typeof ErrorReason)[keyof typeof ErrorReason]>(ErrorReason.None);
  const [errorMessage, setErrorMessage] = useState("");
  const [invocationsAndDataCount, setInvocationsAndDataCount] = useState<InvocationsAndDataCount>(NO_DATA);
  /**
   * This is not a cache for Galaxy data - it is a local flag which will prevent document fields repeatedly requesting
   * Galaxy updates when there is actually no data in Galaxy associated with that field. If data is ever found in Galaxy
   * for that field, the data exists query flag is set to false and the update Galaxy data flag is set to trye.
   *
   * Always attempt to fetch data from Galaxy once, to cover shared documents, user changing browser or user erasing local storage
   * setShouldCheckForGalaxyData and setButtonVisible will be reset to true if a user ever uploads data from this document field to Galaxy
   */
  const [shouldCheckForGalaxyData, setShouldCheckForGalaxyData] = useLocalStorage(
    `checkForGalaxyData_${fieldId}`,
    true,
  );
  const [galaxyDataFoundInRSpace, setGalaxyDataFoundInRSpace] = useLocalStorage(`galaxyDataFound_${fieldId}`, false);
  const [querying, setQuerying] = useState(false);
  const [buttonVisible, setButtonVisible] = useState(false);
  const [galaxyDataSummary, setGalaxyDataSummary] = useState<Array<GalaxyDataSummary>>([]);
  const [showDialog, setShowDialog] = useState(false);
  const BUTTON_TOP = isForNotebookPage ? 115 : 100;
  const BUTTON_RIGHT = isForNotebookPage ? -48 : -24;
  const BUTTON_BOTTOM = BUTTON_TOP - 48;

  async function checkForRSpaceGalaxyData() {
    await galaxyDataExistsInRSpace();
  }

  async function fetchDataFromGalaxy() {
    await updateGalaxyCountOnly();
  }

  useEffect(() => {
    if (shouldCheckForGalaxyData) {
      void checkForRSpaceGalaxyData();
    }
  }, [shouldCheckForGalaxyData]);

  useEffect(() => {
    if (galaxyDataFoundInRSpace) {
      void fetchDataFromGalaxy();
    }
  }, [galaxyDataFoundInRSpace]);

  useEffect(() => {
    const handleGalaxyUsed = (event: Event) => {
      const { detail } = event as CustomEvent<GalaxyUsedEventDetail>;
      const eventFieldId = detail?.fieldId;
      const normalizedFieldId = typeof eventFieldId === "string" ? eventFieldId.substring(4) : null;

      if (normalizedFieldId === fieldId) {
        setButtonVisible(true);
        // not strictly true yet but will be true when the page is refreshed etc
        setGalaxyDataFoundInRSpace(true);
        setShouldCheckForGalaxyData(false);
        // this is a workaround because saving the data is async we cant use
        // a simple check for the existence of the data
      }
    };

    window.addEventListener("galaxy-used", handleGalaxyUsed);
    return () => {
      window.removeEventListener("galaxy-used", handleGalaxyUsed);
    };
  }, [fieldId, setGalaxyDataFoundInRSpace, setShouldCheckForGalaxyData]);

  const galaxyDataExistsInRSpace = async () => {
    const data = await getGalaxyDataExists();
    setGalaxyDataFoundInRSpace(data);
    setShouldCheckForGalaxyData(false);
  };

  const updateGalaxyDataSummary = async () => {
    try {
      setQuerying(true);
      const data = await getGalaxyData();
      setQuerying(false);
      setGalaxyDataSummary(data);
      const numData = data.length;
      const numInvocations = data.filter((d) => d.galaxyInvocationName !== null).length;
      setInvocationsAndDataCount({
        invocationCount: numInvocations,
        dataCount: numData,
      });
    } catch (error) {
      handleRequestError(error as RSpaceError);
    }
  };

  const updateGalaxyCountOnly = async () => {
    try {
      setQuerying(true);
      const data = await getGalaxyDataCount();
      setQuerying(false);
      setInvocationsAndDataCount(data);
    } catch (error) {
      handleRequestError(error as RSpaceError);
    }
  };

  function handleRequestError(error: RSpaceError) {
    // error.message is generated by axios and contains user unfriendly response codes, therefore only use as a last resort
    let responseError = error.response.data.exceptionMessage ? error.response.data.exceptionMessage : error.message;
    responseError += error.response.data.errorId ? ` (errorId: ${error.response.data.errorId})` : "";
    setErrorMessage(responseError);
    if (error.message.slice(error.message.length - 3) === "408") {
      setErrorReason(ErrorReason.Timeout);
    } else if (error.message.slice(error.message.length - 3) === "404") {
      setErrorReason(ErrorReason.NotFound);
    } else if (error.message.slice(error.message.length - 3) === "403") {
      setErrorReason(ErrorReason.Unauthorized);
    } else if (error.message.slice(error.message.length - 3) === "400") {
      setErrorReason(ErrorReason.BadRequest);
    } else {
      setErrorReason(ErrorReason.UNKNOWN);
    }
  }

  const getGalaxyData = async (): Promise<Array<GalaxyDataSummary>> => {
    const { data } = await axios.get<Array<GalaxyDataSummary>>(
      `/apps/galaxy/getSummaryGalaxyDataForRSpaceField/${fieldId}`,
    );
    return data;
  };
  const getGalaxyDataExists = async (): Promise<boolean> => {
    const { data } = await axios.get<boolean>(`/apps/galaxy/galaxyDataExists/${fieldId}`);
    return data;
  };

  const getGalaxyDataCount = async (): Promise<InvocationsAndDataCount> => {
    const { data } = await axios.get<InvocationsAndDataCount>(
      `/apps/galaxy/getGalaxyInvocationCountForRSpaceField/${fieldId}`,
    );
    return data;
  };

  return (
    <>
      {errorReason !== ErrorReason.None && (
        <ErrorView errorReason={errorReason} errorMessage={errorMessage} WorkFlowIcon={WorkFlowIcon} />
      )}
      {(buttonVisible || invocationsAndDataCount.dataCount > 0) && (
        <>
          <Box
            sx={{
              position: "absolute",
              top: BUTTON_TOP,
              right: BUTTON_RIGHT,
              bottom: BUTTON_BOTTOM,
              pointerEvents: "none",
              "@media print": { display: "none" },
              zIndex: 2,
            }}
          >
            <Badge
              badgeContent={invocationsAndDataCount.invocationCount}
              color="primary"
              sx={{ position: "sticky", top: BUTTON_TOP, zIndex: 1, pointerEvents: "auto" }}
              slotProps={{ badge: { style: { transform: "none" } } }}
            >
              <Fab
                onClick={() => {
                  void updateGalaxyDataSummary().then(() => {
                    setShowDialog(true);
                  });
                }}
                color="primary"
                size="medium"
                aria-label="Show computational workflows associated with this field"
                aria-haspopup="menu"
                sx={{ zIndex: "initial" }}
              >
                <WorkFlowIcon width="100%" viewBox="0 0 225 225" enableBackground="new 0 0 225 225"></WorkFlowIcon>
              </Fab>
            </Badge>
          </Box>
          <Modal
            open={querying}
            aria-label="Please wait, querying galaxy is in progress"
            title={"Galaxy Query In Progress"}
          >
            <Grid
              container
              spacing={0}
              sx={{
                alignItems: "center",
                justifyContent: "center",
                flexDirection: "column",
                minHeight: "100vh",
              }}
            >
              <Grid size={3}>{querying && <CircularProgress variant="indeterminate" value={1} size="8rem" />}</Grid>
            </Grid>
          </Modal>
          <ExternalWorkflowDialog open={showDialog} setOpen={setShowDialog} galaxySummaryReport={galaxyDataSummary} />
        </>
      )}
      {querying && invocationsAndDataCount.dataCount === 0 && (
        <Box
          sx={{
            position: "absolute",
            top: BUTTON_TOP,
            right: BUTTON_RIGHT,
            bottom: BUTTON_BOTTOM,
            pointerEvents: "none",
            "@media print": { display: "none" },
            zIndex: 2,
          }}
        >
          <Badge
            badgeContent={invocationsAndDataCount.invocationCount}
            color="primary"
            sx={{ position: "sticky", top: BUTTON_TOP, zIndex: 1, pointerEvents: "auto" }}
            slotProps={{ badge: { style: { transform: "none" } } }}
          >
            <Fab
              disabled={true}
              size="medium"
              aria-label="Please wait, loading computational workflows associated with this field"
              aria-haspopup="menu"
              sx={{ zIndex: "initial" }}
            >
              <WorkFlowIcon width="100%" viewBox="0 0 225 225" enableBackground="new 0 0 225 225"></WorkFlowIcon>
            </Fab>
          </Badge>
        </Box>
      )}
    </>
  );
}

export const WorkFlowIcon = createSvgIcon(
  // biome-ignore lint/a11y/noSvgWithoutTitle: initial biome migration
  <svg id="Layer_1" xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 20 20">
    <g>
      <circle cx="5.055" cy="3.51" r="2.332" fill="#fff" />
      <circle cx="5.154" cy="10" r="2.332" fill="#fff" />
      <circle cx="5.154" cy="16.49" r="2.332" fill="#fff" />
    </g>
    <line x1="6.443" y1="10" x2="17.276" y2="10" fill="#fff" stroke="#fff" strokeMiterlimit="10" strokeWidth="1.4" />
    <path
      d="M4.932,3.535c3.136,0,5.677,2.894,5.677,6.465s-2.542,6.465-5.677,6.465"
      fill="none"
      stroke="#fff"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.4"
    />
    <polyline
      points="15.073 7.867 17.206 10 15.073 12.133"
      fill="none"
      stroke="#fff"
      strokeLinecap="round"
      strokeLinejoin="round"
      strokeWidth="1.4"
    />
  </svg>,
  "WorkFlow",
);

export default ExternalWorkflowInvocations;
