/* Examples/reed_sol_01.c
 * James S. Plank

Jerasure - A C/C++ Library for a Variety of Reed-Solomon and RAID-6 Erasure Coding Techniques
Copright (C) 2007 James S. Plank

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.
  
This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.
 
You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
  
James S. Plank
Department of Electrical Engineering and Computer Science
University of Tennessee 
Knoxville, TN 37996
plank@cs.utk.edu
*/

/*
 * $Revision: 1.2 $
 * $Date: 2008/08/19 17:41:40 $
 */
    

/*
	revised by S. Simmerman
	2/25/08  
*/
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "jerasure.h"
#include "reed_sol.h"
#include "edu_utaustin_fusion_JavaReedInterface.h"   // this header file was generated by javah
#define talloc(type, num) (type *) malloc(sizeof(type)*(num))

void update(int numStructures, int numBackups, int w, int *coding_int,int old_int_value, int new_int_value,int position);
int update_single_code(int numStructures, int numBackups, int w, int code_int,int code_index, int old_int_value, int new_int_value,int position,int mat_element);

void recover(int numStructures, int numBackups, int w, int *coding_int,int *data_int,int *erasures_int);
void encode(int numStructures, int numBackups, int w, int *coding_int,int *data_int);

void add_codeWords(int numBackups, int* coding_dest_int,int* coding_source_int);
int add_codes(int coding_dest_int,int coding_source_int);
static void print_data(int w, int psize, 
		char *data);  
//static void print_data_and_coding(int k, int m, int w, int psize, 
//		char **data, char **coding); 

//jni call
JNIEXPORT jobjectArray JNICALL Java_JavaReedInterface_genRsMatrix
(JNIEnv *env, jclass cls, jint numStructures , jint numBackups, jint w)
{


     int *matrix= reed_sol_vandermonde_coding_matrix(numStructures, numBackups,w);
     
     jobjectArray result;
     int i;
 //    jerasure_print_matrix(matrix, numStructures, numBackups,w);

     jclass intArrCls = (*env)->FindClass(env, "[I");
     if (intArrCls == NULL) {
         return NULL; /* exception thrown */
     }
     //allocate space for the result...this only allocates for the first dimension
     result = (*env)->NewObjectArray(env, numStructures, intArrCls,
                                     NULL);
     if (result == NULL) {
         return NULL; /* out of memory error thrown */
     }
     for (i = 0; i < numStructures; i++) {
         jint tmp[256];  /* make sure it is large enough! */
         int j;
         jintArray iarr = (*env)->NewIntArray(env, numBackups);
         if (iarr == NULL) {
             return NULL; /* out of memory error thrown */
         }
         for (j = 0; j < numBackups; j++) {
             tmp[j] = matrix[i*numBackups+j];
         }
         (*env)->SetIntArrayRegion(env, iarr, 0, numBackups, tmp);
         (*env)->SetObjectArrayElement(env, result, i, iarr);
         (*env)->DeleteLocalRef(env, iarr);
     }
//     printf("%d\n",matrix[4*numStructures+5]);
     return result;
 }


JNIEXPORT jintArray JNICALL Java_JavaReedInterface_update
  (JNIEnv *env, jobject obj, jint numStructures , jint numBackups, jint w, jintArray codes, jint old_int_value, jint new_int_value, jint position){
	
	//copy the elements of the codes into a C array.	
	jint* coding_int = (int*)malloc(numBackups*sizeof(int));
	(*env)->GetIntArrayRegion(env, codes, 0, numBackups, coding_int);
	
	//get the new codes corresponding to the updated value
	update(numStructures, numBackups, w, coding_int,old_int_value, new_int_value,position);
	
	//convert the native array to jnitarray and return it.
	jintArray updated_codes = (*env)-> NewIntArray(env,numBackups);
	(*env)->SetIntArrayRegion(env,updated_codes,0,numBackups,coding_int);
	return updated_codes;

}


