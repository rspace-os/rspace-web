import React, { useContext } from "react";
import { observer } from "mobx-react-lite";
import Card from "@mui/material/Card";
import CardHeader from "@mui/material/CardHeader";
import CardActions from "@mui/material/CardActions";
import CardContent from "@mui/material/CardContent";
import RecordDetails from "./RecordDetails";
import Button from "@mui/material/Button";
import useStores from "../../stores/use-stores";
import { library } from "@fortawesome/fontawesome-svg-core";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faExternalLinkAlt } from "@fortawesome/free-solid-svg-icons";
library.add(faExternalLinkAlt);
import InventoryBaseRecord from "../../stores/models/InventoryBaseRecord";
import { type Record } from "../../stores/definitions/Record";
import { withStyles } from "Styles";
import ContainerModel from "../../stores/models/ContainerModel";
import { doNotAwait } from "../../util/Util";
import NavigateContext from "../../stores/contexts/Navigate";

const OpenButton = withStyles<
  React.ComponentProps<typeof Button> & { icon?: React.ReactNode },
  { root: string }
>(() => ({
  root: {
    cursor: "default",
  },
}))(({ icon, ...rest }) => (
  <Button color="primary" variant="text" disableElevation {...rest}>
    {icon}
    Open
  </Button>
));

type InfoCardArgs = {
  record: Record;
};

function InfoPopover({ record }: InfoCardArgs): React.ReactNode {
  const { moveStore, uiStore } = useStores();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const moveActions = (r: InventoryBaseRecord) => (
    <>
      {!r.isWorkbench && (
        <OpenButton
          href={r.permalinkURL || undefined}
          // @ts-expect-error for some reason comppnent="a" is not recognised
          component="a"
          target="_blank"
          icon={
            <FontAwesomeIcon
              icon="external-link-alt"
              style={{ marginRight: 10 }}
            />
          }
        />
      )}
      {r instanceof ContainerModel && (
        <Button
          color="primary"
          variant="contained"
          onClick={doNotAwait(async () => {
            await moveStore.search?.setActiveResult(r);
            moveStore.setActivePane("right");
          })}
          disableElevation
        >
          Set as Target
        </Button>
      )}
    </>
  );

  return (
    <Card
      onClick={(e) => {
        /*
         * This stop clicking on the info card from registering as a click on
         * the parent element
         */
        e.stopPropagation();
      }}
      onMouseDown={(e) => {
        /*
         * This stops image/grid locations from being selected by tapping on
         * the info card is the right spot
         */
        e.stopPropagation();
      }}
      style={{
        maxWidth: 500,
        /*
         * This stops the text content of the card from becoming selected when
         * the user taps and holds on the display to open the info popup on
         * touchscreens. It has the unfortunate side effect of making it
         * impossible to copy the contents of the info card on any device nor
         * to be able to right click on any of the links in the info card and
         * copy their link text.
         */
        userSelect: "none",
      }}
    >
      <CardHeader
        title={record.name}
        subheader={record.cardTypeLabel}
        style={{ paddingBottom: "4px" }}
      />
      <CardContent style={{ overflowY: "auto" }}>
        <RecordDetails record={record} hideName />
      </CardContent>
      {record instanceof InventoryBaseRecord && (
        <CardActions style={{ justifyContent: "flex-end" }}>
          {moveStore.isMoving ? (
            moveActions(record)
          ) : (
            <OpenButton
              icon={undefined}
              onClick={() => {
                navigate(record.permalinkURL || "");
                /*
                 * If we're already on the permalink page then navigate will do
                 * nothing and wont automatically call setVisiblePanel,
                 * therefore we should call it to be sure it runs and the
                 * record is shown on mobile
                 */
                uiStore.setVisiblePanel("right");
              }}
            />
          )}
        </CardActions>
      )}
    </Card>
  );
}

export default observer(InfoPopover);
