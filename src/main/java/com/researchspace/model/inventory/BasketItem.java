package com.researchspace.model.inventory;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents an item stored stored in inventory basket.
 */
@Entity
@Setter
@EqualsAndHashCode(of={"id"}, callSuper = false)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BasketItem extends InventoryRecordConnectedEntity implements Serializable {

	private Long id;
	
	private Basket basket;
	
	public BasketItem(Basket parentBasket, InventoryRecord invRec) {
		setBasket(parentBasket);
		setInventoryRecord(invRec);
	}
	
	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "basket_item_gen")
	@TableGenerator(name = "basket_item_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	public Long getId() {
		return id;
	}

	@ManyToOne(cascade = CascadeType.MERGE)
	@JoinColumn(nullable = false)
	private Basket getBasket() {
		return basket;
	}
	
}
