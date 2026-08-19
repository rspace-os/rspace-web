package com.researchspace.model.inventory;

import com.researchspace.model.User;
import com.researchspace.model.core.GlobalIdPrefix;
import com.researchspace.model.core.GlobalIdentifier;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.TableGenerator;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

/** Represents a virtual container for inventory items */
@Entity
@Getter
@Setter
@EqualsAndHashCode
public class Basket implements Serializable {

  private Long id;

  private String name;

  @Setter(AccessLevel.PRIVATE)
  private List<BasketItem> items = new ArrayList<>();

  @Setter(AccessLevel.PRIVATE)
  private int itemCount;

  private User owner;

  /** for hibernate & pagination criteria */
  public Basket() {}

  @Id
  @GeneratedValue(strategy = GenerationType.TABLE, generator = "basket_gen")
  @TableGenerator(
      name = "basket_gen",
      table = "hibernate_sequences",
      pkColumnName = "sequence_name",
      valueColumnName = "next_val",
      allocationSize = 50)
  public Long getId() {
    return id;
  }

  @Transient
  public GlobalIdentifier getOid() {
    return new GlobalIdentifier(GlobalIdPrefix.BA, getId());
  }

  /**
   * @return the materials used in this list
   */
  @OneToMany(mappedBy = "basket", cascade = CascadeType.ALL, orphanRemoval = true)
  @OrderBy(value = "id")
  public List<BasketItem> getItems() {
    return items;
  }

  private void updateItemsCount() {
    setItemCount(getItems().size());
  }

  /**
   * @return true if item was added, false if was already in the basket
   */
  public boolean addInventoryItem(InventoryRecord invRec) {
    if (isInvRecInBasket(invRec)) {
      return false; // item already in the basket
    }

    BasketItem basketItem = new BasketItem();
    basketItem.setInventoryRecord(invRec);
    basketItem.setBasket(this);
    getItems().add(basketItem);
    updateItemsCount();
    return true;
  }

  private boolean isInvRecInBasket(InventoryRecord invRec) {
    return getItems().stream()
        .anyMatch(
            bi -> bi.getConnectedRecordGlobalIdentifier().equals(invRec.getGlobalIdentifier()));
  }

  /**
   * @return true if item was removed, false if wasn't present in the basket
   */
  public boolean removeInventoryItem(InventoryRecord invRec) {
    /* a bit convolutent implementation ensures that even TestDao update/load call sequence sees the updated list */
    int itemIndex = -1;
    for (int i = 0; i < getItems().size(); i++) {
      if (getItems()
          .get(i)
          .getConnectedRecordGlobalIdentifier()
          .equals(invRec.getGlobalIdentifier())) {
        itemIndex = i;
        break;
      }
    }
    if (itemIndex > -1) {
      getItems()
          .remove(
              itemIndex); /* removing by index seems crucial for TestDao update/load call sequence to work */
      updateItemsCount();
      return true;
    }
    return false;
  }

  @ManyToOne
  @JoinColumn(nullable = false)
  public User getOwner() {
    return owner;
  }
}
