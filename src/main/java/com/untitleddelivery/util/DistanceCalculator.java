package com.untitleddelivery.util;

public class DistanceCalculator {

	private static final double EARTH_RADIUS_KM = 6371.0;

	/**
	 * Calculate distance between two geographic points using the Haversine formula.
	 * Returns distance in kilometers.
	 */
	public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
		double lat1Rad = Math.toRadians(lat1);
		double lat2Rad = Math.toRadians(lat2);
		double deltaLatRad = Math.toRadians(lat2 - lat1);
		double deltaLonRad = Math.toRadians(lon2 - lon1);

		double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2) +
		           Math.cos(lat1Rad) * Math.cos(lat2Rad) *
		           Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);

		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

		return EARTH_RADIUS_KM * c;
	}
}
