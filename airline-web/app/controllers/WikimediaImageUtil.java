package controllers;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Photographs of cities and airports, from Wikipedia.
 *
 * The alternative is Google Places, which needs a Cloud project with billing
 * attached. For a private game shared with friends that is a lot of paperwork -
 * and a credit card - for some decorative pictures, so this asks Wikipedia
 * instead: no API key, no account, no billing.
 *
 * Lookup is by COORDINATES rather than name. Place names are ambiguous
 * (Frankfurt, Paris, Santiago, and any number of airports named after people),
 * and the game already knows exactly where each airport is. Wikipedia's
 * geosearch returns the nearest article to a point, and one request can both
 * find that article and return its lead image:
 *
 *   action=query&generator=geosearch&ggscoord=LAT|LON&ggsradius=...&prop=pageimages
 *
 * Results are cached by the caller (GoogleImageUtil's Guava cache, backed by
 * the database), so a given airport is looked up once and then remembered.
 *
 * Wikimedia asks for a descriptive User-Agent so they can contact whoever is
 * making the requests. Sending one is a condition of their API etiquette, not
 * an optional nicety.
 */
public class WikimediaImageUtil {
	private final static Logger logger = LoggerFactory.getLogger(WikimediaImageUtil.class);

	private final static String USER_AGENT =
		"AirlineClubSelfHosted/1.0 (private game server; https://github.com/patsonluk/airline)";

	private final static int TIMEOUT_MS = 8000;
	private final static int THUMB_WIDTH = 800;

	/** How far from the given point to accept an article, in metres. */
	private final static int CITY_RADIUS = 10000;
	private final static int AIRPORT_RADIUS = 5000;

	private final static Pattern THUMBNAIL_SOURCE =
		Pattern.compile("\"source\"\\s*:\\s*\"([^\"]+)\"");

	private static String language() {
		Config config = ConfigFactory.load();
		return config.hasPath("images.wikipediaLanguage") ? config.getString("images.wikipediaLanguage") : "en";
	}

	public static URL getCityImageUrl(String cityName, Double latitude, Double longitude) {
		return lookup(latitude, longitude, CITY_RADIUS, cityName);
	}

	public static URL getAirportImageUrl(String airportName, Double latitude, Double longitude) {
		return lookup(latitude, longitude, AIRPORT_RADIUS, airportName);
	}

	private static URL lookup(Double latitude, Double longitude, int radius, String describeFor) {
		if (latitude == null || longitude == null) {
			return null;
		}

		try {
			String endpoint = String.format(
				"https://%s.wikipedia.org/w/api.php" +
					"?action=query&format=json&formatversion=2" +
					"&generator=geosearch&ggscoord=%s%%7C%s&ggsradius=%d&ggslimit=1" +
					"&prop=pageimages&piprop=thumbnail&pithumbsize=%d",
				URLEncoder.encode(language(), StandardCharsets.UTF_8),
				latitude, longitude, radius, THUMB_WIDTH);

			String body = fetch(endpoint);
			if (body == null) {
				return null;
			}

			// The response is small and its shape is fixed, so a regex is
			// enough and avoids pulling a JSON parser into this class. The
			// only "source" field present is the thumbnail's.
			Matcher matcher = THUMBNAIL_SOURCE.matcher(body);
			if (!matcher.find()) {
				logger.debug("No Wikipedia image near {},{} (for {})", latitude, longitude, describeFor);
				return null;
			}

			String imageUrl = matcher.group(1).replace("\\/", "/");
			logger.info("Wikipedia image for {} -> {}", describeFor, imageUrl);
			return new URL(imageUrl);

		} catch (Exception e) {
			// Never let a missing decorative picture break the page that wanted
			// it. The caller treats null as "no image" and shows its
			// placeholder.
			logger.warn("Could not fetch a Wikipedia image for {}: {}", describeFor, e.toString());
			return null;
		}
	}

	private static String fetch(String endpoint) throws Exception {
		HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
		connection.setRequestProperty("User-Agent", USER_AGENT);
		connection.setRequestProperty("Accept", "application/json");
		connection.setConnectTimeout(TIMEOUT_MS);
		connection.setReadTimeout(TIMEOUT_MS);

		try {
			int status = connection.getResponseCode();
			if (status != 200) {
				logger.warn("Wikipedia returned {} for {}", status, endpoint);
				return null;
			}

			StringBuilder body = new StringBuilder();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					body.append(line);
				}
			}
			return body.toString();
		} finally {
			connection.disconnect();
		}
	}
}
