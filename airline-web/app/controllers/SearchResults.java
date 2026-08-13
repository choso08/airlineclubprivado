package controllers;

import com.patson.model.Airline;

/**
 * What a search box gets back.
 *
 * These lived at the bottom of SearchUtil.java, which existed to talk to
 * Elasticsearch. The talking is gone - the game answers its own searches now,
 * see LocalSearchEngine - but the shapes it answered in are still what the
 * controllers serialize, so they moved here rather than being rewritten for
 * the sake of it.
 *
 * They sort by score and break ties on whatever makes one result more useful
 * than another: the busier airport, the bigger country. Note that they sort
 * ASCENDING, so callers reverse.
 */
class AirportSearchResult implements Comparable {
	private final long power;
	private int id;
	private String iata, name, city, countryCode;
	private double score;

	public AirportSearchResult(int id, String iata, String name, String city, String countryCode, long power, double score) {
		this.id = id;
		this.iata = iata;
		this.name = name;
		this.city = city;
		this.countryCode = countryCode;
		this.power = power;
		this.score = score;
	}

	public int getId() {
		return id;
	}

	public String getIata() {
		return iata;
	}

	public String getName() {
		return name;
	}

	public String getCity() {
		return city;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public double getScore() {
		return score;
	}

	public long getPower() {
		return power;
	}

	@Override
	public int compareTo(Object o) {
		if (!(o instanceof AirportSearchResult)) {
			throw new IllegalArgumentException(o + " is not a " + AirportSearchResult.class.getSimpleName());
		}

		AirportSearchResult that = (AirportSearchResult) o;

		if (this.score != that.score) {
			return this.score < that.score ? -1 : 1;
		} else if (this.power != that.power){
			return this.power < that.power ? -1 : 1;
		} else {
			return this.iata.compareTo(that.iata);
		}
	}
}

class CountrySearchResult implements Comparable {
	private final String name, countryCode;
	private final double score;
	private final int population;

	public CountrySearchResult(String name, String countryCode, int population, double score) {
		this.name = name;
		this.countryCode = countryCode;
		this.population = population;
		this.score = score;
	}

	public String getName() {
		return name;
	}

	public String getCountryCode() {
		return countryCode;
	}

	public double getScore() {
		return score;
	}

	public int getPopulation() {
		return population;
	}

	@Override
	public int compareTo(Object o) {
		if (!(o instanceof CountrySearchResult)) {
			throw new IllegalArgumentException(o + " is not a " + CountrySearchResult.class.getSimpleName());
		}

		CountrySearchResult that = (CountrySearchResult) o;

		if (this.score != that.score) {
			return this.score < that.score ? -1 : 1;
		} else {
			return this.population - that.population;
		}
	}
}


class ZoneSearchResult implements Comparable {
	private final String name, zone;
	private final double score;

	public ZoneSearchResult(String name, String zone,  double score) {
		this.name = name;
		this.zone = zone;
		this.score = score;
	}

	public String getName() {
		return name;
	}

	public String getZone() {
		return zone;
	}

	public double getScore() {
		return score;
	}


	@Override
	public int compareTo(Object o) {
		if (!(o instanceof ZoneSearchResult)) {
			throw new IllegalArgumentException(o + " is not a " + ZoneSearchResult.class.getSimpleName());
		}

		ZoneSearchResult that = (ZoneSearchResult) o;

		if (this.score != that.score) {
			return this.score < that.score ? -1 : 1;
		} else {
			return this.name.compareTo(that.name);
		}
	}
}


class AirlineSearchResult implements Comparable {
	private final Airline airline;
	private final double score;
	private final boolean previousNameMatch;
	//private final int status;

	public AirlineSearchResult(Airline airline, double score, boolean previousNameMatch) {
		this.airline = airline;
		this.score = score;
		this.previousNameMatch = previousNameMatch;
		//this.status = status;
	}

	public Airline getAirline() {
		return airline;
	}

	public double getScore() {
		return score;
	}

	public boolean isPreviousNameMatch() {
		return previousNameMatch;
	}

	//	public int getStatus() {
//		return status;
//	}

	@Override
	public int compareTo(Object o) {
		if (!(o instanceof AirlineSearchResult)) {
			throw new IllegalArgumentException(o + " is not a " + AirlineSearchResult.class.getSimpleName());
		}

		AirlineSearchResult that = (AirlineSearchResult) o;

		if (this.score != that.score) {
			return this.score < that.score ? -1 : 1;
		} else {
			return that.airline.id() - this.airline.id();
		}
	}

	@Override
	public String toString() {
		return "AirlineSearchResult{" +
				"airline=" + airline +
				", score=" + score +
				'}';
	}
}

class AllianceSearchResult implements Comparable {
	private final String allianceName;
	private final double score;
	private final int allianceId;

	public AllianceSearchResult(int id, String name, double score) {
		this.allianceId = id;
		this.allianceName = name;
		this.score = score;
	}

	public String getAllianceName() {
		return allianceName;
	}

	public double getScore() {
		return score;
	}

	public int getAllianceId() {
		return allianceId;
	}

	@Override
	public int compareTo(Object o) {
		if (!(o instanceof AllianceSearchResult)) {
			throw new IllegalArgumentException(o + " is not a " + AllianceSearchResult.class.getSimpleName());
		}

		AllianceSearchResult that = (AllianceSearchResult) o;

		if (this.score != that.score) {
			return this.score < that.score ? -1 : 1;
		} else {
			return that.allianceId - this.allianceId;
		}
	}
}
