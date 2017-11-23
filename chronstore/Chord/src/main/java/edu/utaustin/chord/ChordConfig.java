package edu.utaustin.chord;

import java.lang.reflect.Array;
import java.net.InetAddress;
import java.util.ArrayList;

/**
 * Created by amit on 23/2/17.
 */
public class ChordConfig {

  /* Depending on CHORD_ID_MAX_BITS in each ChordID will be calculated. This is also used in finger table */
  static int CHORD_ID_MAX_BITS = 4; // 32

  /* Number of maximum entries to keep in successor list */
  static int SUCCESSOR_LIST_MAX_SIZE = 3;

  /* ArrayList of IPs of all bootstrap nodes */
  static ArrayList<InetAddress> bootstrapNodes;

  static {
    bootstrapNodes = new ArrayList<>();
    try {
      bootstrapNodes.add(InetAddress.getByName("172.18.0.2"));
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  /* RMI Registry Port */
  static int RMI_REGISTRY_PORT = 1099;

  /* Network interface to be used for communication */
  public static String NetworkInterface = "eth0";

  /* Seconds to wait before stabilization process starts */
  static int STABILIZER_INITIAL_DELAY = 2;

  /* Seconds after which stabilizer function should be called again */
  static int STABILIZER_PERIOD = 2;
}
