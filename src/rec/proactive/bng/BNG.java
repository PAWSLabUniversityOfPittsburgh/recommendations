package rec.proactive.bng;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import rec.GetUserActivity;

import java.util.Map.Entry;

public class BNG {

	public static ArrayList<ArrayList<String>> calculateSequenceRank(String user_id, String group_id, String domain,
			String rec_dbstring, String rec_dbuser, String rec_dbpass, String um2_dbstring, String um2_dbuser,
			String um2_dbpass, String[] contentList, String lastAct, int proactive_max,
			Map<String, List<String>> topicContents,
			Map<String, Double> usrContentProgress,
			HashMap<String, Double> itemKCEstimates, String updatesm) {
		
//		System.out.println("-----------------------------------------------");
//		long startTime = System.currentTimeMillis();
//		double second = (System.currentTimeMillis()-startTime) / 1000.0;
//		System.out.println("start of bng:" + second + " (sec)");

		
		// STATIC DATA
		BNGStaticData static_data = BNGStaticData.getInstance(rec_dbstring, rec_dbuser, rec_dbpass, um2_dbstring,
				um2_dbuser, um2_dbpass, contentList);
		HashSet<String> codingList = static_data.getCodingList();
		HashSet<String> challengeList = static_data.getChallengeList();
		HashSet<String> exampleList = static_data.getExampleList();
		Map<String, List<String>> itemKCs = static_data.getItemKcs();
		Map<String, HashSet<String>> setChallenges = static_data.getSetChallenges();

		
		// DYNAMIC DATA
		if (usrContentProgress == null || usrContentProgress.size() == 0) {
			// Get progress data on coding/challenges/examples (all in one map)
			HashMap<String, String[]> userActivityMap = GetUserActivity.getUserActivityReport(user_id, group_id, domain,
					codingList.toArray(new String[codingList.size()]), "pcrs", GetUserActivity.pcrsActivityServiceURL);
			userActivityMap.putAll(GetUserActivity.getUserActivityReport(user_id, group_id, domain, 
					challengeList.toArray(new String[challengeList.size()]), "pcex_ch",
					GetUserActivity.pcexChallengeActivityURL));
			userActivityMap.putAll(GetUserActivity.getUserActivityReport(user_id, group_id, domain, 
					exampleList.toArray(new String[exampleList.size()]), "pcex",
					GetUserActivity.pcexExampleActivityURL));
			
			usrContentProgress = new HashMap<String,Double>();
			for (Entry<String, String[]> entry : userActivityMap.entrySet())
			{
				//progress is the 3rd value in String[]
				usrContentProgress.put(entry.getKey(), Double.parseDouble(entry.getValue()[2]));
			}
		}
		
//		second = (System.currentTimeMillis()-startTime) / 1000.0;
//		System.out.println("got static data:" + second + " (sec)");

		// LOGIC
		double threshold = 0.7;
		double masteryThreshold = 0.95;
		ArrayList<ArrayList<String>> sequenceList = new ArrayList<ArrayList<String>>();

		// Get recommendations for each topic
//		System.out.println("******************");
		for (String topic : topicContents.keySet()) {
//			System.out.println("***Topic: "+topic);
			getTopicRecommendation(proactive_max, codingList, challengeList, exampleList, itemKCs,
					usrContentProgress, itemKCEstimates, threshold, masteryThreshold, sequenceList,
					topicContents.get(topic), lastAct, setChallenges, updatesm);

		}
//		System.out.println("******************");

//		second = (System.currentTimeMillis()-startTime) / 1000.0;
//		System.out.println("Total time [got complete recs]:" + second + " (sec)");
//		System.out.println("-----------------------------------------------");
		return sequenceList;
	}

