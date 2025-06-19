import React, { lazy, Suspense } from "react";
import { observer } from "mobx-react-lite";
import Alert from "@mui/material/Alert";

const LoadedImageEditorDialog = lazy(() => import("../ImageEditingDialog"));

class ErrorBoundary extends React.Component<
  { children: React.ReactNode },
  { hasError: boolean }
> {
  constructor(props: { children: React.ReactNode }) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): { hasError: boolean } {
    return { hasError: true };
  }

  render(): React.ReactNode {
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

type DynamicallyLoadedImageEditorArgs = {
  editorFile: Blob | null;
  editorOpen: boolean;
  close: () => void;
  submitHandler: (file: Blob) => void;
  alt: string;
};

function DynamicallyLoadedImageEditor({
  editorFile,
  editorOpen,
  close,
  submitHandler,
  alt,
}: DynamicallyLoadedImageEditorArgs): React.ReactNode {
  return (
    <ErrorBoundary>
      <Suspense fallback="">
        <LoadedImageEditorDialog
          imageFile={editorFile}
          open={editorOpen}
          close={close}
          submitHandler={submitHandler}
          alt={alt}
        />
      </Suspense>
    </ErrorBoundary>
  );
}

export default observer(DynamicallyLoadedImageEditor);
