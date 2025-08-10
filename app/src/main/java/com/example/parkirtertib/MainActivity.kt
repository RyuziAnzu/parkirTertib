package com.example.parkirtertib

import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.parkirtertib.adapter.PelanggaranAdapter
import com.example.parkirtertib.databinding.ActivityMainBinding
import com.example.parkirtertib.model.PelanggaranResponse
import com.example.parkirtertib.service.ApiClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class MainActivity : AppCompatActivity() {
    private lateinit var adapter: PelanggaranAdapter
    private var currentPage = 1
    private var hasNext = true
    private var isLoading = false
    private lateinit var binding: ActivityMainBinding

    private var selectedYear: String? = null
    private var selectedMonth: String? = null
    private var selectedJenis: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = PelanggaranAdapter(mutableListOf())
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        // Scroll listener for pagination
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val visibleItemCount = layoutManager.childCount
                val totalItemCount = layoutManager.itemCount
                val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()

                if (!isLoading && hasNext) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                        && firstVisibleItemPosition >= 0
                    ) {
                        loadData()
                    }
                }
            }
        })

        loadData()
    }

    private fun loadData() {
        isLoading = true

        // Build filter JSON dynamically
        val filters = mutableMapOf<String, List<String>>()
        selectedYear?.let { filters["tahun"] = listOf(it) }
        selectedMonth?.let { filters["bulan"] = listOf(it) }
        selectedJenis?.let { filters["jenis_pelanggaran"] = listOf(it) }

        val whereParam = if (filters.isNotEmpty()) {
            // Convert to JSON: {"tahun":["2018"],"bulan":["JANUARI"],"jenis_pelanggaran":["PARKIR DI BAHU JALAN"]}
            val json = filters.entries.joinToString(",", "{", "}") { entry ->
                "\"${entry.key}\": [${entry.value.joinToString(",") { "\"$it\"" }}]"
            }
            json
        } else null

        ApiClient.instance.getPelanggaran(
            page = currentPage,
            perPage = 500,
            where = whereParam,
        ).enqueue(object :
            Callback<PelanggaranResponse> {
            override fun onResponse(
                call: Call<PelanggaranResponse>,
                response: Response<PelanggaranResponse>
            ) {
                if (response.isSuccessful) {
                    response.body()?.let { res ->
                        // Set header
                        val first = res.data.firstOrNull()
                        val countPelanggaran = res.data.sumOf { it.jumlah_pelanggaran }

                        if (first != null) {
                            binding.tvTahun.text = first.tahun
                            binding.tvJumlahPelanggaran.text = countPelanggaran.toString()
                            binding.tvBulan.text = first.bulan
                        }

                        val tahunList = res.metadata_filter.find { it.key == "tahun" }?.let { tahunFilter ->
                            tahunFilter.value.map { it.replaceFirstChar { it.uppercase() } }
                        }

                        val bulanList = res.metadata_filter.find { it.key == "bulan" }?.let { bulanFilter ->
                            bulanFilter.value.map { it.replace("_", " ").replaceFirstChar { it.uppercase() } }
                        }

                        val jenisPelanggaranList = res.metadata_filter.find { it.key == "jenis_pelanggaran" }?.let { jenisFilter ->
                            jenisFilter.value.map { it.replaceFirstChar { it.uppercase() } }
                        }

                        setupAdapterTahun(tahunList ?: emptyList())
                        setupAdapterBulan(bulanList ?: emptyList())
                        setupAdapterJenisPelanggaran(jenisPelanggaranList ?: emptyList())

                        adapter.addData(res.data)
                        hasNext = res.pagination.has_next
                        if (hasNext) currentPage++
                    }
                }
                isLoading = false
            }

            override fun onFailure(call: Call<PelanggaranResponse>, t: Throwable) {
                isLoading = false
                Toast.makeText(this@MainActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupAdapterTahun(years: List<String>) {
        val adapterTahun = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_dropdown_item_1line,
            years
        )
        val dropdown = findViewById<AutoCompleteTextView>(R.id.dropdownTahun)
        dropdown.setAdapter(adapterTahun)

        dropdown.setOnItemClickListener { _, _, position, _ ->
            selectedYear = years[position]
            currentPage = 1
            adapter.clearData()
            loadData()
        }
    }

    private fun setupAdapterBulan(months: List<String>) {
        val adapterBulan = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_dropdown_item_1line,
            months
        )
        val dropdown = findViewById<AutoCompleteTextView>(R.id.dropdownBulan)
        dropdown.setAdapter(adapterBulan)

        dropdown.setOnItemClickListener { _, _, position, _ ->
            selectedMonth = months[position]
            currentPage = 1
            adapter.clearData()
            loadData()
        }
    }

    private fun setupAdapterJenisPelanggaran(jenisPelanggaran: List<String>) {
        val adapterJenis = ArrayAdapter(
            this@MainActivity,
            android.R.layout.simple_dropdown_item_1line,
            jenisPelanggaran
        )
        val dropdown = findViewById<AutoCompleteTextView>(R.id.dropdownJenisPelanggaran)
        dropdown.setAdapter(adapterJenis)

        dropdown.setOnItemClickListener { _, _, position, _ ->
            selectedJenis = jenisPelanggaran[position]
            currentPage = 1
            adapter.clearData()
            loadData()
        }
    }
}