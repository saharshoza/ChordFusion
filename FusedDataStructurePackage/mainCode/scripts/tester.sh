echo "--MAP_FUSION_SIZE_REC"
numFaults=3
numOperations=10
for ((  numPrimaries = 1 ;  numPrimaries <= 10;  numPrimaries++  ))
do
	pkill -9 java

	echo "Test1"
	for ((  i = 0 ;  i < $numFaults;  i++  ))
	do
	java FusedMap $i $numPrimaries $numFaults &
	sleep 10
	done

	echo "Test2"
	for ((  i = 0 ;  i < $numPrimaries;  i++  ))
	do
	java FusionPrimaryMap $i $numFaults &
	sleep 10
	done

	echo "Test3"
	java FusionMapClient $numPrimaries $numFaults $numOperations
	echo "Done!!!!!!!"
done
	