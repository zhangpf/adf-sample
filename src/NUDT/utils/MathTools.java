package NUDT.utils;

import rescuecore2.misc.Pair;
import rescuecore2.misc.geometry.Line2D;
import rescuecore2.misc.geometry.Point2D;

public class MathTools {

	public static Pair<Double, Boolean> ptSegDistSq(Line2D line,Point2D point) {
		return ptSegDistSq((int)line.getOrigin().getX(), (int)line.getOrigin().getY(), 
				(int)line.getEndPoint().getX(), (int)line.getEndPoint().getY(), 
				(int)point.getX(), (int)point.getY());
	}
	
	public static Pair<Double, Boolean> ptSegDistSq(double x1, double y1, double x2,
			double y2, double px, double py) {

		x2 -= x1;
		y2 -= y1;

		px -= x1;
		py -= y1;

		double dotprod = px * x2 + py * y2;

		double projlenSq;

		if (dotprod <= 0) {
			projlenSq = 0;
		} else {
			px = x2 - px;
			py = y2 - py;
			dotprod = px * x2 + py * y2;

			if (dotprod <= 0.0) {
				projlenSq = 0.0;
			} else {
				projlenSq = dotprod * dotprod / (x2 * x2 + y2 * y2);
			}
		}
		
		double lenSq = px * px + py * py - projlenSq;

		if (lenSq < 0)
			lenSq = 0;
		
		if (projlenSq == 0) {
			// the target point out of this line
			return new Pair<Double, Boolean>(Math.sqrt(lenSq), true);
		} else {
			// the target point within this line
			return new Pair<Double, Boolean>(Math.sqrt(lenSq), false);
		}
	}
}
