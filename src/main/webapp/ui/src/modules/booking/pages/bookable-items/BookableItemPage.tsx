import { useParams } from "@tanstack/react-router";
import { Suspense } from "react";
import { BookableItemDetailsLoader } from "./BookableItemDetailsLoader";
import { BookableItemSkeleton } from "./BookableItemSkeleton";
import { bookableItemTab } from "./LoadedBookableItemPage";

export default function BookableItemPage() {
  const { globalId, tab } = useParams({ from: "/booking/bookable-items/$globalId/{-$tab}" });
  return (
    <Suspense key={globalId} fallback={<BookableItemSkeleton />}>
      <BookableItemDetailsLoader globalId={globalId} tab={bookableItemTab(tab)} key={globalId} />
    </Suspense>
  );
}
