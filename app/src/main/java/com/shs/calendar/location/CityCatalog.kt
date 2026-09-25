package com.shs.calendar.location

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Curated offline catalog of major cities for the manual location picker (M1).
 * Pure Kotlin — no Android dependencies — unit-testable on the JVM.
 *
 * @param timeZone IANA timezone id (validated by [TimeZones.isValid]).
 */
data class City(
    val name: String,
    val country: String,
    val latitude: Double,
    val longitude: Double,
    val timeZone: String
)

/**
 * Offline city search: case-insensitive substring matching over name and
 * country, plus nearest-city lookup via haversine great-circle distance.
 */
object CityCatalog {

    val all: List<City> = listOf(
        // ---- Bangladesh ----
        City("Dhaka", "Bangladesh", 23.8103, 90.4125, "Asia/Dhaka"),
        City("Chattogram", "Bangladesh", 22.3569, 91.7832, "Asia/Dhaka"),
        City("Rajshahi", "Bangladesh", 24.3745, 88.6042, "Asia/Dhaka"),
        City("Khulna", "Bangladesh", 22.8241, 89.5640, "Asia/Dhaka"),
        City("Barishal", "Bangladesh", 22.7010, 90.3535, "Asia/Dhaka"),
        City("Sylhet", "Bangladesh", 24.8949, 91.8687, "Asia/Dhaka"),
        City("Rangpur", "Bangladesh", 25.7439, 89.2752, "Asia/Dhaka"),
        City("Mymensingh", "Bangladesh", 24.7471, 90.4172, "Asia/Dhaka"),
        City("Coxs Bazar", "Bangladesh", 21.4366, 91.9776, "Asia/Dhaka"),
        City("Bogura", "Bangladesh", 24.8481, 89.3730, "Asia/Dhaka"),
        City("Cumilla", "Bangladesh", 23.4577, 91.1877, "Asia/Dhaka"),
        City("Gazipur", "Bangladesh", 23.9910, 90.4146, "Asia/Dhaka"),
        City("Narayanganj", "Bangladesh", 23.6310, 90.4967, "Asia/Dhaka"),
        City("Jessore", "Bangladesh", 23.1634, 89.2182, "Asia/Dhaka"),
        City("Dinajpur", "Bangladesh", 25.6277, 88.6332, "Asia/Dhaka"),
        // ---- India ----
        City("New Delhi", "India", 28.6139, 77.2090, "Asia/Kolkata"),
        City("Mumbai", "India", 19.0760, 72.8777, "Asia/Kolkata"),
        City("Kolkata", "India", 22.5726, 88.3639, "Asia/Kolkata"),
        City("Chennai", "India", 13.0827, 80.2707, "Asia/Kolkata"),
        City("Bengaluru", "India", 12.9716, 77.5946, "Asia/Kolkata"),
        City("Hyderabad", "India", 17.3850, 78.4867, "Asia/Kolkata"),
        City("Ahmedabad", "India", 23.0225, 72.5714, "Asia/Kolkata"),
        City("Pune", "India", 18.5204, 73.8567, "Asia/Kolkata"),
        City("Jaipur", "India", 26.9124, 75.7873, "Asia/Kolkata"),
        City("Lucknow", "India", 26.8467, 80.9462, "Asia/Kolkata"),
        City("Kanpur", "India", 26.4499, 80.3319, "Asia/Kolkata"),
        City("Surat", "India", 21.1702, 72.8311, "Asia/Kolkata"),
        City("Nagpur", "India", 21.1458, 79.0882, "Asia/Kolkata"),
        City("Kochi", "India", 9.9312, 76.2673, "Asia/Kolkata"),
        City("Guwahati", "India", 26.1445, 91.7362, "Asia/Kolkata"),
        City("Srinagar", "India", 34.0837, 74.7973, "Asia/Kolkata"),
        // ---- Pakistan / Nepal / Sri Lanka / Bhutan / Maldives ----
        City("Karachi", "Pakistan", 24.8607, 67.0011, "Asia/Karachi"),
        City("Lahore", "Pakistan", 31.5204, 74.3587, "Asia/Karachi"),
        City("Islamabad", "Pakistan", 33.6844, 73.0479, "Asia/Karachi"),
        City("Peshawar", "Pakistan", 34.0151, 71.5249, "Asia/Karachi"),
        City("Quetta", "Pakistan", 30.1798, 66.9750, "Asia/Karachi"),
        City("Kathmandu", "Nepal", 27.7172, 85.3240, "Asia/Kathmandu"),
        City("Colombo", "Sri Lanka", 6.9271, 79.8612, "Asia/Colombo"),
        City("Thimphu", "Bhutan", 27.4712, 89.6339, "Asia/Thimphu"),
        City("Male", "Maldives", 4.1755, 73.5093, "Indian/Maldives"),
        // ---- Middle East ----
        City("Mecca", "Saudi Arabia", 21.3891, 39.8579, "Asia/Riyadh"),
        City("Medina", "Saudi Arabia", 24.5247, 39.5692, "Asia/Riyadh"),
        City("Riyadh", "Saudi Arabia", 24.7136, 46.6753, "Asia/Riyadh"),
        City("Jeddah", "Saudi Arabia", 21.4858, 39.1925, "Asia/Riyadh"),
        City("Dammam", "Saudi Arabia", 26.4207, 50.0888, "Asia/Riyadh"),
        City("Dubai", "United Arab Emirates", 25.2048, 55.2708, "Asia/Dubai"),
        City("Abu Dhabi", "United Arab Emirates", 24.4539, 54.3773, "Asia/Dubai"),
        City("Sharjah", "United Arab Emirates", 25.3463, 55.4209, "Asia/Dubai"),
        City("Doha", "Qatar", 25.2854, 51.5310, "Asia/Qatar"),
        City("Kuwait City", "Kuwait", 29.3759, 47.9774, "Asia/Kuwait"),
        City("Manama", "Bahrain", 26.2285, 50.5860, "Asia/Bahrain"),
        City("Muscat", "Oman", 23.5880, 58.3829, "Asia/Muscat"),
        City("Sanaa", "Yemen", 15.3694, 44.1910, "Asia/Aden"),
        City("Baghdad", "Iraq", 33.3152, 44.3661, "Asia/Baghdad"),
        City("Basra", "Iraq", 30.5085, 47.7804, "Asia/Baghdad"),
        City("Erbil", "Iraq", 36.1901, 44.0091, "Asia/Baghdad"),
        City("Amman", "Jordan", 31.9454, 35.9284, "Asia/Amman"),
        City("Beirut", "Lebanon", 33.8938, 35.5018, "Asia/Beirut"),
        City("Damascus", "Syria", 33.5138, 36.2765, "Asia/Damascus"),
        City("Jerusalem", "Israel", 31.7683, 35.2137, "Asia/Jerusalem"),
        City("Tel Aviv", "Israel", 32.0853, 34.7818, "Asia/Jerusalem"),
        City("Tehran", "Iran", 35.6892, 51.3890, "Asia/Tehran"),
        City("Istanbul", "Türkiye", 41.0082, 28.9784, "Europe/Istanbul"),
        City("Ankara", "Türkiye", 39.9334, 32.8597, "Europe/Istanbul"),
        City("Izmir", "Türkiye", 38.4237, 27.1428, "Europe/Istanbul"),
        City("Baku", "Azerbaijan", 40.4093, 49.8671, "Asia/Baku"),
        City("Kabul", "Afghanistan", 34.5553, 69.2075, "Asia/Kabul"),
        // ---- South / Southeast / East Asia ----
        City("Jakarta", "Indonesia", -6.2088, 106.8456, "Asia/Jakarta"),
        City("Surabaya", "Indonesia", -7.2575, 112.7521, "Asia/Jakarta"),
        City("Medan", "Indonesia", 3.5952, 98.6722, "Asia/Jakarta"),
        City("Makassar", "Indonesia", -5.1477, 119.4327, "Asia/Makassar"),
        City("Kuala Lumpur", "Malaysia", 3.1390, 101.6869, "Asia/Kuala_Lumpur"),
        City("Penang", "Malaysia", 5.4141, 100.3288, "Asia/Kuala_Lumpur"),
        City("Singapore", "Singapore", 1.3521, 103.8198, "Asia/Singapore"),
        City("Bangkok", "Thailand", 13.7563, 100.5018, "Asia/Bangkok"),
        City("Chiang Mai", "Thailand", 18.7883, 98.9853, "Asia/Bangkok"),
        City("Hanoi", "Vietnam", 21.0278, 105.8342, "Asia/Ho_Chi_Minh"),
        City("Ho Chi Minh City", "Vietnam", 10.8231, 106.6297, "Asia/Ho_Chi_Minh"),
        City("Manila", "Philippines", 14.5995, 120.9842, "Asia/Manila"),
        City("Cebu City", "Philippines", 10.3157, 123.8854, "Asia/Manila"),
        City("Yangon", "Myanmar", 16.8661, 96.1951, "Asia/Yangon"),
        City("Naypyidaw", "Myanmar", 19.7633, 96.0785, "Asia/Yangon"),
        City("Beijing", "China", 39.9042, 116.4074, "Asia/Shanghai"),
        City("Shanghai", "China", 31.2304, 121.4737, "Asia/Shanghai"),
        City("Guangzhou", "China", 23.1291, 113.2644, "Asia/Shanghai"),
        City("Shenzhen", "China", 22.5431, 114.0579, "Asia/Shanghai"),
        City("Chengdu", "China", 30.5728, 104.0668, "Asia/Shanghai"),
        City("Urumqi", "China", 43.8256, 87.6168, "Asia/Urumqi"),
        City("Hong Kong", "China", 22.3193, 114.1694, "Asia/Hong_Kong"),
        City("Taipei", "Taiwan", 25.0330, 121.5654, "Asia/Taipei"),
        City("Tokyo", "Japan", 35.6762, 139.6503, "Asia/Tokyo"),
        City("Osaka", "Japan", 34.6937, 135.5023, "Asia/Tokyo"),
        City("Seoul", "South Korea", 37.5665, 126.9780, "Asia/Seoul"),
        City("Busan", "South Korea", 35.1796, 129.0756, "Asia/Seoul"),
        City("Ulaanbaatar", "Mongolia", 47.8864, 106.9057, "Asia/Ulaanbaatar"),
        // ---- Europe ----
        City("London", "United Kingdom", 51.5074, -0.1278, "Europe/London"),
        City("Manchester", "United Kingdom", 53.4808, -2.2426, "Europe/London"),
        City("Birmingham", "United Kingdom", 52.4862, -1.8904, "Europe/London"),
        City("Edinburgh", "United Kingdom", 55.9533, -3.1883, "Europe/London"),
        City("Dublin", "Ireland", 53.3498, -6.2603, "Europe/Dublin"),
        City("Paris", "France", 48.8566, 2.3522, "Europe/Paris"),
        City("Lyon", "France", 45.7640, 4.8357, "Europe/Paris"),
        City("Marseille", "France", 43.2965, 5.3698, "Europe/Paris"),
        City("Berlin", "Germany", 52.5200, 13.4050, "Europe/Berlin"),
        City("Munich", "Germany", 48.1351, 11.5820, "Europe/Berlin"),
        City("Frankfurt", "Germany", 50.1109, 8.6821, "Europe/Berlin"),
        City("Hamburg", "Germany", 53.5511, 9.9937, "Europe/Berlin"),
        City("Madrid", "Spain", 40.4168, -3.7038, "Europe/Madrid"),
        City("Barcelona", "Spain", 41.3874, 2.1686, "Europe/Madrid"),
        City("Rome", "Italy", 41.9028, 12.4964, "Europe/Rome"),
        City("Milan", "Italy", 45.4642, 9.1900, "Europe/Rome"),
        City("Amsterdam", "Netherlands", 52.3676, 4.9041, "Europe/Amsterdam"),
        City("Rotterdam", "Netherlands", 51.9244, 4.4777, "Europe/Amsterdam"),
        City("Brussels", "Belgium", 50.8503, 4.3517, "Europe/Brussels"),
        City("Zurich", "Switzerland", 47.3769, 8.5417, "Europe/Zurich"),
        City("Geneva", "Switzerland", 46.2044, 6.1432, "Europe/Zurich"),
        City("Vienna", "Austria", 48.2082, 16.3738, "Europe/Vienna"),
        City("Stockholm", "Sweden", 59.3293, 18.0686, "Europe/Stockholm"),
        City("Oslo", "Norway", 59.9139, 10.7522, "Europe/Oslo"),
        City("Copenhagen", "Denmark", 55.6761, 12.5683, "Europe/Copenhagen"),
        City("Helsinki", "Finland", 60.1699, 24.9384, "Europe/Helsinki"),
        City("Warsaw", "Poland", 52.2297, 21.0122, "Europe/Warsaw"),
        City("Prague", "Czechia", 50.0755, 14.4378, "Europe/Prague"),
        City("Budapest", "Hungary", 47.4979, 19.0402, "Europe/Budapest"),
        City("Athens", "Greece", 37.9838, 23.7275, "Europe/Athens"),
        City("Lisbon", "Portugal", 38.7223, -9.1393, "Europe/Lisbon"),
        City("Moscow", "Russia", 55.7558, 37.6173, "Europe/Moscow"),
        City("Saint Petersburg", "Russia", 59.9311, 30.3609, "Europe/Moscow"),
        City("Kyiv", "Ukraine", 50.4501, 30.5234, "Europe/Kyiv"),
        City("Bucharest", "Romania", 44.4268, 26.1025, "Europe/Bucharest"),
        City("Belgrade", "Serbia", 44.7866, 20.4489, "Europe/Belgrade"),
        City("Reykjavik", "Iceland", 64.1466, -21.9426, "Atlantic/Reykjavik"),
        // ---- Africa ----
        City("Cairo", "Egypt", 30.0444, 31.2357, "Africa/Cairo"),
        City("Alexandria", "Egypt", 31.2001, 29.9187, "Africa/Cairo"),
        City("Casablanca", "Morocco", 33.5731, -7.5898, "Africa/Casablanca"),
        City("Rabat", "Morocco", 34.0209, -6.8416, "Africa/Casablanca"),
        City("Algiers", "Algeria", 36.7538, 3.0588, "Africa/Algiers"),
        City("Tunis", "Tunisia", 36.8065, 10.1815, "Africa/Tunis"),
        City("Tripoli", "Libya", 32.8872, 13.1913, "Africa/Tripoli"),
        City("Khartoum", "Sudan", 15.5007, 32.5599, "Africa/Khartoum"),
        City("Dakar", "Senegal", 14.7167, -17.4677, "Africa/Dakar"),
        City("Bamako", "Mali", 12.6392, -8.0029, "Africa/Bamako"),
        City("Timbuktu", "Mali", 16.7735, -3.0074, "Africa/Bamako"),
        City("Abuja", "Nigeria", 9.0765, 7.3986, "Africa/Lagos"),
        City("Lagos", "Nigeria", 6.5244, 3.3792, "Africa/Lagos"),
        City("Accra", "Ghana", 5.6037, -0.1870, "Africa/Accra"),
        City("Abidjan", "Cote d'Ivoire", 5.3600, -4.0083, "Africa/Abidjan"),
        City("Addis Ababa", "Ethiopia", 9.0320, 38.7469, "Africa/Addis_Ababa"),
        City("Nairobi", "Kenya", -1.2921, 36.8219, "Africa/Nairobi"),
        City("Dar es Salaam", "Tanzania", -6.7924, 39.2083, "Africa/Dar_es_Salaam"),
        City("Kampala", "Uganda", 0.3476, 32.5825, "Africa/Kampala"),
        City("Kigali", "Rwanda", -1.9441, 30.0619, "Africa/Kigali"),
        City("Kinshasa", "DR Congo", -4.4419, 15.2663, "Africa/Kinshasa"),
        City("Luanda", "Angola", -8.8390, 13.2894, "Africa/Luanda"),
        City("Harare", "Zimbabwe", -17.8252, 31.0335, "Africa/Harare"),
        City("Lusaka", "Zambia", -15.3875, 28.3228, "Africa/Lusaka"),
        City("Maputo", "Mozambique", -25.9692, 32.5732, "Africa/Maputo"),
        City("Johannesburg", "South Africa", -26.2041, 28.0473, "Africa/Johannesburg"),
        City("Cape Town", "South Africa", -33.9249, 18.4241, "Africa/Johannesburg"),
        City("Durban", "South Africa", -29.8587, 31.0218, "Africa/Johannesburg"),
        City("Port Louis", "Mauritius", -20.1609, 57.5012, "Indian/Mauritius"),
        // ---- Americas ----
        City("New York", "United States", 40.7128, -74.0060, "America/New_York"),
        City("Boston", "United States", 42.3601, -71.0589, "America/New_York"),
        City("Washington", "United States", 38.9072, -77.0369, "America/New_York"),
        City("Atlanta", "United States", 33.7490, -84.3880, "America/New_York"),
        City("Miami", "United States", 25.7617, -80.1918, "America/New_York"),
        City("Chicago", "United States", 41.8781, -87.6298, "America/Chicago"),
        City("Houston", "United States", 29.7604, -95.3698, "America/Chicago"),
        City("Dallas", "United States", 32.7767, -96.7970, "America/Chicago"),
        City("Denver", "United States", 39.7392, -104.9903, "America/Denver"),
        City("Phoenix", "United States", 33.4484, -112.0740, "America/Phoenix"),
        City("Los Angeles", "United States", 34.0522, -118.2437, "America/Los_Angeles"),
        City("San Francisco", "United States", 37.7749, -122.4194, "America/Los_Angeles"),
        City("Seattle", "United States", 47.6062, -122.3321, "America/Los_Angeles"),
        City("Las Vegas", "United States", 36.1699, -115.1398, "America/Los_Angeles"),
        City("Anchorage", "United States", 61.2181, -149.9003, "America/Anchorage"),
        City("Honolulu", "United States", 21.3069, -157.8583, "Pacific/Honolulu"),
        City("Toronto", "Canada", 43.6532, -79.3832, "America/Toronto"),
        City("Montreal", "Canada", 45.5017, -73.5673, "America/Toronto"),
        City("Vancouver", "Canada", 49.2827, -123.1207, "America/Vancouver"),
        City("Calgary", "Canada", 51.0447, -114.0719, "America/Edmonton"),
        City("Edmonton", "Canada", 53.5461, -113.4938, "America/Edmonton"),
        City("Winnipeg", "Canada", 49.8951, -97.1384, "America/Winnipeg"),
        City("Halifax", "Canada", 44.6488, -63.5752, "America/Halifax"),
        City("Mexico City", "Mexico", 19.4326, -99.1332, "America/Mexico_City"),
        City("Guadalajara", "Mexico", 20.6597, -103.3496, "America/Mexico_City"),
        City("Monterrey", "Mexico", 25.6866, -100.3161, "America/Monterrey"),
        City("Guatemala City", "Guatemala", 14.6349, -90.5069, "America/Guatemala"),
        City("San Jose", "Costa Rica", 9.9281, -84.0907, "America/Costa_Rica"),
        City("Panama City", "Panama", 8.9824, -79.5199, "America/Panama"),
        City("Havana", "Cuba", 23.1136, -82.3666, "America/Havana"),
        City("Kingston", "Jamaica", 17.9714, -76.7936, "America/Jamaica"),
        City("Santo Domingo", "Dominican Republic", 18.4861, -69.9312, "America/Santo_Domingo"),
        City("Port-au-Prince", "Haiti", 18.5944, -72.3074, "America/Port-au-Prince"),
        City("San Juan", "Puerto Rico", 18.4655, -66.1057, "America/Puerto_Rico"),
        City("Bogota", "Colombia", 4.7110, -74.0721, "America/Bogota"),
        City("Medellin", "Colombia", 6.2442, -75.5812, "America/Bogota"),
        City("Caracas", "Venezuela", 10.4806, -66.9036, "America/Caracas"),
        City("Quito", "Ecuador", -0.1807, -78.4678, "America/Guayaquil"),
        City("Lima", "Peru", -12.0464, -77.0428, "America/Lima"),
        City("La Paz", "Bolivia", -16.4897, -68.1193, "America/La_Paz"),
        City("Santiago", "Chile", -33.4489, -70.6693, "America/Santiago"),
        City("Buenos Aires", "Argentina", -34.6037, -58.3816, "America/Argentina/Buenos_Aires"),
        City("Montevideo", "Uruguay", -34.9011, -56.1645, "America/Montevideo"),
        City("Asuncion", "Paraguay", -25.2637, -57.5759, "America/Asuncion"),
        City("Sao Paulo", "Brazil", -23.5505, -46.6333, "America/Sao_Paulo"),
        City("Rio de Janeiro", "Brazil", -22.9068, -43.1729, "America/Sao_Paulo"),
        City("Brasilia", "Brazil", -15.8267, -47.9218, "America/Sao_Paulo"),
        City("Salvador", "Brazil", -12.9777, -38.5016, "America/Bahia"),
        City("Manaus", "Brazil", -3.1190, -60.0217, "America/Manaus"),
        City("Recife", "Brazil", -8.0476, -34.8770, "America/Recife"),
        // ---- Oceania ----
        City("Sydney", "Australia", -33.8688, 151.2093, "Australia/Sydney"),
        City("Melbourne", "Australia", -37.8136, 144.9631, "Australia/Melbourne"),
        City("Canberra", "Australia", -35.2809, 149.1300, "Australia/Sydney"),
        City("Brisbane", "Australia", -27.4698, 153.0251, "Australia/Brisbane"),
        City("Perth", "Australia", -31.9505, 115.8605, "Australia/Perth"),
        City("Adelaide", "Australia", -34.9285, 138.6007, "Australia/Adelaide"),
        City("Darwin", "Australia", -12.4634, 130.8456, "Australia/Darwin"),
        City("Hobart", "Australia", -42.8821, 147.3272, "Australia/Hobart"),
        City("Auckland", "New Zealand", -36.8485, 174.7633, "Pacific/Auckland"),
        City("Wellington", "New Zealand", -41.2866, 174.7756, "Pacific/Auckland"),
        City("Christchurch", "New Zealand", -43.5321, 172.6362, "Pacific/Auckland"),
        City("Suva", "Fiji", -18.1416, 178.4419, "Pacific/Fiji"),
        City("Port Moresby", "Papua New Guinea", -9.4438, 147.1803, "Pacific/Port_Moresby"),
    )

    /** Case-insensitive substring search across name and country. */
    fun search(query: String): List<City> {
        val q = query.trim()
        if (q.isEmpty()) return all
        val lower = q.lowercase()
        return all.filter {
            it.name.lowercase().contains(lower) || it.country.lowercase().contains(lower)
        }.sortedWith(compareBy(
            { !it.name.lowercase().startsWith(lower) },
            { it.name.length }
        ))
    }

    /** Nearest catalog city to a coordinate, or null when the catalog is empty. */
    fun nearest(latitude: Double, longitude: Double): City? =
        all.minByOrNull { distanceKm(latitude, longitude, it.latitude, it.longitude) }

    /** Great-circle (haversine) distance in kilometres. */
    fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    /** Cities whose [City.timeZone] is a valid IANA id on this runtime. */
    fun validTimeZoneCities(): List<City> = all.filter { TimeZones.isValid(it.timeZone) }
}