	private static void getTopicRecommendation(int proactive_max, HashSet<String> codingList, HashSet<String> challengeList,
			HashSet<String> exampleList, Map<String, List<String>> itemKCs, Map<String, Double> usrContentProgress,
			HashMap<String, Double> itemKCEstimates, double threshold, double masteryThreshold,
			ArrayList<ArrayList<String>> sequenceList, List<String> topicContents,
			String lastAct, Map<String, HashSet<String>> setChallenges, String updatesm) {

		Map<String, double[]> coRankMap = new HashMap<String, double[]>();
		Map<String, double[]> chRankMap = new HashMap<String, double[]>();
		Map<String, double[]> exRankMap = new HashMap<String, double[]>();
		TreeMap<String, double[]> coSortedMap = null;
		TreeMap<String, double[]> chSortedMap = null;
		TreeMap<String, double[]> exSortedMap = null;
		double estimate, progress;
		double[] values;

		for (String item : topicContents) {
			if (codingList.contains(item)) {
				estimate = (itemKCEstimates.get(item) == null ? 0 : itemKCEstimates.get(item));
				progress = usrContentProgress.get(item) == null ? 0 : usrContentProgress.get(item);
				if (isEligibleToRecommend(estimate, progress, masteryThreshold)) {
					values = new double[3];
					values[0] = estimate;
					values[1] = progress;
					values[2] = (itemKCs.get(item) == null ? 0 : itemKCs.get(item).size());
					coRankMap.put(item, values);
				}
			}
		}
		
		for (String item : topicContents) {
			if (challengeList.contains(item)) {
				estimate = (itemKCEstimates.get(item) == null ? 0 : itemKCEstimates.get(item));
				progress = usrContentProgress.get(item) == null ? 0 : usrContentProgress.get(item);
				if (isEligibleToRecommend(estimate, progress, masteryThreshold)) {
					values = new double[3];
					values[0] = estimate;
					values[1] = progress;
					values[2] = (itemKCs.get(item) == null ? 0 : itemKCs.get(item).size());
					chRankMap.put(item, values);
				}
			}
		}
		
		for (String item : topicContents) {
			if (exampleList.contains(item)) {
				estimate = getAveragePLearn(item, itemKCs.get(item), itemKCEstimates);
				progress = usrContentProgress.get(item) == null ? 0 : usrContentProgress.get(item);
				values = new double[3];
				values[0] = estimate;
				values[1] = progress;
				values[2] = (itemKCs.get(item) == null ? 0 : itemKCs.get(item).size());
				if (isEligibleToRecommend(estimate, progress, masteryThreshold)) {
					exRankMap.put(item, values);							
				}
			}
		}

		// Sort the coding based on probabilities descendingly, if equal
		// probabilities, less concept first [because progress of the items in coRankMap are all 0]
		coSortedMap = new TreeMap<String, double[]>(
				new ValueComparatorItemEstimatesProgressKCNumDesc(coRankMap));
		coSortedMap.putAll(coRankMap); // sorted map

//		System.out.println("==>coRankMap:  "+ printMap(coRankMap));
//		System.out.println("==>coSortedMap:  "+ printMap(coSortedMap));


		// Sort the challenges based on probabilities descendingly, if equal
		// probabilities, less concept first [because progress of the items in chRankMap all 0]
		chSortedMap = new TreeMap<String, double[]>(
				new ValueComparatorItemEstimatesProgressKCNumDesc(chRankMap));
		chSortedMap.putAll(chRankMap); // sorted map

//		System.out.println("==>chRankMap:  "+ printMap(chRankMap));
//		System.out.println("==>chSortedMap:  "+ printMap(chSortedMap));
	

		// Sort the examples based on probabilities descendingly, if equal
		// probabilities, less progress comes first [if progress are also same, the one with lower kcs comes first]
		exSortedMap = new TreeMap<String, double[]>(
				new ValueComparatorItemEstimatesProgressKCNumDesc(exRankMap));
		exSortedMap.putAll(exRankMap);

//		System.out.println("==>exRankMap:  "+ printMap(exRankMap));
//		System.out.println("==>exSortedMap:  "+ printMap(exSortedMap));
		

		addItemsToRecList(proactive_max, sequenceList, coRankMap, chRankMap,
				coSortedMap, chSortedMap, exSortedMap, lastAct, threshold, masteryThreshold,
				exampleList, itemKCEstimates, setChallenges, updatesm);
		
		coRankMap.clear(); coRankMap = null; 
		chRankMap.clear(); chRankMap = null;
		exRankMap.clear(); exRankMap = null;
		coSortedMap.clear(); coSortedMap = null;
		chSortedMap.clear(); chSortedMap = null;
		exSortedMap.clear(); exSortedMap = null;
	}

