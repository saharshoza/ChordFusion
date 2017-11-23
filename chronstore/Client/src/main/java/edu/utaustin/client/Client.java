package edu.utaustin.client;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.*;

import edu.utaustin.store.KeyMetadata;
import org.apache.log4j.Logger;

import edu.utaustin.chord.ChordID;
import edu.utaustin.chord.SHA256Hash;
import edu.utaustin.store.DataContainer;
import edu.utaustin.store.StoreClientAPI;
import edu.utaustin.store.StoreConfig;

/**
 * Created by amit on 24/3/17.
 */
public class Client {

  private final transient static Logger logger = Logger.getLogger(Client.class);

  /* All keys-values used for get & put will be taken & verified from below file */
  private String inputDataPath = "/root/chronstore/Resources/tests/input_keys_new";
  private String ipListPath = "/root/chronstore/Resources/tests/node_ip_list";
  private String backupIpListPath = "/root/chronstore/Resources/tests/backup_ip_list";

  HashMap<String, String> keyValueMap;
  ArrayList<ChordID<InetAddress>> ipList;
  ArrayList<ChordID<InetAddress>> backupIpList;
  HashMap<ChordID<InetAddress>, List<KeyMetadata>> nodeDataMap;

  public Client(int nKeys) {
    keyValueMap = new HashMap<>();
    try (FileReader fr = new FileReader(inputDataPath);
         BufferedReader br = new BufferedReader(fr);) {
      String line;
      while ((line = br.readLine()) != null && keyValueMap.size() < nKeys) {
        String data[] = line.split("\\$");
        keyValueMap.put(data[1], data[2]);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    ipList = new ArrayList<>();
    try {
      FileReader fr = new FileReader(ipListPath);
      BufferedReader br = new BufferedReader(fr);
      String line;
      while ((line = br.readLine()) != null) {
        ipList.add(new ChordID<>(InetAddress.getByName(line)));
      }
      Collections.sort(ipList);
      logger.debug("Active nodes: " + ipList);
    } catch (Exception e) {
      e.printStackTrace();
    }

      backupIpList = new ArrayList<>();
      try {
          FileReader fr = new FileReader(backupIpListPath);
          BufferedReader br = new BufferedReader(fr);
          String line;
          while ((line = br.readLine()) != null) {
              backupIpList.add(new ChordID<>(InetAddress.getByName(line)));
          }
          Collections.sort(backupIpList);
          logger.debug("Active backup nodes: " + backupIpList);
      } catch (Exception e) {
          e.printStackTrace();
      }

  }

  public void putKeys() {
    StoreClientAPI handle = ClientRMIUtils.getRemoteClient();
    Double average_time = 0D;
    try {
      for (Map.Entry<String, String> e : keyValueMap.entrySet()) {
        long before_time = System.currentTimeMillis();
        logger.info("Putting key : " + e.getKey() + " with value : " + e.getValue());
        handle.put(e.getKey(), e.getValue());
        long after_time = System.currentTimeMillis();
        //logger.debug("Time Taken to put key : " + (after_time - before_time));
        average_time += after_time - before_time;
        }
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    logger.info("Average Time Taken to put key : " + (average_time / keyValueMap.size()) + " ms For "
                + keyValueMap.size() + " keys");
    System.out.println("Average Time Taken to put key : " + (average_time / keyValueMap.size()) + " ms For "
                       + keyValueMap.size() + " keys");

  }

  public void getKeys() {
    boolean result = true;
    int failureCount = 0;
    StoreClientAPI handle = ClientRMIUtils.getRemoteClient();
    Double average_time = 0D;
    try {
      for (Map.Entry<String, String> e : keyValueMap.entrySet()) {
        long before_time = System.currentTimeMillis();
        logger.info("Getting key : " + e.getKey());
        String retrievedValue = (String) handle.get(e.getKey());
        long after_time = System.currentTimeMillis();
        if (retrievedValue == null || !retrievedValue.equals(e.getValue())) {
          logger.error("Value for Key: " + e.getKey() + " Could not be found.");
          result = false;
          failureCount++;
        }
        //logger.debug("Time Taken to get key : " + (after_time - before_time));
        average_time += after_time - before_time;
      }
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    logger.info("Average Time Taken to get key : " + (average_time / keyValueMap.size()) + " ms For "
                + keyValueMap.size() + " keys");
    if (result) {
      System.out.println("Successfully retrieved all keys!");
    } else {
      System.out.println("Error - Count not get " + failureCount + " Keys");
    }
    System.out.println("Average Time Taken to get key : " + (average_time / keyValueMap.size()) + " ms For "
                       + keyValueMap.size() + " keys");
  }

  private ChordID<InetAddress> getResponsibleNode(ChordID<String> key, int replicaNumber) {
    int i = 0;
    while (i < ipList.size() && ipList.get(i).compareTo(key) < 0) i++;
    if (i == ipList.size())
      i = 0;
    i = (i + replicaNumber - 1) % ipList.size();
    return ipList.get(i);
  }

  private boolean verifyKeyLocation(ChordID<String> chordKey) {
    ChordID<InetAddress> responsibleNode = null;
    KeyMetadata serachKey = new KeyMetadata(chordKey);
    boolean result = true;
    for (int i = 1; i <= StoreConfig.REPLICATION_COUNT; i++) {
      responsibleNode = getResponsibleNode(chordKey, i);
      serachKey.setReplicaNumber(i);
      int index = nodeDataMap.get(responsibleNode).indexOf(serachKey);
      if (index == -1) {
        logger.error("Key: " + chordKey  + " (Replica: "+ i + ")  Not found on " + responsibleNode);
        result = false;
      }
    }
    return result;
  }

  public void testKeys() {
    boolean result = true;
    nodeDataMap = new HashMap<>();
    for (ChordID<InetAddress> nodeID : ipList) {
      StoreClientAPI handle = ClientRMIUtils.getRemoteClient(nodeID.getKey());
      try {
        List<KeyMetadata> allKeys = handle.keySet();
        nodeDataMap.put(nodeID, allKeys);
        System.out.println("Node: " + nodeID + " has " + allKeys.size() + " keys.");
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    /* Iterate over all keys to verify each key one by one */
    for (Map.Entry<String, String> e : keyValueMap.entrySet()) {
      ChordID<String> chordKey = new ChordID<>(e.getKey());
      result &= verifyKeyLocation(chordKey);
    }
    if (result) {
      System.out.println("Success: Key location verification");
    } else {
      System.out.println("Error: Key location verification");
    }
  }

    public void listKeys() {
        nodeDataMap = new HashMap<>();
        for (ChordID<InetAddress> nodeID : ipList) {
            StoreClientAPI handle = ClientRMIUtils.getRemoteClient(nodeID.getKey());
            try {
                List<KeyMetadata> allKeys = handle.keySet();
                nodeDataMap.put(nodeID, allKeys);
                System.out.println("Node: " + nodeID + " has " + allKeys.size() + " keys.");
                Map<Integer, Integer> dataMap = handle.dataSet();
                for(Map.Entry<Integer, Integer> entry: dataMap.entrySet()){
                    System.out.println("Key: " + entry.getKey() + " Value : " + entry.getValue());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }


            // System.out.println("BackupList Size  :: " + backupIpList.size());
            System.out.println("DataVector :: ");

                System.out.println("NodeID  :: " + nodeID.getKey());
                try {
                    Vector<Integer> dataVector = handle.reqBackupData();
                    System.out.println(dataVector);
                } catch (Exception e) {
                    e.printStackTrace();
                }


            System.out.println("Index Structure :: ");
                try {
                    Vector<Vector<Integer>> dataIndex = handle.reqBackuoIndex();
                    System.out.println(dataIndex);
                } catch (Exception e) {
                    e.printStackTrace();
                }

        }
    }

  public static void main(String args[]) {
    System.out.println("Arg1: " + args[0] + " Arg2:" + args[1]);
    if (args.length != 2) {
      System.out.println("Usage: java Client <PUT/GET/TEST> <NKEYS>");
      System.exit(0);
    }
    Client c = new Client(Integer.parseInt(args[1]));
    switch (args[0]) {
      case "PUT":
        c.putKeys();
        c.listKeys();
        break;
      case "GET":
        c.getKeys();
        c.listKeys();
        break;
      case "TEST":
        c.testKeys();
        c.listKeys();
        break;
      case "LIST":
        c.listKeys();
        break;
      default: {
        c.putKeys();
        c.testKeys();
        c.listKeys();
        break;
      }
    }
  }
}
