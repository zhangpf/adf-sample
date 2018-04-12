package NUDT.module.complex.utils;

import java.awt.Point;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import NUDT.module.algorithm.pathplanning.pov.EscapePoint;
import NUDT.utils.Ruler;
import NUDT.utils.Util;
import NUDT.utils.geom.ExpandApexes;
import NUDT.utils.MathTools;

import adf.agent.info.WorldInfo;
import rescuecore2.standard.entities.Road;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import rescuecore2.misc.Pair;
import rescuecore2.standard.entities.Blockade;
import rescuecore2.standard.entities.Edge;

public class RoadTools {
	
	private static Blockade getBlockade(EntityID entityID, WorldInfo wi)
	{
		StandardEntity entity = wi.getEntity(entityID);
		if(entity instanceof Blockade)
			return (Blockade)entity;
		return null;
	}
	
	private static List<Blockade> getBlockades(Road road, WorldInfo wi)
	{
		List<Blockade> blockades = new LinkedList<Blockade>();
		for(EntityID blockadeID : road.getBlockades())
		{
			Blockade blk = getBlockade(blockadeID, wi);
			if(blk != null)
				blockades.add(blk);
		}
		return blockades;
	}
	
	public static boolean isPassable(Road road, WorldInfo wi)
	{
		if (isAllEdgePassable(road) || isOneEdgeUnpassable(road)) {
			
			/*for (CSUBlockade next : getCsuBlockades()) {
				if (next.getPolygon().contains(selfRoad.getX(), selfRoad.getY()))
					return false;
			}*/
			// return true;
			// TODO July 9, 2014  Time: 2:58pm
			return getPassableEdge(road, wi).size() > 1;      ///why > 
		} else {
			
			List<Blockade> blockades = getBlockades(road, wi);
			
			for (EscapePoint next : getEscapePoint(road, 500, wi)) {
				blockades.removeAll(next.getRelateBlockade());
			}
			
			if (blockades.isEmpty())
				return true;
			return false;
		}
	}
	
	
	public static List<EscapePoint> getEscapePoint(Road road, int threshold, WorldInfo wi) {
		List<EscapePoint> m_p_points = new ArrayList<>();
		
		for (EntityID nextID : road.getBlockades()) {
			Blockade next = getBlockade(nextID, wi);
			if (next == null)
				continue;
			Polygon expan = Util.getPolygon(next.getApexes());
			
			for(Edge edge : road.getEdges()) {
				EscapePoint p = findPoints(edge, expan, next);
				if (p == null) {
					continue;
				} else {
					m_p_points.add(p);
				}
			}
		}
		
		filter(road, m_p_points, threshold, wi);
		return m_p_points;
	}
	
	private static EscapePoint findPoints(Edge edge, Polygon expan, Blockade next) {
		if (edge.isPassable()) {
			// do nothing
		} else {
			if (Util.hasIntersection(expan, edge.getLine())) {
				return null;
			}
			double minDistance = Double.MAX_VALUE, distance;
			Pair<Integer, Integer> minDistanceVertex = null;
			
			for (Pair<Integer, Integer> vertex : Util.getVertices(next.getApexes())) {
				
				Pair<Double, Boolean> dis = MathTools.ptSegDistSq(edge.getStart().getX(), 
						edge.getStart().getY(), edge.getEnd().getX(), 
						edge.getEnd().getY(), vertex.first(), vertex.second());
				
				if (dis.second().booleanValue())
					continue;
				distance = dis.first().doubleValue();
				
				if (distance < minDistance) {
					minDistance = distance;
					minDistanceVertex = vertex;
				}
			}
			
			if (minDistanceVertex == null)
				return null;
			
			Point2D perpendicular = GeometryTools2D.getClosestPoint(edge.getLine(), 
					new Point2D(minDistanceVertex.first(), minDistanceVertex.second()));
			
			Point middlePoint = Util.getMiddle(minDistanceVertex, perpendicular);
			
			Point2D vertex = new Point2D(minDistanceVertex.first(), minDistanceVertex.second());
			Point2D perpenPoint = new Point2D(perpendicular.getX(), perpendicular.getY());
			
			Line2D lin = new Line2D(vertex, perpenPoint);
			
			return new EscapePoint(middlePoint, lin, next);
		}
		
		return null;
	}
	
