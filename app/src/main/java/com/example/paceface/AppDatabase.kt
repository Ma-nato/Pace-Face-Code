//AppDatabase.kt
package com.example.paceface

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(

    entities = [
        User::class,
        History::class,
        Emotion::class,
        SpeedRule::class,
        DeviceStatus::class,
        Proximity::class,
        Badge::class,
        UserBadge::class,
        DailyAverageSpeed::class,
        HourlyAverageSpeed::class,
        HourlyEmotionPercentage::class
    ],
    version = 1, // データベースのバージョン
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun emotionDao(): EmotionDao
    abstract fun historyDao(): HistoryDao
    abstract fun speedRuleDao(): SpeedRuleDao
    abstract fun deviceStatusDao(): DeviceStatusDao
    abstract fun proximityDao(): ProximityDao
    abstract fun badgeDao(): BadgeDao
    abstract fun userBadgeDao(): UserBadgeDao
    abstract fun dailyAverageSpeedDao(): DailyAverageSpeedDao
    abstract fun hourlyAverageSpeedDao(): HourlyAverageSpeedDao
    abstract fun hourlyEmotionPercentageDao(): HourlyEmotionPercentageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "paceface_database"
                )
                    // ★ 修正: 本番環境では非推奨の破壊的マイグレーションを警告
                    // .fallbackToDestructiveMigration()
                    // 適切なマイグレーション処理を実装することを推奨します。
                    .addCallback(AppDatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class AppDatabaseCallback : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    // 感情データの更新（新しい表情 sleep_expression を確実に追加するため）
                    updateEmotionData(database)

                    // ★ 修正: ダミーデータ投入はテスト用とし、ユーザー登録機能があるため本番では不要
                    // if (database.userDao().getUserCount() == 0) {
                    //     populateWithDummyData(database)
                    // }
                    Log.d("AppDatabase", "Database opened. Dummy data population skipped for production.")
                }
            }
        }

        private suspend fun updateEmotionData(database: AppDatabase) {
            val emotionDao = database.emotionDao()
            val emotions = listOf(
                Emotion(1, "通常", "normal_expression", "平常心を保っている状態"),
                Emotion(2, "困惑", "troubled_expression", "どうしていいか分からない状態"),
                Emotion(3, "焦り", "impatient_expression", "急いでいて落ち着かない状態"),
                Emotion(4, "笑顔", "smile_expression", "楽しくて嬉しい状態"),
                Emotion(5, "悲しみ", "sad_expression", "悲しくて元気がない状態"),
                Emotion(6, "怒り", "angry_expression", "何かに腹を立てている状態"),
                Emotion(7, "睡眠", "sleep_expression", "眠っている状態"),
                Emotion(8, "ウィンク", "wink_expression", "楽しんでいる状態"),
                Emotion(9, "どや顔", "smug_expression", "自信に満ちている状態")
            )
            // OnConflictStrategy.REPLACE を使用して、既存の ID があれば更新、なければ挿入する
            emotionDao.insertAll(emotions)
        }

        // ★ 修正: ダミーデータ投入関数はテスト用として残すか、削除を推奨
        private suspend fun populateWithDummyData(database: AppDatabase) {
            val userDao = database.userDao()
            val proximityDao = database.proximityDao()
            val emotionDao = database.emotionDao()
            val badgeDao = database.badgeDao()
            val userBadgeDao = database.userBadgeDao()

            // 表情の初期データを挿入 (updateEmotionDataで実行済みのため重複を避ける)
            // emotionDao.insertAll(...)

            // Add dummy users
            userDao.insert(User(userId = 1, firebaseUid = null, email = "user1@example.com", name = "Alice", password = ""))
            userDao.insert(User(userId = 2, firebaseUid = null, email = "user2@example.com", name = "Bob", password = ""))
            userDao.insert(User(userId = 3, firebaseUid = null, email = "user3@example.com", name = "Charlie", password = ""))
            userDao.insert(User(userId = 4, firebaseUid = null, email = "user4@example.com", name = "Dave", password = ""))
            userDao.insert(User(userId = 5, firebaseUid = null, email = "user5@example.com", name = "Eve", password = ""))
            userDao.insert(User(userId = 6, firebaseUid = null, email = "user6@example.com", name = "Frank", password = ""))

            // Add dummy proximity data
            val now = System.currentTimeMillis()
            proximityDao.insert(Proximity(userId = 1, passedUserId = 2, timestamp = now - (1000 * 60 * 5), isConfirmed = false, badgeId = null, emotionId = 1, passedUserEmotionId = 4)) // 笑顔
            proximityDao.insert(Proximity(userId = 1, passedUserId = 3, timestamp = now - (1000 * 60 * 60), isConfirmed = true, badgeId = null, emotionId = 3, passedUserEmotionId = 2)) // 困惑
            proximityDao.insert(Proximity(userId = 1, passedUserId = 4, timestamp = now - (1000 * 60 * 60 * 24), isConfirmed = true, badgeId = null, emotionId = 1, passedUserEmotionId = 9)) // どや顔
            proximityDao.insert(Proximity(userId = 1, passedUserId = 5, timestamp = now - (1000 * 60 * 60 * 2), isConfirmed = false, badgeId = null, emotionId = 1, passedUserEmotionId = 8)) // ウィンク
            proximityDao.insert(Proximity(userId = 1, passedUserId = 6, timestamp = now - (1000 * 60 * 60 * 5), isConfirmed = true, badgeId = null, emotionId = 1, passedUserEmotionId = 7)) // 睡眠

            // Add dummy badges
            badgeDao.insert(Badge(badgeId = 1, name = "First Passing", description = "すれちがい合計人数\n10人達成", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 2, name = "Social Butterfly", description = "すれちがい合計人数\n50人達成", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 3, name = "Crowd Surfer", description = "すれちがい合計人数\n100人達成", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 4, name = "Busy Bee", description = "１日で10人とすれちがう", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 5, name = "Night Owl", description = "夜の時間帯（20:00～04:00）に5人とすれちがう", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 6, name = "Early Bird", description = "朝の時間帯（06:00～09:00）に5人とすれちがう", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 7, name = "Top of the World", description = "最も感情レベルの高い「喜び」のユーザーとすれちがう", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 8, name = "Mayor of the Town", description = "すれちがい合計人数\n500人達成", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 9, name = "Twin Flame", description = "同じユーザーと2回すれちがう", imageUrl = ""))
            badgeDao.insert(Badge(badgeId = 10, name = "Spreading Joy", description = "「笑顔」のユーザーと5回すれちがう", imageUrl = ""))

            // Add dummy user badges
            userBadgeDao.insert(UserBadge(userId = 1, badgeId = 1, achievedAt = now))
            userBadgeDao.insert(UserBadge(userId = 1, badgeId = 4, achievedAt = now))
        }
    }
}