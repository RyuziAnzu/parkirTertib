package com.example.parkirtertib.model

// Response wrapper
data class PelanggaranResponse(
    val code: Int,
    val message: String,
    val data: List<PelanggaranData>,
    val pagination: Pagination,
    val metadata_filter: List<MetadataFilter>
)

data class PelanggaranData(
    val id: Int,
    val kode_provinsi: String,
    val nama_provinsi: String,
    val bps_kode_kabupaten_kota: String,
    val bps_nama_kabupaten_kota: String,
    val bulan: String,
    val jenis_pelanggaran: String,
    val jumlah_pelanggaran: Int,
    val satuan: String,
    val tahun: String
)

data class Pagination(
    val page: Int,
    val per_page: Int,
    val total_page: Int,
    val total_data: Int,
    val has_next: Boolean,
    val has_previous: Boolean
)

data class MetadataFilter(
    val key: String,
    val type: String,
    val value: List<String>
)