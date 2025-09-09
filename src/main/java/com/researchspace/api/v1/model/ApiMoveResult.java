package com.researchspace.api.v1.model;

public class ApiMoveResult {
  private int movedCount;
  private int total;

  public ApiMoveResult() {}

  public ApiMoveResult(int movedCount, int total) {
    this.movedCount = movedCount;
    this.total = total;
  }

  public int getMovedCount() {
    return movedCount;
  }

  public void setMovedCount(int movedCount) {
    this.movedCount = movedCount;
  }

  public int getTotal() {
    return total;
  }

  public void setTotal(int total) {
    this.total = total;
  }
}
