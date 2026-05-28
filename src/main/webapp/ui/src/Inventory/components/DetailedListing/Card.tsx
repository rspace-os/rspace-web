import React, { useContext, useState } from "react";
import { observer } from "mobx-react-lite";
import { type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import { useTheme } from "@mui/material/styles";
import Box from "@mui/material/Box";
import CardMedia from "@mui/material/CardMedia";
import IconButton from "@mui/material/IconButton";
import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import { preventEventBubbling } from "../../../util/Util";
import DescriptionList from "../../../components/DescriptionList";
import { RecordLink } from "../RecordLink";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import TimeAgoCustom from "@/components/TimeAgoCustom";
import contextActions from "../ContextMenu/ContextActions";
import { menuIDs } from "../../../util/menuIDs";
import CardStructure from "./CardStructure";
import PhotoIcon from "@mui/icons-material/Photo";
import ContentsChips from "../../Container/Content/ContentsChips";
import SearchContext from "../../../stores/contexts/Search";
import useStores from "../../../stores/use-stores";
import ImagePreview from "../../../components/ImagePreview";
import { StyledMenu } from "../../../components/StyledMenu";
import Portal from "@mui/material/Portal";
import NavigateContext from "../../../stores/contexts/Navigate";
import { UserCancelledAction } from "../../../util/error";
import UserDetails from "../../../components/UserDetails";
import ContainerModel from "../../../stores/models/ContainerModel";
import SampleModel from "../../../stores/models/SampleModel";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import { type BlobUrl } from "../../../util/types";
import CustomTooltip from "../../../components/CustomTooltip";
import { hasRequiredPermissions } from "../../../stores/definitions/InventoryRecord";

const REQUIRED_PERMISSIONS_TOOLTIP =
  "You do not have permission to select this item.";

function CustomCardStructure({
  navigateOnClick,
  greyOut,
  ...rest
}: React.ComponentProps<typeof CardStructure> & {
  navigateOnClick: boolean;
  greyOut: boolean;
}): React.ReactNode {
  return (
    <CardStructure
      sx={{
        transition: "filter .2s ease-in-out, opacity .2s ease-in-out",
        cursor: navigateOnClick ? "pointer" : "default",
        height: "100%",
        ...(greyOut ? { filter: "grayscale(1)", pointerEvents: "none", opacity: 0.6 } : {}),
      }}
      {...rest}
    />
  );
}

function Details({ record }: { record: InventoryRecord }): React.ReactNode {
  return (
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
                  <Box sx={{ mt: 0.5 }}>
                    <RecordLink
                      record={record.immediateParentContainer}
                      overflow
                    />
                  </Box>
                ),
                below: true,
              },
            ]
          : []),
      ]}
    />
  );
}

function Modified({ record }: { record: InventoryRecord }): React.ReactNode {
  return (
    <Box
      sx={{
        m: (theme) => theme.spacing(0, 1, 1, 1),
        color: "text.secondary",
        fontSize: "0.8em",
      }}
    >
      <span>Modified </span>
      <TimeAgoCustom
        time={record.lastModified}
        formatter={(value, unit, suffix) => `${value}${unit[0]} ${suffix}`}
      />
      <span> by </span>
      {record.modifiedByFullName}
    </Box>
  );
}

function ImagePlaceholder(): React.ReactNode {
  return (
    <CardMedia
      sx={{
        display: "flex",
        backgroundColor: (theme) => theme.palette.primary.saturated,
        opacity: "0.3",
        height: "100%",
      }}
    >
      <PhotoIcon
        sx={{
          color: "white",
          margin: "auto auto",
          fontSize: "5em",
        }}
      />
    </CardMedia>
  );
}

type CardArgs = {
  record: InventoryRecord;
};

function RecordCard({ record }: CardArgs): React.ReactNode {
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

  const [link, setLink] = useState<BlobUrl | null>(null);
  const [size, setSize] = useState<{ width: number; height: number } | null>(
    null,
  );

  const [fetching, setFetching] = useState(false);

  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  const openPreview = () => {
    setFetching(true);
    void record.fetchImage("image").then(() => {
      if (record.image) {
        setLink(record.image);
      }
      setFetching(false);
    });
  };

  const closePreview = () => {
    setLink(null);
  };

  const isFilteredOut = search.alwaysFilterOut(record);
  const hasPermission = hasRequiredPermissions(
    record.permittedActions,
    search.uiConfig?.requiredPermissions,
  );
  const cardIsGreyedOut = isFilteredOut || !hasPermission;
  const filteredOutReason = isFilteredOut
    ? search.uiConfig?.alwaysFilteredOutReason
    : undefined;
  const tooltipText =
    filteredOutReason ??
    (!hasPermission ? REQUIRED_PERMISSIONS_TOOLTIP : undefined);

  const menuItems = contextActions({
    selectedResults: [record],
    menuID: menuIDs.CARD,
    closeMenu: () => setAnchorEl(null),
    forceDisabled: search.processingContextActions ? "Action in Progress" : "",
    basketSearch: search.fetcher.basketSearch,
  })("menuitem");

  const card = (
    <CustomCardStructure
      deleted={record.deleted}
      greyOut={cardIsGreyedOut && !tooltipText}
      image={
        record.thumbnail ? (
          <CardMedia
            sx={{
              height: "100%",
              cursor: fetching ? "progress" : "zoom-in",
              backgroundSize: "contain",
            }}
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
          color={record.deleted ? theme.palette.deletedGrey : undefined}
        />
      }
      title={record.name}
      subheader={record.cardTypeLabel}
      headerAction={
        <>
          <IconButton
            sx={{ p: 1 }}
            onClick={preventEventBubbling(
              (e: React.MouseEvent<HTMLButtonElement>) => {
                const { currentTarget } = e;
                if (currentTarget instanceof HTMLElement) {
                  setAnchorEl(currentTarget);
                }
              },
            )}
          >
            <MoreHorizIcon fontSize="small" />
          </IconButton>
          <StyledMenu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={() => setAnchorEl(null)}
            disableAutoFocusItem={true}
          >
            {menuItems
              .filter(({ hidden }) => !hidden)
              .map(
                ({
                  component,
                }: {
                  hidden: boolean;
                  component: React.ReactNode;
                }) => component,
              )}
          </StyledMenu>
        </>
      }
      content={<Details record={record} />}
      contentFooter={
        record.readAccessLevel === "full" ? <Modified record={record} /> : null
      }
      onClick={() => {
        if (disabled || Boolean(anchorEl) || cardIsGreyedOut) {
          return;
        }
        if (isChild ?? false) {
          navigateToResult(record);
        } else {
          activateResult(record);
        }
      }}
      navigateOnClick={!disabled && !anchorEl && !cardIsGreyedOut && Boolean(isChild)}
    />
  );

  return (
    <>
      {link && (
        <Portal>
          <ImagePreview
            closePreview={closePreview}
            link={link}
            size={size}
            setSize={setSize}
          />
        </Portal>
      )}
      {tooltipText ? (
        <CustomTooltip title={tooltipText}>
          <Box
            aria-disabled="true"
            sx={{ filter: "grayscale(1)", opacity: 0.6 }}
          >
            <Box sx={{ pointerEvents: "none" }}>{card}</Box>
          </Box>
        </CustomTooltip>
      ) : (
        card
      )}
    </>
  );
}

export default observer(RecordCard);
