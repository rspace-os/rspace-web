import type { RouterHistory } from "@tanstack/react-router";
import { BookableItemPageStory } from "./BookableItemPage.story";

export function BookableItemsStory({ history }: { history?: RouterHistory } = {}) {
  return <BookableItemPageStory initialPath="/booking/config/bookable-items" history={history} />;
}
