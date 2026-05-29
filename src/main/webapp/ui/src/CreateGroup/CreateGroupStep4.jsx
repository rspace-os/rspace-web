"use strict";
import React from "react";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";

const createGroupStep4 = (props) => {
  const selfService = $("#selfServiceLabGroup").length != 0;
  const projectGroup = $("#projectGroup").length != 0;
  return (
    <Box sx={{ padding: "0 25px 10px 25px" }}>
      <h3>Summary</h3>
      <p data-test-id="createGroupSummaryGroupName">
        <strong>Group name:</strong>{" "}
        {props.summary.groupName === "" ? (
          <Typography variant="inherit" component="span" sx={{ color: "#f44336" }}>
            You require a group name
          </Typography>
        ) : (
          props.summary.groupName
        )}
      </p>
      <p data-test-id="createGroupSummaryPI">
        {projectGroup && <strong>Group Owner:</strong>}
        {!projectGroup && <strong>PI:</strong>}{" "}
        {props.summary.selectPI.selectedUser === "" ? (
          <Typography variant="inherit" component="span" sx={{ color: "#f44336" }}>
            You must select a PI
          </Typography>
        ) : (
          props.summary.selectPI.selectedUser
        )}
      </p>
      <p data-test-id="createGroupSummaryInvitedMem">
        <strong>Invited members:</strong>{" "}
        {props.summary.existingUsers.join(", ")}
      </p>
      {!selfService && (
        <p data-test-id="createGroupSummaryInvitedNonMem">
          <strong>Invited non-members:</strong>{" "}
          {props.summary.newUsers.join(", ")}
        </p>
      )}
    </Box>
  );
};

export default createGroupStep4;
