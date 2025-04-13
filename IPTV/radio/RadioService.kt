package com.example.nesmat

import retrofit2.http.GET
import retrofit2.http.Url

interface RadioService {
    @GET
    suspend fun getChannels(@Url url: String): List<RadioChannel>
}