//FirebaseUtils.kt
package com.example.paceface

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * メール認証の状態を確認し、認証済みであればFirestoreにユーザーデータを保存します。
 * この関数は共通ユーティリティとして定義され、必要なActivityから呼び出されます。
 *
 * @param context アプリケーションコンテキスト
 */
suspend fun saveUserDataToFirestoreAfterEmailVerification(context: Context) {
    val auth = Firebase.auth
    val db = Firebase.firestore

    val currentUser = auth.currentUser
    if (currentUser != null) {
        // ユーザー情報をリロード
        try {
            currentUser.reload().await()
        } catch (e: Exception) {
            Log.e("FirestoreSave", "ユーザー情報のリロードに失敗しました: ${e.message}")
            return
        }

        if (currentUser.isEmailVerified) {
            Log.d("FirestoreSave", "メールアドレスが認証済みです。Firestoreへのデータ保存を試みます。")

            val tempPrefs = context.getSharedPreferences("PendingRegistrations", Context.MODE_PRIVATE)
            val email = currentUser.email
            val name = tempPrefs.getString("${email}_name", null)

            if (email != null && name != null) {
                // 保存するユーザーデータ
                val userData = hashMapOf(
                    "uid" to currentUser.uid,
                    "name" to name,
                    "email" to email,
                    "registrationTimestamp" to com.google.firebase.Timestamp.now()
                )

                withContext(Dispatchers.IO) {
                    try {
                        // "users" コレクションに、ユーザーのUIDをドキュメントIDとしてデータを保存
                        db.collection("users").document(currentUser.uid)
                            .set(userData)
                            .await()
                        Log.i("FirestoreSave", "Firestoreにユーザーデータが正常に保存されました。UID: ${currentUser.uid}")

                        // データ保存後、一時保存したSharedPreferencesのデータを削除
                        with(tempPrefs.edit()) {
                            remove("${email}_name")
                            // remove("${email}_password") // UserRegistrationScreenActivity.ktで既に削除済み
                            apply()
                        }
                        Log.d("FirestoreSave", "一時保存されたユーザーデータを削除しました。")

                    } catch (e: Exception) {
                        Log.e("FirestoreSave", "Firestoreへのデータ保存に失敗しました: ${e.message}", e)
                    }
                }
            } else {
                Log.w("FirestoreSave", "SharedPreferencesからユーザー名またはメールアドレスを取得できませんでした。メール: $email, 名前: $name")
            }
        } else {
            Log.d("FirestoreSave", "メールアドレスはまだ認証されていません。Firestoreには保存しません。")
        }
    } else {
        Log.d("FirestoreSave", "ログインしているユーザーがいません。")
    }
}