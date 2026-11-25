package com.footballcalendar.app.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
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
}
