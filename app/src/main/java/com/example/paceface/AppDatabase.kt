package com.example.paceface

import android.content.Context
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
    version = 13, // Incremented version to ensure database recreation
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
                .fallbackToDestructiveMigration()
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
                    val userDao = database.userDao()
                    if (userDao.getUserCount() == 0) {
                        populateWithDummyData(database)
                    }
                }
            }
        }

        private suspend fun populateWithDummyData(database: AppDatabase) {
            val userDao = database.userDao()
            val proximityDao = database.proximityDao()
            val emotionDao = database.emotionDao()

            val emotions = listOf(
                Emotion(emotionId = 1, name = "Delighted", imageUrl = "", description = ""),
                Emotion(emotionId = 2, name = "Excited", imageUrl = "", description = ""),
                Emotion(emotionId = 3, name = "Happy", imageUrl = "", description = ""),
                Emotion(emotionId = 4, name = "Neutral", imageUrl = "", description = ""),
                Emotion(emotionId = 5, name = "Sad", imageUrl = "", description = ""),
                Emotion(emotionId = 6, name = "Angry", imageUrl = "", description = ""),
                Emotion(emotionId = 7, name = "Off", imageUrl = "", description = "")
            )
            emotionDao.insertAll(emotions)

            // Add dummy users
            userDao.insert(User(userId = 1, email = "user1@example.com", name = "Alice", password = ""))
            userDao.insert(User(userId = 2, email = "user2@example.com", name = "Bob", password = ""))
            userDao.insert(User(userId = 3, email = "user3@example.com", name = "Charlie", password = ""))

            // Add dummy proximity data
            val now = System.currentTimeMillis()
            proximityDao.insert(Proximity(userId = 1, passedUserId = 2, timestamp = now - (1000 * 60), isConfirmed = false, badgeId = null, emotionId = 1, passedUserEmotionId = 2))
            proximityDao.insert(Proximity(userId = 1, passedUserId = 3, timestamp = now - (1000 * 60 * 120), isConfirmed = true, badgeId = null, emotionId = 3, passedUserEmotionId = 4))
        }
    }
}
