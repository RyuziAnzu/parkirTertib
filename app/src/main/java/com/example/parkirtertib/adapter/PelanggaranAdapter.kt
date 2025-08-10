package com.example.parkirtertib.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.parkirtertib.databinding.ItemPelanggaranBinding
import com.example.parkirtertib.model.PelanggaranData

class PelanggaranAdapter(private val items: MutableList<PelanggaranData>) :
    RecyclerView.Adapter<PelanggaranAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPelanggaranBinding) :
        RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(item: PelanggaranData) {
            binding.tvBulan.text = item.bulan
            binding.tvJenis.text = item.jenis_pelanggaran
            binding.tvJumlah.text = item.jumlah_pelanggaran.toString()
            binding.tvTahun.text = item.tahun
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding =
            ItemPelanggaranBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) =
        holder.bind(items[position])

    override fun getItemCount(): Int = items.size

    fun addData(newItems: List<PelanggaranData>) {
        val start = items.size
        items.addAll(newItems)
        notifyItemRangeInserted(start, newItems.size)
    }

    fun clearData() {
        items.clear()
        notifyDataSetChanged()
    }
}
