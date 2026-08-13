package controllers;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import play.libs.Json;

public class WeatherUtil {
	private static final String APP_ID = "a73aa7c1bf75d7e3cb83d45a40d50c78";

	// Optional, not Weather. A LoadingCache refuses to store null and throws
	// InvalidCacheLoadException instead, so "there is no forecast for this
	// airport" used to come back as an exception - and, worse, was never
	// remembered, so the same doomed lookup ran again on every single request.
	// Wrapping it lets a failure be cached like any other answer.
	private static LoadingCache<Coordinates, Optional<Weather>> cache = CacheBuilder.newBuilder().maximumSize(1000)
			.expireAfterWrite(10, TimeUnit.MINUTES).build(new CacheLoader<Coordinates, Optional<Weather>>() {
				public Optional<Weather> load(Coordinates coordinates) {
					Weather result = loadWeather(coordinates);
					System.out.println("loaded weather for  " + coordinates + " " + result);
					return Optional.ofNullable(result);
				}
			});

	public static class Coordinates {
		@Override
		public String toString() {
			return "Coordinates [longitude=" + longitude + ", latitude=" + latitude + "]";
		}

		private double longitude;
		private double latitude;

		public Coordinates(double latitude, double longitude) {
			super();
			this.longitude = longitude;
			this.latitude = latitude;
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			long temp;
			temp = Double.doubleToLongBits(latitude);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			temp = Double.doubleToLongBits(longitude);
			result = prime * result + (int) (temp ^ (temp >>> 32));
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			Coordinates other = (Coordinates) obj;
			if (Double.doubleToLongBits(latitude) != Double.doubleToLongBits(other.latitude))
				return false;
			if (Double.doubleToLongBits(longitude) != Double.doubleToLongBits(other.longitude))
				return false;
			return true;
		}
		
		
	}

	public static class Weather {
		private int weatherId;
		private String description;
		private String icon;
		private double temperature;
		private double windSpeed;
		public Weather(int weatherId, String description, String icon, double temperature, double windSpeed) {
			super();
			this.weatherId = weatherId;
			this.description = description;
			this.icon = icon;
			this.temperature = temperature;
			this.windSpeed = windSpeed;
		}
		
		@Override
		public String toString() {
			return "Weather [weatherId=" + weatherId + ", description=" + description + ", icon=" + icon
					+ ", temperature=" + temperature + ", windSpeed=" + windSpeed + "]";
		}

		public int getWeatherId() {
			return weatherId;
		}

		public String getDescription() {
			return description;
		}

		public String getIcon() {
			return icon;
		}

		public double getTemperature() {
			return temperature;
		}

		public double getWindSpeed() {
			return windSpeed;
		}
		
		
	}
	
	/**
	 * Never throws. A missing forecast is not worth breaking a page over.
	 *
	 * This used to let the failure out, and the consequences were out of all
	 * proportion: the departure board asks for the weather before it builds the
	 * list, so any failure here answered the whole request with an error and the
	 * board came up completely blank - on every day, for every airline, with no
	 * message saying why. An airport with no forecast should show its flights
	 * and no weather, not the other way round.
	 */
	public static Weather getWeather(Coordinates coordinates) {
		try {
			return cache.get(coordinates).orElse(null);
		} catch (Exception e) {
			// ExecutionException wraps a checked failure in the loader;
			// UncheckedExecutionException wraps a runtime one. Neither should
			// reach a caller who only wants to know whether it is raining.
			e.printStackTrace();
			return null;
		}
	}


	public static void main(String[] args) {
		System.out.println(loadWeather(new Coordinates(100, 35)));
	}

	/**
	 * Which OpenWeatherMap key to use, or null to skip the lookup entirely.
	 *
	 * Upstream hard-coded one key into the source. It is in a public
	 * repository, so it is shared by every copy of this game in existence and
	 * gets rate-limited accordingly - and when it is refused, every airport
	 * silently loses its weather. Set AIRLINE_WEATHER_API_KEY to your own (they
	 * are free), or set it to "off" to stop asking altogether.
	 */
	private static String weatherApiKey() {
		Config config = ConfigFactory.load();
		String key = config.hasPath("weather.apiKey") ? config.getString("weather.apiKey") : APP_ID;
		if (key == null || key.trim().isEmpty() || "off".equalsIgnoreCase(key.trim())) {
			return null;
		}
		return key.trim();
	}

	private static Weather loadWeather(Coordinates coordinates) {
		String appId = weatherApiKey();
		if (appId == null) {
			return null;
		}
		try {

			URL url = new URL("https://api.openweathermap.org/data/2.5/weather?lat=" + coordinates.latitude + "&lon=" + coordinates.longitude + "&units=metric&appid=" + appId);

			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
			conn.setRequestProperty("Accept", "application/json");

			if (conn.getResponseCode() != 200) {
				throw new RuntimeException("Failed : HTTP error code : " + conn.getResponseCode());
			}

			JsonNode result = Json.parse(conn.getInputStream());

			JsonNode weatherNode = result.get("weather").get(0);
			int weatherId = weatherNode.get("id").asInt();
			String description = weatherNode.get("description").asText();
			String icon = weatherNode.get("icon").asText();
			double temperature = result.get("main").get("temp").asDouble();
			double windSpeed = result.get("wind").get("speed").asDouble();
			
			conn.disconnect();

			return new Weather(weatherId, description, icon, temperature, windSpeed);

		} catch (Exception e) {
			e.printStackTrace();
		}

		return null;

	}
}