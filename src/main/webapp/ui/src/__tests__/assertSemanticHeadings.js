//@flow
/* eslint-env jest */

import { match } from "../util/Util";
import { Optional } from "../util/optional";
import * as ArrayUtils from "../util/ArrayUtils";

type HeadingsSpec = Array<{|
  level: 1 | 2 | 3 | 4 | 5 | 6,
  content: string,
|}>;

expect.extend({
  /*
   * This custom assertion function checks that the order, level, and text
   * content of all of the headings within the passed HTMLElement match the
   * expected list.
   *
   * This is useful for ensuring the accessiblity requirement that there are
   * semantic headings exposed to accessiblity technologies and that they
   * descend one level at a time without skipping any levels. As such, this
   * test does not check if the headings are visible or not; partially because
   * doing so is difficult in Jest, but mostly becuase invisible headings are
   * used to meet this accessiblity requirement whilst hiding the headings from
   * sighted users. For more information on this requirement, see
   * [Accessibility#Use headings semantically](../../../../../../DevDocs/DeveloperNotes/GettingStarted/Accessibility.md#Use-headings-semantically)
   *
   * For a page that has headings similar to the following,
   * ```
   * <App>
   *   <h1>Main Heading<h1>
   *   ...
   *   <h2>Sub heading 1</h2>
   *   ...
   *   <h3>Sub-sub heading</h3>
   *   ...
   *   <h2>Sub heading 2</h2>
   *   ...
   * </App>
   * ```
   *
   * The headings can be asserted in a test with a call like this
   * ```
   * const { container } = render(<App/>);
   * expect(container).assertHeadings([
   *   { level: 1, content: "Main Heading" },
   *   { level: 2, content: "Sub heading 1" },
   *   { level: 3, content: "Sub-sub heading" },
   *   { level: 2, content: "Sub heading 2" },
   * ]);
   * ```
   */
  assertHeadings(actualRootNode: HTMLElement, expectedHeadings: HeadingsSpec) {
    const tw = document.createTreeWalker(
      actualRootNode,
      NodeFilter.SHOW_ELEMENT,
      {
        acceptNode: function (node) {
          // $FlowExpectedError[cannot-resolve-name]
          if(node instanceof SVGElement)
            return NodeFilter.FILTER_SKIP;
          if (!(node instanceof HTMLElement))
            throw new Error("Not an element");
          return /^H\d$/.test(node.tagName)
            ? NodeFilter.FILTER_ACCEPT
            : NodeFilter.FILTER_SKIP;
        },
      },
      false
    );

    const actualHeadings: HeadingsSpec = [];
    while (tw.nextNode()) {
      const level = match<string, 1 | 2 | 3 | 4 | 5 | 6>([
        [(t) => t === "H1", 1],
        [(t) => t === "H2", 2],
        [(t) => t === "H3", 3],
        [(t) => t === "H4", 4],
        [(t) => t === "H5", 5],
        [(t) => t === "H6", 6],
      ])(tw.currentNode.tagName);

      actualHeadings.push({
        level,
        content: tw.currentNode.textContent,
      });
    }

    const mismatches: Array<string> = ArrayUtils.mapOptional<
      Optional<string>,
      string
    >(
      (mismatch) => mismatch,
      ArrayUtils.zipWith(
        actualHeadings,
        expectedHeadings,
        (actualHeading, expectedHeading, index) => {
          if (actualHeading.level !== expectedHeading.level)
            return Optional.present(
              `${actualHeading.level} !== ${expectedHeading.level} on line ${index}`
            );
          if (actualHeading.content !== expectedHeading.content)
            return Optional.present(
              `"${actualHeading.content}" !== "${expectedHeading.content}" on line ${index}`
            );
          return Optional.empty<string>();
        }
      )
    );

    if (mismatches.length > 0) {
      return {
        pass: false,
        message: () => mismatches[0],
      };
    } else {
      return {
        pass: true,
        message: () => "",
      };
    }
  },
});
