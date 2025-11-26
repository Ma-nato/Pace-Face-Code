package com.example.paceface

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

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
    version = 5, // Incremented version from 4 to 5
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
                .fallbackToDestructiveMigration() // On version change, recreate the DB
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
