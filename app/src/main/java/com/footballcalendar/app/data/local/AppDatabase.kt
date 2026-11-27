package com.footballcalendar.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MatchEntity::class,
        StandingEntity::class,
        TeamEntity::class,
        MatchDetailEntity::class,
        PlayerEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun matchDao(): MatchDao
    abstract fun standingDao(): StandingDao
    abstract fun teamDao(): TeamDao
    abstract fun matchDetailDao(): MatchDetailDao
    abstract fun playerDao(): PlayerDao

    companion object {
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE matches ADD COLUMN lotteryCode TEXT")
                db.execSQL("ALTER TABLE matches ADD COLUMN lotteryHandicap INTEGER")
                db.execSQL("ALTER TABLE matches ADD COLUMN lotteryDeadlineUtc INTEGER")
                db.execSQL("ALTER TABLE matches ADD COLUMN isFinalized INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE matches ADD COLUMN watchNightDateKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE matches ADD COLUMN localDateKey TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE teams ADD COLUMN coachPortraitUrl TEXT NOT NULL DEFAULT ''")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `match_details` (
                        `matchId` TEXT NOT NULL,
                        `fixtureId` INTEGER NOT NULL,
                        `elapsedMinutes` INTEGER,
                        `statusShort` TEXT NOT NULL,
                        `homeScore` INTEGER,
                        `awayScore` INTEGER,
                        `payloadJson` TEXT NOT NULL,
                        `syncedAtMillis` INTEGER NOT NULL,
                        PRIMARY KEY(`matchId`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `players` (
                        `id` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `zhName` TEXT NOT NULL,
                        `clubName` TEXT NOT NULL,
                        `clubZhName` TEXT NOT NULL,
                        `clubCrestUrl` TEXT NOT NULL,
                        `nationalName` TEXT NOT NULL,
                        `nationalZhName` TEXT NOT NULL,
                        `nationalCrestUrl` TEXT NOT NULL,
                        `positionZh` TEXT NOT NULL,
                        `nationalityZh` TEXT NOT NULL,
                        `shirtNumber` INTEGER NOT NULL,
                        `birthYear` INTEGER NOT NULL,
                        `portraitUrl` TEXT NOT NULL,
                        `bio` TEXT NOT NULL,
                        `competitionCode` TEXT NOT NULL,
                        `rank` INTEGER NOT NULL,
                        `goals` INTEGER NOT NULL,
                        `assists` INTEGER NOT NULL,
                        `penalties` INTEGER NOT NULL,
                        `playedMatches` INTEGER NOT NULL,
                        `isTransliterated` INTEGER NOT NULL,
                        `sortOrder` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "football_calendar.db",
            )
                .addMigrations(MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
    }
}
