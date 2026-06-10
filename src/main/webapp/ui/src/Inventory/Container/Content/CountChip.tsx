import React from "react";
import Chip from "@mui/material/Chip";
import Box from "@mui/material/Box";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import { type Container } from "@/stores/definitions/Container";
import { ContentSummary } from "@/stores/definitions/container/types";

type CountChipArgs = {
  type: string;
  record: Container;
};

function getCount(type: string, cs: ContentSummary): number {
  if (type === "container") return cs.containerCount;
  if (type === "subSample") return cs.subSampleCount;
  throw new TypeError(
    'The string "type" can only be "container" or "subSample"'
  );
}

const CountChip = ({ type, record }: CountChipArgs): React.ReactNode => {
  if (!record.contentSummary.isAccessible) return null;
  const count = getCount(type, record.contentSummary.value);

  return (
    <Chip
      sx={{ ml: 0.5 }}
      label={count}
      size="small"
      icon={
        <Box component="span" sx={(theme) => ({ ml: `${theme.spacing(1)} !important` })}>
          <RecordTypeIcon
            record={{
              recordTypeLabel: type.toUpperCase(),
              iconName: type === "container" ? "container" : "sample",
            }}
          />
        </Box>
      }
      variant="outlined"
    />
  );
};

export default CountChip;
