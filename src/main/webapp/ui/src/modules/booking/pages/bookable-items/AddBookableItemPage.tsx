import { Suspense } from "react";
import { AddBookableItemContent } from "./AddBookableItemContent";
import { AddBookableItemSkeleton } from "./AddBookableItemSkeleton";

export default function AddBookableItemPage() {
  return (
    <Suspense fallback={<AddBookableItemSkeleton />}>
      <AddBookableItemContent />
    </Suspense>
  );
}
