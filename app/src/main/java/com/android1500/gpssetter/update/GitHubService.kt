package com.android1500.gpssetter.update

import com.android1500.gpssetter.update.GitHubRelease
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET


interface GitHubService {

    @GET("releases/latest")
    fun getReleases(): Call<GitHubRelease>

}

