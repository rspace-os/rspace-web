import React, { useState, forwardRef, useContext } from "react";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import ContextMenuAction, {
  type ContextMenuRenderOptions,
} from "./ContextMenuAction";
import ContextDialog from "./ContextDialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContentText from "@mui/material/DialogContentText";
import FormControl from "@mui/material/FormControl";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner, faHandHolding } from "@fortawesome/free-solid-svg-icons";
import { library } from "@fortawesome/fontawesome-svg-core";
library.add(faSpinner, faHandHolding);
import Typography from "@mui/material/Typography";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import PeopleField from "../Inputs/PeopleField";
import RsSet from "../../../util/set";
import { match } from "../../../util/Util";
import SearchContext from "../../../stores/contexts/Search";
import { Observer } from "mobx-react-lite";
import PersonModel from "../../../stores/models/PersonModel";
import { type Username } from "../../../stores/definitions/Person";

const icon = (
  <span className="fa-layers fa-fw">
    <FontAwesomeIcon icon="hand-holding" size="sm" style={{ marginRight: 3 }} />
    <FontAwesomeIcon
      icon="hand-holding"
      size="sm"
      flip="both"
      style={{ marginLeft: 3 }}
    />
  </span>
);

const HelperText = () => (
  <DialogContentText component="span">
    <Typography variant="body1" paragraph>
      Select someone to transfer ownership to. By performing this action you
      will give the new owner full control over the item.{" "}
      <strong>
        This action can only be undone by the recipient or their PI.
      </strong>
    </Typography>
    <Typography variant="body1" paragraph>
      If the desired recipient cannot be found in this list, try searching for
      their name or username.
    </Typography>
  </DialogContentText>
);

type TransferActionArgs = {
  as: ContextMenuRenderOptions;
  selectedResults: Array<InventoryRecord>;
  disabled: string;
  closeMenu: () => void;
};

const TransferAction = forwardRef<
  React.ElementRef<typeof ContextMenuAction>,
  TransferActionArgs
>(({ as, selectedResults, disabled, closeMenu }, ref) => {
  const { search } = useContext(SearchContext);
  const [btnPos, setBtnPos] = useState<{ top: number; left: number } | null>(
    null
  );
  const [recipient, setRecipient] = useState<PersonModel | null>(null);

  const handleOpen = ({ currentTarget }: Event) => {
    if (currentTarget instanceof HTMLElement) {
      const gbcr = currentTarget.getBoundingClientRect();
      setBtnPos({
        top: gbcr.top + gbcr.height,
        left: gbcr.left,
      });
      setRecipient(null);
    }
  };

  const handleClose = () => {
    setBtnPos(null);
    closeMenu();
  };

  const onSubmitHandler = () => {
    if (recipient)
      void search.transferRecords(recipient.username, selectedResults);
    handleClose();
  };

  const ownersOfSelectedResults = new RsSet<Username>(
    selectedResults
      .filter(({ owner }) => Boolean(owner))
      .map((r) => r.owner!.username)
  );
  const excludedFromSelection =
    ownersOfSelectedResults.size === 1
      ? ownersOfSelectedResults
      : new RsSet<Username>();

  const disabledHelp = match<void, string>([
    [() => disabled !== "", disabled],
    [
      () => selectedResults.some((s) => s.recordType === "subSample"),
      "Only whole samples can be transferred, not individual subsamples.",
    ],
    [
      () => selectedResults.some((r) => !r.canTransfer),
      `You do not have permission to transfer ${
        selectedResults.length > 1 ? "these items" : "this item"
      }.`,
    ],
    [() => true, ""],
  ])();
  return (
    <Observer>
      {() => (
        <ContextMenuAction
          onClick={handleOpen}
          icon={icon}
          label="Transfer"
          as={as}
          ref={ref}
          disabledHelp={disabledHelp}
        >
          <ContextDialog open={Boolean(btnPos)} onClose={handleClose}>
            <DialogTitle>Transfer Ownership</DialogTitle>
            <DialogContent>
              <HelperText />
              <FormControl component="fieldset" fullWidth>
                <PeopleField
                  onSelection={(person) => setRecipient(person as PersonModel)}
                  label="Recipient"
                  recipient={recipient}
                  excludedUsernames={excludedFromSelection}
                />
              </FormControl>
            </DialogContent>
            <DialogActions>
              <Button onClick={handleClose} disabled={false}>
                Cancel
              </Button>
              <SubmitSpinner
                onClick={onSubmitHandler}
                disabled={recipient === null}
                loading={false}
                label="Transfer"
              />
            </DialogActions>
          </ContextDialog>
        </ContextMenuAction>
      )}
    </Observer>
  );
});

TransferAction.displayName = "TransferAction";
export default TransferAction;
