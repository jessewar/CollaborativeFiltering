import java.util.*;
import java.io.*;

public class CollaborativeFiltering {

	public static Map<Integer, Map<Integer, Double>> db = new HashMap<Integer, Map<Integer, Double>>();
	public static Map<Integer, Double> averageRatings = new HashMap<Integer, Double>();
	public static Map<Integer, Map<Integer, Double>> userCorrelations = new HashMap<Integer, Map<Integer, Double>>();
	
	public static void main(String[] args) {
		long preproccessingStartTime = System.currentTimeMillis();
		
		populateDb("/home/jesse/Classes/446/hw2/TrainingRatings.txt");
		
		// Cache average movie rating of each user
		Set<Integer> allUserIds = db.keySet();
		for (int userId : allUserIds) {
			averageRatings.put(userId, averageUserRating(userId));
		}
		
		// Read set of all user IDs in test file
		Set<Integer> testUserIds = new HashSet<Integer>();  // 1701 users
		try {
			Scanner scanner = new Scanner(new File("/home/jesse/Classes/446/hw2/TestingRatings.txt"));
			while (scanner.hasNextLine()) {
				String[] tokens = scanner.nextLine().split(",");
				int userId = Integer.parseInt(tokens[0]);
				testUserIds.add(userId);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		
		System.out.println("here");
		
		// Cache the correlation between every pair of users
		for (int userId1 : testUserIds) {
			for (int userId2 : allUserIds) {
				if (!userCorrelations.containsKey(userId1)) {
					userCorrelations.put(userId1, new HashMap<Integer, Double>());
				}
				userCorrelations.get(userId1).put(userId2, userCorrelation(userId1, userId2));
			}
		}

		long preproccessingEndTime = System.currentTimeMillis();
		long preproccessingDiff = (preproccessingEndTime - preproccessingStartTime) / 1000;
		System.out.println("Preproccessing done: " + preproccessingDiff + " seconds");		

		
		long predictionsStartTime = System.currentTimeMillis();
		double meanAbsoluteError = meanAbsoluteError("/home/jesse/Classes/446/hw2/TestingRatings.txt");
		long predictionsEndTime = System.currentTimeMillis();
		long predictionsDiff = (predictionsEndTime - predictionsStartTime) / 1000;
		System.out.println("Run time: " + predictionsDiff + " seconds");
		System.out.println("Mean absolute error: " + meanAbsoluteError);
	}
	
	/**
	 *	Returns the predicted rating for the given user and the given movie
	 */
	public static double predictedRating(int activeUserId, int movieId) {
		double result = 0;
		Map<Integer, Double> activeUserCorrelations = userCorrelations.get(activeUserId);
		Set<Integer> allUsers = db.keySet();
		for (int otherUserId : allUsers) {	// TODO: do not include active user
			if (db.get(otherUserId).get(movieId) != null) {
				result += activeUserCorrelations.get(otherUserId) * (db.get(otherUserId).get(movieId) - averageRatings.get(otherUserId));  //  userCorrelation(activeUserId, otherUserId) 
			}
		}
		return averageRatings.get(activeUserId) + (kValue(activeUserId, allUsers) * result);
	}
	
	/**
	 *  Returns the mean absolute error of the predictions for the testing data
	 */
	private static double meanAbsoluteError(String testFilePath) {
		double errorSum = 0;
		int count = 0;
		try {
			Scanner scanner = new Scanner(new File(testFilePath));
//			while (scanner.hasNextLine()) {
			for (int i = 0; i < 100; i++) {
				String[] tokens = scanner.nextLine().split(",");
				int userId = Integer.parseInt(tokens[0]);
				int movieId = Integer.parseInt(tokens[1]);
				double actualRating = Double.parseDouble(tokens[2]);
				
				double predictedRating = predictedRating(userId, movieId);
				errorSum += Math.abs(predictedRating - actualRating);
				count++;
//				if (count % 100 == 0) {
//					System.out.println("Iteration " + (count / 100) + ": " + (errorSum / count));
//				}
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return errorSum / count;
	}
	
	/**
	 *  Returns the normalizing factor making the absolute values of the weights sum to one
	 */
	private static double kValue(int activeUserId, Set<Integer> userIds) {
		double result = 0;
		for (int otherUserId : userIds) {
			result += Math.abs(userCorrelation(activeUserId, otherUserId));
		}
		return 1.0 / result;
	}
	
	/**
	 *  Returns the average movie rating for the given user across all movies he has rated
	 */
	private static double averageUserRating(int userId) {
		Collection<Double> ratings = db.get(userId).values();
		if (ratings == null || ratings.size() == 0) {
			return 0;
		}
		double ratingSum = 0;
		for (double rating : ratings) {
			ratingSum += rating;
		}
		return ratingSum / ratings.size();
	}
	
	/**
	 *  Returns the correlation coefficient between the two users
	 */
	private static double userCorrelation(int activeUserId, int otherUserId) {
		Set<Integer> movieIds = intersectionOfMoviesRated(activeUserId, otherUserId);
		Map<Integer, Double> activeUserRatings = db.get(activeUserId);
		Map<Integer, Double> otherUserRatings = db.get(otherUserId);
		double activeUserRatingAverage = averageRatings.get(activeUserId); //averageUserRating(activeUserId);
		double otherUserRatingAverage = averageRatings.get(otherUserId); //averageUserRating(otherUserId);
		double numerator = 0;
		double denominator = 0;
		for (int movieId : movieIds) {
			double activeUserDiff = activeUserRatings.get(movieId) - activeUserRatingAverage;
			double otherUserDiff = otherUserRatings.get(movieId) - otherUserRatingAverage; 
			numerator += activeUserDiff * otherUserDiff;
			denominator += Math.sqrt(Math.pow(activeUserDiff, 2) * Math.pow(otherUserDiff, 2));
		}
		
		if (numerator == 0 || denominator == 0) {
			return 0;
		}
		return numerator / denominator;
	}
	
	/**
	 *  Returns the list of movie IDs that both users have rated
	 */
	private static Set<Integer> intersectionOfMoviesRated(int activeUserId, int otherUserId) {
		Set<Integer> activeUserMovies = db.get(activeUserId).keySet();
		Set<Integer> otherUserMovies = db.get(otherUserId).keySet();
		Set<Integer> intersection = new HashSet<Integer>(activeUserMovies);
		intersection.retainAll(otherUserMovies);
		return intersection;
	}
	
	/**
	 *  Fills the db with training data contained within the specified file
	 */
	private static void populateDb(String filePath) {
		try {
			Scanner scanner = new Scanner(new File(filePath));
			while (scanner.hasNextLine()) {
				String[] tokens = scanner.nextLine().split(",");
				int userId = Integer.parseInt(tokens[0]);
				int movieId = Integer.parseInt(tokens[1]);
				double rating = Double.parseDouble(tokens[2]);
				if (!db.containsKey(userId)) {
					db.put(userId, new HashMap<Integer, Double>());	
				}
				db.get(userId).put(movieId, rating);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
