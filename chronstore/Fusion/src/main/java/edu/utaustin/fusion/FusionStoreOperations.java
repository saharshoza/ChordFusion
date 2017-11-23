package edu.utaustin.fusion;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Vector;

/**
 * Created by amit on 24/3/17.
 */
public interface FusionStoreOperations extends Remote {

  /* The getObject, putObject and deleteObject methods are only to be used by
  StoreClientAPIImpl. These should not be used by communication between
  different object stores. The methods for that communication are given below */
  //byte[] getObject(ChordID<String> key) throws RemoteException;

  boolean putObject(int key, int newElement, int oldElement, int primaryId) throws RemoteException;

  boolean removeObject(int key, int newElement, int oldElement, int primaryId) throws RemoteException;

  Vector<Integer> reqData() throws RemoteException;

  Vector<Vector<Integer>> reqIndex() throws RemoteException;

  long reqTime() throws RemoteException;

  int reqSize() throws RemoteException;

}
