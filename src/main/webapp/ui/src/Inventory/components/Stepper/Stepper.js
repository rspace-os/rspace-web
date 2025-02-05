// @flow

import { type URL } from "../../../util/types";
import HelpLinkIcon from "../../../components/HelpLinkIcon";
import RelativeBox from "../../../components/RelativeBox";
import useStores from "../../../stores/use-stores";
import { doNotAwait } from "../../../util/Util";
import CommonEditActions from "../CommonEditActions";
import MoreInfoSidebar from "../MoreInfoSidebar";
import Toolbar from "../Toolbar/Toolbar";
import StepperActions from "./StepperActions";
import { makeStyles } from "tss-react/mui";
import Alert from "@mui/material/Alert";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import React, {
  useEffect,
  useState,
  useContext,
  type Node,
  type ComponentType,
  type Element,
} from "react";
import { type Factory } from "../../../stores/definitions/Factory";
import clsx from "clsx";
import NavigateContext from "../../../stores/contexts/Navigate";
import { generateUrlFromCoreFetcherArgs } from "../../../stores/models/Fetcher/CoreFetcher";
import { HeadingContext } from "../../../components/DynamicHeadingLevel";
import { useIsSingleColumnLayout } from "../Layout/Layout2x1";

const useStyles = makeStyles()((theme) => ({
  relativeBox: {
    minHeight: 0,
    overflowY: "auto",
  },
  rightSpacing: {
    marginRight: theme.spacing(0.75),
  },
  truncatedTitle: {
    whiteSpace: "nowrap",
    overflow: "hidden",
    textOverflow: "ellipsis",
    fontSize: "20px",
  },
}));

const Wrapper = React.forwardRef<{| children: Node |}, mixed>(
  ({ children }: { children: Node }, ref) => <div ref={ref}>{children}</div>
);
Wrapper.displayName = "Wrapper";

type StepperArgs = {|
  // The title string to be displayed in the top left corner of the form.
  titleText: string,

  // Beside the title is an optional help link, shown as a question mark icon.
  helpLink?: {|
    link: URL,
    title: string,
  |},

  // An optional alert that is pinned to the top as the user scrolls, just
  // below the contextMenu.
  stickyAlert?: ?Element<typeof Alert>,

  // A list of StepperPanels, or components that return StepperPanels
  children: Node,

  // Whenever this value changes, the scroll position is reset to 0.
  resetScrollPosition: mixed,

  factory?: Factory,
|};

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
}: StepperArgs): Node {
  const {
    searchStore: { activeResult },
  } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  if (!activeResult) throw new Error("ActiveResult must be a Record");

  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();

  const { state } = activeResult ?? {};

  const { classes } = useStyles();

  /*
   * We pin the header that includes the Title, a Global Id link, and the
   * record type label at the top of the viewport so that they are always able
   * to quickly check what it is that they're viewing/editing. However, long
   * record names would end up covering half the screen on smaller viewports so
   * as the user begins to scroll we truncate the Title.
   *
   * This logic is designed in a way that allows the react-code to re-render
   * only once the thrsehold for truncating has been met, whilst having a
   * minimal impact on the scroll performance. For more information, see the
   * MDN article on the scroll event:
   * https://developer.mozilla.org/en-US/docs/Web/API/Document/scroll_event
   */

  const [needsTruncating, setNeedsTruncating] = useState<boolean>(false);

  let lastKnownScrollPosition = 0;
  let ticking = false;

  const handleScroll = (scrollPosition: number) => {
    if (isSingleColumnLayout) {
      setNeedsTruncating(scrollPosition > 50);
    }
  };

  const onScroll = () => {
    lastKnownScrollPosition = window.scrollY;
    if (!ticking) {
      window.requestAnimationFrame(() => {
        handleScroll(lastKnownScrollPosition);
        ticking = false;
      });
      ticking = true;
    }
  };
  React.useEffect(() => {
    window.addEventListener("scroll", onScroll);
    return () => {
      window.removeEventListener("scroll", onScroll);
    };
  }, []);

  const Title = (
    <>
      <Typography
        variant="h5"
        component="h2"
        className={clsx(
          classes.rightSpacing,
          needsTruncating && classes.truncatedTitle
        )}
      >
        {titleText}
      </Typography>
      {helpLink && (
        <HelpLinkIcon
          link={helpLink.link}
          title={helpLink.title}
          size="small"
          color="white"
        />
      )}
    </>
  );

  const FooterActions = observer(() => (
    <>
      {state === "create" && (
        <StepperActions
          onSubmit={doNotAwait(async () => {
            await activeResult.create();
            navigate(
              generateUrlFromCoreFetcherArgs(
                activeResult.showNewlyCreatedRecordSearchParams
              ),
              { modifyVisiblePanel: false }
            );
          })}
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
      <Toolbar
        title={Title}
        record={activeResult}
        recordType={activeResult.recordType}
        stickyAlert={stickyAlert}
      />
      <RelativeBox className={classes.relativeBox}>
        <HeadingContext level={3}>{children}</HeadingContext>
        <FooterActions />
        {state === "preview" && <MoreInfoSidebar factory={factory} />}
      </RelativeBox>
    </>
  );
}

const Stepper: ComponentType<StepperArgs> = observer(_Stepper);
export default Stepper;