	private static void filter(Road road, List<EscapePoint> m_p_points, int threshold, WorldInfo wi) {
		Mark:for (Iterator<EscapePoint> itor = m_p_points.iterator(); itor.hasNext(); ) {
			
			EscapePoint m_p = itor.next();
			for (Edge edge : road.getEdges()) {
				if (edge.isPassable())
					continue;
				if (Util.pointToSegWithinThreshold(edge.getLine(), m_p.getUnderlyingPoint(), threshold / 2)) {
					itor.remove();
					continue Mark;
				}
			}
			
			for (EntityID blockadeID : road.getBlockades()) {
				Blockade blockade = getBlockade(blockadeID, wi);
				if (blockade == null)
					continue;
				Polygon polygon = Util.getPolygon(blockade.getApexes());
				Polygon po = ExpandApexes.expandApexes(blockade, 200);
				
				
				if (po.contains(m_p.getLine().getEndPoint().getX(), m_p.getLine().getEndPoint().getY())) {
					
					Set<Point2D> intersections = Util.getIntersections(polygon, m_p.getLine());
					
					double minDistance = Double.MAX_VALUE, distance;
					Point2D closest = null;
					boolean shouldRemove = false;
					for (Point2D inter : intersections) {
						distance = Ruler.getDistance(m_p.getLine().getOrigin(), inter);
						
						if (distance > threshold && distance < minDistance) {
							minDistance = distance;
							closest = inter;
						}
						shouldRemove = true;
					}
					
					if (closest != null) {
						Point p = Util.getMiddle(m_p.getLine().getOrigin(), closest);
						m_p.getUnderlyingPoint().setLocation(p);
						m_p.addCsuBlockade(blockade);
					} else if (shouldRemove){
						itor.remove();
						continue Mark;
					}
				}
				
				if (po.contains(m_p.getUnderlyingPoint())) {
					itor.remove();
					continue Mark;
				}
			}
		}
	}
	
	/**
	 * 所有边都有邻居
	 * @param road
	 * @return
	 */
	public static boolean isAllEdgePassable(Road road) {
		for (Edge next : road.getEdges()) {
			if (!next.isPassable())
				return false;
		}
		return true;
	}
	
	/**
	 * 只有一条边没有邻居
	 * @param road
	 * @return
	 */
	public static boolean isOneEdgeUnpassable(Road road) {
		int count = 0;
		for (Edge next : road.getEdges()) {
			if (!next.isPassable()) 
				count++;
		}
		
		if (count == 1)
			return true;
		else
			return false;
	}
	
	/**
	 * 
	 * 获取这条道路所有能通过的边，
	 * 能通过是指有邻居并且没有被路障堵住
	 * @return a set of passable edge.
	 */
	public static Set<Edge> getPassableEdge(Road road, WorldInfo wi) {
		Set<Edge> result = new HashSet<>();
		
		for (Edge next : road.getEdges()) {
			//isPassable()表示有邻居，isBlocked()判断是否被路障堵住
			if (next.isPassable() && !isBlocked(next, road, wi)) {
				result.add(next);
			}
		}
		
		return result;
	}
	
	public static boolean isRoadCenterBlocked(Road road, WorldInfo wi)
	{
		for (EntityID blockadeID : road.getBlockades()) {
			Blockade next = getBlockade(blockadeID, wi);
			if(next == null)
				continue;
			if (Util.getPolygon(next.getApexes()).contains(road.getX(), road.getY()))
				return true;
		}
		return false;
	}
	
