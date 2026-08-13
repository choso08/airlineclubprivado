package controllers;

import com.patson.data.AirlineSource;
import com.patson.data.AirportSource;
import com.patson.data.AllianceSource;
import com.patson.data.CountrySource;
import com.patson.model.Airline;
import com.patson.model.Airport;
import com.patson.model.Alliance;
import com.patson.model.Country;
import com.patson.util.AirlineCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.Option;
import scala.collection.JavaConverters;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

/**
 * The search boxes, answered from the game's own memory.
 *
 * This is what Elasticsearch was there for. It is a fine piece of software and
 * entirely the wrong size for this: a second server to install, run, watch and
 * keep indexed, holding a few thousand airports and however many airlines
 * there are players - on a machine that is already running the database, the
 * simulation and the web server. When it is not running, upstream's search
 * boxes answer nothing at all and say nothing about why.
 *
 * There are two ideas here and they are both small. Everything searchable is
 * kept in memory as a handful of pre-folded strings, rebuilt now and then;
 * SearchText decides how well a piece of text matches what was typed. Together
 * they answer a keystroke in about a millisecond without leaving the process.
 *
 * The alternative would have been a LIKE against the database on every
 * keystroke - which is what the first fix here did, and it works, but it
 * reloads every airport in the world each time somebody types a letter, and it
 * cannot do accents, words in the middle of a name, or typos.
 *
 * Two rebuild rates, because the data comes in two kinds. Airports, countries
 * and zones are fixed for the life of a game and are rebuilt every half hour
 * out of caution rather than need. Airlines and alliances change while people
 * play - a new player, a rename, an alliance formed - so they are rebuilt
 * every half minute, and immediately when the game tells us one of those
 * things has happened.
 */
public class LocalSearchEngine {
	private final static Logger logger = LoggerFactory.getLogger(LocalSearchEngine.class);

	/** Airports, countries and zones: fixed for the life of a game. */
	private final static long STATIC_TTL_MS = 30 * 60 * 1000L;
	/** Airlines and alliances: they change while people are playing. */
	private final static long LIVE_TTL_MS = 30 * 1000L;

	/**
	 * How many results are enough before typo matching is worth doing.
	 *
	 * Typos are the expensive question - every word of every airport measured
	 * against what was typed - and they are hardly ever the question that
	 * needs asking, because people mostly type things correctly. So the data
	 * is read once without them, and only read again with them when what came
	 * back was too thin to be useful.
	 */
	private final static int ENOUGH_WITHOUT_TYPOS = 5;

	private final static int AIRPORT_LIMIT = 20;
	private final static int COUNTRY_LIMIT = 20;
	private final static int ZONE_LIMIT = 10;
	private final static int AIRLINE_LIMIT = 20;
	private final static int ALLIANCE_LIMIT = 20;

	// ---- what is searchable ------------------------------------------------

	private interface Doc {
		SearchText.Field[] fields();
	}

	private final static class AirportDoc implements Doc {
		final int id;
		final String iata, name, city, countryCode;
		final long power;
		final SearchText.Field[] fields;

		AirportDoc(Airport airport) {
			this.id = airport.id();
			this.iata = airport.iata();
			this.name = airport.name();
			this.city = airport.city();
			this.countryCode = airport.countryCode();
			// basePower rather than power: the boosted figure is worked out
			// lazily from an airport's features and assets, and asking four
			// thousand airports for it would turn building this index into a
			// stampede of queries. It is used to break ties between equally
			// good matches, where the difference could not be seen anyway.
			this.power = airport.basePower();
			this.fields = new SearchText.Field[] {
				new SearchText.Field(airport.iata(), 5),
				// The four letter code is not in upstream's index at all,
				// which is a shame - people who know it are exactly the people
				// who would type it.
				new SearchText.Field(airport.icao(), 4),
				new SearchText.Field(airport.city(), 2),
				new SearchText.Field(airport.name(), 1)
			};
		}

		public SearchText.Field[] fields() {
			return fields;
		}
	}

	private final static class CountryDoc implements Doc {
		final String name, countryCode;
		final int population;
		final SearchText.Field[] fields;

		CountryDoc(Country country) {
			this.name = country.name();
			this.countryCode = country.countryCode();
			this.population = country.airportPopulation();
			this.fields = new SearchText.Field[] {
				new SearchText.Field(country.countryCode(), 5),
				new SearchText.Field(country.name(), 1)
			};
		}

		public SearchText.Field[] fields() {
			return fields;
		}
	}

	private final static class ZoneDoc implements Doc {
		final String name, zone;
		final SearchText.Field[] fields;

		ZoneDoc(String zone, String name) {
			this.zone = zone;
			this.name = name;
			this.fields = new SearchText.Field[] {
				new SearchText.Field(zone, 10),
				new SearchText.Field(name, 2)
			};
		}

		public SearchText.Field[] fields() {
			return fields;
		}
	}

