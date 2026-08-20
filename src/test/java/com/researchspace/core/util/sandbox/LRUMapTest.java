package com.researchspace.core.util.sandbox;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.collections4.keyvalue.MultiKey;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.collections4.map.MultiKeyMap;
import org.junit.jupiter.api.Test;

import com.researchspace.core.util.TransformerUtils;

class LRUMapTest {

	@Test
	void multiKeyMapCandidateForPermissionsLookup() {
	
	   MultiKeyMap<String, Set<Integer>> mkm = MultiKeyMap.multiKeyMap(new LRUMap<>(10000));
		
	   Map<MultiKey<? extends String>, Set<Integer>> m2  = Collections.synchronizedMap(mkm);
	   
	   MultiKey<String> m1 = new MultiKey<>("a1","a2");
	   m2.put(m1, TransformerUtils.toSet(1));
	  
	   System.out.println(m2.get(new MultiKey<>("stringkey", "25","3rdlevel")));
	   System.out.println(m2.get(new MultiKey<>("a1","a2")));
	   
	   List<String> users =  IntStream.range(0, 200).mapToObj(i->"user"+i).collect(toList());
	   List<String> perms =  IntStream.range(0, 20).mapToObj(i->"PERM:"+i).collect(toList());
	   Set<Integer> ids =  IntStream.range(0, 100).mapToObj(i->i).collect(Collectors.toSet());
	   
	   for (String u: users) {
		   for (String perm: perms) {
			   MultiKey<String> key = new MultiKey<>(u,perm);
			   if(m2.get(key)== null) {
				   m2.put(key, ids);
			   }
		   }
	   }
	   MultiKey<String> search = new MultiKey<>("user45","PERM:17");
	   assertNotNull(m2.get(search));
 
	}

}
