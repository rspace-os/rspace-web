// @flow

import React, {
  useContext,
  useState,
  type Node,
  type ComponentType,
} from "react";
import { observer } from "mobx-react-lite";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { useTheme } from "@mui/material/styles";
import { withStyles } from "Styles";
import { makeStyles } from "tss-react/mui";
import CardMedia from "@mui/material/CardMedia";
import IconButton from "@mui/material/IconButton";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import { preventEventBubbling, match } from "../../../util/Util";
import DescriptionList from "../../../components/DescriptionList";
import { RecordLink } from "../RecordLink";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import TimeAgoCustom from "../../../components/TimeAgoCustom";
import contextActions from "../ContextMenu/ContextActions";
import { menuIDs } from "../../../util/menuIDs";
import CardStructure from "./CardStructure";
import PhotoIcon from "@mui/icons-material/Photo";
import ContentsChips from "../../Container/Content/ContentsChips";
import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import { globalStyles, type Theme } from "../../../theme";
import clsx from "clsx";
import ImagePreview from "../../../components/ImagePreview";
import { StyledMenu } from "../../../components/StyledMenu";
import Portal from "@mui/material/Portal";
import NavigateContext from "../../../stores/contexts/Navigate";
import { UserCancelledAction } from "../../../util/error";
import UserDetails from "../UserDetails";
import ContainerModel from "../../../stores/models/ContainerModel";
import SampleModel from "../../../stores/models/SampleModel";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import { type BlobUrl } from "../../../util/types";

const CustomCardStructure = withStyles<
  {|
    navigateOnClick: boolean,
    greyOut: boolean,
    title: string,
    subheader: string,
    image: Node,
    content: Node,
    onClick: () => void,
    headerAvatar: Node,
    headerAction: Node,
    contentFooter: Node,
    deleted: boolean,
  |},
  { card: string }
>((theme, { navigateOnClick }) => ({
  card: {
    transition: theme.transitions.filterToggle,
    cursor: navigateOnClick ? "pointer" : "default",
    height: "100%",
  },
}))(({ classes, greyOut, navigateOnClick: _navigateOnClick, ...rest }) => {
  const { classes: globalClasses } = globalStyles();
  return (
    <CardStructure
      className={clsx(classes.card, greyOut && globalClasses.greyOut)}
      {...rest}
    />
  );
});

const Details = withStyles<{| record: InventoryRecord |}, { location: string }>(
  (theme: Theme) => ({
    location: {
      marginTop: theme.spacing(0.5),
    },
  })
)(({ record, classes }) => (
  <DescriptionList
    content={[
      ...(record.readAccessLevel === "full" && record instanceof ContainerModel
        ? [
            {
              label: "Contents",
              value: <ContentsChips record={record} />,
            },
          ]
        : []),
      ...(record.readAccessLevel === "full" && record instanceof SampleModel
        ? [
            {
              label: "Total Quantity",
              value: record.quantityLabel,
            },
          ]
        : []),
      ...(record.readAccessLevel === "full" && record instanceof SubSampleModel
        ? [
            {
              label: "Quantity",
              value: record.quantityLabel,
            },
          ]
        : []),
      ...(record.readAccessLevel !== "public" &&
      record instanceof SubSampleModel
        ? [
            {
              label: "Sample",
              value: <RecordLink record={record.sample} overflow />,
              reducedPadding: true,
            },
          ]
        : []),
      ...(record.owner
        ? [
            {
              label: "Owner",
              value: (
                <UserDetails
                  userId={record.owner.id}
                  fullName={record.owner.fullName}
                  position={["bottom", "right"]}
                />
              ),
              reducedPadding: true,
            },
          ]
        : []),
      ...(record.readAccessLevel !== "public" &&
      (record instanceof SubSampleModel || record instanceof ContainerModel) &&
      record.immediateParentContainer
        ? [
            {
              label: "Location",
              value: (
                <div className={classes.location}>
                  <RecordLink
                    record={record.immediateParentContainer}
                    overflow
                  />
                </div>
              ),
              below: true,
            },
          ]
        : []),
    ]}
  />
));

const Modified = withStyles<
  {| record: InventoryRecord |},
  { container: string }
>((theme) => ({
  container: {
    margin: theme.spacing(0, 1, 1, 1),
    color: theme.palette.text.secondary,
    fontSize: "0.8em",
  },
}))(({ record, classes }) => (
  <div className={classes.container}>
    <span>Modified </span>
    <TimeAgoCustom
      time={record.lastModified}
      formatter={(value, unit, suffix) => `${value}${unit[0]} ${suffix}`}
    />
    <span> by </span>
    {record.modifiedByFullName}
  </div>
));

