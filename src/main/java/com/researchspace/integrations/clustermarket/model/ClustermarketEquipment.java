package com.researchspace.integrations.clustermarket.model;

import java.io.Serializable;
import java.util.Objects;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

@Entity
@Table(name = "ClustermarketEquipment")
public class ClustermarketEquipment implements Serializable, ClustermarketData {
  private Long id;
  private String data;

  public ClustermarketEquipment() {}

  public ClustermarketEquipment(Long id, String data) {
    this.setId(id);
    this.setData(data);
  }

  @Id
  public Long getId() {
    return id;
  }

  @Lob
  public String getData() {
    return data;
  }

  // ID is comes from Clustermarket and is immutable
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ClustermarketEquipment that = (ClustermarketEquipment) o;
    return Objects.equals(getId(), that.getId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getId());
  }

  private void setId(Long id) {
    this.id = id;
  }

  private void setData(String data) {
    this.data = data;
  }
}
