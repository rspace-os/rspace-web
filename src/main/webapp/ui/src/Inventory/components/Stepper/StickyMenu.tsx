import React from "react";
import { observer } from "mobx-react-lite";
import useStores from "../../../stores/use-stores";
import { withStyles } from "Styles";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import Box from "@mui/material/Box";
import ExtendedContextMenu from "../ContextMenu/ExtendedContextMenu";
import Alert from "@mui/material/Alert";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import theme, { RecordPalette } from "../../../theme";

const MenuWrapper = withStyles<
  {
    children: React.ReactNode;
    recordType: keyof typeof theme.palette.record;
    isSingleColumnLayout: boolean;
  },
  { root: string }
>((theme, { recordType, isSingleColumnLayout }) => ({
  root: {
    backgroundColor: (theme.palette.record[recordType] as RecordPalette).bg,
    borderBottomLeftRadius: isSingleColumnLayout ? 0 : theme.spacing(1),
    borderBottomRightRadius: 0,
    borderBottom: theme.borders.section,
    color: `${theme.palette.text.primary} !important`,
  },
}))(({ children, classes }) => <div className={classes.root}>{children}</div>);

const RoundedWhiteContainer = withStyles<
  { children: React.ReactNode; isSingleColumnLayout: boolean },
  { root: string }
>((theme, { isSingleColumnLayout }) => ({
  root: {
    borderTopLeftRadius: theme.spacing(1),
    borderTopRightRadius: isSingleColumnLayout ? theme.spacing(1) : 0,
    borderBottomLeftRadius: isSingleColumnLayout ? 0 : theme.spacing(0.75),
    backgroundColor: theme.palette.background.alt,
  },
}))(({ children, classes }) => <div className={classes.root}>{children}</div>);

type StickyMenuArgs = {
  stickyAlert?: React.ReactElement<typeof Alert> | null;
};

function StickyMenu({ stickyAlert }: StickyMenuArgs): React.ReactNode {
  const {
    searchStore: { activeResult, search },
    uiStore,
  } = useStores();
  if (!activeResult) throw new Error("ActiveResult must be a Record");
  const isSingleColumnLayout = useIsSingleColumnLayout();

  const prefixActions = [
    {
      key: "show-info",
      onClick: () => uiStore.toggleInfo(),
      icon: <InfoOutlinedIcon />,
      label: "Info",
      disabledHelp:
        activeResult.readAccessLevel !== "full"
          ? "You do not have permission to see more details about this item."
          : "",
      active: uiStore.infoVisible,
    },
  ];
  return (
    !["create", "edit"].includes(activeResult.state) && (
      <MenuWrapper
        recordType={activeResult.recordType}
        isSingleColumnLayout={isSingleColumnLayout}
      >
        <RoundedWhiteContainer isSingleColumnLayout={isSingleColumnLayout}>
          <Box ml={0.5} pt={0.5}>
            <ExtendedContextMenu
              menuID="stepper"
              prefixActions={prefixActions}
              selectedResults={[activeResult].filter(Boolean)}
              forceDisabled={
                search.processingContextActions ? "Action in Progress" : ""
              }
              basketSearch={search.fetcher.basketSearch}
            />
          </Box>
          {stickyAlert}
        </RoundedWhiteContainer>
      </MenuWrapper>
    )
  );
}

export default observer(StickyMenu);
