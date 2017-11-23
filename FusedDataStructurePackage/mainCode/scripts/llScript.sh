#Script for running tests...
for(( retries =1; retries <= 5; retries++))
do
	echo "Trial = $retries"



	echo "--LL_NEW_FUSION_SIZE_REC"
	numFaults=3
	numOperations=500
	for ((  numPrimaries = 1 ;  numPrimaries <= 10;  numPrimaries++  ))
	do
		pkill -9 java

		for ((  i = 0 ;  i < $numFaults;  i++  ))
		do
		java FusedList $i $numPrimaries $numFaults &
		sleep 10
		done

		for ((  i = 0 ;  i < $numPrimaries;  i++  ))
		do
		java PrimaryList $i $numFaults &
		sleep 10
		done

		java ListClient  $numPrimaries $numFaults $numOperations
	done

	echo"--LL_NEW_FUSION_UPDATE"
	numFaults=1
	numOperations=50
	numPrimaries=1
	for (( opFactor =1 ; opFactor<=10;  ++opFactor  ))

	do
		pkill -9 java
		for ((  i = 0 ;  i < $numFaults;  i++  ))
		do
		java FusedList $i $numPrimaries $numFaults &
		sleep 10
		done

		for ((  i = 0 ;  i < $numPrimaries;  i++  ))
		do
		java PrimaryList $i $numFaults &
		sleep 10
		done
		java ListClient  $numPrimaries $numFaults $[numOperations*opFactor]
	done

	echo "--LL_OLD_FUSION_SIZE_REC"
	numFaults=3
	numOperations=500
	for ((  numPrimaries = 1 ;  numPrimaries <= 10;  numPrimaries++  ))
	do
		pkill -9 java

		for ((  i = 0 ;  i < $numFaults;  i++  ))
		do
		java OldFusedLinkedList $i $numPrimaries $numFaults &
		sleep 10
		done

		for ((  i = 0 ;  i < $numPrimaries;  i++  ))
		do
		java OriginalLinkedList $i &
		sleep 10
		done

		java FusionLinkedListOracle $numPrimaries $numFaults $numOperations
	done

	echo"--LL_OLD_FUSION_UPDATE"
	numFaults=1
	numOperations=50
	numPrimaries=1
	for (( opFactor =1 ; opFactor<=10;  ++opFactor  ))

	do
		pkill -9 java

		for ((  i = 0 ;  i < $numFaults;  i++  ))
		do
		java OldFusedLinkedList $i $numPrimaries $numFaults &
		sleep 10
		done

		for ((  i = 0 ;  i < $numPrimaries;  i++  ))
		do
		java OriginalLinkedList $i &
		sleep 10
		done
		java FusionLinkedListOracle  $numPrimaries $numFaults $[numOperations*opFactor]
	done

	echo "--LL_REP_SIZE_REC"
	numFaults=3
	numOperations=500
	for ((  numPrimaries = 1 ;  numPrimaries <= 10;  numPrimaries++  ))
	do
		pkill -9 java

		for ((  i = 0 ;  i <= ($numFaults \* $numPrimaries);  i++  ))
		do
		java ReplicatedLinkedList $i &
		sleep 10
		done

		for ((  i = 0 ;  i < $numPrimaries;  i++  ))
		do
		java OriginalLinkedList $i & 
		sleep 10
		done

		java ReplicationLinkedListOracle  $numPrimaries $numFaults $numOperations 
	done

	echo "--LL_REP_UPDATE"
	numFaults=1
	numOperations=50
	numPrimaries=1
	for ((  opFactor = 1 ;  opFactor <= 10;  opFactor++  ))
	do

		pkill -9 java

		for ((  i = 0 ;  i <= ($numFaults \* $numPrimaries);  i++  ))
		do
		java ReplicatedLinkedList $i &
		sleep 10
		done

		for ((  i = 0 ;  i < $numPrimaries;  i++  ))
		do
		java OriginalLinkedList $i &
		sleep 10
		done

		java ReplicationLinkedListOracle  $numPrimaries $numFaults $[numOperations*opFactor]
	done


done
