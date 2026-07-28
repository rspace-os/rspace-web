package com.researchspace.model;

import java.io.Serializable;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Transient;

import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

/**
 * POJO class to hold archiving checksum
 */
@Entity
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class ArchivalCheckSum implements Serializable {
	@Override
	public String toString() {
		return "ArchivalCheckSum [uid=" + uid + ", zipName=" + zipName + ", zipSize=" + zipSize + ", archivalDate="
				+ archivalDate + ", checkSum=" + checkSum + ", algorithm=" + algorithm + ", zipContentsChecksum="
				+ zipContentCheckSum + "]";
	}

	@Transient
	private static final long serialVersionUID = 1L;

	private String uid;
	private String zipName;
	private long zipSize;
	private long archivalDate;
	private long checkSum;
	private String algorithm = "CRC32";
	private String zipContentCheckSum;
	private User exporter;
	private boolean downloadTimeExpired;

	/**
	 * If true, the archive file will have been deleted.
	 * 
	 * @return
	 */
	public boolean isDownloadTimeExpired() {
		return downloadTimeExpired;
	}

	public void setDownloadTimeExpired(boolean downloadTimeExpired) {
		this.downloadTimeExpired = downloadTimeExpired;
	}

	/**
	 * The user who generated the export.
	 * 
	 * @return
	 */
	@ManyToOne()
	public User getExporter() {
		return exporter;
	}

	public void setExporter(User exporter) {
		this.exporter = exporter;
	}

	@Id
	public String getUid() {
		return uid;
	}

	/**
	 * The file name of the zip
	 * 
	 * @return
	 */
	public String getZipName() {
		return zipName;
	}

	/**
	 * The size of the archive
	 * 
	 * @return
	 */
	public long getZipSize() {
		return zipSize;
	}

	public long getArchivalDate() {
		return archivalDate;
	}

	public long getCheckSum() {
		return checkSum;
	}

	public String getAlgorithm() {
		return algorithm;
	}

	public String getZipContentCheckSum() {
		return zipContentCheckSum;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public void setZipName(String zipName) {
		this.zipName = zipName;
	}

	public void setZipSize(long zipSize) {
		this.zipSize = zipSize;
	}

	public void setArchivalDate(long archivalDate) {
		this.archivalDate = archivalDate;
	}

	public void setCheckSum(long checkSum) {
		this.checkSum = checkSum;
	}

	public void setAlgorithm(String algorithm) {
		this.algorithm = algorithm;
	}

	public void setZipContentCheckSum(String zipContentCheckSum) {
		this.zipContentCheckSum = zipContentCheckSum;
	}
}
