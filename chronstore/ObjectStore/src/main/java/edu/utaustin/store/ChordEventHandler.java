package edu.utaustin.store;

import edu.utaustin.fusion.FusionStoreOperations;
import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.rmi.RemoteException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import edu.utaustin.chord.ChordID;
import edu.utaustin.chord.Event;
import edu.utaustin.chord.UpcallEventHandler;

/**
 * Created by amit on 9/4/17.
 */
public class ChordEventHandler implements UpcallEventHandler {

  /* Keep all loggers transient so that they are not passed over RMI call */
  private final transient static Logger logger = Logger.getLogger(ChordEventHandler.class);
  public static int recoveryDone = 1;
  public static int failedID;
  public static ChordID<InetAddress> successorIP;
  public static ChordID<InetAddress> failedIP;
  public static ChordID<InetAddress> myIP;
  public static int failedHandled = 1;

  private void joinRoutine(ChordID<InetAddress> prevPredecessor,
                           ChordID<InetAddress> newPredecessor){

      // Local Step: Remove my backup from old predecessor
      ObjectStore store = ObjectStoreService.getStore();
      try {
          List<ChordID<String>> allKeys = store.keySet();
          for (ChordID<String> km : allKeys){
            store.removeOldPredBkp(Integer.parseInt(km.getKey()), prevPredecessor.getKey());
          }

      } catch (RemoteException e) {
          e.printStackTrace();
      }

      // Old Predecessor Step: Remove old predecessor's backup from me
      ObjectStoreOperations objectStoreOperationsPrevPred =
              StoreRMIUtils.getRemoteObjectStore(prevPredecessor.getKey());
      try {
          objectStoreOperationsPrevPred.handleJoinOldPred(ObjectStoreService.getChordSession().getChordNodeID().getKey(), newPredecessor.getKey());
      } catch (RemoteException e) {
          e.printStackTrace();
      }

      // New Predecessor Step (New node):
      try {
          List<ChordID<String>> allKeys = store.keySet();
          logger.info("Total Keys Present on this node : " + allKeys.size());
          Map<ChordID<String>, byte[]> misplacedObjects = new LinkedHashMap<>();
          for (ChordID<String> km : allKeys) {
              if (km.inRange(prevPredecessor, newPredecessor, false, true)) {
                  // This key ID is either a replica or belongs to new predecessor
                  // This key needs to be moved to new predecessor.
                  misplacedObjects.put(km, store.getObject(km));
              }
          }
          ObjectStoreOperations
                  predecessorStore =
                  StoreRMIUtils.getRemoteObjectStore(newPredecessor.getKey());
          predecessorStore.putObjectsForIncompleteJoining(misplacedObjects, ObjectStoreService.getChordSession().getChordNodeID().getKey(),
                  prevPredecessor.getKey());
          store.updateNewPredBkp(newPredecessor.getKey());

          // Remove local extra keys
          store.deleteObjectsForIncompleteJoining(misplacedObjects, ObjectStoreService.getChordSession().getSelfSuccessor().getKey(),
                  newPredecessor.getKey());

      } catch (Exception e) {
        e.printStackTrace();
        }



  }

  public void moveKeystoNewPredecessor(ChordID<InetAddress> prevPredecessor,
                                       ChordID<InetAddress> newPredecessor) {

      long start_time = System.currentTimeMillis();
   logger.info("Move keys to new predecessor start at node  " + ObjectStoreService.getChordSession().getChordNodeID() +
                 " started ::: " + start_time);
    logger.info("Handler called in " + ObjectStoreService.getChordSession().getChordNodeID()
                + " New predecessor is: " + newPredecessor);

      // Special case handling when new node joins
      if(ObjectStoreService.getChordSession().getChordNodeID().equals(prevPredecessor)){
          // This means that I am joining the system for the first time.
          logger.info("I am joining the system for the first time !!");
          logger.info("No need to call new Predecessor event !! Skipping !!");
      }


      else {
          this.joinRoutine(prevPredecessor, newPredecessor);
      /* We can come to this method via two ways. Either our predecessor failed or we got a new
       predecessor in between
      Go through all keys of localStorage and see if we have any keys that needs to be
      moved to this new predecessor (in case we got here because new predecessor joined) OR
       find all keys for which we are now responsible and replicate those keys (in case predecessor failed) */
    /*ObjectStore store = ObjectStoreService.getStore();
    try {
    List<ChordID<String>> allKeys = store.keySet();
    logger.info("Total Keys Present on this node : " + allKeys.size());
    Map<ChordID<String>, byte[]> misplacedObjects = new LinkedHashMap<>();
    for (ChordID<String> km : allKeys) {
      if(km.inRange(prevPredecessor, newPredecessor, false, true)) {
        // This key ID is either a replica or belongs to new predecessor
        // This key needs to be moved to new predecessor.
          misplacedObjects.put(km, store.getObject(km));
      }
    }

    logger.info("Number of keys that needs to moved: " + misplacedObjects.size());
    // TODO: remove below log statement after debugging is done
    logger.info("About to move below keys: " + new ArrayList<>(misplacedObjects.keySet()));
    store.deleteObjects(misplacedObjects);
    // Start key movement. First get remote object for predecessor object store
    ObjectStoreOperations
	predecessorStore =
	StoreRMIUtils.getRemoteObjectStore(newPredecessor.getKey());
      predecessorStore.putObjects(misplacedObjects);
      // If above operation did not throw an exception
      // only then delete those keys from your storage
      //store.deleteKeys(new ArrayList<>(misplacedObjects.keySet()));
    } catch (Exception e) {
      e.printStackTrace();
    }*/
      }

      logger.info("Move keys to new predecessor at node  " + ObjectStoreService.getChordSession().getChordNodeID() +
              " ended ::: " + System.currentTimeMillis());
    logger.info(" Total time ::: "  + (System.currentTimeMillis() - start_time));

  }


