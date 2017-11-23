package edu.utaustin.fusion;


import edu.utaustin.chord.ChordDriver;
import edu.utaustin.chord.ChordSession;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.server.RMISocketFactory;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;
import java.util.Vector;
import java.util.Map.Entry;


public class FusedMap implements Serializable,FusionStoreOperations {

    private final transient static Logger logger = Logger.getLogger(FusedMap.class);
    public static FusedMap backup;

    public Vector<Map<Integer,FusedAuxNode>> auxStructure;//:( cannot create array of parameterized objects in java
	FusedNode[] tos;
	int numPrimaries, numFaults, backupId;
	private MyContainer dataStack; 
	//int fusedStructureSize = 0; 
	
	long totalUpdateTime;//total time taken for all updates received in milliseconds. 
	int numOperations=0;
	
	public FusedMap(int numPrimaries, int numFaults, int backupId, Vector<Map<Integer,FusedAuxNode>> auxStructure){
		this.auxStructure = auxStructure;  
		tos = new FusedNode[numPrimaries]; 
		this.numPrimaries = numPrimaries;
		this.numFaults = numFaults; 
		this.backupId = backupId;
		dataStack = new MyContainer();
		Fusion.initialize(numPrimaries, numFaults); 
	}
	

	public int size(){
		return dataStack.size(); 
	}
	
	public MyContainer getDataStack(){
		return dataStack; 
	}
	
	
	public Vector<Integer> dataStackinVectorFormat(){
		Vector<Integer> v = new Vector<Integer>(); 
		FusedNode node =(FusedNode) dataStack.getFirst();
		while(node != null){
			v.add(node.getCodeValue()); 
			node = (FusedNode)dataStack.getNext(node); 
		}
		return v; 	
	}
	