	private static void addItemsToRecList(int proactive_max, ArrayList<ArrayList<String>> sequenceList,
			Map<String, double[]> coRankMap, Map<String, double[]> chRankMap, TreeMap<String, double[]> coSortedMap,
			TreeMap<String, double[]> chSortedMap, TreeMap<String, double[]> exSortedMap, 
			String lastAct, double threshold, double masteryThreshold, HashSet<String> exampleList, 
			HashMap<String, Double> itemKCEstimates, Map<String, HashSet<String>> setChallenges, String updatesm) {
		
		int addedToRecList = 0; //we need it to keep track of number of items that we add to sequencing list
		HashSet<String> addedChallenges = new HashSet<String>(); //keep track of added challenges
		HashSet<String> addedCodings = new HashSet<String>(); //keep track of added codings
		
		//check the numbers of items we want to recommend
		if (exampleList.contains(lastAct)) {
			//if last activity was example, add to reclist a challenge related to that example 
			//the challenge will be the first in the reclist
			HashSet<String> chLst= setChallenges.get(lastAct);
			for (Entry<String, double[]> e : chSortedMap.entrySet()) {
				if (chLst.contains(e.getKey()))
				{
					ArrayList<String> list = new ArrayList<String>();
					list.add(e.getKey());
					list.add(getScore(addedToRecList));
					list.add("bng");
					sequenceList.add(list);
//					System.out.println("~~~added to reclist: "+e.getKey());
					addedChallenges.add(e.getKey());
					addedToRecList++;
					break; //we need only one challenge
				}
			}
		}
		
		//Add eligible codings to the reclist, if any
		double estimate;
		for (Entry<String, double[]> e : coSortedMap.entrySet()) {
			if (addedToRecList == proactive_max)
				break;
			estimate = e.getValue()[0];
			if (estimate >= threshold) { //add only if the items is above estimate
				ArrayList<String> list = new ArrayList<String>();
				list.add(e.getKey());
				list.add(getScore(addedToRecList));
				list.add("bng");
				sequenceList.add(list);
//				System.out.println("~~~added to reclist: "+e.getKey());
				addedCodings.add(e.getKey());
				addedToRecList++;
			}
		}
		
		
		//Add eligible challenges to the reclist, if any
		if ( addedToRecList < proactive_max ) {
			for (Entry<String, double[]> e : chSortedMap.entrySet()) {
				if (addedToRecList == proactive_max)
					break;
				estimate = e.getValue()[0];
				if (estimate >= threshold && addedChallenges.contains(e.getKey()) == false) {  //add only if the items is above estimate and not added in the previous step
					ArrayList<String> list = new ArrayList<String>();
					list.add(e.getKey());
					list.add(getScore(addedToRecList));
					list.add("bng");
					sequenceList.add(list);
//					System.out.println("~~~added to reclist: "+e.getKey());
					addedChallenges.add(e.getKey());
					addedToRecList++;
				}
			}
		}
		
		/*
		 * We assume  that if student solved all codings and challenges, we should not
		 * recommend examples, even if they are not fully completed or even seen. Normally
		 * this case should not happen because we expect that all estimates in examples
		 * reached mastery after solving problems. In some cases, estimates reach very
		 * close to mastery but if all problems are solved, there's no way to increase
		 * estimates and the only way to stop recommendation is that student clicks all
		 * lines which is not reasonable. For this reasons, if all codings/challenges
		 * are sovled, examples will not be recommended.
		 * Challenges or codings are not all solved if eligible
		 * challenge list or eligible coding list is non empty.
		 */
		if (chSortedMap.size() > 0 || coSortedMap.size() > 0) {
			//Add eligible examples to the reclist, if any
			if ( addedToRecList < proactive_max ) {
				//first check examples above threshold
				for (Entry<String, double[]> e : exSortedMap.entrySet()) {
					if (addedToRecList == proactive_max)
						break;
					//if the example that we want to add to list is equal to the last activity that student attempted, 
					//don't recommend that example immediately, recommend next best example instead
					if (e.getKey().equals(lastAct)) continue;
					
					//if an example has at least one of its challenges solved, don't recommend it
					if ( atLeastOneExChallengedSolved(chSortedMap, setChallenges, e.getKey()) ) continue;
					
					ArrayList<String> list = new ArrayList<String>();
					list.add(e.getKey());
					list.add(getScore(addedToRecList));
					list.add("bng");
					sequenceList.add(list);
//					System.out.println("~~~added to reclist: "+e.getKey());
					addedToRecList++;
				}	
			}
			
			//Check if size of reclist has reached the number of items that we need to recommend
			if (addedToRecList < proactive_max) {
				//start by picking from challenges
				for (Entry<String, double[]> e : chSortedMap.entrySet()) {
					if (addedToRecList == proactive_max)
						break;
					if (addedChallenges.contains(e.getKey()) == false) {
						estimate = e.getValue()[0]; //we don't filter by the estimate in the last round
						ArrayList<String> list = new ArrayList<String>();
						list.add(e.getKey());
						list.add(getScore(addedToRecList));
						list.add("bng");
						sequenceList.add(list);
//						System.out.println("~~~added to reclist: "+e.getKey());
						addedToRecList++;
					}
				}
			}
			//Check if size of reclist has reached the number of items that we need to recommend
			if (addedToRecList < proactive_max) {
				//after challenges, we move to codings
				for (Entry<String, double[]> e : coSortedMap.entrySet()) {
					if (addedToRecList == proactive_max)
						break;
					if (addedCodings.contains(e.getKey()) == false) {
						estimate = e.getValue()[0]; //we don't filter by the estimate in the last round
						ArrayList<String> list = new ArrayList<String>();
						list.add(e.getKey());
						list.add(getScore(addedToRecList));
						list.add("bng");
						sequenceList.add(list);
//						System.out.println("~~~added to reclist: "+e.getKey());
						addedToRecList++;
					}
				}
			}
		}
		addedCodings.clear(); addedCodings = null;
		addedChallenges.clear(); addedChallenges = null;
	}
	
