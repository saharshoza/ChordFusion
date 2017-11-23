package edu.utaustin.store;

import edu.utaustin.chord.ChordID;

import java.io.Serializable;

/**
 * Holds the key and metadata about that key in database. Currently, we use this
 * mostly to hold the replica number of this key. However, other values can also be
 * added into this object. This object will be stored along with every key.
 */
public class KeyMetadata implements Serializable {
    ChordID<String> key; // the key whose metadata this is
    int replicaNumber; // replica number of this key

    public KeyMetadata(ChordID<String> key) {
        this.key = key;
    }

    public int getReplicaNumber() {
        return replicaNumber;
    }

    public void setReplicaNumber(int replicaNumber) {
        this.replicaNumber = replicaNumber;
    }

    public ChordID<String> getKey() {
        return key;
    }

    @Override
    public int hashCode() {
        return key.hashCode() * 17 + replicaNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || !(o instanceof KeyMetadata))
            return false;
        return ((KeyMetadata) o).key.equals(this.key) &&
                ((KeyMetadata) o).replicaNumber == this.replicaNumber;
    }
}
