package com.example.nesmat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class RadioAdapter(
    private val channels: List<RadioChannel>,
    private val onItemClick: (Int) -> Unit
) : RecyclerView.Adapter<RadioAdapter.RadioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RadioViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return RadioViewHolder(view)
    }

    override fun onBindViewHolder(holder: RadioViewHolder, position: Int) {
        val channel = channels[position]
        holder.channelName.text = channel.name
        holder.itemView.setOnClickListener { onItemClick(position) }
    }

    override fun getItemCount(): Int = channels.size

    inner class RadioViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val channelName: TextView = itemView.findViewById(android.R.id.text1)
    }
}