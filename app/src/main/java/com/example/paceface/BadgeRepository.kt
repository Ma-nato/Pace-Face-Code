package com.example.paceface

import android.content.Context

class BadgeRepository(context: Context) {

    private val appDatabase = AppDatabase.getDatabase(context)
    private val proximityDao = appDatabase.proximityDao()
    private val userBadgeDao = appDatabase.userBadgeDao()

    suspend fun checkAndAwardBadges(userId: Int, passedUserId: Int) {
        val proximityCount = proximityDao.getProximityCountForUser(userId)
        val userBadges = userBadgeDao.getBadgesForUser(userId).map { it.badgeId }.toSet()

        // すれちがい合計人数10人達成
        if (proximityCount >= 10 && !userBadges.contains(1)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 1, achievedAt = System.currentTimeMillis()))
        }

        // すれちがい合計人数50人達成
        if (proximityCount >= 50 && !userBadges.contains(2)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 2, achievedAt = System.currentTimeMillis()))
        }

        // すれちがい合計人数100人達成
        if (proximityCount >= 100 && !userBadges.contains(3)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 3, achievedAt = System.currentTimeMillis()))
        }

        // 1日で10人とすれちがう
        val todaysProximityCount = proximityDao.getTodaysUniqueProximityCount(userId)
        if (todaysProximityCount >= 10 && !userBadges.contains(4)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 4, achievedAt = System.currentTimeMillis()))
        }

        // 夜の時間帯（22:00～06:00）に5人とすれちがう
        val nightProximityCount = proximityDao.getNightProximityCountForUser(userId)
        if (nightProximityCount >= 5 && !userBadges.contains(5)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 5, achievedAt = System.currentTimeMillis()))
        }

        // 朝の時間帯（06:00～09:00）に5人とすれちがう
        val morningProximityCount = proximityDao.getMorningProximityCountForUser(userId)
        if (morningProximityCount >= 5 && !userBadges.contains(6)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 6, achievedAt = System.currentTimeMillis()))
        }

        // 5種類すべての感情のユーザーとすれちがう
        val encounteredEmotions = proximityDao.getEncounteredEmotionIds(userId)
        if (encounteredEmotions.size >= 5 && !userBadges.contains(7)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 7, achievedAt = System.currentTimeMillis()))
        }

        // 最も感情レベルの高い「Delighted」のユーザーとすれちがう
        val hasEncounteredDelighted = proximityDao.hasEncounteredEmotion(userId, 1) // EmotionId for Delighted is 1
        if (hasEncounteredDelighted && !userBadges.contains(8)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 8, achievedAt = System.currentTimeMillis()))
        }

        // すれちがい合計人数500人達成
        if (proximityCount >= 500 && !userBadges.contains(9)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 9, achievedAt = System.currentTimeMillis()))
        }

        // 同じユーザーと3回すれちがう
        val proximityWithUserCount = proximityDao.getProximityCountWithUser(userId, passedUserId)
        if (proximityWithUserCount >= 3 && !userBadges.contains(10)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 10, achievedAt = System.currentTimeMillis()))
        }

        // 「Happy」なユーザーと5回すれちがう
        val happyProximityCount = proximityDao.getProximityCountWithEmotion(userId, 3) // EmotionId for Happy is 3
        if (happyProximityCount >= 5 && !userBadges.contains(11)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 11, achievedAt = System.currentTimeMillis()))
        }

        // 6種類の感情のユーザーとすれちがう
        if (encounteredEmotions.size >= 6 && !userBadges.contains(12)) {
            userBadgeDao.insert(UserBadge(userId = userId, badgeId = 12, achievedAt = System.currentTimeMillis()))
        }
    }
}
