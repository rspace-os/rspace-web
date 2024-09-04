//@flow

import React, { type Node, type ComponentType } from "react";
import ErrorBoundary from "../../components/ErrorBoundary";
import useStores from "../../stores/use-stores";
import { observer } from "mobx-react-lite";
import SampleNewRecordForm from "../Sample/NewRecordForm";
import SampleForm from "../Sample/Form";
import SubSampleForm from "../Subsample/Form";
import ContainerForm from "../Container/Form";
import ContainerNewRecordForm from "../Container/NewRecordForm";
import TemplateForm from "../Template/Form";
import TemplateNewRecordForm from "../Template/NewRecordForm";
import { withStyles } from "Styles";
import clsx from "clsx";
import NoActiveResultPlaceholder from "./components/NoActiveResultPlaceholder";
import ContainerBatchForm from "../Container/BatchForm";
import SampleBatchForm from "../Sample/BatchForm";
import SubSampleBatchForm from "../Subsample/BatchForm";
import MixedBatchForm from "../Mixed/BatchForm";
import LoadingCircular from "../../components/LoadingCircular";
import { type RecordType } from "../../stores/definitions/InventoryRecord";
import { type Theme } from "../../theme";
import SynchroniseFormSections from "../components/Stepper/SynchroniseFormSections";
import { useIsSingleColumnLayout } from "../components/Layout/Layout2x1";

const border = (
  theme: Theme,
  isMobile: boolean,
  recordType: ?RecordType | "mixed"
): string => {
  const width = 4 + (isMobile ? 2 : 0);
  const color =
    recordType && recordType !== "mixed"
      ? theme.palette.record[recordType].bg
      : theme.palette.background.main;
  return `${width}px solid ${color}`;
};

const BorderContainer = withStyles<
  {| recordType: ?RecordType | "mixed", children: Node |},
  { root: string, notMobile: string, mobile: string }
>((theme, { recordType }) => ({
  root: {
    backgroundColor: theme.palette.background.alt,
    position: "relative",
    minHeight: "100vh",
  },
  notMobile: {
    borderLeft: border(theme, false, recordType),
    background: recordType
      ? `linear-gradient(${theme.palette.record[recordType].lighter} 30%, #fff 31%)`
      : "initial",
  },
  mobile: {
    borderLeft: border(theme, true, recordType),
    borderRight: border(theme, true, recordType),
    borderTopRightRadius: theme.spacing(1),
    borderTopLeftRadius: theme.spacing(1),
  },
}))(
  ({
    classes,
    children,
  }: {
    classes: { root: string, notMobile: string, mobile: string },
    children: Node,
  }) => {
    const isSingleColumnLayout = useIsSingleColumnLayout();
    return (
      <div
        className={clsx(
          classes.root,
          isSingleColumnLayout ? classes.mobile : classes.notMobile
        )}
        data-testid="MainActiveResult"
      >
        {children}
      </div>
    );
  }
);

function RightPanelView(): Node {
  const { searchStore } = useStores();

  const noActiveResult = () => {
    const search = searchStore.search;

    // check if the reason for there being no single active result is because of batch editing
    if (search.loadingBatchEditing)
      return <LoadingCircular message="Loading batch editing" />;
    if (search.batchEditingRecordsByType?.type === "container")
      return (
        <ContainerBatchForm
          records={search.batchEditingRecordsByType.records}
        />
      );
    if (search.batchEditingRecordsByType?.type === "sample")
      return (
        <SampleBatchForm records={search.batchEditingRecordsByType.records} />
      );
    if (search.batchEditingRecordsByType?.type === "subSample")
      return (
        <SubSampleBatchForm
          records={search.batchEditingRecordsByType.records}
        />
      );
    if (search.batchEditingRecordsByType?.type === "mixed")
      return (
        <MixedBatchForm records={search.batchEditingRecordsByType.records} />
      );

    // if we're not loading then show placeholder
    if (!search.fetcher.loading) return <NoActiveResultPlaceholder />;

    // otherwise show empty screen whilst loading
    return null;
  };

  const form = () => {
    const activeResult = searchStore.activeResult;
    if (!activeResult) return noActiveResult();

    // show nothing if record has not yet been full loaded
    if (Boolean(activeResult.id) && Boolean(activeResult.noFullDetails))
      return null;

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

    throw Error("The active item's type is not valid.");
  };

  return (
    <ErrorBoundary>
      <BorderContainer
        recordType={
          searchStore.activeResult?.recordType ??
          searchStore.search.batchEditingRecordsByType?.type
        }
      >
        <SynchroniseFormSections>{form()}</SynchroniseFormSections>
      </BorderContainer>
    </ErrorBoundary>
  );
}

export default (observer(RightPanelView): ComponentType<{||}>);
