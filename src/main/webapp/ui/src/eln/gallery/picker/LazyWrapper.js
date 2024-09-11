//@flow

import React, { type Node } from "react";

const GalleryPicker = React.lazy(() => import("./index"));

const GalleryWrapper = ({
  setGalleryDialogOpen,
}: {|
  setGalleryDialogOpen: (boolean) => void,
|}): Node => {
  return (
    <React.Suspense fallback={<></>}>
      <GalleryPicker
        open={true}
        onClose={() => {
          setGalleryDialogOpen(false);
        }}
      />
    </React.Suspense>
  );
};

export default GalleryWrapper;
