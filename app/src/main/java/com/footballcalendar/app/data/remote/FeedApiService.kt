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

    @GET
    suspend fun fetchWikipediaSummary(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "FootballCalendarApp/2.0 (https://github.com/kiki1112/football-calendar-android)",
    ): WikipediaSummaryResponse

    @GET
    suspend fun fetchWikipediaLanglinks(
        @Url url: String,
        @Header("User-Agent") userAgent: String = "FootballCalendarApp/2.0 (https://github.com/kiki1112/football-calendar-android)",
    ): WikipediaLanglinksApiResponse
}
