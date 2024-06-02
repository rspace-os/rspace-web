package com.researchspace.testutils.matchers;

import com.researchspace.core.util.ISearchResults;
import org.hamcrest.Description;
import org.hamcrest.TypeSafeDiagnosingMatcher;

public class TotalSearchResults extends TypeSafeDiagnosingMatcher<ISearchResults<?>> {
  private int expectedTotalHits;

  public TotalSearchResults(int expectedTotalHits) {
    super();
    this.expectedTotalHits = expectedTotalHits;
  }

  @Override
  public void describeTo(Description description) {
    description.appendText(expectedTotalHits + " total hits");
  }

  @Override
  protected boolean matchesSafely(ISearchResults<?> page, Description mismatchDescription) {
    if (page.getTotalHits().intValue() != expectedTotalHits) {
      mismatchDescription.appendText(" was " + page.getTotalHits().intValue() + " total hits");
      return false;
    }
    return true;
  }

  /**
   * Use this in tests
   *
   * @param term
   * @return
   */
  public static TotalSearchResults totalSearchResults(int expectedTotalHits) {
    return new TotalSearchResults(expectedTotalHits);
  }
}
