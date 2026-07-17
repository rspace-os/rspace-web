import Box from "@mui/material/Box";
import Chip from "@mui/material/Chip";
import type React from "react";
import { useTranslation } from "react-i18next";
import type { Container } from "@/stores/definitions/Container";
import type { ContentSummary } from "@/stores/definitions/container/types";
import RecordTypeIcon from "../../../components/RecordTypeIcon";

type CountChipArgs = {
  type: string;
  record: Container;
};

function getCount(type: string, cs: ContentSummary): number {
  if (type === "container") return cs.containerCount;
  if (type === "subSample") return cs.subSampleCount;
  if (type === "instrument") return cs.instrumentCount;
  throw new TypeError('The string "type" can only be "container", "subSample", or "instrument"');
}

const CountChip = ({ type, record }: CountChipArgs): React.ReactNode => {
  const { t } = useTranslation("inventory");
  if (!record.contentSummary.isAccessible) return null;
  const count = getCount(type, record.contentSummary.value);
  const recordTypeLabel =
    type === "container" ? t("recordTypes.container.singular") : t("recordTypes.subsample.singular");

  return (
    <Chip
      sx={{ ml: 0.5 }}
      label={count}
      size="small"
      icon={
        <Box component="span" sx={(theme) => ({ ml: `${theme.spacing(1)} !important` })}>
          <RecordTypeIcon
            record={{
              recordTypeLabel,
              iconName: type === "container" ? "container" : type === "instrument" ? "instrument" : "sample",
            }}
          />
        </Box>
      }
      variant="outlined"
    />
  );
};

export default CountChip;
