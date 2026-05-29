import React from "react";
import Box from "@mui/material/Box";
import { useTheme } from "@mui/material/styles";
import Tooltip from "@mui/material/Tooltip";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import NotInterestedIcon from "@mui/icons-material/NotInterested";
import PadlockIcon from "../../assets/graphics/PadlockIcon";
import PeopleIcon from "../../assets/graphics/PeopleIcon";
import { observer } from "mobx-react-lite";
import { type Record } from "../../stores/definitions/Record";

type InfoBadgeArgs = {
  inline?: boolean;
  children: React.ReactNode;
  record: Record;
};

function InfoBadge({
  inline = false,
  children,
  record,
}: InfoBadgeArgs): React.ReactNode {
  const { readAccessLevel, deleted } = record;
  const theme = useTheme();

  return (
    <Tooltip
      onClick={(e) => e.stopPropagation()}
      disableFocusListener
      enterDelay={300}
      enterTouchDelay={300}
      leaveTouchDelay={1200}
      title={children}
      slotProps={{ tooltip: { sx: { maxWidth: "calc(100vw - 16px)", p: 0 } } }}
    >
      <Box
        component="span"
        sx={{
          color: "white",
          backgroundColor: deleted
            ? theme.palette.deletedGrey
            : theme.palette.primary.dark,
          borderRadius: 10,
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
          height: "fit-content",
          ...(inline
            ? {
                mb: "-5px",
                mr: "5px",
              }
            : {
                position: "absolute",
                top: 0,
                right: -10,
              }),
          "& .MuiSvgIcon-root": {
            height: 20,
            width: 20,
            borderRadius: 10,
          },
        }}
      >
        {readAccessLevel === "public" ? (
          <NotInterestedIcon />
        ) : readAccessLevel === "limited" ? (
          <PadlockIcon color="white" />
        ) : record.owner && !record.currentUserIsOwner ? (
          <PeopleIcon />
        ) : (
          <InfoOutlinedIcon />
        )}
      </Box>
    </Tooltip>
  );
}

export default observer(InfoBadge);
