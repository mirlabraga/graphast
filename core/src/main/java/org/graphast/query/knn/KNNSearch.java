package org.graphast.query.knn;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.PriorityQueue;

import org.graphast.model.Graph;
import org.graphast.model.Node;
import org.graphast.query.model.LowerBoundEntry;
import org.graphast.util.DateUtils;

public class KNNSearch extends AbstractKNNService{
	
	public KNNSearch(Graph network, BoundsKNN minBounds, BoundsKNN maxBounds){
		super(network, minBounds, maxBounds);
	}
	
	public ArrayList<NearestNeighbor> search(Node v, Date time, int k){
		ArrayList<NearestNeighbor> nn = new ArrayList<NearestNeighbor>();
		HashMap<Long, Integer> wasTraversed = new HashMap<Long, Integer>();
		PriorityQueue<LowerBoundEntry> queue = new PriorityQueue<LowerBoundEntry>();
		PriorityQueue<UpperEntry> upperCandidates = new PriorityQueue<UpperEntry>();
		HashMap<Long, Integer> isIn = new HashMap<Long, Integer>();
		HashMap<Long, Long> parents = new HashMap<Long, Long>();
		int kth = Integer.MAX_VALUE;
		int t = DateUtils.dateToMilli(time);
		LowerBoundEntry removed = null;
		
		init(v.getId(), t, k, kth, queue, upperCandidates, isIn, parents);
		
		while(!queue.isEmpty()){
			removed = queue.poll();
			wasTraversed.put(removed.getId(), wasRemoved);	
			parents.put(removed.getId(), removed.getParent());
			
			if(((Graph) network).isPoi(removed.getId())){
				nn.add(new NearestNeighbor(removed.getId(), removed.getTravelTime(), 
						reconstructPath(removed.getId(), parents)));
				if(nn.size()==k) return nn;
			}
			
			expandVertex(removed, kth, wasTraversed, k,queue, upperCandidates, isIn);
		}
		return nn;
	}
}
