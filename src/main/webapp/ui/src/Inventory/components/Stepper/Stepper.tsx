import type Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { type ReactNode, useContext, useEffect } from "react";
import { clampToLines, mergeSx } from "@/modules/common/utils/styles";
import { HeadingContext } from "../../../components/DynamicHeadingLevel";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import NavigateContext from "../../../stores/contexts/Navigate";
import type { Factory } from "../../../stores/definitions/Factory";
import { generateUrlFromCoreFetcherArgs } from "../../../stores/models/Fetcher/CoreFetcher";
import useStores from "../../../stores/use-stores";
import type { URL } from "../../../util/types";
import CommonEditActions from "../CommonEditActions";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";
import MoreInfoSidebar from "../MoreInfoSidebar";
import Toolbar from "../Toolbar/Toolbar";
import StepperActions from "./StepperActions";

type StepperArgs = {
  // The title string to be displayed in the top left corner of the form.
  titleText: string;

  // Beside the title is an optional help link, shown as a question mark icon.
  helpLink?: {
    link: URL;
    title: string;
  };

  // An optional alert that is pinned to the top as the user scrolls, just
  // below the contextMenu.
  stickyAlert?: React.ReactElement<typeof Alert> | null;

  // A list of StepperPanels, or components that return StepperPanels
  children: ReactNode;

  // Whenever this value changes, the scroll position is reset to 0.
  resetScrollPosition: unknown;

  factory?: Factory;
};

/*
 * A wrapper component for forms that appear on the right side of the main
 * Inventory UI. It provides various header and pinned elements at the top, and
 * scroll controls at the bottom; allowing the user to page through the various
 * panels that constitute the form.
 */
function _Stepper({
  titleText,
  helpLink,
  stickyAlert,
  children,
  resetScrollPosition,
  factory,
}: StepperArgs): ReactNode {
  const {
    searchStore: { activeResult },
  } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  if (!activeResult) throw new Error("ActiveResult must be a Record");

  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const { state } = activeResult ?? {};

  /*
   * The header holding the Title, Global Id link, and record type label is pinned to the top so
   * the user can always see what they are viewing/editing; in a single-column layout the title is
   * clamped so that long record names cannot cover most of the screen. Long names with no spaces
   * break mid-word, in either layout, rather than overflowing the header.
   */
  const Title = (
    <Stack direction="row" spacing={1}>
      <Typography
        variant="h5"
        component="h2"
        sx={mergeSx(
          { mr: 0.75, minWidth: 0, color: "unset !important", overflowWrap: "anywhere" },
          isSingleColumnLayout ? clampToLines(2) : undefined,
        )}
      >
        {titleText}
      </Typography>
      {helpLink && <HelpLinkIcon link={helpLink.link} title={helpLink.title} size="small" color="white" />}
    </Stack>
  );

  const FooterActions = observer(() => (
    <>
      {state === "create" && (
        <StepperActions
          onSubmit={() => {
            void (async () => {
              await activeResult.create();
              navigate(generateUrlFromCoreFetcherArgs(activeResult.showNewlyCreatedRecordSearchParams), {
                modifyVisiblePanel: false,
              });
            })();
          }}
        />
      )}
      {state === "edit" && <CommonEditActions editableObject={activeResult} />}
    </>
  ));

  // Scroll to top when the record or its state changed
  useEffect(() => {
    window.scrollTo(0, 0);
  }, [resetScrollPosition, state]);

  return (
    <>
      <Toolbar title={Title} record={activeResult} recordType={activeResult.recordType} stickyAlert={stickyAlert} />
      <Box sx={{ position: "relative", minHeight: 0, overflowY: "auto" }}>
        <HeadingContext level={3}>{children}</HeadingContext>
        <FooterActions />
        {state === "preview" && <MoreInfoSidebar factory={factory || null} />}
      </Box>
    </>
  );
}

const Stepper = observer(_Stepper);
export default Stepper;
