"use strict";
import React from "react";
import Box from "@mui/material/Box";

const createGroupStep4 = (props) => {
  const styles = {
    container: {
      padding: "0 25px 10px 25px",
    },
    error: {
      color: "#f44336",
    },
  };
  const selfService = $("#selfServiceLabGroup").length != 0;
  const projectGroup = $("#projectGroup").length != 0;
  return (
    <Box sx={styles.container}>
      <h3>Summary</h3>
      <p data-test-id="createGroupSummaryGroupName">
        <strong>Group name:</strong>{" "}
        {props.summary.groupName === "" ? (
          <Box component="span" sx={styles.error}>
            You require a group name
          </Box>
        ) : (
          props.summary.groupName
        )}
      </p>
      <p data-test-id="createGroupSummaryPI">
        {projectGroup && <strong>Group Owner:</strong>}
        {!projectGroup && <strong>PI:</strong>}{" "}
        {props.summary.selectPI.selectedUser === "" ? (
          <Box component="span" sx={styles.error}>
            You must select a PI
          </Box>
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
