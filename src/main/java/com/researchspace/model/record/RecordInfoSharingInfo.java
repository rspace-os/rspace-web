package com.researchspace.model.record;

import java.util.ArrayList;
import java.util.List;

import com.researchspace.model.RecordGroupSharing;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RecordInfoSharingInfo {
	
	private List<RecordGroupSharing> directShares = new ArrayList<>();
	private List<RecordGroupSharing> implicitShares= new ArrayList<>();
	
	/**
	 * <code>true</code> if either or both of  directShares or implicitShares contain items 
	 * @return
	 */
	public boolean hasSharingInfo () {
		return !(directShares.isEmpty() && implicitShares.isEmpty());
	}
	

}
