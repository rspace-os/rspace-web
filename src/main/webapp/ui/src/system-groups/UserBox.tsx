import { faAngleDoubleLeft } from "@fortawesome/free-solid-svg-icons/faAngleDoubleLeft";
import { faCaretLeft } from "@fortawesome/free-solid-svg-icons/faCaretLeft";
import { faCaretRight } from "@fortawesome/free-solid-svg-icons/faCaretRight";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import CloseIcon from "@mui/icons-material/Close";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Snackbar from "@mui/material/Snackbar";
import React, { useEffect } from "react";

import UserList from "./UserList";

// biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
export default function main(props: any) {
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [usersLeft, setUsersLeft] = React.useState<any[]>([]);
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [usersRight, setUsersRight] = React.useState<any[]>([]);
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [selectedLeft, setSelectedLeft] = React.useState<any[]>([]);
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [selectedRight, setSelectedRight] = React.useState<any[]>([]);
  const [snackbar, setSnackbar] = React.useState(false);
  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const [snackbarMessage, setSnackbarMessage] = React.useState<any>(false);

  // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
  const handleSelect = (username: any, column: any) => {
    // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
    if (column == "left") {
      if (Array.isArray(username)) {
        setSelectedLeft(selectedLeft.filter((s) => !username.includes(s)));
      } else {
        // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
        const idx = selectedLeft.findIndex((u) => u == username);
        // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
        if (idx == -1) {
          setSelectedLeft(selectedLeft.concat([username]));
        } else {
          // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
          setSelectedLeft(selectedLeft.filter((u) => u != username));
        }
      }
    } else if (Array.isArray(username)) {
      setSelectedRight(selectedRight.filter((s) => !username.includes(s)));
    } else {
      // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
      const idx = selectedRight.findIndex((u) => u == username);
      // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
      if (idx == -1) {
        setSelectedRight(selectedRight.concat([username]));
      } else {
        // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
        setSelectedRight(selectedRight.filter((u) => u != username));
      }
    }
  };

  function addUsers() {
    // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
    const selected = usersRight.concat(props.users.filter((u: any) => selectedLeft.includes(u.username)));
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
    // biome-ignore lint/suspicious/noExplicitAny: pragmatic jsx->tsx conversion
    setUsersLeft(props.users.filter((u: any) => selectedRight.includes(u.username)).concat(usersLeft));
    const selected = usersRight.filter((u) => !selectedRight.includes(u.username));
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
    // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
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
          // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
          disabled={selectedLeft.length == 0}
          onClick={addUsers}
          data-test-id={`add-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon={faCaretRight} />
        </IconButton>
        <IconButton
          sx={{ width: 48 }}
          // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
          disabled={selectedRight.length == 0}
          onClick={removeUsers}
          data-test-id={`remove-${props.labelRight.split(" ").join("-")}`}
        >
          <FontAwesomeIcon icon={faCaretLeft} />
        </IconButton>
        <IconButton
          sx={{ width: 48 }}
          // biome-ignore lint/suspicious/noDoubleEquals: initial biome migration
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
        slotProps={{
          content: {
            "aria-describedby": "message-id",
          },
        }}
        message={<span id="message-id">{snackbarMessage}</span>}
        action={[
          <IconButton key="close" aria-label="close" color="inherit" onClick={() => setSnackbar(false)}>
            <CloseIcon />
          </IconButton>,
        ]}
      />
    </Box>
  );
}
