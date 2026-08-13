package controllers;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The matching rules behind every search box in the game.
 *
 * Upstream hands this job to Elasticsearch: a separate server, a second JVM,
 * a gigabyte of memory, its own indices to keep in step - to search a few
 * thousand airports and a handful of airlines. On a machine that also runs the
 * database, the simulation and the web server that is a poor trade, and when
 * it is not running the search boxes answer nothing at all, for ever, with no
 * error anyone can see.
 *
 * So the same job is done here, in the game's own process. This class holds
 * only the text rules - no database, no configuration, no Play - so that it
 * can be reasoned about and tested on its own; LocalSearchEngine holds the
 * data those rules run against.
 *
 * Three things a plain LIKE cannot do, and which are the whole reason people
 * reach for Elasticsearch in the first place:
 *
 * Accents are ignored, in both directions. Sao Tome is written with two of
 * them and nobody types either at a search box, so "sao tome" has to find it -
 * and "SÃO TOMÉ" has to find it as well.
 *
 * Words are matched, not just the beginning of the line. "kennedy" finds
 * "John F Kennedy International", which is what someone typing it meant.
 *
 * Typos still find the place. "frankfrut" is a slip of two fingers, not a
 * different city, and being told "No match" for it is the search box calling
 * you a liar.
 *
 * What comes out is a score rather than a yes/no, because the ordering is the
 * useful part: someone typing "lis" wants Lisbon first and Lisburn later.
 */
public final class SearchText {
	private SearchText() {
	}

	/*
	 * How good a match is, before the field's own weight is applied. The gaps
	 * matter more than the numbers: an exact code has to beat a name that
	 * merely contains the text, whatever the weights say, or typing "LIS"
	 * stops finding Lisbon.
	 */
	public static final double EXACT = 1.0;
	public static final double PREFIX = 0.75;
	public static final double WORD = 0.7;
	public static final double WORD_PREFIX = 0.55;
	public static final double CONTAINS = 0.4;
	public static final double NEAR = 0.3;      // one typo
	public static final double NEARER = 0.18;   // two typos

	/**
	 * Case, accents and stray spacing removed, so that what someone types can
	 * be compared with what is written.
	 *
	 * Decomposing first (NFD) splits an accented letter into the letter and the
	 * accent, and the accents are then dropped - which is what makes this work
	 * for accents the game holds but nobody types.
	 */
	public static String fold(String text) {
		if (text == null) {
			return "";
		}
		String stripped = Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toUpperCase(Locale.ROOT);
		return stripped.replaceAll("\\s+", " ").trim();
	}

	/** The separate words of an already folded string. */
	public static String[] words(String folded) {
		if (folded.isEmpty()) {
			return new String[0];
		}
		List<String> found = new ArrayList<>();
		for (String word : folded.split("[^\\p{L}\\p{N}]+")) {
			if (!word.isEmpty()) {
				found.add(word);
			}
		}
		return found.toArray(new String[0]);
	}

	/** What was typed, folded and split into the words that must all match. */
	public static String[] terms(String input) {
		return words(fold(input));
	}

	/**
	 * One searchable piece of a thing, folded once when the index is built.
	 *
	 * Folding is the expensive part - it allocates and walks the string twice -
	 * and doing it per keystroke for every airport in the world would be felt.
	 * Done here, it happens once per airport per index rebuild instead.
	 *
	 * The weight is how much this field counts for. An airport's code is worth
	 * five times its name, which is what makes typing three letters do what
	 * people expect.
	 */
	public static final class Field {
		public final String value;
		public final String[] words;
		public final double weight;

		public Field(String text, double weight) {
			this.value = fold(text);
			this.words = words(this.value);
			this.weight = weight;
		}
	}

	/**
	 * How far a typo may be from the real thing.
	 *
	 * Short words get no allowance at all: at three letters almost everything
	 * is one edit from everything else, and "LIS" would drag in LIT, LIN, LIZ
	 * and BIS ahead of the airport that was actually asked for.
	 */
	public static int allowedTypos(String term) {
		if (term.length() <= 3) {
			return 0;
		}
		return term.length() <= 6 ? 1 : 2;
	}

