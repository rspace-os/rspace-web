import MoreHorizIcon from "@mui/icons-material/MoreHoriz";
import PhotoIcon from "@mui/icons-material/Photo";
import Box from "@mui/material/Box";
import CardMedia from "@mui/material/CardMedia";
import IconButton from "@mui/material/IconButton";
import Portal from "@mui/material/Portal";
import { useTheme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useState } from "react";
import TimeAgoCustom from "@/components/TimeAgoCustom";
import CustomTooltip from "../../../components/CustomTooltip";
import DescriptionList from "../../../components/DescriptionList";
import ImagePreview from "../../../components/ImagePreview";
import RecordTypeIcon from "../../../components/RecordTypeIcon";
import StyledMenu from "../../../components/StyledMenu";
import UserDetails from "../../../components/UserDetails";
import NavigateContext from "../../../stores/contexts/Navigate";
import SearchContext from "../../../stores/contexts/Search";
import { hasRequiredPermissions, type InventoryRecord } from "../../../stores/definitions/InventoryRecord";
import ContainerModel from "../../../stores/models/ContainerModel";
import SampleModel from "../../../stores/models/SampleModel";
import SubSampleModel from "../../../stores/models/SubSampleModel";
import useStores from "../../../stores/use-stores";
import { UserCancelledAction } from "../../../util/error";
import { menuIDs } from "../../../util/menuIDs";
import type { BlobUrl } from "../../../util/types";
import { preventEventBubbling } from "../../../util/Util";
import ContentsChips from "../../Container/Content/ContentsChips";
import contextActions from "../ContextMenu/ContextActions";
import { RecordLink } from "../RecordLink";
import CardStructure from "./CardStructure";

const REQUIRED_PERMISSIONS_TOOLTIP = "You do not have permission to select this item.";

type CardArgs = {
  record: InventoryRecord;
};

type ContentItem = React.ComponentProps<typeof DescriptionList>["content"][number];

function RecordCard({ record }: CardArgs): React.ReactNode {
  const { search, disabled, isChild, scopedResult, differentSearchForSettingActiveResult } = useContext(SearchContext);
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
    if (!scopedResult) throw new Error("A scoped record has not been assigned to this search");
    const params = searchStore.fetcher.generateNewQuery({
      parentGlobalId: scopedResult.globalId,
    });
    navigate(`/inventory/search?${params.toString()}`, {
      skipToParentContext: true,
    });
    activateResult(r);
  };

  const [link, setLink] = useState<BlobUrl | null>(null);
  const [size, setSize] = useState<{ width: number; height: number } | null>(null);

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
  const hasPermission = hasRequiredPermissions(record.permittedActions, search.uiConfig?.requiredPermissions);
  const cardIsGreyedOut = isFilteredOut || !hasPermission;
  const filteredOutReason = isFilteredOut ? search.uiConfig?.alwaysFilteredOutReason : undefined;
  const tooltipText = filteredOutReason ?? (!hasPermission ? REQUIRED_PERMISSIONS_TOOLTIP : undefined);

  const menuItems = contextActions({
    selectedResults: [record],
    menuID: menuIDs.CARD,
    closeMenu: () => setAnchorEl(null),
    forceDisabled: search.processingContextActions ? "Action in Progress" : "",
    basketSearch: search.fetcher.basketSearch,
  })("menuitem");

  const navigateOnClick = !disabled && !anchorEl && !cardIsGreyedOut && Boolean(isChild);
  const greyOut = cardIsGreyedOut && !tooltipText;

  const fullAccess = record.readAccessLevel === "full";
  const notPublic = record.readAccessLevel !== "public";
  const contentItems: Array<ContentItem> = [];
  if (fullAccess && record instanceof ContainerModel) {
    contentItems.push({
      label: "Contents",
      value: <ContentsChips record={record} />,
    });
  }
  if (fullAccess && record instanceof SampleModel) {
    contentItems.push({ label: "Total Quantity", value: record.quantityLabel });
  }
  if (fullAccess && record instanceof SubSampleModel) {
    contentItems.push({ label: "Quantity", value: record.quantityLabel });
  }
  if (notPublic && record instanceof SubSampleModel) {
    contentItems.push({
      label: "Sample",
      value: <RecordLink record={record.sample} overflow />,
      reducedPadding: true,
    });
  }
  if (record.owner) {
    contentItems.push({
      label: "Owner",
      value: <UserDetails userId={record.owner.id} fullName={record.owner.fullName} position={["bottom", "right"]} />,
      reducedPadding: true,
    });
  }
  if (
    notPublic &&
    (record instanceof SubSampleModel || record instanceof ContainerModel) &&
    record.immediateParentContainer
  ) {
    contentItems.push({
      label: "Location",
      value: (
        <Box sx={{ mt: 0.5 }}>
          <RecordLink record={record.immediateParentContainer} overflow />
        </Box>
      ),
      below: true,
    });
  }

  const card = (
    <CardStructure
      sx={{
        transition: "filter .2s ease-in-out, opacity .2s ease-in-out",
        cursor: navigateOnClick ? "pointer" : "default",
        height: "100%",
        ...(greyOut ? { filter: "grayscale(1)", pointerEvents: "none", opacity: 0.6 } : {}),
      }}
      deleted={record.deleted}
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
        )
      }
      headerAvatar={<RecordTypeIcon record={record} color={record.deleted ? theme.palette.deletedGrey : undefined} />}
      title={record.name}
      subheader={record.cardTypeLabel}
      headerAction={
        <>
          <IconButton
            sx={{ p: 1 }}
            onClick={preventEventBubbling((e: React.MouseEvent<HTMLButtonElement>) => setAnchorEl(e.currentTarget))}
          >
            <MoreHorizIcon fontSize="small" />
          </IconButton>
          <StyledMenu
            anchorEl={anchorEl}
            open={Boolean(anchorEl)}
            onClose={() => setAnchorEl(null)}
            disableAutoFocusItem={true}
          >
            {menuItems.filter(({ hidden }) => !hidden).map(({ component }) => component)}
          </StyledMenu>
        </>
      }
      content={<DescriptionList content={contentItems} />}
      contentFooter={
        record.readAccessLevel === "full" ? (
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
        ) : null
      }
      onClick={() => {
        if (disabled || anchorEl || cardIsGreyedOut) {
          return;
        }
        if (isChild ?? false) {
          navigateToResult(record);
        } else {
          activateResult(record);
        }
      }}
    />
  );

  return (
    <>
      {link && (
        <Portal>
          <ImagePreview closePreview={closePreview} link={link} size={size} setSize={setSize} />
        </Portal>
      )}
      {tooltipText ? (
        <CustomTooltip title={tooltipText}>
          <Box aria-disabled="true" sx={{ filter: "grayscale(1)", opacity: 0.6 }}>
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
