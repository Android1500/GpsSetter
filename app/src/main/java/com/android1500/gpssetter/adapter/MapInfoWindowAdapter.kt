package com.android1500.gpssetter.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import com.android1500.gpssetter.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import com.google.android.material.textview.MaterialTextView


class MapInfoWindowAdapter(private val layoutInflater: LayoutInflater) :
    GoogleMap.InfoWindowAdapter{

    override fun getInfoWindow(p0: Marker): View? {
        return null
    }

    @SuppressLint("InflateParams")
    override fun getInfoContents(p0: Marker): View? {
        val view = layoutInflater.inflate(R.layout.map_info_content, null)
        val markerTitle = view.findViewById<MaterialTextView>(R.id.marker_title)
        markerTitle.text = p0.title
        return view
    }



}