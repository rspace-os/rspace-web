import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
import type React from "react";
import { useContext, useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import { useLandmark } from "@/components/LandmarksContext";
import VisuallyHiddenHeading from "@/components/VisuallyHiddenHeading";
import NavigateContext from "@/stores/contexts/Navigate";
import ApiService from "../../common/InvApiService";
import Main from "../Main";
import RequestDetailPanel from "./RequestDetailPanel";
import RequestsList, { type ApiSampleRequestListItem } from "./RequestsList";

/**
 * Lets a user see all of the requests they have made, and (as an owner)
 * all of the requests made against their own samples.
 */
export default function RequestsPage(): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  const mainContentRef = useLandmark(t("requestsManagement.landmark"));
  const [selectedRequest, setSelectedRequest] = useState<ApiSampleRequestListItem | null>(null);
  const { useLocation } = useContext(NavigateContext);
  const location = useLocation();

  // Supports deep-linking to a specific request (e.g. from the status chip in the
  // Sample form's "Request this sample" box) via a `requestId` query param.
  useEffect(() => {
    const requestId = new URLSearchParams(location.search).get("requestId");
    if (!requestId) return;
    let cancelled = false;
    ApiService.get<ApiSampleRequestListItem>("sampleRequests", requestId)
      .then(({ data }) => {
        if (cancelled) return;
        setSelectedRequest(data);
      })
      .catch((error: unknown) => {
        console.error("Failed to load the linked sample request", error);
      });
    return () => {
      cancelled = true;
    };
  }, [location.search]);

  return (
    <Main ref={mainContentRef} role="main" aria-label={t("requestsManagement.landmark")}>
      <VisuallyHiddenHeading variant="h2">{t("requestsManagement.pageTitle")}</VisuallyHiddenHeading>
      <Box sx={{ display: "flex", height: "100%" }}>
        <Box
          sx={{
            display: "flex",
            flex: "1 1 40%",
            minWidth: 0,
            borderRight: `1px solid ${theme.palette.divider}`,
          }}
        >
          <RequestsList selectedRequestId={selectedRequest?.id ?? null} onSelect={setSelectedRequest} />
        </Box>
        <Box sx={{ display: "flex", flex: "1 1 60%", minWidth: 0 }}>
          <RequestDetailPanel key={selectedRequest?.id ?? "none"} request={selectedRequest} />
        </Box>
      </Box>
    </Main>
  );
}
