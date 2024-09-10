// @flow

import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import ContentImage, {
  type TappedLocationData,
} from "../Content/ImageView/PlaceMarkers/ContentImage";
import DialogContentText from "@mui/material/DialogContentText";
import Grid from "@mui/material/Grid";
import LocationsTable from "../Content/ImageView/PlaceMarkers/LocationsTable";
import React, {
  useEffect,
  createRef,
  useRef,
  type Node,
  type ComponentType,
  type ElementRef,
} from "react";
import SummaryCard from "../Content/ImageView/PlaceMarkers/SummaryCard";
import ViewAgendaOutlinedIcon from "@mui/icons-material/ViewAgendaOutlined";
import ViewHeadlineIcon from "@mui/icons-material/ViewHeadline";
import ImageOutlinedIcon from "@mui/icons-material/ImageOutlined";
import useStores from "../../../stores/use-stores";
import { makeStyles } from "tss-react/mui";
import { observer } from "mobx-react-lite";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Layout2x1Dialog from "../../components/Layout/Layout2x1Dialog";
import TitledBox from "../../components/TitledBox";
import { type UseState } from "../../../util/types";
import ContainerModel from "../../../stores/models/ContainerModel";
import { type Location } from "../../../stores/definitions/Container";
import { useIsSingleColumnLayout } from "../../components/Layout/Layout2x1";

export const COMPACT_VIEW = 0;
export const DETAILED_VIEW = 1;
export const IMAGE_VIEW = 2;
export const LOCATION_TAPPED_EVENT = "locationTapped";

const useStyles = makeStyles()(() => ({
  fullHeight: {
    height: "auto",
    alignSelf: "stretch",
  },
  divider: {
    height: "100%",
  },
  content: {
    padding: 0,
  },
  imagePane: {
    padding: "16px 24px",
  },
  infoPane: {
    padding: 16,
    backgroundColor: "#f5f5f5",
  },
  toggle: {
    backgroundColor: "#f5f5f5",
  },
  dialog: {
    userSelect: "none",
  },
  scrollPane: {
    height: "calc(100vh - 182px)",
    overflow: "auto",
  },
  cardContainer: {
    marginBottom: 10,
    flexWrap: "unset",
  },
}));

type LocationsImageMarkersDialogArgs = {|
  open: boolean,
  close: () => void,
|};

