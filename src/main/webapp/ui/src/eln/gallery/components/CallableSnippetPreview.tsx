import React from "react";
import { GalleryFile } from "../useGalleryListing";

const SnippetPreviewContext = React.createContext((_file: GalleryFile) => {});

export function useSnippetPreview(): {
  openSnippetPreview: (file: GalleryFile) => void;
} {
  const openSnippetPreview = React.useContext(SnippetPreviewContext);
  return {
    openSnippetPreview,
  };
}

export function CallableSnippetPreview({
  children,
}: {
  children: React.ReactNode;
}): React.ReactNode {
  return (
    <SnippetPreviewContext.Provider
      value={(file) => {
        alert(file.name);
      }}
    >
      {children}
    </SnippetPreviewContext.Provider>
  );
}
