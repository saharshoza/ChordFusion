package edu.utaustin.chord;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.ArrayList;

import org.apache.log4j.Logger;

import com.google.gson.Gson;


/**
 * Created by amit on 13/2/17.
 */
class ChordNode implements ChordOperations {

  /* ChordID of this node */
  ChordID<InetAddress> selfChordID;

  /* ChordID of the predecessor node */
  ChordID<InetAddress> predecessorChordID = null;

  /* IP address of this node */
  InetAddress selfIP;

  /* Finger table of this node */
  FingerTable fingerTable;

  /* A successor list maintained by each node to stabilization protocol*/
  ArrayList<ChordID<InetAddress>> successorList;

  /* Number of entries to keep in successor list */
  private static final int SUCCESSOR_LIST_MAX_SIZE = ChordConfig.SUCCESSOR_LIST_MAX_SIZE;

  /* Keep all loggers transient so that they are not passed over RMI call */
  private final transient static Logger logger = Logger.getLogger(ChordNode.class);
  private final transient static Logger analysisLogger = Logger.getLogger("analysis");
  
  /* UpCallHandler for all events that upper layers needs to be notified about */
  UpcallEventHandler upcallHandler;

  ChordNode(InetAddress selfIP) {
    this.selfIP = selfIP;
    selfChordID = new ChordID<InetAddress>(selfIP);
    fingerTable = new FingerTable(selfChordID);
    predecessorChordID = selfChordID;
    successorList = new ArrayList<ChordID<InetAddress>>();
  }

  public String toString() {
    return "[" + selfChordID + "," + selfIP.toString() + "]";
  }

  public void setUpcallHandler(UpcallEventHandler upcallHandler) {
    this.upcallHandler = upcallHandler;
  }

  private void setPredecessorChordID(Event updateEvent, ChordID<InetAddress> chordID) {
    ChordID<InetAddress> prevPredecessor = this.predecessorChordID;
    synchronized (this) {
      this.predecessorChordID = chordID;
    }
    if (upcallHandler != null)
      upcallHandler.handleEvent(updateEvent, prevPredecessor, chordID);
  }

  private void setSuccessor(Event updateEvent, ChordID<InetAddress> successorChordID) {
    logger.debug("[Entry] Method:  setSuccessor " + "@" + selfChordID +
		 " Caller: " + selfChordID + "Parameters: " + successorChordID);

    ChordID<InetAddress> prevSuccessor = fingerTable.getEntry(0).responsibleNodeID;
    synchronized (this) {
      fingerTable.getEntry(0).responsibleNodeID = successorChordID;
    }
    upcallHandler.handleEvent(updateEvent, prevSuccessor, successorChordID);

    logger.debug("[Exit] Method:  setSuccessor " + "@" + selfChordID +
                 " Caller: " + selfChordID + "Parameters: " + successorChordID);
  }

