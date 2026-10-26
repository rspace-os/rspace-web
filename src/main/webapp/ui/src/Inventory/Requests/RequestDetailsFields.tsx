import Box from "@mui/material/Box";
import type React from "react";
import { useTranslation } from "react-i18next";
import { HeadingContext } from "@/components/DynamicHeadingLevel";
import GlobalId from "@/components/GlobalId";
import NoValue from "@/components/NoValue";
import UserDetails from "@/components/UserDetails";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import { isoToLocale } from "@/util/Util";
import DetailField from "./DetailField";
import { type ApiSampleRequestListItem, userDisplayName } from "./RequestsList";

/**
 * The Requests detail panel's "Details" section content: the requester, submission date,
 * requested sample, and any note the requester added.
 */
export default function RequestDetailsFields({ request }: { request: ApiSampleRequestListItem }): React.ReactNode {
  const { t } = useTranslation(["inventory", "common"]);

  return (
    <HeadingContext level={4}>
      <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
        <DetailField label={t("requestsManagement.detail.fields.requester")}>
          {request.requester.id === null ? (
            userDisplayName(request.requester, t)
          ) : (
            <UserDetails
              userId={request.requester.id}
              fullName={userDisplayName(request.requester, t)}
              position={["bottom", "right"]}
            />
          )}
        </DetailField>
        <DetailField label={t("requestsManagement.detail.fields.submitted")}>
          {isoToLocale(request.created)}
        </DetailField>
        <DetailField label={t("requestsManagement.detail.fields.sampleRequested")}>
          <GlobalId record={new LinkableRecordFromGlobalId(request.sample.globalId)} onClick={() => {}} />
        </DetailField>
        <DetailField label={t("requestsManagement.detail.fields.additionalNotes")}>
          {request.note ? request.note : <NoValue label={t("requestsManagement.detail.fields.noNotes")} />}
        </DetailField>
      </Box>
    </HeadingContext>
  );
}
