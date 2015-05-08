import java.util.*;
import java.io.*;

public class CollaborativeFiltering {

	public static Map<Integer, Map<Integer, Float>> db = new HashMap<Integer, Map<Integer, Float>>();
	public static Map<Integer, Float> averageRatings = new HashMap<Integer, Float>();
	public static Map<Integer, Map<Integer, Float>> userCorrelations = new HashMap<Integer, Map<Integer, Float>>();
	public static Map<Integer, Float> kValues = new HashMap<Integer, Float>();
	
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
		
		// Cache k-values for each user in the test set
		for (int userId : testUserIds) {
			kValues.put(userId, kValue(userId, allUserIds));
		}
		
		// Cache the correlation between every pair of users
		for (int userId1 : testUserIds) {
			for (int userId2 : allUserIds) {
				if (!userCorrelations.containsKey(userId1)) {
					userCorrelations.put(userId1, new HashMap<Integer, Float>());
				}
				userCorrelations.get(userId1).put(userId2, userCorrelation(userId1, userId2));
			}
		}
		
		// Print time spent pre-processing data
		long preproccessingEndTime = System.currentTimeMillis();
		long preproccessingDiff = (preproccessingEndTime - preproccessingStartTime) / 1000;
		System.out.println("Preproccessing done: " + preproccessingDiff + " seconds");		

		// Print time spent predicting ratings for test set
		long predictionsStartTime = System.currentTimeMillis();
		float meanAbsoluteError = meanAbsoluteError("/home/jesse/Classes/446/hw2/TestingRatings.txt");
		long predictionsEndTime = System.currentTimeMillis();
		long predictionsDiff = (predictionsEndTime - predictionsStartTime) / 1000;
		System.out.println("Run time: " + predictionsDiff + " seconds");
		System.out.println("Mean absolute error: " + meanAbsoluteError);
	}
	
	/**
	 *	Returns the predicted rating for the given user and the given movie
	 */
	public static float predictedRating(int activeUserId, int movieId) {
		float result = 0;
		Map<Integer, Float> activeUserCorrelations = userCorrelations.get(activeUserId);
		Set<Integer> allUsers = db.keySet();
		for (int otherUserId : allUsers) {	// TODO: do not include active user
			if (db.get(otherUserId).get(movieId) != null) {
				result += activeUserCorrelations.get(otherUserId) * (db.get(otherUserId).get(movieId) - averageRatings.get(otherUserId));  //  userCorrelation(activeUserId, otherUserId) 
			}
		}
		return averageRatings.get(activeUserId) + (kValues.get(activeUserId) * result);
	}
	
	/**
	 *  Returns the mean absolute error of the predictions for the testing data
	 */
	private static float meanAbsoluteError(String testFilePath) {
		float errorSum = 0;
		int count = 0;
		try {
			Scanner scanner = new Scanner(new File(testFilePath));
			while (scanner.hasNextLine()) {
			//for (int i = 0; i < 100; i++) {
				String[] tokens = scanner.nextLine().split(",");
				int userId = Integer.parseInt(tokens[0]);
				int movieId = Integer.parseInt(tokens[1]);
				float actualRating = Float.parseFloat(tokens[2]);
				
				float predictedRating = predictedRating(userId, movieId);
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
	private static float kValue(int activeUserId, Set<Integer> userIds) {
		float result = 0;
		for (int otherUserId : userIds) {
			result += Math.abs(userCorrelation(activeUserId, otherUserId));
		}
		return 1 / result;
	}
	
	/**
	 *  Returns the average movie rating for the given user across all movies he has rated
	 */
	private static float averageUserRating(int userId) {
		Collection<Float> ratings = db.get(userId).values();
		if (ratings == null || ratings.size() == 0) {
			return 0;
		}
		float ratingSum = 0;
		for (float rating : ratings) {
			ratingSum += rating;
		}
		return ratingSum / ratings.size();
	}
	
	/**
	 *  Returns the correlation coefficient between the two users
	 */
	private static float userCorrelation(int activeUserId, int otherUserId) {
		Set<Integer> movieIds = intersectionOfMoviesRated(activeUserId, otherUserId);
		Map<Integer, Float> activeUserRatings = db.get(activeUserId);
		Map<Integer, Float> otherUserRatings = db.get(otherUserId);
		float activeUserRatingAverage = averageRatings.get(activeUserId); //averageUserRating(activeUserId);
		float otherUserRatingAverage = averageRatings.get(otherUserId); //averageUserRating(otherUserId);
		float numerator = 0;
		float denominator = 0;
		for (int movieId : movieIds) {
			float activeUserDiff = activeUserRatings.get(movieId) - activeUserRatingAverage;
			float otherUserDiff = otherUserRatings.get(movieId) - otherUserRatingAverage; 
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
				float rating = Float.parseFloat(tokens[2]);
				if (!db.containsKey(userId)) {
					db.put(userId, new HashMap<Integer, Float>());	
				}
				db.get(userId).put(movieId, rating);
			}
			scanner.close();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}
