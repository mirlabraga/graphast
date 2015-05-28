package org.graphast.query.route.shortestpath.dijkstra;

import it.unimi.dsi.fastutil.ints.Int2DoubleMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.ObjectCollection;

import java.util.HashMap;
import java.util.PriorityQueue;
import java.util.Set;

import org.graphast.model.Edge;
import org.graphast.model.Graph;
import org.graphast.model.GraphBounds;
import org.graphast.model.Node;
import org.graphast.query.model.Bound;
import org.graphast.query.model.QueueEntry;
import org.graphast.query.route.shortestpath.model.RouteEntry;
import org.graphast.query.route.shortestpath.model.TimeEntry;

public class DijkstraLinearFunction extends Dijkstra{

	public DijkstraLinearFunction(Graph graph) {
		super(graph);
	}
	
	public DijkstraLinearFunction(GraphBounds graphBounds) {
		super(graphBounds);
	}
	
	public void expandVertex(Node target, TimeEntry removed, HashMap<Long, Integer> wasTraversed, 
			PriorityQueue<TimeEntry> queue, HashMap<Long, RouteEntry> parents){
		
		HashMap<Node, Integer> neig = graph.accessNeighborhood(graph.getNode(removed.getId()), removed.getArrivalTime());
		
		for (Node v : neig.keySet()) {
			long vid = v.getId();
			int at = graph.getArrival(removed.getArrivalTime(), neig.get(v));
			int tt = removed.getTravelTime() + neig.get(v);
			TimeEntry newEntry = new TimeEntry(	vid, tt, at, removed.getId());
			
			Edge edge = null;
			int distance = -1;
			
			if(!wasTraversed.containsKey(vid)){					
				queue.offer(newEntry);
				wasTraversed.put(newEntry.getId(), newEntry.getTravelTime());
				
				distance = neig.get(v);
				edge = getEdge(removed.getId(), vid, distance);
				parents.put(vid, new RouteEntry(removed.getId(), distance, edge.getId(), edge.getLabel()));
			}else{
				int cost = wasTraversed.get(vid);
				if(cost != wasRemoved){
					if(cost>newEntry.getTravelTime()){
						queue.remove(newEntry);
						queue.offer(newEntry);
						wasTraversed.remove(newEntry.getId());
						wasTraversed.put(newEntry.getId(), newEntry.getTravelTime());
						
						parents.remove(v);
						distance = neig.get(v);
						edge = getEdge(removed.getId(), vid, distance);
						parents.put(vid, new RouteEntry(removed.getId(), distance, edge.getId(), edge.getLabel()));
					}
				}
			}
		}
	}
	
	private Edge getEdge(long fromNodeId, long toNodeId, int distance) {
		Edge edge = null;
		for(Long outEdge : graph.getOutEdges(fromNodeId)) {
			edge = graph.getEdge(outEdge);
			if ((int) edge.getToNode() == toNodeId && edge.getDistance() == distance) {
				break;
			}
		}
		return edge;
	}
	