	private final static class AirlineDoc implements Doc {
		final Airline airline;
		final SearchText.Field[] fields;
		final SearchText.Field[] previousNameFields;

		AirlineDoc(Airline airline) {
			this.airline = airline;
			List<String> previousNames = new ArrayList<>(JavaConverters.asJava(airline.previousNames()));
			this.previousNameFields = new SearchText.Field[previousNames.size()];
			for (int i = 0; i < previousNames.size(); i++) {
				this.previousNameFields[i] = new SearchText.Field(previousNames.get(i), 4);
			}

			List<SearchText.Field> all = new ArrayList<>();
			all.add(new SearchText.Field(airline.name(), 5));
			all.add(new SearchText.Field(airline.getAirlineCode(), 1));
			Collections.addAll(all, this.previousNameFields);
			this.fields = all.toArray(new SearchText.Field[0]);
		}

		public SearchText.Field[] fields() {
			return fields;
		}
	}

	private final static class AllianceDoc implements Doc {
		final int id;
		final String name;
		final SearchText.Field[] fields;

		AllianceDoc(Alliance alliance) {
			this.id = alliance.id();
			this.name = alliance.name();
			this.fields = new SearchText.Field[] {
				new SearchText.Field(alliance.name(), 10)
			};
		}

		public SearchText.Field[] fields() {
			return fields;
		}
	}

	// ---- the searches ------------------------------------------------------

	public static List<AirportSearchResult> searchAirport(String input) {
		return search(airports(), input, AIRPORT_LIMIT,
			(a, b) -> Long.compare(b.power, a.power),
			(doc, score) -> new AirportSearchResult(doc.id, doc.iata, doc.name, doc.city,
				doc.countryCode, doc.power, score));
	}

	public static List<CountrySearchResult> searchCountry(String input) {
		return search(countries(), input, COUNTRY_LIMIT,
			(a, b) -> Integer.compare(b.population, a.population),
			(doc, score) -> new CountrySearchResult(doc.name, doc.countryCode, doc.population, score));
	}

	public static List<ZoneSearchResult> searchZone(String input) {
		return search(zones(), input, ZONE_LIMIT,
			(a, b) -> a.name.compareTo(b.name),
			(doc, score) -> new ZoneSearchResult(doc.name, doc.zone, score));
	}

	public static List<AirlineSearchResult> searchAirline(String input) {
		String[] terms = SearchText.terms(input);
		return search(airlines(), input, AIRLINE_LIMIT,
			(a, b) -> a.airline.name().compareTo(b.airline.name()),
			(doc, score) -> {
				// The airline object is read again from the cache rather than
				// taken from the index, so that someone who renamed their
				// airline a moment ago is not found under the old name and
				// then shown it.
				Airline airline = doc.airline;
				Option<Airline> current = AirlineCache.getAirline(doc.airline.id(), false);
				if (current.isDefined()) {
					airline = current.get();
				}
				boolean previousNameMatch =
					doc.previousNameFields.length > 0
						&& SearchText.score(terms, false, doc.previousNameFields) > 0;
				return new AirlineSearchResult(airline, score, previousNameMatch);
			});
	}

	public static List<AllianceSearchResult> searchAlliance(String input) {
		return search(alliances(), input, ALLIANCE_LIMIT,
			(a, b) -> a.name.compareTo(b.name),
			(doc, score) -> new AllianceSearchResult(doc.id, doc.name, score));
	}

	/**
	 * Everything that matches, best first, at most so many.
	 *
	 * Ties are broken by whatever the caller thinks matters - the busier
	 * airport, the bigger country - because on a short query there are plenty
	 * of them and the order is the whole value of a search box.
	 */
	private static <D extends Doc, R> List<R> search(List<D> docs, String input, int limit,
			Comparator<D> tieBreak, BiFunction<D, Double, R> toResult) {
		String[] terms = SearchText.terms(input);
		if (terms.length == 0 || docs.isEmpty()) {
			return Collections.emptyList();
		}

		List<Hit<D>> hits = collect(docs, terms, false);
		if (hits.size() < ENOUGH_WITHOUT_TYPOS) {
			List<Hit<D>> withTypos = collect(docs, terms, true);
			if (withTypos.size() > hits.size()) {
				hits = withTypos;
			}
		}
		if (hits.isEmpty()) {
			return Collections.emptyList();
		}

		hits.sort((a, b) -> {
			if (a.score != b.score) {
				return a.score > b.score ? -1 : 1;
			}
			return tieBreak.compare(a.doc, b.doc);
		});

		List<R> results = new ArrayList<>();
		for (Hit<D> hit : hits.subList(0, Math.min(limit, hits.size()))) {
			results.add(toResult.apply(hit.doc, hit.score));
		}
		return results;
	}

	private static <D extends Doc> List<Hit<D>> collect(List<D> docs, String[] terms, boolean allowFuzzy) {
		List<Hit<D>> hits = new ArrayList<>();
		for (D doc : docs) {
			double score = SearchText.score(terms, allowFuzzy, doc.fields());
			if (score > 0) {
				hits.add(new Hit<>(doc, score));
			}
		}
		return hits;
	}

