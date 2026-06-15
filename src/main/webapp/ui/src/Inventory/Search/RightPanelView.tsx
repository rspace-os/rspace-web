import Box from "@mui/material/Box";
import type { Theme } from "@mui/material/styles";
import { observer } from "mobx-react-lite";
import React, { type ReactNode } from "react";
import ErrorBoundary from "../../components/ErrorBoundary";
import { useLandmark } from "../../components/LandmarksContext";
import LoadingCircular from "../../components/LoadingCircular";
import type { RecordType } from "../../stores/definitions/InventoryRecord";
import useStores from "../../stores/use-stores";
import { UserCancelledAction } from "../../util/error";
import ContainerBatchForm from "../Container/BatchForm";
import ContainerForm from "../Container/Form";
import ContainerNewRecordForm from "../Container/NewRecordForm";
import { useIsSingleColumnLayout } from "../components/Layout/Layout2x1";
import SynchroniseFormSections from "../components/Stepper/SynchroniseFormSections";
import MixedBatchForm from "../Mixed/BatchForm";
import SampleBatchForm from "../Sample/BatchForm";
import SampleForm from "../Sample/Form";
import SampleNewRecordForm from "../Sample/NewRecordForm";
import SubSampleBatchForm from "../Subsample/BatchForm";
import SubSampleForm from "../Subsample/Form";
import TemplateForm from "../Template/Form";
import TemplateNewRecordForm from "../Template/NewRecordForm";
import InstrumentForm from "../Instrument/Form";
import InstrumentTemplateForm from "../InstrumentTemplate/Form";
import NoActiveResultPlaceholder from "./components/NoActiveResultPlaceholder";
import PermalinkNotFound from "./PermalinkNotFound";

const border = (theme: Theme, isMobile: boolean, recordType: RecordType | "mixed" | null): string => {
  const width = 4 + (isMobile ? 2 : 0);
  const color =
    recordType && recordType !== "mixed" ? theme.palette.record[recordType].bg : theme.palette.background.default;
  return `${width}px solid ${color}`;
};

const BorderContainer = React.forwardRef<
  HTMLDivElement,
  {
    recordType: RecordType | "mixed" | null;
    children: ReactNode;
    role?: string;
    "aria-label"?: string;
    "data-testid"?: string;
  }
>(({ recordType, children, ...props }, ref) => {
  const isSingleColumnLayout = useIsSingleColumnLayout();

  return (
    <Box
      {...props}
      ref={ref}
      sx={(theme) => ({
        backgroundColor: theme.palette.background.alt,
        position: "relative",
        height: "100%",
        display: "flex",
        flexDirection: "column",
        borderLeft: border(theme, isSingleColumnLayout, recordType),
        ...(isSingleColumnLayout && {
          borderRight: border(theme, true, recordType),
          borderTopRightRadius: theme.spacing(1),
          borderTopLeftRadius: theme.spacing(1),
        }),
        ...(!isSingleColumnLayout && {
          background:
            recordType && recordType !== "mixed"
              ? `linear-gradient(${theme.palette.record[recordType].lighter} 30%, #fff 31%)`
              : "initial",
        }),
      })}
    >
      {children}
    </Box>
  );
});
BorderContainer.displayName = "BorderContainer";

function RightPanelView(): ReactNode {
  const { searchStore, uiStore } = useStores();
  const isSingleColumnLayout = useIsSingleColumnLayout();
  const mainContentRef = useLandmark("Details Panel");

  React.useEffect(() => {
    void (async () => {
      if (!searchStore.activeResult && searchStore.search.filteredResults.length && !isSingleColumnLayout) {
        try {
          await searchStore.search.setActiveResult();
          uiStore.setVisiblePanel("right");
        } catch (e) {
          if (e instanceof UserCancelledAction) return;
          throw e;
        }
      }
    })();
  }, [searchStore.search.filteredResults]);

  const noActiveResult = () => {
    const search = searchStore.search;

    // check if the reason for there being no single active result is because of batch editing
    if (search.loadingBatchEditing) return <LoadingCircular message="Loading batch editing" />;
    if (search.batchEditingRecordsByType?.type === "container")
      return <ContainerBatchForm records={search.batchEditingRecordsByType.records} />;
    if (search.batchEditingRecordsByType?.type === "sample")
      return <SampleBatchForm records={search.batchEditingRecordsByType.records} />;
    if (search.batchEditingRecordsByType?.type === "subSample")
      return <SubSampleBatchForm records={search.batchEditingRecordsByType.records} />;
    if (search.batchEditingRecordsByType?.type === "mixed")
      return <MixedBatchForm records={search.batchEditingRecordsByType.records} />;

    // a permalink (possibly versioned) pointed at something that doesn't exist
    if (search.fetcher.permalinkNotFound) return <PermalinkNotFound permalink={search.fetcher.permalinkNotFound} />;

    // if we're not loading then show placeholder
    if (!search.fetcher.loading) return <NoActiveResultPlaceholder />;

    // otherwise show empty screen whilst loading
    return null;
  };

  const form = () => {
    const activeResult = searchStore.activeResult;
    if (!activeResult) return noActiveResult();

    // show nothing if record has not yet been full loaded
    if (activeResult.id && activeResult.noFullDetails) return null;

    if (activeResult.recordType === "sample") {
      if (activeResult.id) return <SampleForm />;
      return <SampleNewRecordForm />;
    }

    if (activeResult.recordType === "subSample") {
      if (activeResult.id) return <SubSampleForm />;
      throw new Error("Creating new subsamples is not supported.");
    }

    if (activeResult.recordType === "container") {
      if (activeResult.id) return <ContainerForm />;
      return <ContainerNewRecordForm />;
    }

    if (activeResult.recordType === "sampleTemplate") {
      if (activeResult.id) return <TemplateForm />;
      return <TemplateNewRecordForm />;
    }

    if (activeResult.recordType === "instrument") {
      if (activeResult.id) return <InstrumentForm />;
      throw new Error("Creating new instruments is not supported.");
    }

    if (activeResult.recordType === "instrumentTemplate") {
      if (activeResult.id) return <InstrumentTemplateForm />;
      throw new Error("Creating new instrument templates is not supported.");
    }

    throw Error("The active item's type is not valid.");
  };

  return (
    <ErrorBoundary>
      <BorderContainer
        data-testid="MainActiveResult"
        ref={mainContentRef as React.RefObject<HTMLDivElement>}
        recordType={searchStore.activeResult?.recordType ?? searchStore.search.batchEditingRecordsByType?.type ?? null}
      >
        <SynchroniseFormSections>{form()}</SynchroniseFormSections>
      </BorderContainer>
    </ErrorBoundary>
  );
}

export default observer(RightPanelView);
