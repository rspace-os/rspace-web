/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import "@testing-library/jest-dom";
import { encodeTags } from "../ParseEncodedTagStrings";
import { Optional } from "../../../util/optional";

describe("ParseEncodedTagStrings", () => {
  describe("encodeTags", () => {
    test("Empty array should return empty string.", () => {
      expect(encodeTags([]).isEqual(Optional.present(""))).toBe(true);
    });
    test("Single simple tag should simply be outputted.", () => {
      expect(
        encodeTags([
          {
            value: "foo",
            uri: Optional.empty(),
            vocabulary: Optional.empty(),
            version: Optional.empty(),
          },
        ]).isEqual(Optional.present("foo"))
      ).toBe(true);
    });
    test("Single controlled vocabulary tag should be encoded correctly.", () => {
      expect(
        encodeTags([
          {
            value: "value",
            uri: Optional.present("uri"),
            vocabulary: Optional.present("voc"),
            version: Optional.present("ver"),
          },
        ]).isEqual(
          Optional.present(
            `value__RSP_EXTONT_URL_DELIM__uri__RSP_EXTONT_NAME_DELIM__voc__RSP_EXTONT_VERSION_DELIM__ver`
          )
        )
      ).toBe(true);
    });
    test("Multiple tags should be delimited by commas.", () => {
      expect(
        encodeTags([
          {
            value: "foo",
            uri: Optional.empty(),
            vocabulary: Optional.empty(),
            version: Optional.empty(),
          },
          {
            value: "bar",
            uri: Optional.empty(),
            vocabulary: Optional.empty(),
            version: Optional.empty(),
          },
        ]).isEqual(Optional.present("foo,bar"))
      ).toBe(true);
    });
    test("Invalid tag will result in Optional.empty.", () => {
      expect(
        encodeTags([
          {
            value: "foo",
            uri: Optional.present("bar"),
            vocabulary: Optional.empty(),
            version: Optional.empty(),
          },
        ]).isEqual(Optional.empty())
      ).toBe(true);
    });
  });
});
