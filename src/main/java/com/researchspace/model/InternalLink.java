package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.TableGenerator;

import com.researchspace.model.record.BaseRecord;
import com.researchspace.model.record.Record;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity behind internal links between RSpace documents.
 */
@Entity
@Data
@EqualsAndHashCode(of = { "target", "source" })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InternalLink implements Serializable {

	private static final long serialVersionUID = 1234321L;

	@Id
	@GeneratedValue(strategy = GenerationType.TABLE, generator = "internal_link_gen")
	@TableGenerator(name = "internal_link_gen", table = "hibernate_sequences",
			pkColumnName = "sequence_name", valueColumnName = "next_val", allocationSize = 50)
	@Setter(AccessLevel.PACKAGE)
	private Long id;

	/** source document, the one containing the link */
	@ManyToOne
	private Record source;

	/** target document pointed by the link */
	@ManyToOne	
	private BaseRecord target;

	public InternalLink(Record source, BaseRecord target) {
		this.source = source;
		this.target = target;
	}

	
}
