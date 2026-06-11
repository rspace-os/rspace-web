"use strict";
import React, { useEffect } from "react";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Snackbar from "@mui/material/Snackbar";
import CloseIcon from "@mui/icons-material/Close";
import { faCaretLeft } from "@fortawesome/free-solid-svg-icons/faCaretLeft";
import { faCaretRight } from "@fortawesome/free-solid-svg-icons/faCaretRight";
import { faAngleDoubleLeft } from "@fortawesome/free-solid-svg-icons/faAngleDoubleLeft";

import UserList from "./UserList";

export default function main(props) {
  const [usersLeft, setUsersLeft] = React.useState([]);
  const [usersRight, setUsersRight] = React.useState([]);
  const [selectedLeft, setSelectedLeft] = React.useState([]);
  const [selectedRight, setSelectedRight] = React.useState([]);
  const [snackbar, setSnackbar] = React.useState(false);
  const [snackbarMessage, setSnackbarMessage] = React.useState(false);

  const handleSelect = (username, column) => {
    if (column == "left") {
      if (Array.isArray(username)) {
        setSelectedLeft(selectedLeft.filter((s) => !username.includes(s)));
      } else {
        const idx = selectedLeft.findIndex((u) => u == username);
        if (idx == -1) {
          setSelectedLeft(selectedLeft.concat([username]));
        } else {
          setSelectedLeft(selectedLeft.filter((u) => u != username));
        }
      }
    } else if (Array.isArray(username)) {
      setSelectedRight(selectedRight.filter((s) => !username.includes(s)));
    } else {
      const idx = selectedRight.findIndex((u) => u == username);
      if (idx == -1) {
        setSelectedRight(selectedRight.concat([username]));
      } else {
        setSelectedRight(selectedRight.filter((u) => u != username));
      }
    }
  };

  function addUsers() {
    const selected = usersRight.concat(
      props.users.filter((u) => selectedLeft.includes(u.username)),
    );
    if (props.maxSelected && props.maxSelected < selected.length) {
      setSnackbarMessage(
        <span>
          Please, select only <strong>one</strong> PI
        </span>,
      );
      setSnackbar(true);
    } else {
      setUsersRight(selected);
      props.updateSelected(selected);
      // remove from left column
      setUsersLeft(usersLeft.filter((u) => !selectedLeft.includes(u.username)));
    }
    setSelectedLeft([]);
  }

  function removeUsers() {
    setUsersLeft(
      props.users
        .filter((u) => selectedRight.includes(u.username))
        .concat(usersLeft),
    );
    const selected = usersRight.filter(
      (u) => !selectedRight.includes(u.username),
    );
    // remove from right column
    setUsersRight(selected);
    props.updateSelected(selected);
    setSelectedRight([]);
  }

  function resetColumns() {
    setSelectedRight([]);
    setUsersRight([]);
    setUsersLeft(props.users);
    props.updateSelected([]);
  }

  useEffect(() => {
    if (usersLeft.length + usersRight.length == 0) {
      setUsersLeft(props.users);
    }
  }, [props.users]);

  return (
    <Box
      sx={{
        padding: "10px",
        borderRadius: "4px",
        display: "flex",
        width: "100%",
        backgroundColor: "rgb(245, 245, 245) !important",
      }}
    >
      <UserList
        users={usersLeft}
        selected={selectedLeft}
        onSelect={(username) => handleSelect(username, "left")}
        listTitle={props.labelLeft}
      />
      <Box
        sx={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          padding: "0px 20px",
        }}
      >
        <IconButton
          sx={{ width: 48 }}
          disabled={selectedLeft.length == 0}
          onClick={addUsers}
          data-test-id={`add-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon={faCaretRight} />
        </IconButton>
        <IconButton
          sx={{ width: 48 }}
          disabled={selectedRight.length == 0}
          onClick={removeUsers}
          data-test-id={`remove-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon={faCaretLeft} />
        </IconButton>
        <IconButton
          sx={{ width: 48 }}
          disabled={usersRight.length == 0}
          onClick={resetColumns}
          data-test-id={`remove-all-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon={faAngleDoubleLeft} />
        </IconButton>
      </Box>
      <UserList
        users={usersRight}
        selected={selectedRight}
        onSelect={(username) => handleSelect(username, "right")}
        listTitle={props.labelRight}
      />
      <Snackbar
        anchorOrigin={{
          vertical: "bottom",
          horizontal: "left",
        }}
        open={snackbar}
        autoHideDuration={6000}
        onClose={() => setSnackbar(false)}
        ContentProps={{
          "aria-describedby": "message-id",
        }}
        message={<span id="message-id">{snackbarMessage}</span>}
        action={[
          <IconButton
            key="close"
            aria-label="close"
            color="inherit"
            onClick={() => setSnackbar(false)}
          >
            <CloseIcon />
          </IconButton>,
        ]}
      />
    </Box>
  );
}
