package com.example.parkirtertib.service

import com.example.parkirtertib.model.PelanggaranResponse
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query


object ApiClient {
    private const val BASE_URL = "https://opendata.bandung.go.id/api/"

    val instance: PelanggaranService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(PelanggaranService::class.java)
    }
}

interface PelanggaranService {
    @GET("bigdata/dinas_perhubungan/jmlh_prs_drk_trhdp_plnggrn_prkr_d_kt_bndng")
    fun getPelanggaran(
        @Query("page") page: Int,
        @Query("per_page") perPage: Int = 500,
        @Query("sort") sort: String? = null,
        @Query("where") where: String? = null,
        @Query("search") search: String? = null
    ): Call<PelanggaranResponse>
}