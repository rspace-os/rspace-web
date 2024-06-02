//@flow

import React, { useState, type Node, useEffect } from "react";
import FormControl from "@mui/material/FormControl";
import FormLabel from "@mui/material/FormLabel";
import TextField from "@mui/material/TextField";
import MenuItem from "@mui/material/MenuItem";
import Button from "@mui/material/Button";
import Grid from "@mui/material/Grid";
import Tooltip from "@mui/material/Tooltip";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import ListItemSecondaryAction from "@mui/material/ListItemSecondaryAction";
import ListItemText from "@mui/material/ListItemText";
import Avatar from "@mui/material/Avatar";
import DeleteIcon from "@mui/icons-material/Delete";
import IconButton from "@mui/material/IconButton";
import Account from "@mui/icons-material/AccountCircle";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import EmailValidator from "email-validator";
import FormHelperText from "@mui/material/FormHelperText";
import styled from "@emotion/styled";
import axios from "axios";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faUserPlus } from "@fortawesome/free-solid-svg-icons";
import { type UseState } from "../util/types";
import { type Person } from "./repositories/common";
import * as ArrayUtils from "../util/ArrayUtils";
library.add(faUserPlus);

const VALIDATIONS = {
  nameCheck: false,
  emailCheck: false,
  typeCheck: false,
};

function AdditionalUserDialog({
  onAddUser,
  onClose,
  open,
}: {|
  onAddUser: (Person) => void,
  onClose: () => void,
  open: boolean,
|}) {
  const [contactsName, setContactsName] = useState("");
  const [contactsType, setContactsType]: UseState<"" | "Author" | "Contact"> =
    useState("");
  const [contactsEmail, setContactsEmail] = useState("");

  /*
   * This state variable is set to true once the user has attempted to add a
   * new user to their repository export options, but has been prevented from
   * doing so due to an error.
   */
  const [addPersonDialogSubmitAttempt, setAddPersonDialogSubmitAttempt] =
    useState(false);

  const [validations, setValidations] = useState(VALIDATIONS);

  const handleClose = () => {
    setAddPersonDialogSubmitAttempt(false);
    setContactsName("");
    setContactsEmail("");
    setContactsType("");
    setValidations(VALIDATIONS);
    onClose();
  };

  const validateDetails = (details: Person) => {
    const newValidations = VALIDATIONS;

    if (/(.*[a-z]){3}/i.test(details.uniqueName))
      newValidations.nameCheck = true;
    if (EmailValidator.validate(details.email))
      newValidations.emailCheck = true;
    if (details.type !== "") newValidations.typeCheck = true;

    setValidations(newValidations);
    setAddPersonDialogSubmitAttempt(true);

    return Object.values(newValidations).every((b) => b);
  };

  const handleAddPerson = () => {
    const details = {
      uniqueName: contactsName,
      email: contactsEmail,
      type: contactsType,
    };
    if (validateDetails(details)) {
      onAddUser(details);
      handleClose();
    }
  };

  return (
    <Dialog
      onClose={handleClose}
      aria-labelledby="simple-dialog-title"
      open={open}
      maxWidth={"md"}
    >
      <DialogTitle>Add new person</DialogTitle>
      <DialogContent>
        <FormControl error aria-describedby="name-error-text" fullWidth>
          <TextField
            variant="standard"
            error={addPersonDialogSubmitAttempt && !validations.nameCheck}
            name="contactsName"
            label="Name *"
            value={contactsName}
            onChange={({ target: { value } }) => setContactsName(value)}
            margin="normal"
            data-test-id="user-name"
          />
        </FormControl>
        <FormControl error aria-describedby="email-error-text" fullWidth>
          <TextField
            variant="standard"
            error={addPersonDialogSubmitAttempt && !validations.emailCheck}
            name="contactsEmail"
            label="Email *"
            value={contactsEmail}
            onChange={({ target: { value } }) => setContactsEmail(value)}
            margin="normal"
            data-test-id="user-email"
          />
        </FormControl>
        <FormControl fullWidth error aria-describedby="type-error-text">
          <TextField
            variant="standard"
            error={addPersonDialogSubmitAttempt && !validations.typeCheck}
            name="contactsType"
            select
            label="Type *"
            value={contactsType}
            onChange={({ target: { value } }) => setContactsType(value)}
            margin="normal"
            data-test-id="user-type"
          >
            <MenuItem value="Author" data-test-id="user-type-author">
              Author
            </MenuItem>
            <MenuItem value="Contact" data-test-id="user-type-contact">
              Contact
            </MenuItem>
          </TextField>
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button
          onClick={handleClose}
          color="primary"
          data-test-id="button-cancel"
        >
          Cancel
        </Button>
        <Button
          onClick={handleAddPerson}
          color="primary"
          data-test-id="button-add"
        >
          Add
        </Button>
      </DialogActions>
    </Dialog>
  );
}

