package org.graphast.model;

import static org.graphast.util.GeoUtils.latLongToDouble;
import static org.graphast.util.GeoUtils.latLongToInt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.graphast.enums.CompressionType;
import org.graphast.enums.TimeType;
import org.graphast.geometry.BBox;
import org.graphast.geometry.PoI;
import org.graphast.geometry.PoICategory;
import org.graphast.geometry.Point;
import org.graphast.util.DistanceUtils;
import org.graphast.util.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.davidmoten.grumpy.core.Position;
import com.github.davidmoten.rtree.Entry;
import com.github.davidmoten.rtree.RTree;
import com.github.davidmoten.rtree.geometry.Geometries;
import com.github.davidmoten.rtree.geometry.Rectangle;

import it.unimi.dsi.fastutil.BigArrays;
import it.unimi.dsi.fastutil.ints.IntBigArrayBigList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import it.unimi.dsi.fastutil.objects.ObjectBigArrayBigList;
import it.unimi.dsi.fastutil.objects.ObjectBigList;
import rx.Observable;
import rx.functions.Func1;

public class GraphImpl implements Graph {

	private Logger log = LoggerFactory.getLogger(this.getClass());

	private Long2LongMap nodeIndex = new Long2LongOpenHashMap();

	protected String directory;

	protected String absoluteDirectory;

	private IntBigArrayBigList nodes;

	private IntBigArrayBigList edges;

	private ObjectBigList<String> nodesLabels;

	private ObjectBigList<String> edgesLabels;

	private IntBigArrayBigList edgesCosts;

	private IntBigArrayBigList nodesCosts;

	private IntBigArrayBigList points;

	protected int blockSize = 4096;

	private int[] intCosts;

	protected CompressionType compressionType;

	protected TimeType timeType;

	protected int maxTime = 86400000;

	protected BBox bBox;
	
	protected RTree<String, com.github.davidmoten.rtree.geometry.Point> tree;

	/**
	 * Creates a Graph for the given directory passed as parameter.
	 * 
	 * This constructor will instantiate all lists needed to properly handle the
	 * information of a Graph, e.g. nodes, edges, labels, etc.
	 * 
	 * @param directory
	 *            Directory in which the graph is (or will be) persisted.
	 */
	public GraphImpl(String directory) {
		this(directory, CompressionType.GZIP_COMPRESSION, TimeType.MILLISECOND);
	}

