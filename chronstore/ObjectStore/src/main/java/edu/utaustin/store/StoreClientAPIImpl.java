package edu.utaustin.store;

import org.apache.log4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.*;

import edu.utaustin.chord.ChordID;
import edu.utaustin.chord.ChordSession;

/**
 * Created by amit on 1/4/17.
 */
public class StoreClientAPIImpl implements StoreClientAPI {

  /* Keep all loggers transient so that they are not passed over RMI call */
  private final transient static Logger logger = Logger.getLogger(StoreClientAPIImpl.class);


  public static byte[] serialize(Object obj) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    ObjectOutputStream os = new ObjectOutputStream(out);
    os.writeObject(obj);
    return out.toByteArray();
  }

  public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
    ByteArrayInputStream in = new ByteArrayInputStream(data);
    ObjectInputStream is = new ObjectInputStream(in);
    return is.readObject();
  }


  @Override
  public Object get(String key) throws RemoteException {
    ChordSession session = ObjectStoreService.getChordSession();
    ChordID<String> chordKey = new ChordID<>(key);
    ChordID<InetAddress> responsibleNodeID = session.getResponsibleNodeID(chordKey);
    ObjectStoreOperations responsibleStore = StoreRMIUtils.getRemoteObjectStore(responsibleNodeID.getKey());
    Object value = null;
    try {
      byte[] val = responsibleStore.getObject(chordKey);
      if (val == null) {
        logger.error("Key " + key + " not found on " + session.getChordNodeID());
      } else {
        value = deserialize(val);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return value;
  }

  @Override
  public void put(String key, Object value) throws RemoteException {
    ChordSession session = ObjectStoreService.getChordSession();
    ChordID<String> chordKey = new ChordID<>(key);
    ChordID<InetAddress> responsibleNodeID = session.getResponsibleNodeID(chordKey);
    ObjectStoreOperations responsibleStore = StoreRMIUtils.getRemoteObjectStore(responsibleNodeID.getKey());

    /* Serialize the value */
    try {
      responsibleStore.putObject(chordKey, serialize(value));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Override
  public void delete(String key) throws RemoteException {

  }

  @Override
  public List<KeyMetadata> keySet() throws RemoteException {
    List<KeyMetadata> keyMetadataList = new ArrayList<>();
    for(ChordID<String> val :ObjectStoreService.getStore().keySet()){
      KeyMetadata km = new KeyMetadata(val);
      keyMetadataList.add(km);
    }
    return keyMetadataList;
  }


  @Override
  public Map<Integer, Integer> dataSet() throws RemoteException {
    Map<Integer, Integer> dataMap = new HashMap<>();
    for(Map.Entry<Integer, Integer> val :ObjectStoreService.getStore().reqData().entrySet()){
        dataMap.put(val.getKey(), val.getValue());
    }
    return dataMap;
  }

    @Override
    public Vector<Integer> reqBackupData() throws RemoteException {
        return ObjectStoreService.getStore().getBackupData();
    }

    @Override
    public Vector<Vector<Integer>> reqBackuoIndex() throws RemoteException {
        return ObjectStoreService.getStore().getIndexData();
    }
//  @Override
//  public HashMap<String, DataContainer> dumpStore() throws RemoteException {
//    return ObjectStoreService.getStore().dumpStore();
//  }

}