JNIEXPORT jint JNICALL Java_JavaReedInterface_updateSingleCode (JNIEnv *env, jobject obj, jint numStructures , jint numBackups, jint w, jint code,
jint code_index,jint old_int_value, jint new_int_value, jint position,jint mat_element){
	//get the new codes corresponding to the updated value
	return update_single_code(numStructures, numBackups, w, code,code_index,old_int_value, new_int_value,position,mat_element);
}


JNIEXPORT jintArray JNICALL Java_JavaReedInterface_recover
  (JNIEnv *env, jobject obj, jint numStructures , jint numBackups, jint w, jintArray codes, jintArray data, jintArray erasures){
 	//copy the input elements into C arrays.	
	jint* coding_int = (int*)malloc(numBackups*sizeof(int));
	(*env)->GetIntArrayRegion(env, codes, 0, numBackups, coding_int);
	
	jint* data_int = (int*)malloc(numStructures*sizeof(int));
	(*env)->GetIntArrayRegion(env, data, 0, numStructures, data_int);
	
	jint* erasures_int = (int*)malloc((numBackups+1)*sizeof(int));//the number of erasures = maxFaults+1. maxFaults is obv = numBackups.
	(*env)->GetIntArrayRegion(env, erasures, 0, (numBackups+1), erasures_int);

	
	//get the new codes corresponding to the updated value
	recover(numStructures, numBackups, w, coding_int,data_int,erasures_int);
	
	//convert the native array to jnitarray and return it.
	jintArray recovered_data = (*env)-> NewIntArray(env,numStructures);
	(*env)->SetIntArrayRegion(env,recovered_data,0,numStructures,data_int);
	return recovered_data;
}

//given data words, returns the code words
JNIEXPORT jintArray JNICALL Java_JavaReedInterface_encode
  (JNIEnv *env, jobject obj, jint numStructures , jint numBackups, jint w, jintArray codes, jintArray data){
 	//copy the input elements into C arrays.	
	jint* coding_int = (int*)malloc(numBackups*sizeof(int));
	(*env)->GetIntArrayRegion(env, codes, 0, numBackups, coding_int);
	
	jint* data_int = (int*)malloc(numStructures*sizeof(int));
	(*env)->GetIntArrayRegion(env, data, 0, numStructures, data_int);
	

	//get the new codes corresponding to the updated value
	encode(numStructures, numBackups, w, coding_int,data_int);
	
	//convert the native array to jnitarray and return it.
	jintArray generated_code = (*env)-> NewIntArray(env,numBackups);
	(*env)->SetIntArrayRegion(env,generated_code,0,numBackups,coding_int);
	return generated_code;
}


//returns the galois sum of the code_words
JNIEXPORT jintArray JNICALL Java_JavaReedInterface_addCodeWords
  (JNIEnv *env, jobject obj, jint numBackups,jintArray codes_destination, jintArray codes_source){
	jint* codes_destination_int = (int*)malloc(numBackups*sizeof(int));
	(*env)->GetIntArrayRegion(env, codes_destination, 0, numBackups, codes_destination_int);

	jint* codes_source_int = (int*)malloc(numBackups*sizeof(int));
	(*env)->GetIntArrayRegion(env, codes_source, 0, numBackups, codes_source_int);

	add_codeWords(numBackups,codes_destination_int,codes_source_int);

	//convert the native array to jnitarray and return it.
	jintArray code_sum = (*env)-> NewIntArray(env,numBackups);
	(*env)->SetIntArrayRegion(env,code_sum,0,numBackups,codes_destination_int);
	return code_sum;

}
JNIEXPORT jint JNICALL Java_JavaReedInterface_addCodes
(JNIEnv *env, jobject obj, jint code_destination, jint code_source){
	return add_codes(code_destination,code_source);
}


//start of cauchy routines...

