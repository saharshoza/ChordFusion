package edu.utaustin.store;

import java.util.HashMap;
import java.util.List;
import java.util.Set;



/**
 * Created by amit on 1/4/17.
 */
public interface LocalStorage {

  byte[] get(String key) throws Exception;

  //byte[] get(String key, Long timestamp);

  //List<byte[]> get(String key, Long fromTime, Long toTime);

  boolean containsKey(String key);

  KeyMetadata getMetadata(String key);

  boolean put(KeyMetadata km, byte[] value) throws Exception;
  //void put(int key, int value);

  boolean delete(String key);
  //void delete(int key);

  List<KeyMetadata> keySet();

//  HashMap<String, DataContainer> dumpStorage();

//  int size();
}
