package NUDT.utils.extendTools.FireBrigadeTools;


import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.sun.java.swing.plaf.windows.resources.windows;

import rescuecore2.standard.entities.Edge;
import rescuecore2.standard.entities.Building;
import rescuecore2.standard.entities.StandardEntity;
import rescuecore2.standard.entities.StandardEntityConstants;
import rescuecore2.standard.entities.StandardEntityConstants.Fieryness;
import rescuecore2.standard.entities.StandardEntityURN;
import rescuecore2.standard.entities.StandardWorldModel;
import rescuecore2.worldmodel.ChangeSet;
import rescuecore2.worldmodel.EntityID;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.GeometryTools2D;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;
import adf.agent.communication.MessageManager;
import adf.agent.develop.DevelopData;
import adf.agent.info.AgentInfo;
import adf.agent.info.ScenarioInfo;
import adf.agent.info.WorldInfo;
import adf.agent.module.ModuleManager;
import adf.agent.precompute.PrecomputeData;
import adf.component.module.AbstractModule;
import adf.component.module.algorithm.Clustering;

public class EnergyFlow extends AbstractModule {

	private static final double LINE_LENGTH_SCALE = 1000.0 * 2.0;
	private static final double K = 1000000.0;// = k / (4 * PI)
	private static final int rayCount = 32;
	
	private final Map<Building, FlowLine[]> flowGraph;
	private final Map<Building, Double> outFlow;
	private final Map<Building, Double> inFlow;
	private final Map<Building, Double> inTotal;
	private ArrayList<Building> affectedRanking;
	
	public EnergyFlow(AgentInfo ai, WorldInfo wi, ScenarioInfo si, ModuleManager moduleManager,
			DevelopData developData) {
		super(ai, wi, si, moduleManager, developData);
		
		final Collection<StandardEntity> buildings = this.worldInfo.getEntitiesOfType(StandardEntityURN.BUILDING);
		
		Map<Building, FlowLine[]> modifiableFlowGraph = new HashMap<Building, FlowLine[]>(buildings.size());
		for (StandardEntity se : buildings) {
			final Building target = (Building) se;

			final double r = Math.sqrt(target.getGroundArea()) * LINE_LENGTH_SCALE;
			FlowLine[] lineArray = new FlowLine[rayCount];
			final double dAngle = Math.PI * 2 / rayCount;
			for (int i = 0; i < rayCount; ++i) {
				final double angle = i * dAngle;
				final double dx = Math.sin(angle) * r;
				final double dy = Math.cos(angle) * r;
				final Line2D ray = new Line2D(target.getX(), target.getY(), dx, dy);
				lineArray[i] = new FlowLine(target, this.worldInfo.getObjectsInRange(target, (int) r), ray);
			}
			modifiableFlowGraph.put(target, lineArray);
		}
		flowGraph = Collections.unmodifiableMap(modifiableFlowGraph);
		
		outFlow = new HashMap<Building, Double>(buildings.size());
		inFlow = new HashMap<Building, Double>(buildings.size());
		inTotal = new HashMap<Building, Double>(buildings.size());
		
	}

	public void update(ChangeSet change) {
		inFlow.clear();
		Collection<Building> onFireBuildings = this.worldInfo.getFireBuildings();
		for (Building building : onFireBuildings) {
			final double lineAffecct = calcurateLineHits(building);
			outFlow.put(building, lineAffecct);
		}
		for (Iterator<Building> it = outFlow.keySet().iterator(); it.hasNext();) {
			Building building = it.next();
			if (onFireBuildings.contains(building))
				continue;
			if (!building.isOnFire()) {
				it.remove();
			}
		}
		
		affectedRanking = new ArrayList<Building>();
		for (Entry<Building, Double> entry : inFlow.entrySet()) {
			Building key = entry.getKey();
			if (key.isFierynessDefined()
					&& key.getFierynessEnum() == StandardEntityConstants.Fieryness.BURNT_OUT) {
				inTotal.remove(key);
				continue;
			}
			inTotal.put(key, getInTotal(key) + entry.getValue());
			affectedRanking.add(key);
		}
		Collections.sort(affectedRanking, new Comparator<Building>() {
			@Override
			public int compare(Building b1, Building b2) {
				double d = getInTotal(b1) - getInTotal(b2);
				if (d < 0) return -1;
				if (d > 0) return 1;
				return 0;
			}
		});
	}

