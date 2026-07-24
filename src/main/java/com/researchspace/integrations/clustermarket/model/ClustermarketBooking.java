package com.researchspace.integrations.clustermarket.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;

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
