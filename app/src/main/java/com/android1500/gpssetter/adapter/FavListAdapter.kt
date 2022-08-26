package com.android1500.gpssetter.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android1500.gpssetter.R
import com.android1500.gpssetter.room.Favourite

class FavListAdapter(
    ) : RecyclerView.Adapter<FavListAdapter.ViewHolder>() {

    private var mFavorites = arrayListOf<Favourite>()
    private lateinit var mListener :ClickListener

    fun setOnClickListener(listener: ClickListener){
       mListener = listener
    }


    fun addAllFav(favorites: ArrayList<Favourite>){
        mFavorites = favorites
        mFavorites.sortBy {it.address}

    }
   inner class ViewHolder(view: View,private val listener: ClickListener): RecyclerView.ViewHolder(view) {

        private val address: TextView = view.findViewById(R.id.address)
        private val delete: ImageView = itemView.findViewById(R.id.del)


        fun bind(favorite: Favourite){

            address.text = favorite.address
            delete.setOnClickListener {
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
        return ViewHolder(view,mListener)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(mFavorites[position])

    }

    override fun getItemCount() = mFavorites.size


    interface ClickListener {
        fun onItemClick(item: Favourite?)
        fun onItemDelete(item: Favourite?)
    }


}