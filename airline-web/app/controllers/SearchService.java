package controllers;

import com.patson.model.Airline;
import com.patson.model.Alliance;

import java.util.List;

/**
 * The one place that answers the search boxes.
 *
 * It used to choose between Elasticsearch and the game's own index. There is
 * no choice left: Elasticsearch is gone. It was a separate server holding a
 * few thousand airports, on a machine already running the database, the
 * simulation and the web site, and it brought twenty-eight jars and twenty-four
 * megabytes into every build for a feature that had been answered locally for
 * some time. Nobody here ever ran it.
 *
 * This class stays rather than being dissolved into the controller, because
 * the write side is the part worth having in one place: a new airline, a
 * rename, an alliance formed or dissolved all have to reach the index, and
 * they are called from four different files.
 */
public class SearchService {

	// ---- searching ---------------------------------------------------------

	public static List<AirportSearchResult> searchAirport(String input) {
		return LocalSearchEngine.searchAirport(input);
	}

	public static List<CountrySearchResult> searchCountry(String input) {
		return LocalSearchEngine.searchCountry(input);
	}

	public static List<ZoneSearchResult> searchZone(String input) {
		return LocalSearchEngine.searchZone(input);
	}

	public static List<AirlineSearchResult> searchAirline(String input) {
		return LocalSearchEngine.searchAirline(input);
	}

	public static List<AllianceSearchResult> searchAlliance(String input) {
		return LocalSearchEngine.searchAlliance(input);
	}

	// ---- keeping the index honest -----------------------------------------

	/** A new player signed up. */
	public static void airlineAdded(Airline airline) {
		LocalSearchEngine.airlinesChanged();
	}

	/** Somebody renamed their airline or changed its code. */
	public static void airlineUpdated(Airline airline) {
		LocalSearchEngine.airlinesChanged();
	}

	public static void allianceAdded(Alliance alliance) {
		LocalSearchEngine.alliancesChanged();
	}

	public static void allianceRemoved(int allianceId) {
		LocalSearchEngine.alliancesChanged();
	}

	/** The simulation may have dissolved alliances behind our back. */
	public static void alliancesChanged() {
		LocalSearchEngine.alliancesChanged();
	}
}
