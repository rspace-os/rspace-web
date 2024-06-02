/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import React from "react";
import { render, cleanup, screen, fireEvent } from "@testing-library/react";
import "@testing-library/jest-dom";
import RsSet, { flattenWithIntersectionWithEq } from "../../set";

beforeEach(() => {
  jest.clearAllMocks();
});

afterEach(cleanup);

describe("flattenWithIntersectionWithEq", () => {

    test("Some overlap", () => {
      const actual = flattenWithIntersectionWithEq(new RsSet([
        new RsSet([ { id: 1 }, { id: 2 } ]),
        new RsSet([ { id: 1 }, { id: 3 } ]),
      ]), (elemA, elemB) => elemA.id === elemB.id);

      expect(actual.map(({id}) => id).isSame(new RsSet([ 1 ]))).toBe(true);
    });

    test("No overlap", () => {
      const actual = flattenWithIntersectionWithEq(new RsSet([
        new RsSet([ { id: 1 }, { id: 2 } ]),
        new RsSet([ { id: 3 }, { id: 4 } ]),
      ]), (elemA, elemB) => elemA.id === elemB.id);

      expect(actual.map(({id}) => id).isSame(new RsSet([]))).toBe(true);
    });

    test("All overlap", () => {
      const actual = flattenWithIntersectionWithEq(new RsSet([
        new RsSet([ { id: 1 }, { id: 2 } ]),
        new RsSet([ { id: 1 }, { id: 2 } ]),
      ]), (elemA, elemB) => elemA.id === elemB.id);

      expect(actual.map(({id}) => id).isSame(new RsSet([ 1, 2 ]))).toBe(true);
    });

});

