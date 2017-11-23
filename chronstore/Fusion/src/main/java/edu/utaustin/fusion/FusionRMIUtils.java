package edu.utaustin.fusion;

import org.apache.log4j.Logger;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;


/**
 * Created by amit on 24/3/17.
 */
public class FusionRMIUtils {

  /* Keep all loggers transient so that they are not passed over RMI call */
  private final transient static Logger logger = Logger.getLogger(FusionRMIUtils.class);

  private static Registry getRegistry() {
    Registry registry = null;

    try {
      registry = LocateRegistry.getRegistry();
      registry.list();
    } catch (RemoteException e) {
      try {
        registry = LocateRegistry.createRegistry(FusionConfig.RMI_REGISTRY_PORT);
      } catch (RemoteException nestedException) {
        nestedException.printStackTrace();
        logger.error("Unable to get/create RMI registry. Exiting");
      }
    }
    return registry;
  }

  public static boolean exportStoreObjectRMI(FusedMap store) {
    System.out.println("Exporting fusion store RMI..");
    Registry registry = getRegistry();
    try {
      FusionStoreOperations storeOps = (FusionStoreOperations) UnicastRemoteObject.exportObject(store, 0);
      registry.rebind("FusionStoreOperations", storeOps);
      String entries[] = registry.list();
      logger.debug("Registry entries:");
      for (String binding : entries)
        logger.debug(binding);
    } catch (Exception e) {
      e.printStackTrace();
      return false;
    }
    return true;
  }


  static FusionStoreOperations getRemoteFusionStore(InetAddress ip) {
    /* INetAddress toString adds a '/' at the beginning remove that */
    String serverURL = "rmi://" + ip.toString().substring(1) + "/FusionStoreOperations";
    FusionStoreOperations store = null;
    try {
      store = (FusionStoreOperations) Naming.lookup(serverURL);
      if (store == null) {
        throw new RemoteException();
      }
    } catch (Exception e) {
      /* TODO: Need to do all RMI exception handling
      Why did RMI execution fail
      Node down? Node failure? Network partitioning? New node arrival?
       */
      e.printStackTrace();
      logger.error("Unable to get Remote object for " + ip + " Trying one more time...");
      /* Try one more time before giving up */
      try {
        store = (FusionStoreOperations) Naming.lookup(serverURL);
      } catch (Exception nestedException) {
        nestedException.printStackTrace();
        store = null;
      }
      e.printStackTrace();
    }
    return store;
  }

}
