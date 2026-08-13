package controllers;

/**
 * The matching rules behind the search boxes, checked on their own.
 *
 *   ./test/search-matching-test.sh
 *
 * SearchText deliberately knows nothing about the database, the configuration
 * or Play, which is what lets this run in a second against a single compiled
 * file rather than needing the whole game standing up. Everything asserted
 * here is a case someone actually hit: accents nobody types, words in the
 * middle of a long airport name, three letter codes that must not be widened
 * by typo matching, and fingers that swap two letters.
 */
public class SearchMatchingTest {
	private static int passed = 0;
	private static int failed = 0;

	public static void main(String[] args) {
		folding();
		splitting();
		singleFieldScoring();
		typos();
		editDistance();
		wholeQueries();
		ranking();

		System.out.println();
		System.out.println("  " + passed + "/" + (passed + failed) + " passed");
		System.out.println();
		System.exit(failed == 0 ? 0 : 1);
	}

	// ---- the cases ---------------------------------------------------------

	private static void folding() {
		equal("accents are dropped", "SAO TOME", SearchText.fold("São Tomé"));
		equal("umlauts are dropped", "ZURICH", SearchText.fold("Zürich"));
		equal("case is levelled", "LISBON", SearchText.fold("lisbon"));
		equal("stray spacing collapses", "NEW YORK", SearchText.fold("  new   york "));
		equal("nothing at all is not a crash", "", SearchText.fold(null));
		equal("a folded string folds to itself", "LIS", SearchText.fold(SearchText.fold("lis")));
	}

	private static void splitting() {
		String[] parts = SearchText.words(SearchText.fold("John F. Kennedy Intl"));
		equal("a name splits into its words", "JOHN|F|KENNEDY|INTL", join(parts));
		equal("punctuation is not a word", "ST JOHN S", joinWithSpaces(SearchText.words(SearchText.fold("St. John's"))));
		equal("what was typed is folded and split", "SAO|TOME", join(SearchText.terms(" são  tomé ")));
		equal("an empty search has no terms", "", join(SearchText.terms("   ")));
		equal("digits count as part of a word", "TERMINAL|2", join(SearchText.terms("Terminal 2")));
	}

	private static void singleFieldScoring() {
		SearchText.Field code = new SearchText.Field("LIS", 5);
		SearchText.Field city = new SearchText.Field("Lisbon", 2);
		SearchText.Field name = new SearchText.Field("Humberto Delgado Airport", 1);
		SearchText.Field saoTome = new SearchText.Field("São Tomé", 2);

		near("the exact code scores highest", SearchText.EXACT, SearchText.scoreTerm("LIS", code, false));
		near("the start of a name scores next", SearchText.PREFIX, SearchText.scoreTerm("LIS", city, false));
		near("a whole word inside the name", SearchText.WORD, SearchText.scoreTerm("DELGADO", name, false));
		near("the start of a word inside the name", SearchText.WORD_PREFIX, SearchText.scoreTerm("DELG", name, false));
		near("text buried inside a word", SearchText.CONTAINS, SearchText.scoreTerm("ELGAD", name, false));
		near("something that is not there", 0, SearchText.scoreTerm("TOKYO", name, false));
		near("an accent the player did not type", SearchText.WORD, SearchText.scoreTerm("TOME", saoTome, false));
		near("the accented spelling still finds it", SearchText.PREFIX,
			SearchText.scoreTerm(SearchText.fold("São"), saoTome, false));
	}

	private static void typos() {
		SearchText.Field frankfurt = new SearchText.Field("Frankfurt am Main", 1);
		SearchText.Field lis = new SearchText.Field("LIS", 5);
		SearchText.Field porto = new SearchText.Field("Porto", 2);

		near("a typo is not a match when typos are off", 0,
			SearchText.scoreTerm("FRANKFRUT", frankfurt, false));
		near("two swapped fingers still find the city", SearchText.NEAR,
			SearchText.scoreTerm("FRANKFRUT", frankfurt, true));
		near("a swap earlier in the word too", SearchText.NEAR,
			SearchText.scoreTerm("FRNAKFURT", frankfurt, true));
		near("a missing letter", SearchText.NEAR,
			SearchText.scoreTerm("PORTU", porto, true));
		near("two mistakes in a long word", SearchText.NEARER,
			SearchText.scoreTerm("FRANKFRUTT", frankfurt, true));
		near("two mistakes that change the first letter are not believed", 0,
			SearchText.scoreTerm("XRANKFRUT", frankfurt, true));
		near("three letter codes are never widened", 0,
			SearchText.scoreTerm("LIZ", lis, true));

		equal("no allowance at three letters", 0, SearchText.allowedTypos("LIS"));
		equal("one mistake allowed at five", 1, SearchText.allowedTypos("PORTO"));
		equal("two mistakes allowed at nine", 2, SearchText.allowedTypos("FRANKFURT"));
	}

