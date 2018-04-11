package NUDT.module.algorithm.pathplanning.pov;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import rescuecore2.misc.geometry.Line2D;
import rescuecore2.standard.entities.Blockade;

public class EscapePoint {
	private List<Blockade> realteBlockades = new ArrayList<>();;
	
	private Point underlyingPoint;
	
	private Line2D line;
	
	public EscapePoint(Point point, Line2D line, Blockade... blockade) {
		this.setUnderlyingPoint(point);
		this.setLine(line);
		
		for (Blockade next : blockade) {
			this.realteBlockades.add(next);
		}
	}

	public List<Blockade> getRelateBlockade() {
		return this.realteBlockades;
	}
	
	public void addCsuBlockade(Blockade blockade) {
		this.realteBlockades.add(blockade);
	}
	
	public boolean removeCsuBLockade(Blockade blockade) {
		return this.realteBlockades.remove(blockade);
	}

	public Point getUnderlyingPoint() {
		return underlyingPoint;
	}

	public void setUnderlyingPoint(Point underlyingPoint) {
		this.underlyingPoint = underlyingPoint;
	}

	public Line2D getLine() {
		return line;
	}

	public void setLine(Line2D line) {
		this.line = line;
	}
}
