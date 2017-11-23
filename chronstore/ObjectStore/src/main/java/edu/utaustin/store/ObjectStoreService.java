package edu.utaustin.store;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;

import edu.utaustin.chord.ChordDriver;
import edu.utaustin.chord.ChordSession;
import edu.utaustin.fusion.FusedMap;

/**
 * Created by amit on 1/4/17.
 */
class ObjectStoreService {

  /* Reference to underlying chord node - only if client wants to join as a node */
  private static ChordSession chordSession = null;

  /* Reference to ObjectStore */
  private static ObjectStore store = null;

  static ChordSession getChordSession() {
    return chordSession;
  }

  static ObjectStore getStore() {
    return store;
  }

  private static void initRMI() {
    try {
    /* Set custom SocketFactories for handling RMI timeout */
      RMISocketFactory.setSocketFactory(new RMISocketFactory() {
        public Socket createSocket(String host, int port) throws IOException {
          int timeoutMillis = StoreConfig.RMI_TIMEOUT * 1000; /* RMI call waits for 'RMI_TIMEOUT' seconds */
          Socket socket = new Socket();
          socket.setSoTimeout(timeoutMillis);
          socket.setSoLinger(false, 0);
          socket.connect(new InetSocketAddress(host, port), timeoutMillis);
          return socket;
        }

        public ServerSocket createServerSocket(int port) throws IOException {
          return new ServerSocket(port);
        }
      });
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private static void start(int numPrimaries, int numFaults) {
    /* Initialize RMI */
    initRMI();

    System.out.println("RMI initialization done.. ObjectStore");
    /* Create object store object and export it for RMI */


    store = new ObjectStore(numPrimaries, numFaults);
    StoreRMIUtils.exportStoreObjectRMI(store);
    /* Creation objectStore before starting chord is very important
    otherwise chord upcalls with start getting NullPointerExceptions.
     */

    // Setup Fusion backup on this node
     FusedMap.start(numPrimaries, numFaults, 0);


    System.out.println("Starting chord session..");
    /* First start chord node and join network */
    chordSession = ChordDriver.getSession();
    chordSession.registerUpcall(new ChordEventHandler());
    System.out.println("chord upcall registerd");
    chordSession.join();
    System.out.println("chord network join done..");

    /* create client API object and export it for RMI */
    StoreClientAPIImpl storeClientAPI = new StoreClientAPIImpl();
    StoreRMIUtils.exportClientAPIRMI(storeClientAPI);
  }

  public static void main(String args[]) {
    int numPrimaries = Integer.parseInt(args[0]);
    int numFaults = Integer.parseInt(args[1]);
    ObjectStoreService.start(numPrimaries, numFaults);
  }
}