    private void failRoutine(ChordID<InetAddress> prevSuccessor,
                             ChordID<InetAddress> newSuccessor) {


        ObjectStore store = ObjectStoreService.getStore();
        if (store == null) {
            logger.info("Store Object found to be NULL. No further action needed!");
            return;
        }

        try {
            // Get Primary Data from Predecessor
            LinkedHashMap<Integer, Integer> recoveredData = store.recoverFailedData();
            store.removeMyBkp(recoveredData, newSuccessor.getKey());
            // Now I am done
            logger.info("I think we have recovered failed data and updated necessary structures !! ");
            System.out.println("I think we have recovered failed data and updated necessary structures !! ");
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }


    public void moveKeystoNewSuccessor(ChordID<InetAddress> prevSuccessor,
                                       ChordID<InetAddress> newSuccessor) throws RemoteException {

    successorIP = newSuccessor;
      // move all the keys with replicaNumber != StoreConfig.REPLICATION_COUNT to this new successor
     long start_time = System.currentTimeMillis();
      logger.info("Move keys to new successor start at node  " + ObjectStoreService.getChordSession().getChordNodeID() +
              " started ::: " + start_time);

      logger.info("Handler called in " + ObjectStoreService.getChordSession().getChordNodeID()
                + " New successor is: " + newSuccessor);

       /* Go through all keys of localStorage and see if we have any keys that needs to bb
      further replicated.*/

       // Avoid event from predecessor when failure happens. Ping logic
       this.failRoutine(prevSuccessor, newSuccessor);


       /*
      ObjectStore store = ObjectStoreService.getStore();
      if (store == null) {
          logger.info("Store Object found to be NULL. No further action needed!");
          return;
      }
      recoveryDone = 0;
    //int actualErasures = Math.min(numPrimaries, numFaults);
      logger.info("Total : " + store.numPrimaries);
      logger.info("Primary ID : " + store.primaryId);


      Vector<Integer> erasures = new Vector<Integer>(store.numPrimaries+1);
      for(int i = 0; i<= store.numPrimaries; i++) {
          erasures.add(0);
      }
      erasures.set(store.primaryId, 1);
      String prevIPAddress = prevSuccessor.getKey().toString().substring(1);
      String [] split = prevIPAddress.split("\\.");
      failedID = Integer.parseInt(split[3]) - 2;
      erasures.set(failedID, -1 );

      logger.info("failed ID : " + failedID);
      logger.info("In new successor ---> erasures : " + erasures);
      for(Integer v: erasures){
          logger.info(v);
      }

//   try {
//    List<ChordID<String>> allKeys = store.keySet();
//    Map<KeyMetadata, byte[]> replicableKeys = new HashMap();
//    for (Object km : allKeys) {
//        // This key ID needs be further replicated
//      KeyMetadata newKm = new KeyMetadata(km.key);
//      newKm.setReplicaNumber(km.replicaNumber + 1);
//      replicableKeys.put(newKm, store.getObject(km.key));
//    }
//    logger.info("Number of keys that can be replicated: " + replicableKeys.size());
//    // TODO: remove below log statement after debugging is done
//    logger.info("About to replicate below keys: " + new ArrayList<>(replicableKeys.keySet()));
//    // Start key movement. First get remote object for predecessor object store
      logger.info("Getting new Successor ObjectStore ---- " + newSuccessor.getKey());
      ObjectStoreOperations
        successorStore =
        StoreRMIUtils.getRemoteObjectStore(newSuccessor.getKey());
      logger.info(" Ping started ::: "  + (System.currentTimeMillis()));

      logger.info("Calling Ping ---- ");
      successorStore.ping(erasures);
    */

      logger.info("Move keys to new successor start at node  " + ObjectStoreService.getChordSession().getChordNodeID() +
              " ended ::: " + System.currentTimeMillis());
      logger.info(" Total time ::: "  + (System.currentTimeMillis() - start_time));

      //successorStore.makeReplicas(replicableKeys);
  }

  public void handleSuccessorFailure(ChordID<InetAddress> prevPredecessor,
                                     ChordID<InetAddress> newPredecessor) {
    /**
     * When successor fails, you need to call replicateKeys on all the keys with
     * replicaNumber < StoreConfig.REPLICA_COUNT (Keys with replicaNumber == REPLICA_COUNT
     * have no relation with successor)
     */

  }

//  public void replicateKeysofFailedPredecessor(ChordID<InetAddress> prevPredecessor,
//                                     ChordID<InetAddress> newPredecessor) {
//    /** replicateKeysofFailedPredecessor:
//     *  When predecessor fails, you need to call replicate Keys on all the
//     *  keys with replicaNumber == 2. (keys with replicaNumber == 2 are the keys
//     *  which had failed predecessor as primary node)
//     *  */
//    // move all the keys with replicaNumber != StoreConfig.REPLICATION_COUNT to this new successor
//    logger.info("Handler called in " + ObjectStoreService.getChordSession().getChordNodeID()
//                + " failed predecessor is: " + prevPredecessor);
//
//    /* Go through all keys of localStorage and see if we have any keys that needs to bb
//      further replicated.*/
//    ObjectStore store = ObjectStoreService.getStore();
//    try {
//    List<KeyMetadata> allKeys = store.keySet();
//    Map<KeyMetadata, byte[]> replicableKeys = new HashMap();
//    for (KeyMetadata km : allKeys) {
//      /* Second replicas are the keys whose primary node failed - now you are the primary node for those */
//      if (km.replicaNumber == 2 ) {
//        // This key ID can be further replicated
//        km.replicaNumber = 1;
//        // Create new keyMetadata for replication
//        KeyMetadata newKm = new KeyMetadata(km.key);
//        newKm.setReplicaNumber(km.replicaNumber + 1);
//
//          replicableKeys.put(newKm, store.getObject(km.key));
//
//      }
//    }
//    logger.info("Number of keys that can be replicated: " + replicableKeys.size());
//    // TODO: remove below log statement after debugging is done
//    logger.info("About to replicate below keys: " + new ArrayList<>(replicableKeys.keySet()));
//    // Start key movement. First get remote object for predecessor object store
//    ObjectStoreOperations successorStore =
//        StoreRMIUtils.getRemoteObjectStore(ObjectStoreService.getChordSession().getSelfSuccessor().getKey());
//
//      successorStore.makeReplicas(replicableKeys);
//    } catch (Exception e) {
//      e.printStackTrace();
//    }
//
//  }

  /* preValue and newValue will contain old and updated values of predecessor/successor depending on the event
  * Currently we can Identify only 4 types of important events with respect to key movements
  * 1.SUCCESSOR_FAILED,
  * 2.NEW_SUCCESSOR,
  * 3.PREDECESSOR_FAILED,
  * 4.NEW_PREDECESSOR
  *
  * Note a SUCCESSOR FAILED or PREDECSSOR_FAILED even will most probably be always followed by
  * NEW_SUCCESSOR or NEW_PREDECESSOR event so if key movement is being done on these events then you can
  * avoid doing any keymovement on SUCCSSOR_FAILED or PREDECESSOR_FAILED events (just do all key movement on
  * NEW_* events.
  * Ideally on getting NEW_PREDECSSOR you move all the keys that now belong to that predecessor and on getting
  * NEW_SUCCESSOR you call replicate on all the kesy that have replciaNumber < REPLICA_COUNT
  * */


  @Override
  public void handleEvent(Event updateEvent,
                          ChordID<InetAddress> prevValue, ChordID<InetAddress> newValue) {
    switch (updateEvent) {
      case NEW_PREDECESSOR: {
          if(failedHandled == 0){
              logger.info(" NEW_PREDECESSOR IS CALLED IN CASE OF FAILURE !! ");
              failedHandled = 1;
          }
          else {
              // this is join case - normal
              moveKeystoNewPredecessor(prevValue, newValue);
          }
        break;
      }
      case NEW_SUCCESSOR: {
        /*try {
          moveKeystoNewSuccessor(prevValue, newValue);
        } catch (RemoteException e) {
          e.printStackTrace();
        }*/
        break;
      }
      case PREDECESSOR_FAILED: {
        /* predecessor failure is also handled when current predecessor is updated i.e in
        NEW_PREDECESSOR event.
         */
        //replicateKeysofFailedPredecessor(prevValue, newValue);
          failedIP = prevValue;
          myIP = newValue;
          failedHandled = 0;
          logger.info(" PREDECESSOR FAILED IS CALLED !! ");
        break;
      }
      case SUCCESSOR_FAILED: {
          try {
              logger.info("::::: Successor Failure Notification ::::");
              moveKeystoNewSuccessor(prevValue, newValue);
          } catch (RemoteException e) {
              e.printStackTrace();
          }
          break;
      }
      default:
        break;
    }
  }
}