const ImagePlaceholder = withStyles<{||}, { media: string, icon: string }>(
  (theme) => ({
    media: {
      display: "flex",
      backgroundColor: theme.palette.primary.saturated,
      opacity: "0.3",
      height: "100%",
    },
    icon: {
      color: "white",
      margin: "auto auto",
      fontSize: "5em",
    },
  })
)(({ classes }) => (
  <CardMedia className={classes.media}>
    <PhotoIcon className={classes.icon} />
  </CardMedia>
));

const useStyles = makeStyles()((theme) => ({
  menuButton: {
    padding: theme.spacing(1),
  },
  preview: {
    height: "100%",
    cursor: (fetching) => (fetching ? "progress" : "zoom-in"),
    backgroundSize: "contain",
  },
}));

type CardArgs = {|
  record: InventoryRecord,
|};

function RecordCard({ record }: CardArgs): Node {
  const {
    search,
    disabled,
    isChild,
    scopedResult,
    differentSearchForSettingActiveResult,
  } = useContext(SearchContext);
  const { searchStore, uiStore } = useStores();
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const theme = useTheme();

  const activateResult = (r: InventoryRecord) => {
    differentSearchForSettingActiveResult
      .setActiveResult(r)
      .then(() => {
        uiStore.setVisiblePanel("right");
      })
      .catch((e) => {
        if (e instanceof UserCancelledAction) return;
        throw e;
      });
  };

  const navigateToResult = (r: InventoryRecord) => {
    if (!scopedResult)
      throw new Error("A scoped record has not been assigned to this search");
    const params = searchStore.fetcher.generateNewQuery({
      parentGlobalId: scopedResult.globalId,
    });
    navigate(`/inventory/search?${params.toString()}`, {
      skipToParentContext: true,
    });
    activateResult(r);
  };

  const [link, setLink] = useState<?BlobUrl>(null);
  const [size, setSize] = useState<?{| width: number, height: number |}>(null);

  const [fetching, setFetching] = useState(false);

  const [anchorEl, setAnchorEl] = useState<?HTMLElement>(null);

  const openPreview = async () => {
    setFetching(true);
    await record.fetchImage("image");
    if (record.image) {
      setLink(record.image);
    }
    setFetching(false);
  };

  const closePreview = () => {
    setLink(null);
  };

  const { classes } = useStyles(fetching);

  const actions = contextActions({
    selectedResults: [record],
    menuID: menuIDs.CARD,
    closeMenu: () => setAnchorEl(null),
    forceDisabled: search.processingContextActions ? "Action in Progress" : "",
    basketSearch: search.fetcher.basketSearch,
  });

  return (
    <>
      {link && (
        <Portal>
          <ImagePreview
            closePreview={closePreview}
            link={link}
            size={size}
            setSize={setSize}
            modal={true}
          />
        </Portal>
      )}
      <CustomCardStructure
        deleted={record.deleted}
        greyOut={search.alwaysFilterOut(record)}
        image={
          record.thumbnail ? (
            <CardMedia
              className={classes.preview}
              title={record.name}
              image={record.thumbnail}
              onClick={openPreview}
            />
          ) : (
            <ImagePlaceholder />
          )
        }
        headerAvatar={
          <RecordTypeIcon
            record={record}
            color={record.deleted ? theme.palette.deletedGrey : null}
          />
        }
        title={record.name}
        subheader={record.cardTypeLabel}
        headerAction={
          <>
            <IconButton
              className={classes.menuButton}
              onClick={preventEventBubbling(({ currentTarget }) => {
                if (currentTarget instanceof HTMLElement) {
                  setAnchorEl(currentTarget);
                }
              })}
            >
              <MoreHorizIcon size="small" />
            </IconButton>
            <StyledMenu
              anchorEl={anchorEl}
              open={Boolean(anchorEl)}
              onClose={() => setAnchorEl(null)}
              disableAutoFocusItem={true}
            >
              {actions("menuitem")
                .filter(({ hidden }) => !hidden)
                .map(({ component }) => component)}
            </StyledMenu>
          </>
        }
        content={<Details record={record} />}
        contentFooter={
          record.readAccessLevel === "full" && <Modified record={record} />
        }
        onClick={match<void, () => void>([
          [() => disabled || Boolean(anchorEl), () => {}],
          [() => isChild ?? false, () => navigateToResult(record)],
          [() => true, () => activateResult(record)],
        ])()}
        navigateOnClick={!disabled && !anchorEl && Boolean(isChild)}
      />
    </>
  );
}

export default (observer(RecordCard): ComponentType<CardArgs>);