	private final static class Hit<D> {
		final D doc;
		final double score;

		Hit(D doc, double score) {
			this.doc = doc;
			this.score = score;
		}
	}

	// ---- what is held in memory, and for how long --------------------------

	private final static AtomicReference<Snapshot<AirportDoc>> airportSnapshot = new AtomicReference<>();
	private final static AtomicReference<Snapshot<CountryDoc>> countrySnapshot = new AtomicReference<>();
	private final static AtomicReference<Snapshot<ZoneDoc>> zoneSnapshot = new AtomicReference<>();
	private final static AtomicReference<Snapshot<AirlineDoc>> airlineSnapshot = new AtomicReference<>();
	private final static AtomicReference<Snapshot<AllianceDoc>> allianceSnapshot = new AtomicReference<>();

	private static List<AirportDoc> airports() {
		return cached(airportSnapshot, STATIC_TTL_MS, () -> {
			List<AirportDoc> docs = new ArrayList<>();
			for (Airport airport : JavaConverters.asJava(AirportSource.loadAllAirports(false, false))) {
				docs.add(new AirportDoc(airport));
			}
			logger.info("Search index: {} airports", docs.size());
			return docs;
		});
	}

	private static List<CountryDoc> countries() {
		return cached(countrySnapshot, STATIC_TTL_MS, () -> {
			List<CountryDoc> docs = new ArrayList<>();
			for (Country country : JavaConverters.asJava(CountrySource.loadAllCountries())) {
				docs.add(new CountryDoc(country));
			}
			return docs;
		});
	}

	private static List<ZoneDoc> zones() {
		return cached(zoneSnapshot, STATIC_TTL_MS, () -> {
			Map<String, String> names = new LinkedHashMap<>();
			names.put("AS", "Asia");
			names.put("OC", "Oceania");
			names.put("AF", "Africa");
			names.put("EU", "Europe");
			names.put("NA", "North America");
			names.put("SA", "South America");

			List<ZoneDoc> docs = new ArrayList<>();
			for (Map.Entry<String, String> entry : names.entrySet()) {
				docs.add(new ZoneDoc(entry.getKey(), entry.getValue()));
			}
			return docs;
		});
	}

	private static List<AirlineDoc> airlines() {
		return cached(airlineSnapshot, LIVE_TTL_MS, () -> {
			List<AirlineDoc> docs = new ArrayList<>();
			for (Airline airline : JavaConverters.asJava(AirlineSource.loadAllAirlines(false))) {
				docs.add(new AirlineDoc(airline));
			}
			return docs;
		});
	}

	private static List<AllianceDoc> alliances() {
		return cached(allianceSnapshot, LIVE_TTL_MS, () -> {
			List<AllianceDoc> docs = new ArrayList<>();
			for (Alliance alliance : JavaConverters.asJava(AllianceSource.loadAllAlliances(false))) {
				docs.add(new AllianceDoc(alliance));
			}
			return docs;
		});
	}

	/** A new player, or somebody renamed their airline. */
	public static void airlinesChanged() {
		airlineSnapshot.set(null);
	}

	/** An alliance was formed, joined, left or dissolved. */
	public static void alliancesChanged() {
		allianceSnapshot.set(null);
	}

	/** Everything, for when the world itself was rebuilt. */
	public static void reset() {
		airportSnapshot.set(null);
		countrySnapshot.set(null);
		zoneSnapshot.set(null);
		airlinesChanged();
		alliancesChanged();
	}

	private final static class Snapshot<D> {
		final List<D> docs;
		final long builtAt;

		Snapshot(List<D> docs, long builtAt) {
			this.docs = docs;
			this.builtAt = builtAt;
		}
	}

	/**
	 * The list, built if it is missing or old.
	 *
	 * If the database cannot be read the previous list is kept and its clock
	 * reset, rather than the search box going empty: stale results are worth
	 * more than none, and it stops a database hiccup turning into one failed
	 * query per keystroke per player.
	 */
	private static <D> List<D> cached(AtomicReference<Snapshot<D>> holder, long ttl, Supplier<List<D>> build) {
		Snapshot<D> current = holder.get();
		if (current != null && System.currentTimeMillis() - current.builtAt < ttl) {
			return current.docs;
		}

		synchronized (holder) {
			current = holder.get();
			long now = System.currentTimeMillis();
			if (current != null && now - current.builtAt < ttl) {
				return current.docs;
			}
			try {
				List<D> docs = build.get();
				holder.set(new Snapshot<>(docs, now));
				return docs;
			} catch (Throwable e) {
				logger.warn("Could not rebuild the search index (" + e.getMessage() + ")");
				if (current == null) {
					return Collections.emptyList();
				}
				holder.set(new Snapshot<>(current.docs, now));
				return current.docs;
			}
		}
	}
}
