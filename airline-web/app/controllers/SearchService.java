package controllers;

import com.patson.model.Airline;
import com.patson.model.Alliance;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The one place that decides how the search boxes are answered.
 *
 * Upstream calls Elasticsearch directly from six places, which is why turning
 * it off was never really possible: the searches, the sign-up, the rename and
 * the alliance code all knew about it separately. They all come through here
 * now, and the choice is one setting.
 *
 *   search.engine = "local"          the game's own index. Nothing to install,
 *                                    nothing to run, nothing to keep in step.
 *   search.engine = "elasticsearch"  upstream behaviour, with the local index
 *                                    kept as a safety net.
 *
 * "local" is the default because for a private game it is simply better: a
 * separate search server is a lot of machine to spend on a few thousand
 * airports, and when it is not running upstream's search boxes fail silently.
 * Elasticsearch remains available for anyone running a big public game who
 * wants it.
 *
 * Nothing here touches Elasticsearch unless it is asked for. That matters more
 * than it looks: SearchUtil opens a connection the moment the class is first
 * used, so merely mentioning it in a code path that runs would cost every
 * player a failed connection on every keystroke.
 */
public class SearchService {
	private final static Logger logger = LoggerFactory.getLogger(SearchService.class);

	private static Boolean elasticsearch = null;

	private static boolean useElasticsearch() {
		if (elasticsearch == null) {
			String engine = "local";
			try {
				Config config = ConfigFactory.load();
				if (config.hasPath("search.engine")) {
					engine = config.getString("search.engine");
				}
			} catch (Throwable e) {
				logger.warn("Could not read search.engine (" + e.getMessage() + ") - using the local index");
			}
			elasticsearch = "elasticsearch".equalsIgnoreCase(engine.trim());
			logger.info("Search engine: " + (elasticsearch ? "elasticsearch" : "local"));
		}
		return elasticsearch;
	}

	// ---- searching ---------------------------------------------------------

	public static List<AirportSearchResult> searchAirport(String input) {
		List<AirportSearchResult> hits = fromElasticsearch(() -> SearchUtil.searchAirport(input));
		return hits.isEmpty() ? LocalSearchEngine.searchAirport(input) : hits;
	}

	public static List<CountrySearchResult> searchCountry(String input) {
		List<CountrySearchResult> hits = fromElasticsearch(() -> SearchUtil.searchCountry(input));
		return hits.isEmpty() ? LocalSearchEngine.searchCountry(input) : hits;
	}

	public static List<ZoneSearchResult> searchZone(String input) {
		List<ZoneSearchResult> hits = fromElasticsearch(() -> SearchUtil.searchZone(input));
		return hits.isEmpty() ? LocalSearchEngine.searchZone(input) : hits;
	}

	public static List<AirlineSearchResult> searchAirline(String input) {
		List<AirlineSearchResult> hits = fromElasticsearch(() -> SearchUtil.searchAirline(input));
		return hits.isEmpty() ? LocalSearchEngine.searchAirline(input) : hits;
	}

	public static List<AllianceSearchResult> searchAlliance(String input) {
		List<AllianceSearchResult> hits = fromElasticsearch(() -> SearchUtil.searchAlliance(input));
		return hits.isEmpty() ? LocalSearchEngine.searchAlliance(input) : hits;
	}

	/**
	 * Whatever Elasticsearch has to say, if it was asked for and is answering.
	 *
	 * An empty answer is treated as no answer, so the local index gets its
	 * turn. Elasticsearch refuses anything with an accent or a digit in it
	 * outright - the local index does not - so this is not only about the
	 * server being down.
	 */
	private static <T> List<T> fromElasticsearch(Supplier<List<T>> query) {
		if (!useElasticsearch()) {
			return Collections.emptyList();
		}
		try {
			List<T> result = query.get();
			return result == null ? Collections.emptyList() : result;
		} catch (Throwable e) {
			logger.warn("Elasticsearch did not answer (" + e.getMessage() + ") - using the local index");
			return Collections.emptyList();
		}
	}

	// ---- keeping the index honest -----------------------------------------

	/** A new player signed up. */
	public static void airlineAdded(Airline airline) {
		if (useElasticsearch()) {
			SearchUtil.addAirline(airline);
		}
		LocalSearchEngine.airlinesChanged();
	}

	/** Somebody renamed their airline or changed its code. */
	public static void airlineUpdated(Airline airline) {
		if (useElasticsearch()) {
			SearchUtil.updateAirline(airline);
		}
		LocalSearchEngine.airlinesChanged();
	}

	public static void allianceAdded(Alliance alliance) {
		if (useElasticsearch()) {
			SearchUtil.addAlliance(alliance);
		}
		LocalSearchEngine.alliancesChanged();
	}

	public static void allianceRemoved(int allianceId) {
		if (useElasticsearch()) {
			SearchUtil.removeAlliance(allianceId);
		}
		LocalSearchEngine.alliancesChanged();
	}

	/** The simulation may have dissolved alliances behind our back. */
	public static void alliancesChanged() {
		if (useElasticsearch()) {
			SearchUtil.refreshAlliances();
		}
		LocalSearchEngine.alliancesChanged();
	}
}
