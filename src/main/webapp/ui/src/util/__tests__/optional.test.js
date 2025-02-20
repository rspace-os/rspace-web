/*
 * @jest-environment jsdom
 */
//@flow
/* eslint-env jest */
import fc, { type Arbitrary } from "fast-check";
import { Optional } from "../optional";

const arbOptional = <T>(arb: Arbitrary<T>): Arbitrary<Optional<T>> =>
  fc.option(arb).map((x) => Optional.fromNullable(x));

describe("optional", () => {
  /*
   * The Optional type adheres to the definition of a Functor, which is to say
   * that it can be mapped over just like arrays. For more info on Functors,
   * see https://en.wikipedia.org/wiki/Functor_(functional_programming)
   */
  describe("functor laws", () => {
    test("Mapping with identity function does nothing", () => {
      fc.assert(
        fc.property(arbOptional(fc.anything()), (opt) => {
          expect(opt.map((x) => x)).toEqual(opt);
        })
      );
    });
    test("Mapping once with two functions is the same as mapping with each separately", () => {
      fc.assert(
        fc.property(
          arbOptional(fc.anything()),
          fc.func<mixed, mixed>(fc.anything()),
          fc.func<mixed, mixed>(fc.anything()),
          (opt, funcA, funcB) => {
            expect(opt.map(funcA).map(funcB)).toEqual(
              opt.map((x) => funcB(funcA(x)))
            );
          }
        )
      );
    });
  });

  /*
   * The Optional type also adheres to the definitions of a Monad, which is to
   * say that functions that produce Optionals can be chained together. For
   * more info on Monads, see
   * https://en.wikipedia.org/wiki/Monad_(functional_programming)
   */
  describe("monad laws", () => {
    test("Left identity", () => {
      fc.assert(
        fc.property(
          fc.anything(),
          fc.func<mixed, Optional<mixed>>(arbOptional(fc.anything())),
          (value, func) => {
            expect(Optional.present(value).flatMap(func)).toEqual(func(value));
          }
        )
      );
    });
    test("Right identity", () => {
      fc.assert(
        fc.property(arbOptional(fc.anything()), (opt) => {
          expect(opt.flatMap((x) => Optional.present(x))).toEqual(opt);
        })
      );
    });
    test("Associativity", () => {
      fc.assert(
        fc.property(
          arbOptional(fc.anything()),
          fc.func<mixed, Optional<mixed>>(arbOptional(fc.anything())),
          fc.func<mixed, Optional<mixed>>(arbOptional(fc.anything())),
          (opt, funcA, funcB) => {
            expect(opt.flatMap(funcA).flatMap(funcB)).toEqual(
              opt.flatMap((x) => funcA(x).flatMap(funcB))
            );
          }
        )
      );
    });
  });
});