	public GraphImpl(String directory, CompressionType compressionType, TimeType timeType) {
		setDirectory(directory);
		this.compressionType = compressionType;
		setTimeType(timeType);

		nodes = new IntBigArrayBigList();
		edges = new IntBigArrayBigList();
		nodesLabels = new ObjectBigArrayBigList<String>();
		edgesLabels = new ObjectBigArrayBigList<String>();
		nodesCosts = new IntBigArrayBigList();
		edgesCosts = new IntBigArrayBigList();
		points = new IntBigArrayBigList();

		nodeIndex.defaultReturnValue(-1);

		tree = RTree.star().create();

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#save()
	 */
	@Override
	public void save() {
		FileUtils.saveIntList(absoluteDirectory + "/nodes", nodes, blockSize,
				compressionType);
		FileUtils.saveIntList(absoluteDirectory + "/edges", edges, blockSize,
				compressionType);
		FileUtils.saveStringList(absoluteDirectory + "/nodesLabels", nodesLabels,
				blockSize, compressionType);
		FileUtils.saveStringList(absoluteDirectory + "/edgesLabels", edgesLabels,
				blockSize, compressionType);
		FileUtils.saveIntList(absoluteDirectory + "/nodesCosts", nodesCosts, blockSize,
				compressionType);
		FileUtils.saveIntList(absoluteDirectory + "/edgesCosts", edgesCosts, blockSize,
				compressionType);
		FileUtils.saveIntList(absoluteDirectory + "/points", points, blockSize,
				compressionType);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#load()
	 */
	@Override
	public void load() {
		nodes = FileUtils.loadIntList(absoluteDirectory + "/nodes", blockSize,
				compressionType);
		edges = FileUtils.loadIntList(absoluteDirectory + "/edges", blockSize,
				compressionType);
		nodesLabels = FileUtils.loadStringList(absoluteDirectory + "/nodesLabels",
				blockSize, compressionType);
		edgesLabels = FileUtils.loadStringList(absoluteDirectory + "/edgesLabels",
				blockSize, compressionType);
		nodesCosts = FileUtils.loadIntList(absoluteDirectory + "/nodesCosts",
				blockSize, compressionType);
		edgesCosts = FileUtils.loadIntList(absoluteDirectory + "/edgesCosts",
				blockSize, compressionType);
		points = FileUtils.loadIntList(absoluteDirectory + "/points", 
				blockSize, compressionType);
		createNodeIndex();
		findBBox();
		log.info("nodes: {}", this.getNumberOfNodes());
		log.info("edges: {}", this.getNumberOfEdges());
	}

	private void createNodeIndex() {
		long numberOfNodes = getNumberOfNodes();
		NodeImpl node;
		for (int i = 0; i < numberOfNodes; i++) {
			node = (NodeImpl) getNode(i);
			nodeIndex.put(
					BigArrays.index(node.getLatitudeConvertedToInt(),
							node.getLongitudeConvertedToInt()), (long) i);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#addNode(org.graphast.model.GraphastNode)
	 */
	@Override
	public void addNode(Node n) {

		long id;

		NodeImpl node = (NodeImpl) n;

		long labelIndex = storeLabel(node.getLabel(), nodesLabels);
		node.setLabelIndex(labelIndex);
		long costsIndex = storeCosts(node.getCosts(), nodesCosts);
		node.setCostsIndex(costsIndex);
		node.setLabelIndex(labelIndex);

		synchronized (nodes) {
			id = nodes.size64() / Node.NODE_BLOCKSIZE;

			nodes.add(node.getExternalIdSegment());
			nodes.add(node.getExternalIdOffset());
			nodes.add(node.getCategory());
			nodes.add(node.getLatitudeConvertedToInt());
			nodes.add(node.getLongitudeConvertedToInt());
			nodes.add(node.getFirstEdgeSegment());
			nodes.add(node.getFirstEdgeOffset());
			nodes.add(node.getLabelIndexSegment());
			nodes.add(node.getLabelIndexOffset());
			nodes.add(node.getCostsIndexSegment());
			nodes.add(node.getCostsIndexOffset());
		}
		nodeIndex.put(
				BigArrays.index(node.getLatitudeConvertedToInt(),
						node.getLongitudeConvertedToInt()), (long) id);
		node.setId(id);
	}



	// TODO Why we only update the latitude, longitude and FirstEdge?
	// Wouldn't be better if we had a method that updates everything?
	/**
	 * This method will update the IntBigArrayBigList of nodes with need
	 * information of a passed GraphastNode.
	 * 
	 * @param n
	 *            GraphastNode with the informations that must be updated.
	 */
	public void updateNodeInfo(Node n) {

		NodeImpl node = (NodeImpl) n;

		long labelIndex = storeLabel(node.getLabel(), nodesLabels);
		node.setLabelIndex(labelIndex);
		long costsIndex = storeCosts(node.getCosts(), nodesCosts);
		node.setCostsIndex(costsIndex);

		long position = node.getId() * Node.NODE_BLOCKSIZE;
		position = position + 2;

		synchronized (nodes) {
			nodes.set(position++, node.getCategory());
			nodes.set(position++, node.getLatitudeConvertedToInt());
			nodes.set(position++, node.getLongitudeConvertedToInt());
			nodes.set(position++, node.getFirstEdgeSegment());
			nodes.set(position++, node.getFirstEdgeOffset());
			nodes.set(position++, node.getLabelIndexSegment());
			nodes.set(position++, node.getLabelIndexOffset());
			nodes.set(position++, node.getCostsIndexSegment());
			nodes.set(position++, node.getCostsIndexOffset());

		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getNode(long)
	 */
	@Override
	public Node getNode(long id) {

		long position = id * Node.NODE_BLOCKSIZE;
		NodeImpl node = new NodeImpl(BigArrays.index(nodes.getInt(position),
				nodes.getInt(position + 1)), // externalId
				nodes.getInt(position + 2), // category
				latLongToDouble(nodes.getInt(position + 3)), // latitude
				latLongToDouble(nodes.getInt(position + 4)), // longitude
				BigArrays.index(nodes.getInt(position + 5),
						nodes.getInt(position + 6)), // firstEdge
				BigArrays.index(nodes.getInt(position + 7),
						nodes.getInt(position + 8)), // labelIndex
				BigArrays.index(nodes.getInt(position + 9),
						nodes.getInt(position + 10)) // costIndex
				);

		node.setId(id);
		long labelIndex = node.getLabelIndex();
		if (labelIndex >= 0) {
			node.setLabel(getNodesLabels().get(labelIndex));
		}

		long costsIndex = node.getCostsIndex();
		if (costsIndex >= 0) {
			node.setCosts(getNodeCostsByCostsIndex(costsIndex));
		}

		node.validate();

		return node;
	}

	public Edge getEdge(long originNodeId, long destinationNodeId) {

		List<Edge> listOfPossibleEdges = new ArrayList<Edge>();

		for(Long edgeId : this.getOutEdges(originNodeId)) {
			Edge candidateEdge = this.getEdge(edgeId);

			if(candidateEdge.getToNode()==destinationNodeId) {
				listOfPossibleEdges.add(candidateEdge);
			}
		}

		Edge resultEdge = null;

		if(listOfPossibleEdges.size() != 0) {

			resultEdge = listOfPossibleEdges.get(0); 

		} else {
			return null;
		}

		for(Edge possibleResult : listOfPossibleEdges) {

			if(resultEdge.getDistance()>possibleResult.getDistance()) {
				resultEdge = possibleResult;
			}

		}

		return resultEdge;
	}


	// TODO Suggestion: delete this method and keep all these operations in
	// updateEdgeInfo
	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#setEdge(org.graphast.model.GraphastEdge,
	 * long)
	 */
	@Override
	public void setEdge(Edge e, long pos) {

		EdgeImpl edge = (EdgeImpl) e;

		synchronized (edges) {
			edges.set(pos++, edge.getExternalIdSegment());
			edges.set(pos++, edge.getExternalIdOffset());
			edges.set(pos++, edge.getFromNodeSegment());
			edges.set(pos++, edge.getFromNodeOffset());
			edges.set(pos++, edge.getToNodeSegment());
			edges.set(pos++, edge.getToNodeOffset());
			edges.set(pos++, edge.getFromNodeNextEdgeSegment());
			edges.set(pos++, edge.getFromNodeNextEdgeOffset());
			edges.set(pos++, edge.getToNodeNextEdgeSegment());
			edges.set(pos++, edge.getToNodeNextEdgeOffset());
			edges.set(pos++, edge.getDistance());
			edges.set(pos++, edge.getCostsSegment());
			edges.set(pos++, edge.getCostsOffset());
			edges.set(pos++, edge.getGeometrySegment());
			edges.set(pos++, edge.getGeometryOffset());
			edges.set(pos++, edge.getLabelIndexSegment());
			edges.set(pos++, edge.getLabelIndexOffset());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#addEdge(org.graphast.model.GraphastEdge)
	 */
	@Override
	public void addEdge(Edge e) {

		EdgeImpl edge = (EdgeImpl) e;
		long labelIndex = storeLabel(edge.getLabel(), edgesLabels);
		long costsIndex = storeCosts(edge.getCosts(), edgesCosts);
		long geometryIndex = storePoints(edge.getGeometry(), points);
		edge.setLabelIndex(labelIndex);
		edge.setCostsIndex(costsIndex);
		edge.setGeometryIndex(geometryIndex);

		long id;

		synchronized (edges) {
			id = edges.size64() / Edge.EDGE_BLOCKSIZE;

			edges.add(edge.getExternalIdSegment());
			edges.add(edge.getExternalIdOffset());
			edges.add(edge.getFromNodeSegment());
			edges.add(edge.getFromNodeOffset());
			edges.add(edge.getToNodeSegment());
			edges.add(edge.getToNodeOffset());
			edges.add(edge.getFromNodeNextEdgeSegment());
			edges.add(edge.getFromNodeNextEdgeOffset());
			edges.add(edge.getToNodeNextEdgeSegment());
			edges.add(edge.getToNodeNextEdgeOffset());
			edges.add(edge.getDistance());
			edges.add(edge.getCostsSegment());
			edges.add(edge.getCostsOffset());
			edges.add(edge.getGeometrySegment());
			edges.add(edge.getGeometryOffset());
			edges.add(edge.getLabelIndexSegment());
			edges.add(edge.getLabelIndexOffset());
		}
		edge.setId(id);
		updateNeighborhood(edge);
	}

	/**
	 * This method will store the passed list of costs in a ShortBigArrayBigList
	 * and return the position of this insertion.
	 * 
	 * @param c
	 *            list of costs that will be stored
	 * @return the costId (position where the cost was inserted).
	 */
	private long storeCosts(int[] c, IntBigArrayBigList costs) {
		if (c == null || c.length == 0) {
			return -1l;
		}

		long costId;

		synchronized (costs) {
			costId = costs.size64();
			costs.add(c.length);

			for (int i = 0; i < c.length; i++) {
				costs.add(c[i]);
			}
		}
		return costId;
	}

	/**
	 * This method will store the passed label in a ObjectBigList of Strings and
	 * return the position of this insertion.
	 * 
	 * @param label
	 *            String that will be added into the ObjectBigList.
	 * @return the labelId (position where the label was inserted).
	 */
	private long storeLabel(String label, ObjectBigList<String> labelList) {
		// Do not store a null label
		if (label == null) {
			return -1;
		}

		long labelId;

		synchronized (labelList) {
			labelId = labelList.size64();
			labelList.add(label);
		}
		return labelId;
	}

	/**
	 * This method will store the passed list of points in a IntBigArrayBigList
	 * and return the position of this insertion.
	 * 
	 * @param listPoints
	 *            list of points that will be stored
	 * @return the listId (position where the list was inserted).
	 */
	private long storePoints(List<Point> listPoints, IntBigArrayBigList points) {
		if (listPoints == null || listPoints.size() == 0) {
			return -1l;
		}

		long listId;

		synchronized (points) {
			listId = points.size64();
			points.add(listPoints.size());

			for (Point p : listPoints) {
				points.add(latLongToInt(p.getLatitude()));
				points.add(latLongToInt(p.getLongitude()));
			}
		}
		return listId;
	}

	/**
	 * This method will update the IntBigArrayBigList of edges with need
	 * information of a passed Edge.
	 * 
	 * @param edge
	 *            Edge with the informations that must be updated.
	 */
	private void updateEdgeInfo(Edge edge) {

		long pos = edge.getId() * Edge.EDGE_BLOCKSIZE;
		setEdge(edge, pos);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#updateNeighborhood(org.graphast.model.
	 * GraphastEdge)
	 */
	@Override
	public void updateNeighborhood(Edge edge) {

		Node from = getNode(edge.getFromNode());
		from.validate();

		Node to = getNode(edge.getToNode());
		to.validate();

		long eid = edge.getId();

		updateNodeNeighborhood(from, eid);
		updateNodeNeighborhood(to, eid);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphast.model.Graphast#updateNodeNeighborhood(org.graphast.model
	 * .GraphastNode, long)
	 */
	@Override
	public void updateNodeNeighborhood(Node n, long eid) {

		NodeImpl node = (NodeImpl) n;

		if (BigArrays.index(node.getFirstEdgeSegment(),
				node.getFirstEdgeOffset()) == -1) {

			node.setFirstEdge(eid);
			updateNodeInfo(node);

		} else {

			long next = 0;
			EdgeImpl nextEdge = (EdgeImpl) getEdge(BigArrays.index(
					node.getFirstEdgeSegment(), node.getFirstEdgeOffset()));

			while (next != -1) {

				if (node.getId() == nextEdge.getFromNode()) {
					next = nextEdge.getFromNodeNextEdge();
				} else if (node.getId() == nextEdge.getToNode()) {
					next = nextEdge.getToNodeNextEdge();
				}
				if (next != -1) {
					nextEdge = (EdgeImpl) getEdge(next);
				}
			}

			if (node.getId() == nextEdge.getFromNode()) {
				nextEdge.setFromNodeNextEdge(eid);
			} else if (node.getId() == nextEdge.getToNode()) {
				nextEdge.setToNodeNextEdge(eid);
			}

			updateEdgeInfo(nextEdge);

		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getOutEdges(long)
	 */
	@Override
	public LongList getOutEdges(long nodeId) {

		LongList outEdges = new LongArrayList();
		NodeImpl v = (NodeImpl) getNode(nodeId);

		long firstEdgeId = BigArrays.index(v.getFirstEdgeSegment(),
				v.getFirstEdgeOffset());
		Edge nextEdge = getEdge(firstEdgeId);
		long next = 0;

		while (next != -1) {

			if (nodeId == nextEdge.getFromNode()) {
				outEdges.add(nextEdge.getId());
				next = nextEdge.getFromNodeNextEdge();
			} else if (nodeId == nextEdge.getToNode()) {
				next = nextEdge.getToNodeNextEdge();
			}

			if (next != -1) {
				nextEdge = getEdge(next);
			}
		}
		return outEdges;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphast.model.Graphast#getEdgesCosts(it.unimi.dsi.fastutil.longs
	 * .LongList, int)
	 */
	@Override
	public int[] getEdgesCosts(LongList edges, int time) {

		int[] costs = new int[edges.size()];
		Edge e;
		for (int i = 0; i < edges.size(); i++) {
			e = getEdge(edges.get(i));
			costs[i] = getEdgeCost(e, time);
		}
		return costs;

	}

	/**
	 * This method returns all costs of all edges stored in a BigArrayBigList.
	 * 
	 * @return all costs of all edges
	 */
	IntBigArrayBigList getCosts() {

		return edgesCosts;
	}

	/**
	 * This method returns all costs of all nodes stored in a BigArrayBigList.
	 * 
	 * @return all costs of all nodes
	 */
	IntBigArrayBigList getNodesCosts() {

		return nodesCosts;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getOutNeighbors(long)
	 */
	@Override
	public LongList getOutNeighbors(long vid) {
		return getOutNeighbors(vid, 0, false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getOutNeighborsAndCosts(long, int)
	 */
	@Override
	public LongList getOutNeighborsAndCosts(long vid, int time) {
		return getOutNeighbors(vid, time, true);
	}

	private LongList getOutNeighbors(long vid, int time, boolean getCosts) {
		LongList neighborsCosts = new LongArrayList();
		NodeImpl v = (NodeImpl) getNode(vid);
		long firstEdgeId = BigArrays.index(v.getFirstEdgeSegment(),
				v.getFirstEdgeOffset());
		Edge nextEdge = getEdge(firstEdgeId);
		long next = 0;
		while (next != -1) {
			if (vid == nextEdge.getFromNode()) {
				neighborsCosts.add(nextEdge.getToNode());

				if (getCosts) {
					neighborsCosts.add(getEdgeCost(nextEdge, time));
				}
				next = nextEdge.getFromNodeNextEdge();
			} else if (vid == nextEdge.getToNode()) {
				next = nextEdge.getToNodeNextEdge();
			}
			if (next != -1) {
				nextEdge = getEdge(next);
			}
		}

		return neighborsCosts;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getEdge(long)
	 */
	@Override
	public Edge getEdge(long id) {

		long pos = id * Edge.EDGE_BLOCKSIZE;

		long externalId = BigArrays.index(edges.getInt(pos++),
				edges.getInt(pos++));
		long fromId = BigArrays.index(edges.getInt(pos++), edges.getInt(pos++));
		long toId = BigArrays.index(edges.getInt(pos++), edges.getInt(pos++));
		long fromNodeNextEdge = BigArrays.index(edges.getInt(pos++),
				edges.getInt(pos++));
		long toNodeNextEdge = BigArrays.index(edges.getInt(pos++),
				edges.getInt(pos++));
		int distance = edges.getInt(pos++);
		long costsIndex = BigArrays.index(edges.getInt(pos++),
				edges.getInt(pos++));
		long geometryIndex = BigArrays.index(edges.getInt(pos++),
				edges.getInt(pos++));
		long labelIndex = BigArrays.index(edges.getInt(pos++),
				edges.getInt(pos++));

		EdgeImpl edge = new EdgeImpl(externalId, fromId, toId,
				fromNodeNextEdge, toNodeNextEdge, distance, costsIndex,
				geometryIndex, labelIndex, null);

		edge.setId(id);
		if (labelIndex >= 0) {
			edge.setLabel(getEdgesLabels().get(labelIndex));
		}

		if (costsIndex >= 0) {
			edge.setCosts(getEdgeCostsByCostsIndex(costsIndex));
		}

		if (geometryIndex >= 0) {

			edge.setGeometry(getGeometryByGeometryIndex(geometryIndex));

		}

		edge.validate();
		return edge;

	}

	@Override
	public int[] getEdgeCosts(long edgeId) {

		EdgeImpl edge = (EdgeImpl) getEdge(edgeId);
		long costsIndex = edge.getCostsIndex();

		if (costsIndex == -1) {
			return null;
		} else {
			return getEdgeCostsByCostsIndex(costsIndex);
		}
	}

	int[] getEdgeCostsByCostsIndex(long costsIndex) {

		int size = edgesCosts.getInt(costsIndex);
		int[] c = new int[size];
		int i = 0;
		while (size > 0) {
			costsIndex++;
			c[i] = edgesCosts.getInt(costsIndex);
			size--;
			i++;
		}
		return c;
	}

	public int[] getNodeCosts(long nodeId) {

		NodeImpl node = (NodeImpl) getNode(nodeId);
		long costsIndex = node.getCostsIndex();

		if (costsIndex == -1) {
			return null;
		} else {
			return getNodeCostsByCostsIndex(costsIndex);
		}

	}

	public int[] getNodeCostsByCostsIndex(long costsIndex) {

		int size = nodesCosts.getInt(costsIndex);
		int[] c = new int[size];
		int i = 0;
		while (size > 0) {
			costsIndex++;
			c[i] = nodesCosts.getInt(costsIndex);
			size--;
			i++;
		}
		return c;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.graphast.model.Graphast#getEdgeCost(org.graphast.model.GraphastEdge,
	 * int)
	 */
	// TODO getEdgeCost
	@Override
	public Integer getEdgeCost(Edge e, int time) {
		EdgeImpl edge = (EdgeImpl) e;
		long costsIndex = edge.getCostsIndex();
		if (costsIndex < 0) {
			return null;
		}
		int size = edgesCosts.getInt(costsIndex++);

		int intervalSize = (maxTime / size);
		long index = (long) (costsIndex + (time / intervalSize));

		return edgesCosts.getInt(index);

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getEdgePoints(long)
	 */
	@Override
	public List<Point> getGeometry(long id) {
		EdgeImpl edge = (EdgeImpl) getEdge(id);
		long geometryIndex = edge.getGeometryIndex();
		int size = points.getInt(geometryIndex++);
		List<Point> listPoints = new ArrayList<Point>(size);
		while (size > 0) {
			listPoints.add(new Point(latLongToDouble(points
					.getInt(geometryIndex++)), latLongToDouble(points
							.getInt(geometryIndex++))));
			size--;
		}
		return listPoints;
	}

	public List<Point> getGeometryByGeometryIndex(long geometryIndex) {

		int size = points.getInt(geometryIndex++);
		List<Point> listPoints = new ArrayList<Point>(size);
		while (size > 0) {
			listPoints.add(new Point(latLongToDouble(points
					.getInt(geometryIndex++)), latLongToDouble(points
							.getInt(geometryIndex++))));
			size--;
		}
		return listPoints;
	}

	Long getNodeId(int latitude, int longitude) {

		Long result = nodeIndex.get(BigArrays.index(latitude, longitude));

		if (result != -1) {

			return result;

		}

		return null;

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getNode(double, double)
	 */
	@Override
	public Long getNodeId(double latitude, double longitude) {

		int lat, lon;

		lat = latLongToInt(latitude);
		lon = latLongToInt(longitude);
		if (getNodeId(lat, lon) == null) {
			return getNearestNode(latitude, longitude).getId();
		}
		else {
			return getNodeId(lat, lon);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getNodeLabel(long)
	 */
	@Override
	public String getNodeLabel(long id) {
		Node node = this.getNode(id);
		return node.getLabel();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getEdgeLabel(long)
	 */
	@Override
	public String getEdgeLabel(long id) {
		Edge edge = this.getEdge(id);
		return edge.getLabel();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getNodes()
	 */
	@Override
	public IntBigArrayBigList getNodes() {
		return nodes;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getEdges()
	 */
	@Override
	public IntBigArrayBigList getEdges() {
		return edges;
	}

	ObjectBigList<String> getNodesLabels() {
		return nodesLabels;
	}

	void setNodesLabels(ObjectBigList<String> labels) {
		this.nodesLabels = labels;
	}

	ObjectBigList<String> getEdgesLabels() {
		return edgesLabels;
	}

	void setEdgesLabels(ObjectBigList<String> labels) {
		this.edgesLabels = labels;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#logNodes()
	 */
	@Override
	public void logNodes() {
		for (int i = 0; i < nodes.size64() / Node.NODE_BLOCKSIZE; i++) {
			log.info(getNode(i).toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#logEdges()
	 */
	@Override
	public void logEdges() {

		for (long i = 0; i < (edges.size64() / Edge.EDGE_BLOCKSIZE); i++) {
			log.info(getEdge(i).toString());
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getNumberOfNodes()
	 */
	@Override
	public long getNumberOfNodes() {
		return getNodes().size64() / Node.NODE_BLOCKSIZE;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.graphast.model.Graphast#getNumberOfEdges()
	 */
	@Override
	public long getNumberOfEdges() {
		return getEdges().size64() / Edge.EDGE_BLOCKSIZE;
	}

	public Long2IntMap accessNeighborhood(Node v) {

		Long2IntMap neighbors = new Long2IntOpenHashMap();

		for (Long e : this.getOutEdges(v.getId())) {

			Edge edge = this.getEdge(e);
			long neighborNodeId = edge.getToNode();
			int cost = edge.getDistance();
			if (!neighbors.containsKey(neighborNodeId)) {
				neighbors.put(neighborNodeId, cost);
			} else {
				if (neighbors.get(neighborNodeId) > cost) {
					neighbors.put(neighborNodeId, cost);
				}
			}
		}

		return neighbors;

	}

	// TODO Reimplement this method
	public HashMap<Node, Integer> accessNeighborhood(Node v, int time) {

		HashMap<Node, Integer> neig = new HashMap<Node, Integer>();
		for (Long e : this.getOutEdges(v.getId())) {
			Edge edge = this.getEdge(e);
			long vNeig = edge.getToNode();
			int cost = getEdgeCost(edge, time);
			// int cost = edge.getDistance();
			if (!neig.containsKey(vNeig)) {

				neig.put(getNode(vNeig), cost);
			} else {
				if (neig.get(vNeig) > cost) {
					neig.put(getNode(vNeig), cost);
				}
			}
		}

		return neig;

	}

	public boolean hasNode(long id) {
		try {
			long position = id * Node.NODE_BLOCKSIZE;
			if (nodes.contains(position)) {
				return true;
			} else {
				return false;
			}
		} catch (NullPointerException e) {
			return false;
		}
	}

	public boolean hasNode(Node n) {

		NodeImpl node = (NodeImpl) n;

		try {
			if (nodeIndex.containsKey(BigArrays.index(
					node.getLatitudeConvertedToInt(),
					node.getLongitudeConvertedToInt()))) {
				return true;
			} else {
				return false;
			}
		} catch (NullPointerException e) {
			return false;
		}
	}

	@Override
	public boolean hasNode(double latitude, double longitude) {
		int lat = latLongToInt(latitude);
		int lon = latLongToInt(longitude);
		try {
			if (nodeIndex.containsKey(BigArrays.index(lat, lon))) {
				return true;
			} else {
				return false;
			}
		} catch (NullPointerException e) {
			return false;
		}
	}

	@Override
	public Node addPoi(long id, double lat, double lon, int category,
			LinearFunction[] costs) {
		int[] intCosts = linearFunctionArrayToCostIntArray(costs);
		Node poi = new NodeImpl(id, category, lat, lon, 0l, 0l, 0l, intCosts);
		this.addNode(poi);
		return poi;
	}

	public Node addPoi(long id, double lat, double lon, int category) {
		Node poi = new NodeImpl(id, lat, lon, category);
		this.addNode(poi);
		return poi;
	}

	public int poiGetCost(long vid, int time) {
		int i = 0;
		LinearFunction[] lf = convertToLinearFunction(getPoiCost(vid));
		while (lf[i].getEndInterval() <= time) {
			i++;
		}
		return lf[i].calculateCost(time);
	}

	public int poiGetCost(long vid) {
		LinearFunction[] lf = convertToLinearFunction(getPoiCost(vid));
		return lf[0].calculateCost(0);
	}

	public int[] getPoiCost(long vid) {
		return getNodeCosts(vid);
	}

	public LinearFunction[] convertToLinearFunction(int[] costs) {
		int tetoCosts = (int)Math.ceil(costs.length / 2.0);
		LinearFunction[] result = new LinearFunction[tetoCosts];
		int interval = maxTime / (tetoCosts);
		int startInterval = 0; 
		int endInterval = interval;
		for (int i = 0; i < tetoCosts; i++) {
			result[i] = new LinearFunction(startInterval, costs[i],
					endInterval, costs[i]);
			startInterval = endInterval;
			endInterval = endInterval + interval;
		}

		return result;
	}

	int[] linearFunctionArrayToCostIntArray(LinearFunction[] linearFunction) {
		intCosts = new int[linearFunction.length];
		for (int i = 0; i < linearFunction.length; i++) {
			intCosts[i] = (linearFunction[i].getEndCost() + linearFunction[i].getStartCost())/2;
		}
		return intCosts;
	}

	public int getMaximunCostValue(int[] costs) {

		if (costs == null) {
			// throw new IllegalArgumentException("Costs can not be null.");
			return -1;
		}

		int max = costs[0];

		for (int i = 0; i < costs.length; i++) {

			if (costs[i] > max) {
				max = costs[i];
			}
		}

		return max;
	}

	public int getMinimunCostValue(int[] costs) {

		if (costs == null) {
			// throw new IllegalArgumentException("Costs can not be null.");
			return -1;
		}

		int min = costs[0];

		for (int i = 0; i < costs.length; i++) {

			if (costs[i] < min) {
				min = costs[i];
			}
		}

		return min;
	}

	public boolean isPoi(long vid) {
		return getNode(vid).getCategory() >= 0;
	}

	public Node getPoi(long vid) {
		Node v = getNode(vid);
		if (v.getCategory() < 0)
			return null;
		else
			return v;
	}

	// TODO Verify if this access is correct
	@Override
	public IntSet getCategories() {
		IntSet categories = new IntOpenHashSet();

		for (int i = 0; i < getNumberOfNodes(); i++) {
			long position = i * Node.NODE_BLOCKSIZE;
			int category = getNodes().getInt(position + 2);
			if (category != -1) {

				categories.add(category);
			}
			// long position = i*Node.NODE_BLOCKSIZE;
			// long vid = ga.getNodes().getInt(position);
			// bounds.put(vid, d.shortestPathPoi(vid, -1).getDistance());
		}

		return categories;
	}

	@Override
	public CompressionType getCompressionType() {
		return compressionType;
	}

	@Override
	public void setCompressionType(CompressionType compressionType) {
		this.compressionType = compressionType;
	}

	public void reverseGraph() {

		for (long i = 0; i < (edges.size64() / Edge.EDGE_BLOCKSIZE); i++) {

			long pos = i * Edge.EDGE_BLOCKSIZE;

			int externalIdSegment = edges.getInt(pos++);
			int externalIdOffset = edges.getInt(pos++);
			int fromNodeSegment = edges.getInt(pos++);
			int fromNodeOffset = edges.getInt(pos++);
			int toNodeSegment = edges.getInt(pos++);
			int toNodeOffset = edges.getInt(pos++);
			int fromNodeNextEdgeSegment = edges.getInt(pos++);
			int fromNodeNextEdgeOffset = edges.getInt(pos++);
			int toNodeNextEdgeSegment = edges.getInt(pos++);
			int toNodeNextEdgeOffset = edges.getInt(pos++);

			pos = i * Edge.EDGE_BLOCKSIZE;
			edges.set(pos++, externalIdSegment);
			edges.set(pos++, externalIdOffset);
			edges.set(pos++, toNodeSegment);
			edges.set(pos++, toNodeOffset);
			edges.set(pos++, fromNodeSegment);
			edges.set(pos++, fromNodeOffset);
			edges.set(pos++, toNodeNextEdgeSegment);
			edges.set(pos++, toNodeNextEdgeOffset);
			edges.set(pos++, fromNodeNextEdgeSegment);
			edges.set(pos++, fromNodeNextEdgeOffset);

		}
	}

	public int getMaxTime() {
		return maxTime;
	}

	public void setMaxTime(int maxTime) {
		this.maxTime = maxTime;
	}

	@Override
	public TimeType getTimeType() {
		return timeType;
	}

	@Override
	public void setTimeType(TimeType timeType) {
		this.timeType = timeType;

		if(timeType == TimeType.MILLISECOND) {
			maxTime = 86400000;
		} else if(timeType == TimeType.SECOND){
			maxTime = 86400;
		} else if(timeType == TimeType.MINUTE) {
			maxTime = 1440;
		} else {
			maxTime = 24;
		}

	}

	public void setEdgeCosts(long edgeId, int[] costs) {

		EdgeImpl edge = (EdgeImpl) getEdge(edgeId);
		edge.setCosts(costs);

		/*
		 * In this part of the method setEdgeCosts, we're multiplying the
		 * edgeCostsSize by -1 because we can optimize the entire array of costs
		 * by shifting the unused positions (we'll know that a sequence of
		 * positions is not being used by the minus sign in front of the
		 * edgeCostsSize).
		 */
		long costsIndex = edge.getCostsIndex();
		if (costsIndex != -1) {
			int edgeCostsSize = edgesCosts.getInt(costsIndex);
			edgesCosts.set(costsIndex, -edgeCostsSize);
		}
		long position = edge.getId() * Edge.EDGE_BLOCKSIZE;
		costsIndex = storeCosts(edge.getCosts(), edgesCosts);
		edge.setCostsIndex(costsIndex);

		position = position + 11;

		synchronized (edges) {

			edges.set(position++, edge.getCostsSegment());
			edges.set(position++, edge.getCostsOffset());

		}

	}

	public void setNodeCosts(long nodeId, int[] costs) {

		NodeImpl node = (NodeImpl) getNode(nodeId);
		node.setCosts(costs);

		/*
		 * In this part of the method setNodeCosts, we're multiplying the
		 * nodeCostsSize by -1 because we can optimize the entire array of costs
		 * by shifting the unused positions (we'll know that a sequence of
		 * positions is not being used by the minus sign in front of the
		 * nodeCostsSize).
		 */
		long costsIndex = node.getCostsIndex();
		if (costsIndex != -1) {
			int nodeCostsSize = nodesCosts.getInt(costsIndex);
			nodesCosts.set(costsIndex, -nodeCostsSize);
		}
		long position = node.getId() * Node.NODE_BLOCKSIZE;
		costsIndex = storeCosts(node.getCosts(), nodesCosts);
		node.setCostsIndex(costsIndex);

		position = position + 9;

		synchronized (nodes) {

			nodes.set(position++, node.getCostsIndexSegment());
			nodes.set(position++, node.getCostsIndexOffset());

		}

	}

	public int getArrival(int dt, int tt) {
		int arrivalTime = dt + tt;

		arrivalTime = arrivalTime % maxTime;
		return arrivalTime;
	}

	// TODO This method must be improved. It should use a spatial index to 
	// be much more efficient.
	// See rtree implementation in: https://github.com/davidmoten/rtree 
	public Node getNearestNode (double latitude, double longitude) {
//		StopWatch sw = new StopWatch();
//		sw.start();
		double distanceKm = 0.1;
		
		com.github.davidmoten.rtree.geometry.Point pointToSearch = com.github.davidmoten.rtree.geometry.Geometries.point(longitude, latitude);

		
		List<Entry<String, com.github.davidmoten.rtree.geometry.Point>> list = search(this.tree, pointToSearch, distanceKm)
			        // get the result
			                .toList().toBlocking().single();
		
		while(list.size() == 0) {
			
			distanceKm = distanceKm*2;
			
			list = search(this.tree, pointToSearch, distanceKm).toList().toBlocking().single();
			
		}

		if(list.size()>1) {
		
			Node point = new NodeImpl();
			point.setLatitude(latitude);
			point.setLongitude(longitude);
			Node nearestNode = getNode(Integer.parseInt(list.get(0).value()));
			Node currentNode;
			double currentDistance;
			double nearestDistance = DistanceUtils.distanceLatLong(point, nearestNode);
			
			for (int i = 1; i<list.size(); i++) {
				currentNode = getNode(Integer.parseInt(list.get(i).value()));
				currentDistance = DistanceUtils.distanceLatLong(point, currentNode);
				if (currentDistance < nearestDistance) {
					nearestNode = currentNode;
					nearestDistance = currentDistance;
				}
			}
			
			return nearestNode;
			
		} else {
			return getNode(getNodeId(list.get(0).geometry().y(), list.get(0).geometry().x()));
		}
		
//		Node point = new NodeImpl();
//		point.setLatitude(latitude);
//		point.setLongitude(longitude);
//		Node nearestNode = getNode(nodes.get(0));
//		Node currentNode;
//		double currentDistance;
//		double nearestDistance = DistanceUtils.distanceLatLong(point, nearestNode);
//		for (long i = 1; i<getNumberOfNodes(); i++) {
//			currentNode = getNode(i);
//			currentDistance = DistanceUtils.distanceLatLong(point, currentNode);
//			if (currentDistance < nearestDistance) {
//				nearestNode = currentNode;
//				nearestDistance = currentDistance;
//			}
//		}

//		sw.stop();
		//log.debug("Execution Time of getNearestNode(): {}ms", sw.getTime());

		
	}
	
	//TODO REMOVE THIS METHOD
	public static <T> Observable<Entry<T, com.github.davidmoten.rtree.geometry.Point>> search(RTree<T, com.github.davidmoten.rtree.geometry.Point> tree, com.github.davidmoten.rtree.geometry.Point lonLat,
            final double distanceKm) {
        // First we need to calculate an enclosing lat long rectangle for this
        // distance then we refine on the exact distance
        final Position from = Position.create(lonLat.y(), lonLat.x());
        Rectangle bounds = createBounds(from, distanceKm);

        return tree
        // do the first search using the bounds
                .search(bounds)
                // refine using the exact distance
                .filter(new Func1<Entry<T, com.github.davidmoten.rtree.geometry.Point>, Boolean>() {
                    @Override
                    public Boolean call(Entry<T, com.github.davidmoten.rtree.geometry.Point> entry) {
                    	com.github.davidmoten.rtree.geometry.Point p = entry.geometry();
                        Position position = Position.create(p.y(), p.x());
                        return from.getDistanceToKm(position) < distanceKm;
                    }
                });
    }
	
	private static Rectangle createBounds(final Position from, final double distanceKm) {
        // this calculates a pretty accurate bounding box. Depending on the
        // performance you require you wouldn't have to be this accurate because
        // accuracy is enforced later
        Position north = from.predict(distanceKm, 0);
        Position south = from.predict(distanceKm, 180);
        Position east = from.predict(distanceKm, 90);
        Position west = from.predict(distanceKm, 270);

        return Geometries.rectangle(north.getLat(),west.getLon(),south.getLat(),east.getLon());
        
        
        
//        return Geometries.rectangle(west.getLon(), south.getLat(), east.getLon(), north.getLat());
    }

	public boolean equals(Graph obj) {
		if((obj.getNumberOfNodes() == this.getNumberOfNodes()) && (obj.getNumberOfEdges() == this.getNumberOfEdges())) {
			for(int i = 0; i < this.getNumberOfNodes(); i++) {
				if(!obj.getNode(i).equals(this.getNode(i))) {
					return false;
				}
			}
			for(int i = 0; i < this.getNumberOfEdges(); i++) {
				if(!obj.getEdge(i).equals(this.getEdge(i))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}
	public void setNodeCategory(long nodeId, int category) {
		long position = nodeId * Node.NODE_BLOCKSIZE;
		getNodes().set(position+2, category);
	}

	public void setEdgeGeometry(long edgeId, List<Point> geometry) {
		EdgeImpl e = (EdgeImpl) this.getEdge(edgeId);
		e.setGeometry(geometry);
		long geometryIndex = storePoints(e.getGeometry(), points);
		e.setGeometryIndex(geometryIndex);
		this.updateEdgeInfo(e);
	}

	@Override
	public BBox getBBox() {
		if (bBox == null) {
			findBBox();
		}
		return bBox;
	}

	@Override
	public void setBBox(BBox bBox) {
		this.bBox = bBox;
	}

	private void findBBox() {
		Node node = this.getNode(0);
		Node minLatNode = null, minLongNode = null, maxLatNode = null, maxLongNode = null;
		BBox bBox = new BBox(node.getLatitude(), node.getLongitude(), node.getLatitude(), node.getLongitude());

		for (long i = 1; i < this.getNumberOfNodes(); i++) {
			node = this.getNode(i);
			if (node.getLatitude() < bBox.getMinLatitude()) {
				minLatNode = node;
				bBox.setMinLatitude(node.getLatitude());
			}
			if (node.getLatitude() > bBox.getMaxLatitude()) {
				minLongNode = node;
				bBox.setMaxLatitude(node.getLatitude());
			}
			if (node.getLongitude() < bBox.getMinLongitude()) {
				maxLatNode = node;
				bBox.setMinLongitude(node.getLongitude());
			}
			if (node.getLongitude() > bBox.getMaxLongitude()) {
				maxLongNode = node;
				bBox.setMaxLongitude(node.getLongitude());
			}
		}
		if (minLatNode != null && maxLatNode != null && minLongNode != null && maxLongNode != null) {
			log.debug("minLatitude: {},{}", minLatNode.getLatitude(), minLatNode.getLongitude());
			log.debug("maxLatitude: {},{}", maxLatNode.getLatitude(), maxLatNode.getLongitude());
			log.debug("minLongitude: {},{}", minLongNode.getLatitude(), minLongNode.getLongitude());
			log.debug("maxLongitude: {},{}", maxLongNode.getLatitude(), maxLongNode.getLongitude());
		}
		setBBox(bBox);
	}

	public List<PoI> getPOIs() {
		return getPOIs(null);
	}
	
	public List<PoI> getPOIs(Integer categoryId) {
		List<PoI> result = new ArrayList<>();
		for (long i = 0; i < this.getNumberOfNodes(); i++) {
			Node n = this.getNode(i);
			if ((categoryId == null && n.getCategory() >= 0) || 
					(categoryId != null && n.getCategory() == categoryId)) {
				PoICategory poiCategory = new PoICategory(n.getCategory());
				result.add(new PoI(n.getLabel(), n.getLatitude(), n.getLongitude(), poiCategory));
			}
		}
		return result;
	}

	public List<Integer> getPOICategories() {
		List<Integer> result = new ArrayList<Integer>();
		for (long i = 0; i < this.getNumberOfNodes(); i++) {
			Node n = this.getNode(i);
			if ( n.getCategory() >= 0 && (! result.contains(n.getCategory())) ) {
				result.add(n.getCategory());
			}
		}
		Collections.sort(result);
		return result;
	}
	
	public String getDirectory() {
		return directory;
	}

	public String getAbsoluteDirectory() {
		return absoluteDirectory;
	}
	
	public void setDirectory(String directory) {
		this.absoluteDirectory = FileUtils.getAbsolutePath(directory);
		this.directory = directory;
	}
	
	@Override
	public void addNodeInIndex(Node n) {
	
		com.github.davidmoten.rtree.geometry.Point point = com.github.davidmoten.rtree.geometry.Geometries.point(n.getLongitude(), n.getLatitude());
		
		this.tree = tree.add(n.getId().toString(), point);
		
	}
	
}