JNIEXPORT jintArray JNICALL Java_JavaReedInterface_genCauchyMatrix
  (JNIEnv *env, jobject obj, jint k, jint m, jint w){
  int i,j; 
  int* matrix = talloc(int, m*k);
  for (i = 0; i < m; i++) {
    for (j = 0; j < k; j++) {
      matrix[i*k+j] = galois_single_divide(1, i ^ (m + j), w);
    }
  }
  int* bitmatrix = jerasure_matrix_to_bitmatrix(k, m, w, matrix);
  
  jintArray mat = (*env)-> NewIntArray(env,k*w*m*w);
  (*env)->SetIntArrayRegion(env,mat,0,k*w*m*w,bitmatrix);
  return mat;
}

JNIEXPORT jintArray JNICALL Java_JavaReedInterface_cauchyUpdate
  (JNIEnv * env, jobject obj, jintArray diff, jintArray old_code, jintArray matrix_element, jint
psize, jint w){
//	printf("--------------------------\n"); 
	jint* c_element = talloc(int, w*w);
	(*env)->GetIntArrayRegion(env, matrix_element, 0, w*w, c_element);
  //      printf("pMatrix\n");	
//	jerasure_print_matrix(c_element, w,w,w);        
	//the only reason why i am passing around ints rather than longs is because it is crashing
	//in evil JNI	
	jint* c_code = talloc(int, w);
	(*env)->GetIntArrayRegion(env, old_code, 0, w, c_code);

	jint* c_diff = talloc(int, w);
	(*env)->GetIntArrayRegion(env, diff, 0, w, c_diff);

	char* char_diff = talloc(char,psize*w);
	char* char_code = talloc(char,psize*w);
	int i; 
	for(i=0; i < psize*w; ++i){
		char_diff[i]=0; char_code[i]=0; 
	}
	int j; 
	memcpy(char_diff,c_diff,w*sizeof(int));
//	printf("Data Difference\n");
//	print_data(w,psize,char_diff);

	memcpy(char_code,c_code,w*sizeof(int));
//	printf("Old Code\n");
//	print_data(w,psize,char_code);

	jerasure_cauchy_update(char_diff,char_code,c_element, w, psize,psize*w);      
//	printf("New Code\n");
//	print_data(w,psize,char_code);
	int* updated_code = talloc(int,w); 
        for (j = 0; j < w; j++) {
		updated_code[j]= *(int *)(char_code+j*psize); 
//		printf("%d\n", updated_code[j]); 
   	} 

	jintArray j_code = (*env)-> NewIntArray(env,w);
	(*env)->SetIntArrayRegion(env,j_code,0,w,updated_code);
	return j_code;
}
JNIEXPORT jintArray JNICALL Java_JavaReedInterface_cauchyRecover
  (JNIEnv *env, jobject obj, jint k, jint m, jint w, jint psize, jintArray matrix, jintArray code,
jintArray data, jintArray erasures){
	int i,j; 
	jint* c_code_array = talloc(int, m*w);
//	printf("%d %d\n",m,w);
	(*env)->GetIntArrayRegion(env, code, 0, m*w, c_code_array);
	for(i=0; i <m*w;i=i++){
		int temp = c_code_array[i]; 
//		printf("%d ",temp); 
	}
//	printf("\n"); 
	char** coding = talloc(char *, m);
//	printf("Code \n");
	for (i = 0; i < m; i++) {
		coding[i] = talloc(char, psize*w);
		 int* temp = talloc(int,w); 
		 for (j = 0; j < w; j++) {
			 temp[j]= c_code_array[i*w+j]; 
		 }
		 memcpy(coding[i],temp,w*psize);
//		 print_data(w,psize, coding[i]);
	}

//	printf("Data \n");
	jint* c_data_array = talloc(int, k*w);
	(*env)->GetIntArrayRegion(env, data, 0, k*w, c_data_array);
	 char** data_blocks= talloc(char *, k);
         for (i = 0; i < k; i++) {
		 data_blocks[i] = talloc(char, psize*w);
		 int* temp = talloc(int,w); 
		 for (j = 0; j < w; j++) {
			 temp[j]= c_data_array[i*w+j]; 
		 }
		 memcpy(data_blocks[i],temp,w*psize);
//		 print_data(w,psize, data_blocks[i]);
	}
//	print_data_and_coding(k, m, w, psize,data_blocks,coding); 

	jint* c_matrix = talloc(int, k*w*m*w);
	(*env)->GetIntArrayRegion(env, matrix, 0, k*w*m*w, c_matrix);


  	jint* c_erasures = talloc(int, (m+1));
	(*env)->GetIntArrayRegion(env, erasures, 0, m+1, c_erasures);

//	printf("Before\n");
//	for(i=0; i < k;++i){
//		print_data(w,psize,data_blocks[i]);
//	}
//	for(i=0; i < m;++i){
//		print_data(w,psize,coding[i]);
//	}
//
//	print_data_and_coding(k, m, w, psize,data_blocks,coding); 
	jerasure_bitmatrix_decode(k, m, w, c_matrix, 0, c_erasures, data_blocks, coding, 
		  w*psize, psize);

//	printf("After\n");
//	for(i=0; i < k;++i){
//		print_data(w,psize,data_blocks[i]);
//	}
//	for(i=0; i < m;++i){
//		print_data(w,psize,coding[i]);
//	}

	int* recovered_data = talloc(int,k*w); 

	for(i=0; i < k; ++i){
		char* temp= talloc(char, psize*w);
		memcpy(temp,data_blocks[i],w*psize ); 
		//print_data(w,psize,temp);
		for (j = 0; j < w; j++) {
			recovered_data[w*i+j]= *(int*)(temp +j*psize); 
		//	printf("\n%d olo", recovered_data[w*i+j]);
		} 
	}

	jintArray j_data = (*env)-> NewIntArray(env,k*w);
	(*env)->SetIntArrayRegion(env,j_data,0,k*w,recovered_data);
	return j_data;

}

