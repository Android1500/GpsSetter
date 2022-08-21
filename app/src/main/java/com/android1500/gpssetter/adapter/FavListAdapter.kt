package com.android1500.gpssetter.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android1500.gpssetter.R
import com.android1500.gpssetter.room.User

class FavListAdapter(private val listener: ClickListener) : RecyclerView.Adapter<FavListAdapter.ViewHolder>() {


    private var mFavorites = arrayListOf<User>()
    fun addAllFav(favorites: ArrayList<User>){
        mFavorites = favorites
        mFavorites.sortBy {it.address}

    }
   inner class ViewHolder(view: View,private val listener: ClickListener): RecyclerView.ViewHolder(view) {

        private val address: TextView = view.findViewById(R.id.address)
        private val del: ImageView = itemView.findViewById(R.id.del)

        fun bind(favorite: User){
            address.text = favorite.address
            del.setOnClickListener {
                listener.onItemDelete(favorite)
            }

            address.setOnClickListener {
                listener.onItemClick(favorite)
            }

        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.fav_items, parent, false)
        return ViewHolder(view,listener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(mFavorites[position])

    }

    override fun getItemCount() = mFavorites.size


    interface ClickListener {
        fun onItemClick(item: User?)
        fun onItemDelete(item: User?)
    }


}