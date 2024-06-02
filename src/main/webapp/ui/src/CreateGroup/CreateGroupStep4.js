"use strict";
import React from "react";

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
    <div style={styles.container}>
      <h3>Summary</h3>
      <p data-test-id="createGroupSummaryGroupName">
        <strong>Group name:</strong>{" "}
        {props.summary.groupName === "" ? (
          <span style={styles.error}>You require a group name</span>
        ) : (
          props.summary.groupName
        )}
      </p>
      <p data-test-id="createGroupSummaryPI">
          {projectGroup && (<strong>Group Owner:</strong>)}
          {!projectGroup && (<strong>PI:</strong>)}
          {" "}
        {props.summary.selectPI.selectedUser === "" ? (
          <span style={styles.error}>You must select a PI</span>
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
    </div>
  );
};

export default createGroupStep4;
