package com.footballcalendar.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface FeedApiService {
    @GET
    suspend fun fetchGitHubPagesFeed(@Url url: String): GitHubFeedResponse

    @GET("v4/matches")
    suspend fun fetchFootballDataMatches(
        @Header("X-Auth-Token") apiKey: String,
        @Query("competitions") competitions: String,
        @Query("dateFrom") dateFrom: String,
        @Query("dateTo") dateTo: String,
    ): FootballDataMatchesResponse

    @GET("v4/competitions/{code}/standings")
    suspend fun fetchCompetitionStandings(
        @Header("X-Auth-Token") apiKey: String,
        @Path("code") competitionCode: String,
    ): FootballDataStandingsResponse

    @GET("v4/competitions/{code}/teams")
    suspend fun fetchCompetitionTeams(
        @Header("X-Auth-Token") apiKey: String,
        @Path("code") competitionCode: String,
    ): FootballDataTeamsResponse

    @GET("v4/competitions/{code}/scorers")
    suspend fun fetchCompetitionScorers(
        @Header("X-Auth-Token") apiKey: String,
        @Path("code") competitionCode: String,
        @Query("limit") limit: Int = 30,
    ): FootballDataScorersResponse

    @GET("v4/matches/{id}/head2head")
    suspend fun fetchFootballDataHead2Head(
        @Header("X-Auth-Token") apiKey: String,
        @Path("id") matchId: Long,
        @Query("limit") limit: Int = 15,
    ): FootballDataHead2HeadResponse

    @GET("v4/teams/{id}/matches")
    suspend fun fetchFootballDataTeamMatches(
        @Header("X-Auth-Token") apiKey: String,
        @Path("id") teamId: Int,
        @Query("status") status: String = "FINISHED",
        @Query("limit") limit: Int = 15,
    ): FootballDataTeamMatchesResponse

    @GET
    suspend fun fetchApiFootballFixtures(
        @Url url: String,
        @Header("x-apisports-key") apiKey: String,
    ): ApiFootballFixturesResponse

    @GET
    suspend fun fetchApiFootballSquad(
        @Url url: String,
        @Header("x-apisports-key") apiKey: String,
    ): ApiFootballSquadResponse

    @GET
    suspend fun fetchApiFootballTeamInfo(
        @Url url: String,
        @Header("x-apisports-key") apiKey: String,
    ): ApiFootballTeamInfoResponse

    @GET
    suspend fun fetchApiFootballCoaches(
        @Url url: String,
        @Header("x-apisports-key") apiKey: String,
    ): ApiFootballCoachesResponse

    @GET
    suspend fun fetchEspnScoreboard(
        @Url url: String,
    ): EspnScoreboardResponse

    @GET
    suspend fun fetchEspnSummary(
        @Url url: String,
    ): EspnSummaryResponse

    @GET
    suspend fun fetchEspnStandings(
        @Url url: String,
    ): EspnStandingsResponse

    @GET
    suspend fun fetchEspnTeams(
        @Url url: String,
    ): EspnTeamsResponse

    @GET
    suspend fun fetchEspnRoster(
        @Url url: String,
    ): EspnTeamRosterResponse

    @GET
    suspend fun fetchEspnTeamSchedule(
        @Url url: String,
    ): EspnTeamScheduleResponse

    @GET
    suspend fun fetchWikipediaPageImages(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "FootballCalendarApp/2.3 (https://github.com/kiki1112/football-calendar-android)",
    ): WikipediaPageImagesResponse

    @GET
    suspend fun fetchWikipediaSummary(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "FootballCalendarApp/2.3 (https://github.com/kiki1112/football-calendar-android)",
    ): WikipediaSummaryResponse

    @GET
    suspend fun fetchWikipediaLanglinks(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "FootballCalendarApp/2.4 (https://github.com/kiki1112/football-calendar-android)",
    ): WikipediaLanglinksApiResponse

    @GET
    suspend fun fetchWikidataSparql(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "FootballCalendarApp/2.4 (https://github.com/kiki1112/football-calendar-android)",
        @Header("Accept") accept: String = "application/sparql-results+json",
    ): WikidataSparqlResponse
}
