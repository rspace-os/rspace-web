package com.researchspace.archive;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import javax.xml.bind.annotation.adapters.XmlAdapter;

public class MapAdapter extends XmlAdapter<ArrayList<KeyValue>, Map<String, String>> {

  @Override
  public ArrayList<KeyValue> marshal(Map<String, String> map) throws Exception {
    ArrayList<KeyValue> keyValues = new ArrayList<KeyValue>();
    for (Entry<String, String> entry : map.entrySet()) {
      KeyValue kv = new KeyValue(entry.getKey(), entry.getValue());
      keyValues.add(kv);
    }
    return keyValues;
  }

  @Override
  public Map<String, String> unmarshal(ArrayList<KeyValue> adaptedMap) throws Exception {
    Map<String, String> map = new HashMap<String, String>();
    ArrayList<KeyValue> kvs = (ArrayList<KeyValue>) adaptedMap;
    for (KeyValue kv : kvs) map.put(kv.getOldLink(), kv.getNewLink());

    return map;
  }
}
