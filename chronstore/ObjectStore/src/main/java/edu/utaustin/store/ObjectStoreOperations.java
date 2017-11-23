package edu.utaustin.store;

import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.*;

import edu.utaustin.chord.ChordID;

/**
 * Created by amit on 24/3/17.
 */
public interface ObjectStoreOperations extends Remote {

  /* The getObject, putObject and deleteObject methods are only to be used by
  StoreClientAPIImpl. These should not be used by communication between
  different object stores. The methods for that communication are given below */
  byte[] getObject(ChordID<String> key) throws RemoteException;

  boolean putObject(ChordID<String> key, byte[] value) throws RemoteException;

  boolean deleteObject(ChordID<String> key) throws RemoteException;

  boolean deleteObjects(Map<ChordID<String>, byte[]> keyValueMap) throws RemoteException;

  /* These methods are only to be used when objectStore of one node wants to
  communicate with object store of other node. StoreClientImpl should not call
  these methods. The reason for this distinction is that StoreClientAPIImpl does
  not have to worry about the replica number and other metadata. It will simply
  call getObject or PutObject method and then that object store will take care
  of metadata */
  boolean putObjects(Map<ChordID<String>, byte[]> keyValueMap) throws RemoteException;

  //boolean makeReplicas(Map<ChordID<String>, byte[]> replicaData) throws RemoteException;

  //boolean removeReplica(ChordID<String> key) throws RemoteException;

  HashMap<Integer,Integer> reqData() throws RemoteException;

  int reqSize() throws RemoteException;

  boolean ping(Vector<Integer> erasures) throws RemoteException;

  boolean recoverFromRemote(LinkedHashMap<Integer,Integer> recovered, int failedID) throws RemoteException;

  List<ChordID<String>> keySet() throws RemoteException;

  Vector<Integer> getBackupData() throws RemoteException;

  Vector<Vector<Integer>> getIndexData() throws RemoteException;

  void removeOldPredBkp(int key, InetAddress prevPredIP) throws RemoteException;

  void handleJoinOldPred(InetAddress prevSuccIP, InetAddress newSuccIP) throws RemoteException;

  void updateNewPredBkp(InetAddress newPredIP) throws RemoteException;

  boolean putObjectsForIncompleteJoining(Map<ChordID<String>, byte[]> keyValueMap, InetAddress newSuccessor, InetAddress
          newPredecessor) throws RemoteException;

 boolean putObjectForIncompleteJoining(ChordID<String> key, byte[] value, InetAddress newSuccessor,
                                                          InetAddress newPredecessor) throws RemoteException;

    LinkedHashMap<Integer, Integer> doTasksForFailureRecoveryBeingNewSuccessor(LinkedHashMap<Integer, Integer> data)
        throws RemoteException;

    boolean deleteObjectForIncompleteJoining(ChordID<String> key, InetAddress newSuccessor,
                                             InetAddress newPredecessor) throws RemoteException;

    boolean deleteObjectsForIncompleteJoining(Map<ChordID<String>, byte[]> keyValueMap, InetAddress newSuccessor,
                                              InetAddress newPredecessor) throws RemoteException;

  LinkedHashMap<Integer, Integer> recoverFailedData() throws RemoteException;

   void removeMyBkp(LinkedHashMap<Integer, Integer> recoveredData, InetAddress newSuccessor) throws RemoteException;
}
