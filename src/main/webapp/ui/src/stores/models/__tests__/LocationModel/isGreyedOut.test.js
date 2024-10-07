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
import { type Search } from "../../../definitions/Search";

describe("method: isGreyedOut", () => {
  /*
   * This test asserts every permutation of state that could affect what the
   * computed property isGreyedOut returns. There are five boolean variables to
   * consider, that are not entirely mutually exclusive.
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
    alwaysFilterOut | performingSearch | hasContent | inSearchResults | isGreyedOut
    ${false}        | ${false}         | ${false}   | ${null}         | ${false}
    ${false}        | ${false}         | ${true}    | ${null}         | ${false}
    ${false}        | ${true}          | ${false}   | ${null}         | ${true}
    ${false}        | ${true}          | ${true}    | ${false}        | ${true}
    ${false}        | ${true}          | ${true}    | ${true}         | ${false}
    ${true}         | ${false}         | ${false}   | ${null}         | ${false}
    ${true}         | ${false}         | ${true}    | ${null}         | ${true}
    ${true}         | ${true}          | ${false}   | ${null}         | ${true}
    ${true}         | ${true}          | ${true}    | ${false}        | ${true}
    ${true}         | ${true}          | ${true}    | ${true}         | ${true}
  `.test(
    "{" +
      "alwaysFilterOut = $alwaysFilterOut," +
      "performingSearch = $performingSearch," +
      "hasContent = $hasContent," +
      "inSearchResults = $inSearchResults," +
      "isGreyedOut = $isGreyedOut" +
      "}",
    ({
      alwaysFilterOut,
      performingSearch,
      inSearchResults,
      hasContent,
      isGreyedOut,
    }: {|
      alwaysFilterOut: boolean,
      performingSearch: boolean,
      inSearchResults: ?boolean,
      hasContent: boolean,
      isGreyedOut: (Search) => boolean,
    |}) => {
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

      expect(location.isGreyedOut(parentContainer.contentSearch)).toBe(
        isGreyedOut
      );
    }
  );
});