function LocationsImageMarkersDialog({
  open,
  close,
}: LocationsImageMarkersDialogArgs): Node {
  const { classes } = useStyles();
  const { searchStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const activeResult = searchStore.activeResult;
  if (!activeResult || !(activeResult instanceof ContainerModel))
    throw new Error("ActiveResult must be a Container");
  const [selected, setSelected]: UseState<?TappedLocationData> =
    React.useState(null);
  const [rightView, setRightView] = React.useState(
    isSingleColumnLayout ? IMAGE_VIEW : COMPACT_VIEW
  );
  const cardParent = useRef<?HTMLElement>();
  const tableParent = useRef<?HTMLElement>();

  const listener =
    (num: number, cardRef: ElementRef<typeof ListItem>) =>
    ({ detail: { number: tappedNum } }: { detail: { number: number } }) => {
      if (tappedNum === num && rightView === DETAILED_VIEW && cardRef.current) {
        cardRef.current.scrollIntoView({
          behavior: "smooth",
        });
      }
    };

  const noMarkersWarning = () =>
    !activeResult.locations.length ? (
      <Alert severity="warning">
        No marked locations yet; click on the image to add a location marker.
      </Alert>
    ) : null;

  const onLocationTap = (mark: TappedLocationData) => {
    setSelected(mark);
    if (isSingleColumnLayout && rightView === IMAGE_VIEW)
      setRightView(DETAILED_VIEW);
    const event = new CustomEvent(LOCATION_TAPPED_EVENT, {
      detail: { number: mark.number },
    });
    if (cardParent.current) {
      cardParent.current.dispatchEvent(event);
    }
    if (tableParent.current) {
      tableParent.current.dispatchEvent(event);
    }
  };

  const deleteLocation = ({ number }: { number: number, ... }) => {
    setSelected(null);
    activeResult.deleteSortedLocation(number - 1);
  };

  const Card = ({
    location,
    number,
  }: {
    location: Location,
    number: number,
  }) => {
    const cardRef = createRef<?HTMLElement>();
    const l = listener(number, cardRef);
    const mark = { location, number, point: { left: 0, top: 0 } }; // point is unused, but necessary for type

    useEffect(() => {
      const c = cardParent.current;
      if (c) {
        // $FlowExpectedError[incompatible-call] Invented new event type
        c.addEventListener(LOCATION_TAPPED_EVENT, l);
      }
      // $FlowExpectedError[incompatible-call] Invented new event type
      return () => c?.removeEventListener(LOCATION_TAPPED_EVENT, l);
    });

    return (
      <ListItem ref={cardRef} disableGutters>
        <SummaryCard
          location={location}
          number={number}
          editable={true}
          onRemove={() => deleteLocation(mark)}
          selected={selected?.number}
          onClick={() => onLocationTap(mark)}
          fullWidth
        />
      </ListItem>
    );
  };

  const colRight = () => (
    <TitledBox>
      <Grid container directon="column">
        <Grid item>
          <DialogContentText>
            Tap on the image to add a location marker.
            <br />
            Tap and hold on a marker, and then drag to adjust the marked
            location.
          </DialogContentText>
        </Grid>
        <Grid item xs={12}>
          <ContentImage
            editable
            onLocationTap={onLocationTap}
            onClearSelection={() => setSelected(null)}
            selected={selected}
          />
        </Grid>
      </Grid>
    </TitledBox>
  );

  return (
    <Layout2x1Dialog
      colLeft={
        <TitledBox>
          <Tabs
            value={rightView}
            onChange={(_, v) => setRightView(v)}
            indicatorColor="primary"
            textColor="primary"
          >
            <Tab
              icon={<ViewHeadlineIcon />}
              label="Compact"
              iconPosition="start"
              value={COMPACT_VIEW}
            />
            <Tab
              icon={<ViewAgendaOutlinedIcon />}
              label="Detailed"
              iconPosition="start"
              value={DETAILED_VIEW}
            />
            {isSingleColumnLayout && (
              <Tab
                icon={<ImageOutlinedIcon />}
                label="Image"
                iconPosition="start"
                value={IMAGE_VIEW}
              />
            )}
          </Tabs>
          {rightView === COMPACT_VIEW && (
            <Box ref={tableParent} pr={1}>
              <LocationsTable
                locations={activeResult.sortedLocations}
                onRemove={deleteLocation}
                isCompactView={rightView === COMPACT_VIEW}
                parentRef={tableParent}
                selected={selected?.number}
                onClick={onLocationTap}
              />
              {noMarkersWarning()}
            </Box>
          )}
          {rightView === DETAILED_VIEW && (
            <Box pr={1}>
              <List
                direction="column"
                ref={cardParent}
                className={classes.cardContainer}
              >
                {noMarkersWarning()}
                {activeResult.sortedLocations.map(
                  (location: Location, index: number) => (
                    <Card location={location} number={index + 1} key={index} />
                  )
                )}
              </List>
            </Box>
          )}
          {isSingleColumnLayout && rightView === IMAGE_VIEW && colRight()}
        </TitledBox>
      }
      colRight={colRight()}
      open={open}
      onClose={close}
      dialogTitle={"Edit Locations"}
      actions={
        <Button onClick={close} color="primary">
          Done
        </Button>
      }
    />
  );
}

export default (observer(
  LocationsImageMarkersDialog
): ComponentType<LocationsImageMarkersDialogArgs>);