	public Vector<Vector<Integer>> auxStructuresInVectorFormat(){
		Vector<Vector<Integer>> v = new Vector<Vector<Integer>>(); 
		FusedNode node =(FusedNode) dataStack.getFirst();
		while(node != null){
			Vector<Integer> positions = new Vector<Integer>();
			for(int i =0; i < numPrimaries; ++i){
				FusedAuxNode aux = node.getAuxNode()[i];
				if(aux != null)	{
					positions.add(keyOf(auxStructure.get(i),aux)); 
				}
				else
					positions.add(-1); 
			}
			v.add(positions); 
			node = (FusedNode)dataStack.getNext(node); 
		}
		return v; 	
	}
	public int keyOf(Map<Integer,FusedAuxNode> map, FusedAuxNode value){
		Set<Entry<Integer,FusedAuxNode>> s= map.entrySet();
		
		Iterator<Entry<Integer,FusedAuxNode>> it = s.iterator(); 
		while (it.hasNext()) {
	        Entry<Integer,FusedAuxNode> pairs = it.next();
	        if(pairs.getValue() == value)
	        	return pairs.getKey(); 
		}
		
		return -1; 
	}
	public boolean sanityCheck(){
		int max=0;
		for(int i = 0; i < numPrimaries; ++i){
			if(max < auxStructure.get(i).size())
				max = auxStructure.get(i).size();
		}
		System.out.println("sanity: "+dataStack.size()+" "+max);

		if(dataStack.size() != max)
			try {
				throw new Exception("Backup has more nodes than max primary");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		else
			return true; 
		return false; 
	}


    /* dumps the new element at the end of the list for that primary and updates the correct position
           in the auxStructure
      */
    public boolean putObject(int key, int newElement,int oldElement, int primaryId) throws RemoteException {
        System.out.println("-------In Add, i:"+key+" v:"+newElement+ " oldElement: " + oldElement +" prim:"+primaryId);
        FusedNode nodeToUpdate;
        if(auxStructure.get(primaryId).containsKey(key)){
            nodeToUpdate = auxStructure.get(primaryId).get(key).getFusNode();
            nodeToUpdate.removeElement(oldElement, primaryId, backupId);
            nodeToUpdate.addElement(newElement, primaryId, backupId);
        }else{
            try {
                if (oldElement != -1) throw new Exception("Primary says no value with this key, backup differs");
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if(dataStack.isEmpty() ||(tos[primaryId] == dataStack.getLast()) ){
                FusedNode node = new FusedNode(numPrimaries);
                dataStack.add(node);
                tos[primaryId] = node;
                nodeToUpdate = node;
            }else
            if ((tos[primaryId] == null) && (dataStack.isEmpty() == false)){
                nodeToUpdate = (FusedNode)dataStack.getFirst();
                tos[primaryId] = nodeToUpdate;
            }else{// there is place, just update the node
                nodeToUpdate = (FusedNode)dataStack.getNext(tos[primaryId]);
                tos[primaryId] = nodeToUpdate;
            }

            logger.info("Adding to backup: " + backupId + " element : " + newElement + " with primary ID : " + primaryId);
			System.out.println("Adding to backup: " + backupId + " element : " + newElement + " with primary ID : " + primaryId);
            nodeToUpdate.addElement(newElement, primaryId,backupId);


            //create a pointer to this newly added node and insert into the index structure
            FusedAuxNode auxNode = new FusedAuxNode(nodeToUpdate);
            nodeToUpdate.setAuxNode(primaryId,auxNode);
            auxStructure.get(primaryId).put(key, auxNode);
        }
        sanityCheck();

        System.out.println("After add, data store:"+dataStack);
        System.out.println("Aux Structures:");
        for(int i=0; i < numPrimaries;++i){
            System.out.println("aux["+i+"]:"+auxStructure.get(i));
        }
        System.out.println(auxStructuresInVectorFormat());
        return true;
    }

    public boolean removeObject(int key, int elementToDelete, int finalElement, int primaryId) throws RemoteException {
        System.out.println("-------In Remove, i:"+key+" eToD:"+elementToDelete+" eFin:"+finalElement+" prim:"+primaryId);
        logger.info("-------In Remove, i:"+key+" eToD:"+elementToDelete+" eFin:"+finalElement+" prim:"+primaryId);

        FusedAuxNode auxNode = auxStructure.get(primaryId).remove(key);
        FusedNode nodeToUpdate = auxNode.getFusNode();
        if(nodeToUpdate != tos[primaryId]){//hole has been created
            nodeToUpdate.removeElement(elementToDelete,primaryId,backupId);
            nodeToUpdate.addElement(finalElement,primaryId,backupId);
            FusedAuxNode finalAux = tos[primaryId].getAuxNode()[primaryId];
            finalAux.setFusNode(nodeToUpdate);
            nodeToUpdate.setAuxNode(primaryId, finalAux);
            tos[primaryId].setAuxNode(primaryId, null);
        }

        tos[primaryId].removeElement(finalElement,primaryId,backupId);
        FusedNode finalNode = (FusedNode)tos[primaryId];
        if(finalNode.isEmpty()){//no elements in this node
            dataStack.pop();
        }
        tos[primaryId] =  (FusedNode)dataStack.getPrevious(tos[primaryId]);
        sanityCheck();
            System.out.println("After remove, data stack:"+dataStack);
            System.out.println("Aux Structures:");
            for(int i=0; i < numPrimaries;++i){
                System.out.println(auxStructure.get(i));
            }
            System.out.println(auxStructuresInVectorFormat());
            return true;
    }


    public Vector<Integer> reqData() throws RemoteException{
		logger.info("data sent out from backup node ::: " + backupId);
        return dataStackinVectorFormat();
    }

    public Vector<Vector<Integer>> reqIndex() throws RemoteException{
        return auxStructuresInVectorFormat();
    }

    public long reqTime() throws RemoteException{
        return totalUpdateTime/numOperations;
    }

    public int reqSize() throws RemoteException{
        return dataStack.size();
    }


	/* Reference to underlying chord node - only if client wants to join as a node */
	private static ChordSession chordSession = null;

	static ChordSession getChordSession() {
		return chordSession;
	}

	private static void initRMI() {
		try {
    /* Set custom SocketFactories for handling RMI timeout */
			RMISocketFactory.setSocketFactory(new RMISocketFactory() {
				public Socket createSocket(String host, int port) throws IOException {
					int timeoutMillis = FusionConfig.RMI_TIMEOUT * 1000; /* RMI call waits for 'RMI_TIMEOUT' seconds */
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

	public static void start(int numPrimaries, int numFaults, int id) {
    /* Initialize RMI */
		// initRMI();
		System.out.println("RMI initialization done.. FusedMap");

		Vector<Map<Integer,FusedAuxNode>> v = new Vector<Map<Integer,FusedAuxNode>>(numPrimaries);
		for(int i =0; i < numPrimaries; ++i){
			GenericMap<Integer,FusedAuxNode> m = new GenericMap<Integer,FusedAuxNode>();
			v.add(m);
		}

		backup = new FusedMap(numPrimaries, numFaults, id,v);
		FusionRMIUtils.exportStoreObjectRMI(backup);
		System.out.println("Backup "+ id+" started:");
		//while(true);
    /* Create object store object and export it for RMI */
	}



	public static void main(String[] args) {
    	int id = Integer.parseInt(args[0]);
    	int numPrimaries = Integer.parseInt(args[1]);
    	int numFaults = Integer.parseInt(args[2]);

    	//FusedMap.start();

    	/*Vector<Map<Integer,FusedAuxNode>> v = new Vector<Map<Integer,FusedAuxNode>>(numPrimaries);
    	for(int i =0; i < numPrimaries; ++i){
    		GenericMap<Integer,FusedAuxNode> m = new GenericMap<Integer,FusedAuxNode>();
    		v.add(m);
    	}

    	backup = new FusedMap(numPrimaries, numFaults, id,v);
        FusionRMIUtils.exportStoreObjectRMI(backup);
        System.out.println("Backup "+ id+" started:");
        while(true);*/
	}
}

