package com.researchspace.integrations.clustermarket.model;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "ClustermarketBookings")
public class ClustermarketBooking implements Serializable, ClustermarketData {
  private String data;
  private Long id;

  public ClustermarketBooking() {}

  public ClustermarketBooking(Long id, String data) {
    this.setId(id);
    this.setData(data);
  }

  @Override
  @Lob
  public String getData() {
    return data;
  }

  @Override
  @Id
  public Long getId() {
    return id;
  }

  // ID is comes from Clustermarket and is immutable
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClustermarketBooking that = (ClustermarketBooking) o;
    return Objects.equals(getId(), that.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getId());
  }

  private void setData(String data) {
    this.data = data;
  }

  private void setId(Long id) {
    this.id = id;
  }
}
