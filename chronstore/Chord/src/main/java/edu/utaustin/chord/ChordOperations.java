package edu.utaustin.chord;

import java.net.InetAddress;
import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 * Created by amit on 12/2/17. An interface for all the operations a valid Chord Node should
 * support
 */

public interface ChordOperations extends Remote {

  /* Routing related methods */

  /**
   * Finds the closest known successor for @param id.
   *
   * @return returns the ChordID of closest successor known for @param id. return this.id only when
   * this node itself is responsible for key @param id.
   */
  ChordID<InetAddress> getSuccessor(ChordID<InetAddress> callerID, Hash id) throws RemoteException;

  /**
   * Returns immediate next node for this node
   */
  ChordID<InetAddress> getSuccessor(ChordID<InetAddress> callerID) throws RemoteException;

  /**
   * @return returns the ChordID of the predecessor of this node.
   */
  ChordID<InetAddress> getPredecessor(ChordID<InetAddress> callerID, Hash id) throws RemoteException;

  /**
   * Returns immediate predecessor of this node
   */
  ChordID<InetAddress> getPredecessor(ChordID<InetAddress> callerID) throws RemoteException;

  /**
   * Finding closes prcedding finger. This method findgs the closes finger for given id.
   * getPredecessor and getSuccessor both use this method. Refer chord paper for more details
   */
  ChordID<InetAddress> getClosestPrecedingFinger(ChordID<InetAddress> callerID, Hash id) throws RemoteException;



  /* New node join and stabilization methods */
  /**
   * notify a newly joining node about you being its predecessor. New node should check and set you
   * as its predecessor if required.
   */
  void notify(ChordID<InetAddress> callerID, ChordID<InetAddress> id) throws RemoteException;
}
