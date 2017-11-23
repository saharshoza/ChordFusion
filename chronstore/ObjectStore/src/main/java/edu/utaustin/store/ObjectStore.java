package edu.utaustin.store;

import edu.utaustin.chord.*;
import edu.utaustin.fusion.*;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.*;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.security.PublicKey;
import java.util.*;

import static edu.utaustin.store.StoreClientAPIImpl.deserialize;
import static edu.utaustin.store.StoreClientAPIImpl.serialize;

/**
 * Created by amit on 24/3/17.
 */
class ObjectStore implements ObjectStoreOperations {

  private LocalStorage localStorage;
  int primaryId;
  int numPrimaries;
  int numFaults;
  Map<Integer,PrimaryNode<Integer>> primaryMap;
  MyContainer auxList;
  FusionStoreOperations fusionStoreOperations;
  FusionStoreOperations fusionStoreOperationsForSuccessor;
  FusionStoreOperations fusionStoreOperationsForPredecessor;
  /* Keep all loggers transient so that they are not passed over RMI call */
  private final transient static Logger logger = Logger.getLogger(ObjectStore.class);
    private Map.Entry<Integer, PrimaryNode<Integer>> en;

    public ObjectStore(int numPrimaries, int numFaults) {
    try {
      this.numPrimaries = numPrimaries;
        this.numFaults = numFaults;
        // Fusion.initialize(numPrimaries, numFaults);
        String ownIPAddress = getMyEthernetIP().toString().substring(1);
        //System.out.println("PrimaryIP : " + ownIPAddress);
        logger.info("PrimaryIP : " + ownIPAddress);
        String [] split = ownIPAddress.split("\\.");
        primaryId = Integer.parseInt(split[3]) - 2;
        logger.info("PrimaryID : " + primaryId);
        GenericMap<Integer, PrimaryNode<Integer>> map = new GenericMap<>();
        this.primaryMap = map;
        auxList = new MyContainer();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

    private static InetAddress getMyEthernetIP() {
        Inet4Address ipv4 = null;
        Inet6Address ipv6 = null;
    /* Interface name used for Docker containers */
        String interfaceName = ChordConfig.NetworkInterface;
        try {
            NetworkInterface iface = NetworkInterface.getByName(interfaceName);
            Enumeration<InetAddress> addrList = iface.getInetAddresses();
            while (addrList.hasMoreElements()) {
                InetAddress address = addrList.nextElement();
                if (address instanceof Inet4Address) {
                    ipv4 = (Inet4Address) address;
                } else if (address instanceof Inet6Address) {
                    ipv6 = (Inet6Address) address;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return ipv4 != null ? ipv4 : ipv6;
    }


  /* The flow of get/put key:
  Client will call get/put on StoreClientAPI. The StoreClientAPIImpl will then use chordsession
  to identify the reponsible node and call getObject or putObject on that node using RMI.
  If its a getObject call then that node can simply return the value associated with key. However,
  if that is a putObject call then the node will put the key in KeyMeatdata object along with
  the replicaNumber (which is a metadata field) and then call putObjects method. This method will
  then replicate the data and also store that data in its local store. */
  @Override
  public byte[] getObject(ChordID<String> key) throws RemoteException {
//    if (!localStorage.containsKey(key.getKey())) {
//      return null;
//    } else {
//      return localStorage.get(key.getKey());
//    }

      byte[] val = null;

      Integer value = primaryMap.get(Integer.parseInt(key.getKey())).getValue();
      if(value == null){
          return val;
      }
      else {
          try {
             val = serialize(value.toString());
          } catch (IOException e) {
              e.printStackTrace();
          }
      }
      return val;
  }


    @Override
    public boolean putObjectForIncompleteJoining(ChordID<String> key, byte[] value, InetAddress newSuccessor,
                                                          InetAddress newPredecessor) throws RemoteException {
        try {
            logger.info("Incomplete Joining ::: Creating first copy of " + key +
                    " on Node: " + ObjectStoreService.getChordSession().getChordNodeID());

            this.putFusionPrimaryForIncompleteJoining(Integer.parseInt(key.getKey()), Integer.parseInt((String)deserialize(value)),
                    newSuccessor, newPredecessor);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    private void putFusionPrimaryForIncompleteJoining(int key, int value, InetAddress newSuccessor,
                                                      InetAddress newPredecessor){

        int oldValue = -1;
        if(primaryMap.containsKey(key)){//if key already exists, just update its value
            logger.info("Primary map contains key : " + key );
            System.out.println("Primary map contains key : " + key );
            PrimaryNode<Integer> oldNode = 	primaryMap.get(key);
            oldValue = oldNode.getValue();//the backup will need this value for update.
            oldNode.setValue(value);
        }else{
            PrimaryNode<Integer> p = new PrimaryNode<Integer>(value);
            PrimaryAuxNode<Integer> a = new PrimaryAuxNode<Integer>(p);
            p.setAuxNode(a);
            primaryMap.put(key, p);
            auxList.add(a);//adds to end of list
            logger.info("Primary map does not contain key : " + key + " ::: Adding key to primary map and auxiliary list");
            System.out.println("Primary map does not contain key : " + key + " ::: Adding key to primary map and auxiliary list");
        }

        try {
            // Update backup on Successor and Predecessor
            // ChordSession chordSession = ObjectStoreService.getChordSession();
            logger.info("Put Operation ::: Updating backup on Successor :: " + newSuccessor.toString());

            fusionStoreOperationsForSuccessor = StoreRMIUtils.getRemoteFusionStore(newSuccessor);
            fusionStoreOperationsForSuccessor.putObject(key, value, oldValue, 0);

            logger.info("Put Operation ::: Updating backup on Predecessor :: " + newPredecessor.toString());
            fusionStoreOperationsForPredecessor = StoreRMIUtils.getRemoteFusionStore(newPredecessor);
            fusionStoreOperationsForPredecessor.putObject(key, value, oldValue, 1);

            logger.info("add, index:" + key + " value:" + value + " oldValue:" + oldValue + " Map:" + primaryMap + " AuxList:" + auxList);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }



    @Override
  public boolean putObject(ChordID<String> key, byte[] value) throws RemoteException {
    try {
      logger.info("Creating first copy of " + key +
                  " on Node: " + ObjectStoreService.getChordSession().getChordNodeID());
      this.putFusionPrimary(Integer.parseInt(key.getKey()), Integer.parseInt((String)deserialize(value)));
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }

  private void putFusionPrimary(int key, int value){

    int oldValue = -1;
    if(primaryMap.containsKey(key)){//if key already exists, just update its value
      logger.info("Primary map contains key : " + key );
      System.out.println("Primary map contains key : " + key );
      PrimaryNode<Integer> oldNode = 	primaryMap.get(key);
      oldValue = oldNode.getValue();//the backup will need this value for update.
      oldNode.setValue(value);
    }else{
      PrimaryNode<Integer> p = new PrimaryNode<Integer>(value);
      PrimaryAuxNode<Integer> a = new PrimaryAuxNode<Integer>(p);
      p.setAuxNode(a);
      primaryMap.put(key, p);
      auxList.add(a);//adds to end of list
      logger.info("Primary map does not contain key : " + key + " ::: Adding key to primary map and auxiliary list");
      System.out.println("Primary map does not contain key : " + key + " ::: Adding key to primary map and auxiliary list");
    }

    try {
      /*for(int i=2; i< (numFaults+2);i++) {
        Integer num = i + numPrimaries;
        String ipAddress = "172.18.0." + num.toString();
        InetAddress ip = InetAddress.getByName(ipAddress);
        System.out.println("Updating Backup with IP : " + ipAddress);
        this.fusionStoreOperations = StoreRMIUtils.getRemoteFusionStore(ip);

        this.fusionStoreOperations.putObject(key, value, oldValue, primaryId);
        //send the message to all backups

        //if (Util.debugFlag)
          //System.out.println("add, index:" + key + " value:" + value + " oldValue:" + oldValue + " Map:" + primaryMap + " AuxList:" + auxList);
          logger.info("add, index:" + key + " value:" + value + " oldValue:" + oldValue + " Map:" + primaryMap + " AuxList:" + auxList);

      }*/

      // Update backup on Successor and Predecessor
        ChordSession chordSession = ObjectStoreService.getChordSession();
        logger.info("Put Operation ::: Updating backup on Successor :: " + chordSession.getSelfSuccessor().getKey().toString());

      fusionStoreOperationsForSuccessor = StoreRMIUtils.getRemoteFusionStore(chordSession.getSelfSuccessor().getKey());
      fusionStoreOperationsForSuccessor.putObject(key, value, oldValue, 0);

        logger.info("Put Operation ::: Updating backup on Predecessor :: " + chordSession.getSelfPredecessor().getKey().toString());
      fusionStoreOperationsForPredecessor = StoreRMIUtils.getRemoteFusionStore(chordSession.getSelfPredecessor().getKey());
      fusionStoreOperationsForPredecessor.putObject(key, value, oldValue, 1);

        logger.info("add, index:" + key + " value:" + value + " oldValue:" + oldValue + " Map:" + primaryMap + " AuxList:" + auxList);
    }
    catch (Exception e){
        e.printStackTrace();
    }
  }

  @Override
  public boolean deleteObject(ChordID<String> key) throws RemoteException {
    //removeReplica(key);
    this.remove(Integer.parseInt(key.getKey()));
    return true;
  }

    @Override
    public boolean deleteObjects(Map<ChordID<String>, byte[]> keyValueMap) throws RemoteException {
        for(Map.Entry<ChordID<String>, byte[]> entry : keyValueMap.entrySet()){
            this.deleteObject(entry.getKey());
        }
        return true;
    }

    public void updateNewPredBkp(InetAddress newPredIP){
        logger.info("Add Operation ::: Updating backup on New Predecessor :: " + newPredIP.toString());
        try {
            fusionStoreOperationsForPredecessor = StoreRMIUtils.getRemoteFusionStore(newPredIP);
            for (Map.Entry<Integer,PrimaryNode<Integer>> entry : primaryMap.entrySet()){
                fusionStoreOperationsForPredecessor.putObject(entry.getKey(), entry.getValue().getValue(), -1, 1);
            }
        } catch (RemoteException e){
            e.printStackTrace();
        }
    }

    public void handleJoinOldPred(InetAddress prevSuccIP, InetAddress newSuccIP){
        updateNewSuccBkp(newSuccIP);
        removeOldSuccBkp(prevSuccIP);
    }

    private void updateNewSuccBkp(InetAddress newSuccIP){
        logger.info("Add Operation ::: Updating backup on New Successor :: " + newSuccIP.toString());
        try {
            fusionStoreOperationsForSuccessor = StoreRMIUtils.getRemoteFusionStore(newSuccIP);
            for (Map.Entry<Integer,PrimaryNode<Integer>> entry : primaryMap.entrySet()){
                fusionStoreOperationsForSuccessor.putObject(entry.getKey(), entry.getValue().getValue(), -1, 0);
            }
        } catch (RemoteException e){
            e.printStackTrace();
        }
    }

    private void removeOldSuccBkp(InetAddress prevSuccIP){
        logger.info("Remove Old Backup Operation ::: Removing backup on Old Successor :: " + prevSuccIP.toString());
        try {
            List<ChordID<String>> allKeys = this.keySet();
            for (ChordID<String> km : allKeys){
                removeOldPredBkpForOldSuccessor(Integer.parseInt(km.getKey()), prevSuccIP);
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }

    }


    public void removeMyBkp(LinkedHashMap<Integer, Integer> recoveredData, InetAddress newSuccessor){

        // Clear my backup so that new successor can put his backup on me
        logger.info("Remove Own Backup Operation ::: Removing backup to make space for new Successor :: " + ObjectStore.getMyEthernetIP().toString());
        FusionStoreOperations newSuccessorFusionStore = StoreRMIUtils.getRemoteFusionStore(newSuccessor);
        ObjectStoreOperations newSuccessorObjectStore = StoreRMIUtils.getRemoteObjectStore(newSuccessor);
        LinkedHashMap<Integer, Integer> newSuccessorData = null;
        try {
            for (Map.Entry<Integer, Integer> entry : recoveredData.entrySet()){
                // we don't have primary auxStructure with us
                // we have to remove from top to bottom in correct fashion
                FusedMap.backup.removeObject(entry.getKey(), entry.getValue(), entry.getValue(), 1);
                // remove old fused backup on the new successor before putting your data
                newSuccessorFusionStore.removeObject(entry.getKey(), entry.getValue(), entry.getValue(), 0);
            }
            // I am updating my own entries on new successor's backup
            HashMap<Integer, Integer> myPrimaryData = this.reqData();
            for(Map.Entry<Integer, Integer> entry: myPrimaryData.entrySet()){
                newSuccessorFusionStore.putObject(entry.getKey(), entry.getValue(), -1, 0);
            }
            // the node who detects failure sends recovered data to new successor
            newSuccessorData = newSuccessorObjectStore.doTasksForFailureRecoveryBeingNewSuccessor(recoveredData);
            for (Map.Entry<Integer, Integer> entry : newSuccessorData.entrySet()){
                // we don't have primary auxStructure with us
                // we have to add from top to bottom in correct fashion
                FusedMap.backup.putObject(entry.getKey(), entry.getValue(), -1, 1);
            }
            System.out.println("Recovery Done !! Check Data Structures !!! in " + ObjectStore.getMyEthernetIP().toString() );
            logger.info("Recovery Done !! Check Data Structures !!! in " + ObjectStore.getMyEthernetIP().toString());
        } catch (Exception e){
            e.printStackTrace();
        }
    }


    public LinkedHashMap<Integer, Integer> doTasksForFailureRecoveryBeingNewSuccessor(
            LinkedHashMap<Integer, Integer> recoveredData){
        LinkedHashMap<Integer, Integer> updatedPrimariesToSend = null;
        for(Map.Entry<Integer, Integer> entry: recoveredData.entrySet()) {
            putFusionPrimaryForIncompleteFailure(entry.getKey(), entry.getValue(),
                    ObjectStoreService.getChordSession().getSelfSuccessor().getKey());
        }
        try {
            updatedPrimariesToSend = (LinkedHashMap<Integer,Integer>) this.reqData();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return updatedPrimariesToSend;
    }


    private void putFusionPrimaryForIncompleteFailure(int key, int value, InetAddress successor){

        int oldValue = -1;
        if(primaryMap.containsKey(key)){//if key already exists, just update its value
            logger.info("Primary map contains key : " + key );
            System.out.println("Primary map contains key : " + key );
            PrimaryNode<Integer> oldNode = 	primaryMap.get(key);
            oldValue = oldNode.getValue();//the backup will need this value for update.
            oldNode.setValue(value);
        }else{
            PrimaryNode<Integer> p = new PrimaryNode<Integer>(value);
            PrimaryAuxNode<Integer> a = new PrimaryAuxNode<Integer>(p);
            p.setAuxNode(a);
            primaryMap.put(key, p);
            auxList.add(a);//adds to end of list
            logger.info("Primary map does not contain key : " + key + " ::: Adding key to primary map and auxiliary list");
            System.out.println("Primary map does not contain key : " + key + " ::: Adding key to primary map and auxiliary list");
        }

        try {
            // Update backup on Successor and Predecessor
            // ChordSession chordSession = ObjectStoreService.getChordSession();
            logger.info("Put Operation for Failure ::: Updating backup on Successor :: " + successor.toString());

            fusionStoreOperationsForSuccessor = StoreRMIUtils.getRemoteFusionStore(successor);
            fusionStoreOperationsForSuccessor.putObject(key, value, oldValue, 0);

            logger.info("add, index:" + key + " value:" + value + " oldValue:" + oldValue + " Map:" + primaryMap + " AuxList:" + auxList);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void removeOldPredBkp(int key, InetAddress prevPredIP){
        if(!primaryMap.containsKey(key))
            try {
                throw new Exception("Key "+ key+" Not Present");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        PrimaryNode<Integer> p = primaryMap.get(key);
        PrimaryAuxNode<Integer> a = (PrimaryAuxNode<Integer>)auxList.getLast();
        FusionStoreOperations fusionStoreOperationsPrevPred = StoreRMIUtils.getRemoteFusionStore(prevPredIP);
        try {
            fusionStoreOperationsPrevPred.removeObject(key, p.getValue(), a.getPrimNode().getValue(), 1);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        logger.info("rem_bkp, index:"+key+" Map:"+primaryMap+" AuxList:"+auxList);
    }


    private void removeOldPredBkpForOldSuccessor(int key, InetAddress prevSuccIP){

        logger.info("Calling removeOldPredBkpForOldSuccessor method :::: Attention :::: ");
        logger.info("I am telling this guy to update his backup ::: " + prevSuccIP);
        if(!primaryMap.containsKey(key))
            try {
                throw new Exception("Key "+ key+" Not Present");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        PrimaryNode<Integer> p = primaryMap.get(key);
        PrimaryAuxNode<Integer> a = (PrimaryAuxNode<Integer>)auxList.getLast();
        FusionStoreOperations fusionStoreOperationsPrevPred = StoreRMIUtils.getRemoteFusionStore(prevSuccIP);
        try {
            fusionStoreOperationsPrevPred.removeObject(key, p.getValue(), a.getPrimNode().getValue(), 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        logger.info("rem_bkp, index:"+key+" Map:"+primaryMap+" AuxList:"+auxList);
    }



    private void remove(int key){
        if(!primaryMap.containsKey(key))
            try {
                throw new Exception("Key "+ key+" Not Present");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        PrimaryNode<Integer> p = primaryMap.remove(key);
        PrimaryAuxNode<Integer> a = (PrimaryAuxNode<Integer>)auxList.getLast();
        //send the message to all backups
        //String msgForBackups = "remove"+ " "+key + " " + p.getValue() + " "+ a.getPrimNode().getValue()+" "+primaryId;
        //sendMsgToBackups(msgForBackups);

        /*try {
            for(int i=2; i< (numFaults+2);i++) {
                Integer num = i + numPrimaries;
                String ipAddress = "172.18.0." + num.toString();
                InetAddress ip = InetAddress.getByName(ipAddress);
                this.fusionStoreOperations = StoreRMIUtils.getRemoteFusionStore(ip);

                this.fusionStoreOperations.removeObject(key, p.getValue(), a.getPrimNode().getValue(), primaryId);
                //send the message to all backups

                //if (Util.debugFlag)
                    //System.out.println("add, index:" + key + " value:" + p.getValue() + " oldValue:" + a.getPrimNode().getValue() + " Map:" + primaryMap + " AuxList:" + auxList);
                logger.info("add, index:" + key + " value:" + p.getValue() + " oldValue:" + a.getPrimNode().getValue() + " Map:" + primaryMap + " AuxList:" + auxList);

            }
        } catch(UnknownHostException e){
            e.printStackTrace();
        } catch (RemoteException e) {
            e.printStackTrace();
        }*/

        ChordSession chordSession = ObjectStoreService.getChordSession();
        logger.info("Remove Operation ::: Updating backup on Successor :: " + chordSession.getSelfSuccessor().getKey().toString());

        try {
        fusionStoreOperationsForSuccessor = StoreRMIUtils.getRemoteFusionStore(chordSession.getSelfSuccessor().getKey());
        fusionStoreOperationsForSuccessor.removeObject(key, p.getValue(), a.getPrimNode().getValue(), 0);

        logger.info("Remove Operation ::: Updating backup on Predecessor :: " + chordSession.getSelfPredecessor().getKey().toString());
        fusionStoreOperationsForPredecessor = StoreRMIUtils.getRemoteFusionStore(chordSession.getSelfPredecessor().getKey());
        fusionStoreOperationsForPredecessor.removeObject(key, p.getValue(), a.getPrimNode().getValue(), 1);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        //shift the final aux node to the position index
        auxList.replaceNodeWithTail(p.getAuxNode());

        //if(Util.debugFlag)
        // System.out.println("rem, index:"+key+" Map:"+primaryMap+" AuxList:"+auxList);
        logger.info("rem, index:"+key+" Map:"+primaryMap+" AuxList:"+auxList);
    }


    private void removeForIncompleteJoining(int key, InetAddress newSuccessor, InetAddress newPredecessor){
        if(!primaryMap.containsKey(key))
            try {
                throw new Exception("Key "+ key+" Not Present");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        PrimaryNode<Integer> p = primaryMap.remove(key);
        PrimaryAuxNode<Integer> a = (PrimaryAuxNode<Integer>)auxList.getLast();


        ChordSession chordSession = ObjectStoreService.getChordSession();
        logger.info("Remove Operation ::: Updating backup on Successor :: " + newSuccessor.toString());

        try {
            fusionStoreOperationsForSuccessor = StoreRMIUtils.getRemoteFusionStore(newSuccessor);
            fusionStoreOperationsForSuccessor.removeObject(key, p.getValue(), a.getPrimNode().getValue(), 0);

            logger.info("Remove Operation ::: Updating backup on Predecessor :: " + newPredecessor.toString());
            fusionStoreOperationsForPredecessor = StoreRMIUtils.getRemoteFusionStore(newPredecessor);
            fusionStoreOperationsForPredecessor.removeObject(key, p.getValue(), a.getPrimNode().getValue(), 1);
        } catch (RemoteException e) {
            e.printStackTrace();
        }

        //shift the final aux node to the position index
        auxList.replaceNodeWithTail(p.getAuxNode());

        //if(Util.debugFlag)
        // System.out.println("rem, index:"+key+" Map:"+primaryMap+" AuxList:"+auxList);
        logger.info("rem, index:"+key+" Map:"+primaryMap+" AuxList:"+auxList);
    }



  /* Method for internal usage of objectStore - Not to be used by StoreClientImpl */

  @Override
  public boolean putObjects(Map<ChordID<String>, byte[]> keyValueMap) throws RemoteException {
    boolean result = true;
    for(Map.Entry<ChordID<String>, byte[]> entry : keyValueMap.entrySet()){
        this.putObject(entry.getKey(), entry.getValue());
    }



    //makeReplicas(keyValueMap);
//    for (Map.Entry<ChordID<String>, DataContainer> e : keyValueMap.entrySet()) {
//      try {
//        localStorage.put(e.getKey().getKey(), e.getValue());
//      } catch (Exception ex) {
//        ex.printStackTrace();
//      }
//      //result &= putObject(e.getKey(), e.getValue());
//    }
    return result;
  }



    @Override
    public boolean putObjectsForIncompleteJoining(Map<ChordID<String>, byte[]> keyValueMap, InetAddress newSuccessor, InetAddress
            newPredecessor) throws RemoteException {
        boolean result = true;
        for (Map.Entry<ChordID<String>, byte[]> entry : keyValueMap.entrySet()) {
            this.putObjectForIncompleteJoining(entry.getKey(), entry.getValue(), newSuccessor, newPredecessor);
        }
        return result;
    }


    @Override
    public boolean deleteObjectsForIncompleteJoining(Map<ChordID<String>, byte[]> keyValueMap, InetAddress newSuccessor, InetAddress
            newPredecessor) throws RemoteException {
        for(Map.Entry<ChordID<String>, byte[]> entry : keyValueMap.entrySet()){
            this.deleteObjectForIncompleteJoining(entry.getKey(), newSuccessor, newPredecessor);
        }
        return true;
    }

    @Override
    public LinkedHashMap<Integer, Integer> recoverFailedData() throws RemoteException {
        InetAddress selfPredecessor = ObjectStoreService.getChordSession().getSelfPredecessor().getKey();
        ObjectStoreOperations predecessorStore = StoreRMIUtils.getRemoteObjectStore(selfPredecessor);
        Vector<HashMap<Integer, Integer>> primaries = new Vector<>();
        HashMap<Integer, Integer> dataFromPrimary = predecessorStore.reqData();
        primaries.add(dataFromPrimary);

        //for all the faulty ones...just fill in dummy data for failed Successor node
        HashMap<Integer,Integer> dummy = new HashMap<Integer,Integer>();
        primaries.add(dummy);


        logger.info("Printing Primaries ::");
        for(int i = 0;i < primaries.size();i++) {
            logger.info("Primary " + i);
           for(Map.Entry<Integer, Integer> keyVal: primaries.get(i).entrySet()){
               logger.info("Key :: " + keyVal.getKey() + " Value :: " + keyVal.getValue());
           }
        }

        Vector<Integer> ownBackupData = getBackupsForPrinting(null);
        Vector<Vector<Integer>> ownIndexInfo = getIndexInfoForPrinting();


        // Recovery Process
        logger.info("Local Recovery Started ::");

        List<Integer> integerList = new ArrayList<>();
        integerList.add(1);
        integerList.add(-1);

        // conversion
        int[] erasuresArray = new int[numFaults+1];
        for(int i = 0;i < erasuresArray.length;i++)
            erasuresArray[i] = 0;

        for(int i = 0;i < integerList.size();i++)
            erasuresArray[i] = integerList.get(i);

        LinkedHashMap<Integer, Integer> orderedRecoveryData = new LinkedHashMap<>();

        int[] code	 = new int[numFaults];
        int[] data = new int[numPrimaries];

        logger.info("Primaries ::" + numPrimaries);
        logger.info("Backups   ::" + numFaults);

        for(int fusedNodeNumber =0; fusedNodeNumber < ownIndexInfo.size();++fusedNodeNumber){
            for(int backupId =0; backupId < numFaults; ++backupId){
                code[backupId]=ownBackupData.get(fusedNodeNumber);
            }
            // This condition will be false for k = 1 which is your failed successor.
            for(int k =0; k < numPrimaries; ++k){
                int keyOfPrimary = ownIndexInfo.get(fusedNodeNumber).get(k);
                if(!integerList.contains(k) && (keyOfPrimary != -1)){
                    HashMap<Integer,Integer> original =  primaries.get(k);
                    data[k] = original.get(keyOfPrimary);
                }
                else
                    data[k] = 0;
            }

            logger.info("Before Recovery Data :: ");
            for(int i = 0; i < data.length; i++) {
                logger.info(data[i]);
            }

            logger.info("Before Recovery Code :: ");
            for(int i = 0; i < code.length; i++) {
                logger.info(code[i]);
            }



            logger.info("Before Recovery erasuresArray :: ");
            for(int i = 0; i < erasuresArray.length; i++) {
                logger.info(erasuresArray[i]);
            }

            try {
                data = Fusion.getRecoveredData(code,data,erasuresArray);
                logger.info("After Recovery Data :: " + data);
                for(int i = 0; i < data.length; i++) {
                    logger.info(data[i]);
                }
            } catch (InterfaceException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            for(int k =0; k < numPrimaries; ++k){
                int keyOfPrimary = ownIndexInfo.get(fusedNodeNumber).get(k);
                if(integerList.contains(k) && (keyOfPrimary != -1)){
                    HashMap<Integer,Integer> original =  primaries.get(k);
                    original.put(keyOfPrimary,data[k]);
                    // separate logic to maintain order in LinkedHashMap
                    for(Map.Entry<Integer, Integer> keyVal : original.entrySet()){
                        if(!orderedRecoveryData.containsKey(keyVal.getKey())){
                            // add (key, value) pair to ordered map if it does not exist.
                            orderedRecoveryData.put(keyVal.getKey(), keyVal.getValue());
                        }
                    }
                }
                logger.info("Original Data For Primary Node " + k + " :: " + primaries.get(k));
            }
            logger.info("Recovered Data For Failed Node in ordered format :: ");
            for(Map.Entry<Integer, Integer> keyVal : orderedRecoveryData.entrySet()){
                logger.info("Key :: " + keyVal.getKey() + " Value :: " + keyVal.getValue());
            }
        }
        logger.info("Now reversing the LinkedHashMap ::");

        List<Integer> list = new ArrayList<>(orderedRecoveryData.keySet());

        LinkedHashMap<Integer, Integer> reversedOrderedRecoveryData = new LinkedHashMap<>();

        for( int i = list.size() -1; i >= 0 ; i --){
            reversedOrderedRecoveryData.put(list.get(i), orderedRecoveryData.get(list.get(i)));
        }
        logger.info("Reversed and Recovered Data For Failed Node in ordered format :: ");
        for(Map.Entry<Integer, Integer> keyVal : reversedOrderedRecoveryData.entrySet()){
            logger.info("Key :: " + keyVal.getKey() + " Value :: " + keyVal.getValue());
        }

        logger.info("Local Recovery Done ::");

        return reversedOrderedRecoveryData;

    }

    @Override
    public boolean deleteObjectForIncompleteJoining(ChordID<String> key, InetAddress newSuccessor, InetAddress
            newPredecessor) throws RemoteException {
        //removeReplica(key);
        this.removeForIncompleteJoining(Integer.parseInt(key.getKey()), newSuccessor, newPredecessor);
        return true;
    }


    @Override
    public HashMap<Integer, Integer> reqData() throws RemoteException{
        HashMap<Integer,Integer> data = new LinkedHashMap<Integer,Integer>();
        Set<Map.Entry<Integer,PrimaryNode<Integer>>> s= primaryMap.entrySet();
        if(!s.isEmpty()) {
            Iterator<Map.Entry<Integer, PrimaryNode<Integer>>> it = s.iterator();
            while (it.hasNext()) {
                Map.Entry<Integer, PrimaryNode<Integer>> pairs = it.next();
                data.put(pairs.getKey(), pairs.getValue().getValue());
            }
        }
        //if(Util.debugFlag)
        //    System.out.println("data sent out");
        logger.info("data sent out from primary node ::: " + primaryId);
        return data;
    }

    @Override
    public int reqSize() throws RemoteException {
        return primaryMap.size();
    }

    @Override
    public boolean ping(Vector<Integer> erasures) throws RemoteException{
        logger.info("Ping requested :::: ");
        ObjectStoreOperations successorStore = null;
        int firstOne = 0;
        for(firstOne = 0; firstOne < erasures.size(); firstOne++){
            if(erasures.get(firstOne) == 1){
                logger.info("I found entry with 1 !!");
                break;
            }
        }

            if (erasures.get(primaryId) == 1) {
                logger.info("I got my ping back :::: and my ID :: " + primaryId);
                logger.info("Calling recover from local with erasures :::: ");
                logger.info("Ping Ended :::: " + System.currentTimeMillis());
                for(Integer v: erasures){
                    logger.info(v);
                }
                recoverFromLocal(erasures);
            } else {
                erasures.set(primaryId, 1);
                if (ChordEventHandler.recoveryDone == 0) {
                    logger.info("Failed ID in else block ::: " + ChordEventHandler.failedID);
                    erasures.set(ChordEventHandler.failedID, -1);
                    successorStore = StoreRMIUtils.getRemoteObjectStore(ChordEventHandler.successorIP.getKey());
                } else {
                    successorStore = StoreRMIUtils.getRemoteObjectStore(ObjectStoreService.getChordSession().getSelfSuccessor().getKey());
                }
                logger.info("I have updated erasures and passing data to next successor");
                logger.info("Erasures ::: " + erasures);
                for(Integer v: erasures){
                    logger.info(v);
                }
                successorStore.ping(erasures);
            }
        return true;
    }

    @Override
    public boolean recoverFromRemote(LinkedHashMap<Integer, Integer> recovered, int failedID) throws RemoteException{

        logger.info("In recover from Remote :::: ");
        for(Map.Entry<Integer, Integer> entry: recovered.entrySet()){
            try {
                for(int i=2; i< (numFaults+2);i++) {
                    Integer num = i + numPrimaries;
                    String ipAddress = "172.18.0." + num.toString();
                    InetAddress ip = InetAddress.getByName(ipAddress);
                    System.out.println("In Recovery while deleting ::: Updating Backup with IP : " + ipAddress);
                    this.fusionStoreOperations = StoreRMIUtils.getRemoteFusionStore(ip);

                    logger.info("Key : " + entry.getKey() + " Old and new Value: " + entry.getValue() + " FailedID: " + failedID);
                    this.fusionStoreOperations.removeObject(entry.getKey(), entry.getValue(), entry.getValue(), failedID);
                }
                // add the (key, value) pair of failed node to my data structure
                logger.info("Putting Key : " + entry.getKey() + " Value: " + entry.getValue() + " for FailedID: " + failedID + " in my data structure !!");
                ChordID<String> putKey = new ChordID<>(entry.getKey().toString());
                putObject(putKey, serialize(entry.getValue().toString()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        ChordEventHandler.recoveryDone = 1;
        return false;
    }

    synchronized public void recoverFromLocal(Vector<Integer> erasures) throws RemoteException {

        //int[] erasuresArray = new int[numFaults+1];
        List<Integer> integerList = new ArrayList<>();
        for(int i =0; i < erasures.size(); i++){
            if(erasures.get(i) == -1)
                integerList.add(i);
        }
        integerList.add(-1); // last element

        // conversion
        int[] erasuresArray = new int[numFaults+1];
        for(int i = 0;i < erasuresArray.length;i++)
            erasuresArray[i] = 0;

        for(int i = 0;i < integerList.size();i++)
            erasuresArray[i] = integerList.get(i);

        logger.info("Erasures Array Local ::: ");
        for(int i = 0; i<erasuresArray.length; i++)
            logger.info(erasuresArray[i]);

        Vector< HashMap<Integer,Integer> > primaries = getPrimaries(erasures);
        Vector< Vector<Integer> > backups =  getBackups(erasures);
        Vector<Vector<Integer>> indexInfo = getIndexInfo();
        logger.info("Local Recovery Started ::");

        LinkedHashMap<Integer, Integer> orderedRecoveryData = new LinkedHashMap<>();

        int[] code	 = new int[numFaults];
        int[] data = new int[numPrimaries];

        for(int fusedNodeNumber =0; fusedNodeNumber < indexInfo.size();++fusedNodeNumber){
            for(int backupId =0; backupId < numFaults; ++backupId){
                code[backupId]=backups.get(backupId).get(fusedNodeNumber);
            }
            for(int k =0; k < numPrimaries; ++k){
                int keyOfPrimary = indexInfo.get(fusedNodeNumber).get(k);
                if(!integerList.contains(k) && (keyOfPrimary != -1)){
                    HashMap<Integer,Integer> original =  primaries.get(k);
                    data[k] = original.get(keyOfPrimary);
                }
                else
                    data[k] = 0;
            }

            logger.info("Before Recovery Data :: ");
            for(int i = 0; i < data.length; i++) {
                logger.info(data[i]);
            }

            logger.info("Before Recovery Code :: ");
            for(int i = 0; i < code.length; i++) {
                logger.info(code[i]);
            }

            logger.info("Before Recovery erasuresArray :: ");
            for(int i = 0; i < erasuresArray.length; i++) {
                logger.info(erasuresArray[i]);
            }

            logger.info("Before Recovery Erasures :: ");
            for(int i = 0; i < erasures.size(); i++) {
                logger.info(erasures.get(i));
            }

            try {
                data = Fusion.getRecoveredData(code,data,erasuresArray);
                logger.info("After Recovery Data :: " + data);
            } catch (InterfaceException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            for(int k =0; k < numPrimaries; ++k){
                int keyOfPrimary = indexInfo.get(fusedNodeNumber).get(k);
                if(integerList.contains(k) && (keyOfPrimary != -1)){
                    HashMap<Integer,Integer> original =  primaries.get(k);
                    original.put(keyOfPrimary,data[k]);
                    // separate logic to maintain order in LinkedHashMap
                    for(Map.Entry<Integer, Integer> keyVal : original.entrySet()){
                        if(!orderedRecoveryData.containsKey(keyVal.getKey())){
                            // add (key, value) pair to ordered map if it does not exist.
                            orderedRecoveryData.put(keyVal.getKey(), keyVal.getValue());
                        }
                    }
                }
                logger.info("Original Data For Primary Node " + k + " :: " + primaries.get(k));
            }
            logger.info("Recovered Data For Failed Node in ordered format :: ");
            for(Map.Entry<Integer, Integer> keyVal : orderedRecoveryData.entrySet()){
                logger.info("Key :: " + keyVal.getKey() + " Value :: " + keyVal.getValue());
            }
        }
        logger.info("Now reversing the LinkedHashMap ::");

        List<Integer> list = new ArrayList<>(orderedRecoveryData.keySet());

        LinkedHashMap<Integer, Integer> reversedOrderedRecoveryData = new LinkedHashMap<>();

        for( int i = list.size() -1; i >= 0 ; i --){
            reversedOrderedRecoveryData.put(list.get(i), orderedRecoveryData.get(list.get(i)));
        }
        logger.info("Reversed and Recovered Data For Failed Node in ordered format :: ");
        for(Map.Entry<Integer, Integer> keyVal : reversedOrderedRecoveryData.entrySet()){
            logger.info("Key :: " + keyVal.getKey() + " Value :: " + keyVal.getValue());
        }

        logger.info("Local Recovery Done ::");
        logger.info("Informing backups now :: ");
        // restore backup
        for(int i =0; i < erasures.size(); i++){
            if(erasures.get(i) == -1){
                logger.info("Informing responsible node for : " + i + " as it is failed");
                // Send to the predecessor of the failure
                // Integer num = i + 1;
                // String ipAddress = "172.18.0." + num.toString();
                InetAddress ip = ChordEventHandler.successorIP.getKey();
                ObjectStoreOperations store = StoreRMIUtils.getRemoteObjectStore(ip);
                store.recoverFromRemote(reversedOrderedRecoveryData, ChordEventHandler.failedID);
            }
        }

        ChordEventHandler.recoveryDone = 1;
    }

    public Vector<HashMap<Integer,Integer>> getPrimaries(Vector<Integer> erasures){
      logger.info("Inside getPrimaries ::::: ");
        Vector<HashMap<Integer,Integer>> primaries = new Vector<HashMap<Integer,Integer>>();
        for(int i =0; i < erasures.size() ; i++){
            if(erasures.get(i) == 1){
                try {
                    Integer num = i + 2;
                    String ipAddress = "172.18.0." + num.toString();
                    InetAddress ip = InetAddress.getByName(ipAddress);
                    ObjectStoreOperations store = StoreRMIUtils.getRemoteObjectStore(ip);
                    HashMap<Integer,Integer> data = store.reqData();
                    primaries.add(data);
                    logger.info("Received data from IP : " + ipAddress);
                    logger.info("Received data in Primary Call :: " + data);
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            else{//for all the faulty ones...just fill in dummy data
                HashMap<Integer,Integer> dummy = new HashMap<Integer,Integer>();
                primaries.add(dummy);
            }
        }
        return primaries;
    }

    public Vector<Vector<Integer>> getBackups(Vector<Integer> erasures){
        logger.info("Inside getBackups ::::: ");
        Vector<Vector<Integer>> backups = new Vector<Vector<Integer>>();

        for(int i = 0; i < numFaults ; i++){
            try {
                Integer num = i + numPrimaries + 2;
                String ipAddress = "172.18.0." + num.toString();
                InetAddress ip = InetAddress.getByName(ipAddress);
                FusionStoreOperations store = StoreRMIUtils.getRemoteFusionStore(ip);
                Vector<Integer> data = store.reqData();
                backups.add(data);
                logger.info("Received backup from IP : " + ipAddress);
                logger.info("Received backup in Backup Call :: " + data);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        }
        return backups;
    }

    public Vector<Vector<Integer>> getIndexInfo(){
        Vector<Vector<Integer>> indexInfo = new Vector<Vector<Integer>>();
        try {
            Integer num = numPrimaries + 2;
            String ipAddress = "172.18.0." + num.toString();
            InetAddress ip = InetAddress.getByName(ipAddress);
            FusionStoreOperations store = StoreRMIUtils.getRemoteFusionStore(ip);
            indexInfo = store.reqIndex();
            logger.info("Received indexInfo from IP : " + ipAddress);
            logger.info("Received indexInfo  :: " + indexInfo);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return indexInfo;
    }

    public Vector<Integer> getBackupsForPrinting(Vector<Integer> erasures){
        logger.info("Inside getBackups ::::: ");

        Vector<Integer> data = null;
        try {
            data = FusedMap.backup.reqData();
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        Vector<Integer> backups = new Vector<Integer>(data);
        logger.info("Received backup in Backup Call :: " + data);
        return backups;
    }


    public Vector<Vector<Integer>> getIndexInfoForPrinting(){
        Vector<Vector<Integer>> indexInfo = new Vector<Vector<Integer>>();
        try {
             indexInfo = FusedMap.backup.reqIndex();
              logger.info("Received indexInfo  :: " + indexInfo);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return indexInfo;
    }


    @Override
  public List<ChordID<String>> keySet() throws RemoteException {
    List<Integer> intList = new ArrayList<>(primaryMap.keySet());
    List<ChordID<String>> keyList = new ArrayList<>();
    for (Integer k : intList){
        keyList.add(new ChordID<>(k.toString()));
    }
    return keyList;
  }

    @Override
    public  Vector<Integer> getBackupData() throws RemoteException{
        logger.info("Get BackupData ::::: ");
        Vector<Integer>
                backupData = null;

         /*   try {
                String ipAddress = getMyEthernetIP().toString().substring(1);
                InetAddress ip = InetAddress.getByName(ipAddress);
                FusionStoreOperations store = StoreRMIUtils.getRemoteFusionStore(ip);
                Vector<Integer> data = store.reqData();
                backupData = new Vector<Integer>(data);
                logger.info("Received indexInfo from IP : " + ipAddress);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }*/
         backupData = getBackupsForPrinting(null);
        return backupData;
    }

    @Override
    public Vector<Vector<Integer>> getIndexData() throws RemoteException{
        Vector<Vector<Integer>> indexData = null;
        /*try {
            String ipAddress = getMyEthernetIP().toString().substring(1);
            InetAddress ip = InetAddress.getByName(ipAddress);
            FusionStoreOperations store = StoreRMIUtils.getRemoteFusionStore(ip);
            indexData = new Vector<>(store.reqIndex());
            logger.info("Received indexInfo from IP : " + ipAddress);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }*/
        indexData = getIndexInfoForPrinting();
        return indexData;
    }
}
