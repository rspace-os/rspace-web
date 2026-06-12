import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react-lite";
import type React from "react";
import type { Record } from "../../stores/definitions/Record";
import InfoBadge from "./InfoBadge";
import InfoCard from "./InfoCard";

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
