package edu.utaustin.store;

/**
 * Created by amit on 1/4/17.
 */
public class StoreConfig {

  /* RMI Registry Port */
  static int RMI_REGISTRY_PORT = 1099;

  /* Number of replicas to maintain */
  public static int REPLICATION_COUNT = 0;

  /* RMI Call timeout - Seconds to wait before call is considered as failed */
  static int RMI_TIMEOUT = 1;

  /* metadata directory paths - this directory will store indexes and metadata */
  public final static String META_DIR = "/tmp/indexes/";

  /* Data directory path - this directory will store actual data */
  public final static String DATA_DIR = "/tmp/data/";
}
