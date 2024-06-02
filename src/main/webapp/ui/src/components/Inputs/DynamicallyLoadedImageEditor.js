//@flow

import React, { lazy, Suspense, type ComponentType, type Node } from "react";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";

const LoadedImageEditorDialog = lazy(() => import("../ImageEditingDialog"));

class ErrorBoundary extends React.Component<
  { children: Node },
  { hasError: boolean }
> {
  constructor(props: { children: Node }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): {| hasError: boolean |} {
    return { hasError: true };
  }

  render(): Node {
    if (this.state.hasError) {
      return (
        <Alert severity="error">
          Could not load the image editor at this time. Please check your
          network connection and refresh.
        </Alert>
      );
    }
    return this.props.children;
  }
}

type DynamicallyLoadedImageEditorArgs = {|
  editorFile: ?Blob,
  editorOpen: boolean,
  close: () => void,
  submitHandler: (string) => void,
|};

function DynamicallyLoadedImageEditor({
  editorFile,
  editorOpen,
  close,
  submitHandler,
}: DynamicallyLoadedImageEditorArgs): Node {
  return (
    <ErrorBoundary>
      <Suspense fallback="">
        <LoadedImageEditorDialog
          imageFile={editorFile}
          open={editorOpen}
          close={close}
          submitHandler={submitHandler}
        />
      </Suspense>
    </ErrorBoundary>
  );
}

export default (observer(
  DynamicallyLoadedImageEditor
): ComponentType<DynamicallyLoadedImageEditorArgs>);
