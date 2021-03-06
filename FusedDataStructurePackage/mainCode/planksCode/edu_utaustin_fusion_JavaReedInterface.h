/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class edu_utaustin_fusion_JavaReedInterface */

#ifndef _Included_edu_utaustin_fusion_JavaReedInterface
#define _Included_edu_utaustin_fusion_JavaReedInterface
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:     edu_utaustin_fusion_JavaReedInterface
 * Method:    update
 * Signature: (III[IIII)[I
 */
JNIEXPORT jintArray JNICALL Java_edu_utaustin_fusion_JavaReedInterface_update
  (JNIEnv *, jobject, jint, jint, jint, jintArray, jint, jint, jint);

/*
 * Class:     edu_utaustin_fusion_JavaReedInterface
 * Method:    updateSingleCode
 * Signature: (IIIIIIIII)I
 */
JNIEXPORT jint JNICALL Java_edu_utaustin_fusion_JavaReedInterface_updateSingleCode
  (JNIEnv *, jobject, jint, jint, jint, jint, jint, jint, jint, jint, jint);

/*
 * Class:     edu_utaustin_fusion_JavaReedInterface
 * Method:    recover
 * Signature: (III[I[I[I)[I
 */
JNIEXPORT jintArray JNICALL Java_edu_utaustin_fusion_JavaReedInterface_recover
  (JNIEnv *, jobject, jint, jint, jint, jintArray, jintArray, jintArray);

/*
 * Class:     edu_utaustin_fusion_JavaReedInterface
 * Method:    addCodeWords
 * Signature: (I[I[I)[I
 */
JNIEXPORT jintArray JNICALL Java_edu_utaustin_fusion_JavaReedInterface_addCodeWords
  (JNIEnv *, jobject, jint, jintArray, jintArray);

/*
 * Class:     edu_utaustin_fusion_JavaReedInterface
 * Method:    addCodes
 * Signature: (II)I
 */
JNIEXPORT jint JNICALL Java_edu_utaustin_fusion_JavaReedInterface_addCodes
  (JNIEnv *, jobject, jint, jint);

/*
 * Class:     edu_utaustin_fusion_JavaReedInterface
 * Method:    encode
 * Signature: (III[I[I)[I
 */
JNIEXPORT jintArray JNICALL Java_edu_utaustin_fusion_JavaReedInterface_encode
  (JNIEnv *, jobject, jint, jint, jint, jintArray, jintArray);

/*
 * Class:     edu_utaustin_fusion_JavaReedInterface
 * Method:    genRsMatrix
 * Signature: (III)[[I
 */
JNIEXPORT jobjectArray JNICALL Java_edu_utaustin_fusion_JavaReedInterface_genRsMatrix
  (JNIEnv *, jclass, jint, jint, jint);

#ifdef __cplusplus
}
#endif
#endif