//
static void print_data(int w, int psize, 
		char *data) 
{
	int i, j, x, n, sp;
	long l;
		for (j = 0; j < w; j++) {
				printf("    p%-2d:", j);
				for(x = 0; x < psize; x +=4) {
					memcpy(&l, data+j*psize+x, sizeof(long));
					printf(" %08lx", l);
				}
				printf("\n");
			}
}


static void print_data_and_coding(int k, int m, int w, int size, 
		char **data, char **coding) 
{
  int i, j, x;
  int n, sp;
  long l;

  if(k > m) n = k;
  else n = m;
  sp = size * 2 + size/(w/8) + 8;

  printf("%-*sCoding\n", sp, "Data");
  for(i = 0; i < n; i++) {
	  if(i < k) {
		  printf("D%-2d:", i);
		  for(j=0;j< size; j+=(w/8)) { 
			  printf(" ");
			  for(x=0;x < w/8;x++){
				printf("%02x", (unsigned char)data[i][j+x]);
				//printf("%d", data[i][j+x]);
			  }
		  }
		  printf("    ");
	  }
	  else printf("%*s", sp, "");
	  if(i < m) {
		  printf("C%-2d:", i);
		  for(j=0;j< size; j+=(w/8)) { 
			  printf(" ");
			  for(x=0;x < w/8;x++){
				printf("%02x", (unsigned char)coding[i][j+x]);
			  }
		  }
	  }
	  printf("\n");
  }
	printf("\n");
}
/*bharath : update function
Given a set of codes and the value which has been updated, it returns the updated code corresponding to the new value.
*/
void update(int numStructures, int numBackups, int w, int *coding_int,int old_int_value, int new_int_value,int position){
	
	int *matrix;
	char **coding,**data;
	long old_long_value,new_long_value;
	char* old_value;
	char* new_value;
	
	//generate the matrix....this is bad...we shud not be generating it everytime...but that is for later
	matrix = reed_sol_vandermonde_coding_matrix(numStructures ,numBackups, w);

	//convert the codes in integer form to char **
	long l = 0;
	int i;
	coding = talloc(char *, numBackups);
	for (i = 0; i < numBackups; i++) {
		coding[i] = talloc(char, sizeof(long));
		l = (long)coding_int[i];
		memcpy(coding[i], &l, sizeof(long));
	}
	
	//convert the old and new values to char*
	old_value = talloc(char, sizeof(long));
	old_long_value = (long)old_int_value;
	memcpy(old_value, &old_long_value, sizeof(long));
	
	new_value = talloc(char, sizeof(long));
	new_long_value = (long)new_int_value;
	memcpy(new_value, &new_long_value, sizeof(long));
	
	//create dummy data
	data = talloc(char *, numStructures);
	l =0;
	for (i = 0; i < numStructures; i++) {
		data[i] = talloc(char, sizeof(long));
		memcpy(data[i], &l, sizeof(long));
	}
	//printf("\n");

	//printf("Before update:\n");
	//print_data_and_coding(numStructures, numBackups, w, sizeof(long), data, coding);	
	
	memcpy(data[position], &new_long_value, sizeof(long));
	
	//updating....
	jerasure_update(numStructures,numBackups,w,matrix,coding,sizeof(long),old_value, new_value, position);
	//printf("after update:\n");
	//print_data_and_coding(numStructures, numBackups, w, sizeof(long), data, coding);	
	
	//update the source code array...
	for(i =0; i < numBackups;++i){
		coding_int[i] = *(long *)coding[i];
		//printf("%d ",coding_int[i]);
	}
	//printf("\n");
	
}