	private double calcurateLineHits(Building target) {
		if (!target.isTemperatureDefined()) return 0.0;
		FlowLine[] lines = flowGraph.get(target);
		if (lines == null) return 0.0;
		
		double result = 0.0;
		for (FlowLine line : lines) {
			final Building nearestHit = line.getBuilding();
			if (nearestHit == null) continue;
			if (nearestHit.isFierynessDefined()
					&& nearestHit.getFierynessEnum() == StandardEntityConstants.Fieryness.BURNT_OUT)
				continue;
			final double dist = line.getDistance();
			final double sin = line.getSin();
			final double affectTemp = target.getTemperature();
			final double value = K * sin * affectTemp / (dist * dist);
			
			Double preAffectedValue = inFlow.get(nearestHit);
			if (preAffectedValue == null) {
				preAffectedValue = 0.0;
			}
			inFlow.put(nearestHit, preAffectedValue + value);
			
			result += value;
			if (nearestHit.isFierynessDefined()
					&& nearestHit.getFierynessEnum() == Fieryness.UNBURNT) {
				result += value;
			}
		}
		return result;
	}

	public double getOut(Building building) {
		Double result = outFlow.get(building);
		if (result == null) return 0.0;
		return result;
	}
	
	public double getOut(EntityID id) {
		return getOut((Building) this.worldInfo.getEntity(id));
	}
	
	public double getIn(Building building) {
		Double result = inFlow.get(building);
		if (result == null) return 0.0;
		return result;
	}
	
	public double getIn(EntityID id) {
		return getIn((Building) this.worldInfo.getEntity(id));
	}
	
	public double getInTotal(Building building) {
		Double result = inTotal.get(building);
		if (result == null) return 0.0;
		return result;
	}
	
	public double getInTotal(EntityID id) {
		return getInTotal((Building) this.worldInfo.getEntity(id));
	}
	
	public List<Building> getInTotalRanking() {
		return Collections.unmodifiableList(affectedRanking);
	}
	
	
	
	
	private class FlowLine {
		
		private final Building building;
		private final double distance; 
		private final double sin;
		
		public FlowLine(Building target, Collection<StandardEntity> range, Line2D ray) {
			// 线段从中最附近时Building寻求
			Building nearestHit = null;
			Line2D hitLine = null;
			double nearestHitDist = Double.MAX_VALUE;
			
			for (StandardEntity se : range) {
				if (!(se instanceof Building) || target.equals(se))
					continue;
				final Building building = (Building) se;
				for (Edge e : building.getEdges()) {
					if (e.isPassable()) continue;
					final Line2D l = e.getLine();
					final double d1 = ray.getIntersection(l);
					final double d2 = l.getIntersection(ray);
					if (d1 < nearestHitDist
							&& 0.0 <= d2 && d2 <= 1.0
							&& 0.0 < d1 && d1 <= 1.0) {
						nearestHit = building;
						hitLine = l;
						nearestHitDist = d1;
					}
				}
			}
			building = nearestHit;
			if (nearestHit != null) {
				final Point2D rayPt1 = ray.getOrigin(), rayPt2 = ray.getEndPoint();
				final Point2D hitLinePt1 = hitLine.getOrigin(), hitLinePt2 = hitLine.getEndPoint();
				// Outer product
				final double cross = Math.abs(
						(rayPt2.getX() - rayPt1.getX()) * (hitLinePt2.getY() - hitLinePt1.getY())
						- (hitLinePt2.getX() - hitLinePt1.getX()) * (rayPt2.getY() - rayPt1.getY()));
				final double rayLength = GeometryTools2D.getDistance(rayPt1, rayPt2);
				final double hitLineLength = GeometryTools2D.getDistance(hitLinePt1, hitLinePt2);
				sin = cross / (rayLength * hitLineLength);
				// 交点为止的hitLine和垂直的方向的距离
				distance = rayLength * nearestHitDist;
			}
			else {
				sin = 0.0;
				distance = 0.0;
			}
		}

		public Building getBuilding() {
			return building;
		}

		public double getDistance() {
			return distance;
		}

		public double getSin() {
			return sin;
		}
	}

	@Override
	public EnergyFlow precompute(PrecomputeData precomputeData) {
		super.precompute(precomputeData);
		return this;
	}

	@Override
	public EnergyFlow resume(PrecomputeData precomputeData) {
		super.resume(precomputeData);
		return this;
	}

	@Override
	public EnergyFlow preparate() {
		super.preparate();
		return this;
	}

	@Override
	public EnergyFlow updateInfo(MessageManager messageManager) {
		super.updateInfo(messageManager);
		this.update(worldInfo.getChanged());
		return this;
	}

	@Override
	public AbstractModule calc() {
		// TODO Auto-generated method stub
		return null;
	}

	
}