  @Override
  public ChordID<InetAddress> getSuccessor(ChordID<InetAddress> callerID) throws RemoteException {
    logger.debug("[Entry] Method:  getSuccessor " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: ");
    logger.debug("[Exit] Method:  getSuccessor " + "@" + selfChordID +
                 " Caller: " + callerID + "Parameters: ");
    return fingerTable.getEntry(0).responsibleNodeID;
  }

  @Override
  public ChordID<InetAddress> getSuccessor(ChordID<InetAddress> callerID, Hash id)
      throws RemoteException {
    logger.debug("[Entry] Method:  getSuccessor " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + id);

    ChordID<InetAddress> successorID = null;
    ChordID<InetAddress> predecessorID = getPredecessor(selfChordID, id);
    logger.debug("Predecessor of " + id + " is found to be " + predecessorID);
    ChordOperations predecessorROR = ChordRMIUtils.getRemoteNodeObject(predecessorID.getKey());
    if (predecessorROR == null) {
      logger.error("Unable to get RMI object for " + predecessorID);
    } else {
      try {
        successorID = predecessorROR.getSuccessor(selfChordID);
      } catch (Exception e) {
        e.printStackTrace();
        successorID = null;
      }
    }
    logger.debug("Successor ID for " + id + " is found to be " + successorID);

    logger.debug("[Exit] Method:  getSuccessor " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + id);
    return successorID;
  }

  @Override
  public ChordID<InetAddress> getPredecessor(ChordID<InetAddress> callerID) throws RemoteException {
    logger.debug("[Entry] Method:  getPredecessor " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: ");
    logger.debug("[Exit] Method:  getPredecessor " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: ");
    return predecessorChordID;
  }

  @Override
  public ChordID<InetAddress> getPredecessor(ChordID<InetAddress> callerID, Hash id)
      throws RemoteException {
    logger.debug("[Entry] Method:  getPredecessor " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + id);

    ChordID<InetAddress> predecessor = selfChordID;
    ChordOperations predecessorROR = ChordRMIUtils.getRemoteNodeObject(selfChordID.getKey());
    try {
      while (!id.inRange(predecessor, predecessorROR.getSuccessor(selfChordID), false, true)) {
        predecessor = predecessorROR.getClosestPrecedingFinger(selfChordID, id);
        predecessorROR = ChordRMIUtils.getRemoteNodeObject(predecessor.getKey());
        if (predecessorROR == null) {
          logger.error("Unable to get RMI object for " + predecessor);
          predecessor = null;
        }
      }
    } catch (RemoteException e) {
      e.printStackTrace();
      predecessor = null;
    }

    logger.debug("Predecessor found is: " + predecessor);

    logger.debug("[Exit] Method:  getPredecessor " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + id);
    return predecessor;
  }


  @Override
  public ChordID<InetAddress> getClosestPrecedingFinger(ChordID<InetAddress> callerID, Hash id)
      throws RemoteException {
    logger.debug("[Entry] Method:  getClosestPrecedingFinger " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + id);

    ChordID<InetAddress> closestPrecedingFinger = selfChordID;

    for (int i = FingerTable.MAX_SIZE - 1; i >= 0; i--) {
      FingerTableEntry ftEntry = fingerTable.getEntry(i);
      if (ftEntry.responsibleNodeID.inRange(selfChordID, id, false, false)) {
	closestPrecedingFinger = ftEntry.responsibleNodeID;
	break;
      }
    }

    logger.debug("Closest Preceding finger is " + closestPrecedingFinger);

    logger.debug("[Exit] Method:  getClosestPrecedingFinger " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + id);
    return closestPrecedingFinger;
  }


  /**
   * A newly joining node 'n' needs an ID of any existing chord node say 'm'. 'n' calls
   * getSuccessor() method on that node 'm' with lookup id as 'n' to find successor of 'n'. This
   * method will setup the successor node for 'n'.
   */
  boolean join(ArrayList<InetAddress> bootstrapNodes) throws RemoteException {
    logger.debug("[Entry] Method:  join " + "@" + selfChordID +
		 " Caller: " + "Parameters: " + bootstrapNodes);
    analysisLogger.info("%JOIN%"+selfChordID.getKey()+"%"+selfChordID.getValue());
    boolean result = true;
    ChordOperations bootstrapNodeROR = null;

     /* Get RMI reference of bootstrap node */
      for (int i = 0; i < bootstrapNodes.size() && bootstrapNodeROR == null; i++) {
        bootstrapNodeROR = ChordRMIUtils.getRemoteNodeObject(bootstrapNodes.get(i));
        if (bootstrapNodeROR == null) {
          logger.error("Unable to get RMI object for " + bootstrapNodes.get(i));
        }
      }

      if (bootstrapNodeROR == null) {
        logger.error("Could not join the network. Exiting.");
        result = false;
      } else {
        /* Joining the network for first time. Get successor ID from bootstrap node */
        setSuccessor(Event.NEW_SUCCESSOR, bootstrapNodeROR.getSuccessor(selfChordID));
      }

    logger.debug("[Exit] Method:  join " + "@" + selfChordID +
		 " Caller: " + "Parameters: " + bootstrapNodes);

    return result;
  }

  /**
   * This method will check with current successor to see if predecesor of current successor is same
   * as its own ID. If not it means new node has joined. Then change your successor to this new node
   * and ask the new node to set its own predecessor as your ID.
   */
  void stabilize() throws RemoteException {
    logger.debug("[Entry] Method:  stabilize " + "@" + selfChordID +
		 " Caller: " + "Parameters: ");

    ChordID<InetAddress> successorChordID = getSuccessor(selfChordID);
      /* Periodically check predecessor of current successor. This will tell if a new node has
      joined in between */
    ChordOperations successorROR = ChordRMIUtils.getRemoteNodeObject(successorChordID.getKey());
    if (successorROR == null) {
      logger.error("Unable to get RMI object for " + successorChordID
		   + " will try again in next interval");
    } else {
      try {
        ChordID<InetAddress> predecessorOfSuccessor = successorROR.getPredecessor(selfChordID);
        logger.debug("Predecessor found from successor is " + predecessorOfSuccessor);
        if (predecessorOfSuccessor.inRange(selfChordID, successorChordID, false, false)) {
          setSuccessor(Event.NEW_SUCCESSOR, predecessorOfSuccessor);
        }
      } catch (Exception e) {
        e.printStackTrace();
      }

      successorROR = ChordRMIUtils.getRemoteNodeObject(getSuccessor(selfChordID).getKey());
      if (successorROR == null) {
	logger.error("Unable to notify " + getSuccessor(selfChordID)
		     + " will try again in next interval");
      } else {
        try {
          successorROR.notify(selfChordID, selfChordID);
        } catch (Exception e) {
          logger.error("Unable to notify " + getSuccessor(selfChordID)
                       + " will try again in next interval");
          e.printStackTrace();
        }
      }
    }

    logger.debug("[Exit] Method:  stabilize " + "@" + selfChordID +
		 " Caller: " + "Parameters: ");
  }

  /* Get a notification from my possible new predecessor */
  @Override
  public void notify(ChordID<InetAddress> callerID, ChordID<InetAddress> possiblePredecessor)
      throws RemoteException {
    logger.debug("[Entry] Method:  notify " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + possiblePredecessor);

    logger.debug(" Current predecessor is " + predecessorChordID);
    /* If new value is more closer or current predecessor is down => update predecessor */
    if (possiblePredecessor.inRange(predecessorChordID, selfChordID, false, false)) {
      setPredecessorChordID(Event.NEW_PREDECESSOR, possiblePredecessor);
    }
    logger.debug(" Updated predecessor is " + predecessorChordID);

    logger.debug("[Exit] Method:  notify " + "@" + selfChordID +
		 " Caller: " + callerID + "Parameters: " + possiblePredecessor);
  }



  private void updateSuccessorList() throws RemoteException {
    logger.debug("[Entry] Method:  updateSuccessorList " + "@" + selfChordID +
		 " Caller: " + "Parameters: ");

    /* Flush all entries from successor list and regenerate it */
    //successorList.clear();

    /* First entry in the successor list is direct successor */
//    synchronized (this) {
      if (successorList.size() == 0) {
        successorList.add(0, getSuccessor(selfChordID));
      } else {
        successorList.set(0, getSuccessor(selfChordID));
      }
//    }

    /* First update current entries in the list */
    for (int i = 1; i < ChordConfig.SUCCESSOR_LIST_MAX_SIZE; i++) {
      /* Set (i+1)'th successor entry by querying i'th entry node for its successor */
      try {
        ChordOperations previousEntryROR = ChordRMIUtils.getRemoteNodeObject(successorList.get(i - 1).getKey());
        ChordID<InetAddress> nextSuccessor = previousEntryROR.getSuccessor(selfChordID);
        if (i < successorList.size()) {
          successorList.set(i, nextSuccessor);
        } else {
          successorList.add(nextSuccessor);
        }
      } catch (Exception e ) {
        e.printStackTrace();
        break;
      }
    }


    logger.info("Successor list for node  " + selfChordID + " " + successorList);

    logger.debug("[Exit] Method:  updateSuccessorList " + "@" + selfChordID +
		 " Caller: " + "Parameters: ");
  }

  /**
   * Periodically run this function to update your finger table. Basically for every entry in finger
   * table re-check its successor and update if required.
   */
  void fixFingers() throws RemoteException {
    logger.debug("[Entry] Method:  fixFingers " + "@" + selfChordID +
		 " Caller: " + "Parameters: ");

      /* Always re-search for successor of entry 1 in finger table to remove stale entries */
    ChordOperations successorROR = ChordRMIUtils.getRemoteNodeObject(getSuccessor(selfChordID).getKey());

    if (successorROR == null) {
	/* unable to contact with successor mark this successor as failed
	 and remove its entry. */
      //TODO: move all keys for failed node to new successor
      logger.error("Unable to get RMI object for successor!");
      /* Check next available node in successor list */
      for (int i = 1; i < successorList.size(); i++) {
	successorROR = ChordRMIUtils.getRemoteNodeObject(successorList.get(i).getKey());
	if (successorROR != null) {
	  setSuccessor(Event.SUCCESSOR_FAILED, successorList.get(i));
	  break;
	}
      }
    }

    /* If successor is still not available return */
    if (successorROR == null) {
      logger.error("Unable to get successor.Trying in next interval..");
    } else {
      logger.debug("FixFingers with successor " + getSuccessor(selfChordID));

      for (int i = 1; i < fingerTable.MAX_SIZE; i++) {
	ChordID<InetAddress> responsibleNode;
	FingerTableEntry entry = fingerTable.getEntry(i);
	/* If successorID of previous entry is still bigger than this entry.RangeStart
	then use it as successor. Else ask our successor to find correct successor for this entry
         */
	if (entry.hashRangeStart
		.inRange(selfChordID, fingerTable.getEntry(i - 1).responsibleNodeID, true, false)) {
	  responsibleNode = fingerTable.getEntry(i - 1).responsibleNodeID;
	} else {
          try {
            responsibleNode = successorROR.getSuccessor(selfChordID, fingerTable.getEntry(i).hashRangeStart);
          } catch (RemoteException e) {
            e.printStackTrace();
            responsibleNode = selfChordID;
          }
	}

//        synchronized (this) {
          entry.responsibleNodeID = responsibleNode;
//        }
      }

      /* Also update your successor list */
      updateSuccessorList();
    }


    /* Also check if your predecessor is still up and running */
    if (ChordRMIUtils.getRemoteNodeObject(predecessorChordID.getKey()) == null) {
      //synchronized (this) {
        setPredecessorChordID(Event.PREDECESSOR_FAILED, selfChordID);
      //}
    }

    logger.info(fingerTable.toString());
    logger.info("Predecessor is: " + predecessorChordID);

    logger.debug("[Exit] Method:  fixFingers " + "@" + selfChordID +
		 " Caller: " + "Parameters: ");
  }
  
  public void printNode() {
	  //analysisLogger.info("\n"+selfChordID.getKey()+"$"+selfChordID.getValue()+"\n"+fingerTable.toSimpleString());
	  Gson gson = new Gson();
	  analysisLogger.info("JSON-PAYLOAD\n"+gson.toJson(this));
  }
  
}