/*bharath : update function
Given a code value and the value which has been updated, it returns the updated code corresponding to the new value.
*/

int update_single_code(int numStructures, int numBackups, int w, int code_int,int code_index, int old_int_value, int new_int_value,int position, int mat_element){
	
//	int *matrix;
	char *code,**data;
	long old_long_value,new_long_value;
	char* old_value;
	char* new_value;
	
	//generate the matrix....this is bad...we shud not be generating it everytime...but that is for later
//	matrix = reed_sol_vandermonde_coding_matrix(numStructures ,numBackups, w);

	//convert the code in integer form to char *
	long l = 0;
	int i;
	code = talloc(char, sizeof(long));
	l = (long)code_int;
	memcpy(code, &l, sizeof(long));
	
	//convert the old and new values to char*
	old_value = talloc(char, sizeof(long));
	old_long_value = (long)old_int_value;
	memcpy(old_value, &old_long_value, sizeof(long));
	
	new_value = talloc(char, sizeof(long));
	new_long_value = (long)new_int_value;
	memcpy(new_value, &new_long_value, sizeof(long));

	//updating....
	jerasure_update_single_code(numStructures,numBackups,w,mat_element,code,code_index, sizeof(long),old_value, new_value, position);

	//update the source code///
	code_int = *(long *)code;
	
	return code_int;
}

/*bharath : recover function
Given code,data,erasures..returns the revcovered data .
*/
void recover(int numStructures, int numBackups, int w, int *coding_int,int *data_int,int *erasures_int){

	int *matrix;
	char **coding,**data;
	
	//generate the matrix....this is bad...we shud not be generating it everytime...but that is for later
	matrix = reed_sol_vandermonde_coding_matrix(numStructures ,numBackups, w);

	//convert the code,data in integer form to char **
	long l = 0;
	int i;
	coding = talloc(char *, numBackups);
	for (i = 0; i < numBackups; i++) {
		coding[i] = talloc(char, sizeof(long));
		l = (long)coding_int[i];
		memcpy(coding[i], &l, sizeof(long));
	}
	
	data = talloc(char *, numStructures);
	for (i = 0; i < numStructures; i++) {
		data[i] = talloc(char, sizeof(long));
		l = (long)data_int[i];
		memcpy(data[i], &l, sizeof(long));
	}

	//printf("Before recovery:\n");
	//print_data_and_coding(numStructures, numBackups, w, sizeof(long), data, coding);	
	//printf("Erasures:");
	//printf("\n");
	//recovery....
	jerasure_matrix_decode(numStructures, numBackups, w, matrix, 1, erasures_int, data, coding, sizeof(long));

	//printf("After recovery:\n");
	//print_data_and_coding(numStructures, numBackups, w, sizeof(long), data, coding);	

	//update the data array...
	for(i =0; i < numStructures ;++i){
		data_int[i] = *(long *)data[i];
	}
	//printf("\n");

}


