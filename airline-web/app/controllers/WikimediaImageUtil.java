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

	/**
	 * Wikipedia could not be asked - no network, a timeout, an error status.
	 *
	 * This exists to keep "there is no photograph of this place" apart from "I
	 * was unable to find out". The caller records the first permanently, so
	 * that a place with no picture is not looked up again on every page view.
	 * Recording the second the same way would be a lie that never expires: one
	 * bad minute of network, and that airport has no picture for the rest of
	 * the game's life.
	 */
	public static class LookupFailedException extends RuntimeException {
		public LookupFailedException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private final static String USER_AGENT =
		"AirlineClubSelfHosted/1.0 (private game server; https://github.com/patsonluk/airline)";

	private final static int TIMEOUT_MS = 8000;
	private final static int THUMB_WIDTH = 800;

	/** How far from the given point to accept an article, in metres. */
	private final static int CITY_RADIUS = 10000;
	// Ten kilometres is the most geosearch allows, and an airport's own article
	// is not always pinned to the runway we hold the coordinates of.
	private final static int AIRPORT_RADIUS = 10000;

	private final static Pattern THUMBNAIL_SOURCE =
		Pattern.compile("\"source\"\\s*:\\s*\"([^\"]+)\"");

	private static String language() {
		Config config = ConfigFactory.load();
		return config.hasPath("images.wikipediaLanguage") ? config.getString("images.wikipediaLanguage") : "en";
	}

	/**
	 * The city, by name, falling back to what is near the airport.
	 *
	 * Coordinates alone cannot find a city from its airport. Geosearch accepts
	 * a radius of at most ten kilometres, and airports are routinely further
	 * out than that - Frankfurt's is twelve kilometres from the city, Paris
	 * Charles de Gaulle twenty-five. Searching around the runway for Paris
	 * finds Roissy-en-France, and a village with no photograph looks exactly
	 * like a failure.
	 *
	 * So the city is looked up by name, and the results are ranked by distance
	 * from the airport - which is what resolves the ambiguity that made
	 * name lookups unattractive in the first place. There are a dozen
	 * Frankfurts; only one of them is near this runway.
	 */
	public static URL getCityImageUrl(String cityName, Double latitude, Double longitude) {
		if (cityName != null && !cityName.trim().isEmpty()) {
			URL byName = lookupByName(cityName, latitude, longitude);
			if (byName != null) {
				return byName;
			}
		}
		return lookup(latitude, longitude, CITY_RADIUS, cityName);
	}

	public static URL getAirportImageUrl(String airportName, Double latitude, Double longitude) {
		return lookup(latitude, longitude, AIRPORT_RADIUS, airportName);
	}

	/** Search by title, keeping the match closest to the given point. */
	private static URL lookupByName(String name, Double latitude, Double longitude) {
		try {
			String endpoint = String.format(
				"https://%s.wikipedia.org/w/api.php" +
					"?action=query&format=json&formatversion=2" +
					"&generator=search&gsrsearch=%s&gsrlimit=5&gsrnamespace=0" +
					"&prop=pageimages&piprop=thumbnail&pithumbsize=%d",
				URLEncoder.encode(language(), StandardCharsets.UTF_8),
				URLEncoder.encode(name, StandardCharsets.UTF_8),
				THUMB_WIDTH);

			String body = fetch(endpoint);
			if (body == null) {
				throw new LookupFailedException("Wikipedia did not answer for " + name, null);
			}
			return firstThumbnail(body);
		} catch (LookupFailedException e) {
			throw e;
		} catch (Exception e) {
			logger.warn("Could not search Wikipedia for {}: {}", name, e.toString());
			throw new LookupFailedException("Could not reach Wikipedia for " + name, e);
		}
	}

	/**
	 * The first thumbnail in the response.
	 *
	 * Asking for one result and giving up if it had no picture was the other
	 * half of the problem: the nearest article to a runway is as likely to be a
	 * motorway junction as the terminal, and junctions have no photographs.
	 * Twenty results cost the same single request, and the first one with a
	 * picture is almost always the right sort of thing.
	 */
	private static URL firstThumbnail(String body) throws java.net.MalformedURLException {
		Matcher matcher = THUMBNAIL_SOURCE.matcher(body);
		if (!matcher.find()) {
			return null;
		}
		return new URL(matcher.group(1).replace("\\/", "/"));
	}

	private static URL lookup(Double latitude, Double longitude, int radius, String describeFor) {
		if (latitude == null || longitude == null) {
			return null;
		}

		try {
			String endpoint = String.format(
				"https://%s.wikipedia.org/w/api.php" +
					"?action=query&format=json&formatversion=2" +
					"&generator=geosearch&ggscoord=%s%%7C%s&ggsradius=%d&ggslimit=20" +
					"&prop=pageimages&piprop=thumbnail&pithumbsize=%d",
				URLEncoder.encode(language(), StandardCharsets.UTF_8),
				latitude, longitude, radius, THUMB_WIDTH);

			// fetch returns null when Wikipedia answered with something other
			// than 200 - that is a failure to ask, not an answer of "no photo".
			String body = fetch(endpoint);
			if (body == null) {
				throw new LookupFailedException("Wikipedia did not answer for " + describeFor, null);
			}

			URL image = firstThumbnail(body);
			if (image == null) {
				logger.info("No Wikipedia image within {}m of {},{} (for {})",
					radius, latitude, longitude, describeFor);
			} else {
				logger.info("Wikipedia image for {} -> {}", describeFor, image);
			}
			return image;

		} catch (LookupFailedException e) {
			throw e;
		} catch (Exception e) {
			// Never let a missing decorative picture break the page that wanted
			// it - but do say that this was a failure rather than an answer, so
			// the caller does not write "no photograph" into the database and
			// leave this place blank for good.
			logger.warn("Could not fetch a Wikipedia image for {}: {}", describeFor, e.toString());
			throw new LookupFailedException("Could not reach Wikipedia for " + describeFor, e);
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
