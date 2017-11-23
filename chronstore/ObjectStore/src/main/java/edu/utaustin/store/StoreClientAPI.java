package edu.utaustin.store;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

/**
 * Created by amit on 1/4/17.
 */
public interface StoreClientAPI extends Remote {

  Object get(String key) throws RemoteException;

  void put(String key, Object value) throws RemoteException;

  void delete(String key) throws RemoteException;

  List<KeyMetadata> keySet() throws RemoteException;
//  HashMap<String, DataContainer> dumpStore() throws RemoteException;

    Map<Integer, Integer> dataSet() throws RemoteException;

    Vector<Integer> reqBackupData() throws RemoteException;

    Vector<Vector<Integer>> reqBackuoIndex() throws RemoteException;
}