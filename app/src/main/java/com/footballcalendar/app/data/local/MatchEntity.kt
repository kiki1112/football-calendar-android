package com.footballcalendar.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.footballcalendar.app.model.MatchItem
import com.footballcalendar.app.model.MatchStatus

@Entity(tableName = "matches")
data class MatchEntity(
    @PrimaryKey val id: String,
    val competitionCode: String,
    val competitionName: String,
    val competitionZhName: String,
    val matchday: Int?,
    val stage: String,
    val utcDate: String,
    val status: String,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamShortName: String,
    val homeTeamZhName: String,
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamShortName: String,
    val awayTeamZhName: String,
    val homeScore: Int?,
    val awayScore: Int?,
    val venue: String,
    val lastUpdatedUtc: String,
) {
    fun toDomain(): MatchItem = MatchItem(
        id = id,
        competitionCode = competitionCode,
        competitionName = competitionName,
        competitionZhName = competitionZhName,
        matchday = matchday,
        stage = stage,
        utcDate = utcDate,
        status = MatchStatus.fromRaw(status),
        homeTeamId = homeTeamId,
        homeTeamName = homeTeamName,
        homeTeamShortName = homeTeamShortName,
        homeTeamZhName = homeTeamZhName,
        awayTeamId = awayTeamId,
        awayTeamName = awayTeamName,
        awayTeamShortName = awayTeamShortName,
        awayTeamZhName = awayTeamZhName,
        homeScore = homeScore,
        awayScore = awayScore,
        venue = venue,
        lastUpdatedUtc = lastUpdatedUtc,
    )

    companion object {
        fun fromDomain(item: MatchItem): MatchEntity = MatchEntity(
            id = item.id,
            competitionCode = item.competitionCode,
            competitionName = item.competitionName,
            competitionZhName = item.competitionZhName,
            matchday = item.matchday,
            stage = item.stage,
            utcDate = item.utcDate,
            status = item.status.name,
            homeTeamId = item.homeTeamId,
            homeTeamName = item.homeTeamName,
            homeTeamShortName = item.homeTeamShortName,
            homeTeamZhName = item.homeTeamZhName,
            awayTeamId = item.awayTeamId,
            awayTeamName = item.awayTeamName,
            awayTeamShortName = item.awayTeamShortName,
            awayTeamZhName = item.awayTeamZhName,
            homeScore = item.homeScore,
            awayScore = item.awayScore,
            venue = item.venue,
            lastUpdatedUtc = item.lastUpdatedUtc,
        )
    }
}
