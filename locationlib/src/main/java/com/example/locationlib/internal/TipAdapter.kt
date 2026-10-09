package com.example.locationlib.internal

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.services.help.Tip

internal class TipAdapter(
    private val tips: List<Tip>,
    private val onClick: (Tip) -> Unit
) : RecyclerView.Adapter<TipAdapter.TipViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TipViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_2, parent, false)
        return TipViewHolder(view)
    }

    override fun onBindViewHolder(holder: TipViewHolder, position: Int) {
        val tip = tips[position]
        holder.title.text = tip.name ?: "未命名地点"
        val sub = listOfNotNull(tip.district, tip.address).filter { it.isNotBlank() }.distinct().joinToString("  ")
        holder.subtitle.text = if (sub.isBlank()) "点击使用此地点" else sub
        holder.title.setTextColor(Color.rgb(30, 30, 30))
        holder.subtitle.setTextColor(Color.rgb(100, 100, 100))
        holder.itemView.setPadding(18, 14, 18, 14)
        holder.itemView.setOnClickListener { onClick(tip) }
    }

    override fun getItemCount(): Int = tips.size

    class TipViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title: TextView = itemView.findViewById(android.R.id.text1)
        val subtitle: TextView = itemView.findViewById(android.R.id.text2)
    }
}
