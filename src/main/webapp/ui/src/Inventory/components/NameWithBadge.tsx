import React from "react";
import Box from "@mui/material/Box";
import InfoBadge from "./InfoBadge";
import InfoCard from "./InfoCard";
import type { Record } from "../../stores/definitions/Record";
import { observer } from "mobx-react-lite";
import Stack from "@mui/material/Stack";

type NameWithBadgeArgs = {
  record: Record;
};

function NameWithBadge({ record }: NameWithBadgeArgs): React.ReactNode {
  return (
    <Stack
      direction="row"
      spacing={1}
      sx={{
        alignItems: "center",
      }}
    >
      <InfoBadge inline record={record}>
        <InfoCard record={record} />
      </InfoBadge>
      <Box
        component="span"
        sx={{
          textDecorationLine: record.deleted ? "line-through" : "none",
          wordBreak: "break-all",
        }}
      >
        {record.name}
      </Box>
    </Stack>
  );
}

export default observer(NameWithBadge);
