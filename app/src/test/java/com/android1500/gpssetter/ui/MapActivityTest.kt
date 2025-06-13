package com.android1500.gpssetter.ui

import com.google.android.gms.maps.model.LatLng
import org.junit.Assert.*
import org.junit.Test

class MapActivityTest {

    // Helper function to calculate distance (Haversine formula)
    private fun calculateDistance(point1: LatLng, point2: LatLng): Float {
        val earthRadius = 6371000.0 // meters
        val lat1Rad = Math.toRadians(point1.latitude)
        val lon1Rad = Math.toRadians(point1.longitude)
        val lat2Rad = Math.toRadians(point2.latitude)
        val lon2Rad = Math.toRadians(point2.longitude)

        val dLat = lat2Rad - lat1Rad
        val dLon = lon2Rad - lon1Rad

        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))

        return (earthRadius * c).toFloat()
    }

    @Test
    fun testGenerateRandomLocation_withinRadius() {
        // MapActivity instance is not strictly needed if generateRandomLocation is moved
        // to a companion object or if we can call it on a default/mocked instance.
        // For now, assuming we can instantiate or access it.
        // If MapActivity has heavy dependencies, this might need Robolectric or refactoring.
        // However, since generateRandomLocation is now internal and doesn't rely on
        // complex MapActivity state, we can call it directly if we can instantiate MapActivity
        // or make generateRandomLocation a top-level internal fun or in a companion object.
        // For simplicity with current structure, let's assume we can make a dummy instance
        // or call it in a way that doesn't require full Activity lifecycle.
        // The function itself is pure math and random generation.
        // val activity = MapActivity() // No longer needed after moving to companion object

        val center = LatLng(10.0, 20.0)
        val radiusMeters = 1000.0f
        val iterations = 100

        for (i in 0 until iterations) {
            // Call the function from the companion object
            val randomPoint = MapActivity.generateRandomLocation(center, radiusMeters)

            // Assert that latitude and longitude are valid numbers
            assertFalse("Latitude should not be NaN", randomPoint.latitude.isNaN())
            assertFalse("Longitude should not be NaN", randomPoint.longitude.isNaN())
            assertTrue("Latitude should be finite", randomPoint.latitude.isFinite())
            assertTrue("Longitude should be finite", randomPoint.longitude.isFinite())

            val distance = calculateDistance(center, randomPoint)

            // Check if the point is within the radius (add a small epsilon for floating point inaccuracies)
            assertTrue(
                "Point ($randomPoint) is outside radius $radiusMeters. Distance was $distance",
                distance <= radiusMeters + 0.01f // Epsilon for precision
            )
        }
    }

    @Test
    fun testFavoritesToJson_emptyList() {
        val favorites = emptyList<com.android1500.gpssetter.room.Favourite>()
        val jsonString = MapActivity.favoritesToJson(favorites)
        assertEquals("[]", jsonString.trim()) // Should be an empty JSON array
    }

    @Test
    fun testFavoritesToJson_singleFavorite() {
        val favorites = listOf(
            com.android1500.gpssetter.room.Favourite(id = 1, name = "Home", lat = 12.345, lng = 67.890, address = "Some Address")
        )
        val jsonString = MapActivity.favoritesToJson(favorites)
        // Using org.json to parse back and verify, to be independent of exact string formatting (like spacing)
        val jsonArray = org.json.JSONArray(jsonString)
        assertEquals(1, jsonArray.length())
        val jsonObj = jsonArray.getJSONObject(0)
        assertEquals("Home", jsonObj.getString("name"))
        assertEquals(12.345, jsonObj.getDouble("latitude"), 0.0001)
        assertEquals(67.890, jsonObj.getDouble("longitude"), 0.0001)
    }

    @Test
    fun testFavoritesToJson_multipleFavorites() {
        val favorites = listOf(
            com.android1500.gpssetter.room.Favourite(id = 1, name = "Home", lat = 12.345, lng = 67.890, address = "Some Address"),
            com.android1500.gpssetter.room.Favourite(id = 2, name = "Work", lat = 12.355, lng = 67.900, address = "Another Address")
        )
        val jsonString = MapActivity.favoritesToJson(favorites)
        val jsonArray = org.json.JSONArray(jsonString)
        assertEquals(2, jsonArray.length())
        val jsonObj1 = jsonArray.getJSONObject(0)
        assertEquals("Home", jsonObj1.getString("name"))
        val jsonObj2 = jsonArray.getJSONObject(1)
        assertEquals("Work", jsonObj2.getString("name"))
    }

    @Test
    fun testJsonToImportableFavorites_emptyJsonArray() {
        val jsonString = "[]"
        val favorites = MapActivity.jsonToImportableFavorites(jsonString)
        assertTrue(favorites.isEmpty())
    }

    @Test
    fun testJsonToImportableFavorites_validJson() {
        val jsonString = """
            [
              { "name": "Home", "latitude": 12.345, "longitude": 67.890 },
              { "name": "Work", "latitude": 12.355, "longitude": 67.900 }
            ]
        """.trimIndent()
        val favorites = MapActivity.jsonToImportableFavorites(jsonString)
        assertEquals(2, favorites.size)
        assertEquals("Home", favorites[0].name)
        assertEquals(12.345, favorites[0].latitude, 0.0001)
        assertEquals(67.890, favorites[0].longitude, 0.0001)
        assertEquals("Work", favorites[1].name)
        assertEquals(12.355, favorites[1].latitude, 0.0001)
        assertEquals(67.900, favorites[1].longitude, 0.0001)
    }

    @Test
    fun testJsonToImportableFavorites_missingFields() {
        val jsonString = """
            [
              { "name": "Home", "latitude": 12.345 },
              { "name": "Work", "longitude": 67.900 },
              { "latitude": 1.0, "longitude": 2.0 }
            ]
        """.trimIndent()
        val favorites = MapActivity.jsonToImportableFavorites(jsonString)
        // Only items with all required fields (name, latitude, longitude) are considered valid
        assertEquals(0, favorites.size)
    }

    @Test
    fun testJsonToImportableFavorites_invalidJsonStructure() {
        val jsonString = "{ \"name\": \"Home\" }" // Not an array
        assertThrows(org.json.JSONException::class.java) {
            MapActivity.jsonToImportableFavorites(jsonString)
        }
    }

     @Test
    fun testJsonToImportableFavorites_emptyName() {
        val jsonString = """
            [
              { "name": "", "latitude": 12.345, "longitude": 67.890 }
            ]
        """.trimIndent()
        val favorites = MapActivity.jsonToImportableFavorites(jsonString)
        // Empty name should be filtered out by the current logic `if (name.isNotEmpty() ...)`
        assertEquals(0, favorites.size)
    }

    @Test
    fun testJsonToImportableFavorites_NaNValues() {
        val jsonString = """
            [
              { "name": "Test", "latitude": "NaN", "longitude": 1.0 }
            ]
        """.trimIndent()
         // org.json.JSONObject.optDouble("key", Double.NaN) will parse "NaN" string to Double.NaN
        val favorites = MapActivity.jsonToImportableFavorites(jsonString)
        assertEquals(0, favorites.size) // NaN latitude should make it invalid
    }

}