type ExportRepoUserArgs = {|
  submitAttempt: boolean,
  inputValidations: { author: boolean, contact: boolean, ... },
  initialPeople: Array<Person>,
  updatePeople: (Array<Person>) => void,
|};

/*
 * This component a widget for selecting another user who should be associated
 * with the export to a repository, either because they are an author of the
 * work or are some other noteworthy point of contact.
 */
export default function ExportRepoUser({
  /*
   * This prop is renamed to make it clear that it is distinct from
   * addPersonDialogSubmitAttempt. This is set to true once the user has
   * attempted to submit the whole export dialog but has been prevented from
   * doing so due to an error.
   */
  submitAttempt: exportDialogSubmitAttempt,

  inputValidations,
  initialPeople,
  updatePeople,
}: ExportRepoUserArgs): Node {
  const [open, setOpen] = useState(false);
  const [people, setPeople] = useState(initialPeople);

  const getCurrentUser = () => {
    void axios
      .get<{ fullName: string, email: string, ... }>("/directory/ajax/subject")
      .then((response) => {
        const user = response.data;
        const currentUserRoles = [
          { uniqueName: user.fullName, email: user.email, type: "Author" },
          { uniqueName: user.fullName, email: user.email, type: "Contact" },
        ];
        setPeople(currentUserRoles);
        updatePeople(currentUserRoles);
      });
  };

  useEffect(() => {
    if (initialPeople.length === 0) getCurrentUser();
  }, []);

  const handleDeletePerson = (index: number) => {
    const newPeople = ArrayUtils.splice(people, index, 1);
    setPeople(newPeople);
    updatePeople(newPeople);
  };

  return (
    <Grid container item>
      <Grid item xs={12} sx={{ mt: 2.5 }}>
        <AddUsers>
          <div className="child1">
            <FormLabel
              error={
                exportDialogSubmitAttempt &&
                (!inputValidations.author || !inputValidations.contact)
              }
              component="legend"
            >
              People
            </FormLabel>
            <FormHelperText
              error={
                exportDialogSubmitAttempt &&
                (!inputValidations.author || !inputValidations.contact)
              }
            >
              Add author(s) AND contact(s) to your repository export.
            </FormHelperText>
          </div>
          <Tooltip title="Add author/contact" className="child2">
            <IconButton onClick={() => setOpen(true)} data-test-id="add-user">
              <FontAwesomeIcon icon="user-plus" />
            </IconButton>
          </Tooltip>
        </AddUsers>
      </Grid>
      <AdditionalUserDialog
        open={open}
        onClose={() => setOpen(false)}
        onAddUser={(person) => {
          setPeople([...people, person]);
          updatePeople([...people, person]);
        }}
      />
      <Grid item xs={12}>
        <List>
          {people.map((option, index) => (
            <ListItem key={option.email + option.type}>
              <ListItemAvatar>
                <Avatar>
                  <Account />
                </Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={`${option.uniqueName} - ${option.type}`}
                secondary={option.email}
              />
              <ListItemSecondaryAction>
                <IconButton
                  aria-label="Delete"
                  onClick={() => handleDeletePerson(index)}
                  data-test-id="button-delete-user"
                >
                  <DeleteIcon />
                </IconButton>
              </ListItemSecondaryAction>
            </ListItem>
          ))}
        </List>
      </Grid>
    </Grid>
  );
}

const AddUsers = styled.div`
  display: flex;
  .child1 {
    flex-grow: 1;
  }
  .child2 {
    flex-grow: 0;
    width: auto;
  }
`;
