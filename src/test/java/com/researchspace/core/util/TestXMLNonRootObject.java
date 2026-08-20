package com.researchspace.core.util;

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

@XmlType()
class TestXMLNonRootObject {
	@Override
	public String toString() {
		return "TestXMLRootObject [id=" + id + "]";
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		TestXMLNonRootObject other = (TestXMLNonRootObject) obj;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		return true;
	}

	public TestXMLNonRootObject(Long id) {
		super();
		this.id = id;
	}

	private Long id;

	public TestXMLNonRootObject() {
		super();
	}

	@XmlAttribute(required = true)
	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

}
