package edu.utaustin.chord;

import org.apache.log4j.Logger;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.rmi.RemoteException;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Created by amit on 1/4/17.
 */
public class ChordSession {

  ChordNode node;

  /* Keep all loggers transient so that they are not passed over RMI call */
  private final transient static Logger logger = Logger.getLogger(ChordSession.class);

  ChordSession() {
    node = new ChordNode(getMyEthernetIP());
  }

  public boolean join() {
    boolean result = true;

    /* Export this object so that it is available for RMI calls */
    ChordRMIUtils.exportNodeObjectRMI(node);

    try {
      logger.info("Node:" + node.selfChordID + "Joining network..");
      node.join(ChordConfig.bootstrapNodes);
    } catch (RemoteException e) {
      result = false;
      e.printStackTrace();
    }

    Thread stabilizer = new Thread(new Runnable() {
      @Override
      public void run() {
        while(true) {
          try {
            node.stabilize();
            node.fixFingers();
            node.printNode();
            Thread.sleep(ChordConfig.STABILIZER_PERIOD * 1000);
          } catch (Exception e) {
            e.printStackTrace();
          }
        }
      }
    });
    stabilizer.start();


    /* Schedule stabilize & fixFingers method to run after every 1 second */
//
//    ScheduledExecutorService stabilizer = Executors.newScheduledThreadPool(1);
//    stabilizer.scheduleAtFixedRate(() -> {
//      try {
//        node.stabilize();
//        node.fixFingers();
//        node.printNode();
//      } catch (RemoteException e) {
//        e.printStackTrace();
//      }
//    }, ChordConfig.STABILIZER_INITIAL_DELAY, ChordConfig.STABILIZER_PERIOD, TimeUnit.SECONDS);

    return result;
  }

  public ChordID<InetAddress> getResponsibleNodeID(ChordID<String> key) {
    ChordID<InetAddress> responsibleNodeID = null;
    try {
      responsibleNodeID = node.getSuccessor(node.selfChordID, key);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return responsibleNodeID;
  }

  public ChordID<InetAddress> getSelfSuccessor() {
    ChordID<InetAddress> successor = null;
    try {
      successor = node.getSuccessor(node.selfChordID);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return successor;
  }

  public ChordID<InetAddress> getSelfPredecessor() {
    ChordID<InetAddress> predecessor = null;
    try {
      predecessor = node.getPredecessor(node.selfChordID);
    } catch (RemoteException e) {
      e.printStackTrace();
    }
    return predecessor;
  }

  public void registerUpcall(UpcallEventHandler handler) {
    node.setUpcallHandler(handler);
  }

  public ChordID<InetAddress> getChordNodeID() {
    return node.selfChordID;
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
}