	/**
	 * 判断一条有邻居边是否被堵住
	 * 如果被堵住，计算出“开口”的两个点
	 * 注释中的凸包实际是多边形
	 * @param edge
	 * @param road
	 * @param wi
	 * @return
	 */
	public static boolean isBlocked(Edge edge, Road road, WorldInfo wi) {
		Polygon expand = null;
		boolean isStartBlocked = false, isEndBlocked = false;
		Point2D openPartStart = null, openPartEnd = null;
		
		List<Line2D> blockedPart = new ArrayList<Line2D>();
		
		/*
		 * 遍历每一个路障，如果当前路障直接挡住了整个edge，则直接返回true
		 * 否则，将当前路障遮住edge的部分记录到blockPart中
		 * 最后再判断blockPart是否挡住了整个edge
		 */
		for (EntityID nextID : road.getBlockades()) {
			Blockade next = getBlockade(nextID, wi);
			if(next == null)
				continue;
			
			isStartBlocked = false;
			isEndBlocked = false;
			openPartStart = null;
			openPartEnd = null;
			
			/*计算线段与凸包的交点
			  有3中情况：
				1. 线段在凸包内部，此时没有交点
				2. 线段在凸包外部，此时没有交点
				3. 线段部分在凸包内部
				  3.1 线段与凸包只有1个交点
				     3.1.1 线段起点在凸包内
				     3.1.2 线段终点在凸包内
				  3.2 线段与凸包有两个交点(非凸多边形的话，可能有多于两个交点)
			*/
			
			
			//为了便于计算，将路障扩张一点
			expand = ExpandApexes.expandApexes(next, 10);
			
			//判断边的起点与终点是否被路障覆盖
			if (expand.contains(edge.getStart().getX(), edge.getStart().getY())) {
				isStartBlocked = true;
			} else if (expand.contains(edge.getEnd().getX(), edge.getEnd().getY())) {
				isEndBlocked = true;
			}
			
			//情况1：如果多边形覆盖了一个线段的起点与终点，则认为这条线段是不能通过的
			if (isStartBlocked && isEndBlocked)
				return true;

			
			Set<Point2D> intersections = Util.getIntersections(expand, edge.getLine());
			
			//情况2：因为情况1已经排除，此时，线段与多边形没有交点，说明没有block
			if(intersections.isEmpty())
			{
				continue;
			}
			
			//计算出代表开口的两个点
			//情况3.1.1：起点被覆盖的情况，此时开口的终点是线段的终点，开口的起点是交点中离开口终点最近的点
			if (isStartBlocked) {
				double minDistance = Double.MAX_VALUE, distance;
				openPartEnd = edge.getEnd();
				for (Point2D point : intersections) {
					distance = Ruler.getDistance(point, openPartEnd);
					if (distance < minDistance) {
						minDistance = distance;
						openPartStart = point;
					}
				}
				//如果开口过小，认为不能通过
				if(Ruler.getDistance(openPartStart, openPartEnd) < 200)
				{
					return true;
				}
				blockedPart.add(new Line2D(edge.getStart(), openPartStart));
			}
			//情况3.1.2：情况终点被覆盖的情况，此时开口的起点是线段的起点，开口的终点是交点中离开口起点最近的点
			else if (isEndBlocked) {
				double minDistance = Double.MAX_VALUE, distance;
				openPartStart = edge.getStart();
				for (Point2D point : intersections) {
					distance = Ruler.getDistance(point, openPartStart);
					if (distance < minDistance) {
						minDistance = distance;
						openPartEnd = point;
					}
				}
				//如果开口过小，认为不能通
				if(Ruler.getDistance(openPartStart, openPartEnd) < 200)
				{
					return true;
				}
				blockedPart.add(new Line2D(openPartEnd, edge.getEnd()));
			}
			//情况3.2：多边形盖住了线段中间的“若干”部分
			else 
			{
				Line2D blockedsegment = getLongestLine(edge, intersections);
				if(blockedsegment != null)
				{
					blockedPart.add(blockedsegment);
				}
			}	
		}

		List<Line2D> mergedLines = mergeSegments(blockedPart);		
		return canPass(edge, mergedLines);
	}
	
	/**
	 * segs是edge上互不相交的若干线段，判断edge是否为可通过的
	 * segs要根据起点排好序
	 * @param edge 要判断能否通过的边
	 * @param segs edge上的若干线段
	 * @return
	 */
	public static boolean canPass(Edge edge, List<Line2D> segs)
	{
		//segs.add(new Line2D(edge.getStart(), edge.getStart()));
		//segs.add(new Line2D(edge.getEnd(), edge.getEnd()));
		//sortSegmentsByLeftPoint(segs);
		segs.add(0, new Line2D(edge.getStart(), edge.getStart()));
		segs.add(segs.size(), new Line2D(edge.getEnd(), edge.getEnd()));
		//PrintSegs(segs);
		int len = segs.size();
		for(int i=0; i<len-1; i++)
		{
			if(Ruler.getDistance(segs.get(i), segs.get(i+1)) >= 200.0)
				return true;
		}
		return false;
	}
	