	private static void editDistance() {
		equal("the textbook case", 3, SearchText.distance("KITTEN", "SITTING", 4));
		equal("the same word", 0, SearchText.distance("PORTO", "PORTO", 2));
		equal("two neighbours swapped is one mistake", 1, SearchText.distance("AB", "BA", 2));
		equal("giving up early reports past the limit", 2, SearchText.distance("ABCD", "DCBA", 1));
		equal("a length difference beyond the limit stops at once", 2,
			SearchText.distance("A", "ABCDEFG", 1));
		equal("measuring against nothing is the length", 5, SearchText.distance("PORTO", "", 9));
	}

	private static void wholeQueries() {
		SearchText.Field code = new SearchText.Field("LIS", 5);
		SearchText.Field city = new SearchText.Field("Lisbon", 2);
		SearchText.Field name = new SearchText.Field("Humberto Delgado Airport", 1);

		double both = SearchText.score(SearchText.terms("lisbon airport"), false, code, city, name);
		yes("two words matching two different fields", both > 0);
		near("each word counts once, at its best field",
			SearchText.EXACT * 2 + SearchText.WORD * 1, both);

		near("a word that matches nothing fails the whole search", 0,
			SearchText.score(SearchText.terms("lisbon tokyo"), false, code, city, name));
		near("an empty search matches nothing", 0,
			SearchText.score(SearchText.terms("  "), false, code, city, name));
		near("a search with no fields to look in matches nothing", 0,
			SearchText.score(SearchText.terms("lis"), false));
		yes("the same words in the other order score the same",
			SearchText.score(SearchText.terms("airport lisbon"), false, code, city, name) == both);
	}

	private static void ranking() {
		SearchText.Field[] lisbon = {
			new SearchText.Field("LIS", 5),
			new SearchText.Field("Lisbon", 2),
			new SearchText.Field("Humberto Delgado Airport", 1)
		};
		SearchText.Field[] lisburn = {
			new SearchText.Field("BHD", 5),
			new SearchText.Field("Lisburn", 2),
			new SearchText.Field("Belfast City Airport", 1)
		};
		SearchText.Field[] tbilisi = {
			new SearchText.Field("TBS", 5),
			new SearchText.Field("Tbilisi", 2),
			new SearchText.Field("Tbilisi International Airport", 1)
		};

		String[] lis = SearchText.terms("lis");
		double a = SearchText.score(lis, false, lisbon);
		double b = SearchText.score(lis, false, lisburn);
		double c = SearchText.score(lis, false, tbilisi);

		yes("the airport whose code was typed comes first", a > b);
		yes("a city that starts with it beats one that merely contains it", b > c);
		yes("but the one that contains it is still found", c > 0);

		String[] kennedy = SearchText.terms("kennedy");
		SearchText.Field[] jfk = {
			new SearchText.Field("JFK", 5),
			new SearchText.Field("New York", 2),
			new SearchText.Field("John F Kennedy International Airport", 1)
		};
		yes("a word in the middle of a long name is found",
			SearchText.score(kennedy, false, jfk) > 0);
		near("nothing is found for a word that is nowhere", 0,
			SearchText.score(SearchText.terms("heathrow"), false, jfk));
	}

	// ---- the harness -------------------------------------------------------

	private static void yes(String what, boolean pass) {
		report(what, pass, "");
	}

	private static void equal(String what, Object expected, Object actual) {
		report(what, expected == null ? actual == null : expected.equals(actual),
			expected + " expected, got " + actual);
	}

	private static void near(String what, double expected, double actual) {
		report(what, Math.abs(expected - actual) < 0.000001,
			expected + " expected, got " + actual);
	}

	private static void report(String what, boolean pass, String detail) {
		if (pass) {
			passed++;
			System.out.println("  PASS  " + what);
		} else {
			failed++;
			System.out.println("  FAIL  " + what + "  " + detail);
		}
	}

	private static String join(String[] parts) {
		return String.join("|", parts);
	}

	private static String joinWithSpaces(String[] parts) {
		return String.join(" ", parts);
	}
}