void add_codeWords(int numBackups, int* coding_dest_int,int* coding_source_int){

	char **code_dest,**code_source;
	
	//convert the arrays in integer form to char **
	long l = 0;
	int i;
	code_dest = talloc(char *, numBackups);
	for (i = 0; i < numBackups; i++) {
		code_dest[i] = talloc(char, sizeof(long));
		l = (long)coding_dest_int[i];
		memcpy(code_dest[i], &l, sizeof(long));
	}
	
	code_source = talloc(char *, numBackups);
	for (i = 0; i < numBackups; i++) {
		code_source[i] = talloc(char, sizeof(long));
		l = (long)coding_source_int[i];
		memcpy(code_source[i], &l, sizeof(long));
	}

	//add....
	jerasure_array_add(numBackups,code_dest,code_source,sizeof(long));

	//update the data array...
	for(i =0; i < numBackups ;++i){
		coding_dest_int[i] = *(long *)code_dest[i];
	}
	//printf("\n");

}

int add_codes(int coding_dest_int,int coding_source_int){

	char *code_dest,*code_source;
	
	//convert the arrays in integer form to char **
	long l = 0;
	code_dest = talloc(char, sizeof(long));
	l = (long)coding_dest_int;
	memcpy(code_dest, &l, sizeof(long));

	
	code_source = talloc(char, sizeof(long));
	l = (long)coding_source_int;
	memcpy(code_source, &l, sizeof(long));


	//add....
	jerasure_code_add(code_dest,code_source,sizeof(long));

	//update the data array...
	coding_dest_int = *(long *)code_dest;

	return coding_dest_int;
}

void encode(int numStructures, int numBackups, int w, int *coding_int,int *data_int){

	int *matrix;
	char **coding,**data;
	//printf("In encode function");
	//generate the matrix....this is bad...we shud not be generating it everytime...but that is for later
	matrix = reed_sol_vandermonde_coding_matrix(numStructures ,numBackups, w);

	//convert the code,data in integer form to char **
	long l = 0;
	int i;
	coding = talloc(char *, numBackups);
	
	for (i = 0; i < numBackups; i++) {
		coding[i] = talloc(char, sizeof(long));
		l = (long)coding_int[i];
		memcpy(coding[i], &l, sizeof(long));
	}
	
	data = talloc(char *, numStructures);
	for (i = 0; i < numStructures; i++) {
		data[i] = talloc(char, sizeof(long));
		l = (long)data_int[i];
		memcpy(data[i], &l, sizeof(long));
	}

	//printf("Before recovery:\n");
	//print_data_and_coding(numStructures, numBackups, w, sizeof(long), data, coding);	
	//printf("Erasures:");
	/*
	for(i =0; i <= numBackups;++i){
		//printf("%d ",erasures_int[i]);
	}
	*/
	//printf("\n");

	//encode....
	//printf("Just Before encode");
	jerasure_matrix_encode(numStructures, numBackups, w, matrix, data, coding, sizeof(long));
	//printf("Just after encode");
	//printf("After recovery:\n");
	//print_data_and_coding(numStructures, numBackups, w, sizeof(long), data, coding);	

	//update the codes array...
	for(i =0; i < numBackups ;++i){
		coding_int[i] = *(long *)coding[i];
	}
	//printf("\n");

}