	public static void PrintSegs(List<Line2D> segs)
	{
		for(Line2D seg : segs)
		{
			System.out.printf("(%f, %f) -> (%f, %f)\n", seg.getOrigin().getX(), seg.getOrigin().getY(), seg.getEndPoint().getX(), seg.getEndPoint().getY());
		}
		
	}
	
	/**
	 * points是线段edge上的若干点，返回覆盖points集合的最短线段
	 * @param 
	 * @return
	 */
	private static Line2D getLongestLine(Edge edge, Collection<Point2D> points)
	{
		if(points.isEmpty())
		{
			return null;
		}
		Point2D mostLeft = points.iterator().next();
		Point2D mostRight = mostLeft;
		double minDistanceLeft = Ruler.getDistance(edge.getStart(), mostLeft);
		double minDistanceRight = Ruler.getDistance(edge.getEnd(), mostRight);
		
		while(points.iterator().hasNext())
		{
			Point2D tmpPoint = points.iterator().next();
			double tmpDistanceLeft = Ruler.getDistance(edge.getStart(), tmpPoint);
			double tmpDistanceRight = Ruler.getDistance(edge.getEnd(), tmpPoint);
			if(tmpDistanceLeft < minDistanceLeft)
			{
				minDistanceLeft = tmpDistanceLeft;
				mostLeft = tmpPoint;
			}
			if(tmpDistanceRight < minDistanceRight)
			{
				minDistanceRight = tmpDistanceRight;
				mostRight = tmpPoint;
			}
		}
		return new Line2D(mostLeft, mostRight);
	}
	
	/**
	 * 将一个平行的线段集合，有重叠部分的线段合并，返回新的线段集合，集合中的线段两两不相交
	 * @param lines 平行的线段集合
	 * @return
	 */
	public static List<Line2D> mergeSegments(List<Line2D> lines)
	{
		//根据线段的起点排序
		sortSegmentsByLeftPoint(lines);
		
		List<Line2D> res = new ArrayList<Line2D>();
		
		while(!lines.isEmpty())
		{
			List<Line2D> linesToRemove = new ArrayList<Line2D>();
			Line2D leftMost = lines.get(0);
			lines.remove(0);
			for (Line2D curLine: lines)
			{
				if(isIntersect(leftMost, curLine))
					linesToRemove.add(curLine);
				else break;
			}
			res.add(leftMost);
			lines.removeAll(linesToRemove);
		}
		//PrintSegs(res);
		return res;
	}
	
	/**
	 * 根据线段的左端点排序
	 * @param lines
	 */
	public static void sortSegmentsByLeftPoint(List<Line2D> lines)
	{
		lines.sort(new Comparator<Line2D>() {
			@Override
			public int compare(Line2D arg0, Line2D arg1) {
				double eps = 1e-8;
				double xx = arg0.getOrigin().getX() - arg1.getOrigin().getX();
				if(abs(xx) > eps)
				{
					if (xx > 0.0) return 1;
					return -1;
				}
				else 
				{
					double yy = arg0.getOrigin().getY() - arg1.getOrigin().getY();
					if(abs(yy) > eps)
					{
						if(yy > 0.0) return 1;
						return -1;
					}
					else 
						return 0;
				}
			}
			private double abs(double x)
			{
				return x > 0.0 ? x : -x;
			}	
		});
	}
	
	/**
	 * line1的左端点在line2的左端点左边的情况下，调用
	 * 判断同一条直线上的两个线段是否相交,并用line1覆盖line2
	 * @param line1
	 * @param line2
	 * @return
	 */
	private static boolean isIntersect(Line2D line1, Line2D line2)
	{
		double aa = Ruler.getDistance(line1.getOrigin(), line1.getEndPoint());
		double bb = Ruler.getDistance(line1.getOrigin(), line2.getOrigin());
		double cc = Ruler.getDistance(line1.getOrigin(), line2.getEndPoint());
		if(aa > cc)
			return true;
		else if (aa > bb)
		{
			line1.setEnd(line2.getEndPoint());
			return true;
		}
		else
			return false;
	}
	
}