	private static boolean atLeastOneExChallengedSolved(TreeMap<String, double[]> chSortedMap,
			Map<String, HashSet<String>> setChallenges, String example) {
		int countChNotSolved = 0;
		HashSet<String> chList = setChallenges.get(example);
		for (String ch : chList) {
			for (String eligibleChToRec : chSortedMap.keySet())
				if (eligibleChToRec.equals(ch))
					countChNotSolved++;
		}
		return (chList.size() - countChNotSolved) > 0  ;
	}

		
	/*
	 * This method assigns score to recommended items in a topic based on the order
	 * that we want add them to the rec list (this is different that using estimates)
	 * returns 1 for the 1st item, 0.7 for the 2nd items, 0.3 for the 3rd items
	 */
	
	private static String getScore(int addedToRecList) {
		if (addedToRecList == 0)
			return "1";
		else if (addedToRecList == 1)
			return "0.7";
		else 
			return "0.3";
	}

//	private static String printMap(Map<String,double[]> map) {
//		String s = "";
//		for (Entry<String, double[]>  e : map.entrySet()){
//			s+= (s.isEmpty()? "{" : ",") + e.getKey()+"=["+Arrays.toString(e.getValue())+"],";
//		}
//		return (s.isEmpty()? "{}" : s+"}");		
//	}
	
	private static boolean isEligibleToRecommend(double estimate, double progress,
			double masteryThreshold) {
		return ( estimate < masteryThreshold && progress < 1);
	}

	private static double getAveragePLearn(String item, List<String> kcList, HashMap<String, Double> itemKCEstimates) {
		double pLearnSum = 0;
		for (String kc : kcList) {
			pLearnSum += (itemKCEstimates.get(kc) == null ? 0 : itemKCEstimates.get(kc));
		}
		return (kcList.size() > 0 ? pLearnSum / kcList.size() : 0);
	}
	
}
