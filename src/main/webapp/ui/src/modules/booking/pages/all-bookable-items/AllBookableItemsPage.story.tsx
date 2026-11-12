import type { RouterHistory } from "@tanstack/react-router";
import { BookableItemPageStory } from "../bookable-items/BookableItemPage.story";

export function AllBookableItemsStory({ history }: { history?: RouterHistory } = {}) {
  return <BookableItemPageStory initialPath="/booking/config/bookable-items" history={history} />;
}
