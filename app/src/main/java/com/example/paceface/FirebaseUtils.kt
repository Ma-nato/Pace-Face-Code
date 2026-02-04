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
 * メール認証の状態を確認し、認証済みであればFirestoreおよびローカルDBにユーザーデータを保存します。
 * この関数は共通ユーティリティとして定義され、必要なActivityから呼び出されます。
 *
 * @param context アプリケーションコンテキスト
 */
suspend fun saveUserDataToFirestoreAfterEmailVerification(context: Context) {
    val auth = Firebase.auth
    val db = Firebase.firestore

    val currentUser = auth.currentUser
    if (currentUser != null) {
        // ユーザー情報をリロードして最新の認証状態を取得
        try {
            currentUser.reload().await()
        } catch (e: Exception) {
            Log.e("FirestoreSave", "ユーザー情報のリロードに失敗しました: ${e.message}")
            return
        }

        if (currentUser.isEmailVerified) {
            Log.d("FirestoreSave", "メールアドレスが認証済みです。データの保存を開始します。")

            val tempPrefs = context.getSharedPreferences("PendingRegistrations", Context.MODE_PRIVATE)
            val email = currentUser.email
            val name = tempPrefs.getString("${email}_name", null)
            val hashedPassword = tempPrefs.getString("${email}_password", "") ?: ""

            if (email != null && name != null) {
                withContext(Dispatchers.IO) {
                    try {
                        // 1. Firestore (クラウド) への保存
                        val userData = hashMapOf(
                            "uid" to currentUser.uid,
                            "name" to name,
                            "email" to email,
                            "isEmailVerified" to true,
                            "registrationTimestamp" to com.google.firebase.Timestamp.now()
                        )

                        db.collection("users").document(currentUser.uid)
                            .set(userData)
                            .await()
                        Log.i("FirestoreSave", "Firestoreにユーザーデータが正常に保存されました。UID: ${currentUser.uid}")

                        // 2. ローカルDB (Room) への保存
                        // アプリ内の多くの機能がローカルDBのuserIdに依存しているため、ここでの登録が必須です
                        val appDatabase = AppDatabase.getDatabase(context)

                        // 既に同じFirebase UIDのユーザーがいないか確認
                        val existingUser = appDatabase.userDao().getUserByFirebaseUid(currentUser.uid)
                        val localUserId: Int

                        if (existingUser == null) {
                            val newLocalUser = User(
                                firebaseUid = currentUser.uid,
                                name = name,
                                email = email,
                                password = hashedPassword,
                                isEmailVerified = true
                            )
                            localUserId = appDatabase.userDao().insert(newLocalUser).toInt()
                            Log.i("FirestoreSave", "ローカルDBに新規ユーザーを登録しました。LocalID: $localUserId")
                        } else {
                            localUserId = existingUser.userId
                            Log.i("FirestoreSave", "ローカルDBに既存ユーザーが見つかりました。LocalID: $localUserId")
                        }

                        // 3. ログイン状態の管理 (SharedPreferences)
                        // 認証直後にアプリを使い始められるように、ログイン中のIDを保存します
                        val sharedPrefs = context.getSharedPreferences("AppPrefs", Context.MODE_PRIVATE)
                        with(sharedPrefs.edit()) {
                            putInt("LOGGED_IN_USER_ID", localUserId)
                            putString("LOGGED_IN_FIREBASE_UID", currentUser.uid)
                            apply()
                        }

                        // 4. 一時保存データの削除
                        with(tempPrefs.edit()) {
                            remove("${email}_name")
                            remove("${email}_password")
                            apply()
                        }
                        Log.d("FirestoreSave", "一時保存された登録用データを削除しました。")

                    } catch (e: Exception) {
                        Log.e("FirestoreSave", "データ保存プロセス中にエラーが発生しました: ${e.message}", e)
                    }
                }
            } else {
                Log.w("FirestoreSave", "SharedPreferencesからユーザー名またはメールアドレスを取得できませんでした。メール: $email, 名前: $name")
            }
        } else {
            Log.d("FirestoreSave", "メールアドレスはまだ認証されていません。保存をスキップします。")
        }
    } else {
        Log.d("FirestoreSave", "ログインしているユーザーがいません。")
    }
}