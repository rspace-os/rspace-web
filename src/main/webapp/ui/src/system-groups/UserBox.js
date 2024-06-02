"use strict";
import React, { useEffect, type Node } from "react";
import styled from "@emotion/styled";
import IconButton from "@mui/material/IconButton";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Snackbar from "@mui/material/Snackbar";
import CloseIcon from "@mui/icons-material/Close";
import {
  faCaretLeft,
  faCaretRight,
  faAngleDoubleLeft,
} from "@fortawesome/free-solid-svg-icons";
library.add(faCaretLeft, faCaretRight, faAngleDoubleLeft);

import UserList from "./UserList";

const Wrapper = styled.div`
  padding: 10px;
  border-radius: 4px;
  display: flex;
  width: 100%;
  background-color: rgb(245, 245, 245) !important;
  .grow {
    flex-grow: 1;
  }
  select {
    min-height: 300px;
    border: 1px solid rgb(255, 255, 255);
  }
  a {
    color: inherit;
  }
  a:hover {
    color: #1465b7 !important;
    font-weight: 500;
  }
`;

const Actions = styled.div`
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0px 20px;

  button {
    width: 48px;
  }
`;

export default function main(props): Node {
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
        let idx = selectedLeft.findIndex((u) => u == username);
        if (idx == -1) {
          setSelectedLeft(selectedLeft.concat([username]));
        } else {
          setSelectedLeft(selectedLeft.filter((u) => u != username));
        }
      }
    } else {
      if (Array.isArray(username)) {
        setSelectedRight(selectedRight.filter((s) => !username.includes(s)));
      } else {
        let idx = selectedRight.findIndex((u) => u == username);
        if (idx == -1) {
          setSelectedRight(selectedRight.concat([username]));
        } else {
          setSelectedRight(selectedRight.filter((u) => u != username));
        }
      }
    }
  };

  function addUsers() {
    let selected = usersRight.concat(
      props.users.filter((u) => selectedLeft.includes(u.username))
    );
    if (props.maxSelected && props.maxSelected < selected.length) {
      setSnackbarMessage(
        <span>
          Please, select only <b>one</b> PI
        </span>
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
        .concat(usersLeft)
    );
    let selected = usersRight.filter(
      (u) => !selectedRight.includes(u.username)
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
    <Wrapper>
      <UserList
        users={usersLeft}
        selected={selectedLeft}
        onSelect={(username) => handleSelect(username, "left")}
        listTitle={props.labelLeft}
      />
      <Actions>
        <IconButton
          disabled={selectedLeft.length == 0}
          onClick={addUsers}
          data-test-id={`add-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon="caret-right" />
        </IconButton>
        <IconButton
          disabled={selectedRight.length == 0}
          onClick={removeUsers}
          data-test-id={`remove-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon="caret-left" />
        </IconButton>
        <IconButton
          disabled={usersRight.length == 0}
          onClick={resetColumns}
          data-test-id={`remove-all-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon="angle-double-left" />
        </IconButton>
      </Actions>
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
    </Wrapper>
  );
}
