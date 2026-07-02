import { faHandHolding } from "@fortawesome/free-solid-svg-icons/faHandHolding";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Button from "@mui/material/Button";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogContentText from "@mui/material/DialogContentText";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import Typography from "@mui/material/Typography";
import { Observer } from "mobx-react-lite";
import type React from "react";
import { forwardRef, useContext, useState } from "react";
import { useTranslation } from "react-i18next";
import TransRichText from "@/modules/common/i18n/TransRichText";
import SubmitSpinner from "../../../components/SubmitSpinnerButton";
import SearchContext from "../../../stores/contexts/Search";
import type { InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import type { Username } from "../../../stores/definitions/Person";
import type PersonModel from "../../../stores/models/PersonModel";
import RsSet from "../../../util/set";
import { match } from "../../../util/Util";
import PeopleField from "../Inputs/PeopleField";
import ContextDialog from "./ContextDialog";
import ContextMenuAction, { type ContextMenuRenderOptions } from "./ContextMenuAction";

const icon = (
  <span className="fa-layers fa-fw">
    <FontAwesomeIcon icon={faHandHolding} size="sm" style={{ marginRight: 8, width: 12 }} />
    <FontAwesomeIcon icon={faHandHolding} size="sm" flip="both" style={{ marginLeft: 8, width: 12 }} />
  </span>
);

type TransferActionArgs = {
  as: ContextMenuRenderOptions;
  selectedResults: Array<InventoryRecord>;
  disabled: string;
  closeMenu: () => void;
};

const TransferAction = forwardRef<React.ElementRef<typeof ContextMenuAction>, TransferActionArgs>(
  ({ as, selectedResults, disabled, closeMenu }, ref) => {
    const { search } = useContext(SearchContext);
    const { t } = useTranslation(["inventory", "common"]);
    const [btnPos, setBtnPos] = useState<{ top: number; left: number } | null>(null);
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
      if (recipient) void search.transferRecords(recipient.username, selectedResults);
      handleClose();
    };

    const ownersOfSelectedResults = new RsSet<Username>(
      selectedResults
        .filter(({ owner }) => Boolean(owner))
        .map((r) => r.owner?.username)
        .filter((username): username is string => username !== undefined),
    );
    const excludedFromSelection = ownersOfSelectedResults.size === 1 ? ownersOfSelectedResults : new RsSet<Username>();

    const disabledHelp = match<void, string>([
      [() => disabled !== "", disabled],
      [() => selectedResults.some((s) => s.recordType === "subSample"), t("contextMenu.transfer.disabled.subSamples")],
      [
        () => selectedResults.some((r) => !r.canTransfer),
        t("contextMenu.transfer.disabled.noPermission", { count: selectedResults.length }),
      ],
      [() => true, ""],
    ])();
    return (
      <Observer>
        {() => (
          <ContextMenuAction
            onClick={handleOpen}
            icon={icon}
            label={t("common:actions.transfer")}
            as={as}
            ref={ref}
            disabledHelp={disabledHelp}
          >
            <ContextDialog open={Boolean(btnPos)} onClose={handleClose}>
              <DialogTitle>{t("contextMenu.transfer.dialog.title")}</DialogTitle>
              <DialogContent>
                <DialogContentText component="span">
                  <Typography component="p" variant="body1" sx={{ mb: 2 }}>
                    <TransRichText i18nKey="inventory:contextMenu.transfer.dialog.body" />
                  </Typography>
                  <Typography component="p" variant="body1" sx={{ mb: 2 }}>
                    {t("contextMenu.transfer.dialog.recipientNotFound")}
                  </Typography>
                </DialogContentText>
                <FormControl component="fieldset" fullWidth>
                  <PeopleField
                    onSelection={(person) => setRecipient(person as PersonModel)}
                    label={t("contextMenu.transfer.dialog.recipientLabel")}
                    recipient={recipient}
                    excludedUsernames={excludedFromSelection}
                  />
                </FormControl>
              </DialogContent>
              <DialogActions>
                <Button onClick={handleClose} disabled={false}>
                  {t("common:actions.cancel")}
                </Button>
                <SubmitSpinner
                  onClick={onSubmitHandler}
                  disabled={recipient === null}
                  loading={false}
                  label={t("common:actions.transfer")}
                />
              </DialogActions>
            </ContextDialog>
          </ContextMenuAction>
        )}
      </Observer>
    );
  },
);

TransferAction.displayName = "TransferAction";
export default TransferAction;
