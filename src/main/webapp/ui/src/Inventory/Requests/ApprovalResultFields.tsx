import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import { HeadingContext } from "@/components/DynamicHeadingLevel";
import GlobalId from "@/components/GlobalId";
import NoValue from "@/components/NoValue";
import LinkableRecordFromGlobalId from "@/stores/models/LinkableRecordFromGlobalId";
import DetailField from "./DetailField";
import RequestsStatusChip from "./RequestsStatusChip";

/**
 * The Requests detail panel's "Approval Result" section content: the request's current status,
 * and either the sample that was transferred (once fulfilled) or the approver's comment, e.g. a
 * rejection reason.
 */
export default function ApprovalResultFields({
  status,
  isSampleOwner,
  sampleOwnerName,
  comment,
  transferredSampleGlobalId,
}: {
  status: string;
  isSampleOwner: boolean;
  sampleOwnerName: string | null;
  comment: string | null | undefined;
  transferredSampleGlobalId: string;
}): React.ReactNode {
  const { t } = useTranslation("inventory");

  return (
    <HeadingContext level={4}>
      <Box sx={{ p: 2, display: "flex", flexDirection: "column", gap: 2 }}>
        <DetailField label={t("requestsManagement.detail.fields.status")}>
          <RequestsStatusChip status={status} />
          {status === "APPROVED" && sampleOwnerName && !isSampleOwner && (
            <Typography variant="body2" sx={{ mt: 1 }}>
              {t("requestsManagement.detail.fields.approvedMessage", { owner: sampleOwnerName })}
            </Typography>
          )}
        </DetailField>
        {status === "FULFILLED" ? (
          <DetailField label={t("requestsManagement.detail.fields.transferredSample")}>
            <GlobalId record={new LinkableRecordFromGlobalId(transferredSampleGlobalId)} onClick={() => {}} />
          </DetailField>
        ) : (
          <DetailField label={t("requestsManagement.detail.fields.commentFromApprover")}>
            {comment ? comment : <NoValue label={t("requestsManagement.detail.fields.noComment")} />}
          </DetailField>
        )}
      </Box>
    </HeadingContext>
  );
}
