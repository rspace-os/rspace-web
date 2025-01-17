//@flow

import * as FetchingData from "../../util/fetchingData";

type UiNavigationData = {||};

/**
 * This hook fetches the state required to display the conditional parts of
 * the AppBar.
 */
export default function useUiNavigationData(): FetchingData.Fetched<UiNavigationData> {
  return { tag: "loading" };
}