	public ObjectCollection<Bound> shortestPathCategories(long nodeId, Set<Integer> categoriesIds, short graphType){

		//TODO Change this PriorityQueue to some FastUtil structure
		PriorityQueue<QueueEntry> unsettledNodes = new PriorityQueue<QueueEntry>();

		LongSet settledNodes = new LongOpenHashSet();
		Long2IntMap shortestDistances = new Long2IntOpenHashMap();

		Int2ObjectMap<Bound> bounds = new Int2ObjectOpenHashMap<Bound>();
		int upper = Integer.MIN_VALUE;
		int waitingTime, timeToService;

		shortestDistances.put(nodeId, 0);

		/*
		 * This queryEntry represents the travel time from this nodeId to 
		 * another vertex (this vertex can be, for example, a PoI. In this 
		 * way, the queryEntry is going to represent the travel time from 
		 * the nodeId to a PoI.).
		 */
		QueueEntry queryEntry = new QueueEntry(nodeId, 0);
		unsettledNodes.add(queryEntry);

		while ((queryEntry = unsettledNodes.poll()) != null){

			if(bounds.keySet().containsAll(categoriesIds) && queryEntry.getTravelTime() > upper){
				return bounds.values();
			}

			if(!settledNodes.contains(queryEntry.getId())) {

				settledNodes.add(queryEntry.getId());

				Node poi = graphBounds.getPoi(queryEntry.getId());

				if(poi != null) {

					int cat = poi.getCategory();

					waitingTime = graphBounds.poiGetCost(queryEntry.getId(), graphType);
					timeToService = queryEntry.getTravelTime() + waitingTime;

					if(bounds.keySet().contains(cat)) {
						
						int cost = bounds.get(cat).getCost();

						if(timeToService < cost)	bounds.put(cat, new Bound(queryEntry.getId(), timeToService));
//                		if(timeToService < cost)	bounds.put(e.getId(), new Bound(e.getId(), timeToService));
						upper = updateUpper(bounds);
						
					} else {
						
						bounds.put(cat, new Bound(queryEntry.getId(), timeToService));
						if(timeToService > upper)	upper = timeToService;
						
					}
				}

				expandVertex(queryEntry, settledNodes, shortestDistances, unsettledNodes, graphType);
			
			}
		}        

		return bounds.values();

	}
	
	public int updateUpper(Int2ObjectMap<Bound> bounds){
		int upper = Integer.MIN_VALUE;
		for(Bound b: bounds.values()){
			if(b.getCost() > upper)	upper = b.getCost();
		}
		return upper;
	}
	
	public void expandVertex(QueueEntry e, LongSet settledNodes, Long2IntMap shortestDistances,
			PriorityQueue<QueueEntry> unsettledNodes){
		
		expandVertex(e, settledNodes, shortestDistances, unsettledNodes, (short) 0);
	
	}
	
	public void expandVertex(QueueEntry e, LongSet settledNodes, Long2IntMap shortestDistances,
			PriorityQueue<QueueEntry> unsettledNodes, short graphType){
		
		Long2IntMap adjacents = graphBounds.accessNeighborhood(graphBounds.getNode(e.getId()), graphType, 0);
		
		if(adjacents != null) {
			
			for (long v : adjacents.keySet()) {
				
				long nodeId = v;
				
				if (settledNodes.contains(nodeId))    continue;

				int shortDist = getShortestDistance(e.getId(), shortestDistances) + adjacents.get(v);

				if (shortDist < getShortestDistance(nodeId, shortestDistances))
				{
					// assign new shortest distance and mark unsettled

					QueueEntry eNew = new QueueEntry(nodeId, shortDist);
					unsettledNodes.remove(eNew);
					unsettledNodes.add(eNew);
					shortestDistances.put(nodeId, shortDist);
				}
			} 
		}
	}
	
	public int getShortestDistance(long id, Long2IntMap minCost)
	{
		if(minCost.containsKey(id))
			return minCost.get(id);
		else
			return Integer.MAX_VALUE;
	}
	
	public Long2DoubleMap shortestPath(long v){
		PriorityQueue<QueueEntry> unsettledNodes = new PriorityQueue<QueueEntry>();
		LongSet settledNodes = new LongOpenHashSet();
		Long2IntMap shortestDistances = new Long2IntOpenHashMap();
		Long2DoubleMap distance = new Long2DoubleOpenHashMap();

		shortestDistances.put(v, 0);
		distance.put(v, 0);
		QueueEntry e = new QueueEntry(v, 0);
		unsettledNodes.add(e);

		while ((e = unsettledNodes.poll()) != null){
			if(!settledNodes.contains(e.getId())){
				settledNodes.add(e.getId());
				distance.put(e.getId(), e.getTravelTime());

				expandVertex(e, settledNodes, shortestDistances, unsettledNodes, (short)0);
			}
		} 
		return distance;
	}

}
