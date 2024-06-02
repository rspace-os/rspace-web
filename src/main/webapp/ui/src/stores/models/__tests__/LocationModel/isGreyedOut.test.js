/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import LocationModel from "../../LocationModel";
import { makeMockContainer } from "../ContainerModel/mocking";
import each from "jest-each";
import getRootStore from "../../../stores/RootStore";

describe("computed: isGreyedOut", () => {
  /*
   * This test asserts every permutation of state that could affect what the
   * computed property isGreyedOut returns. There are five boolean variables to
   * consider, that are not entirely mutually exclusive.
   *
   *  - inCreatingContext :: This determines if the LocationModel is being
   *    used as part of the rendering of a Create dialog's display of a visual
   *    or grid container's content.
   *
   *  - alwaysFilterOut :: This is an override that is set on Search that
   *    allows calling code to make particular records always be greyed out.
   *
   *  - performingSearch :: Whether the user is currently performing a search
   *    of the parent container.
   *
   *  - hasContent :: Whether the location has any content.
   *
   *  - inSearchResults :: Whether the location content, where there is
   *    content, is included in the results of the search being performed. As
   *    such, this only has a value when both performingSearch and hasContent
   *    are true.
   */
  each`
    inCreatingContext | alwaysFilterOut | performingSearch | hasContent | inSearchResults | isGreyedOut
    ${false}          | ${false}        | ${false}         | ${false}   | ${null}         | ${false}
    ${false}          | ${false}        | ${false}         | ${true}    | ${null}         | ${false}
    ${false}          | ${false}        | ${true}          | ${false}   | ${null}         | ${true}
    ${false}          | ${false}        | ${true}          | ${true}    | ${false}        | ${true}
    ${false}          | ${false}        | ${true}          | ${true}    | ${true}         | ${false}
    ${false}          | ${true}         | ${false}         | ${false}   | ${null}         | ${false}
    ${false}          | ${true}         | ${false}         | ${true}    | ${null}         | ${true}
    ${false}          | ${true}         | ${true}          | ${false}   | ${null}         | ${true}
    ${false}          | ${true}         | ${true}          | ${true}    | ${false}        | ${true}
    ${false}          | ${true}         | ${true}          | ${true}    | ${true}         | ${true}
    ${true}           | ${false}        | ${false}         | ${false}   | ${null}         | ${false}
    ${true}           | ${false}        | ${false}         | ${true}    | ${null}         | ${true}
    ${true}           | ${false}        | ${true}          | ${false}   | ${null}         | ${false}
    ${true}           | ${false}        | ${true}          | ${true}    | ${false}        | ${true}
    ${true}           | ${false}        | ${true}          | ${true}    | ${true}         | ${true}
    ${true}           | ${true}         | ${false}         | ${false}   | ${null}         | ${false}
    ${true}           | ${true}         | ${false}         | ${true}    | ${null}         | ${true}
    ${true}           | ${true}         | ${true}          | ${false}   | ${null}         | ${false}
    ${true}           | ${true}         | ${true}          | ${true}    | ${false}        | ${true}
    ${true}           | ${true}         | ${true}          | ${true}    | ${true}         | ${true}
  `.test(
    "{" +
      "inCreatingContext = $inCreatingContext," +
      "alwaysFilterOut = $alwaysFilterOut," +
      "performingSearch = $performingSearch," +
      "hasContent = $hasContent," +
      "inSearchResults = $inSearchResults," +
      "isGreyedOut = $isGreyedOut" +
      "}",
    ({
      inCreatingContext,
      alwaysFilterOut,
      performingSearch,
      inSearchResults,
      hasContent,
      isGreyedOut,
    }: {|
      inCreatingContext: boolean,
      alwaysFilterOut: boolean,
      performingSearch: boolean,
      inSearchResults: ?boolean,
      hasContent: boolean,
      isGreyedOut: boolean,
    |}) => {
      getRootStore().createStore.creationContext = inCreatingContext
        ? "containerLocation"
        : "";
      const content = hasContent ? makeMockContainer() : null;
      const parentContainer = makeMockContainer();
      if (performingSearch) {
        parentContainer.contentSearch.fetcher.query = "foo";
        parentContainer.contentSearch.fetcher.results =
          content && inSearchResults ? [content] : [];
      }
      parentContainer.contentSearch.alwaysFilterOut = () => alwaysFilterOut;
      const location = new LocationModel({
        id: null,
        coordX: 1,
        coordY: 1,
        content,
        parentContainer,
      });

      expect(location.isGreyedOut).toBe(isGreyedOut);
    }
  );
});