	/**
	 * How well one typed word matches one field.
	 *
	 * Fuzzy matching is off in the first pass over the data and only switched
	 * on if too little was found - see LocalSearchEngine. Typos are the
	 * expensive question to ask and hardly ever the one that is needed.
	 */
	public static double scoreTerm(String term, Field field, boolean allowFuzzy) {
		if (term.isEmpty() || field.value.isEmpty()) {
			return 0;
		}

		if (field.value.equals(term)) {
			return EXACT;
		}
		if (field.value.startsWith(term)) {
			return PREFIX;
		}

		boolean wordPrefix = false;
		for (String word : field.words) {
			if (word.equals(term)) {
				return WORD;
			}
			if (word.startsWith(term)) {
				wordPrefix = true;
			}
		}
		if (wordPrefix) {
			return WORD_PREFIX;
		}

		if (field.value.contains(term)) {
			return CONTAINS;
		}

		if (!allowFuzzy) {
			return 0;
		}

		int allowed = allowedTypos(term);
		if (allowed == 0) {
			return 0;
		}
		int best = allowed + 1;
		boolean sameStart = false;
		for (String word : field.words) {
			int distance = distance(term, word, allowed);
			if (distance > allowed) {
				continue;
			}
			boolean starts = word.charAt(0) == term.charAt(0);
			if (distance < best || (distance == best && starts && !sameStart)) {
				best = distance;
				sameStart = starts;
			}
		}
		if (best == 1) {
			return NEAR;
		}
		// Two typos are only believed when the word starts the same way. Left
		// unchecked, an allowance of two turns every short word into a match
		// for every other one, and the results stop meaning anything.
		if (best == 2 && sameStart) {
			return NEARER;
		}
		return 0;
	}

	/**
	 * The whole query against one thing, as a single number; zero means no.
	 *
	 * Every typed word has to match somewhere, but they may match in different
	 * places: "lisbon portela" finds the airport whose city is one and whose
	 * name is the other. A word that matches nowhere fails the lot, which is
	 * what stops a second word from quietly widening the search instead of
	 * narrowing it - the behaviour people expect from every other search box
	 * they have used.
	 */
	public static double score(String[] terms, boolean allowFuzzy, Field... fields) {
		if (terms.length == 0 || fields.length == 0) {
			return 0;
		}
		double total = 0;
		for (String term : terms) {
			double best = 0;
			for (Field field : fields) {
				double score = scoreTerm(term, field, allowFuzzy) * field.weight;
				if (score > best) {
					best = score;
				}
			}
			if (best == 0) {
				return 0;
			}
			total += best;
		}
		return total;
	}

	/**
	 * Edit distance, counting a swap of two neighbours as one mistake.
	 *
	 * "Frnakfurt" is one slip of the fingers, and counting it as two would put
	 * it out of reach of a nine letter word's allowance. Anything further away
	 * than the limit is not worth measuring precisely, so it gives up early and
	 * returns the limit plus one - which is what makes this cheap enough to run
	 * against every airport in the world on a keystroke.
	 */
	public static int distance(String a, String b, int limit) {
		int lengthA = a.length();
		int lengthB = b.length();
		if (Math.abs(lengthA - lengthB) > limit) {
			return limit + 1;
		}
		if (lengthA == 0) {
			return lengthB;
		}
		if (lengthB == 0) {
			return lengthA;
		}

		int[] twoBack = new int[lengthB + 1];
		int[] previous = new int[lengthB + 1];
		int[] current = new int[lengthB + 1];

		for (int j = 0; j <= lengthB; j++) {
			previous[j] = j;
		}

		for (int i = 1; i <= lengthA; i++) {
			current[0] = i;
			int rowBest = current[0];
			for (int j = 1; j <= lengthB; j++) {
				int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
				int value = Math.min(
					Math.min(current[j - 1] + 1, previous[j] + 1),
					previous[j - 1] + cost);
				if (i > 1 && j > 1
					&& a.charAt(i - 1) == b.charAt(j - 2)
					&& a.charAt(i - 2) == b.charAt(j - 1)) {
					value = Math.min(value, twoBack[j - 2] + 1);
				}
				current[j] = value;
				if (value < rowBest) {
					rowBest = value;
				}
			}
			if (rowBest > limit) {
				return limit + 1;
			}
			int[] spare = twoBack;
			twoBack = previous;
			previous = current;
			current = spare;
		}
		return previous[lengthB];
	}
}
